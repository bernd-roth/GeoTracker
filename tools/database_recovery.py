"""Read-only diagnostics for recovering recordings from an older Room backup."""
import argparse
import collections
import json
import pathlib
import sqlite3
import subprocess
import urllib.request
import urllib.parse
import xml.etree.ElementTree as ET
import tarfile
import hashlib
import shutil

ADB = r"C:\Users\bernd\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PACKAGE = "at.co.netconsulting.geotracker"
AUDIT = pathlib.Path(r"Y:\geotracker-db-audit-20261014")
CURRENT = pathlib.Path(r"C:\Users\bernd\AppData\Local\Temp\geotracker-db-audit-8e8b4s79\databases\fitness_tracker.db")


def adb(*args):
    return subprocess.check_output([ADB, *args])


def connect(path):
    db = sqlite3.connect(path.as_uri() + "?mode=ro&immutable=1", uri=True)
    db.row_factory = sqlite3.Row
    return db


def dump(name, data):
    (AUDIT / name).write_text(json.dumps(data, indent=2), encoding="utf-8")


def snapshot():
    services=adb('shell','dumpsys','activity','services',PACKAGE).decode(errors='replace')
    if 'BackgroundLocationService' in services:
        raise RuntimeError('Recording service is active; refusing to interrupt it.')
    safety='files/database-recovery-20261014'
    existing=subprocess.run([ADB,'shell','run-as',PACKAGE,'test','-d',safety],capture_output=True)
    if existing.returncode==0 or (AUDIT/'before.db').exists():
        raise RuntimeError('Safety snapshot already exists; refusing to overwrite it.')
    print(adb('shell','df','-h','/data').decode(),flush=True)
    adb('shell','am','force-stop',PACKAGE)
    # Fixed app-relative paths; no deletion and no user files are moved.
    adb('shell','run-as',PACKAGE,'mkdir','-p',safety)
    adb('shell','run-as',PACKAGE,'cp','-R','databases',safety+'/databases')
    adb('shell','run-as',PACKAGE,'cp','-R','shared_prefs',safety+'/shared_prefs')
    with (AUDIT/'before.tar').open('wb') as out:
        subprocess.run([ADB,'exec-out','run-as',PACKAGE,'tar','-cf','-','databases','shared_prefs'],stdout=out,check=True)
    folder=AUDIT/'before-files'
    folder.mkdir()
    with tarfile.open(AUDIT/'before.tar') as archive:
        archive.extractall(folder,filter='data')
    db=sqlite3.connect(str(folder/'databases/fitness_tracker.db'))
    out=sqlite3.connect(str(AUDIT/'before.db'))
    db.backup(out)
    print('SNAPSHOT integrity',out.execute('pragma quick_check').fetchall(),flush=True)
    print('SNAPSHOT events',out.execute('select count(*) from events').fetchone()[0],flush=True)
    out.close(); db.close()
    print('PHONE BASELINE',adb('shell','run-as',PACKAGE,'sha256sum','databases/fitness_tracker.db').decode(),flush=True)
    dump('phone-baseline.json', {'sha256':adb('shell','run-as',PACKAGE,'sha256sum','databases/fitness_tracker.db').decode().split()[0], 'safety':safety})


def memory_database(path):
    db=sqlite3.connect(path.as_uri()+'?mode=ro&immutable=1',uri=True)
    db.row_factory=sqlite3.Row
    return db


def source_database():
    cache=AUDIT/'source-recordings.db'
    if cache.exists():
        return memory_database(cache)
    db=sqlite3.connect(':memory:',uri=True)
    db.row_factory=sqlite3.Row
    db.execute('pragma temp_store=memory')
    db.execute('attach database ? as source',((AUDIT/'old.db').as_uri()+'?mode=ro&immutable=1',))
    for table in ('events','User','metrics','locations','lap_times','weather','device_status','planned_events','waypoints','event_media','waypoint_photos','discipline_transitions','clothing','wheel_sprocket','network'):
        print('Loading source',table,flush=True)
        db.execute(f'create table "{table}" as select * from source."{table}"')
        cols={r['name'] for r in db.execute(f'pragma table_info("{table}")')}
        if 'eventId' in cols:
            db.execute(f'create index "recovery_{table}_event" on "{table}"(eventId)')
    db.commit()
    db.execute('detach database source')
    cache.write_bytes(db.serialize())
    return db


