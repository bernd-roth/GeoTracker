// Global variables
let map;
let websocket;
let polylines = {};
let startMarkers = {};
let endMarkers = {};
let gpxPolyline;
let altitudeChart;
let speedChart;
let speedHistory = {};
let elevationHistory = {}; // Added to track elevation gain
let userColors = {};
let trackPoints = {};
let isProcessingBatch = false;
let statsVisible = true; // Track the visibility state of stat boxes
let sessionPersonNames = {};
let availableSessions = [];
let sessionPanelVisible = true;

// Interactive chart variables
let hoverMarker = null;
let infoPopup = null;
let currentHoverPoint = null;

// Debug variables
let debugMessages = [];
let debugPaused = false;
let maxDebugMessages = 1000; // Limit to prevent browser slowdown

function getRandomInRange(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function initMap() {
    try {
        console.log("Initializing map...");
        const initialLocation = [48.1818798, 16.3607528]; // Use Vienna as default location
        map = L.map('map', {
            worldCopyJump: false,
            maxBounds: [[-90, -180], [90, 180]],
            minZoom: 1
        }).setView(initialLocation, 6);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '© OpenStreetMap contributors',
            noWrap: true
        }).addTo(map);

        console.log("Map initialized successfully");

        initCharts();
        connectToWebSocket();
    } catch (error) {
        console.error("Error initializing map:", error);
        // Try to provide visual feedback that map initialization failed
        const mapElement = document.getElementById('map');
        if (mapElement) {
            mapElement.innerHTML = '<div style="text-align: center; padding: 20px; color: red;">Error loading map. Please check console for details.</div>';
        }
    }
}

function initCharts() {
    const chartOptions = {
        maintainAspectRatio: false,
        animation: false, // Disable animations for better performance
        elements: {
            line: {
                tension: 0 // Disable bezier curves for better performance
            },
            point: {
                radius: 0, // Hide points for better performance
                hoverRadius: 6 // Show points on hover
            }
        },
        scales: {
            x: {
                type: 'linear',
                title: {
                    display: true,
                    text: 'Distance (km)'
                }
            },
            y: {
                title: {
                    display: true,
                    text: 'Altitude (m)'
                },
                min: 0
            }
        },
        plugins: {
            legend: {
                onClick: handleLegendClick
            }
        },
        interaction: {
            intersect: false,
            mode: 'index'
        },
        onHover: (event, activeElements) => {
            addDebugMessage(`Chart hover event triggered with ${activeElements.length} active elements`, 'interaction');
            handleChartHover(event, activeElements, 'altitude');
        },
        onClick: (event, activeElements) => {
            addDebugMessage(`Chart click event triggered with ${activeElements.length} active elements`, 'interaction');
            handleChartClick(event, activeElements, 'altitude');
        }
    };

    altitudeChart = new Chart(
        document.getElementById('altitudeChart').getContext('2d'),
        {
            type: 'line',
            data: { datasets: [] },
            options: {
                ...chartOptions,
                scales: {
                    ...chartOptions.scales,
                    y: {
                        ...chartOptions.scales.y,
                        text: 'Altitude (m)'
                    }
                }
            }
        }
    );

    speedChart = new Chart(
        document.getElementById('speedChart').getContext('2d'),
        {
            type: 'line',
            data: { datasets: [] },
            options: {
                ...chartOptions,
                scales: {
                    ...chartOptions.scales,
                    y: {
                        ...chartOptions.scales.y,
                        title: {
                            display: true,
                            text: 'Speed (km/h)'
                        }
                    }
                },
                onHover: (event, activeElements) => {
                    addDebugMessage(`Speed chart hover event triggered with ${activeElements.length} active elements`, 'interaction');
                    handleChartHover(event, activeElements, 'speed');
                },
                onClick: (event, activeElements) => {
                    addDebugMessage(`Speed chart click event triggered with ${activeElements.length} active elements`, 'interaction');
                    handleChartClick(event, activeElements, 'speed');
                }
            }
        }
    );

    // Create info popup element
    createInfoPopup();
}

// Create the floating info popup
function createInfoPopup() {
    // Remove existing popup if it exists
    const existingPopup = document.getElementById('chartInfoPopup');
    if (existingPopup) {
        existingPopup.remove();
    }

    infoPopup = document.createElement('div');
    infoPopup.id = 'chartInfoPopup';
    infoPopup.style.cssText = `
        position: fixed !important;
        background: white !important;
        border: 2px solid #333 !important;
        border-radius: 8px !important;
        padding: 12px !important;
        font-family: Arial, sans-serif !important;
        font-size: 12px !important;
        box-shadow: 0 4px 12px rgba(0,0,0,0.3) !important;
        z-index: 2500 !important;
        display: none !important;
        pointer-events: none !important;
        max-width: 300px !important;
        line-height: 1.4 !important;
    `;
    document.body.appendChild(infoPopup);
    
    addDebugMessage('Info popup element created and added to DOM', 'system');
}

// Handle chart hover events
function handleChartHover(event, activeElements, chartType) {
    if (!activeElements || activeElements.length === 0) {
        hideHoverMarker();
        hideInfoPopup();
        return;
    }

    const element = activeElements[0];
    const datasetIndex = element.datasetIndex;
    const dataIndex = element.index;
    
    // Get the chart and dataset
    const chart = chartType === 'altitude' ? altitudeChart : speedChart;
    const dataset = chart.data.datasets[datasetIndex];
    
    if (!dataset) {
        addDebugMessage(`No dataset found at index ${datasetIndex}`, 'warning');
        return;
    }

    addDebugMessage(`Chart hover - Dataset: ${dataset.label}, DataIndex: ${dataIndex}`, 'interaction');

    // Use the dataset label directly as session ID first
    let sessionId = dataset.label;
    
    // Check if we have track points for this session ID
    let sessionTrackPoints = trackPoints[sessionId];
    
    if (!sessionTrackPoints) {
        // If not found, try extracting session ID (for cases where display ID format differs)
        if (sessionId.includes('_') && sessionId !== 'GPX Track') {
            const parts = sessionId.split('_');
            if (parts.length > 1) {
                // Try removing just the first part (person name)
                const extractedId = parts.slice(1).join('_');
                sessionTrackPoints = trackPoints[extractedId];
                if (sessionTrackPoints) {
                    sessionId = extractedId;
                    addDebugMessage(`Found track points using extracted session ID: ${sessionId}`, 'interaction');
                }
            }
        }
    }
    
    if (!sessionTrackPoints) {
        addDebugMessage(`No track points found for session: ${sessionId}`, 'warning');
        addDebugMessage(`Available sessions: ${Object.keys(trackPoints).join(', ')}`, 'debug');
        
        // Try one more approach - look for sessions that end with part of our ID
        const availableKeys = Object.keys(trackPoints);
        for (const key of availableKeys) {
            if (key.includes(sessionId) || sessionId.includes(key)) {
                sessionTrackPoints = trackPoints[key];
                sessionId = key;
                addDebugMessage(`Found match using partial matching: ${sessionId}`, 'interaction');
                break;
            }
        }
        
        if (!sessionTrackPoints) {
            return;
        }
    }
    
    if (!sessionTrackPoints[dataIndex]) {
        addDebugMessage(`No track point at index ${dataIndex} for session ${sessionId}. Available: ${sessionTrackPoints.length}`, 'warning');
        return;
    }

    const point = sessionTrackPoints[dataIndex];
    
    // Show marker on map
    showHoverMarker(point.lat, point.lng, sessionId);
    
    // Show info popup
    showInfoPopup(event, point, sessionId, chartType);
    
    addDebugMessage(`Chart hover successful - Session: ${sessionId}, Point: ${dataIndex}, Coords: ${point.lat}, ${point.lng}`, 'interaction');
}

// Handle chart click events
function handleChartClick(event, activeElements, chartType) {
    if (!activeElements || activeElements.length === 0) return;

    const element = activeElements[0];
    const datasetIndex = element.datasetIndex;
    const dataIndex = element.index;
    
    // Get the chart and dataset
    const chart = chartType === 'altitude' ? altitudeChart : speedChart;
    const dataset = chart.data.datasets[datasetIndex];
    
    if (!dataset) return;

    // Use the same session ID matching logic as hover
    let sessionId = dataset.label;
    let sessionTrackPoints = trackPoints[sessionId];
    
    if (!sessionTrackPoints) {
        // Try extracting session ID if direct match fails
        if (sessionId.includes('_') && sessionId !== 'GPX Track') {
            const parts = sessionId.split('_');
            if (parts.length > 1) {
                const extractedId = parts.slice(1).join('_');
                sessionTrackPoints = trackPoints[extractedId];
                if (sessionTrackPoints) {
                    sessionId = extractedId;
                }
            }
        }
    }
    
    if (!sessionTrackPoints) {
        // Try partial matching
        const availableKeys = Object.keys(trackPoints);
        for (const key of availableKeys) {
            if (key.includes(sessionId) || sessionId.includes(key)) {
                sessionTrackPoints = trackPoints[key];
                sessionId = key;
                break;
            }
        }
        
        if (!sessionTrackPoints) return;
    }
    
    if (!sessionTrackPoints[dataIndex]) return;

    const point = sessionTrackPoints[dataIndex];
    
    // Center map on clicked point
    map.setView([point.lat, point.lng], Math.max(map.getZoom(), 15));
    
    addDebugMessage(`Chart click - Centered map on Session: ${sessionId}, Point: ${dataIndex}`, 'interaction');
}

