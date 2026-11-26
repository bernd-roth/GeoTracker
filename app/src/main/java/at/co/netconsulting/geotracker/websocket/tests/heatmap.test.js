const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const scriptPath = path.join(__dirname, '..', 'static', 'heatmap.js');

test('heatmap redraws both layers after theme changes and honors filter results during a switch', async () => {
    const elements = {
        themeBtn: {},
        sportFilter: { value: '' },
        yearFilter: { value: '' },
        statusText: {},
        radiusSlider: { value: '10' },
        opacitySlider: { value: '0.8' }
    };
    let map;
    const requests = [];
    class MapStub extends EventEmitter {
        constructor({ style }) {
            super();
            map = this;
            this.style = style;
            this.sources = new Map();
            this.layers = new Map();
            this.fitCount = 0;
        }

        setStyle(style, { diff }) {
            assert.equal(diff, false);
            this.style = style;
            this.sources.clear();
            this.layers.clear();
            this.ready = false;
            this.emit('styledata');
        }

        finishStyleLoad() {
            this.ready = true;
            this.emit('style.load');
        }

        addControl() {}
        getSource(id) { return this.sources.get(id); }
        addSource(id, source) {
            assert.ok(this.ready);
            this.sources.set(id, { ...source, setData(data) { this.data = data; } });
        }
        removeSource(id) { this.sources.delete(id); }
        addLayer(layer) { this.layers.set(layer.id, layer); }
        removeLayer(id) { this.layers.delete(id); }
        fitBounds() { this.fitCount++; }
    }

    const context = vm.createContext({
        document: {
            addEventListener() {},
            documentElement: { setAttribute() {} },
            getElementById(id) { return elements[id]; }
        },
        localStorage: { getItem: () => null, setItem() {} },
        maplibregl: { Map: MapStub, AttributionControl: class {}, NavigationControl: class {} },
        URLSearchParams,
        fetch() { return new Promise(resolve => requests.push(resolve)); }
    });
    vm.runInContext(fs.readFileSync(scriptPath, 'utf8'), context, { filename: scriptPath });
    context.initMap();

    function respond(request, points) {
        requests[request]({
            ok: true,
            json: async () => ({ data: { points, returned: points.length, total: points.length } })
        });
    }

    function assertLayers(latitude, longitude) {
        assert.equal(map.layers.get('heatmap-heat').type, 'heatmap');
        assert.equal(map.layers.get('heatmap-point').type, 'circle');
        const feature = map.getSource('heatmap').data.features[0];
        assert.deepEqual(Array.from(feature.geometry.coordinates), [longitude, latitude]);
    }

    const initial = context.loadHeatmap();
    respond(0, [[47, 13]]);
    await initial;
    assert.equal(map.layers.size, 0);
    map.finishStyleLoad();
    assertLayers(47, 13);

    context.toggleTheme();
    assert.match(map.style, /\/dark$/);
    assert.equal(map.layers.size, 0);
    map.finishStyleLoad();
    assertLayers(47, 13);
    context.toggleTheme();
    assert.match(map.style, /\/liberty$/);
    map.finishStyleLoad();
    assertLayers(47, 13);
    assert.equal(map.fitCount, 1);
    assert.equal(requests.length, 1);

    const pending = context.loadHeatmap();
    context.toggleTheme();
    context.toggleTheme();
    const filtered = context.loadHeatmap();
    respond(2, [[48, 16]]);
    await filtered;
    respond(1, [[47, 13]]);
    await pending;
    map.finishStyleLoad();
    assertLayers(48, 16);

    const empty = context.loadHeatmap();
    respond(3, []);
    await empty;
    assert.equal(map.layers.size, 0);
    assert.equal(map.sources.size, 0);
    context.toggleTheme();
    map.finishStyleLoad();
    assert.equal(map.layers.size, 0);
});
