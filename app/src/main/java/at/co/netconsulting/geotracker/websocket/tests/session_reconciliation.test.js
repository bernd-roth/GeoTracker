const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const scriptPath = path.join(__dirname, '..', 'static', 'script.js');
const scriptSource = fs.readFileSync(scriptPath, 'utf8');

function loadLivePageScript() {
    const sessionsList = { innerHTML: '' };
    const context = {
        console: {
            log() {},
            warn() {},
            error() {}
        },
        localStorage: {
            getItem() { return null; },
            setItem() {}
        },
        document: {
            addEventListener() {},
            getElementById(id) {
                return id === 'sessionsList' ? sessionsList : null;
            },
            querySelector() { return null; }
        },
        requestAnimationFrame(callback) { callback(); },
        setInterval() { return 1; },
        clearInterval() {},
        setTimeout() { return 1; },
        clearTimeout() {},
        WebSocket: { OPEN: 1, CLOSED: 3 }
    };
    context.window = context;

    vm.createContext(context);
    vm.runInContext(scriptSource, context, { filename: scriptPath });
    vm.runInContext(`
        map = {
            getLayer() { return false; },
            removeLayer() {},
            getSource() { return false; },
            removeSource() {}
        };
        altitudeChart = { data: { datasets: [] }, update() {}, setDatasetVisibility() {} };
        speedChart = { data: { datasets: [] }, update() {}, setDatasetVisibility() {} };
        hrChart = { data: { datasets: [] }, update() {}, setDatasetVisibility() {} };
    `, context);

    return { context, sessionsList };
}

function readState(context) {
    return JSON.parse(vm.runInContext(`JSON.stringify({
        trackSessionIds: Object.keys(trackPoints),
        availableSessionIds: availableSessions.map(session => session.sessionId),
        altitudeSessionIds: altitudeChart.data.datasets.map(dataset => dataset.sessionId),
        speedSessionIds: speedChart.data.datasets.map(dataset => dataset.sessionId),
        heartRateSessionIds: hrChart.data.datasets.map(dataset => dataset.sessionId)
    })`, context));
}

test('an empty authoritative session list removes the matching graphs and local session', () => {
    const { context, sessionsList } = loadLivePageScript();

    vm.runInContext(`
        trackPoints = { expired_session: [{ personName: 'Bernd' }] };
        speedHistory = { expired_session: { lastUpdate: new Date() } };
        availableSessions = [{ sessionId: 'expired_session', person: 'Bernd' }];
        altitudeChart.data.datasets = [{ sessionId: 'expired_session', label: 'Altitude' }];
        speedChart.data.datasets = [{ sessionId: 'expired_session', label: 'Speed' }];
        hrChart.data.datasets = [{ sessionId: 'expired_session', label: 'Heart rate' }];

        handleSessionList([]);
    `, context);

    assert.deepEqual(readState(context), {
        trackSessionIds: [],
        availableSessionIds: [],
        altitudeSessionIds: [],
        speedSessionIds: [],
        heartRateSessionIds: []
    });
    assert.match(sessionsList.innerHTML, /No sessions found/);
});

test('local history enriches only sessions that still exist in the server snapshot', () => {
    const { context } = loadLivePageScript();

    const result = JSON.parse(vm.runInContext(`
        trackPoints = {
            retained_session: [{ personName: 'Bernd', sportType: 'Running' }],
            expired_session: [{ personName: 'Bernd', sportType: 'Cycling' }]
        };
        JSON.stringify(mergeServerSessionsWithLiveTracks([
            { sessionId: 'retained_session', isActive: false }
        ]).map(session => ({
            sessionId: session.sessionId,
            sportType: session.sportType,
            isActive: session.isActive
        })));
    `, context));

    assert.deepEqual(result, [{
        sessionId: 'retained_session',
        sportType: 'Running',
        isActive: false
    }]);
});

test('saved names survive stale historical and live tracking points', () => {
    const { context, sessionsList } = loadLivePageScript();
    vm.runInContext(`
        trackPoints = { retained: [{ eventName: 'Old name' }] };
        handleSessionList([{ sessionId: 'retained', eventName: 'New name', savedEventName: 'New name' }]);
        ensureSessionAvailable('retained', { eventName: 'Old name' });
    `, context);
    assert.equal(vm.runInContext('availableSessions[0].eventName', context), 'New name');
    assert.match(sessionsList.innerHTML, /New name/);
});

test('name refresh updates retained sessions without restoring expired sessions', () => {
    const { context, sessionsList } = loadLivePageScript();
    vm.runInContext(`
        availableSessions = [{ sessionId: 'retained', eventName: 'Old name' }];
        handleSessionNames({ retained: 'Renamed', expired: 'Should not reappear' });
        ensureSessionAvailable('retained', { eventName: 'Old name' });
    `, context);
    assert.deepEqual(readState(context).availableSessionIds, ['retained']);
    assert.equal(vm.runInContext('availableSessions[0].eventName', context), 'Renamed');
    assert.match(sessionsList.innerHTML, /Renamed/);
    assert.doesNotMatch(sessionsList.innerHTML, /Should not reappear/);
});

test('blank saved names and reset fragments do not revert to cached names', () => {
    const { context } = loadLivePageScript();
    vm.runInContext(`
        availableSessions = [{ sessionId: 'retained', eventName: 'Old name', savedEventName: 'Old name' }];
        handleSessionNames({ retained_reset_123: 'Fragment name', retained: '' });
        ensureSessionAvailable('retained_reset_123', { eventName: 'Old name' });
    `, context);
    assert.equal(vm.runInContext('availableSessions[0].eventName', context), '');
    const name = vm.runInContext(`mergeSessionInfo(
        { sessionId: 'retained', savedEventName: 'Old name' },
        { sessionId: 'retained', savedEventName: '', eventName: '' }
    ).eventName`, context);
    assert.equal(name, '');
});

test('pressure normalization treats zero sentinels as missing', () => {
    const { context } = loadLivePageScript();

    const result = JSON.parse(vm.runInContext(`
        JSON.stringify([
            normalizePressure(undefined),
            normalizePressure(null),
            normalizePressure(0),
            normalizePressure('0.00'),
            normalizePressure(-1),
            normalizePressure('978.57')
        ]);
    `, context));

    assert.deepEqual(result, [null, null, null, null, null, 978.57]);
});

test('historical pressure statistics exclude zero sentinels', () => {
    const { context } = loadLivePageScript();

    const stats = JSON.parse(vm.runInContext(`
        handleHistoryBatch([
            {
                sessionId: 'pressure-session',
                timestamp: '21-08-2026 18:47:01',
                latitude: 48.1817,
                longitude: 16.3606,
                distance: 1,
                pressure: 0
            },
            {
                sessionId: 'pressure-session',
                timestamp: '21-08-2026 18:47:02',
                latitude: 48.1817,
                longitude: 16.3606,
                distance: 2,
                pressure: 978
            },
            {
                sessionId: 'pressure-session',
                timestamp: '21-08-2026 18:47:03',
                latitude: 48.1817,
                longitude: 16.3606,
                distance: 3,
                pressure: 979
            }
        ]);
        JSON.stringify({
            pressures: speedHistory['pressure-session'].pressures,
            min: speedHistory['pressure-session'].minPressure,
            avg: speedHistory['pressure-session'].avgPressure,
            max: speedHistory['pressure-session'].maxPressure
        });
    `, context));

    assert.deepEqual(stats, {
        pressures: [978, 979],
        min: 978,
        avg: 978.5,
        max: 979
    });
});