// Show temporary marker on map
function showHoverMarker(lat, lng, sessionId) {
    // Validate coordinates
    if (!lat || !lng || isNaN(lat) || isNaN(lng)) {
        addDebugMessage(`Invalid coordinates for hover marker: ${lat}, ${lng}`, 'error');
        return;
    }

    // Remove existing hover marker
    if (hoverMarker) {
        map.removeLayer(hoverMarker);
    }

    // Create new hover marker with session color
    const sessionColor = getColorForUser(sessionId);
    
    try {
        hoverMarker = L.circleMarker([lat, lng], {
            radius: 10,
            fillColor: sessionColor,
            color: '#fff',
            weight: 3,
            opacity: 1,
            fillOpacity: 0.8
        }).addTo(map);
        
        addDebugMessage(`Hover marker created at ${lat}, ${lng} with color ${sessionColor}`, 'interaction');
    } catch (error) {
        addDebugMessage(`Error creating hover marker: ${error.message}`, 'error');
    }
}

// Hide hover marker
function hideHoverMarker() {
    if (hoverMarker) {
        map.removeLayer(hoverMarker);
        hoverMarker = null;
    }
}

// Show info popup with point details
function showInfoPopup(event, point, sessionId, chartType) {
    if (!infoPopup) {
        addDebugMessage('Info popup element not found', 'error');
        return;
    }

    // Get person name for display
    const personName = sessionPersonNames[sessionId] || "";
    let displayId = sessionId;
    if (personName) {
        displayId = createDisplayId(sessionId, personName);
    }

    // Format time
    const timeStr = point.timestamp ? point.timestamp.toLocaleTimeString() : 'N/A';
    const dateStr = point.timestamp ? point.timestamp.toLocaleDateString() : 'N/A';

    // Check if this is a GPX track to show additional info
    const isGPXTrack = sessionId === 'GPX Track';
    
    // Build additional info for GPX tracks
    let additionalInfo = '';
    if (isGPXTrack && point.lap !== undefined && point.activityType) {
        additionalInfo = `
            <div style="border-bottom: 1px solid #ddd; margin-bottom: 6px; padding-bottom: 4px;">
                <strong>Activity:</strong> ${point.activityType}<br>
                <strong>Lap:</strong> ${point.lap}
            </div>
        `;
    }

    // Create popup content
    const content = `
        <div style="font-weight: bold; color: ${isGPXTrack ? 'red' : getColorForUser(sessionId)}; margin-bottom: 8px;">
            ${displayId}
        </div>
        <div style="border-bottom: 1px solid #ddd; margin-bottom: 6px; padding-bottom: 4px;">
            <strong>Time:</strong> ${timeStr}<br>
            <strong>Date:</strong> ${dateStr}
        </div>
        ${additionalInfo}
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 11px;">
            <div>
                <strong style="color: #2196F3;">Distance</strong><br>
                ${point.distance.toFixed(2)} km
            </div>
            <div>
                <strong style="color: #4CAF50;">Altitude</strong><br>
                ${point.altitude.toFixed(1)} m
            </div>
            <div>
                <strong style="color: #FF9800;">Speed</strong><br>
                ${point.speed.toFixed(1)} km/h
            </div>
            <div>
                <strong style="color: ${getHeartRateColor(point.heartRate || 0)};">Heart Rate</strong><br>
                ${point.heartRate || 0} bpm
            </div>
        </div>
        <div style="margin-top: 8px; padding-top: 6px; border-top: 1px solid #ddd; font-size: 10px; color: #666;">
            Coordinates: ${point.lat.toFixed(6)}, ${point.lng.toFixed(6)}
        </div>
        ${isGPXTrack ? '<div style="font-size: 9px; color: #999; margin-top: 4px;">📁 GPX File Data</div>' : ''}
    `;

    infoPopup.innerHTML = content;
    
    // Get mouse position relative to the page
    let x, y;
    
    if (event.native) {
        // Chart.js provides native event
        x = event.native.pageX || event.native.clientX;
        y = event.native.pageY || event.native.clientY;
    } else {
        // Fallback to event coordinates
        x = event.pageX || event.clientX;
        y = event.pageY || event.clientY;
    }
    
    // Position popup near mouse cursor
    infoPopup.style.left = (x + 15) + 'px';
    infoPopup.style.top = (y - 10) + 'px';
    infoPopup.style.display = 'block';

    addDebugMessage(`Info popup shown at coordinates: ${x}, ${y} for ${isGPXTrack ? 'GPX track' : 'live session'}`, 'interaction');

    // Adjust position if popup goes off screen
    setTimeout(() => {
        const popupRect = infoPopup.getBoundingClientRect();
        const windowWidth = window.innerWidth;
        const windowHeight = window.innerHeight;

        let newX = x + 15;
        let newY = y - 10;

        if (popupRect.right > windowWidth) {
            newX = x - popupRect.width - 15;
        }
        if (popupRect.bottom > windowHeight) {
            newY = y - popupRect.height + 10;
        }
        
        infoPopup.style.left = newX + 'px';
        infoPopup.style.top = newY + 'px';
        
        addDebugMessage(`Popup repositioned to: ${newX}, ${newY}`, 'interaction');
    }, 1);
}

// Hide info popup
function hideInfoPopup() {
    if (infoPopup) {
        infoPopup.style.display = 'none';
    }
}

// Function to toggle stat boxes visibility
function toggleStatBoxes() {
    const speedDisplay = document.getElementById('speedDisplay');
    const toggleBtn = document.getElementById('toggleStatsBtn');

    if (statsVisible) {
        // Hide the entire speed display container
        speedDisplay.style.display = 'none';
        toggleBtn.textContent = 'Show Stats';
        statsVisible = false;
    } else {
        // Show the speed display container
        speedDisplay.style.display = 'block';
        toggleBtn.textContent = 'Hide Stats';
        statsVisible = true;
    }

    addDebugMessage(`Stats visibility toggled: ${statsVisible ? 'shown' : 'hidden'}`, 'system');
}

function handleLegendClick(e, legendItem, legend) {
    const index = legendItem.datasetIndex;
    const displayId = legend.chart.data.datasets[index].label;
    const isVisible = legend.chart.isDatasetVisible(index);

    // Get the actual sessionId from the displayId (which might include the person name)
    const sessionId = extractSessionIdFromDisplayId(displayId);

    addDebugMessage(`Toggle visibility from legend click: ${displayId} (Session ID: ${sessionId})`, 'ui');

    // Toggle visibility in both charts
    [altitudeChart, speedChart].forEach(chart => {
        chart.data.datasets.forEach((dataset, i) => {
            if (dataset.label === displayId) {
                chart.setDatasetVisibility(i, !isVisible);
            }
        });
        chart.update();
    });

    // Toggle map elements visibility
    toggleMapElementsVisibility(sessionId, !isVisible);
}

// Helper function to extract actual session ID from display ID
function extractSessionIdFromDisplayId(displayId) {
    // If the displayId contains an underscore, it's likely in the format "PersonName_sessionId"
    if (displayId.includes('_')) {
        // Get the part after the first underscore
        return displayId.split('_').slice(1).join('_');
    }
    // Otherwise, it's just the session ID
    return displayId;
}

function toggleMapElementsVisibility(sessionId, visible) {
    addDebugMessage(`Toggling map visibility for session ${sessionId} to ${visible ? 'visible' : 'hidden'}`, 'map');

    // Toggle polyline
    if (polylines[sessionId]) {
        if (visible) {
            if (!map.hasLayer(polylines[sessionId])) {
                map.addLayer(polylines[sessionId]);
            }
        } else {
            if (map.hasLayer(polylines[sessionId])) {
                map.removeLayer(polylines[sessionId]);
            }
        }
    } else {
        addDebugMessage(`No polyline found for session ${sessionId}`, 'warning');
    }

    // Toggle markers
    [startMarkers, endMarkers].forEach(markers => {
        if (markers[sessionId]) {
            if (visible) {
                if (!map.hasLayer(markers[sessionId])) {
                    map.addLayer(markers[sessionId]);
                }
            } else {
                if (map.hasLayer(markers[sessionId])) {
                    map.removeLayer(markers[sessionId]);
                }
            }
        }
    });

    // Toggle speed display container
    const speedContainer = document.getElementById(`speed-container-${sessionId}`);
    if (speedContainer) {
        speedContainer.style.display = visible ? 'block' : 'none';
    }
}

