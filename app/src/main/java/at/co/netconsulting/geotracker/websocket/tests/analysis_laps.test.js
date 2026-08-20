const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const scriptPath = path.join(__dirname, '..', 'static', 'analysis.js');
const scriptSource = fs.readFileSync(scriptPath, 'utf8');

function groupLaps(laps, groupSize) {
    const context = {
        console: { log() {}, warn() {}, error() {} },
        localStorage: { getItem() { return null; }, setItem() {} },
        document: { addEventListener() {} }
    };

    vm.createContext(context);
    vm.runInContext(scriptSource, context, { filename: scriptPath });
    context.laps = laps;
    context.groupSize = groupSize;
    return JSON.parse(vm.runInContext('JSON.stringify(groupLaps(laps, groupSize))', context));
}

function renderLaps(laps, groupSize) {
    const lapTableContainer = { innerHTML: '' };
    const context = {
        console: { log() {}, warn() {}, error() {} },
        localStorage: { getItem() { return null; }, setItem() {} },
        document: {
            addEventListener() {},
            getElementById(id) {
                return id === 'lapTableContainer' ? lapTableContainer : null;
            }
        }
    };

    vm.createContext(context);
    vm.runInContext(scriptSource, context, { filename: scriptPath });
    context.laps = laps;
    context.groupSize = groupSize;
    vm.runInContext(
        'currentLaps = laps; renderLaps(currentLaps); setLapGroupSize(groupSize)',
        context
    );
    return lapTableContainer.innerHTML;
}

test('groups consecutive laps and calculates pace from combined time and distance', () => {
    const result = groupLaps([
        { duration_ms: 300000, distance_km: 1 },
        { duration_ms: 330000, distance_km: 1 },
        { duration_ms: 720000, distance_km: 2 }
    ], 2);

    assert.equal(result.length, 2);
    assert.deepEqual(result[0], {
        lap_number: 1,
        lap_label: '1-2',
        source_lap_count: 2,
        duration_ms: 630000,
        distance_km: 2,
        pace_min_per_km: 5.25,
        is_incomplete: false
    });
    assert.equal(result[1].lap_label, '3');
    assert.equal(result[1].duration_ms, 720000);
    assert.equal(result[1].distance_km, 2);
    assert.equal(result[1].pace_min_per_km, 6);
    assert.equal(result[1].is_incomplete, true);
});

test('uses actual distance when calculating the pace of non-kilometre laps', () => {
    const result = groupLaps([
        { duration_ms: 3600000, distance_km: 6.706 },
        { duration_ms: 3300000, distance_km: 6.706 }
    ], 2);

    assert.equal(result[0].distance_km, 13.412);
    assert.ok(Math.abs(result[0].pace_min_per_km - (115 / 13.412)) < 1e-12);
});

test('marks a short final recorded lap as incomplete even when it fills a group', () => {
    const result = groupLaps([
        { duration_ms: 300000, distance_km: 1 },
        { duration_ms: 300000, distance_km: 1 },
        { duration_ms: 60000, distance_km: 0.2 }
    ], 1);

    assert.equal(result[0].is_incomplete, false);
    assert.equal(result[1].is_incomplete, false);
    assert.equal(result[2].is_incomplete, true);
});

test('falls back to individual laps for an invalid group size', () => {
    const result = groupLaps([
        { duration_ms: 300000, distance_km: 1 },
        { duration_ms: 310000, distance_km: 1 }
    ], 0);

    assert.equal(result.length, 2);
    assert.equal(result[0].lap_label, '1');
    assert.equal(result[0].is_incomplete, false);
});

test('renders the selected group, combined values, and accessible dropdown', () => {
    const html = renderLaps([
        { duration_ms: 300000, distance_km: 1 },
        { duration_ms: 330000, distance_km: 1 },
        { duration_ms: 359999, distance_km: 1 }
    ], 2);

    assert.match(html, /aria-label="Laps per calculated split"/);
    assert.match(html, /<option value="2" selected>2 laps<\/option>/);
    assert.match(html, /<td>1-2<\/td>\s*<td>2\.00<\/td>\s*<td>10m 30s<\/td>\s*<td>5:15<\/td>/);
    assert.match(html, /<td>3<\/td>\s*<td>1\.00<\/td>\s*<td>5m 59s<\/td>\s*<td>6:00<\/td>/);
});
