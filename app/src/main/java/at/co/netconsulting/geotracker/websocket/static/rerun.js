'use strict';

// ─────────────────────────────────────────────────────────────
// CONFIG
// ─────────────────────────────────────────────────────────────
const API_BASE          = '/api';
const SESSIONS_PER_PAGE = 200;
// We want a smooth animation along the recorded path, so request
// many points. 0 means "all points" but we cap to keep memory sane.
const MAX_TRACK_POINTS  = 8000;

// ─────────────────────────────────────────────────────────────
// STATE
// ─────────────────────────────────────────────────────────────
let map               = null;
let currentTrack      = [];     // [{lat,lon,alt,speed,hr,distance,ts,...}]
let currentSessionId  = null;
let currentTheme      = localStorage.getItem('theme') || 'light';
let browserVisible    = true;

let sessionPage       = 1;
let allSessions       = [];
let hasNextPage       = false;
let searchDebounceTimer = null;
let isSearchActive      = false;

// ── Playback state ──────────────────────────────────────────
let playbackIndex     = 0;          // current point index
let isPlaying         = false;
let playbackSpeed     = 10;         // multiplier of real time
let playbackTimer     = null;       // setTimeout handle
let runnerMarker      = null;       // moving marker
let playedSourceId    = 'played-track';
let trackSourceId     = 'track';

// ── Camera state ────────────────────────────────────────────
// 'overview' = static fit-bounds view (default)
// 'follow'   = pan to runner, keep zoom
// 'flyover'  = close zoom + pitch + rotate to bearing of travel
let cameraMode        = 'overview';
let userInteracting   = false;      // suspend follow while user drags map

// ─────────────────────────────────────────────────────────────
// INIT
// ─────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    applyTheme(currentTheme, false);
    initMap();
    loadSessions(1, true);

    if (window.PanelResize) {
        PanelResize.makeResizable(document.getElementById('sessionBrowser'), {
            key: 'rerun_sessionBrowser', edges: ['right', 'bottom']
        });
    }

    document.addEventListener('keydown', (e) => {
        if (currentTrack.length === 0) return;
        if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT')) return;
        if (e.code === 'Space') { e.preventDefault(); togglePlay(); }
        else if (e.code === 'ArrowRight') { onSeek(Math.min(playbackIndex + 1, currentTrack.length - 1)); }
        else if (e.code === 'ArrowLeft')  { onSeek(Math.max(playbackIndex - 1, 0)); }
    });
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
            if (currentTrack.length > 0) {
                renderTrackOnMap(currentTrack);
                updatePlaybackUi();
            }
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

    // Pause auto-follow while the user is dragging or zooming, so they can
    // explore the map without it fighting them. Resume on next playback step.
    map.on('mousedown',  () => { userInteracting = true; });
    map.on('touchstart', () => { userInteracting = true; });
    map.on('dragend',    () => { userInteracting = false; });
    map.on('touchend',   () => { userInteracting = false; });
}