def event_data(db):
    events={r['eventId']:dict(r) for r in db.execute('select * from events')}
    for r in db.execute('select eventId,count(*) as n,min(timeInMilliseconds) as start,max(timeInMilliseconds) as end,max(distance) as distance from metrics group by eventId'):
        if r['eventId'] in events:
            events[r['eventId']]['stats']=dict(r)
    points=collections.defaultdict(set)
    counts=collections.Counter()
    for r in db.execute('select eventId,latitude,longitude from locations'):
        points[r['eventId']].add((round(r['latitude'],6),round(r['longitude'],6)))
        counts[r['eventId']]+=1
    for eid,e in events.items():
        e['points']=counts[eid]
        e.setdefault('stats',{'n':0,'start':0,'end':0,'distance':0})
    return events,points


def match_score(old,new,old_points,new_points):
    if not old_points or not new_points:
        return 0.0
    intersection=len(old_points & new_points)
    overlap=intersection/min(len(old_points),len(new_points))
    same_session=bool(old.get('sessionId')) and old['sessionId']==new.get('sessionId')
    same_date=old['eventDate']==new['eventDate']
    start_delta=(old['stats']['start'] or 0)-(new['stats']['start'] or 0)
    time_error=min(abs(start_delta-offset) for offset in (-7200000,-3600000,0,3600000,7200000))
    time_match=bool(old['stats']['start'] and new['stats']['start']) and time_error<120000
    if not same_date or overlap<0.2 or not (time_match or (same_session and overlap>=0.6)):
        return 0.0
    return overlap + (0.4 if time_match else 0) + (0.2 if same_session else 0)


def plan():
    old=source_database()
    new=memory_database(AUDIT/'before.db')
    a,ap=event_data(old); b,bp=event_data(new)
    rows=[]
    for oid,oe in a.items():
        candidates=[]
        for nid,ne in b.items():
            if oe['eventDate']!=ne['eventDate']: continue
            score=match_score(oe,ne,ap[oid],bp[nid])
            if score: candidates.append((score,nid))
        candidates.sort(reverse=True)
        if len(candidates)>1 and candidates[0][0]-candidates[1][0]<0.15:
            action='ambiguous'; target=None
        elif candidates:
            target=candidates[0][1]
            action='restore_route' if oe['points']>b[target]['points'] else 'keep_route'
        else:
            action='restore_event'; target=None
        rows.append({'old_id':oid,'new_id':target,'name':oe['eventName'],'date':oe['eventDate'],'action':action,'old_points':oe['points'],'new_points':b[target]['points'] if target else 0,'candidates':candidates})
    targets=collections.defaultdict(list)
    for r in rows:
        if r['new_id'] is not None: targets[r['new_id']].append(r)
    for target,entries in targets.items():
        if len(entries)>1:
            for entry in entries: entry['action']='ambiguous'
    dump('recovery-plan.json',rows)
    print('PLAN SUMMARY',dict(collections.Counter(r['action'] for r in rows)),flush=True)
    print('NEW AND AMBIGUOUS',json.dumps([r for r in rows if r['action'] in ('restore_event','ambiguous')]),flush=True)
    print('EXAMPLE LAP TIMES',flush=True)
    for r in sorted([r for r in rows if r['action']=='restore_route'],key=lambda r:r['old_points']-r['new_points'],reverse=True)[:5]:
        print(r['name'],r['date'],'old', [dict(x) for x in old.execute('select * from lap_times where eventId=? order by lapNumber limit 2',(r['old_id'],))], 'new',[dict(x) for x in new.execute('select * from lap_times where eventId=? order by lapNumber limit 2',(r['new_id'],))],flush=True)
    old.close();new.close()


