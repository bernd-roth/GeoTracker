'use strict';

// ─────────────────────────────────────────────────────────────
// CONFIG
// ─────────────────────────────────────────────────────────────
const API_BASE        = '/api';
const SESSIONS_PER_PAGE = 50;
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
let sessionPage       = 1;
let allSessions       = [];       // all pages loaded so far
let hasNextPage       = false;
let currentTheme      = localStorage.getItem('theme') || 'light';
let browserVisible    = true;
let lightboxItems     = [];       // current session's media list
let lightboxIndex     = 0;

// ─────────────────────────────────────────────────────────────
// INIT
// ─────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    applyTheme(currentTheme, false);
    initMap();
    loadSessions(1, true);
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

    // Clean up previous track and markers
    if (map.getSource('track')) {
        map.removeLayer('track-line');
        map.removeSource('track');
    }
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
    const q     = document.getElementById('searchInput').value.trim().toLowerCase();
    const sport = document.getElementById('sportFilter').value;

    const filtered = allSessions.filter(s => {
        const name = (s.event_name || '').toLowerCase();
        return (!q || name.includes(q)) && (!sport || s.sport_type === sport);
    });

    renderSessionList(filtered);
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

    const paces        = laps.filter(l => l.pace_min_per_km).map(l => l.pace_min_per_km);
    const fastestPace  = paces.length ? Math.min(...paces) : null;
    const slowestPace  = paces.length ? Math.max(...paces) : null;

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

    const onHover = (_evt, elements) => {
        if (elements && elements.length > 0) updateHoverMarker(elements[0].index);
    };

    const baseOptions = {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        interaction: { mode: 'index', axis: 'x', intersect: false },
        plugins: { legend: { display: false }, tooltip: { enabled: false } },
        onHover,
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
        }
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
        }
    });
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