function renderTrackOnMap(points) {
    if (!points || points.length === 0) return;

    if (map.getLayer('track-line'))   map.removeLayer('track-line');
    if (map.getSource(trackSourceId)) map.removeSource(trackSourceId);
    if (map.getLayer('played-line'))   map.removeLayer('played-line');
    if (map.getSource(playedSourceId)) map.removeSource(playedSourceId);
    if (window._startMarker) { window._startMarker.remove(); window._startMarker = null; }
    if (window._endMarker)   { window._endMarker.remove();   window._endMarker = null; }

    map.addSource(trackSourceId, {
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
        source: trackSourceId,
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: { 'line-color': '#2196F3', 'line-width': 3, 'line-opacity': 0.55 }
    });

    map.addSource(playedSourceId, {
        type: 'geojson',
        data: { type: 'Feature', geometry: { type: 'LineString', coordinates: [] } }
    });
    map.addLayer({
        id: 'played-line',
        type: 'line',
        source: playedSourceId,
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: { 'line-color': '#FF6F00', 'line-width': 4 }
    });

    const mkStyle = (color) => {
        const el = document.createElement('div');
        el.style.cssText = `width:12px;height:12px;background:${color};border-radius:50%;border:2px solid white;box-shadow:0 2px 4px rgba(0,0,0,0.3)`;
        return el;
    };
    window._startMarker = new maplibregl.Marker({ element: mkStyle('#4CAF50'), anchor: 'center' })
        .setLngLat([points[0].lon, points[0].lat]).addTo(map);
    window._endMarker = new maplibregl.Marker({ element: mkStyle('#F44336'), anchor: 'center' })
        .setLngLat([points[points.length - 1].lon, points[points.length - 1].lat]).addTo(map);

    if (runnerMarker) { runnerMarker.remove(); runnerMarker = null; }
    const runnerEl = document.createElement('div');
    runnerEl.style.cssText = [
        'width:18px', 'height:18px',
        'background:#FF9800', 'border-radius:50%',
        'border:3px solid white',
        'box-shadow:0 0 0 2px #FF9800, 0 2px 8px rgba(0,0,0,0.5)',
        'pointer-events:none'
    ].join(';');
    runnerMarker = new maplibregl.Marker({ element: runnerEl, anchor: 'center' })
        .setLngLat([points[0].lon, points[0].lat])
        .addTo(map);

    const lons = points.map(p => p.lon);
    const lats = points.map(p => p.lat);
    map.fitBounds(
        [[Math.min(...lons), Math.min(...lats)], [Math.max(...lons), Math.max(...lats)]],
        { padding: 60, maxZoom: 17 }
    );
}

function redrawPlayedTrack() {
    const src = map.getSource(playedSourceId);
    if (!src) return;
    const upTo = currentTrack.slice(0, playbackIndex + 1);
    src.setData({
        type: 'Feature',
        geometry: {
            type: 'LineString',
            coordinates: upTo.map(p => [p.lon, p.lat])
        }
    });
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

    if (q) {
        clearTimeout(searchDebounceTimer);
        searchDebounceTimer = setTimeout(() => searchServer(q, sport), 300);
        return;
    }

    if (isSearchActive) {
        isSearchActive = false;
        document.getElementById('loadMoreBtn').style.display = hasNextPage ? 'block' : 'none';
    }

    const filtered = allSessions.filter(s => !sport || s.sport_type === sport);
    renderSessionList(filtered);
}

async function searchServer(query, sport) {
    const list = document.getElementById('sessionList');
    list.innerHTML = '<p class="no-sessions">Searching...</p>';

    try {
        const url = `${API_BASE}/sessions?per_page=100&page=1&hide_resets=true&min_total_points=10&search=${encodeURIComponent(query)}`;
        const resp = await fetch(url);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const json = await resp.json();

        let results = json.data.sessions;
        if (sport) results = results.filter(s => s.sport_type === sport);

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
        const date = s.start_date_time
            ? new Date(s.start_date_time).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
            : 'Unknown date';
        const name  = escapeHtml(s.event_name || 'Unnamed session');
        const sport = s.sport_type ? `<span class="session-sport">${escapeHtml(s.sport_type)}</span>` : '';
        const city  = s.start_city ? ` · ${escapeHtml(s.start_city)}` : '';
        const recorder = formatRecorder(s);
        const recorderLine = recorder
            ? `<div class="session-item-time">Recorded by ${escapeHtml(recorder)}</div>`
            : '';
        const active = s.session_id === currentSessionId ? ' active' : '';

        return `<div class="session-item${active}" onclick="selectSession('${escapeHtml(s.session_id)}')">
            <div class="session-item-info">
                <div class="session-item-name">${name}${sport}</div>
                <div class="session-item-time">${date}${city}</div>
                ${recorderLine}
            </div>
        </div>`;
    }).join('');
}