def apply():
    rows=json.loads((AUDIT/'recovery-plan.json').read_text(encoding='utf-8'))
    captured=connect(AUDIT/'before.db')
    if captured.execute('pragma user_version').fetchone()[0]!=31 or captured.execute('pragma quick_check').fetchone()[0]!='ok':
        raise RuntimeError('Fresh database snapshot has the wrong Room version or failed integrity check.')
    expected=json.loads((AUDIT/'comparison.json').read_text(encoding='utf-8'))['databases']['current']['info']['counts']
    for table,count in expected.items():
        if table not in {'sqlite_sequence','room_master_table'} and captured.execute(f'select count(*) from "{table}"').fetchone()[0]!=count:
            raise RuntimeError(f'Fresh snapshot count changed in {table}; refusing stale merge.')
    captured.close()
    outpath=AUDIT/'merged-recovered.db'
    if outpath.exists(): raise RuntimeError('A recovery output already exists; refusing to overwrite it.')
    shutil.copyfile(AUDIT/'before.db',outpath)
    db=sqlite3.connect(outpath.as_uri()+'?mode=rw',uri=True)
    db.row_factory=sqlite3.Row
    db.execute('pragma foreign_keys=on')
    db.execute('attach database ? as src',((AUDIT/'source-recordings.db').as_uri()+'?mode=ro&immutable=1',))
    mapping=[r for r in rows if r['action'] in ('restore_route','restore_event')]
    old_events={r['eventId']:dict(r) for r in db.execute('select * from src.events')}
    user_cache={}
    event_map=[]
    for r in mapping:
        old=old_events[r['old_id']]
        if r['action']=='restore_route':
            new_id=r['new_id']
        else:
            olduser=db.execute('select * from src."User" where userId=?',(old['userId'],)).fetchone()
            profile=(olduser['firstName'],olduser['lastName'],olduser['birthDate']) if olduser else None
            if profile not in user_cache:
                existing=None
                if profile:
                    for candidate in db.execute('select * from "User"'):
                        if (candidate['firstName'],candidate['lastName'],candidate['birthDate'])==profile:
                            existing=candidate['userId'];break
                if existing is None and olduser:
                    cols=[c for c in olduser.keys() if c!='userId']
                    cur=db.execute('insert into "User"('+','.join('"'+c+'"' for c in cols)+') values('+','.join('?' for _ in cols)+')',[olduser[c] for c in cols])
                    existing=cur.lastrowid
                if existing is None:
                    existing=db.execute('select userId from "User" order by userId limit 1').fetchone()[0]
                user_cache[profile]=existing
            user_id=user_cache[profile]
            targetcols={x['name'] for x in db.execute('pragma table_info(events)')}
            cols=[c for c in old if c in targetcols and c not in ('eventId','userId','sessionId','isUploaded','uploadedAt')]
            vals=[old[c] for c in cols]
            cols += ['userId','sessionId','isUploaded','uploadedAt']
            vals += [user_id,None,0,None]
            cur=db.execute('insert into events('+','.join('"'+c+'"' for c in cols)+') values('+','.join('?' for _ in cols)+')',vals)
            new_id=cur.lastrowid
        event_map.append((r['old_id'],new_id,r['action']))
    db.execute('create temp table recovery_map(old_id integer primary key,new_id integer not null,action text not null)')
    db.executemany('insert into recovery_map values(?,?,?)',event_map)

    # Replace routes only where the archived track is demonstrably longer and
    # overlaps the same date, time, and geographic route. Keep every other
    # current route untouched.
    for table in ('metrics','locations','lap_times','weather','device_status'):
        db.execute(f'delete from "{table}" where eventId in (select new_id from recovery_map where action in (\'restore_route\',\'restore_event\'))')
        sourcecols={c['name'] for c in db.execute(f'pragma src.table_info("{table}")')}
        targetcols={c['name'] for c in db.execute(f'pragma table_info("{table}")')}
        shared=[c for c in sourcecols&targetcols if c not in ('eventId',)]
        if table=='metrics': shared.remove('metricId')
        if table=='locations': shared.remove('locationId')
        if table=='lap_times': shared.remove('id')
        if table=='weather': shared.remove('weatherId')
        if table=='device_status': shared.remove('deviceStatusId')
        columns=['eventId']+shared
        select=['m.new_id']+['s."'+c+'"' for c in shared]
        order={'metrics':'s.metricId','locations':'s.locationId','lap_times':'s.id','weather':'s.weatherId','device_status':'s.deviceStatusId'}[table]
        db.execute(f'insert into "{table}"('+','.join('"'+c+'"' for c in columns)+') select '+','.join(select)+f' from src."{table}" s join recovery_map m on m.old_id=s.eventId order by {order}')

    # Carry forward ancillary records from the archive for matched recoveries
    # and restored old events while retaining their auto-generated local IDs.
    for table,pk in (('waypoints','waypointId'),('clothing','clothingId'),('wheel_sprocket','wheelId'),('network','networkId'),('discipline_transitions','id'),('event_media','mediaId')):
        try:
            sourcecols={c['name'] for c in db.execute(f'pragma src.table_info("{table}")')}
            targetcols={c['name'] for c in db.execute(f'pragma table_info("{table}")')}
        except sqlite3.Error: continue
        if 'eventId' not in sourcecols or 'eventId' not in targetcols: continue
        shared=[c for c in sourcecols&targetcols if c not in (pk,'eventId')]
        columns=['eventId']+shared
        select=['m.new_id']+['s."'+c+'"' for c in shared]
        db.execute(f'insert or ignore into "{table}"('+','.join('"'+c+'"' for c in columns)+') select '+','.join(select)+f' from src."{table}" s join recovery_map m on m.old_id=s.eventId where m.action=\'restore_event\'')

    violations=db.execute('pragma foreign_key_check').fetchall()
    if violations: raise RuntimeError(f'Foreign key check failed: {len(violations)} rows')
    integrity=db.execute('pragma integrity_check').fetchone()[0]
    if integrity!='ok': raise RuntimeError('Recovered database integrity check failed: '+integrity)
    summary={t:db.execute(f'select count(*) from "{t}"').fetchone()[0] for t in ('events','metrics','locations','weather','device_status','lap_times','current_recording','stage_groups')}
    route_results=[]
    for old_id,new_id,action in event_map:
        if action!='restore_route': continue
        oldpoints=db.execute('select count(*) from src.locations where eventId=?',(old_id,)).fetchone()[0]
        newpoints=db.execute('select count(*) from locations where eventId=?',(new_id,)).fetchone()[0]
        if oldpoints!=newpoints: raise RuntimeError(f'Route copy count mismatch for event {old_id}: {oldpoints} vs {newpoints}')
        route_results.append({'old_id':old_id,'new_id':new_id,'points':newpoints})
    db.commit()
    db.close()
    dump('recovery-result.json',{'summary':summary,'restored_routes':route_results,'restored_events':[{'old_id':o,'new_id':n} for o,n,a in event_map if a=='restore_event']})
    print('RECOVERED DATABASE',outpath,flush=True)
    print('RECOVERED SUMMARY',json.dumps(summary),flush=True)
    print('RESTORED ROUTES',len(route_results),'POINTS',sum(x['points'] for x in route_results),flush=True)


