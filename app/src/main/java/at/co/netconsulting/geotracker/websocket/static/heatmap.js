'use strict';

// ─────────────────────────────────────────────────────────────
// CONFIG
// ─────────────────────────────────────────────────────────────
const API_BASE       = '/api';
const MAX_POINTS     = 80000;

// ─────────────────────────────────────────────────────────────
// STATE
// ─────────────────────────────────────────────────────────────
let map          = null;
let currentTheme = localStorage.getItem('theme') || 'light';
let heatmapData  = null;   // GeoJSON FeatureCollection

// ─────────────────────────────────────────────────────────────
// INIT
// ─────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    applyTheme(currentTheme, false);
    initMap();
    loadFilters();
    loadHeatmap();

    // Make heatmap controls panel resizable
    if (window.PanelResize) {
        PanelResize.makeResizable(document.getElementById('heatmapControls'), {
            key: 'heatmap_controls', edges: ['right', 'bottom']
        });
    }
});

// ─────────────────────────────────────────────────────────────
// THEME
// ─────────────────────────────────────────────────────────────
function applyTheme(theme, updateMap = true) {
    currentTheme = theme;
    document.documentElement.setAttribute('data-theme', theme === 'dark' ? 'dark' : '');
    localStorage.setItem('theme', theme);
    document.getElementById('themeBtn').textContent = theme === 'dark' ? '\u2600' : '\u263E';

    if (updateMap && map) {
        const style = mapStyle(theme);
        map.setStyle(style);
        map.once('styledata', () => {
            if (heatmapData) addHeatmapLayer(heatmapData);
        });
    }
}

function toggleTheme() {
    applyTheme(currentTheme === 'dark' ? 'light' : 'dark');
}

function mapStyle(theme) {
    return theme === 'dark'
        ? 'https://tiles.openfreemap.org/styles/dark'
        : 'https://tiles.openfreemap.org/styles/liberty';
}

// ─────────────────────────────────────────────────────────────
// MAP
// ─────────────────────────────────────────────────────────────
function initMap() {
    map = new maplibregl.Map({
        container: 'map',
        style: mapStyle(currentTheme),
        center: [13.4, 47.7],   // Austria default
        zoom: 7,
        attributionControl: false
    });

    map.addControl(new maplibregl.AttributionControl({
        customAttribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }));
    map.addControl(new maplibregl.NavigationControl(), 'bottom-right');
}

// ─────────────────────────────────────────────────────────────
// FILTERS
// ─────────────────────────────────────────────────────────────
async function loadFilters() {
    try {
        const resp = await fetch(`${API_BASE}/heatmap/filters`);
        if (!resp.ok) return;
        const json = await resp.json();

        const sportSel = document.getElementById('sportFilter');
        json.data.sports.forEach(s => {
            const opt = document.createElement('option');
            opt.value = s;
            opt.textContent = s;
            sportSel.appendChild(opt);
        });

        const yearSel = document.getElementById('yearFilter');
        json.data.years.forEach(y => {
            const opt = document.createElement('option');
            opt.value = y;
            opt.textContent = y;
            yearSel.appendChild(opt);
        });
    } catch (err) {
        console.error('loadFilters failed:', err);
    }
}

function onFilterChange() {
    loadHeatmap();
}

// ─────────────────────────────────────────────────────────────
// HEATMAP DATA
// ─────────────────────────────────────────────────────────────
async function loadHeatmap() {
    const sport = document.getElementById('sportFilter').value;
    const year  = document.getElementById('yearFilter').value;

    const params = new URLSearchParams({ max_points: MAX_POINTS });
    if (sport) params.set('sport', sport);
    if (year)  params.set('year', year);

    const status = document.getElementById('statusText');
    status.textContent = 'Loading...';

    try {
        const resp = await fetch(`${API_BASE}/heatmap?${params}`);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const json = await resp.json();

        const points = json.data.points;
        status.textContent = `${json.data.returned.toLocaleString()} of ${json.data.total.toLocaleString()} points`;

        if (points.length === 0) {
            removeHeatmapLayer();
            return;
        }

        heatmapData = {
            type: 'FeatureCollection',
            features: points.map(p => ({
                type: 'Feature',
                geometry: { type: 'Point', coordinates: [p[1], p[0]] }
            }))
        };

        if (map.isStyleLoaded()) {
            addHeatmapLayer(heatmapData);
            fitToBounds(points);
        } else {
            map.once('load', () => {
                addHeatmapLayer(heatmapData);
                fitToBounds(points);
            });
        }

    } catch (err) {
        console.error('loadHeatmap failed:', err);
        status.textContent = 'Failed to load data';
    }
}

