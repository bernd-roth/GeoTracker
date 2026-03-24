'use strict';

/**
 * PanelResize — drag-to-resize for overlay panels and charts container.
 * Sizes are persisted to localStorage so they survive page reloads.
 *
 * Usage:
 *   PanelResize.makeResizable(element, { edges: ['right','bottom'], key: 'myPanel' });
 *   PanelResize.makeChartsResizable(chartsEl, mapEl, { onResize: () => map.resize() });
 */
(function () {
    var STORAGE_PREFIX = 'panelSize_';
    var MIN_WIDTH  = 180;
    var MIN_HEIGHT = 100;
    var MIN_CHART_HEIGHT = 60;

    /* ── helpers ──────────────────────────────────────────── */

    function saveSize(key, w, h) {
        try { localStorage.setItem(STORAGE_PREFIX + key, JSON.stringify({ w: w, h: h })); }
        catch (_) { /* quota exceeded – ignore */ }
    }

    function loadSize(key) {
        try {
            var raw = localStorage.getItem(STORAGE_PREFIX + key);
            return raw ? JSON.parse(raw) : null;
        } catch (_) { return null; }
    }

    function cursorFor(edge) {
        if (edge === 'right' || edge === 'left')   return 'ew-resize';
        if (edge === 'top'   || edge === 'bottom')  return 'ns-resize';
        if (edge === 'corner-br') return 'nwse-resize';
        if (edge === 'corner-bl') return 'nesw-resize';
        return '';
    }

    /* ── makeResizable (overlay panels) ──────────────────── */

    function makeResizable(panel, opts) {
        opts = opts || {};
        var key       = opts.key       || panel.id || 'panel';
        var edges     = opts.edges     || ['right', 'bottom'];
        var minW      = opts.minWidth  || MIN_WIDTH;
        var minH      = opts.minHeight || MIN_HEIGHT;
        var maxW      = opts.maxWidth  || function () { return window.innerWidth  * 0.7; };
        var maxH      = opts.maxHeight || function () { return window.innerHeight * 0.8; };
        var onResize  = opts.onResize  || null;

        // Resolve max functions
        function getMaxW() { return typeof maxW === 'function' ? maxW() : maxW; }
        function getMaxH() { return typeof maxH === 'function' ? maxH() : maxH; }

        // Restore saved size
        var saved = loadSize(key);
        if (saved) {
            if (saved.w && (edges.indexOf('right') !== -1 || edges.indexOf('left') !== -1)) {
                panel.style.width = Math.min(saved.w, getMaxW()) + 'px';
            }
            if (saved.h) {
                panel.style.height    = Math.min(saved.h, getMaxH()) + 'px';
                panel.style.maxHeight = 'none';
                // Also remove max-height from panel-content inside
                var pc = panel.querySelector('.panel-content');
                if (pc) pc.style.maxHeight = 'none';
            }
        }

        // Ensure scrolling happens in an inner container so resize handles
        // (which are absolutely positioned in the panel) are never blocked
        // by scroll behaviour at the edges.
        var panelContent = panel.querySelector('.panel-content');
        if (panelContent) {
            // Panels that already have .panel-content — just let it scroll
            panelContent.style.overflowY = 'auto';
            if (panelContent.style.maxHeight) panelContent.style.maxHeight = 'none';
        } else {
            // Panels like #speedDisplay that scroll on the outer element:
            // wrap existing children in an inner scroll container so the
            // resize handles sit outside the scrollable area.
            var computed = window.getComputedStyle(panel);
            if (computed.overflowY === 'auto' || computed.overflowY === 'scroll') {
                var scrollWrap = document.createElement('div');
                scrollWrap.className = 'resize-scroll-wrap';
                scrollWrap.style.overflowY = computed.overflowY;
                scrollWrap.style.height    = '100%';
                scrollWrap.style.boxSizing = 'border-box';
                // Transfer padding from panel to wrapper
                scrollWrap.style.padding = computed.padding;
                while (panel.firstChild) scrollWrap.appendChild(panel.firstChild);
                panel.appendChild(scrollWrap);
                panel.style.padding  = '0';
                panel.style.overflow = 'hidden';
                panelContent = scrollWrap;
            }
        }

        // Create edge handles
        edges.forEach(function (edge) {
            var h = document.createElement('div');
            h.className = 'resize-handle resize-handle-' + edge;
            h.dataset.edge = edge;
            panel.appendChild(h);
        });

        // Corner handle
        var hasHoriz  = edges.indexOf('right') !== -1 || edges.indexOf('left') !== -1;
        var hasBottom = edges.indexOf('bottom') !== -1;
        if (hasHoriz && hasBottom) {
            var corner = document.createElement('div');
            var side   = edges.indexOf('right') !== -1 ? 'br' : 'bl';
            corner.className = 'resize-handle resize-handle-corner-' + side;
            corner.dataset.edge = 'corner-' + side;
            panel.appendChild(corner);
        }

        // Drag state
        var dragging = false, dragEdge = '', startX = 0, startY = 0, startW = 0, startH = 0;

        panel.addEventListener('mousedown', function (e) {
            if (!e.target.classList.contains('resize-handle')) return;
            e.preventDefault();
            e.stopPropagation();
            dragging  = true;
            dragEdge  = e.target.dataset.edge;
            startX    = e.clientX;
            startY    = e.clientY;
            startW    = panel.offsetWidth;
            startH    = panel.offsetHeight;
            document.body.style.cursor     = cursorFor(dragEdge);
            document.body.style.userSelect = 'none';
        });

        document.addEventListener('mousemove', function (e) {
            if (!dragging) return;
            var dx = e.clientX - startX;
            var dy = e.clientY - startY;

            if (dragEdge === 'right' || dragEdge === 'corner-br') {
                panel.style.width = Math.max(minW, Math.min(getMaxW(), startW + dx)) + 'px';
            }
            if (dragEdge === 'left' || dragEdge === 'corner-bl') {
                panel.style.width = Math.max(minW, Math.min(getMaxW(), startW - dx)) + 'px';
            }
            if (dragEdge === 'bottom' || dragEdge === 'corner-br' || dragEdge === 'corner-bl') {
                var newH = Math.max(minH, Math.min(getMaxH(), startH + dy));
                panel.style.height    = newH + 'px';
                panel.style.maxHeight = 'none';
                if (panelContent) panelContent.style.maxHeight = 'none';
            }
            if (onResize) onResize(panel);
        });

        document.addEventListener('mouseup', function () {
            if (!dragging) return;
            dragging = false;
            document.body.style.cursor     = '';
            document.body.style.userSelect = '';
            saveSize(key, panel.offsetWidth, panel.offsetHeight);
            if (onResize) onResize(panel);
        });
    }

    /* ── makeChartsResizable ─────────────────────────────── */

    function makeChartsResizable(chartsContainer, mapElement, opts) {
        opts = opts || {};
        var key      = opts.key       || 'chartsHeight';
        var minH     = opts.minHeight || MIN_CHART_HEIGHT;
        var maxH     = opts.maxHeight || function () { return window.innerHeight * 0.7; };
        var onResize = opts.onResize  || null;

        function getMaxH() { return typeof maxH === 'function' ? maxH() : maxH; }

        // Restore saved height
        var saved = loadSize(key);
        if (saved && saved.h) {
            var h = Math.min(saved.h, getMaxH());
            chartsContainer.style.height = h + 'px';
            mapElement.style.height      = 'calc(100vh - ' + h + 'px)';
        }

        // Create top-edge handle
        var handle = document.createElement('div');
        handle.className = 'resize-handle charts-resize-handle';
        chartsContainer.style.position = 'relative';
        chartsContainer.insertBefore(handle, chartsContainer.firstChild);

        var dragging = false, startY = 0, startH = 0;

        handle.addEventListener('mousedown', function (e) {
            e.preventDefault();
            e.stopPropagation();
            dragging = true;
            startY   = e.clientY;
            startH   = chartsContainer.offsetHeight;
            document.body.style.cursor     = 'ns-resize';
            document.body.style.userSelect = 'none';
        });

        document.addEventListener('mousemove', function (e) {
            if (!dragging) return;
            var dy = startY - e.clientY;               // drag up → taller charts
            var newH = Math.max(minH, Math.min(getMaxH(), startH + dy));
            chartsContainer.style.height = newH + 'px';
            mapElement.style.height      = 'calc(100vh - ' + newH + 'px)';
            if (onResize) onResize();
        });

        document.addEventListener('mouseup', function () {
            if (!dragging) return;
            dragging = false;
            document.body.style.cursor     = '';
            document.body.style.userSelect = '';
            saveSize(key, null, chartsContainer.offsetHeight);
            if (onResize) onResize();
        });
    }

    /* ── public API ──────────────────────────────────────── */

    window.PanelResize = {
        makeResizable:       makeResizable,
        makeChartsResizable: makeChartsResizable
    };
})();