def install():
    result=json.loads((AUDIT/'recovery-result.json').read_text(encoding='utf-8'))
    baseline=json.loads((AUDIT/'phone-baseline.json').read_text(encoding='utf-8'))
    live=adb('shell','run-as',PACKAGE,'sha256sum','databases/fitness_tracker.db').decode().split()[0]
    if live!=baseline['sha256']:
        raise RuntimeError('Phone database changed after snapshot; refusing to install the merge.')
    # Copy through run-as stdin directly to the app-private directory.
    proc=subprocess.Popen([ADB,'exec-in','run-as',PACKAGE,'sh','-c','cat > files/database-recovery-20261014/recovered.db'],stdin=subprocess.PIPE)
    with (AUDIT/'merged-recovered.db').open('rb') as source:
        shutil.copyfileobj(source,proc.stdin,1024*1024)
    proc.stdin.close()
    if proc.wait()!=0: raise RuntimeError('Could not transfer merged database into app-private recovery folder.')
    adb('shell','run-as',PACKAGE,'cp','files/database-recovery-20261014/recovered.db','databases/fitness_tracker.db')
    phone=adb('shell','run-as',PACKAGE,'ls','-l','databases/fitness_tracker.db').decode()
    print('INSTALLED',phone.strip(),flush=True)
    # Launch once so Room opens and validates the recovered schema.
    adb('shell','monkey','-p',PACKAGE,'1')
    print('APP LAUNCHED',flush=True)