// ─────────────────────────────────────────────────────────────
// SESSION SELECTION
// ─────────────────────────────────────────────────────────────
async function selectSession(sessionId) {
    stopPlayback();
    currentSessionId = sessionId;
    applyFilter();

    const hint = document.getElementById('emptyHint');
    if (hint) hint.style.display = 'none';

    const panel = document.getElementById('playbackPanel');
    panel.classList.add('visible');
    document.getElementById('playbackTitle').textContent = 'Loading…';

    try {
        const resp = await fetch(`${API_BASE}/sessions/${sessionId}/track?max_points=${MAX_TRACK_POINTS}&include_resets=true`);
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
        const json = await resp.json();

        currentTrack = json.data.points || [];
        if (currentTrack.length < 2) {
            document.getElementById('playbackTitle').textContent = 'Route has no points';
            return;
        }

        const meta = allSessions.find(s => s.session_id === sessionId);
        const title = meta
            ? (meta.event_name || formatDate(meta.start_date_time) || 'Route')
            : 'Route';
        document.getElementById('playbackTitle').textContent = title;

        if (map.isStyleLoaded()) renderTrackOnMap(currentTrack);
        else map.once('load', () => renderTrackOnMap(currentTrack));

        // Reset playback UI
        playbackIndex = 0;
        const slider = document.getElementById('progressSlider');
        slider.min = 0;
        slider.max = currentTrack.length - 1;
        slider.value = 0;

        document.getElementById('totalLabel').textContent = formatTrackDuration();
        updatePlaybackUi();
    } catch (err) {
        console.error('selectSession failed:', err);
        document.getElementById('playbackTitle').textContent = 'Failed to load route';
    }
}

// ─────────────────────────────────────────────────────────────
// PLAYBACK
// ─────────────────────────────────────────────────────────────
function togglePlay() {
    if (currentTrack.length < 2) return;
    if (isPlaying) {
        pausePlayback();
    } else {
        // If at the end, restart from beginning
        if (playbackIndex >= currentTrack.length - 1) {
            playbackIndex = 0;
            redrawPlayedTrack();
            updatePlaybackUi();
        }
        startPlayback();
    }
}

function startPlayback() {
    isPlaying = true;
    const btn = document.getElementById('btnPlayPause');
    btn.innerHTML = '&#10074;&#10074;'; // pause
    btn.classList.add('playing');
    btn.title = 'Pause';
    scheduleNextStep();
}

function pausePlayback() {
    isPlaying = false;
    if (playbackTimer) { clearTimeout(playbackTimer); playbackTimer = null; }
    const btn = document.getElementById('btnPlayPause');
    btn.innerHTML = '&#9654;'; // play
    btn.classList.remove('playing');
    btn.title = 'Play';
}

function stopPlayback() {
    pausePlayback();
    playbackIndex = 0;
    if (currentTrack.length > 0) {
        redrawPlayedTrack();
        updatePlaybackUi();
    }
}

function scheduleNextStep() {
    if (!isPlaying) return;
    if (playbackIndex >= currentTrack.length - 1) {
        pausePlayback();
        return;
    }

    const cur  = currentTrack[playbackIndex];
    const next = currentTrack[playbackIndex + 1];

    let realDeltaMs = 1000; // default if timestamps unavailable
    if (cur && next && cur.ts && next.ts) {
        const t0 = Date.parse(cur.ts);
        const t1 = Date.parse(next.ts);
        if (!Number.isNaN(t0) && !Number.isNaN(t1) && t1 > t0) {
            realDeltaMs = t1 - t0;
        }
    }

    // Cap the wait so very long pauses in the recording don't stall playback,
    // and floor it so very fast multipliers stay animatable.
    let waitMs = realDeltaMs / playbackSpeed;
    waitMs = Math.max(8, Math.min(waitMs, 2000));

    playbackTimer = setTimeout(() => {
        playbackIndex++;
        updatePlaybackUi();
        scheduleNextStep();
    }, waitMs);
}

function onSeek(value) {
    const idx = Math.max(0, Math.min(currentTrack.length - 1, parseInt(value, 10) || 0));
    playbackIndex = idx;
    updatePlaybackUi();
}

function onSpeedChange(value) {
    playbackSpeed = parseFloat(value) || 1;
}

function onCameraChange(value) {
    cameraMode = value || 'overview';
    if (cameraMode === 'overview') {
        // Reset pitch/bearing and re-fit to the whole route
        if (currentTrack.length > 0) {
            const lons = currentTrack.map(p => p.lon);
            const lats = currentTrack.map(p => p.lat);
            map.easeTo({ pitch: 0, bearing: 0, duration: 600 });
            map.fitBounds(
                [[Math.min(...lons), Math.min(...lats)], [Math.max(...lons), Math.max(...lats)]],
                { padding: 60, maxZoom: 17, duration: 700 }
            );
        }
    } else {
        // Snap camera to current runner position with the new mode
        updateCamera(true);
    }
}

