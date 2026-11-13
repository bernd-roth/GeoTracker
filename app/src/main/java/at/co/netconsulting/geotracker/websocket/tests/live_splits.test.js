const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const scriptPath = path.join(__dirname, '..', 'static', 'script.js');
const scriptSource = fs.readFileSync(scriptPath, 'utf8');

function loadPage() {
    const jumps = [];
    const markers = [];
    let rows = [];
    const scroll = {
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
    const container = { querySelector() { return scroll; } };
    const speedContainer = {
        querySelector() { return container; },
        querySelectorAll() { return rows; }
    };
    const context = {
        console,
        localStorage: { getItem() { return null; } },
        document: {
            addEventListener() {},
            getElementById() { return speedContainer; },
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
        setInterval() {},
        setTimeout() {},
        recordJump(options) { jumps.push(JSON.parse(JSON.stringify(options))); }
    };
    context.window = context;
    vm.createContext(context);
    vm.runInContext(scriptSource, context, { filename: scriptPath });
    vm.runInContext(`
        map = { jumpTo: recordJump, getZoom() { return 17; } };
        trackPoints = {
            runner: [
                { distance: 0, lat: 48, lng: 16 },
                { distance: 0.99, lat: 48.01, lng: 16.01 },
                { distance: 1.01, lat: 48.02, lng: 16.02 },
                { distance: 2, lat: 48.03, lng: 16.03 }
            ],
            other: [{ distance: 1, lat: 40, lng: 10 }]
        };
    `, context);
    return { context, jumps, markers, getRows: () => rows };
}

test('clicking splits jumps to their starts in the correct session, preserving closer zoom', () => {
    const { context, jumps, getRows } = loadPage();
    vm.runInContext(`updateLapTable('runner', [
        { lapNumber: 1, distance: 1, duration: 300000 },
        { lapNumber: 2, distance: 1, duration: 300000 },
        { lapNumber: 3, distance: 0.2, duration: 60000 }
    ])`, context);
    const rows = getRows();
    assert.equal(rows.length, 3); // Summary is not selectable.
    rows.forEach(row => row.listeners.click());
    assert.deepEqual(jumps, [
        { center: [16, 48], zoom: 17 },
        { center: [16.02, 48.02], zoom: 17 },
        { center: [16.03, 48.03], zoom: 17 }
    ]);
});

test('keyboard selection uses cumulative split distances and zooms in from overview', () => {
    const { context, jumps, getRows } = loadPage();
    vm.runInContext(`
        map.getZoom = () => 10;
        updateLapTable('runner', [
            { lapNumber: 1, distance: 2, duration: 600000 },
            { lapNumber: 2, distance: 0.2, duration: 60000 }
        ]);
    `, context);
    let prevented = 0;
    for (const key of ['Enter', ' ', 'ArrowDown']) {
        getRows()[1].listeners.keydown({ key, preventDefault() { prevented++; } });
    }
    assert.equal(prevented, 2);
    assert.deepEqual(jumps, [{ center: [16.03, 48.03], zoom: 15 }]);
    assert.equal(getRows()[1].attributes['aria-pressed'], 'false');
});

test('split selection places one marker and toggles off through the row or marker', () => {
    const { context, jumps, markers, getRows } = loadPage();
    const render = `updateLapTable('runner', [
        { lapNumber: 1, distance: 1, duration: 300000 },
        { lapNumber: 2, distance: 1, duration: 300000 }
    ])`;
    vm.runInContext(render, context);
    getRows()[0].listeners.click();
    assert.deepEqual(markers[0].coordinates, [16, 48]);
    assert.equal(getRows()[0].attributes['aria-pressed'], 'true');

    // Live table refreshes preserve selection and its toggle behavior.
    vm.runInContext(render, context);
    assert.equal(getRows()[0].attributes['aria-pressed'], 'true');
    getRows()[0].listeners.click();
    assert.equal(markers[0].removed, true);
    assert.equal(jumps.length, 1);
    assert.equal(getRows()[0].attributes['aria-pressed'], 'false');

    getRows()[0].listeners.click();
    getRows()[1].listeners.click();
    assert.equal(markers[1].removed, true);
    assert.deepEqual(markers[2].coordinates, [16.02, 48.02]);
    assert.equal(getRows()[0].attributes['aria-pressed'], 'false');
    assert.equal(getRows()[1].attributes['aria-pressed'], 'true');

    let stopped = false;
    markers[2].element.listeners.click({ stopPropagation() { stopped = true; } });
    assert.equal(stopped, true);
    assert.equal(markers[2].removed, true);
    assert.equal(getRows()[1].attributes['aria-pressed'], 'false');
    assert.equal(vm.runInContext('selectedSplit', context), null);
});

test('missing tracks and invalid points do not cause an invalid map jump', () => {
    const { context, jumps } = loadPage();
    vm.runInContext(`
        jumpToSplitStart('missing', 0);
        jumpToSplitStart('runner', 3);
        jumpToSplitStart('runner', NaN);
        trackPoints.runner = [
            { distance: 1, lat: null, lng: null },
            { distance: 1, lat: 91, lng: 16 },
            { distance: NaN, lat: 48, lng: 16 }
        ];
        jumpToSplitStart('runner', 1);
        map = null;
        jumpToSplitStart('runner', 0);
    `, context);
    assert.deepEqual(jumps, []);
});