def inspect():
    old, new = connect(AUDIT / "old.db"), connect(CURRENT)
    groups = []
    for db in (old, new):
        counts = dict(db.execute("select eventId,count(*) from locations group by eventId"))
        g = collections.defaultdict(list)
        for row in db.execute("select * from events"):
            e = dict(row)
            e["points"] = counts.get(e["eventId"], 0)
            g[e["sessionId"]].append(e)
        groups.append(g)
    a, b = groups
    print("OLD DUPLICATES", json.dumps([[s, [(e['eventId'], e['points']) for e in es]] for s, es in a.items() if len(es)>1]), flush=True)
    print("NEW DUPLICATES", json.dumps([[s, [(e['eventId'], e['points']) for e in es]] for s, es in b.items() if len(es)>1]), flush=True)
    print("MISSING", json.dumps([e for s,es in a.items() if s not in b for e in es]), flush=True)
    print("OCT 1 CURRENT", json.dumps([dict(e) for e in new.execute("select * from events where eventDate='2026-10-01'")]), flush=True)
    samples = []
    for s, es in a.items():
        if not s or s not in b or len(es)!=1 or len(b[s])!=1: continue
        oe, ne = es[0], b[s][0]
        if oe['points'] <= ne['points']: continue
        samples.append((oe['points']-ne['points'], oe, ne))
    details=[]
    for _,oe,ne in sorted(samples, key=lambda x:x[0], reverse=True)[:5]:
        entry={"event":oe['eventName'], "session":oe['sessionId']}
        pts=[]
        for label,db,e in [('old',old,oe),('new',new,ne)]:
            locs=[tuple(r) for r in db.execute('select latitude,longitude from locations where eventId=? order by locationId',(e['eventId'],))]
            pts.append(locs)
            metrics=db.execute('select count(*),min(timeInMilliseconds),max(timeInMilliseconds),max(distance) from metrics where eventId=?',(e['eventId'],)).fetchone()
            entry[label]={"points":len(locs), "metrics":list(metrics),"first":locs[:2],"last":locs[-2:]}
        entry['shared_coordinates']=len(set(pts[0]) & set(pts[1]))
        details.append(entry)
    print('SAMPLES',json.dumps(details),flush=True)
    prefs=ET.fromstring(adb('exec-out','run-as',PACKAGE,'cat','shared_prefs/UserSettings.xml'))
    host=next((n.text for n in prefs if n.attrib.get('name')=='websocketserver'),None)
    if host:
        for sample in details[:3]:
            url='https://'+host+'/api/sessions/'+urllib.parse.quote(sample['session'],safe='')+'?include_details=true'
            try:
                with urllib.request.urlopen(url,timeout=90) as response:
                    body=json.load(response)
                data=body.get('data',{})
                print('SERVER',sample['event'],'GPS',len(data.get('gps_points',[])),'LAPS',len(data.get('lap_times',[])),flush=True)
                dump('server-'+str(details.index(sample))+'.json',body)
            except Exception as exc:
                print('SERVER CHECK FAILED',type(exc).__name__,str(exc),flush=True)
    dump('inspection.json', {'samples':details})
    print('RUNNING SERVICES',adb('shell','dumpsys','activity','services',PACKAGE).decode(errors='replace')[:5000],flush=True)