function connectToWebSocket() {
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${wsProtocol}//${window.location.host}/geotracker`;

    websocket = new WebSocket(wsUrl);

    websocket.onopen = () => {
        console.log('Connected to WebSocket server');
        addDebugMessage('WebSocket connection established', 'connection');

        // Request session list on connect
        requestSessionList();
    };

    websocket.onmessage = (event) => {
        const message = JSON.parse(event.data);

        // Add to debug log first
        addDebugMessage(JSON.stringify(message, null, 2), message.type);

        switch (message.type) {
            case 'history_batch':
                handleHistoryBatch(message.points);
                break;
            case 'history_complete':
                finalizeBatchProcessing();
                break;
            case 'update':
                handlePoint(message.point);
                break;
            case 'session_list':
                handleSessionList(message.sessions);
                break;
            case 'session_deleted':
                handleSessionDeleted(message.sessionId);
                break;
            case 'delete_response':
                if (message.success) {
                    addDebugMessage(`Server confirmed deletion of session ${message.sessionId}`, 'system');
                    showNotification(`Session ${message.sessionId} deleted successfully`, 'info');
                } else {
                    const reason = message.reason || 'Unknown error';
                    addDebugMessage(`Server failed to delete session ${message.sessionId}: ${reason}`, 'error');
                    showNotification(`Cannot delete session: ${reason}`, 'warning');
                }
                break;
        }
    };

    websocket.onclose = () => {
        console.log('WebSocket connection closed');
        addDebugMessage('WebSocket connection closed', 'connection');
        setTimeout(connectToWebSocket, 5000); // Reconnect after 5 seconds
    };

    websocket.onerror = (error) => {
        console.error('WebSocket error:', error);
        addDebugMessage('WebSocket error: ' + error, 'error');
    };
}

function handleHistoryBatch(points) {
    if (!points || points.length === 0) return;

    isProcessingBatch = true;

    points.forEach(point => {
        const sessionId = point.sessionId || "default";
        const personName = point.person || "";

        // Store the person name mapping
        if (personName) {
            sessionPersonNames[sessionId] = personName;
        }

        if (!trackPoints[sessionId]) {
            trackPoints[sessionId] = [];
        }

        // Convert data types and add to trackPoints
        const processedPoint = {
            lat: parseFloat(point.latitude),
            lng: parseFloat(point.longitude),
            distance: parseFloat(point.distance) / 1000, // Convert to kilometers
            altitude: parseFloat(point.altitude || 0),
            speed: parseFloat(point.currentSpeed || 0),
            averageSpeed: parseFloat(point.averageSpeed || 0),
            cumulativeElevationGain: parseFloat(point.cumulativeElevationGain || 0),
            heartRate: parseInt(point.heartRate || 0), // Add heart rate extraction
            timestamp: new Date(point.timestamp.replace(/(\d{2})-(\d{2})-(\d{4})/, '$3-$2-$1')),
            personName: personName
        };

        trackPoints[sessionId].push(processedPoint);
    });
}

function finalizeBatchProcessing() {
    if (!isProcessingBatch) return;

    // Sort all sessions by timestamp
    Object.keys(trackPoints).forEach(sessionId => {
        trackPoints[sessionId].sort((a, b) => a.timestamp - b.timestamp);
    });

    // Update visualizations for all sessions
    requestAnimationFrame(() => {
        Object.keys(trackPoints).forEach(sessionId => {
            updateMapTrack(sessionId);
            updateCharts(sessionId);

            // Update speed display with the latest point
            const latestPoint = trackPoints[sessionId][trackPoints[sessionId].length - 1];
            if (latestPoint) {
                updateSpeedDisplay(sessionId, latestPoint.speed, {
                    averageSpeed: latestPoint.averageSpeed,
                    cumulativeElevationGain: latestPoint.cumulativeElevationGain,
                    heartRate: latestPoint.heartRate,
                    distance: latestPoint.distance, // Pass the distance to updateSpeedDisplay
                    personName: latestPoint.personName ||
                        (window.sessionPersonNames && window.sessionPersonNames[sessionId]) || ""
                });
            }
        });
        isProcessingBatch = false;
    });
}

function handlePoint(data) {
    if (!data || isProcessingBatch) return;

    const sessionId = data.sessionId || "default";
    // Extract the person's name from the data
    const personName = data.person || "";

    if (personName) {
        sessionPersonNames[sessionId] = personName;
    }

    // Create a display ID using our helper function
    const displayId = createDisplayId(sessionId, personName);

    const processedPoint = {
        lat: parseFloat(data.latitude),
        lng: parseFloat(data.longitude),
        distance: parseFloat(data.distance) / 1000,
        altitude: parseFloat(data.altitude || 0),
        speed: parseFloat(data.currentSpeed || 0),
        averageSpeed: parseFloat(data.averageSpeed || 0),
        maxSpeed: parseFloat(data.maxSpeed || 0),
        movingAverageSpeed: parseFloat(data.movingAverageSpeed || 0),
        cumulativeElevationGain: parseFloat(data.cumulativeElevationGain || 0),
        heartRate: parseInt(data.heartRate || 0), // Add heart rate extraction
        timestamp: new Date(data.timestamp.replace(/(\d{2})-(\d{2})-(\d{4})/, '$3-$2-$1')),
        // Store the person's name for use in display
        personName: personName
    };

    if (!trackPoints[sessionId]) {
        trackPoints[sessionId] = [];
    }

    trackPoints[sessionId].push(processedPoint);

    // Use requestAnimationFrame for smooth updates
    requestAnimationFrame(() => {
        updateMapTrack(sessionId);
        updateCharts(sessionId);
        updateSpeedDisplay(sessionId, processedPoint.speed, {
            averageSpeed: processedPoint.averageSpeed,
            maxSpeed: processedPoint.maxSpeed,
            movingAverageSpeed: processedPoint.movingAverageSpeed,
            cumulativeElevationGain: processedPoint.cumulativeElevationGain,
            heartRate: processedPoint.heartRate,
            distance: processedPoint.distance, // Pass the distance to updateSpeedDisplay
            // Pass the person name to the display function
            personName: processedPoint.personName
        });
    });
}

function updateMapTrack(sessionId) {
    const points = trackPoints[sessionId];
    if (!points || points.length === 0) return;

    // Remove existing layers
    if (polylines[sessionId]) {
        map.removeLayer(polylines[sessionId]);
    }
    if (startMarkers[sessionId]) {
        map.removeLayer(startMarkers[sessionId]);
    }
    if (endMarkers[sessionId]) {
        map.removeLayer(endMarkers[sessionId]);
    }

    // Create new polyline
    const coordinates = points.map(point => [point.lat, point.lng]);
    const userColor = getColorForUser(sessionId);

    polylines[sessionId] = L.polyline(coordinates, {
        color: userColor,
        weight: 3
    }).addTo(map);

    // Add markers
    startMarkers[sessionId] = L.marker(coordinates[0], {
        title: "Start Position - " + sessionId
    }).bindPopup('Start - ' + sessionId).addTo(map);

    endMarkers[sessionId] = L.marker(coordinates[coordinates.length - 1], {
        title: "Current Position - " + sessionId
    }).bindPopup('Current - ' + sessionId).addTo(map);
}

function updateCharts(sessionId) {
    const points = trackPoints[sessionId];
    if (!points || points.length === 0) return;

    // Get the person name from our saved mapping
    const personName = sessionPersonNames[sessionId] || "";

    // Use helper function to create display ID
    const displayId = createDisplayId(sessionId, personName);

    const chartData = points.map(point => ({
        x: point.distance,
        y: point.altitude
    }));

    const speedData = points.map(point => ({
        x: point.distance,
        y: point.speed
    }));

    // Update altitude chart
    let altDatasetIndex = altitudeChart.data.datasets.findIndex(
        dataset => dataset.label === sessionId || dataset.label === displayId
    );

    if (altDatasetIndex === -1) {
        altDatasetIndex = altitudeChart.data.datasets.length;
        altitudeChart.data.datasets.push({
            label: displayId,
            borderColor: getColorForUser(sessionId),
            fill: false,
            data: []
        });
    } else if (personName && altitudeChart.data.datasets[altDatasetIndex].label !== displayId) {
        // Update the label if we now have a person name
        altitudeChart.data.datasets[altDatasetIndex].label = displayId;
    }

    altitudeChart.data.datasets[altDatasetIndex].data = chartData;

    // Update speed chart
    let speedDatasetIndex = speedChart.data.datasets.findIndex(
        dataset => dataset.label === sessionId || dataset.label === displayId
    );

    if (speedDatasetIndex === -1) {
        speedDatasetIndex = speedChart.data.datasets.length;
        speedChart.data.datasets.push({
            label: displayId,
            borderColor: getColorForUser(sessionId),
            fill: false,
            data: []
        });
    } else if (personName && speedChart.data.datasets[speedDatasetIndex].label !== displayId) {
        // Update the label if we now have a person name
        speedChart.data.datasets[speedDatasetIndex].label = displayId;
    }

    speedChart.data.datasets[speedDatasetIndex].data = speedData;

    // Batch update both charts
    requestAnimationFrame(() => {
        altitudeChart.update('none');
        speedChart.update('none');
    });
}

