const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const scriptPath = path.join(__dirname, '..', 'static', 'analysis.js');

function loadPage() {
    const markers = [];
    const jumps = [];
    let rows = [];
    const container = {
        set innerHTML(html) {
            rows = [...html.matchAll(/data-start-distance="([^"]+)"/g)].map(match => ({
                dataset: { startDistance: match[1] },
                listeners: {},
                attributes: {},
                setAttribute(name, value) { this.attributes[name] = value; },
                addEventListener(type, listener) { this.listeners[type] = listener; }
            }));
        },
        querySelectorAll() { return rows; }
    };
    const context = {
        console,
        localStorage: { getItem() { return null; } },
        document: {
            addEventListener() {},
            getElementById() { return container; },
            createElement() {
                return {
                    listeners: {},
                    setAttribute() {},
                    addEventListener(type, listener) { this.listeners[type] = listener; }
                };
            }
        },
        maplibregl: {
            Marker: class {
                constructor(options) { this.element = options.element; markers.push(this); }
                setLngLat(coordinates) { this.coordinates = [...coordinates]; return this; }
                addTo() { return this; }
                remove() { this.removed = true; }
            }
        },
        recordJump(options) { jumps.push(JSON.parse(JSON.stringify(options))); }
    };
    vm.createContext(context);
    vm.runInContext(fs.readFileSync(scriptPath, 'utf8'), context, { filename: scriptPath });
    vm.runInContext(`
        map = { jumpTo: recordJump, getZoom() { return 10; } };
        currentTrack = [
            { distance: 0, lat: 48, lon: 16 },
            { distance: 0.99, lat: 48.01, lon: 16.01 },
            { distance: 1.01, lat: 48.02, lon: 16.02 },
            { distance: 2, lat: 48.03, lon: 16.03 }
        ];
        currentLaps = [
            { duration_ms: 300000, distance_km: 1 },
            { duration_ms: 300000, distance_km: 1 },
            { duration_ms: 60000, distance_km: 0.2 }
        ];
        renderLaps(currentLaps);
    `, context);
    return { context, markers, jumps, getRows: () => rows };
}

test('Sessions splits jump to their start, place a marker, and toggle off', () => {
    const { markers, jumps, getRows } = loadPage();
    getRows()[1].listeners.click();
    assert.deepEqual(jumps, [{ center: [16.02, 48.02], zoom: 15 }]);
    assert.deepEqual(markers[0].coordinates, [16.02, 48.02]);
    assert.equal(getRows()[1].attributes['aria-pressed'], 'true');
    getRows()[1].listeners.click();
    assert.equal(markers[0].removed, true);
    assert.equal(getRows()[1].attributes['aria-pressed'], 'false');
    assert.equal(jumps.length, 1);
});

test('switching splits replaces the marker and clicking the marker clears selection', () => {
    const { markers, getRows } = loadPage();
    getRows()[0].listeners.click();
    getRows()[2].listeners.click();
    assert.equal(markers[0].removed, true);
    assert.equal(getRows()[0].attributes['aria-pressed'], 'false');
    assert.deepEqual(markers[1].coordinates, [16.03, 48.03]);
    let stopped = false;
    markers[1].element.listeners.click({ stopPropagation() { stopped = true; } });
    assert.equal(stopped, true);
    assert.equal(markers[1].removed, true);
    assert.equal(getRows()[2].attributes['aria-pressed'], 'false');
});

test('combined splits use the first lap start and support keyboard toggling', () => {
    const { context, markers, jumps, getRows } = loadPage();
    getRows()[0].listeners.click();
    vm.runInContext('setLapGroupSize(2); map.getZoom = () => 18;', context);
    assert.equal(markers[0].removed, true);
    assert.deepEqual(getRows().map(row => row.dataset.startDistance), ['0', '2']);
    let prevented = 0;
    for (const key of ['Enter', ' ', 'ArrowDown']) {
        getRows()[1].listeners.keydown({ key, preventDefault() { prevented++; } });
    }
    assert.equal(prevented, 2);
    assert.deepEqual(jumps.at(-1), { center: [16.03, 48.03], zoom: 18 });
    assert.equal(markers[1].removed, true);
});

test('invalid coordinates and missing track data do not create markers', () => {
    const { context, markers } = loadPage();
    vm.runInContext(`
        jumpToSplitStart(NaN);
        jumpToSplitStart(3);
        currentTrack = [{ distance: 0, lat: null, lon: null }, { distance: 0, lat: 91, lon: 16 }];
        jumpToSplitStart(0);
        currentTrack = [];
        jumpToSplitStart(0);
    `, context);
    assert.equal(markers.length, 0);
});
