const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const scriptPath = path.join(__dirname, '..', 'static', 'script.js');
const scriptSource = fs.readFileSync(scriptPath, 'utf8');

function loadLivePageScript() {
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
            getElementById() { return null; },
            querySelector() { return null; }
        },
        requestAnimationFrame(callback) { callback(); },
        setInterval() { return 1; },
        clearInterval() {},
        setTimeout() { return 1; },
        clearTimeout() {},
        WebSocket: { OPEN: 1, CLOSED: 3 }
    };

    vm.createContext(context);
    vm.runInContext(scriptSource, context, { filename: scriptPath });
    return context;
}

test('line-gradient stops stay strictly increasing with repeated coordinates', () => {
    const context = loadLivePageScript();
    const stops = JSON.parse(vm.runInContext(`JSON.stringify(buildLineGradientStops(
        [[0, 0], [1, 0], [1, 0], [2, 0]],
        [1, 3, 10, 20]
    ))`, context));
    const fractions = stops.filter((_, index) => index % 2 === 0);

    assert.deepEqual(fractions, [0, 0.5, 1]);
    for (let index = 1; index < fractions.length; index++) {
        assert.ok(fractions[index] > fractions[index - 1]);
    }
});

test('stationary tracks use the solid-line fallback', () => {
    const context = loadLivePageScript();
    const stops = JSON.parse(vm.runInContext(`JSON.stringify(buildLineGradientStops(
        [[16.37, 48.21], [16.37, 48.21], [16.37, 48.21]],
        [0, 1, 2]
    ))`, context));

    assert.deepEqual(stops, []);
});

test('a route-layer error after initial load does not replace the map', () => {
    const context = loadLivePageScript();
    const fallbackCalls = vm.runInContext(`
        fallbackCalls = 0;
        initOSMRasterMap = () => { fallbackCalls += 1; };
        initCharts = () => {};
        connectToWebSocket = () => {};
        maplibregl = {
            AttributionControl: function AttributionControl() {},
            Map: function Map() {
                const handlers = {};
                const onceHandlers = {};
                const instance = {
                    on(type, callback) {
                        handlers[type] = handlers[type] || [];
                        handlers[type].push(callback);
                    },
                    once(type, callback) {
                        onceHandlers[type] = callback;
                    },
                    emit(type, event = {}) {
                        (handlers[type] || []).forEach(callback => callback(event));
                        if (onceHandlers[type]) {
                            const callback = onceHandlers[type];
                            delete onceHandlers[type];
                            callback(event);
                        }
                    },
                    addControl() {}
                };
                globalThis.fakeVectorMap = instance;
                return instance;
            }
        };

        initMap();
        fakeVectorMap.emit('load');
        fakeVectorMap.emit('error', { message: 'invalid route gradient' });
        fallbackCalls;
    `, context);

    assert.equal(fallbackCalls, 0);
});

test('the raster fallback redraws retained displayable tracks', () => {
    const context = loadLivePageScript();
    const redrawn = JSON.parse(vm.runInContext(`
        trackPoints = {
            retained_session: [{}],
            retained_session_reset_123: [{}],
            retained_session_archived_456: [{}]
        };
        redrawnSessionIds = [];
        updateMapTrack = sessionId => redrawnSessionIds.push(sessionId);
        redrawStoredMapTracks();
        JSON.stringify(redrawnSessionIds);
    `, context));

    assert.deepEqual(redrawn, ['retained_session']);
});
