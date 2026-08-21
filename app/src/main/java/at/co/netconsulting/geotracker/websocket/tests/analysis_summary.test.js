const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const scriptPath = path.join(__dirname, '..', 'static', 'analysis.js');
const scriptSource = fs.readFileSync(scriptPath, 'utf8');

function assertClose(actual, expected) {
    assert.ok(Math.abs(actual - expected) < 1e-9, `expected ${actual} to be close to ${expected}`);
}

function renderSummary(summary) {
    const summaryContent = { innerHTML: '' };
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
                return id === 'summaryContent' ? summaryContent : null;
            }
        }
    };

    vm.createContext(context);
    vm.runInContext(scriptSource, context, { filename: scriptPath });
    context.summary = summary;
    vm.runInContext('renderSummary(summary)', context);
    return summaryContent.innerHTML;
}

function addTrackAggregatesToSummary(summary, points) {
    const context = {
        console: { log() {}, warn() {}, error() {} },
        localStorage: { getItem() { return null; }, setItem() {} },
        document: { addEventListener() {} }
    };

    vm.createContext(context);
    vm.runInContext(scriptSource, context, { filename: scriptPath });
    context.summary = summary;
    context.points = points;
    return JSON.parse(vm.runInContext(
        'JSON.stringify(addTrackAggregatesToSummary(summary, points))',
        context
    ));
}

test('session summary renders slope, temperature, pressure, and altitude aggregates', () => {
    const html = renderSummary({
        min_slope: -7.25,
        avg_slope: 0,
        max_slope: 11.24,
        min_temperature_c: -3.2,
        avg_temperature_c: 0,
        max_temperature_c: 19.86,
        min_pressure_hpa: 987.64,
        avg_pressure_hpa: 1001.25,
        max_pressure_hpa: 1020,
        min_altitude_m: -4.5,
        avg_altitude_m: 143.26,
        max_altitude_m: 821,
        point_count: 3,
        lap_count: 0,
        variant_count: 1
    });

    const expectedStats = [
        ['Min Slope', '-7.3', '%'],
        ['Avg Slope', '0.0', '%'],
        ['Max Slope', '11.2', '%'],
        ['Min Temp', '-3.2', '&deg;C'],
        ['Avg Temp', '0.0', '&deg;C'],
        ['Max Temp', '19.9', '&deg;C'],
        ['Min Pressure', '987.6', 'hPa'],
        ['Avg Pressure', '1001.3', 'hPa'],
        ['Max Pressure', '1020.0', 'hPa'],
        ['Min Altitude', '-4.5', 'm'],
        ['Avg Altitude', '143.3', 'm'],
        ['Max Altitude', '821.0', 'm']
    ];

    for (const [label, value, unit] of expectedStats) {
        assert.match(html, new RegExp(`>${label}<`));
        assert.match(html, new RegExp(`>${value}<`));
        assert.match(html, new RegExp(`>${unit}<`));
    }
});

test('session summary keeps missing aggregate boxes visible with placeholders', () => {
    const html = renderSummary({
        point_count: 0,
        lap_count: 0,
        variant_count: 1
    });

    for (const label of [
        'Min Slope', 'Avg Slope', 'Max Slope',
        'Min Temp', 'Avg Temp', 'Max Temp',
        'Min Pressure', 'Avg Pressure', 'Max Pressure',
        'Min Altitude', 'Avg Altitude', 'Max Altitude'
    ]) {
        assert.match(html, new RegExp(`>${label}<`));
    }

    assert.equal((html.match(/>--</g) || []).length, 20);
});

test('missing aggregates are calculated from full track data', () => {
    const summary = addTrackAggregatesToSummary(
        { point_count: 3 },
        [
            { alt: 228, slope: -4.2, temperature: 26.7, pressure: 978.88 },
            { alt: 257, slope: 0, temperature: 27.5, pressure: 982.3 },
            { alt: 289, slope: 8.1, temperature: 28.4, pressure: 985.1 }
        ]
    );

    assert.equal(summary.min_altitude_m, 228);
    assert.equal(summary.avg_altitude_m, 258);
    assert.equal(summary.max_altitude_m, 289);
    assert.equal(summary.min_slope, -4.2);
    assertClose(summary.avg_slope, 1.3);
    assert.equal(summary.max_slope, 8.1);
    assert.equal(summary.min_temperature_c, 26.7);
    assertClose(summary.avg_temperature_c, 27.53333333333333);
    assert.equal(summary.max_temperature_c, 28.4);
    assert.equal(summary.min_pressure_hpa, 978.88);
    assertClose(summary.avg_pressure_hpa, 982.0933333333332);
    assert.equal(summary.max_pressure_hpa, 985.1);
});

test('track fallback preserves backend aggregates and ignores invalid pressure', () => {
    const summary = addTrackAggregatesToSummary(
        { min_slope: -9, avg_slope: 2, max_slope: 12 },
        [
            { alt: 100, slope: -1, temperature: 0, pressure: 0 },
            { alt: 120, slope: 1, temperature: 2, pressure: 1000 },
            { alt: null, slope: null, temperature: null, pressure: -1 }
        ]
    );

    assert.equal(summary.min_slope, -9);
    assert.equal(summary.avg_slope, 2);
    assert.equal(summary.max_slope, 12);
    assert.equal(summary.min_temperature_c, 0);
    assert.equal(summary.avg_temperature_c, 1);
    assert.equal(summary.max_temperature_c, 2);
    assert.equal(summary.min_pressure_hpa, 1000);
    assert.equal(summary.avg_pressure_hpa, 1000);
    assert.equal(summary.max_pressure_hpa, 1000);
});