function fitToBounds(points) {
    if (points.length === 0) return;

    let minLat = Infinity, maxLat = -Infinity;
    let minLon = Infinity, maxLon = -Infinity;

    for (const p of points) {
        if (p[0] < minLat) minLat = p[0];
        if (p[0] > maxLat) maxLat = p[0];
        if (p[1] < minLon) minLon = p[1];
        if (p[1] > maxLon) maxLon = p[1];
    }

    map.fitBounds(
        [[minLon, minLat], [maxLon, maxLat]],
        { padding: 40, maxZoom: 15 }
    );
}

// ─────────────────────────────────────────────────────────────
// HEATMAP LAYER
// ─────────────────────────────────────────────────────────────
function removeHeatmapLayer() {
    if (map.getLayer('heatmap-heat'))  map.removeLayer('heatmap-heat');
    if (map.getLayer('heatmap-point')) map.removeLayer('heatmap-point');
    if (map.getSource('heatmap'))      map.removeSource('heatmap');
    heatmapData = null;
}

function addHeatmapLayer(geojson) {
    removeHeatmapLayer();

    const radius  = parseFloat(document.getElementById('radiusSlider').value);
    const opacity = parseFloat(document.getElementById('opacitySlider').value);

    map.addSource('heatmap', { type: 'geojson', data: geojson });

    // Heatmap layer (visible at lower zoom)
    map.addLayer({
        id: 'heatmap-heat',
        type: 'heatmap',
        source: 'heatmap',
        maxzoom: 17,
        paint: {
            'heatmap-weight': 1,
            'heatmap-intensity': [
                'interpolate', ['linear'], ['zoom'],
                0, 0.5,
                14, 2,
                17, 3
            ],
            'heatmap-color': [
                'interpolate', ['linear'], ['heatmap-density'],
                0,    'rgba(0, 0, 255, 0)',
                0.1,  '#3b528b',
                0.2,  '#2c728e',
                0.35, '#21918c',
                0.5,  '#27ad81',
                0.65, '#5ec962',
                0.8,  '#fde725',
                0.9,  '#FF9800',
                1,    '#F44336'
            ],
            'heatmap-radius': [
                'interpolate', ['linear'], ['zoom'],
                0,  radius * 0.5,
                8,  radius,
                14, radius * 2,
                17, radius * 3
            ],
            'heatmap-opacity': [
                'interpolate', ['linear'], ['zoom'],
                0,  opacity,
                15, opacity * 0.8,
                17, 0.2
            ]
        }
    });

    // Circle layer (visible at higher zoom as the heatmap fades)
    map.addLayer({
        id: 'heatmap-point',
        type: 'circle',
        source: 'heatmap',
        minzoom: 14,
        paint: {
            'circle-radius': [
                'interpolate', ['linear'], ['zoom'],
                14, 2,
                17, 5
            ],
            'circle-color': '#FF9800',
            'circle-stroke-color': 'white',
            'circle-stroke-width': 0.5,
            'circle-opacity': [
                'interpolate', ['linear'], ['zoom'],
                14, 0,
                15, 0.5,
                17, 0.9
            ]
        }
    });
}

// ─────────────────────────────────────────────────────────────
// SLIDER CONTROLS
// ─────────────────────────────────────────────────────────────
function onRadiusChange(val) {
    document.getElementById('radiusValue').textContent = val;
    if (!map.getLayer('heatmap-heat')) return;
    const r = parseFloat(val);
    map.setPaintProperty('heatmap-heat', 'heatmap-radius', [
        'interpolate', ['linear'], ['zoom'],
        0,  r * 0.5,
        8,  r,
        14, r * 2,
        17, r * 3
    ]);
}

function onOpacityChange(val) {
    document.getElementById('opacityValue').textContent = parseFloat(val).toFixed(1);
    if (!map.getLayer('heatmap-heat')) return;
    const o = parseFloat(val);
    map.setPaintProperty('heatmap-heat', 'heatmap-opacity', [
        'interpolate', ['linear'], ['zoom'],
        0,  o,
        15, o * 0.8,
        17, 0.2
    ]);
}