function updateCamera(immediate) {
    if (cameraMode === 'overview') return;
    if (userInteracting) return;
    const p = currentTrack[playbackIndex];
    if (!p) return;

    // Keep the runner clear of the playback panel that overlays the bottom.
    const padding = { top: 80, bottom: 200, left: 40, right: 40 };

    if (cameraMode === 'follow') {
        // Smooth pan only — keep current zoom/pitch/bearing the user chose
        map.easeTo({
            center: [p.lon, p.lat],
            padding: padding,
            duration: immediate ? 400 : 250,
            essential: true
        });
    } else if (cameraMode === 'flyover') {
        // Close, tilted view that rotates so the runner is heading "up"
        const bearing = bearingAtIndex(playbackIndex);
        map.easeTo({
            center: [p.lon, p.lat],
            zoom: 17,
            pitch: 60,
            bearing: bearing,
            padding: padding,
            duration: immediate ? 600 : 280,
            essential: true
        });
    }
}

// Bearing in degrees from the segment around the given index.
function bearingAtIndex(idx) {
    const a = currentTrack[Math.max(0, idx - 1)];
    const b = currentTrack[Math.min(currentTrack.length - 1, idx + 1)];
    if (!a || !b || (a === b)) return 0;
    const toRad = d => d * Math.PI / 180;
    const toDeg = r => r * 180 / Math.PI;
    const φ1 = toRad(a.lat), φ2 = toRad(b.lat);
    const Δλ = toRad(b.lon - a.lon);
    const y = Math.sin(Δλ) * Math.cos(φ2);
    const x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(Δλ);
    return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

function updatePlaybackUi() {
    if (currentTrack.length === 0) return;

    const p = currentTrack[playbackIndex];
    if (!p) return;

    if (runnerMarker) runnerMarker.setLngLat([p.lon, p.lat]);
    redrawPlayedTrack();
    updateCamera(false);

    const slider = document.getElementById('progressSlider');
    if (parseInt(slider.value, 10) !== playbackIndex) slider.value = playbackIndex;

    document.getElementById('elapsedLabel').textContent = formatElapsedAt(playbackIndex);
    document.getElementById('pbDistance').textContent =
        p.distance != null ? p.distance.toFixed(2) : '0.00';
    document.getElementById('pbSpeed').textContent =
        p.speed != null ? (p.speed).toFixed(1) : '0.0';
    document.getElementById('pbAltitude').textContent =
        p.alt != null ? Math.round(p.alt) : '--';
    document.getElementById('pbHeartRate').textContent =
        (p.hr != null && p.hr > 0) ? p.hr : '--';
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
function formatTrackDuration() {
    if (currentTrack.length < 2) return '00:00';
    const t0 = Date.parse(currentTrack[0].ts);
    const t1 = Date.parse(currentTrack[currentTrack.length - 1].ts);
    if (Number.isNaN(t0) || Number.isNaN(t1)) return '--:--';
    return formatHms(t1 - t0);
}

function formatElapsedAt(idx) {
    if (currentTrack.length === 0) return '00:00';
    const t0 = Date.parse(currentTrack[0].ts);
    const ti = Date.parse(currentTrack[idx].ts);
    if (Number.isNaN(t0) || Number.isNaN(ti)) return '--:--';
    return formatHms(ti - t0);
}

function formatHms(ms) {
    const s = Math.max(0, Math.floor(ms / 1000));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    if (h > 0) return `${h}:${pad(m)}:${pad(sec)}`;
    return `${pad(m)}:${pad(sec)}`;
}

function formatDate(iso) {
    if (!iso) return null;
    return new Date(iso).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

function pad(n) { return String(n).padStart(2, '0'); }

function formatRecorder(s) {
    const parts = [s && s.firstname, s && s.lastname].filter(Boolean);
    return parts.length ? parts.join(' ') : '';
}

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