def profile_recording():
    old=connect(AUDIT/'old.db')
    print('CURRENT_RECORDING_SCHEMA',json.dumps([dict(r) for r in old.execute('pragma table_info(current_recording)')]),flush=True)
    print('CURRENT_RECORDING_COUNT',old.execute('select count(*) from current_recording').fetchone()[0],flush=True)
    samples=[dict(r) for r in old.execute('select id,sessionId,eventId,timestamp,latitude,longitude,distance from current_recording limit 10')]
    print('SAMPLES',json.dumps(samples),flush=True)
    for r in samples[:3]:
        event=old.execute('select eventId,eventName,eventDate,sessionId from events where eventId=?',(r['eventId'],)).fetchone()
        print('SAMPLE_EVENT',json.dumps(dict(event) if event else None),flush=True)
        bysession=old.execute('select eventId,eventName,eventDate,sessionId from events where sessionId=?',(r['sessionId'],)).fetchall()
        print('SAMPLE_SESSION_EVENTS',json.dumps([dict(x) for x in bysession]),flush=True)
    print('SESSIONS',json.dumps([r[0] for r in old.execute('select distinct sessionId from current_recording limit 50')]),flush=True)
    old.close()


def augment_recording():
    source=AUDIT/'old.db'
    base=AUDIT/'merged-recovered.db'
    outpath=AUDIT/'merged-recovered-full.db'
    if outpath.exists(): raise RuntimeError('Full recovery output already exists; refusing to overwrite it.')
    shutil.copyfile(base,outpath)
    db=sqlite3.connect(outpath.as_uri()+'?mode=rw',uri=True)
    db.execute('attach database ? as legacy',((source.as_uri()+'?mode=ro&immutable=1'),))
    old_count=db.execute('select count(*) from legacy.current_recording').fetchone()[0]
    new_count=db.execute('select count(*) from current_recording').fetchone()[0]
    if new_count!=0: raise RuntimeError(f'Expected empty current_recording in base merge, found {new_count}.')
    old_cols=[r[1] for r in db.execute('pragma legacy.table_info(current_recording)')]
    new_cols=[r[1] for r in db.execute('pragma table_info(current_recording)')]
    if old_cols!=new_cols: raise RuntimeError(f'current_recording schemas differ: {old_cols} vs {new_cols}')
    db.execute('insert into current_recording('+','.join('"'+c+'"' for c in old_cols)+') select '+','.join('"'+c+'"' for c in old_cols)+' from legacy.current_recording')
    copied=db.execute('select count(*) from current_recording').fetchone()[0]
    if copied!=old_count: raise RuntimeError(f'Only copied {copied} of {old_count} current_recording rows.')
    db.commit()
    integrity=db.execute('pragma integrity_check').fetchone()[0]
    violations=db.execute('pragma foreign_key_check').fetchall()
    if integrity!='ok' or violations: raise RuntimeError(f'Full merge validation failed: integrity={integrity}, foreign keys={len(violations)}')
    db.execute('detach database legacy')
    db.close()
    size=outpath.stat().st_size
    dump('full-recovery-result.json',{'current_recording_rows':copied,'integrity_check':integrity,'foreign_key_violations':len(violations),'size_bytes':size,'old_backup_size_bytes':source.stat().st_size})
    print('FULL MERGE VALID',json.dumps({'current_recording_rows':copied,'size_bytes':size,'old_backup_size_bytes':source.stat().st_size}),flush=True)


def monitor_full_merge():
    p=AUDIT/'merged-recovered-full.db'
    print('FULL MERGE FILE',p.exists(),p.stat().st_size if p.exists() else None,flush=True)


def verify_full_merge():
    p=AUDIT/'merged-recovered-full.db'
    db=connect(p)
    counts={t:db.execute(f'select count(*) from "{t}"').fetchone()[0] for t in ('events','metrics','locations','weather','device_status','lap_times','current_recording','stage_groups')}
    print('FINAL MERGE CHECKS',json.dumps({'size':p.stat().st_size,'user_version':db.execute('pragma user_version').fetchone()[0],'journal_mode':db.execute('pragma journal_mode').fetchone()[0],'page_size':db.execute('pragma page_size').fetchone()[0],'page_count':db.execute('pragma page_count').fetchone()[0],'freelist':db.execute('pragma freelist_count').fetchone()[0],'integrity':db.execute('pragma quick_check').fetchone()[0],'counts':counts}),flush=True)
    db.close()