function getSpeedColor(speed) {
    if (speed < 5) return '#2196F3';
    if (speed < 10) return '#4CAF50';
    return '#F44336';
}

function getElevationColor(elevation) {
    if (elevation < 10) return '#2196F3';  // Low elevation gain
    if (elevation < 50) return '#4CAF50';  // Medium elevation gain
    return '#F44336';                      // High elevation gain
}

// Add new function to determine heart rate color based on value
function getHeartRateColor(heartRate) {
    if (heartRate === 0) return '#999'; // Gray for no data
    if (heartRate < 60) return '#2196F3'; // Blue for low
    if (heartRate < 100) return '#4CAF50'; // Green for normal
    if (heartRate < 140) return '#FF9800'; // Orange for elevated
    return '#F44336'; // Red for high
}

function updateSpeedDisplay(sessionId, speed, data) {
    // Get the person name from the data or from our saved mapping
    const personName = data.personName || sessionPersonNames[sessionId] || "";

    // Use helper function to create display ID
    const displayId = createDisplayId(sessionId, personName);

    // Initialize or update the speed history
    if (!speedHistory[sessionId]) {
        speedHistory[sessionId] = {
            speeds: [],
            maxSpeed: 0,
            avgSpeed: 0,
            movingAvg: 0,
            elevationGain: 0,
            heartRate: 0,
            totalDistance: 0, // Add distance tracking
            lastUpdate: new Date(),
            personName: personName
        };
    } else if (personName && !speedHistory[sessionId].personName) {
        // Update the person name if it wasn't set before
        speedHistory[sessionId].personName = personName;
    }

    const history = speedHistory[sessionId];
    history.speeds.push(speed);
    history.lastUpdate = new Date();

    // If stats are hidden, just store the data without updating the DOM
    if (!statsVisible && document.getElementById('speedDisplay').style.display === 'none') {
        if (data.maxSpeed !== undefined) {
            history.maxSpeed = Math.max(history.maxSpeed, data.maxSpeed);
        }

        if (data.cumulativeElevationGain !== undefined) {
            history.elevationGain = data.cumulativeElevationGain;
        }

        if (data.heartRate !== undefined) {
            history.heartRate = data.heartRate;
        }

        // Store distance data
        if (data.distance !== undefined) {
            history.totalDistance = data.distance;
        }

        // Clean up old sessions
        cleanupOldSessions();
        return;
    }

    // Get the speed display element
    const speedDisplay = document.getElementById('speedDisplay');

    // Check for default display and remove it when first session arrives
    // This happens before we check if the container exists
    const defaultDisplay = speedDisplay.querySelector('.stats-grid:not([id^="speed-grid-"])');
    if (defaultDisplay) {
        // Remove the default display immediately when we get any new session
        defaultDisplay.remove();
        console.log("Removed default display when handling session: " + sessionId);
    }

    // Check if a container for this session already exists
    let speedContainer = document.getElementById(`speed-container-${sessionId}`);

    if (!speedContainer) {
        // Create a new container for this session
        speedContainer = document.createElement('div');
        speedContainer.id = `speed-container-${sessionId}`;
        speedContainer.className = 'session-container';

        // Create the grid for this session
        const statsGrid = document.createElement('div');
        statsGrid.id = `speed-grid-${sessionId}`;
        statsGrid.className = 'stats-grid';

        // Add the session label
        const sessionLabel = document.createElement('div');
        sessionLabel.className = 'session-label';
        sessionLabel.textContent = displayId;

        // Add these elements to the container
        speedContainer.appendChild(sessionLabel);
        speedContainer.appendChild(statsGrid);

        // Add the container to the speed display
        speedDisplay.appendChild(speedContainer);

        // Create all the stat boxes
        statsGrid.innerHTML = `
            <div class="stat-box">
                <div class="speed-label">Current Speed</div>
                <div class="speed-value" id="currentSpeed-${sessionId}">0.0</div>
                <div class="speed-unit">km/h</div>
            </div>
            <div class="stat-box">
                <div class="speed-label">Maximum Speed</div>
                <div class="speed-value" id="maxSpeed-${sessionId}">0.0</div>
                <div class="speed-unit">km/h</div>
            </div>
            <div class="stat-box">
                <div class="speed-label">Average Speed</div>
                <div class="speed-value" id="avgSpeed-${sessionId}">0.0</div>
                <div class="speed-unit">km/h</div>
            </div>
            <div class="stat-box">
                <div class="speed-label">Moving Average</div>
                <div class="speed-value" id="movingAvg-${sessionId}">0.0</div>
                <div class="speed-unit">km/h</div>
            </div>
            <div class="stat-box">
                <div class="elevation-label">Elevation Gain</div>
                <div class="elevation-value" id="elevationGain-${sessionId}">0.0</div>
                <div class="elevation-unit">m</div>
            </div>
            <div class="stat-box">
                <div class="speed-label">Distance</div>
                <div class="elevation-value" id="totalDistance-${sessionId}">0.0</div>
                <div class="elevation-unit">km</div>
            </div>
            <div class="stat-box">
                <div class="speed-label">Heart Rate</div>
                <div class="speed-value" id="heartRate-${sessionId}">0</div>
                <div class="speed-unit">bpm</div>
            </div>
        `;

        // Auto-scroll to show the new session
        speedDisplay.scrollTop = speedDisplay.scrollHeight;
    } else {
        // Update the session label if the person name has changed
        const sessionLabel = speedContainer.querySelector('.session-label');
        if (sessionLabel && personName) {
            sessionLabel.textContent = displayId;
        }
    }

    // Update current speed
    const currentSpeedElement = document.getElementById(`currentSpeed-${sessionId}`);
    if (currentSpeedElement) {
        currentSpeedElement.textContent = speed.toFixed(1);
        currentSpeedElement.style.color = getSpeedColor(speed);
    }

    // Update max speed from the data
    const maxSpeedElement = document.getElementById(`maxSpeed-${sessionId}`);
    if (maxSpeedElement && data.maxSpeed !== undefined) {
        history.maxSpeed = Math.max(history.maxSpeed, data.maxSpeed);
        maxSpeedElement.textContent = history.maxSpeed.toFixed(1);
        maxSpeedElement.style.color = getSpeedColor(history.maxSpeed);
    }

    // Update average speed
    const avgSpeedElement = document.getElementById(`avgSpeed-${sessionId}`);
    if (avgSpeedElement && data.averageSpeed !== undefined) {
        avgSpeedElement.textContent = data.averageSpeed.toFixed(1);
        avgSpeedElement.style.color = getSpeedColor(data.averageSpeed);
    }

    // Update moving average
    const movingAvgElement = document.getElementById(`movingAvg-${sessionId}`);
    if (movingAvgElement && data.movingAverageSpeed !== undefined) {
        movingAvgElement.textContent = data.movingAverageSpeed.toFixed(1);
        movingAvgElement.style.color = getSpeedColor(data.movingAverageSpeed);
    }

    // Update elevation gain
    const elevationGainElement = document.getElementById(`elevationGain-${sessionId}`);
    if (elevationGainElement && data.cumulativeElevationGain !== undefined) {
        history.elevationGain = data.cumulativeElevationGain;
        elevationGainElement.textContent = history.elevationGain.toFixed(1);
        elevationGainElement.style.color = getElevationColor(history.elevationGain);
    }

    // Update total distance
    const totalDistanceElement = document.getElementById(`totalDistance-${sessionId}`);
    if (totalDistanceElement && data.distance !== undefined) {
        history.totalDistance = data.distance;
        totalDistanceElement.textContent = history.totalDistance.toFixed(2);
        totalDistanceElement.style.color = getDistanceColor(history.totalDistance);
    }

    // Update heart rate
    const heartRateElement = document.getElementById(`heartRate-${sessionId}`);
    if (heartRateElement && data.heartRate !== undefined) {
        history.heartRate = data.heartRate;
        heartRateElement.textContent = history.heartRate;
        heartRateElement.style.color = getHeartRateColor(history.heartRate);
    }

    // Clean up old sessions
    cleanupOldSessions();
}

