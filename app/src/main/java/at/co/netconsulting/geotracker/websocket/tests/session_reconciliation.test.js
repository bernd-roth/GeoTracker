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
        hrAltitudeChart = { data: { datasets: [] }, update() {}, setDatasetVisibility() {} };
    `, context);

    return { context, sessionsList };
}

function readState(context) {
    return JSON.parse(vm.runInContext(`JSON.stringify({
        trackSessionIds: Object.keys(trackPoints),
        availableSessionIds: availableSessions.map(session => session.sessionId),
        altitudeSessionIds: altitudeChart.data.datasets.map(dataset => dataset.sessionId),
        speedSessionIds: speedChart.data.datasets.map(dataset => dataset.sessionId),
        heartRateSessionIds: hrChart.data.datasets.map(dataset => dataset.sessionId),
        heartRateAltitudeSessionIds: hrAltitudeChart.data.datasets.map(dataset => dataset.sessionId)
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
        hrAltitudeChart.data.datasets = [{ sessionId: 'expired_session', label: 'Heart rate' }, { sessionId: 'expired_session', label: 'Altitude' }];

        handleSessionList([]);
    `, context);

    assert.deepEqual(readState(context), {
        trackSessionIds: [],
        availableSessionIds: [],
        altitudeSessionIds: [],
        speedSessionIds: [],
        heartRateSessionIds: [],
        heartRateAltitudeSessionIds: []
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


test('live heart rate stats use valid history and live samples without counting a replay twice', () => {
    const { context, sessionsList } = loadLivePageScript();
    const min = {};
    const avg = {};
    const panel = { style: {}, querySelector() { return null; } };
    const elements = {
        sessionsList,
        speedDisplay: panel,
        'speed-container-example': panel,
        'minHeartRate-example': min,
        'avgHeartRate-example': avg
    };
    context.document.getElementById = id => elements[id] || null;
    vm.runInContext(`
        shouldDisplaySession = () => true;
        updateMapTrack = () => {};
        updateCharts = () => {};
        const sample = (second, heartRate) => ({
            sessionId: 'example',
            latitude: 48.2,
            longitude: 16.3,
            distance: second * 10,
            timestamp: '21-08-2026 18:47:0' + second,
            heartRate
        });
        handleHistoryBatch([sample(0, 0), sample(1)]);
        finalizeBatchProcessing();
    `, context);
    assert.equal(min.textContent, '--');
    assert.equal(avg.textContent, '--');

    vm.runInContext(`
        handleHistoryBatch([sample(2, 101), sample(3, 126)]);
        finalizeBatchProcessing();
    `, context);
    assert.equal(min.textContent, '101');
    assert.equal(avg.textContent, '114');

    panel.style.display = 'none';
    vm.runInContext(`
        statsVisible = false;
        handlePoint(sample(4, 94));
        handleHistoryBatch([sample(4, 94), sample(5, 'invalid')]);
        finalizeBatchProcessing();
    `, context);
    assert.equal(min.textContent, '94');
    assert.equal(avg.textContent, '107');
});

test('Session Manager swatches match the speed and altitude graphs for each run', () => {
    const { context, sessionsList } = loadLivePageScript();
    vm.runInContext(`
        const points = [
            { lat: 48.2, lng: 16.3, distance: 0, altitude: 200, speed: 8 },
            { lat: 48.201, lng: 16.301, distance: 0.15, altitude: 210, speed: 9 }
        ];
        trackPoints.run_a = points;
        trackPoints.run_b = points;
        updateCharts('run_a');
        handleSessionList([{ sessionId: 'run_a' }, { sessionId: 'run_b_reset_123', isActive: true }]);
        updateCharts('run_b_reset_123');
    `, context);

    const matches = sessionsList.innerHTML.matchAll(
        /data-session-id="([^"]+)">\s*<span class="session-color-swatch" style="background-color: (rgb\([^"]+\))"/g
    );
    const swatches = Object.fromEntries(Array.from(matches, ([, sessionId, color]) => [sessionId, color]));
    for (const chart of ['speedChart', 'altitudeChart']) {
        const colors = JSON.parse(vm.runInContext(`JSON.stringify(Object.fromEntries(
            ${chart}.data.datasets.map(dataset => [dataset.sessionId, dataset.borderColor])
        ))`, context));
        assert.deepEqual(Object.keys(colors).sort(), ['run_a', 'run_b']);
        assert.deepEqual(swatches, colors);
    }
    assert.notEqual(swatches.run_a, swatches.run_b);
});

test('heart rate and altitude use a fourth graph only when HR samples exist', () => {
    const { context } = loadLivePageScript();
    const classes = new Set();
    const containers = {
        hrChartContainer: { style: {} },
        hrAltitudeChartContainer: { style: {} }
    };
    context.document.querySelector = () => ({ classList: {
        remove(name) { classes.delete(name); },
        toggle(name, enabled) { enabled ? classes.add(name) : classes.delete(name); }
    } });
    const originalGetElement = context.document.getElementById;
    context.document.getElementById = id => containers[id] || originalGetElement(id);
    vm.runInContext(`
        shouldDisplaySession = () => true;
        trackPoints.example = [
            { lat: 48.2, lng: 16.3, distance: 0, altitude: 200, speed: 8, heartRate: 0 },
            { lat: 48.201, lng: 16.301, distance: 0.15, altitude: 210, speed: 9, heartRate: 0 },
            { lat: 48.202, lng: 16.302, distance: 0.3, altitude: 220, speed: 10, heartRate: 0 }
        ];
        updateCharts('example');
    `, context);
    assert.equal(containers.hrAltitudeChartContainer.style.display, 'none');
    assert.equal(classes.has('four-charts'), false);

    vm.runInContext(`
        trackPoints.example[0].heartRate = 110;
        trackPoints.example[2].heartRate = 130;
        updateCharts('example');
        updateCharts('example');
    `, context);
    const series = JSON.parse(vm.runInContext(`JSON.stringify({
        hr: hrChart.data.datasets,
        combined: hrAltitudeChart.data.datasets
    })`, context));
    assert.equal(series.hr.length, 1);
    assert.equal(series.hr[0].yAxisID, 'y');
    assert.equal(series.combined.length, 2);
    assert.deepEqual(series.combined[0].data, [{ x: 0, y: 110 }, { x: 0.3, y: 130 }]);
    assert.deepEqual(series.combined[1].data, [{ x: 0, y: 200 }, { x: 0.15, y: 210 }, { x: 0.3, y: 220 }]);
    assert.equal(series.combined[1].yAxisID, 'yAlt');
    assert.equal(series.combined[1].hidden, false);
    assert.equal(classes.has('four-charts'), true);
    assert.equal(containers.hrAltitudeChartContainer.style.display, '');

    vm.runInContext(`removeSessionFromDisplays('example');`, context);
    assert.equal(vm.runInContext('hrAltitudeChart.data.datasets.length', context), 0);
    assert.equal(containers.hrAltitudeChartContainer.style.display, 'none');
    assert.equal(classes.has('four-charts'), false);
});