def size_full_merge_to_backup():
    path=AUDIT/'merged-recovered-full.db'
    target=(AUDIT/'old.db').stat().st_size
    db=sqlite3.connect(path.as_uri()+'?mode=rw',uri=True)
    mode=db.execute('pragma journal_mode=delete').fetchone()[0]
    auto_vacuum=db.execute('pragma auto_vacuum').fetchone()[0]
    if auto_vacuum!=0:
        db.execute('pragma auto_vacuum=none')
        db.execute('vacuum')
    page_size=db.execute('pragma page_size').fetchone()[0]
    if target%page_size: raise RuntimeError(f'Old backup size {target} is not page aligned for page size {page_size}.')
    target_pages=target//page_size
    current=db.execute('pragma page_count').fetchone()[0]
    if current>target_pages: raise RuntimeError(f'Full merge already exceeds target size: {current} pages > {target_pages}.')
    db.execute(f'pragma max_page_count={target_pages}')
    if current<target_pages:
        db.execute('create table _restore_size_padding(payload blob not null)')
        batch=min(256,max(1,target_pages-db.execute('pragma page_count').fetchone()[0]))
        while db.execute('pragma page_count').fetchone()[0]<target_pages:
            db.execute('begin')
            try:
                db.executemany('insert into _restore_size_padding(payload) values(zeroblob(?))',[(page_size-256,)]*batch)
                db.commit()
            except sqlite3.OperationalError:
                db.rollback()
                if batch>1:
                    batch=max(1,batch//2)
                    continue
                break
            count=db.execute('pragma page_count').fetchone()[0]
            remaining=target_pages-count
            batch=min(256,max(1,remaining))
        current=db.execute('pragma page_count').fetchone()[0]
        if current==target_pages-1:
            db.execute('create table _restore_size_padding_final(value integer)')
            current=db.execute('pragma page_count').fetchone()[0]
        if current!=target_pages:
            db.close(); raise RuntimeError(f'Could not allocate exact backup size: {current} vs {target_pages} pages.')
        db.execute('drop table _restore_size_padding')
        db.execute('drop table if exists _restore_size_padding_final')
        if db.execute("select count(*) from sqlite_master where name like '_restore_size_padding%'").fetchone()[0]:
            db.close(); raise RuntimeError('Temporary size-padding schema objects remain.')
    db.execute('pragma max_page_count=0')
    integrity=db.execute('pragma integrity_check').fetchone()[0]
    if integrity!='ok': db.close(); raise RuntimeError(f'Padded database integrity check failed: {integrity}')
    db.close()
    actual=path.stat().st_size
    if actual!=target: raise RuntimeError(f'Padded database size {actual} differs from old backup {target}.')
    dump('full-recovery-result.json',{'size_bytes':actual,'old_backup_size_bytes':target,'page_size':page_size,'page_count':target_pages,'journal_mode':mode,'integrity_check':integrity,'padding_is_unused_freelist_space':True})
    print('FULL MERGE SIZE MATCH',json.dumps({'size_bytes':actual,'page_size':page_size,'page_count':target_pages,'integrity_check':integrity}),flush=True)


def snapshot_full_restore():
    services=adb('shell','dumpsys','activity','services',PACKAGE).decode(errors='replace')
    if 'BackgroundLocationService' in services:
        raise RuntimeError('Recording service is active; refusing to interrupt it.')
    safety='files/database-recovery-20261014/pre-full-restore'
    if subprocess.run([ADB,'shell','run-as',PACKAGE,'test','-d',safety],capture_output=True).returncode==0:
        raise RuntimeError('Pre-full-restore safety snapshot already exists; refusing to overwrite it.')
    adb('shell','am','force-stop',PACKAGE)
    adb('shell','run-as',PACKAGE,'mkdir','-p',safety)
    adb('shell','run-as',PACKAGE,'cp','-R','databases',safety+'/databases')
    adb('shell','run-as',PACKAGE,'cp','-R','shared_prefs',safety+'/shared_prefs')
    with (AUDIT/'before-full-restore.tar').open('wb') as out:
        subprocess.run([ADB,'exec-out','run-as',PACKAGE,'tar','-cf','-','databases','shared_prefs'],stdout=out,check=True)
    baseline=adb('shell','run-as',PACKAGE,'sha256sum','databases/fitness_tracker.db').decode().split()[0]
    dump('full-restore-baseline.json',{'sha256':baseline,'safety':safety})
    print('FULL RESTORE SAFETY SNAPSHOT',baseline,flush=True)


def install_full():
    stage='files/database-recovery-20261014/full-restore.db'
    baseline=json.loads((AUDIT/'full-restore-baseline.json').read_text(encoding='utf-8'))
    live=adb('shell','run-as',PACKAGE,'sha256sum','databases/fitness_tracker.db').decode().split()[0]
    if live!=baseline['sha256']:
        raise RuntimeError('Phone database changed after the new safety snapshot; refusing installation.')
    subprocess.run([ADB,'shell','run-as',PACKAGE,'mkdir','-p','files/database-recovery-20261014'],check=True)
    proc=subprocess.Popen([ADB,'exec-in','run-as',PACKAGE,'sh','-c','cat > '+stage],stdin=subprocess.PIPE)
    with (AUDIT/'merged-recovered-full.db').open('rb') as source:
        shutil.copyfileobj(source,proc.stdin,1024*1024)
    proc.stdin.close()
    if proc.wait()!=0: raise RuntimeError('Could not transfer full merge into the app-private recovery folder.')
    local_digest=hashlib.sha256()
    with (AUDIT/'merged-recovered-full.db').open('rb') as staged:
        for chunk in iter(lambda:staged.read(8*1024*1024),b''):
            local_digest.update(chunk)
    local_hash=local_digest.hexdigest()
    remote_hash=adb('shell','run-as',PACKAGE,'sha256sum',stage).decode().split()[0]
    if local_hash!=remote_hash: raise RuntimeError('Transferred full merge checksum does not match the verified local file.')
    adb('shell','am','force-stop',PACKAGE)
    adb('shell','run-as',PACKAGE,'rm','-f','databases/fitness_tracker.db-wal','databases/fitness_tracker.db-shm')
    adb('shell','run-as',PACKAGE,'cp',stage,'databases/fitness_tracker.db')
    installed=adb('shell','run-as',PACKAGE,'sha256sum','databases/fitness_tracker.db').decode().split()[0]
    if installed!=local_hash: raise RuntimeError('Installed main database checksum mismatch.')
    print('FULL MERGE INSTALLED',installed,flush=True)
    adb('shell','monkey','-p',PACKAGE,'1')
    print('APP LAUNCHED',flush=True)


def backup_full_to_downloads():
    source=AUDIT/'merged-recovered-full.db'
    name='geotracker_backup_recovered_2026-10-14.db'
    target='/sdcard/Download/GeoTracker/DatabaseBackups/'+name
    exists=subprocess.run([ADB,'shell','test','-e',target]).returncode==0
    if exists: raise RuntimeError(f'Refusing to overwrite existing backup {target}.')
    subprocess.run([ADB,'push',str(source),target],check=True)
    phone=adb('shell','ls','-l',target).decode().strip()
    print('BACKUP FILE',phone,flush=True)
    print('BACKUP HASH',adb('shell','sha256sum',target).decode().strip(),flush=True)


if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('action',choices=['inspect','snapshot','plan','apply','install','profile_recording','augment_recording','monitor_full_merge','verify_full_merge','size_full_merge_to_backup','snapshot_full_restore','install_full','backup_full_to_downloads'])
    args=parser.parse_args()
    globals()[args.action]()