function getDistanceColor(distance) {
    if (distance < 1) return '#2196F3';  // Low distance
    if (distance < 5) return '#4CAF50';  // Medium distance
    return '#FF9800';                    // High distance
}

function cleanupOldSessions() {
    const now = new Date();
    for (const sessionId in speedHistory) {
        const timeDiff = now - speedHistory[sessionId].lastUpdate;
        if (timeDiff > 300000) { // 5 minutes timeout
            // Remove the container instead of just the element
            const container = document.getElementById(`speed-container-${sessionId}`);
            if (container) {
                container.remove();
            }
            delete speedHistory[sessionId];
        }
    }
}

function getColorForUser(sessionId) {
    if (!userColors[sessionId]) {
        // Generate random RGB values within specific ranges to ensure visibility
        // We'll avoid very light colors (high values) and very dark colors (low values)
        const ranges = [
            // R, G, B ranges for different color characteristics
            [150, 255, 0, 100, 0, 100],    // Reddish
            [0, 100, 150, 255, 0, 100],    // Greenish
            [0, 100, 0, 100, 150, 255],    // Bluish
            [150, 255, 150, 255, 0, 100],  // Yellowish
            [150, 255, 0, 100, 150, 255],  // Purplish
            [0, 100, 150, 255, 150, 255]   // Cyanish
        ];

        // Use the sessionId to consistently select a range
        const hash = sessionId.split('').reduce((acc, char) => {
            return char.charCodeAt(0) + ((acc << 5) - acc);
        }, 0);

        const selectedRange = ranges[Math.abs(hash) % ranges.length];

        // Generate random RGB values within the selected range
        const r = getRandomInRange(selectedRange[0], selectedRange[1]);
        const g = getRandomInRange(selectedRange[2], selectedRange[3]);
        const b = getRandomInRange(selectedRange[4], selectedRange[5]);

        userColors[sessionId] = `rgb(${r}, ${g}, ${b})`;
    }
    return userColors[sessionId];
}

function createDisplayId(sessionId, personName) {
    if (!personName) return sessionId;

    // Check if the sessionId already starts with the person name to avoid duplication
    if (sessionId.startsWith(personName + '_')) {
        return sessionId;  // Session ID already includes the name properly
    } else {
        return `${personName}_${sessionId}`;  // Combine name with session ID
    }
}

function parseSessionId(sessionId) {
    const [name, timestamp] = sessionId.split('_');
    if (!timestamp) return { name: sessionId, timestamp: null };

    const year = timestamp.substr(0, 4);
    const month = timestamp.substr(4, 2);
    const day = timestamp.substr(6, 2);
    const hour = timestamp.substr(9, 2);
    const minute = timestamp.substr(11, 2);
    const second = timestamp.substr(13, 2);

    return {
        name: name,
        formattedTime: `${day}/${month}/${year} ${hour}:${minute}:${second}`
    };
}

function resetMap() {
    addDebugMessage('Reset Map button clicked', 'system');

    try {
        // Flash the button for visual feedback
        const resetBtn = document.getElementById('resetMapBtn');
        if (resetBtn) {
            resetBtn.classList.add('reset-flash');
            setTimeout(() => {
                resetBtn.classList.remove('reset-flash');
            }, 500);
        }

        // Clear all data structures
        trackPoints = {};
        speedHistory = {};
        sessionPersonNames = {}; // Also clear session person names
        userColors = {};

        // Remove all session containers from the speed display
        const speedDisplay = document.getElementById('speedDisplay');
        const sessionContainers = speedDisplay.querySelectorAll('.session-container');
        sessionContainers.forEach(container => {
            container.remove();
        });

        // Reset the default speed display stats
        const defaultStatsElements = {
            'currentSpeed': '0.0',
            'maxSpeed': '0.0',
            'avgSpeed': '0.0',
            'movingAvg': '0.0',
            'elevationGain': '0.0',
            'totalDistance': '0.0', // Add the new totalDistance field
            'heartRate': '0'
        };

        // Reset all the default stats values
        Object.entries(defaultStatsElements).forEach(([id, value]) => {
            const element = document.getElementById(id);
            if (element) {
                element.textContent = value;
                if (id === 'elevationGain') {
                    element.style.color = '#4CAF50'; // Reset elevation color
                } else if (id === 'heartRate') {
                    element.style.color = '#999'; // Reset heart rate color to gray
                } else if (id === 'totalDistance') {
                    element.style.color = '#2196F3'; // Reset distance color
                } else {
                    element.style.color = '#2196F3'; // Reset speed colors
                }
            }
        });

        // Show the default stats grid if it's not already visible
        const defaultStatsGrid = speedDisplay.querySelector('.stats-grid:not([id^="speed-grid-"])');
        if (!defaultStatsGrid) {
            // Create default stats grid if it doesn't exist
            const defaultGrid = document.createElement('div');
            defaultGrid.className = 'stats-grid';
            defaultGrid.innerHTML = `
                <div class="stat-box">
                    <div class="speed-label">Current Speed</div>
                    <div class="speed-value" id="currentSpeed">0.0</div>
                    <div class="speed-unit">km/h</div>
                </div>
                <div class="stat-box">
                    <div class="speed-label">Maximum Speed</div>
                    <div class="speed-value" id="maxSpeed">0.0</div>
                    <div class="speed-unit">km/h</div>
                </div>
                <div class="stat-box">
                    <div class="speed-label">Average Speed</div>
                    <div class="speed-value" id="avgSpeed">0.0</div>
                    <div class="speed-unit">km/h</div>
                </div>
                <div class="stat-box">
                    <div class="speed-label">Moving Average</div>
                    <div class="speed-value" id="movingAvg">0.0</div>
                    <div class="speed-unit">km/h</div>
                </div>
                <div class="stat-box">
                    <div class="elevation-label">Elevation Gain</div>
                    <div class="elevation-value" id="elevationGain">0.0</div>
                    <div class="elevation-unit">m</div>
                </div>
                <div class="stat-box">
                    <div class="speed-label">Distance</div>
                    <div class="elevation-value" id="totalDistance">0.0</div>
                    <div class="elevation-unit">km</div>
                </div>
                <div class="stat-box">
                    <div class="speed-label">Heart Rate</div>
                    <div class="speed-value" id="heartRate">0</div>
                    <div class="speed-unit">bpm</div>
                </div>
            `;
            speedDisplay.appendChild(defaultGrid);
        }

        // Check if map is initialized before trying to remove layers
        if (!isMapLoaded()) {
            console.warn("Map not fully initialized during reset attempt");
            addDebugMessage("Map not fully initialized - skipping layer removal", "warning");
            return;
        }

            // Clear map layers safely
            Object.keys(polylines).forEach(key => {
                if (polylines[key] && map.hasLayer(polylines[key])) {
                    map.removeLayer(polylines[key]);
                }
            });

            Object.keys(startMarkers).forEach(key => {
                if (startMarkers[key] && map.hasLayer(startMarkers[key])) {
                    map.removeLayer(startMarkers[key]);
                }
            });

            Object.keys(endMarkers).forEach(key => {
                if (endMarkers[key] && map.hasLayer(endMarkers[key])) {
                    map.removeLayer(endMarkers[key]);
                }
            });

            polylines = {};
            startMarkers = {};
            endMarkers = {};

            if (gpxPolyline && map.hasLayer(gpxPolyline)) {
                map.removeLayer(gpxPolyline);
                gpxPolyline = null;
            }

            // Reset charts
            altitudeChart.data.datasets = [];
            speedChart.data.datasets = [];
            altitudeChart.update();
            speedChart.update();

            // Reset map view
            map.setView([48.1818798, 16.3607528], 10);

            // Clear file input
            const fileInput = document.getElementById('gpxFile');
            if (fileInput) {
                fileInput.value = '';
            }

            addDebugMessage('Map reset completed successfully', 'system');
        } catch (error) {
            console.error('Error during map reset:', error);
            addDebugMessage(`Error during map reset: ${error.message}`, 'error');
        }
    }

    // Function to check if the map has loaded properly
    function isMapLoaded() {
        return map && typeof map.getCenter === 'function';
    }

    // GPX file handling
    async function loadGPX() {
        const fileInput = document.getElementById('gpxFile');
        const file = fileInput.files[0];

        if (!file) {
            alert('Please select a GPX file first.');
            addDebugMessage('GPX file load attempted but no file selected', 'error');
            return;
        }

        addDebugMessage(`Loading GPX file: ${file.name}`, 'gpx');

        try {
            const gpxText = await readFileAsync(file);
            addDebugMessage(`GPX file loaded: ${file.name} (${Math.round(gpxText.length / 1024)} KB)`, 'gpx');

            const parser = new DOMParser();
            const gpxDoc = parser.parseFromString(gpxText, "text/xml");

            // Reset previous GPX data
            if (gpxPolyline && map.hasLayer(gpxPolyline)) {
                map.removeLayer(gpxPolyline);
            }

            const trackPoints = processGPXTrack(gpxDoc);
            if (!trackPoints.coordinates.length) {
                alert('No valid track points found in GPX file.');
                addDebugMessage('No valid track points found in GPX file', 'error');
                return;
            }

            addDebugMessage(`Processed GPX track: ${trackPoints.coordinates.length} points`, 'gpx');
            visualizeGPXData(trackPoints);

        } catch (error) {
            console.error('Error processing GPX file:', error);
            alert('Error processing GPX file. Please check the console for details.');
            addDebugMessage(`Error processing GPX file: ${error.message}`, 'error');
        }
    }

    function readFileAsync(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = (e) => resolve(e.target.result);
            reader.onerror = (e) => reject(e);
            reader.readAsText(file);
        });
    }

    function processGPXTrack(gpxDoc) {
        const trackPoints = {
            coordinates: [],
            altitudes: [],
            times: [],
            distances: [0],
            speeds: [],
            customData: [] // Store additional GPX data
        };

        addDebugMessage('Starting GPX track processing', 'gpx');

        const points = gpxDoc.getElementsByTagName('trkpt');
        let lastCoord = null;
        let cumulativeDistance = 0;

        for (let i = 0; i < points.length; i++) {
            const point = points[i];
            const lat = parseFloat(point.getAttribute('lat'));
            const lon = parseFloat(point.getAttribute('lon'));
            
            trackPoints.coordinates.push([lat, lon]);

            // Extract elevation
            const ele = point.getElementsByTagName('ele')[0];
            const altitude = ele ? parseFloat(ele.textContent) : 0;
            trackPoints.altitudes.push(altitude);

            // Extract time
            const time = point.getElementsByTagName('time')[0];
            const timestamp = time ? new Date(time.textContent) : new Date();
            trackPoints.times.push(timestamp);

            // Extract speed - try multiple sources and convert from m/s to km/h
            let speed = 0;
            let speedMPS = 0;
            
            // Try direct speed element first
            const speedElement = point.getElementsByTagName('speed')[0];
            if (speedElement) {
                speedMPS = parseFloat(speedElement.textContent);
                speed = (speedMPS / 1000) * 3600; // Convert m/s to km/h
            } else {
                // Try custom extension speed
                const customSpeed = point.getElementsByTagName('custom:speed')[0];
                if (customSpeed) {
                    speedMPS = parseFloat(customSpeed.textContent);
                    speed = (speedMPS / 1000) * 3600; // Convert m/s to km/h
                }
            }
            trackPoints.speeds.push(speed);

            // Log speed conversion for first few points
            if (i < 3 && speedMPS > 0) {
                addDebugMessage(`Speed conversion point ${i}: ${speedMPS.toFixed(2)} m/s → ${speed.toFixed(2)} km/h`, 'gpx');
            }

            // Extract distance from custom extensions
            let distance = 0;
            const customDistance = point.getElementsByTagName('custom:distance')[0];
            if (customDistance) {
                distance = parseFloat(customDistance.textContent) / 1000; // Convert to km
            } else {
                // Calculate cumulative distance if not provided
                if (lastCoord) {
                    const distanceMeters = map.distance(lastCoord, [lat, lon]);
                    cumulativeDistance += distanceMeters;
                    distance = cumulativeDistance / 1000; // Convert to km
                }
            }
            trackPoints.distances.push(distance);

            // Store additional custom data
            const customData = {
                lat: lat,
                lng: lon,
                altitude: altitude,
                speed: speed,
                distance: distance,
                timestamp: timestamp,
                lap: 0,
                type: 'gpx'
            };

            // Extract lap information if available
            const customLap = point.getElementsByTagName('custom:lap')[0];
            if (customLap) {
                customData.lap = parseInt(customLap.textContent);
            }

            // Extract activity type if available
            const customType = point.getElementsByTagName('custom:type')[0];
            if (customType) {
                customData.type = customType.textContent;
            }

            trackPoints.customData.push(customData);
            lastCoord = [lat, lon];
        }

        // Remove the first distance entry (which is always 0)
        if (trackPoints.distances.length > 0) {
            trackPoints.distances.shift();
        }

        // Log summary stats
        const totalDistance = trackPoints.distances[trackPoints.distances.length - 1] || 0;
        let minAlt = Infinity, maxAlt = -Infinity;
        if (trackPoints.altitudes.length > 0) {
            minAlt = Math.min(...trackPoints.altitudes);
            maxAlt = Math.max(...trackPoints.altitudes);
        }

        const maxSpeed = trackPoints.speeds.length > 0 ? Math.max(...trackPoints.speeds) : 0;
        const avgSpeed = trackPoints.speeds.length > 0 ? 
            trackPoints.speeds.reduce((sum, speed) => sum + speed, 0) / trackPoints.speeds.length : 0;

        addDebugMessage(`GPX track processed: ${trackPoints.coordinates.length} points,
            Distance: ${totalDistance.toFixed(2)} km,
            Altitude range: ${minAlt.toFixed(1)}m - ${maxAlt.toFixed(1)}m,
            Max speed: ${maxSpeed.toFixed(1)} km/h,
            Avg speed: ${avgSpeed.toFixed(1)} km/h,
            Speed conversion: m/s to km/h applied`, 'gpx');

        return trackPoints;
    }

