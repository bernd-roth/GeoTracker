'use strict';

// ─────────────────────────────────────────────────────────────
// CONFIG
// ─────────────────────────────────────────────────────────────
const API_BASE        = '/api';
const SESSIONS_PER_PAGE = 200;
const MAX_TRACK_POINTS  = 3000;

// ─────────────────────────────────────────────────────────────
// STATE
// ─────────────────────────────────────────────────────────────
let map               = null;
let hoverMarker       = null;
let hoverMarkerOnMap  = false;
let currentTrack      = [];       // [{lat,lon,alt,speed,hr,distance,ts,lap,slope,ele_gain}]
let currentSessionId  = null;
let elevChart         = null;
let speedChart        = null;
let hrChart           = null;
let hrAltVisible      = false;
let sessionPage       = 1;
let allSessions       = [];       // all pages loaded so far
let hasNextPage       = false;
let currentTheme      = localStorage.getItem('theme') || 'light';
let browserVisible    = true;
let lightboxItems     = [];       // current session's media list
let lightboxIndex     = 0;
let infoPopup         = null;     // chart hover info popup element
let searchDebounceTimer = null;   // debounce timer for server-side search
let isSearchActive      = false;  // true when showing server search results

// ── Range selection state ───────────────────────────────────
let rangeStartIdx     = null;     // first click index into currentTrack
let rangeEndIdx       = null;     // second click index
let rangeMarkerA      = null;     // map marker for start of range
let rangeMarkerB      = null;     // map marker for end of range

// ─────────────────────────────────────────────────────────────
// INIT
// ─────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    applyTheme(currentTheme, false);
    initMap();
    createInfoPopup();
    loadSessions(1, true);

    // Make panels resizable by dragging edges
    if (window.PanelResize) {
        PanelResize.makeResizable(document.getElementById('sessionBrowser'), {
            key: 'analysis_sessionBrowser', edges: ['right', 'bottom']
        });
        PanelResize.makeResizable(document.getElementById('summaryPanel'), {
            key: 'analysis_summaryPanel', edges: ['left', 'bottom']
        });
        PanelResize.makeChartsResizable(
            document.querySelector('.charts-container'),
            document.getElementById('map'),
            {
                key: 'analysis_chartsHeight',
                onResize: function () {
                    if (map) { map.resize(); }
                    if (elevChart)  elevChart.resize();
                    if (speedChart) speedChart.resize();
                    if (hrChart)    hrChart.resize();
                }
            }
        );
    }
});