function visualizeGPXData(gpxTrackPoints) {
    addDebugMessage('Visualizing GPX data on map and charts', 'gpx');

    // Draw track on map
    gpxPolyline = L.polyline(gpxTrackPoints.coordinates, {
        color: 'red',
        weight: 3
    }).addTo(map);

    // Fit map to track bounds
    map.fitBounds(gpxPolyline.getBounds());

    const bounds = gpxPolyline.getBounds();
    addDebugMessage(`Map bounds adjusted to: SW(${bounds.getSouthWest().lat.toFixed(4)}, ${bounds.getSouthWest().lng.toFixed(4)})
        NE(${bounds.getNorthEast().lat.toFixed(4)}, ${bounds.getNorthEast().lng.toFixed(4)})`, 'gpx');

    // Update altitude chart
    if (gpxTrackPoints.altitudes.length > 0) {
        const altDatasetIndex = altitudeChart.data.datasets.findIndex(
            dataset => dataset.label === 'GPX Track'
        );

        if (altDatasetIndex !== -1) {
            altitudeChart.data.datasets.splice(altDatasetIndex, 1);
        }

        altitudeChart.data.datasets.push({
            label: 'GPX Track',
            borderColor: 'red',
            fill: false,
            data: gpxTrackPoints.distances.map((d, i) => ({
                x: d,
                y: gpxTrackPoints.altitudes[i]
            }))
        });

        // Update speed chart with extracted speeds
        updateSpeedChartFromGPX(gpxTrackPoints);

        // Store GPX track points for interactive features using the rich custom data
        const interactiveGPXPoints = gpxTrackPoints.customData.map((customPoint, i) => ({
            lat: customPoint.lat,
            lng: customPoint.lng,
            distance: customPoint.distance,
            altitude: customPoint.altitude,
            speed: customPoint.speed,
            averageSpeed: customPoint.speed, // Use current speed as average for GPX
            maxSpeed: Math.max(...gpxTrackPoints.speeds), // Global max for this track
            movingAverageSpeed: customPoint.speed,
            cumulativeElevationGain: 0, // Could be calculated if needed
            heartRate: 0, // GPX doesn't typically have heart rate
            timestamp: customPoint.timestamp,
            personName: 'GPX Track',
            lap: customPoint.lap,
            activityType: customPoint.type
        }));

        // Store in the GLOBAL trackPoints object for interactive features
        trackPoints['GPX Track'] = interactiveGPXPoints;

        addDebugMessage(`Stored ${interactiveGPXPoints.length} GPX track points for interactive features in global trackPoints`, 'gpx');
        addDebugMessage(`Global trackPoints now contains: ${Object.keys(trackPoints).join(', ')}`, 'gpx');

        // Show the total distance in the UI
        const totalDistance = gpxTrackPoints.distances[gpxTrackPoints.distances.length - 1];
        const totalDistanceElement = document.getElementById('totalDistance');
        if (totalDistanceElement) {
            totalDistanceElement.textContent = totalDistance.toFixed(2);
            totalDistanceElement.style.color = getDistanceColor(totalDistance);
        }

        requestAnimationFrame(() => {
            altitudeChart.update('none');
            speedChart.update('none');
        });
    }
}

    function updateSpeedChartFromGPX(gpxTrackPoints) {
        const speeds = [];

        // Use extracted speeds if available, otherwise calculate from time/distance
        if (gpxTrackPoints.speeds && gpxTrackPoints.speeds.length > 0) {
            // Use the speeds extracted from GPX extensions
            for (let i = 0; i < gpxTrackPoints.speeds.length; i++) {
                if (gpxTrackPoints.distances[i] !== undefined && gpxTrackPoints.speeds[i] !== undefined) {
                    speeds.push({
                        x: gpxTrackPoints.distances[i],
                        y: gpxTrackPoints.speeds[i]
                    });
                }
            }
            addDebugMessage(`Speed chart updated with ${speeds.length} extracted speed points from GPX`, 'gpx');
        } else if (gpxTrackPoints.times && gpxTrackPoints.times.length > 1) {
            // Fallback to calculating speeds from time/distance
            for (let i = 1; i < gpxTrackPoints.times.length; i++) {
                const timeDiff = (gpxTrackPoints.times[i] - gpxTrackPoints.times[i - 1]) / 1000; // seconds
                const dist = gpxTrackPoints.distances[i] - gpxTrackPoints.distances[i - 1]; // kilometers

                if (timeDiff > 0) {
                    // Convert to km/h
                    const speed = (dist / timeDiff) * 3600;
                    speeds.push({
                        x: gpxTrackPoints.distances[i],
                        y: speed
                    });
                }
            }
            addDebugMessage(`Speed chart updated with ${speeds.length} calculated speed points from GPX`, 'gpx');
        }

        if (speeds.length > 0) {
            const speedDatasetIndex = speedChart.data.datasets.findIndex(
                dataset => dataset.label === 'GPX Track'
            );

            if (speedDatasetIndex !== -1) {
                speedChart.data.datasets.splice(speedDatasetIndex, 1);
            }

            speedChart.data.datasets.push({
                label: 'GPX Track',
                borderColor: 'red',
                fill: false,
                data: speeds
            });
        }
    }

    // Debug popup functions
    function toggleDebugPopup() {
        const popup = document.getElementById('debugPopup');
        const overlay = document.getElementById('debugOverlay');
        const button = document.getElementById('debugToggle');

        if (popup.style.display === 'flex') {
            popup.style.display = 'none';
            overlay.style.display = 'none';
            button.textContent = 'Show Debug Data';
        } else {
            popup.style.display = 'flex';
            overlay.style.display = 'block';
            button.textContent = 'Hide Debug Data';

            // Scroll to bottom when opening
            const content = document.getElementById('debugContent');
            content.scrollTop = content.scrollHeight;
        }
    }

    function addDebugMessage(message, type) {
        if (debugPaused) return;

        const now = new Date();
        const timestamp = now.toISOString().split('T')[1].split('.')[0];

        debugMessages.push({
            timestamp: timestamp,
            message: message,
            type: type
        });

        // Limit the number of messages to prevent browser slowdown
        if (debugMessages.length > maxDebugMessages) {
            debugMessages.shift();
        }

        updateDebugContent();
    }

    function updateDebugContent() {
        const content = document.getElementById('debugContent');
        if (!content) return;

        const filterText = document.getElementById('debugFilter').value.toLowerCase();
        const wasAtBottom = content.scrollHeight - content.clientHeight <= content.scrollTop + 5;

        let html = '';
        debugMessages.forEach(entry => {
            if (filterText && !entry.message.toLowerCase().includes(filterText) &&
                !entry.type.toLowerCase().includes(filterText)) {
                return;
            }

            let messageClass = 'message';
            if (entry.type === 'update') messageClass += ' update-message';
            if (entry.type === 'history_batch') messageClass += ' batch-message';
            if (entry.type === 'error') messageClass += ' error-message';

            html += `<div class="${messageClass}">
                <span class="message-timestamp">[${entry.timestamp}]</span>
                <span class="message-type">[${entry.type}]</span>
                <pre>${entry.message}</pre>
            </div>`;
        });

        content.innerHTML = html;

        // If previously at bottom, scroll to bottom after update
        if (wasAtBottom) {
            content.scrollTop = content.scrollHeight;
        }
    }

    function filterDebugMessages() {
        updateDebugContent();
    }

    function clearDebugLog() {
        debugMessages = [];
        updateDebugContent();
        addDebugMessage('Debug log cleared', 'system');
    }

    function pauseDebugLog() {
        debugPaused = !debugPaused;
        const pauseButton = document.getElementById('pauseButton');
        pauseButton.textContent = debugPaused ? 'Resume' : 'Pause';

        if (!debugPaused) {
            addDebugMessage('Debug logging resumed', 'system');
        }
    }

    function exportDebugLog() {
        const content = debugMessages.map(entry =>
            `[${entry.timestamp}] [${entry.type}] ${entry.message}`
        ).join('\n');

        const blob = new Blob([content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);

        const a = document.createElement('a');
        a.href = url;
        a.download = `debug-log-${new Date().toISOString().split('.')[0].replace(/[:.]/g, '-')}.txt`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);

        addDebugMessage('Debug log exported', 'system');
    }

    function handleSessionList(sessions) {
    console.log("Raw sessions data:", sessions);
    addDebugMessage(`Raw sessions data: ${JSON.stringify(sessions)}`, 'debug');

    if (!Array.isArray(sessions)) {
        addDebugMessage('Sessions data not in expected format', 'error');
        return;
    }

    availableSessions = sessions;
    updateSessionList();

    addDebugMessage(`Processed ${sessions.length} sessions`, 'system');
}

    // Toggle session visibility on map and charts
    function toggleSessionVisibility(sessionId) {
        addDebugMessage(`Toggle visibility requested for session ${sessionId}`, 'system');

        // First, find if there's a person name associated with this session
        const personName = sessionPersonNames[sessionId] || "";
        const displayId = createDisplayId(sessionId, personName);

        // Find all datasets in charts that match this session or display ID
        let currentVisibility = null;

        // Check altitude chart first
        const altDatasets = altitudeChart.data.datasets;
        for (let i = 0; i < altDatasets.length; i++) {
            if (altDatasets[i].label === displayId ||
                altDatasets[i].label.includes(sessionId)) {
                // Set initial visibility based on first found dataset
                if (currentVisibility === null) {
                    currentVisibility = altitudeChart.isDatasetVisible(i);
                }
                // Toggle visibility
                altitudeChart.setDatasetVisibility(i, !currentVisibility);
            }
        }

        // Then check speed chart
        const speedDatasets = speedChart.data.datasets;
        for (let i = 0; i < speedDatasets.length; i++) {
            if (speedDatasets[i].label === displayId ||
                speedDatasets[i].label.includes(sessionId)) {
                // Toggle visibility (using the same as altitude chart)
                speedChart.setDatasetVisibility(i, !currentVisibility);
            }
        }

        // Update charts
        altitudeChart.update();
        speedChart.update();

        // Toggle map elements visibility
        toggleMapElementsVisibility(sessionId, !currentVisibility);

        // If we didn't find any datasets, default to showing
        if (currentVisibility === null) {
            toggleMapElementsVisibility(sessionId, true);
            addDebugMessage(`No datasets found for session ${sessionId}, defaulting to visible`, 'warning');
        } else {
            addDebugMessage(`Toggled visibility of session ${sessionId} to ${!currentVisibility ? 'visible' : 'hidden'}`, 'system');
        }

        // Update the eye button in the session list
        updateVisibilityButtonAppearance(sessionId, !currentVisibility);
    }

    // Update the session list UI
    function updateSessionList() {
        const sessionsList = document.getElementById('sessionsList');

        if (!availableSessions || availableSessions.length === 0) {
            sessionsList.innerHTML = '<p class="no-sessions">No sessions found.</p>';
            return;
        }

        let html = '';

        availableSessions.forEach(session => {
            const sessionId = session.sessionId;
            const isActive = session.isActive;

            const parsedSession = parseSessionId(sessionId);
            const name = parsedSession.name;
            const time = parsedSession.formattedTime || 'Unknown time';
            const color = getColorForUser(sessionId);

            html += `
                <div class="session-item ${isActive ? 'active' : ''}" data-session-id="${sessionId}">
                    <div class="session-item-info">
                        <div class="session-item-name">
                            ${name}
                            ${isActive ? '<span class="active-badge" title="Active Session">⚡</span>' : ''}
                        </div>
                        <div class="session-item-time">${time}</div>
                    </div>
                    <div class="session-actions">
                        <button class="session-action-btn toggle-visibility"
                                onclick="toggleSessionVisibility('${sessionId}')"
                                title="Toggle Visibility">
                            👁
                        </button>
                        <button class="session-action-btn delete"
                                onclick="confirmDeleteSession('${sessionId}')"
                                title="Delete Session"
                                ${isActive ? 'disabled' : ''}>
                            🗑
                        </button>
                    </div>
                </div>
            `;
        });

        sessionsList.innerHTML = html;
    }

    // Show confirmation dialog before deleting a session
    function confirmDeleteSession(sessionId) {
        // Check if session is active
        const session = availableSessions.find(s => s.sessionId === sessionId);
        if (session && session.isActive) {
            showNotification('Cannot delete an active session. Wait for the session to complete first.', 'warning');
            addDebugMessage(`Attempted to delete active session: ${sessionId}`, 'warning');
            return;
        }

        // Create overlay
        const overlay = document.createElement('div');
        overlay.id = 'confirmOverlay';
        overlay.style.position = 'fixed';
        overlay.style.top = '0';
        overlay.style.left = '0';
        overlay.style.width = '100%';
        overlay.style.height = '100%';
        overlay.style.backgroundColor = 'rgba(0, 0, 0, 0.5)';
        overlay.style.zIndex = '2000';

        // Create confirmation dialog
        const dialog = document.createElement('div');
        dialog.className = 'confirm-delete';

        const parsedSession = parseSessionId(sessionId);

        dialog.innerHTML = `
            <h3>Delete Session</h3>
            <p>Are you sure you want to delete "${parsedSession.name}" session?</p>
            <p><strong>This action cannot be undone.</strong></p>
            <div class="confirm-buttons">
                <button class="confirm-btn cancel" onclick="closeConfirmDialog()">Cancel</button>
                <button class="confirm-btn delete" onclick="deleteSession('${sessionId}')">Delete</button>
            </div>
        `;

        // Add to document
        document.body.appendChild(overlay);
        document.body.appendChild(dialog);

        addDebugMessage(`Showing delete confirmation for session ${sessionId}`, 'system');
    }

    // Show notification message
    function showNotification(message, type = 'info') {
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        notification.innerHTML = message;

        // Style the notification
        Object.assign(notification.style, {
            position: 'fixed',
            top: '20px',
            right: '20px',
            padding: '12px 20px',
            backgroundColor: type === 'warning' ? '#ff9800' : '#4CAF50',
            color: 'white',
            borderRadius: '4px',
            boxShadow: '0 2px 10px rgba(0,0,0,0.2)',
            zIndex: '3000',
            fontFamily: 'Arial, sans-serif',
            fontSize: '14px',
            maxWidth: '300px'
        });

        document.body.appendChild(notification);

        // Remove after 3 seconds
        setTimeout(() => {
            notification.style.opacity = '0';
            notification.style.transition = 'opacity 0.5s';
            setTimeout(() => {
                document.body.removeChild(notification);
            }, 500);
        }, 3000);
    }

    function toggleSessionPanel() {
        const panel = document.getElementById('sessionManagerPanel');
        const content = panel.querySelector('.panel-content');
        const toggleBtn = document.getElementById('toggleSessionPanel');

        if (sessionPanelVisible) {
            content.style.display = 'none';
            toggleBtn.textContent = '+';
        } else {
            content.style.display = 'block';
            toggleBtn.textContent = '-';
        }

        sessionPanelVisible = !sessionPanelVisible;
    }

    // Request session list from server
    function requestSessionList() {
        if (websocket && websocket.readyState === WebSocket.OPEN) {
            websocket.send(JSON.stringify({
                type: 'request_sessions'
            }));

            addDebugMessage('Requested session list from server', 'system');
        } else {
            addDebugMessage('WebSocket not connected, cannot request sessions', 'error');
        }
    }

    // Handle session deletion response from server
	function handleSessionDeleted(sessionId) {
		// Add debug logging
		addDebugMessage(`Handling deletion of session ${sessionId}`, 'system');

		// Remove from available sessions list - fix the filter
		availableSessions = availableSessions.filter(session => session.sessionId !== sessionId);

		// Remove from trackPoints
		if (trackPoints[sessionId]) {
			delete trackPoints[sessionId];
		}

		// Remove from speedHistory
		if (speedHistory[sessionId]) {
			delete speedHistory[sessionId];
		}

		// Remove from charts - the key fix is here!
		// Use includes() instead of exact match to handle cases where the label includes the person name
		const altDatasets = altitudeChart.data.datasets;
		for (let i = altDatasets.length - 1; i >= 0; i--) {
			if (altDatasets[i].label.includes(sessionId)) {
				addDebugMessage(`Removing altitude dataset: ${altDatasets[i].label}`, 'system');
				altDatasets.splice(i, 1);
			}
		}
		altitudeChart.update();

		const speedDatasets = speedChart.data.datasets;
		for (let i = speedDatasets.length - 1; i >= 0; i--) {
			if (speedDatasets[i].label.includes(sessionId)) {
				addDebugMessage(`Removing speed dataset: ${speedDatasets[i].label}`, 'system');
				speedDatasets.splice(i, 1);
			}
		}
		speedChart.update();

		// Remove from map
		if (polylines[sessionId]) {
			map.removeLayer(polylines[sessionId]);
			delete polylines[sessionId];
		}

		if (startMarkers[sessionId]) {
			map.removeLayer(startMarkers[sessionId]);
			delete startMarkers[sessionId];
		}

		if (endMarkers[sessionId]) {
			map.removeLayer(endMarkers[sessionId]);
			delete endMarkers[sessionId];
		}

		// Remove from speed display - fix the container ID
		const speedContainer = document.getElementById(`speed-container-${sessionId}`);
		if (speedContainer) {
			speedContainer.remove();
		}

		// Clean up session name mapping
		if (sessionPersonNames[sessionId]) {
			delete sessionPersonNames[sessionId];
		}

		// Update session list UI
		updateSessionList();

		addDebugMessage(`Session ${sessionId} deleted and removed from all displays`, 'system');
		showNotification(`Session ${sessionId} deleted successfully`, 'info');
	}

    // Send delete request to server
	function deleteSession(sessionId) {
		if (websocket && websocket.readyState === WebSocket.OPEN) {
			const deleteRequest = {
				type: 'delete_session',
				sessionId: sessionId
			};

			console.log("Sending delete request:", deleteRequest);
			addDebugMessage(`Sending delete request: ${JSON.stringify(deleteRequest)}`, 'system');

			websocket.send(JSON.stringify(deleteRequest));
		} else {
			console.error("WebSocket not connected");
			addDebugMessage('WebSocket not connected, cannot delete session', 'error');
			showNotification('Cannot delete: Connection lost', 'warning');
		}

		closeConfirmDialog();
	}

	// Close the confirmation dialog
    function closeConfirmDialog() {
        const overlay = document.getElementById('confirmOverlay');
        const dialog = document.querySelector('.confirm-delete');

        if (overlay) overlay.remove();
        if (dialog) dialog.remove();
    }

    document.addEventListener('DOMContentLoaded', () => {
        try {
            console.log("DOM content loaded, initializing application...");

            // Initialize the map only once
            initMap();

            // Add event listener to the reset button for visual feedback
            const resetBtn = document.getElementById('resetMapBtn');
            if (resetBtn) {
                resetBtn.addEventListener('click', function() {
                    // Add visual feedback
                    this.style.backgroundColor = '#e6e6e6';
                    setTimeout(() => {
                        this.style.backgroundColor = 'white';
                    }, 300);
                });
            }
            
            addDebugMessage('Running Tracker application initialized with interactive chart features', 'system');
            
            // Add chart container event listeners for mouse leave
            setupChartEventListeners();
            
            // Test chart interaction setup
            setTimeout(() => {
                if (altitudeChart && speedChart) {
                    addDebugMessage('Charts initialized successfully with hover support', 'system');
                } else {
                    addDebugMessage('Chart initialization may have failed', 'error');
                }
            }, 1000);
            
        } catch (error) {
            console.error("Error during application initialization:", error);
            alert("There was an error initializing the application. Please check the console for details.");
        }
    });

// Setup additional event listeners for chart interactions
function setupChartEventListeners() {
    const altitudeContainer = document.getElementById('altitudeChartContainer');
    const speedContainer = document.getElementById('speedChartContainer');

    // Hide hover effects when mouse leaves chart containers
    [altitudeContainer, speedContainer].forEach(container => {
        if (container) {
            container.addEventListener('mouseleave', () => {
                hideHoverMarker();
                hideInfoPopup();
            });
        }
    });

    // Also hide when mouse leaves the charts area entirely
    const chartsContainer = document.querySelector('.charts-container');
    if (chartsContainer) {
        chartsContainer.addEventListener('mouseleave', () => {
            hideHoverMarker();
            hideInfoPopup();
        });
    }
}