// ─────────────────────────────────────────────────────────────
// THEME
// ─────────────────────────────────────────────────────────────
function applyTheme(theme, updateMap = true) {
    currentTheme = theme;
    document.documentElement.setAttribute('data-theme', theme === 'dark' ? 'dark' : '');
    localStorage.setItem('theme', theme);
    document.getElementById('themeBtn').textContent = theme === 'dark' ? '☀' : '☾';

    if (updateMap && map) {
        const style = mapStyle(theme);
        map.setStyle(style);
        map.once('styledata', () => {
            if (currentTrack.length > 0) renderTrackOnMap(currentTrack);
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
        center: [0, 0],
        zoom: 1,
        attributionControl: false
    });

    map.addControl(new maplibregl.AttributionControl({
        customAttribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }));
    map.addControl(new maplibregl.NavigationControl(), 'bottom-right');

    // Hover marker element
    const el = document.createElement('div');
    el.style.cssText = [
        'width:14px', 'height:14px',
        'background:#FF9800', 'border-radius:50%',
        'border:2px solid white',
        'box-shadow:0 2px 6px rgba(0,0,0,0.4)',
        'pointer-events:none'
    ].join(';');
    hoverMarker = new maplibregl.Marker({ element: el, anchor: 'center' }).setLngLat([0, 0]);
}

function renderTrackOnMap(points) {
    if (!points || points.length === 0) return;

    // Clean up previous track, markers, and range segment
    if (map.getSource('track')) {
        map.removeLayer('track-line');
        map.removeSource('track');
    }
    if (map.getLayer('range-line')) map.removeLayer('range-line');
    if (map.getSource('range-segment')) map.removeSource('range-segment');
    if (window._startMarker) { window._startMarker.remove(); window._startMarker = null; }
    if (window._endMarker)   { window._endMarker.remove();   window._endMarker = null; }
    if (hoverMarkerOnMap)    { hoverMarker.remove(); hoverMarkerOnMap = false; }

    map.addSource('track', {
        type: 'geojson',
        data: {
            type: 'Feature',
            geometry: {
                type: 'LineString',
                coordinates: points.map(p => [p.lon, p.lat])
            }
        }
    });

    map.addLayer({
        id: 'track-line',
        type: 'line',
        source: 'track',
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: { 'line-color': '#2196F3', 'line-width': 3 }
    });

    const mkStyle = (color) => {
        const el = document.createElement('div');
        el.style.cssText = `width:12px;height:12px;background:${color};border-radius:50%;border:2px solid white;box-shadow:0 2px 4px rgba(0,0,0,0.3)`;
        return el;
    };
    window._startMarker = new maplibregl.Marker({ element: mkStyle('#4CAF50'), anchor: 'center' })
        .setLngLat([points[0].lon, points[0].lat])
        .addTo(map);
    window._endMarker = new maplibregl.Marker({ element: mkStyle('#F44336'), anchor: 'center' })
        .setLngLat([points[points.length - 1].lon, points[points.length - 1].lat])
        .addTo(map);

    // Fit bounds with padding
    const lons = points.map(p => p.lon);
    const lats = points.map(p => p.lat);
    map.fitBounds(
        [[Math.min(...lons), Math.min(...lats)], [Math.max(...lons), Math.max(...lats)]],
        { padding: 60, maxZoom: 17 }
    );
}

function updateHoverMarker(index) {
    const p = currentTrack[index];
    if (!p) return;
    if (!hoverMarkerOnMap) { hoverMarker.addTo(map); hoverMarkerOnMap = true; }
    hoverMarker.setLngLat([p.lon, p.lat]);
}

// ─────────────────────────────────────────────────────────────
// SESSION LIST
// ─────────────────────────────────────────────────────────────
async function loadSessions(page, reset) {
    try {
        const resp = await fetch(`${API_BASE}/sessions?per_page=${SESSIONS_PER_PAGE}&page=${page}&hide_resets=true&min_total_points=10`);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const json = await resp.json();

        const sessions   = json.data.sessions;
        const pagination = json.data.pagination;

        if (reset) allSessions = [];
        allSessions = allSessions.concat(sessions);
        sessionPage = pagination.page;
        hasNextPage = pagination.has_next;

        updateSportFilter();
        applyFilter();
        document.getElementById('loadMoreBtn').style.display = hasNextPage ? 'block' : 'none';

    } catch (err) {
        console.error('loadSessions failed:', err);
        document.getElementById('sessionList').innerHTML =
            '<p class="no-sessions" style="color:var(--accent-red)">Failed to load sessions.</p>';
    }
}

function loadMoreSessions() {
    loadSessions(sessionPage + 1, false);
}

function updateSportFilter() {
    const sel = document.getElementById('sportFilter');
    const current = sel.value;
    const sports = [...new Set(allSessions.map(s => s.sport_type).filter(Boolean))].sort();
    sel.innerHTML = '<option value="">All sports</option>';
    sports.forEach(sp => {
        const opt = document.createElement('option');
        opt.value = sp;
        opt.textContent = sp;
        if (sp === current) opt.selected = true;
        sel.appendChild(opt);
    });
}

function applyFilter() {
    const q     = document.getElementById('searchInput').value.trim();
    const sport = document.getElementById('sportFilter').value;

    // If there's a text query, debounce a server-side search
    if (q) {
        clearTimeout(searchDebounceTimer);
        searchDebounceTimer = setTimeout(() => searchServer(q, sport), 300);
        return;
    }

    // No text query — filter locally from loaded sessions (by sport only)
    if (isSearchActive) {
        // Was in search mode, returning to normal — restore pagination state
        isSearchActive = false;
        document.getElementById('loadMoreBtn').style.display = hasNextPage ? 'block' : 'none';
    }

    const filtered = allSessions.filter(s => {
        return !sport || s.sport_type === sport;
    });

    renderSessionList(filtered);
}

async function searchServer(query, sport) {
    const list = document.getElementById('sessionList');
    list.innerHTML = '<p class="no-sessions">Searching...</p>';

    try {
        let url = `${API_BASE}/sessions?per_page=100&page=1&hide_resets=true&min_total_points=10&search=${encodeURIComponent(query)}`;
        const resp = await fetch(url);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const json = await resp.json();

        let results = json.data.sessions;

        // Apply sport filter client-side on search results
        if (sport) {
            results = results.filter(s => s.sport_type === sport);
        }

        isSearchActive = true;
        document.getElementById('loadMoreBtn').style.display = 'none';
        renderSessionList(results);
    } catch (err) {
        console.error('searchServer failed:', err);
        list.innerHTML = '<p class="no-sessions" style="color:var(--accent-red)">Search failed.</p>';
    }
}

function renderSessionList(sessions) {
    const container = document.getElementById('sessionList');

    if (sessions.length === 0) {
        container.innerHTML = '<p class="no-sessions">No sessions found.</p>';
        return;
    }

    container.innerHTML = sessions.map(s => {
        const date  = s.start_date_time
            ? new Date(s.start_date_time).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
            : 'Unknown date';
        const name  = escapeHtml(s.event_name || 'Unnamed session');
        const sport = s.sport_type ? `<span class="session-sport">${escapeHtml(s.sport_type)}</span>` : '';
        const city  = s.start_city ? ` · ${escapeHtml(s.start_city)}` : '';
        const active = s.session_id === currentSessionId ? ' active' : '';

        return `<div class="session-item${active}" onclick="selectSession('${escapeHtml(s.session_id)}')">
            <div class="session-item-info">
                <div class="session-item-name">${name}${sport}</div>
                <div class="session-item-time">${date}${city}</div>
            </div>
        </div>`;
    }).join('');
}

// ─────────────────────────────────────────────────────────────
// SESSION SELECTION
// ─────────────────────────────────────────────────────────────
async function selectSession(sessionId) {
    currentSessionId = sessionId;
    // Clear any previous range selection
    rangeStartIdx = null;
    rangeEndIdx = null;
    clearRangeMapMarkers();
    const rp = document.getElementById('rangeStatsPanel');
    if (rp) rp.style.display = 'none';
    applyFilter();  // refresh active state in list

    // Show summary panel with loading state
    const panel = document.getElementById('summaryPanel');
    panel.style.display = 'flex';
    document.getElementById('summaryContent').innerHTML =
        '<p style="text-align:center;padding:20px;color:var(--text-muted);font-size:12px;font-family:Arial,sans-serif">Loading...</p>';
    document.getElementById('lapTableContainer').innerHTML = '';

    try {
        const [trackResp, summaryResp, lapsResp, mediaResp] = await Promise.all([
            fetch(`${API_BASE}/sessions/${sessionId}/track?max_points=${MAX_TRACK_POINTS}&include_resets=true`),
            fetch(`${API_BASE}/sessions/${sessionId}/summary?include_resets=true`),
            fetch(`${API_BASE}/sessions/${sessionId}/laps?include_resets=true`),
            fetch(`${API_BASE}/sessions/${sessionId}/media`)
        ]);

        if (!trackResp.ok || !summaryResp.ok || !lapsResp.ok) {
            throw new Error('One or more API requests failed');
        }

        const [trackJson, summaryJson, lapsJson, mediaJson] = await Promise.all([
            trackResp.json(), summaryResp.json(), lapsResp.json(),
            mediaResp.ok ? mediaResp.json() : Promise.resolve({ data: { media: [] } })
        ]);

        currentTrack = trackJson.data.points;

        if (map.isStyleLoaded()) {
            renderTrackOnMap(currentTrack);
        } else {
            map.once('load', () => renderTrackOnMap(currentTrack));
        }

        const summary = summaryJson.data;
        document.getElementById('summaryTitle').textContent =
            summary.event_name || formatDate(summary.start_date_time) || 'Summary';

        renderSummary(summary);
        renderLaps(lapsJson.data.laps);
        renderCharts(currentTrack);
        renderMedia(mediaJson.data.media);

    } catch (err) {
        console.error('selectSession failed:', err);
        document.getElementById('summaryContent').innerHTML =
            '<p style="text-align:center;padding:20px;color:var(--accent-red);font-size:12px;font-family:Arial,sans-serif">Failed to load session.</p>';
    }
}

function closeSummary() {
    document.getElementById('summaryPanel').style.display = 'none';
}

// ─────────────────────────────────────────────────────────────
// SUMMARY STATS
// ─────────────────────────────────────────────────────────────
function renderSummary(s) {
    const duration = s.duration_ms != null ? formatDuration(s.duration_ms) : '--';
    const pace     = (s.avg_speed_kmh && s.avg_speed_kmh > 0.5)
        ? formatPace(60 / s.avg_speed_kmh) : '--';

    const stat = (label, value, unit, color) =>
        `<div class="stat-box">
            <div class="speed-label">${label}</div>
            <div class="${color}-value" style="font-size:20px">${value}</div>
            <div class="${color}-unit">${unit}</div>
        </div>`;

    document.getElementById('summaryContent').innerHTML = `
        <div class="stats-grid">
            ${stat('Distance',      s.total_distance_km  != null ? s.total_distance_km.toFixed(2)  : '--', 'km',    'elevation')}
            ${stat('Duration',      duration,                                                              '',      'speed')}
            ${stat('Avg Speed',     s.avg_speed_kmh      != null ? s.avg_speed_kmh.toFixed(1)       : '--', 'km/h', 'speed')}
            ${stat('Max Speed',     s.max_speed_kmh      != null ? s.max_speed_kmh.toFixed(1)       : '--', 'km/h', 'speed')}
            ${stat('Elev. Gain',    s.elevation_gain_m   != null ? Math.round(s.elevation_gain_m)   : '--', 'm',    'elevation')}
            ${stat('Avg Pace',      pace,                                                              '/km',   'speed')}
            ${stat('Avg HR',        s.avg_heart_rate     != null ? Math.round(s.avg_heart_rate)     : '--', 'bpm',  'speed')}
            ${stat('Max HR',        s.max_heart_rate     != null ? s.max_heart_rate                 : '--', 'bpm',  'speed')}
        </div>
        <div class="summary-footer">
            ${[s.sport_type, s.start_city, s.start_country].filter(Boolean).join(' · ')}
            <br>${s.point_count} pts · ${s.lap_count} laps${s.variant_count > 1 ? ` · ${s.variant_count} fragments merged` : ''}
        </div>
    `;
}

// ─────────────────────────────────────────────────────────────
// LAPS TABLE
// ─────────────────────────────────────────────────────────────
function renderLaps(laps) {
    const container = document.getElementById('lapTableContainer');
    if (!laps || laps.length === 0) { container.innerHTML = ''; return; }

    // Detect incomplete last lap (distance < 90% of typical lap distance)
    const lastIdx = laps.length - 1;
    let lastLapIncomplete = false;
    if (laps.length >= 2) {
        const allButLast = laps.slice(0, -1);
        const typicalDist = allButLast.reduce((s, l) => s + l.distance_km, 0) / allButLast.length;
        lastLapIncomplete = laps[lastIdx].distance_km < typicalDist * 0.9;
    }

    const eligiblePaces = laps
        .filter((l, i) => l.pace_min_per_km && !(i === lastIdx && lastLapIncomplete))
        .map(l => l.pace_min_per_km);
    const fastestPace  = eligiblePaces.length ? Math.min(...eligiblePaces) : null;
    const slowestPace  = eligiblePaces.length ? Math.max(...eligiblePaces) : null;

    const rows = laps.map(l => {
        let cls = '';
        if (l.pace_min_per_km != null && l.pace_min_per_km === fastestPace) cls = 'fastest-lap';
        else if (l.pace_min_per_km != null && l.pace_min_per_km === slowestPace) cls = 'slowest-lap';
        return `<tr class="${cls}">
            <td>${l.lap_number}</td>
            <td>${l.distance_km.toFixed(2)}</td>
            <td>${formatDuration(l.duration_ms)}</td>
            <td>${l.pace_min_per_km != null ? formatPace(l.pace_min_per_km) : '--'}</td>
        </tr>`;
    }).join('');

    container.innerHTML = `
        <div class="lap-table-container">
            <div class="lap-table-header">
                <h4>Laps</h4>
            </div>
            <div class="lap-table-scroll">
                <table class="lap-table">
                    <thead>
                        <tr><th>#</th><th>km</th><th>Time</th><th>Pace</th></tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>
        </div>`;
}

// ─────────────────────────────────────────────────────────────
// CHARTS
// ─────────────────────────────────────────────────────────────
function renderCharts(points) {
    if (!points || points.length === 0) return;

    const distances = points.map(p => p.distance != null ? +p.distance.toFixed(3) : null);
    const altitudes = points.map(p => p.alt);
    const speeds    = points.map(p => p.speed);

    const axisStyle = {
        ticks: { color: 'var(--text-muted)', font: { size: 10 }, maxTicksLimit: 8 },
        grid:  { color: 'var(--border-light)' }
    };

    const xAxis = {
        ...axisStyle,
        type: 'linear',
        title: { display: true, text: 'Distance (km)', color: 'var(--text-muted)', font: { size: 10 } }
    };

    const onHover = (evt, elements) => {
        if (elements && elements.length > 0) {
            const idx = elements[0].index;
            updateHoverMarker(idx);
            showInfoPopup(evt, currentTrack[idx]);
        } else {
            hideInfoPopup();
        }
    };

    const onClick = (_evt, elements) => {
        if (!elements || elements.length === 0) return;
        const idx = elements[0].index;
        handleRangeClick(idx);
    };

    // Chart.js plugin: draws a shaded rectangle for the selected range
    const rangeHighlightPlugin = {
        id: 'rangeHighlight',
        beforeDraw(chart) {
            if (rangeStartIdx == null) return;
            const endIdx = rangeEndIdx != null ? rangeEndIdx : rangeStartIdx;
            const lo = Math.min(rangeStartIdx, endIdx);
            const hi = Math.max(rangeStartIdx, endIdx);
            if (lo === hi) {
                // Single selection line
                const xScale = chart.scales.x;
                const yScale = chart.scales.y;
                const xPx = xScale.getPixelForValue(distances[lo]);
                const ctx = chart.ctx;
                ctx.save();
                ctx.strokeStyle = '#FF9800';
                ctx.lineWidth = 2;
                ctx.setLineDash([4, 3]);
                ctx.beginPath();
                ctx.moveTo(xPx, yScale.top);
                ctx.lineTo(xPx, yScale.bottom);
                ctx.stroke();
                ctx.restore();
                return;
            }
            const xScale = chart.scales.x;
            const yScale = chart.scales.y;
            const xL = xScale.getPixelForValue(distances[lo]);
            const xR = xScale.getPixelForValue(distances[hi]);
            const ctx = chart.ctx;
            ctx.save();
            ctx.fillStyle = 'rgba(255, 152, 0, 0.15)';
            ctx.fillRect(xL, yScale.top, xR - xL, yScale.bottom - yScale.top);
            // Border lines
            ctx.strokeStyle = '#FF9800';
            ctx.lineWidth = 2;
            ctx.setLineDash([4, 3]);
            ctx.beginPath();
            ctx.moveTo(xL, yScale.top); ctx.lineTo(xL, yScale.bottom);
            ctx.moveTo(xR, yScale.top); ctx.lineTo(xR, yScale.bottom);
            ctx.stroke();
            ctx.restore();
        }
    };

    const baseOptions = {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        interaction: { mode: 'index', axis: 'x', intersect: false },
        plugins: {
            legend: { display: false },
            tooltip: { enabled: false },
            zoom: {
                pan: {
                    enabled: true,
                    mode: 'x',
                    modifierKey: 'ctrl'
                },
                zoom: {
                    wheel: { enabled: true, speed: 0.1 },
                    pinch: { enabled: true },
                    mode: 'x'
                },
                limits: {
                    x: { minRange: 0.05 }
                }
            }
        },
        onHover,
        onClick,
        scales: { x: xAxis }
    };

    // ── Elevation chart
    if (elevChart) elevChart.destroy();
    elevChart = new Chart(document.getElementById('elevChart'), {
        type: 'line',
        data: {
            labels: distances,
            datasets: [{
                data: altitudes,
                borderColor: '#4CAF50',
                backgroundColor: 'rgba(76,175,80,0.15)',
                borderWidth: 1.5,
                pointRadius: 0,
                fill: true
            }]
        },
        options: {
            ...baseOptions,
            scales: {
                x: xAxis,
                y: {
                    ...axisStyle,
                    title: { display: true, text: 'Altitude (m)', color: 'var(--text-muted)', font: { size: 10 } }
                }
            }
        },
        plugins: [rangeHighlightPlugin]
    });

    // ── Speed chart
    if (speedChart) speedChart.destroy();
    speedChart = new Chart(document.getElementById('spdChart'), {
        type: 'line',
        data: {
            labels: distances,
            datasets: [{
                data: speeds,
                borderColor: '#2196F3',
                backgroundColor: 'rgba(33,150,243,0.15)',
                borderWidth: 1.5,
                pointRadius: 0,
                fill: true
            }]
        },
        options: {
            ...baseOptions,
            scales: {
                x: xAxis,
                y: {
                    ...axisStyle,
                    title: { display: true, text: 'Speed (km/h)', color: 'var(--text-muted)', font: { size: 10 } }
                }
            }
        },
        plugins: [rangeHighlightPlugin]
    });

    // ── Heart rate chart (only when HR data exists)
    const heartRates = points.map(p => p.hr || null);
    const hasHR = heartRates.some(v => v != null && v > 0);
    const hrContainer = document.getElementById('hrChartContainer');
    const chartsContainer = document.querySelector('.charts-container');

    if (hrChart) { hrChart.destroy(); hrChart = null; }

    if (hasHR) {
        hrContainer.style.display = '';
        chartsContainer.classList.add('three-charts');
        // Toggle button state
        const toggleBtn = document.getElementById('hrAltToggle');
        if (toggleBtn) toggleBtn.classList.toggle('active', hrAltVisible);

        hrChart = new Chart(document.getElementById('hrChart'), {
            type: 'line',
            data: {
                labels: distances,
                datasets: [
                    {
                        data: heartRates,
                        borderColor: '#F44336',
                        backgroundColor: 'rgba(244,67,54,0.15)',
                        borderWidth: 1.5,
                        pointRadius: 0,
                        fill: true,
                        spanGaps: true,
                        yAxisID: 'y',
                        order: 1
                    },
                    {
                        data: altitudes,
                        borderColor: 'rgba(160,160,160,0.4)',
                        backgroundColor: 'rgba(160,160,160,0.12)',
                        borderWidth: 1,
                        pointRadius: 0,
                        fill: true,
                        yAxisID: 'yAlt',
                        order: 2,
                        hidden: !hrAltVisible
                    }
                ]
            },
            options: {
                ...baseOptions,
                scales: {
                    x: xAxis,
                    y: {
                        ...axisStyle,
                        position: 'left',
                        title: { display: true, text: 'Heart Rate (bpm)', color: 'var(--text-muted)', font: { size: 10 } }
                    },
                    yAlt: {
                        ...axisStyle,
                        position: 'right',
                        display: hrAltVisible,
                        grid: { drawOnChartArea: false },
                        title: { display: true, text: 'Altitude (m)', color: 'rgba(160,160,160,0.7)', font: { size: 10 } }
                    }
                }
            },
            plugins: [rangeHighlightPlugin]
        });
        document.getElementById('hrChart').addEventListener('mouseleave', hideInfoPopup);
    } else {
        hrContainer.style.display = 'none';
        chartsContainer.classList.remove('three-charts');
    }

    // Hide popup when cursor leaves chart area
    document.getElementById('elevChart').addEventListener('mouseleave', hideInfoPopup);
    document.getElementById('spdChart').addEventListener('mouseleave', hideInfoPopup);

    // Double-click any chart to reset zoom
    const resetZoomFor = (chart) => { if (chart && chart.resetZoom) chart.resetZoom(); };
    document.getElementById('elevChart').addEventListener('dblclick', () => resetZoomFor(elevChart));
    document.getElementById('spdChart').addEventListener('dblclick', () => resetZoomFor(speedChart));
    if (hrChart) {
        document.getElementById('hrChart').addEventListener('dblclick', () => resetZoomFor(hrChart));
    }
}

// ─────────────────────────────────────────────────────────────
// MEDIA
// ─────────────────────────────────────────────────────────────
function renderMedia(mediaList) {
    // Remove any previous media section
    const existing = document.querySelector('.media-section');
    if (existing) existing.remove();

    lightboxItems = mediaList || [];
    if (lightboxItems.length === 0) return;

    const thumbsHtml = lightboxItems.map((m, i) => {
        const isVideo  = m.media_type === 'video';
        const thumbSrc = `${API_BASE}/media/${m.media_uuid}/thumbnail`;
        const badge    = isVideo ? '<div class="video-badge">&#9654;</div>' : '';
        const title    = escapeHtml(m.caption || '');
        // onerror: hide broken img and show the video badge at full opacity as fallback
        const onerror  = isVideo
            ? `this.style.display='none';this.nextElementSibling.style.background='rgba(0,0,0,0.6)'`
            : `this.style.display='none'`;
        return `<div class="media-thumb" onclick="openLightbox(${i})" title="${title}">
            <img src="${thumbSrc}" loading="lazy" alt="${title}" onerror="${onerror}" />
            ${badge}
        </div>`;
    }).join('');

    const section = document.createElement('div');
    section.className = 'media-section';
    section.innerHTML = `
        <div class="media-section-header" onclick="toggleMediaSection(this)">
            <span>Media (${lightboxItems.length})</span>
            <span>&#9660;</span>
        </div>
        <div class="media-grid">${thumbsHtml}</div>
    `;

    document.getElementById('summaryContent').appendChild(section);
}

function toggleMediaSection(header) {
    const grid  = header.nextElementSibling;
    const arrow = header.querySelector('span:last-child');
    const hide  = grid.style.display !== 'none';
    grid.style.display  = hide ? 'none' : '';
    arrow.innerHTML     = hide ? '&#9654;' : '&#9660;';
}

function openLightbox(index) {
    lightboxIndex = index;
    renderLightboxItem();
    document.getElementById('lightbox').style.display = 'flex';
    document.addEventListener('keydown', lightboxKeyHandler);
}

function closeLightbox() {
    document.getElementById('lightbox').style.display = 'none';
    document.getElementById('lbContent').innerHTML = '';  // stop video playback
    document.removeEventListener('keydown', lightboxKeyHandler);
}

function lightboxBackdropClick(e) {
    if (e.target === document.getElementById('lightbox')) closeLightbox();
}

function lightboxNav(dir) {
    lightboxIndex = (lightboxIndex + dir + lightboxItems.length) % lightboxItems.length;
    renderLightboxItem();
}

function lightboxKeyHandler(e) {
    if      (e.key === 'Escape')     closeLightbox();
    else if (e.key === 'ArrowRight') lightboxNav(1);
    else if (e.key === 'ArrowLeft')  lightboxNav(-1);
}

function renderLightboxItem() {
    const m = lightboxItems[lightboxIndex];
    if (!m) return;

    const src     = `${API_BASE}/media/${m.media_uuid}`;
    const content = document.getElementById('lbContent');

    // Fallback: if lightbox HTML is missing (e.g. stale cached page), open in new tab
    if (!content) {
        window.open(src, '_blank');
        return;
    }

    if (m.media_type === 'video') {
        content.innerHTML = `<video controls autoplay src="${src}"></video>`;
    } else {
        content.innerHTML = `<img src="${src}" alt="${escapeHtml(m.caption || '')}" />`;
    }

    document.getElementById('lbCaption').textContent = m.caption || '';

    const count   = lightboxItems.length;
    const counter = document.getElementById('lbCounter');
    counter.textContent = count > 1 ? `${lightboxIndex + 1} / ${count}` : '';

    const showNav = count > 1;
    document.getElementById('lbPrev').style.display = showNav ? '' : 'none';
    document.getElementById('lbNext').style.display = showNav ? '' : 'none';
}

// ─────────────────────────────────────────────────────────────
// PANEL TOGGLE
// ─────────────────────────────────────────────────────────────
function toggleBrowser() {
    const content = document.querySelector('#sessionBrowser .panel-content');
    const btn     = document.getElementById('toggleBrowserBtn');
    browserVisible = !browserVisible;
    content.style.display = browserVisible ? 'flex' : 'none';
    btn.textContent = browserVisible ? '-' : '+';
}

// ─────────────────────────────────────────────────────────────
// RANGE SELECTION
// ─────────────────────────────────────────────────────────────
function handleRangeClick(idx) {
    if (rangeStartIdx == null) {
        // First click — set start
        rangeStartIdx = idx;
        rangeEndIdx = null;
        clearRangeMapMarkers();
        clearRangeSegment();
        addRangeMapMarker(idx, 'A');
        redrawCharts();
    } else if (rangeEndIdx == null) {
        // Second click — set end, show stats
        rangeEndIdx = idx;
        addRangeMapMarker(idx, 'B');
        drawRangeSegmentOnMap();
        redrawCharts();
        showRangeStats();
    } else {
        // Third click — reset, start new selection
        clearRangeSelection();
        rangeStartIdx = idx;
        addRangeMapMarker(idx, 'A');
        redrawCharts();
    }
}

function clearRangeSelection() {
    rangeStartIdx = null;
    rangeEndIdx = null;
    clearRangeMapMarkers();
    clearRangeSegment();
    redrawCharts();
}

function redrawCharts() {
    if (elevChart) elevChart.update('none');
    if (speedChart) speedChart.update('none');
    if (hrChart) hrChart.update('none');
}

function toggleHrAltitude() {
    if (!hrChart) return;
    hrAltVisible = !hrAltVisible;
    const btn = document.getElementById('hrAltToggle');
    if (btn) btn.classList.toggle('active', hrAltVisible);
    // Toggle altitude dataset visibility (index 1)
    hrChart.data.datasets[1].hidden = !hrAltVisible;
    // Toggle right y-axis
    hrChart.options.scales.yAlt.display = hrAltVisible;
    hrChart.update();
}

function clearRangeMapMarkers() {
    if (rangeMarkerA) { rangeMarkerA.remove(); rangeMarkerA = null; }
    if (rangeMarkerB) { rangeMarkerB.remove(); rangeMarkerB = null; }
}

function clearRangeSegment() {
    if (map.getLayer('range-line')) map.removeLayer('range-line');
    if (map.getSource('range-segment')) map.removeSource('range-segment');
}

function addRangeMapMarker(idx, label) {
    const p = currentTrack[idx];
    if (!p) return;

    const el = document.createElement('div');
    el.style.cssText = 'width:22px;height:22px;border-radius:50%;border:3px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;font-size:10px;font-weight:bold;color:white;font-family:Arial,sans-serif;';
    el.style.background = label === 'A' ? '#FF9800' : '#E91E63';
    el.textContent = label;

    const marker = new maplibregl.Marker({ element: el, anchor: 'center' })
        .setLngLat([p.lon, p.lat])
        .addTo(map);

    if (label === 'A') rangeMarkerA = marker;
    else rangeMarkerB = marker;
}

function drawRangeSegmentOnMap() {
    clearRangeSegment();
    if (rangeStartIdx == null || rangeEndIdx == null) return;

    const lo = Math.min(rangeStartIdx, rangeEndIdx);
    const hi = Math.max(rangeStartIdx, rangeEndIdx);
    const segment = currentTrack.slice(lo, hi + 1);
    if (segment.length < 2) return;

    map.addSource('range-segment', {
        type: 'geojson',
        data: {
            type: 'Feature',
            geometry: {
                type: 'LineString',
                coordinates: segment.map(p => [p.lon, p.lat])
            }
        }
    });

    map.addLayer({
        id: 'range-line',
        type: 'line',
        source: 'range-segment',
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: { 'line-color': '#FF9800', 'line-width': 5, 'line-opacity': 0.8 }
    });
}

// ── Range stats computation & display ───────────────────────
function showRangeStats() {
    const lo = Math.min(rangeStartIdx, rangeEndIdx);
    const hi = Math.max(rangeStartIdx, rangeEndIdx);
    const slice = currentTrack.slice(lo, hi + 1);
    if (slice.length < 2) return;

    const pA = slice[0];
    const pB = slice[slice.length - 1];

    // Helpers for numeric aggregation
    const vals = (key) => slice.map(p => p[key]).filter(v => v != null);
    const minV = (arr) => arr.length ? Math.min(...arr) : null;
    const maxV = (arr) => arr.length ? Math.max(...arr) : null;
    const avgV = (arr) => arr.length ? arr.reduce((a, b) => a + b, 0) / arr.length : null;
    const fmt  = (v, d) => v != null ? v.toFixed(d) : '--';

    // Distance
    const distA = pA.distance || 0;
    const distB = pB.distance || 0;
    const rangeDist = Math.abs(distB - distA);

    // Duration
    let durationMs = null;
    if (pA.ts && pB.ts) {
        durationMs = Math.abs(new Date(pB.ts) - new Date(pA.ts));
    }

    // Core metrics
    const speeds = vals('speed');
    const alts   = vals('alt');
    const hrs    = vals('hr');
    const slopes = vals('slope');

    // Elevation gain/loss within range
    let elevGain = 0, elevLoss = 0;
    for (let i = 1; i < slice.length; i++) {
        const prev = slice[i - 1].alt, curr = slice[i].alt;
        if (prev != null && curr != null) {
            const diff = curr - prev;
            if (diff > 0) elevGain += diff; else elevLoss += Math.abs(diff);
        }
    }

    // Weather
    const temps     = vals('temperature');
    const humids    = vals('humidity');
    const winds     = vals('wind_speed');
    // Barometer
    const pressures = vals('pressure');
    const seaLevels = vals('sea_level_pressure');
    const baroAlts  = vals('altitude_from_pressure');

    const statRow = (label, min, avg, max, unit) =>
        `<tr>
            <td style="font-weight:600">${label}</td>
            <td>${min}</td>
            <td>${avg}</td>
            <td>${max}</td>
            <td style="color:var(--text-muted)">${unit}</td>
        </tr>`;

    const hasWeather = temps.length > 0 || humids.length > 0 || winds.length > 0;
    const hasBaro    = pressures.length > 0 || seaLevels.length > 0 || baroAlts.length > 0;

    let weatherRows = '';
    if (hasWeather) {
        weatherRows = `
            <tr><td colspan="5" style="font-weight:700;padding-top:10px;color:#FF5722">Weather</td></tr>
            ${temps.length ? statRow('Temperature', fmt(minV(temps),1), fmt(avgV(temps),1), fmt(maxV(temps),1), '\u00B0C') : ''}
            ${humids.length ? statRow('Humidity', fmt(minV(humids),0), fmt(avgV(humids),0), fmt(maxV(humids),0), '%') : ''}
            ${winds.length ? statRow('Wind Speed', fmt(minV(winds),1), fmt(avgV(winds),1), fmt(maxV(winds),1), 'km/h') : ''}`;
    }

    let baroRows = '';
    if (hasBaro) {
        baroRows = `
            <tr><td colspan="5" style="font-weight:700;padding-top:10px;color:#9C27B0">Barometer</td></tr>
            ${pressures.length ? statRow('Pressure', fmt(minV(pressures),1), fmt(avgV(pressures),1), fmt(maxV(pressures),1), 'hPa') : ''}
            ${seaLevels.length ? statRow('Sea Level', fmt(minV(seaLevels),1), fmt(avgV(seaLevels),1), fmt(maxV(seaLevels),1), 'hPa') : ''}
            ${baroAlts.length ? statRow('Baro Alt', fmt(minV(baroAlts),1), fmt(avgV(baroAlts),1), fmt(maxV(baroAlts),1), 'm') : ''}`;
    }

    const timeA = pA.ts ? new Date(pA.ts).toLocaleTimeString() : '--';
    const timeB = pB.ts ? new Date(pB.ts).toLocaleTimeString() : '--';

    const html = `
        <div class="range-stats-header">
            <span>Range Analysis</span>
            <button class="range-stats-close" onclick="closeRangeStats()">\u00D7</button>
        </div>
        <div class="range-stats-body">
            <div class="range-stats-overview">
                <div class="range-stat-card">
                    <div class="range-stat-label">Distance</div>
                    <div class="range-stat-value">${rangeDist.toFixed(2)}<span class="range-stat-unit">km</span></div>
                </div>
                <div class="range-stat-card">
                    <div class="range-stat-label">Duration</div>
                    <div class="range-stat-value">${durationMs != null ? formatDuration(durationMs) : '--'}</div>
                </div>
                <div class="range-stat-card">
                    <div class="range-stat-label">Elev. Gain</div>
                    <div class="range-stat-value">${Math.round(elevGain)}<span class="range-stat-unit">m</span></div>
                </div>
                <div class="range-stat-card">
                    <div class="range-stat-label">Elev. Loss</div>
                    <div class="range-stat-value">${Math.round(elevLoss)}<span class="range-stat-unit">m</span></div>
                </div>
            </div>
            <div class="range-stats-time">
                ${timeA} \u2192 ${timeB} &nbsp;\u00B7&nbsp; ${slice.length} points
            </div>
            <table class="range-stats-table">
                <thead><tr><th></th><th>Min</th><th>Avg</th><th>Max</th><th></th></tr></thead>
                <tbody>
                    ${statRow('Speed', fmt(minV(speeds),1), fmt(avgV(speeds),1), fmt(maxV(speeds),1), 'km/h')}
                    ${statRow('Altitude', fmt(minV(alts),1), fmt(avgV(alts),1), fmt(maxV(alts),1), 'm')}
                    ${hrs.length ? statRow('Heart Rate', fmt(minV(hrs),0), fmt(avgV(hrs),0), fmt(maxV(hrs),0), 'bpm') : ''}
                    ${slopes.length ? statRow('Slope', fmt(minV(slopes),1), fmt(avgV(slopes),1), fmt(maxV(slopes),1), '%') : ''}
                    ${weatherRows}
                    ${baroRows}
                </tbody>
            </table>
        </div>
    `;

    let panel = document.getElementById('rangeStatsPanel');
    if (!panel) {
        panel = document.createElement('div');
        panel.id = 'rangeStatsPanel';
        document.body.appendChild(panel);
    }
    panel.innerHTML = html;
    panel.style.display = 'flex';

    // Register Escape to close
    document.addEventListener('keydown', rangeStatsKeyHandler);
}

function closeRangeStats() {
    const panel = document.getElementById('rangeStatsPanel');
    if (panel) panel.style.display = 'none';
    document.removeEventListener('keydown', rangeStatsKeyHandler);
    clearRangeSelection();
}

function rangeStatsKeyHandler(e) {
    if (e.key === 'Escape') closeRangeStats();
}

// ─────────────────────────────────────────────────────────────
// INFO POPUP
// ─────────────────────────────────────────────────────────────
function createInfoPopup() {
    const existing = document.getElementById('chartInfoPopup');
    if (existing) existing.remove();

    infoPopup = document.createElement('div');
    infoPopup.id = 'chartInfoPopup';
    infoPopup.style.cssText = 'position:fixed;z-index:9999;display:none;pointer-events:none;max-width:320px;line-height:1.4;';
    document.body.appendChild(infoPopup);
}

function hideInfoPopup() {
    if (infoPopup) {
        infoPopup.style.display = 'none';
        infoPopup.classList.remove('popup-visible');
    }
}

function showInfoPopup(event, point) {
    if (!infoPopup || !point) return;

    const timeStr = point.ts ? new Date(point.ts).toLocaleTimeString() : 'N/A';
    const dateStr = point.ts ? new Date(point.ts).toLocaleDateString() : 'N/A';

    const weatherData = extractWeatherData(point);
    const barometerData = extractBarometerData(point);

    let weatherSection = '';
    if (weatherData.hasData || barometerData.hasData) {
        const weatherDescription = getWeatherDescription(weatherData.weatherCode || 0);
        const weatherEmoji = getWeatherEmoji(weatherData.weatherCode || 0);
        const windDirectionText = getWindDirectionText(weatherData.windDirection);
        const weatherTimeFormatted = formatWeatherTime(weatherData.weatherTime);

        weatherSection = `
            <div class="weather-section">
                <div class="weather-header">
                    ${weatherEmoji} Weather & Atmospheric Conditions
                </div>
                <div class="weather-grid">
                    <div class="weather-item">
                        <div class="weather-label" style="color:#FF5722">Temperature</div>
                        <div class="weather-value" style="color:#FF5722;font-weight:bold">
                            ${weatherData.temperature != null ? weatherData.temperature.toFixed(1) + '\u00B0C' : 'N/A'}
                        </div>
                    </div>
                    <div class="weather-item">
                        <div class="weather-label" style="color:#2196F3">Humidity</div>
                        <div class="weather-value" style="color:#2196F3;font-weight:bold">
                            ${weatherData.humidity != null ? weatherData.humidity + '%' : 'N/A'}
                        </div>
                    </div>
                    <div class="weather-item">
                        <div class="weather-label" style="color:#795548">Wind Speed</div>
                        <div class="weather-value" style="color:#795548;font-weight:bold">
                            ${weatherData.windSpeed != null ? weatherData.windSpeed.toFixed(1) + ' km/h' : 'N/A'}
                        </div>
                    </div>
                    <div class="weather-item">
                        <div class="weather-label" style="color:#795548">Wind Dir</div>
                        <div class="weather-value" style="color:#795548;font-weight:bold">
                            ${windDirectionText}
                        </div>
                    </div>`;

        if (barometerData.hasData) {
            weatherSection += `
                    <div class="weather-item">
                        <div class="weather-label" style="color:#9C27B0">Pressure</div>
                        <div class="weather-value" style="color:#9C27B0;font-weight:bold">
                            ${barometerData.pressure != null ? barometerData.pressure.toFixed(1) + ' hPa' : 'N/A'}
                        </div>
                    </div>
                    <div class="weather-item">
                        <div class="weather-label" style="color:#9C27B0">Sea Level</div>
                        <div class="weather-value" style="color:#9C27B0;font-weight:bold">
                            ${barometerData.seaLevelPressure != null ? barometerData.seaLevelPressure.toFixed(1) + ' hPa' : 'N/A'}
                        </div>
                    </div>
                    <div class="weather-item">
                        <div class="weather-label" style="color:#607D8B">Baro Alt</div>
                        <div class="weather-value" style="color:#607D8B;font-weight:bold">
                            ${barometerData.altitudeFromPressure != null ? barometerData.altitudeFromPressure.toFixed(1) + ' m' : 'N/A'}
                        </div>
                    </div>
                    <div class="weather-item">
                        <div class="weather-label" style="color:#607D8B">Accuracy</div>
                        <div class="weather-value" style="color:#607D8B;font-weight:bold">
                            ${barometerData.pressureAccuracy != null ? barometerData.pressureAccuracy + ' Pa' : 'N/A'}
                        </div>
                    </div>`;
        }

        weatherSection += `
                </div>
                ${weatherDescription !== 'Code 0' ? `
                    <div class="weather-footer" style="margin-top:6px;font-size:9px;color:#666;font-style:italic;text-align:center">
                        ${weatherDescription} \u2022 ${weatherTimeFormatted}
                    </div>` : ''}
            </div>`;
    }

    infoPopup.innerHTML = `
        <div style="border-bottom:1px solid #ddd;margin-bottom:6px;padding-bottom:4px">
            <strong>Time:</strong> ${timeStr}<br>
            <strong>Date:</strong> ${dateStr}
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;font-size:11px">
            <div>
                <strong style="color:#2196F3">Distance</strong><br>
                ${point.distance != null ? point.distance.toFixed(2) : '--'} km
            </div>
            <div>
                <strong style="color:#4CAF50">Altitude</strong><br>
                ${point.alt != null ? point.alt.toFixed(1) : '--'} m
            </div>
            <div>
                <strong style="color:#FF9800">Speed</strong><br>
                ${point.speed != null ? point.speed.toFixed(1) : '--'} km/h
            </div>
            <div>
                <strong style="color:${getHeartRateColor(point.hr || 0)}">Heart Rate</strong><br>
                ${point.hr || 0} bpm
            </div>
            <div>
                <strong style="color:${getSlopeColor(point.slope || 0)}">Slope</strong><br>
                ${point.slope != null ? point.slope.toFixed(1) : '0.0'}%
            </div>
        </div>
        ${weatherSection}
        <div style="margin-top:8px;padding-top:6px;border-top:1px solid #ddd;font-size:10px;color:#666">
            Coordinates: ${point.lat.toFixed(6)}, ${point.lon.toFixed(6)}
        </div>
    `;

    // Position near cursor
    let x, y;
    if (event.native) {
        x = event.native.pageX || event.native.clientX + window.scrollX || 0;
        y = event.native.pageY || event.native.clientY + window.scrollY || 0;
    } else {
        x = event.pageX || event.clientX + window.scrollX || 0;
        y = event.pageY || event.clientY + window.scrollY || 0;
    }

    const finalX = Math.max(10, Math.min(x + 15, window.innerWidth - 420));
    const finalY = Math.max(10, Math.min(y - 10, window.innerHeight - 350));

    infoPopup.style.left = finalX + 'px';
    infoPopup.style.top = finalY + 'px';
    infoPopup.style.display = 'block';
    infoPopup.classList.add('popup-visible');

    // Reposition if overflowing
    setTimeout(() => {
        const rect = infoPopup.getBoundingClientRect();
        let newX = finalX, newY = finalY;
        if (rect.right > window.innerWidth) newX = x - rect.width - 15;
        if (rect.bottom > window.innerHeight) newY = y - rect.height + 10;
        if (newX !== finalX || newY !== finalY) {
            infoPopup.style.left = newX + 'px';
            infoPopup.style.top = newY + 'px';
        }
    }, 1);
}

// ── Weather / barometer extraction ──────────────────────────
function extractWeatherData(point) {
    const hasTemperature = point.temperature != null;
    const hasWindSpeed = point.wind_speed != null && point.wind_speed > 0;
    const hasHumidity = point.humidity != null && point.humidity > 0;
    const hasWeatherCode = point.weather_code != null && point.weather_code > 0;
    const hasWindDirection = point.wind_direction != null;

    return {
        hasData: hasTemperature || hasWindSpeed || hasHumidity || hasWeatherCode || hasWindDirection,
        temperature: hasTemperature ? point.temperature : null,
        windSpeed: hasWindSpeed ? point.wind_speed : null,
        windDirection: hasWindDirection ? point.wind_direction : null,
        humidity: hasHumidity ? point.humidity : null,
        weatherCode: hasWeatherCode ? point.weather_code : null,
        weatherTime: point.weather_time || ''
    };
}

function extractBarometerData(point) {
    const hasPressure = point.pressure != null && point.pressure > 0;
    const hasAltFromPressure = point.altitude_from_pressure != null;
    const hasSeaLevel = point.sea_level_pressure != null && point.sea_level_pressure > 0;
    const hasAccuracy = point.pressure_accuracy != null;

    return {
        hasData: hasPressure || hasAltFromPressure || hasSeaLevel || hasAccuracy,
        pressure: hasPressure ? point.pressure : null,
        altitudeFromPressure: hasAltFromPressure ? point.altitude_from_pressure : null,
        seaLevelPressure: hasSeaLevel ? point.sea_level_pressure : null,
        pressureAccuracy: hasAccuracy ? point.pressure_accuracy : null
    };
}

// ── Weather helpers ─────────────────────────────────────────
function getWeatherDescription(code) {
    const map = {
        0:"Clear sky",1:"Mainly clear",2:"Partly cloudy",3:"Overcast",
        45:"Fog",48:"Depositing rime fog",51:"Light drizzle",53:"Moderate drizzle",
        55:"Dense drizzle",61:"Slight rain",63:"Moderate rain",65:"Heavy rain",
        71:"Slight snow",73:"Moderate snow",75:"Heavy snow",77:"Snow grains",
        80:"Slight rain showers",81:"Moderate rain showers",82:"Violent rain showers",
        85:"Slight snow showers",86:"Heavy snow showers",95:"Thunderstorm",
        96:"Thunderstorm with hail",99:"Thunderstorm with heavy hail"
    };
    return map[code] || `Code ${code}`;
}

function getWeatherEmoji(code) {
    const map = {
        0:"\u2600\uFE0F",1:"\uD83C\uDF24\uFE0F",2:"\u26C5",3:"\u2601\uFE0F",
        45:"\uD83C\uDF2B\uFE0F",48:"\uD83C\uDF2B\uFE0F",51:"\uD83C\uDF26\uFE0F",
        53:"\uD83C\uDF26\uFE0F",55:"\uD83C\uDF27\uFE0F",61:"\uD83C\uDF27\uFE0F",
        63:"\uD83C\uDF27\uFE0F",65:"\u26C8\uFE0F",71:"\uD83C\uDF28\uFE0F",
        73:"\u2744\uFE0F",75:"\u2744\uFE0F",77:"\u2744\uFE0F",80:"\uD83C\uDF26\uFE0F",
        81:"\uD83C\uDF27\uFE0F",82:"\u26C8\uFE0F",85:"\uD83C\uDF28\uFE0F",
        86:"\u2744\uFE0F",95:"\u26C8\uFE0F",96:"\u26C8\uFE0F",99:"\u26C8\uFE0F"
    };
    return map[code] || "\uD83C\uDF24\uFE0F";
}

function getWindDirectionText(degrees) {
    if (degrees == null || degrees === 0) return 'N/A';
    const deg = parseFloat(degrees);
    if (isNaN(deg)) return 'N/A';
    const dirs = ["N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW"];
    const norm = ((deg % 360) + 360) % 360;
    return `${dirs[Math.round(norm / 22.5) % 16]} (${norm.toFixed(0)}\u00B0)`;
}

function formatWeatherTime(ts) {
    if (!ts) return 'N/A';
    try { return new Date(ts).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}); }
    catch { return String(ts); }
}

function getHeartRateColor(hr) {
    if (hr <= 0)   return '#999';
    if (hr < 120)  return '#4CAF50';
    if (hr < 150)  return '#FF9800';
    if (hr < 170)  return '#FF5722';
    return '#F44336';
}

function getSlopeColor(slope) {
    const abs = Math.abs(slope);
    if (abs < 3)  return '#4CAF50';
    if (abs < 8)  return '#FF9800';
    if (abs < 15) return '#FF5722';
    return '#F44336';
}

// ─────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────
function formatDuration(ms) {
    const s = Math.floor(ms / 1000);
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    if (h > 0) return `${h}h ${pad(m)}m ${pad(sec)}s`;
    return `${m}m ${pad(sec)}s`;
}

function formatPace(minPerKm) {
    const m = Math.floor(minPerKm);
    const s = Math.round((minPerKm - m) * 60);
    return `${m}:${pad(s)}`;
}

function formatDate(iso) {
    if (!iso) return null;
    return new Date(iso).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

function pad(n) { return String(n).padStart(2, '0'); }

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
