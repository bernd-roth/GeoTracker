// Global variables
let map;
let websocket;
let polylines = {};
let startMarkers = {};
let endMarkers = {};
let altitudeChart;
let speedChart;
let speedHistory = {};
let elevationHistory = {};
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

// Weather statistics tracking
let weatherStats = {
    totalUpdates: 0,
    lastTemperature: null,
    lastUpdate: null,
    temperatureRange: { min: null, max: null }
};

// GPX-related variables for MapLibre GL JS
let gpxLayerId = null;

function getRandomInRange(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function initMap() {
    try {
        console.log("Initializing MapLibre GL JS map...");
        const initialLocation = [0, 0]; // Center of the world [lng, lat]

        map = new maplibregl.Map({
            container: 'map',
            style: 'https://tiles.openfreemap.org/styles/liberty', // Free vector tiles with street names
            center: initialLocation,
            zoom: 1, // Low zoom level to show the whole earth
            attributionControl: false
        });

        map.on('load', () => {
            console.log("MapLibre GL JS map with vector tiles loaded successfully");

            // Add attribution
            map.addControl(new maplibregl.AttributionControl({
                customAttribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            }));

            initCharts();
            connectToWebSocket();
        });

        // If the vector style fails, fall back to OSM raster
        map.on('error', (e) => {
            console.error('Vector map failed, falling back to OSM raster:', e);
            addDebugMessage(`Vector map error, using OSM fallback: ${e.message}`, 'warning');
            initOSMRasterMap();
        });

    } catch (error) {
        console.error("Error initializing map:", error);
        addDebugMessage(`Map initialization error: ${error.message}`, 'error');
        initOSMRasterMap(); // Fallback to OSM raster
    }
}

// Fallback function for OSM raster map with better zoom
function initOSMRasterMap() {
    try {
        console.log("Initializing OSM raster fallback map...");
        const initialLocation = [0, 0]; // Center of the world

        if (map && map.remove) {
            map.remove(); // Clean up existing map
        }

        map = new maplibregl.Map({
            container: 'map',
            style: {
                version: 8,
                sources: {
                    'osm-tiles': {
                        type: 'raster',
                        tiles: [
                            'https://a.tile.openstreetmap.org/{z}/{x}/{y}.png',
                            'https://b.tile.openstreetmap.org/{z}/{x}/{y}.png',
                            'https://c.tile.openstreetmap.org/{z}/{x}/{y}.png'
                        ],
                        tileSize: 256
                    }
                },
                layers: [
                    {
                        id: 'osm-layer',
                        type: 'raster',
                        source: 'osm-tiles',
                        minzoom: 0,
                        maxzoom: 19
                    }
                ]
            },
            center: initialLocation,
            zoom: 1, // Low zoom level to show the whole earth
            attributionControl: false
        });

        map.on('load', () => {
            console.log("OSM raster fallback map loaded successfully");

            map.addControl(new maplibregl.AttributionControl({
                customAttribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            }));

            // Only call these if they haven't been called yet
            if (typeof initCharts === 'function' && !altitudeChart) {
                initCharts();
            }
            if (typeof connectToWebSocket === 'function' && (!websocket || websocket.readyState === WebSocket.CLOSED)) {
                connectToWebSocket();
            }
        });

        map.on('error', (e) => {
            console.error('OSM raster map error:', e);
            addDebugMessage(`OSM map error: ${JSON.stringify(e)}`, 'error');
        });

    } catch (fallbackError) {
        console.error("OSM raster fallback failed:", fallbackError);
        addDebugMessage(`Fallback map error: ${fallbackError.message}`, 'error');

        // Last resort: show error message
        const mapElement = document.getElementById('map');
        if (mapElement) {
            mapElement.innerHTML = `
                <div style="text-align: center; padding: 20px; color: red;">
                    <h3>Map Loading Failed</h3>
                    <p>Unable to load map tiles. Please check your internet connection.</p>
                    <button onclick="location.reload()" style="padding: 8px 16px; margin-top: 10px;">Reload Page</button>
                </div>
            `;
        }
    }
}

function initCharts() {
    const baseChartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        interaction: {
            intersect: false,
            mode: 'index'
        },
        elements: {
            line: {
                tension: 0
            },
            point: {
                radius: 0,
                hoverRadius: 6
            }
        },
        plugins: {
            legend: {
                onClick: handleLegendClick
            },
            tooltip: {
                enabled: false
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
        onHover: (event, activeElements) => {
            console.log('🎯 CHART HOVER EVENT FIRED:', {
                elementCount: activeElements.length,
                hasEvent: !!event,
                eventType: event?.type,
                chartCanvas: event?.native?.target?.id || 'unknown'
            });

            addDebugMessage(`Chart hover event triggered with ${activeElements.length} active elements`, 'interaction');

            if (event.native && event.native.target) {
                event.native.target.style.cursor = activeElements.length > 0 ? 'pointer' : 'default';
            }

            handleChartHover(event, activeElements, 'altitude');
        },
        onClick: (event, activeElements) => {
            console.log('🎯 CHART CLICK EVENT FIRED:', activeElements.length, 'elements');
            addDebugMessage(`Chart click event triggered with ${activeElements.length} active elements`, 'interaction');
            handleChartClick(event, activeElements, 'altitude');
        }
    };

    try {
        altitudeChart = new Chart(
            document.getElementById('altitudeChart').getContext('2d'),
            {
                type: 'line',
                data: { datasets: [] },
                options: baseChartOptions
            }
        );
        console.log('✅ Altitude chart created successfully');
    } catch (error) {
        console.error('❌ Error creating altitude chart:', error);
        addDebugMessage(`Error creating altitude chart: ${error.message}`, 'error');
    }

    const speedChartOptions = {
        ...baseChartOptions,
        scales: {
            ...baseChartOptions.scales,
            y: {
                ...baseChartOptions.scales.y,
                title: {
                    display: true,
                    text: 'Speed (km/h)'
                }
            }
        },
        onHover: (event, activeElements) => {
            console.log('🎯 SPEED CHART HOVER EVENT FIRED:', {
                elementCount: activeElements.length,
                hasEvent: !!event,
                chartCanvas: event?.native?.target?.id || 'unknown'
            });

            addDebugMessage(`Speed chart hover event triggered with ${activeElements.length} active elements`, 'interaction');

            if (event.native && event.native.target) {
                event.native.target.style.cursor = activeElements.length > 0 ? 'pointer' : 'default';
            }

            handleChartHover(event, activeElements, 'speed');
        },
        onClick: (event, activeElements) => {
            console.log('🎯 SPEED CHART CLICK EVENT FIRED:', activeElements.length, 'elements');
            addDebugMessage(`Speed chart click event triggered with ${activeElements.length} active elements`, 'interaction');
            handleChartClick(event, activeElements, 'speed');
        }
    };

    try {
        speedChart = new Chart(
            document.getElementById('speedChart').getContext('2d'),
            {
                type: 'line',
                data: { datasets: [] },
                options: speedChartOptions
            }
        );
        console.log('✅ Speed chart created successfully');
    } catch (error) {
        console.error('❌ Error creating speed chart:', error);
        addDebugMessage(`Error creating speed chart: ${error.message}`, 'error');
    }

    createInfoPopup();

    setTimeout(() => {
        testChartSetup();
    }, 100);
}

function createInfoPopup() {
    const existingPopup = document.getElementById('chartInfoPopup');
    if (existingPopup) {
        existingPopup.remove();
        console.log('🗑️ Removed existing popup');
    }

    try {
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
            z-index: 9999 !important;
            display: none !important;
            pointer-events: none !important;
            max-width: 320px !important;
            line-height: 1.4 !important;
            color: #333 !important;
        `;

        document.body.appendChild(infoPopup);
        console.log('✅ Info popup created and added to DOM');
        addDebugMessage('Info popup element created and added to DOM', 'system');

        const verifyPopup = document.getElementById('chartInfoPopup');
        if (!verifyPopup) {
            throw new Error('Popup not found in DOM after creation');
        }

    } catch (error) {
        console.error('❌ Error creating info popup:', error);
        addDebugMessage(`Error creating info popup: ${error.message}`, 'error');
    }
}

function handleChartHover(event, activeElements, chartType) {
    console.log(`🔍 handleChartHover called for ${chartType} chart:`, {
        activeElementsCount: activeElements ? activeElements.length : 0,
        hasEvent: !!event,
        infoPopupExists: !!infoPopup
    });

    if (!activeElements || activeElements.length === 0) {
        console.log('❌ No active elements, hiding hover effects');
        hideHoverMarker();
        hideInfoPopup();
        return;
    }

    try {
        const element = activeElements[0];
        const datasetIndex = element.datasetIndex;
        const dataIndex = element.index;

        console.log(`📊 Processing hover:`, {
            datasetIndex,
            dataIndex,
            chartType
        });

        const chart = chartType === 'altitude' ? altitudeChart : speedChart;
        const dataset = chart.data.datasets[datasetIndex];

        if (!dataset) {
            console.log('❌ No dataset found at index:', datasetIndex);
            addDebugMessage(`No dataset found at index ${datasetIndex}`, 'warning');
            return;
        }

        console.log(`📈 Found dataset:`, {
            label: dataset.label,
            dataLength: dataset.data ? dataset.data.length : 0
        });

        let sessionId = dataset.label;
        let sessionTrackPoints = trackPoints[sessionId];

        if (!sessionTrackPoints) {
            if (sessionId.includes('_') && sessionId !== 'GPX Track') {
                const parts = sessionId.split('_');
                if (parts.length > 1) {
                    const extractedId = parts.slice(1).join('_');
                    sessionTrackPoints = trackPoints[extractedId];
                    if (sessionTrackPoints) {
                        sessionId = extractedId;
                        console.log('✅ Found using extracted ID:', sessionId);
                    }
                }
            }
        }

        if (!sessionTrackPoints) {
            console.log('❌ No track points found. Available sessions:', Object.keys(trackPoints));
            addDebugMessage(`No track points found for session: ${sessionId}`, 'warning');
            return;
        }

        if (!sessionTrackPoints[dataIndex]) {
            console.log('❌ No track point at index:', dataIndex, 'of', sessionTrackPoints.length);
            addDebugMessage(`No track point at index ${dataIndex}`, 'warning');
            return;
        }

        const point = sessionTrackPoints[dataIndex];

        console.log(`📍 Found point:`, {
            lat: point.lat,
            lng: point.lng,
            distance: point.distance,
            altitude: point.altitude
        });

        showHoverMarker(point.lat, point.lng, sessionId);
        showInfoPopup(event, point, sessionId, chartType);

        console.log('✅ Hover processing completed successfully');
        addDebugMessage(`Hover successful - Session: ${sessionId}, Point: ${dataIndex}`, 'interaction');

    } catch (error) {
        console.error('❌ Error in handleChartHover:', error);
        addDebugMessage(`Hover error: ${error.message}`, 'error');
    }
}

function handleChartClick(event, activeElements, chartType) {
    if (!activeElements || activeElements.length === 0) return;

    const element = activeElements[0];
    const datasetIndex = element.datasetIndex;
    const dataIndex = element.index;

    const chart = chartType === 'altitude' ? altitudeChart : speedChart;
    const dataset = chart.data.datasets[datasetIndex];

    if (!dataset) return;

    let sessionId = dataset.label;
    let sessionTrackPoints = trackPoints[sessionId];

    if (!sessionTrackPoints) {
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

    // Validate coordinates before using them
    if (!isValidCoordinate(point.lat, point.lng)) {
        addDebugMessage(`Invalid coordinates in chart click: lat=${point.lat}, lng=${point.lng}`, 'warning');
        return;
    }

    // Use MapLibre GL JS jumpTo method
    try {
        map.jumpTo({
            center: [point.lng, point.lat], // MapLibre uses [lng, lat]
            zoom: Math.max(map.getZoom(), 15)
        });

        addDebugMessage(`Chart click - Centered map on Session: ${sessionId}, Point: ${dataIndex}`, 'interaction');
    } catch (error) {
        addDebugMessage(`Error centering map on chart click: ${error.message}`, 'error');
    }
}

// Helper function to validate coordinates
function isValidCoordinate(lat, lng) {
    return !isNaN(lat) && !isNaN(lng) &&
           isFinite(lat) && isFinite(lng) &&
           lat >= -90 && lat <= 90 &&
           lng >= -180 && lng <= 180;
}

function showHoverMarker(lat, lng, sessionId) {
    // Validate coordinates first
    if (!isValidCoordinate(lat, lng)) {
        addDebugMessage(`Invalid coordinates for hover marker: lat=${lat}, lng=${lng}`, 'error');
        return;
    }

    // Remove existing hover marker
    if (hoverMarker) {
        hoverMarker.remove();
    }

    const sessionColor = getColorForUser(sessionId);

    try {
        // Create a MapLibre GL JS marker
        const markerElement = document.createElement('div');
        markerElement.style.cssText = `
            width: 20px;
            height: 20px;
            border-radius: 50%;
            background-color: ${sessionColor};
            border: 3px solid white;
            opacity: 0.8;
            box-shadow: 0 0 5px rgba(0,0,0,0.3);
        `;

        hoverMarker = new maplibregl.Marker({
            element: markerElement,
            anchor: 'center'
        })
            .setLngLat([lng, lat]) // MapLibre uses [lng, lat]
            .addTo(map);

        addDebugMessage(`Hover marker created at ${lat}, ${lng} with color ${sessionColor}`, 'interaction');
    } catch (error) {
        addDebugMessage(`Error creating hover marker: ${error.message}`, 'error');
    }
}

function hideHoverMarker() {
    if (hoverMarker) {
        hoverMarker.remove();
        hoverMarker = null;
    }
}

function hideInfoPopup() {
    if (infoPopup) {
        infoPopup.style.display = 'none';
        infoPopup.classList.remove('popup-visible');
    }
}

function getWeatherDescription(weatherCode) {
    const weatherCodes = {
        0: "Clear sky",
        1: "Mainly clear",
        2: "Partly cloudy",
        3: "Overcast",
        45: "Fog",
        48: "Depositing rime fog",
        51: "Light drizzle",
        53: "Moderate drizzle",
        55: "Dense drizzle",
        61: "Slight rain",
        63: "Moderate rain",
        65: "Heavy rain",
        71: "Slight snow",
        73: "Moderate snow",
        75: "Heavy snow",
        77: "Snow grains",
        80: "Slight rain showers",
        81: "Moderate rain showers",
        82: "Violent rain showers",
        85: "Slight snow showers",
        86: "Heavy snow showers",
        95: "Thunderstorm",
        96: "Thunderstorm with hail",
        99: "Thunderstorm with heavy hail"
    };

    return weatherCodes[weatherCode] || `Code ${weatherCode}`;
}

function getWindDirectionText(degrees) {
    if (degrees === undefined || degrees === null || degrees === 0) {
        return 'N/A';
    }

    const deg = parseFloat(degrees);
    if (isNaN(deg)) {
        return 'N/A';
    }

    const directions = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                       "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"];

    const normalizedDeg = ((deg % 360) + 360) % 360;
    const index = Math.round(normalizedDeg / 22.5) % 16;

    return `${directions[index]} (${normalizedDeg.toFixed(0)}°)`;
}

function formatWeatherTime(weatherTime) {
    if (!weatherTime) return "N/A";

    try {
        const date = new Date(weatherTime);
        return date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
    } catch (e) {
        return weatherTime;
    }
}

function getWeatherEmoji(weatherCode) {
    const weatherEmojis = {
        0: "☀️",   1: "🌤️",   2: "⛅",   3: "☁️",   45: "🌫️",  48: "🌫️",
        51: "🌦️",  53: "🌦️",  55: "🌧️",  61: "🌧️",  63: "🌧️",  65: "⛈️",
        71: "🌨️",  73: "❄️",  75: "❄️",  77: "❄️",  80: "🌦️",  81: "🌧️",
        82: "⛈️",  85: "🌨️",  86: "❄️",  95: "⛈️",  96: "⛈️",  99: "⛈️"
    };

    return weatherEmojis[weatherCode] || "🌤️";
}

function showInfoPopup(event, point, sessionId, chartType) {
    console.log('🖼️ showInfoPopup called:', {
        hasPopup: !!infoPopup,
        hasEvent: !!event,
        hasPoint: !!point,
        sessionId,
        chartType
    });

    if (!infoPopup) {
        console.error('❌ Info popup element not found!');
        addDebugMessage('Info popup element not found', 'error');
        createInfoPopup();
        if (!infoPopup) {
            console.error('❌ Failed to recreate popup');
            return;
        }
    }

    try {
        const personName = sessionPersonNames[sessionId] || "";
        let displayId = sessionId;
        if (personName) {
            displayId = createDisplayId(sessionId, personName);
        }

        const timeStr = point.timestamp ? point.timestamp.toLocaleTimeString() : 'N/A';
        const dateStr = point.timestamp ? point.timestamp.toLocaleDateString() : 'N/A';
        const isGPXTrack = sessionId === 'GPX Track';

        let additionalInfo = '';
        if (isGPXTrack && point.lap !== undefined && point.activityType) {
            additionalInfo = `
                <div style="border-bottom: 1px solid #ddd; margin-bottom: 6px; padding-bottom: 4px;">
                    <strong>Activity:</strong> ${point.activityType}<br>
                    <strong>Lap:</strong> ${point.lap}
                </div>
            `;
        }

        const weatherData = extractWeatherData(point);
        const barometerData = extractBarometerData(point);

        let weatherSection = '';

        if (weatherData.hasData || barometerData.hasData) {
            const weatherDescription = getWeatherDescription(weatherData.weatherCode || 0);
            const weatherEmoji = getWeatherEmoji(weatherData.weatherCode || 0);
            const windDirectionText = getWindDirectionText(weatherData.windDirection || 0);
            const weatherTimeFormatted = formatWeatherTime(weatherData.weatherTime);

            weatherSection = `
                <div class="weather-section" style="background-color: #f8f9fa; border-radius: 4px; padding: 8px; margin: 8px 0; border-left: 3px solid #FF5722;">
                    <div class="weather-header" style="font-weight: bold; color: #FF5722; margin-bottom: 6px; display: flex; align-items: center; gap: 4px; font-size: 11px;">
                        ${weatherEmoji} Weather & Atmospheric Conditions
                    </div>
                    <div class="weather-grid" style="display: grid; grid-template-columns: 1fr 1fr; gap: 6px; font-size: 10px;">
                        <div class="weather-item" style="text-align: center;">
                            <div class="weather-label" style="font-weight: bold; margin-bottom: 2px; color: #FF5722;">Temperature</div>
                            <div class="weather-value temperature" style="color: #FF5722; font-weight: bold; font-size: 11px;">
                                ${weatherData.temperature ? weatherData.temperature.toFixed(1) + '°C' : 'N/A'}
                            </div>
                        </div>
                        <div class="weather-item" style="text-align: center;">
                            <div class="weather-label" style="font-weight: bold; margin-bottom: 2px; color: #2196F3;">Humidity</div>
                            <div class="weather-value humidity" style="color: #2196F3; font-weight: bold; font-size: 11px;">
                                ${weatherData.humidity ? weatherData.humidity + '%' : 'N/A'}
                            </div>
                        </div>
                        <div class="weather-item" style="text-align: center;">
                            <div class="weather-label" style="font-weight: bold; margin-bottom: 2px; color: #795548;">Wind Speed</div>
                            <div class="weather-value wind" style="color: #795548; font-weight: bold; font-size: 11px;">
                                ${weatherData.windSpeed ? weatherData.windSpeed.toFixed(1) + ' km/h' : 'N/A'}
                            </div>
                        </div>
                        <div class="weather-item" style="text-align: center;">
                            <div class="weather-label" style="font-weight: bold; margin-bottom: 2px; color: #795548;">Wind Dir</div>
                            <div class="weather-value wind" style="color: #795548; font-weight: bold; font-size: 11px;">
                                ${windDirectionText}
                            </div>
                        </div>`;

            if (barometerData.hasData) {
                weatherSection += `
                        <div class="weather-item" style="text-align: center;">
                            <div class="weather-label" style="font-weight: bold; margin-bottom: 2px; color: #9C27B0;">Pressure</div>
                            <div class="weather-value pressure" style="color: #9C27B0; font-weight: bold; font-size: 11px;">
                                ${barometerData.pressure ? barometerData.pressure.toFixed(1) + ' hPa' : 'N/A'}
                            </div>
                        </div>
                        <div class="weather-item" style="text-align: center;">
                            <div class="weather-label" style="font-weight: bold; margin-bottom: 2px; color: #9C27B0;">Sea Level</div>
                            <div class="weather-value pressure" style="color: #9C27B0; font-weight: bold; font-size: 11px;">
                                ${barometerData.seaLevelPressure ? barometerData.seaLevelPressure.toFixed(1) + ' hPa' : 'N/A'}
                            </div>
                        </div>
                        <div class="weather-item" style="text-align: center;">
                            <div class="weather-label" style="font-weight: bold; margin-bottom: 2px; color: #607D8B;">Baro Alt</div>
                            <div class="weather-value barometer" style="color: #607D8B; font-weight: bold; font-size: 11px;">
                                ${barometerData.altitudeFromPressure ? barometerData.altitudeFromPressure.toFixed(1) + ' m' : 'N/A'}
                            </div>
                        </div>
                        <div class="weather-item" style="text-align: center;">
                            <div class="weather-label" style="font-weight: bold; margin-bottom: 2px; color: #607D8B;">Accuracy</div>
                            <div class="weather-value barometer" style="color: #607D8B; font-weight: bold; font-size: 11px;">
                                ${barometerData.pressureAccuracy ? barometerData.pressureAccuracy + ' Pa' : 'N/A'}
                            </div>
                        </div>`;
            }

            weatherSection += `
                    </div>
                    ${weatherDescription !== 'Code 0' ? `
                        <div class="weather-footer" style="margin-top: 6px; font-size: 9px; color: #666; font-style: italic; text-align: center;">
                            ${weatherDescription} • ${weatherTimeFormatted}
                        </div>
                    ` : ''}
                </div>
            `;
        }

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
                <div>
                    <strong style="color: ${getSlopeColor(point.slope || 0)};">Slope</strong><br>
                    ${(point.slope || 0).toFixed(1)}%
                </div>
            </div>
            ${weatherSection}
            <div style="margin-top: 8px; padding-top: 6px; border-top: 1px solid #ddd; font-size: 10px; color: #666;">
                Coordinates: ${point.lat.toFixed(6)}, ${point.lng.toFixed(6)}
            </div>
            ${isGPXTrack ? '<div style="font-size: 9px; color: #999; margin-top: 4px;">📁 GPX File Data</div>' : ''}
        `;

        infoPopup.innerHTML = content;

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

        console.log('✅ Popup with weather and barometer data displayed at:', finalX, finalY);
        addDebugMessage(`Info popup with weather and barometer data shown at coordinates: ${finalX}, ${finalY}`, 'interaction');

        setTimeout(() => {
            const popupRect = infoPopup.getBoundingClientRect();
            const windowWidth = window.innerWidth;
            const windowHeight = window.innerHeight;

            let newX = finalX;
            let newY = finalY;

            if (popupRect.right > windowWidth) {
                newX = x - popupRect.width - 15;
            }
            if (popupRect.bottom > windowHeight) {
                newY = y - popupRect.height + 10;
            }

            if (newX !== finalX || newY !== finalY) {
                infoPopup.style.left = newX + 'px';
                infoPopup.style.top = newY + 'px';
                console.log('📐 Popup repositioned to:', newX, newY);
            }
        }, 1);

    } catch (error) {
        console.error('❌ Error in showInfoPopup:', error);
        addDebugMessage(`Popup error: ${error.message}`, 'error');
    }
}

function extractWeatherData(point) {
    const hasTemperature = point.temperature !== undefined && point.temperature !== null;
    const hasWindSpeed = point.windSpeed !== undefined && point.windSpeed !== null && point.windSpeed > 0;
    const hasHumidity = point.relativeHumidity !== undefined && point.relativeHumidity !== null && point.relativeHumidity > 0;
    const hasWeatherCode = point.weatherCode !== undefined && point.weatherCode !== null && point.weatherCode > 0;
    const hasWindDirection = point.windDirection !== undefined && point.windDirection !== null;

    return {
        hasData: hasTemperature || hasWindSpeed || hasHumidity || hasWeatherCode || hasWindDirection,
        temperature: hasTemperature ? point.temperature : null,
        windSpeed: hasWindSpeed ? point.windSpeed : null,
        windDirection: hasWindDirection ? point.windDirection : null,
        humidity: hasHumidity ? point.relativeHumidity : null,
        weatherCode: hasWeatherCode ? point.weatherCode : null,
        weatherTime: point.weatherTime || ""
    };
}

function extractBarometerData(point) {
    const hasPressure = point.pressure !== undefined && point.pressure !== null && point.pressure > 0;
    const hasAltitudeFromPressure = point.altitudeFromPressure !== undefined && point.altitudeFromPressure !== null;
    const hasSeaLevelPressure = point.seaLevelPressure !== undefined && point.seaLevelPressure !== null && point.seaLevelPressure > 0;
    const hasPressureAccuracy = point.pressureAccuracy !== undefined && point.pressureAccuracy !== null;

    return {
        hasData: hasPressure || hasAltitudeFromPressure || hasSeaLevelPressure || hasPressureAccuracy,
        pressure: hasPressure ? point.pressure : null,
        altitudeFromPressure: hasAltitudeFromPressure ? point.altitudeFromPressure : null,
        seaLevelPressure: hasSeaLevelPressure ? point.seaLevelPressure : null,
        pressureAccuracy: hasPressureAccuracy ? point.pressureAccuracy : null
    };
}

function testChartSetup() {
    console.log('🧪 Testing chart setup...');

    const tests = {
        altitudeChart: !!altitudeChart,
        speedChart: !!speedChart,
        infoPopup: !!infoPopup,
        altitudeCanvas: !!document.getElementById('altitudeChart'),
        speedCanvas: !!document.getElementById('speedChart'),
        trackPointsCount: Object.keys(trackPoints).length
    };

    console.log('🧪 Chart setup test results:', tests);
    addDebugMessage(`Chart setup test: ${JSON.stringify(tests)}`, 'system');

    if (altitudeChart && altitudeChart.options && altitudeChart.options.onHover) {
        console.log('✅ Altitude chart hover handler is bound');
    } else {
        console.log('❌ Altitude chart hover handler NOT bound');
    }

    if (speedChart && speedChart.options && speedChart.options.onHover) {
        console.log('✅ Speed chart hover handler is bound');
    } else {
        console.log('❌ Speed chart hover handler NOT bound');
    }
}

function toggleStatBoxes() {
    const speedDisplay = document.getElementById('speedDisplay');
    const toggleBtn = document.getElementById('toggleStatsBtn');

    if (statsVisible) {
        speedDisplay.style.display = 'none';
        toggleBtn.title = 'Show Stats';
        statsVisible = false;
    } else {
        speedDisplay.style.display = 'block';
        toggleBtn.title = 'Hide Stats';
        statsVisible = true;
    }

    addDebugMessage(`Stats visibility toggled: ${statsVisible ? 'shown' : 'hidden'}`, 'system');
}

let chartsVisible = true;

function toggleCharts() {
    const chartsContainer = document.querySelector('.charts-container');
    const mapElement = document.getElementById('map');
    const toggleBtn = document.getElementById('toggleChartsBtn');

    if (chartsVisible) {
        chartsContainer.style.display = 'none';
        mapElement.style.height = '100vh';
        toggleBtn.title = 'Show Charts';
        chartsVisible = false;
    } else {
        chartsContainer.style.display = 'flex';
        mapElement.style.height = '67vh';
        toggleBtn.title = 'Hide Charts';
        chartsVisible = true;
    }

    // Trigger map resize so tiles fill the new area
    if (typeof map !== 'undefined' && map) {
        setTimeout(function() { map.invalidateSize(); }, 100);
    }
}

function handleLegendClick(e, legendItem, legend) {
    const index = legendItem.datasetIndex;
    const clickedLabel = legend.chart.data.datasets[index].label;
    const isVisible = legend.chart.isDatasetVisible(index);

    // Extract session ID from the label (handle both single segment and multi-segment labels)
    let sessionId;
    if (clickedLabel.includes(' (') && clickedLabel.includes(')')) {
        // Multi-segment label format: "PersonName_SessionId (1)"
        sessionId = extractSessionIdFromDisplayId(clickedLabel.split(' (')[0]);
    } else {
        // Single segment label format: "PersonName_SessionId"
        sessionId = extractSessionIdFromDisplayId(clickedLabel);
    }

    addDebugMessage(`Toggle visibility from legend click: ${clickedLabel} (Session ID: ${sessionId})`, 'ui');

    // Toggle visibility for ALL datasets belonging to this session in both charts
    [altitudeChart, speedChart].forEach(chart => {
        chart.data.datasets.forEach((dataset, i) => {
            // Check if this dataset belongs to the same session
            let datasetSessionId;
            if (dataset.label.includes(' (') && dataset.label.includes(')')) {
                datasetSessionId = extractSessionIdFromDisplayId(dataset.label.split(' (')[0]);
            } else {
                datasetSessionId = extractSessionIdFromDisplayId(dataset.label);
            }

            if (datasetSessionId === sessionId) {
                chart.setDatasetVisibility(i, !isVisible);
            }
        });
        chart.update();
    });

    // Toggle map elements visibility
    toggleMapElementsVisibility(sessionId, !isVisible);
}

function extractSessionIdFromDisplayId(displayId) {
    if (displayId.includes('_')) {
        return displayId.split('_').slice(1).join('_');
    }
    return displayId;
}

function toggleMapElementsVisibility(sessionId, visible) {
    addDebugMessage(`Toggling map visibility for session ${sessionId} to ${visible ? 'visible' : 'hidden'}`, 'map');

    // Handle polylines (now as map layers)
    const polylineLayerId = `polyline-${sessionId}`;
    if (map.getLayer(polylineLayerId)) {
        map.setLayoutProperty(polylineLayerId, 'visibility', visible ? 'visible' : 'none');
    } else {
        addDebugMessage(`No polyline layer found for session ${sessionId}`, 'warning');
    }

    // Handle markers
    if (startMarkers[sessionId]) {
        if (visible) {
            startMarkers[sessionId].addTo(map);
        } else {
            startMarkers[sessionId].remove();
        }
    }

    if (endMarkers[sessionId]) {
        if (visible) {
            endMarkers[sessionId].addTo(map);
        } else {
            endMarkers[sessionId].remove();
        }
    }

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

        // Request historical data from server
        websocket.send(JSON.stringify({ type: 'request_history' }));

        requestSessionList();
    };

    websocket.onmessage = (event) => {
        const message = JSON.parse(event.data);

        addDebugMessage(JSON.stringify(message, null, 2), message.type);

        if (message.type === 'update' && message.point) {
            logWeatherData(message.point);
        } else if (message.type === 'history_batch' && message.points) {
            if (message.points.length > 0) {
                logWeatherData(message.points[0]);
            }
        }

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
            case 'invalid_coordinates':
                handleInvalidCoordinates(message);
                break;
            case 'session_list':
                handleSessionList(message.sessions);
                break;
        }
    };

    websocket.onclose = () => {
        console.log('WebSocket connection closed');
        addDebugMessage('WebSocket connection closed', 'connection');
        setTimeout(connectToWebSocket, 5000);
    };

    websocket.onerror = (error) => {
        console.error('WebSocket error:', error);
        addDebugMessage('WebSocket error: ' + error, 'error');
    };
}

function handleInvalidCoordinates(message) {
    const sessionId = message.sessionId;
    const reason = message.reason || 'Invalid GPS coordinates';
    const otherData = message.otherData || {};

    addDebugMessage(`Invalid coordinates for session ${sessionId}: ${reason}`, 'warning');

    if (otherData.currentSpeed !== undefined) {
        const speedContainer = document.getElementById(`speed-container-${sessionId}`);
        if (speedContainer) {
            const currentSpeedElement = document.getElementById(`currentSpeed-${sessionId}`);
            if (currentSpeedElement) {
                currentSpeedElement.textContent = parseFloat(otherData.currentSpeed || 0).toFixed(1);
                currentSpeedElement.style.color = getSpeedColor(parseFloat(otherData.currentSpeed || 0));
            }

            if (otherData.heartRate !== undefined) {
                const heartRateElement = document.getElementById(`heartRate-${sessionId}`);
                if (heartRateElement) {
                    heartRateElement.textContent = otherData.heartRate;
                    heartRateElement.style.color = getHeartRateColor(otherData.heartRate);
                }
            }

            if (otherData.slope !== undefined) {
                const slopeElement = document.getElementById(`slope-${sessionId}`);
                if (slopeElement) {
                    slopeElement.textContent = parseFloat(otherData.slope || 0).toFixed(1);
                    slopeElement.style.color = getSlopeColor(parseFloat(otherData.slope || 0));
                }
            }

            if (otherData.averageSlope !== undefined) {
                const avgSlopeElement = document.getElementById(`avgSlope-${sessionId}`);
                if (avgSlopeElement) {
                    avgSlopeElement.textContent = parseFloat(otherData.averageSlope || 0).toFixed(1);
                    avgSlopeElement.style.color = getSlopeColor(parseFloat(otherData.averageSlope || 0));
                }
            }

            if (otherData.maxUphillSlope !== undefined) {
                const maxUphillSlopeElement = document.getElementById(`maxUphillSlope-${sessionId}`);
                if (maxUphillSlopeElement) {
                    maxUphillSlopeElement.textContent = parseFloat(otherData.maxUphillSlope || 0).toFixed(1);
                    maxUphillSlopeElement.style.color = getSlopeColor(parseFloat(otherData.maxUphillSlope || 0));
                }
            }

            if (otherData.maxDownhillSlope !== undefined) {
                const maxDownhillSlopeElement = document.getElementById(`maxDownhillSlope-${sessionId}`);
                if (maxDownhillSlopeElement) {
                    maxDownhillSlopeElement.textContent = parseFloat(otherData.maxDownhillSlope || 0).toFixed(1);
                    maxDownhillSlopeElement.style.color = getSlopeColor(-parseFloat(otherData.maxDownhillSlope || 0));
                }
            }
        }
    }

    showNotification(`GPS signal lost for ${sessionPersonNames[sessionId] || sessionId}: ${reason}`, 'warning');
}

function handleHistoryBatch(points) {
    if (!points || points.length === 0) return;

    console.log('📦 Processing history batch:', points.length, 'points');

    isProcessingBatch = true;

    points.forEach(point => {
        const sessionId = point.sessionId || "default";
        const personName = point.person || "";

        if (personName) {
            sessionPersonNames[sessionId] = personName;
        }

        if (!trackPoints[sessionId]) {
            trackPoints[sessionId] = [];
        }

        const processedPoint = {
            lat: parseFloat(point.latitude),
            lng: parseFloat(point.longitude),
            distance: parseFloat(point.distance) / 1000,
            altitude: parseFloat(point.altitude || 0),
            speed: parseFloat(point.currentSpeed || 0),
            averageSpeed: parseFloat(point.averageSpeed || 0),
            maxSpeed: parseFloat(point.maxSpeed || 0),
            movingAverageSpeed: parseFloat(point.movingAverageSpeed || 0),
            cumulativeElevationGain: parseFloat(point.cumulativeElevationGain || 0),
            heartRate: parseInt(point.heartRate || 0),
            slope: parseFloat(point.slope || 0),
            averageSlope: parseFloat(point.averageSlope || 0),
            maxUphillSlope: parseFloat(point.maxUphillSlope || 0),
            maxDownhillSlope: parseFloat(point.maxDownhillSlope || 0),
            timestamp: new Date(point.timestamp.replace(/(\d{2})-(\d{2})-(\d{4})/, '$3-$2-$1')),
            personName: personName,

            temperature: point.temperature !== undefined && point.temperature !== null ? parseFloat(point.temperature) : null,
            windSpeed: parseFloat(point.windSpeed || 0),
            windDirection: parseFloat(point.windDirection || 0),
            relativeHumidity: parseInt(point.relativeHumidity || point.humidity || 0),
            weatherCode: parseInt(point.weatherCode || 0),
            weatherTime: point.weatherTime || "",

            pressure: point.pressure !== undefined && point.pressure !== null ? parseFloat(point.pressure) : null,
            altitudeFromPressure: parseFloat(point.altitudeFromPressure || 0),
            seaLevelPressure: parseFloat(point.seaLevelPressure || 0),
            pressureAccuracy: parseInt(point.pressureAccuracy || 0)
        };

        console.log('🌤️ Processed point weather & barometer data:', {
            sessionId,
            temperature: processedPoint.temperature,
            windSpeed: processedPoint.windSpeed,
            pressure: processedPoint.pressure,
            altitudeFromPressure: processedPoint.altitudeFromPressure
        });

        validateWeatherData(processedPoint);
        updateWeatherStats(processedPoint);

        // Update per-session temperature and pressure statistics during batch processing
        if (!speedHistory[sessionId]) {
            speedHistory[sessionId] = {
                speeds: [],
                maxSpeed: 0,
                avgSpeed: 0,
                movingAvg: 0,
                currentAltitude: 0,
                elevationGain: 0,
                heartRate: 0,
                slope: 0,
                averageSlope: 0,
                maxUphillSlope: 0,
                maxDownhillSlope: 0,
                totalDistance: 0,
                lastUpdate: new Date(),
                personName: personName,
                // Weather tracking
                temperatures: [],
                minTemperature: null,
                maxTemperature: null,
                avgTemperature: null,
                // Barometer tracking
                pressures: [],
                minPressure: null,
                maxPressure: null,
                avgPressure: null
            };
        }

        const history = speedHistory[sessionId];

        // Update temperature statistics
        // Note: We allow negative temperatures and 0.0°C as valid values
        if (processedPoint.temperature !== undefined && processedPoint.temperature !== null) {
            history.temperatures.push(processedPoint.temperature);
            if (history.minTemperature === null || processedPoint.temperature < history.minTemperature) {
                history.minTemperature = processedPoint.temperature;
            }
            if (history.maxTemperature === null || processedPoint.temperature > history.maxTemperature) {
                history.maxTemperature = processedPoint.temperature;
            }
            history.avgTemperature = history.temperatures.reduce((a, b) => a + b, 0) / history.temperatures.length;
        }

        // Update pressure statistics
        if (processedPoint.pressure !== undefined && processedPoint.pressure !== null) {
            history.pressures.push(processedPoint.pressure);
            if (history.minPressure === null || processedPoint.pressure < history.minPressure) {
                history.minPressure = processedPoint.pressure;
            }
            if (history.maxPressure === null || processedPoint.pressure > history.maxPressure) {
                history.maxPressure = processedPoint.pressure;
            }
            history.avgPressure = history.pressures.reduce((a, b) => a + b, 0) / history.pressures.length;
        }

        trackPoints[sessionId].push(processedPoint);
    });
}

function finalizeBatchProcessing() {
    if (!isProcessingBatch) return;

    Object.keys(trackPoints).forEach(sessionId => {
        trackPoints[sessionId].sort((a, b) => a.timestamp - b.timestamp);
    });

    requestAnimationFrame(() => {
        Object.keys(trackPoints).forEach(sessionId => {
            if (shouldDisplaySession(sessionId)) {
                updateMapTrack(sessionId);
                updateCharts(sessionId);

                const latestPoint = trackPoints[sessionId][trackPoints[sessionId].length - 1];
                if (latestPoint) {
                    updateSpeedDisplay(sessionId, latestPoint.speed, {
                        averageSpeed: latestPoint.averageSpeed,
                        maxSpeed: latestPoint.maxSpeed,
                        movingAverageSpeed: latestPoint.movingAverageSpeed,
                        cumulativeElevationGain: latestPoint.cumulativeElevationGain,
                        heartRate: latestPoint.heartRate,
                        slope: latestPoint.slope,
                        averageSlope: latestPoint.averageSlope,
                        maxUphillSlope: latestPoint.maxUphillSlope,
                        maxDownhillSlope: latestPoint.maxDownhillSlope,
                        distance: latestPoint.distance,
                        personName: latestPoint.personName ||
                            (window.sessionPersonNames && window.sessionPersonNames[sessionId]) || "",
                        weather: {
                            temperature: latestPoint.temperature,
                            windSpeed: latestPoint.windSpeed,
                            windDirection: latestPoint.windDirection,
                            relativeHumidity: latestPoint.relativeHumidity,
                            weatherCode: latestPoint.weatherCode,
                            weatherTime: latestPoint.weatherTime
                        },
                        barometer: {
                            pressure: latestPoint.pressure,
                            altitudeFromPressure: latestPoint.altitudeFromPressure,
                            seaLevelPressure: latestPoint.seaLevelPressure,
                            pressureAccuracy: latestPoint.pressureAccuracy
                        }
                    });
                }
            } else {
                addDebugMessage(`Skipping visualization update for filtered session: ${sessionId}`, 'system');
            }
        });
        isProcessingBatch = false;
    });
}

function handlePoint(data) {
    if (!data || isProcessingBatch) return;

    const sessionId = data.sessionId || "default";
    const personName = data.person || "";

    if (personName) {
        sessionPersonNames[sessionId] = personName;
    }

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
        heartRate: parseInt(data.heartRate || 0),
        slope: parseFloat(data.slope || 0),
        averageSlope: parseFloat(data.averageSlope || 0),
        maxUphillSlope: parseFloat(data.maxUphillSlope || 0),
        maxDownhillSlope: parseFloat(data.maxDownhillSlope || 0),
        timestamp: new Date(data.timestamp.replace(/(\d{2})-(\d{2})-(\d{4})/, '$3-$2-$1')),
        personName: personName,

        temperature: parseFloat(data.temperature || 0),
        windSpeed: parseFloat(data.windSpeed || 0),
        windDirection: parseFloat(data.windDirection || 0),
        relativeHumidity: parseInt(data.relativeHumidity || 0),
        weatherCode: parseInt(data.weatherCode || 0),
        weatherTime: data.weatherTime || "",

        pressure: parseFloat(data.pressure || 0),
        altitudeFromPressure: parseFloat(data.altitudeFromPressure || 0),
        seaLevelPressure: parseFloat(data.seaLevelPressure || 0),
        pressureAccuracy: parseInt(data.pressureAccuracy || 0)
    };

    validateWeatherData(processedPoint);
    updateWeatherStats(processedPoint);

    if (!trackPoints[sessionId]) {
        trackPoints[sessionId] = [];
    }

    trackPoints[sessionId].push(processedPoint);

    clearGPSWarnings(sessionId);

    requestAnimationFrame(() => {
        updateMapTrack(sessionId);
        updateCharts(sessionId);
        updateSpeedDisplay(sessionId, processedPoint.speed, {
            averageSpeed: processedPoint.averageSpeed,
            maxSpeed: processedPoint.maxSpeed,
            movingAverageSpeed: processedPoint.movingAverageSpeed,
            cumulativeElevationGain: processedPoint.cumulativeElevationGain,
            heartRate: processedPoint.heartRate,
            slope: processedPoint.slope,
            averageSlope: processedPoint.averageSlope,
            maxUphillSlope: processedPoint.maxUphillSlope,
            maxDownhillSlope: processedPoint.maxDownhillSlope,
            distance: processedPoint.distance,
            personName: processedPoint.personName,
            weather: {
                temperature: processedPoint.temperature,
                windSpeed: processedPoint.windSpeed,
                windDirection: processedPoint.windDirection,
                relativeHumidity: processedPoint.relativeHumidity,
                weatherCode: processedPoint.weatherCode,
                weatherTime: processedPoint.weatherTime
            },
            barometer: {
                pressure: processedPoint.pressure,
                altitudeFromPressure: processedPoint.altitudeFromPressure,
                seaLevelPressure: processedPoint.seaLevelPressure,
                pressureAccuracy: processedPoint.pressureAccuracy
            }
        });
    });
}

function clearGPSWarnings(sessionId) {
    const speedContainer = document.getElementById(`speed-container-${sessionId}`);
    if (speedContainer) {
        const gpsIndicator = speedContainer.querySelector('.gps-status');
        if (gpsIndicator) {
            gpsIndicator.style.backgroundColor = '#44ff44';
            gpsIndicator.title = 'GPS signal restored';
            setTimeout(() => {
                if (gpsIndicator && gpsIndicator.parentNode) {
                    gpsIndicator.remove();
                }
            }, 2000);
        }
    }
}

function updateMapTrack(sessionId) {
    const points = trackPoints[sessionId];
    if (!points || points.length === 0) return;

    const userColor = getColorForUser(sessionId);
    const sourceId = `track-${sessionId}`;
    const layerId = `polyline-${sessionId}`;

    // Remove existing layers and sources
    if (map.getLayer(layerId)) {
        map.removeLayer(layerId);
    }
    if (map.getSource(sourceId)) {
        map.removeSource(sourceId);
    }

    // Remove existing markers
    if (startMarkers[sessionId]) {
        startMarkers[sessionId].remove();
        delete startMarkers[sessionId];
    }
    if (endMarkers[sessionId]) {
        endMarkers[sessionId].remove();
        delete endMarkers[sessionId];
    }

    // Filter out invalid coordinates and create valid coordinate array
    const validPoints = points.filter(point => isValidCoordinate(point.lat, point.lng));

    if (validPoints.length === 0) {
        addDebugMessage(`No valid coordinates found for session ${sessionId}`, 'warning');
        return;
    }

    // Function to calculate distance between two points in meters
    function calculateDistance(lat1, lng1, lat2, lng2) {
        const R = 6371000; // Earth's radius in meters
        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLng = (lng2 - lng1) * Math.PI / 180;
        const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
                Math.sin(dLng/2) * Math.sin(dLng/2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    // Break track into segments when there are large gaps (> 500 meters between consecutive points)
    const maxGapDistance = 500; // meters
    const trackSegments = [];
    let currentSegment = [];

    for (let i = 0; i < validPoints.length; i++) {
        const currentPoint = validPoints[i];

        if (currentSegment.length === 0) {
            // First point in segment
            currentSegment.push([currentPoint.lng, currentPoint.lat]);
        } else {
            // Check distance from previous point
            const prevPoint = validPoints[i - 1];
            const distance = calculateDistance(
                prevPoint.lat, prevPoint.lng,
                currentPoint.lat, currentPoint.lng
            );

            if (distance > maxGapDistance) {
                // Large gap detected - finish current segment and start new one
                if (currentSegment.length > 1) {
                    trackSegments.push([...currentSegment]);
                }
                currentSegment = [[currentPoint.lng, currentPoint.lat]];
                addDebugMessage(`Track gap detected in session ${sessionId}: ${distance.toFixed(0)}m between points`, 'system');
            } else {
                // Normal point - add to current segment
                currentSegment.push([currentPoint.lng, currentPoint.lat]);
            }
        }
    }

    // Add the final segment
    if (currentSegment.length > 1) {
        trackSegments.push(currentSegment);
    }

    if (trackSegments.length === 0) {
        addDebugMessage(`No valid track segments found for session ${sessionId}`, 'warning');
        return;
    }

    try {
        // Create GeoJSON with MultiLineString to handle multiple segments
        const geojson = {
            type: 'Feature',
            properties: {},
            geometry: {
                type: trackSegments.length === 1 ? 'LineString' : 'MultiLineString',
                coordinates: trackSegments.length === 1 ? trackSegments[0] : trackSegments
            }
        };

        // Add source and layer for the track
        map.addSource(sourceId, {
            type: 'geojson',
            data: geojson
        });

        map.addLayer({
            id: layerId,
            type: 'line',
            source: sourceId,
            layout: {
                'line-join': 'round',
                'line-cap': 'round'
            },
            paint: {
                'line-color': userColor,
                'line-width': 3
            }
        });

        // Store reference for visibility toggling
        polylines[sessionId] = { layerId: layerId, sourceId: sourceId };

        // Add start marker (use first coordinate of first segment)
        const firstSegment = trackSegments[0];
        const startCoord = firstSegment[0];
        if (isValidCoordinate(startCoord[1], startCoord[0])) {
            const startElement = document.createElement('div');
            startElement.style.cssText = `
                width: 12px;
                height: 12px;
                border-radius: 50%;
                background-color: ${userColor};
                border: 2px solid white;
                box-shadow: 0 0 4px rgba(0,0,0,0.3);
            `;

            startMarkers[sessionId] = new maplibregl.Marker({
                element: startElement,
                anchor: 'center'
            })
                .setLngLat(startCoord)
                .setPopup(new maplibregl.Popup().setHTML(`Start - ${sessionId}`))
                .addTo(map);
        }

        // Add end marker (use last coordinate of last segment)
        const lastSegment = trackSegments[trackSegments.length - 1];
        const endCoord = lastSegment[lastSegment.length - 1];
        if (isValidCoordinate(endCoord[1], endCoord[0])) {
            const endElement = document.createElement('div');
            endElement.style.cssText = `
                width: 14px;
                height: 14px;
                border-radius: 50%;
                background-color: ${userColor};
                border: 3px solid white;
                box-shadow: 0 0 4px rgba(0,0,0,0.3);
            `;

            endMarkers[sessionId] = new maplibregl.Marker({
                element: endElement,
                anchor: 'center'
            })
                .setLngLat(endCoord)
                .setPopup(new maplibregl.Popup().setHTML(`Current - ${sessionId}`))
                .addTo(map);
        }

        addDebugMessage(`Map track updated for session ${sessionId}: ${validPoints.length}/${points.length} valid points in ${trackSegments.length} segment(s)`, 'system');

    } catch (error) {
        addDebugMessage(`Error updating map track for session ${sessionId}: ${error.message}`, 'error');
        console.error('UpdateMapTrack error:', error, { sessionId, trackSegments });
    }
}

function updateCharts(sessionId) {
    if (!shouldDisplaySession(sessionId)) {
        addDebugMessage(`Skipping chart update for filtered session: ${sessionId}`, 'system');
        return;
    }

    const points = trackPoints[sessionId];
    if (!points || points.length === 0) return;

    const personName = sessionPersonNames[sessionId] || "";
    const displayId = createDisplayId(sessionId, personName);

    // Function to calculate distance between two points in meters
    function calculateDistance(lat1, lng1, lat2, lng2) {
        const R = 6371000; // Earth's radius in meters
        const dLat = (lat2 - lat1) * Math.PI / 180;
        const dLng = (lng2 - lng1) * Math.PI / 180;
        const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
                Math.sin(dLng/2) * Math.sin(dLng/2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    // Break data into segments when there are large gaps (same logic as map)
    const maxGapDistance = 500; // meters
    const dataSegments = [];
    let currentSegment = [];

    // Filter out invalid coordinates first
    const validPoints = points.filter(point => isValidCoordinate(point.lat, point.lng));

    for (let i = 0; i < validPoints.length; i++) {
        const currentPoint = validPoints[i];

        if (currentSegment.length === 0) {
            // First point in segment
            currentSegment.push(currentPoint);
        } else {
            // Check distance from previous point
            const prevPoint = validPoints[i - 1];
            const distance = calculateDistance(
                prevPoint.lat, prevPoint.lng,
                currentPoint.lat, currentPoint.lng
            );

            if (distance > maxGapDistance) {
                // Large gap detected - finish current segment and start new one
                if (currentSegment.length > 1) {
                    dataSegments.push([...currentSegment]);
                }
                currentSegment = [currentPoint];
                addDebugMessage(`Chart gap detected in session ${sessionId}: ${distance.toFixed(0)}m between points`, 'system');
            } else {
                // Normal point - add to current segment
                currentSegment.push(currentPoint);
            }
        }
    }

    // Add the final segment
    if (currentSegment.length > 1) {
        dataSegments.push(currentSegment);
    }

    if (dataSegments.length === 0) {
        addDebugMessage(`No valid chart data segments found for session ${sessionId}`, 'warning');
        return;
    }

    // Remove existing datasets for this session from both charts
    const sessionColor = getColorForUser(sessionId);

    // Remove from altitude chart
    for (let i = altitudeChart.data.datasets.length - 1; i >= 0; i--) {
        const dataset = altitudeChart.data.datasets[i];
        if (dataset.label === sessionId || dataset.label === displayId ||
            (dataset.label && dataset.label.includes(sessionId))) {
            altitudeChart.data.datasets.splice(i, 1);
        }
    }

    // Remove from speed chart
    for (let i = speedChart.data.datasets.length - 1; i >= 0; i--) {
        const dataset = speedChart.data.datasets[i];
        if (dataset.label === sessionId || dataset.label === displayId ||
            (dataset.label && dataset.label.includes(sessionId))) {
            speedChart.data.datasets.splice(i, 1);
        }
    }

    // Add new datasets for each segment
    dataSegments.forEach((segment, segmentIndex) => {
        const segmentLabel = dataSegments.length === 1 ? displayId : `${displayId} (${segmentIndex + 1})`;

        // Create altitude data for this segment
        const altitudeData = segment.map(point => ({
            x: point.distance,
            y: point.altitude
        }));

        // Create speed data for this segment
        const speedData = segment.map(point => ({
            x: point.distance,
            y: point.speed
        }));

        // Add altitude dataset
        altitudeChart.data.datasets.push({
            label: segmentLabel,
            borderColor: sessionColor,
            backgroundColor: sessionColor + '20', // Add some transparency
            fill: false,
            data: altitudeData,
            pointRadius: 0,
            pointHoverRadius: 6,
            tension: 0
        });

        // Add speed dataset
        speedChart.data.datasets.push({
            label: segmentLabel,
            borderColor: sessionColor,
            backgroundColor: sessionColor + '20', // Add some transparency
            fill: false,
            data: speedData,
            pointRadius: 0,
            pointHoverRadius: 6,
            tension: 0
        });
    });

    // Update both charts
    requestAnimationFrame(() => {
        altitudeChart.update('none');
        speedChart.update('none');
    });

    addDebugMessage(`Charts updated for session ${sessionId}: ${dataSegments.length} segment(s) with ${validPoints.length} total points`, 'system');
}

function getSpeedColor(speed) {
    if (speed < 5) return '#2196F3';
    if (speed < 10) return '#4CAF50';
    return '#F44336';
}

function getElevationColor(elevation) {
    if (elevation < 10) return '#2196F3';
    if (elevation < 50) return '#4CAF50';
    return '#F44336';
}

function getHeartRateColor(heartRate) {
    if (heartRate === 0) return '#999';
    if (heartRate < 60) return '#2196F3';
    if (heartRate < 100) return '#4CAF50';
    if (heartRate < 140) return '#FF9800';
    return '#F44336';
}

function getSlopeColor(slope) {
    if (slope === null || slope === undefined) return '#999';
    if (slope < -10) return '#F44336';  // Steep downhill - red
    if (slope < -5) return '#FF9800';   // Moderate downhill - orange
    if (slope < -2) return '#FFC107';   // Slight downhill - amber
    if (slope < 2) return '#4CAF50';    // Flat - green
    if (slope < 5) return '#2196F3';    // Slight uphill - blue
    if (slope < 10) return '#3F51B5';   // Moderate uphill - indigo
    return '#9C27B0';                   // Steep uphill - purple
}

function updateSpeedDisplay(sessionId, speed, data) {
    // Extract temperature and pressure from nested objects for easier access
    const temperature = data.weather?.temperature ?? data.temperature;
    const pressure = data.barometer?.pressure ?? data.pressure;

    console.log(`[DEBUG] updateSpeedDisplay called for session ${sessionId}`, {
        temperature: temperature,
        pressure: pressure,
        hasTemperature: temperature !== undefined && temperature !== null,
        hasPressure: pressure !== undefined && pressure !== null,
        rawData: data
    });

    if (!shouldDisplaySession(sessionId)) {
        addDebugMessage(`Skipping speed display update for filtered session: ${sessionId}`, 'system');
        return;
    }

    const personName = data.personName || sessionPersonNames[sessionId] || "";
    const displayId = createDisplayId(sessionId, personName);

    if (!speedHistory[sessionId]) {
        speedHistory[sessionId] = {
            speeds: [],
            maxSpeed: 0,
            avgSpeed: 0,
            movingAvg: 0,
            currentAltitude: 0,
            elevationGain: 0,
            heartRate: 0,
            slope: 0,
            averageSlope: 0,
            maxUphillSlope: 0,
            maxDownhillSlope: 0,
            totalDistance: 0,
            lastUpdate: new Date(),
            personName: personName,
            // Weather tracking
            temperatures: [],
            minTemperature: null,
            maxTemperature: null,
            avgTemperature: null,
            // Barometer tracking
            pressures: [],
            minPressure: null,
            maxPressure: null,
            avgPressure: null
        };
    } else if (personName && !speedHistory[sessionId].personName) {
        speedHistory[sessionId].personName = personName;
    }

    const history = speedHistory[sessionId];
    history.speeds.push(speed);
    history.lastUpdate = new Date();

    if (!statsVisible && document.getElementById('speedDisplay').style.display === 'none') {
        if (data.maxSpeed !== undefined) {
            history.maxSpeed = Math.max(history.maxSpeed, data.maxSpeed);
        }

        if (data.cumulativeElevationGain !== undefined) {
            history.elevationGain = data.cumulativeElevationGain;
        }

        if (data.altitude !== undefined) {
            history.currentAltitude = data.altitude;
        }

        if (data.heartRate !== undefined) {
            history.heartRate = data.heartRate;
        }

        if (data.slope !== undefined) {
            history.slope = data.slope;
        }

        if (data.averageSlope !== undefined) {
            history.averageSlope = data.averageSlope;
        }

        if (data.maxUphillSlope !== undefined) {
            history.maxUphillSlope = data.maxUphillSlope;
        }

        if (data.maxDownhillSlope !== undefined) {
            history.maxDownhillSlope = data.maxDownhillSlope;
        }

        if (data.distance !== undefined) {
            history.totalDistance = data.distance;
        }

        // Track weather data even when stats are hidden
        if (temperature !== undefined && temperature !== null) {
            history.temperatures.push(temperature);
            if (history.minTemperature === null || temperature < history.minTemperature) {
                history.minTemperature = temperature;
            }
            if (history.maxTemperature === null || temperature > history.maxTemperature) {
                history.maxTemperature = temperature;
            }
            history.avgTemperature = history.temperatures.reduce((a, b) => a + b, 0) / history.temperatures.length;
        }

        // Track barometer data even when stats are hidden
        if (pressure !== undefined && pressure !== null && pressure !== 0) {
            history.pressures.push(pressure);
            if (history.minPressure === null || pressure < history.minPressure) {
                history.minPressure = pressure;
            }
            if (history.maxPressure === null || pressure > history.maxPressure) {
                history.maxPressure = pressure;
            }
            history.avgPressure = history.pressures.reduce((a, b) => a + b, 0) / history.pressures.length;
        }

        cleanupOldSessions();
        return;
    }

    const speedDisplay = document.getElementById('speedDisplay');

    const defaultDisplay = speedDisplay.querySelector('.stats-grid:not([id^="speed-grid-"])');
    if (defaultDisplay) {
        defaultDisplay.remove();
        console.log("Removed default display when handling session: " + sessionId);
    }

    let speedContainer = document.getElementById(`speed-container-${sessionId}`);

    if (!speedContainer) {
        speedContainer = document.createElement('div');
        speedContainer.id = `speed-container-${sessionId}`;
        speedContainer.className = 'session-container';

        const statsGrid = document.createElement('div');
        statsGrid.id = `speed-grid-${sessionId}`;
        statsGrid.className = 'stats-grid';

        const sessionLabel = document.createElement('div');
        sessionLabel.className = 'session-label';
        sessionLabel.textContent = displayId;

        speedContainer.appendChild(sessionLabel);
        speedContainer.appendChild(statsGrid);

        speedDisplay.appendChild(speedContainer);

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
                <div class="elevation-label">Current Altitude</div>
                <div class="elevation-value" id="currentAltitude-${sessionId}">0.0</div>
                <div class="elevation-unit">m</div>
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
            <div class="stat-box">
                <div class="speed-label">Current Slope</div>
                <div class="elevation-value" id="slope-${sessionId}">0.0</div>
                <div class="elevation-unit">%</div>
            </div>
            <div class="stat-box">
                <div class="speed-label">Average Slope</div>
                <div class="elevation-value" id="avgSlope-${sessionId}">0.0</div>
                <div class="elevation-unit">%</div>
            </div>
            <div class="stat-box">
                <div class="speed-label">Max Uphill</div>
                <div class="elevation-value" id="maxUphillSlope-${sessionId}">0.0</div>
                <div class="elevation-unit">%</div>
            </div>
            <div class="stat-box">
                <div class="speed-label">Max Downhill</div>
                <div class="elevation-value" id="maxDownhillSlope-${sessionId}">0.0</div>
                <div class="elevation-unit">%</div>
            </div>
            <div class="stat-box">
                <div class="speed-label">Temp Avg</div>
                <div class="speed-value" id="avgTemperature-${sessionId}">--</div>
                <div class="speed-unit">°C</div>
            </div>
            <div class="stat-box">
                <div class="speed-label">Temp Max</div>
                <div class="speed-value" id="maxTemperature-${sessionId}">--</div>
                <div class="speed-unit">°C</div>
            </div>
            <div class="stat-box">
                <div class="speed-label">Temp Min</div>
                <div class="speed-value" id="minTemperature-${sessionId}">--</div>
                <div class="speed-unit">°C</div>
            </div>
            <div class="stat-box">
                <div class="elevation-label">Pressure Avg</div>
                <div class="elevation-value" id="avgPressure-${sessionId}">--</div>
                <div class="elevation-unit">hPa</div>
            </div>
            <div class="stat-box">
                <div class="elevation-label">Pressure Max</div>
                <div class="elevation-value" id="maxPressure-${sessionId}">--</div>
                <div class="elevation-unit">hPa</div>
            </div>
            <div class="stat-box">
                <div class="elevation-label">Pressure Min</div>
                <div class="elevation-value" id="minPressure-${sessionId}">--</div>
                <div class="elevation-unit">hPa</div>
            </div>
        `;

        speedDisplay.scrollTop = speedDisplay.scrollHeight;
    } else {
        const sessionLabel = speedContainer.querySelector('.session-label');
        if (sessionLabel && personName) {
            sessionLabel.textContent = displayId;
        }
    }

    const currentSpeedElement = document.getElementById(`currentSpeed-${sessionId}`);
    if (currentSpeedElement) {
        currentSpeedElement.textContent = speed.toFixed(1);
        currentSpeedElement.style.color = getSpeedColor(speed);
    }

    const maxSpeedElement = document.getElementById(`maxSpeed-${sessionId}`);
    if (maxSpeedElement && data.maxSpeed !== undefined) {
        history.maxSpeed = Math.max(history.maxSpeed, data.maxSpeed);
        maxSpeedElement.textContent = history.maxSpeed.toFixed(1);
        maxSpeedElement.style.color = getSpeedColor(history.maxSpeed);
    }

    const avgSpeedElement = document.getElementById(`avgSpeed-${sessionId}`);
    if (avgSpeedElement && data.averageSpeed !== undefined) {
        avgSpeedElement.textContent = data.averageSpeed.toFixed(1);
        avgSpeedElement.style.color = getSpeedColor(data.averageSpeed);
    }

    const movingAvgElement = document.getElementById(`movingAvg-${sessionId}`);
    if (movingAvgElement && data.movingAverageSpeed !== undefined) {
        movingAvgElement.textContent = data.movingAverageSpeed.toFixed(1);
        movingAvgElement.style.color = getSpeedColor(data.movingAverageSpeed);
    }

    const currentAltitudeElement = document.getElementById(`currentAltitude-${sessionId}`);
    if (currentAltitudeElement && data.altitude !== undefined) {
        history.currentAltitude = data.altitude;
        currentAltitudeElement.textContent = history.currentAltitude.toFixed(1);
        currentAltitudeElement.style.color = getElevationColor(history.currentAltitude);
    }

    const elevationGainElement = document.getElementById(`elevationGain-${sessionId}`);
    if (elevationGainElement && data.cumulativeElevationGain !== undefined) {
        history.elevationGain = data.cumulativeElevationGain;
        elevationGainElement.textContent = history.elevationGain.toFixed(1);
        elevationGainElement.style.color = getElevationColor(history.elevationGain);
    }

    const totalDistanceElement = document.getElementById(`totalDistance-${sessionId}`);
    if (totalDistanceElement && data.distance !== undefined) {
        history.totalDistance = data.distance;
        totalDistanceElement.textContent = history.totalDistance.toFixed(2);
        totalDistanceElement.style.color = getDistanceColor(history.totalDistance);
    }

    const heartRateElement = document.getElementById(`heartRate-${sessionId}`);
    if (heartRateElement && data.heartRate !== undefined) {
        history.heartRate = data.heartRate;
        heartRateElement.textContent = history.heartRate;
        heartRateElement.style.color = getHeartRateColor(history.heartRate);
    }

    const slopeElement = document.getElementById(`slope-${sessionId}`);
    if (slopeElement && data.slope !== undefined) {
        history.slope = data.slope;
        slopeElement.textContent = history.slope.toFixed(1);
        slopeElement.style.color = getSlopeColor(history.slope);
    }

    const avgSlopeElement = document.getElementById(`avgSlope-${sessionId}`);
    if (avgSlopeElement && data.averageSlope !== undefined) {
        history.averageSlope = data.averageSlope;
        avgSlopeElement.textContent = history.averageSlope.toFixed(1);
        avgSlopeElement.style.color = getSlopeColor(history.averageSlope);
    }

    const maxUphillSlopeElement = document.getElementById(`maxUphillSlope-${sessionId}`);
    if (maxUphillSlopeElement && data.maxUphillSlope !== undefined) {
        history.maxUphillSlope = data.maxUphillSlope;
        maxUphillSlopeElement.textContent = history.maxUphillSlope.toFixed(1);
        maxUphillSlopeElement.style.color = getSlopeColor(history.maxUphillSlope);
    }

    const maxDownhillSlopeElement = document.getElementById(`maxDownhillSlope-${sessionId}`);
    if (maxDownhillSlopeElement && data.maxDownhillSlope !== undefined) {
        history.maxDownhillSlope = data.maxDownhillSlope;
        maxDownhillSlopeElement.textContent = history.maxDownhillSlope.toFixed(1);
        // Show downhill as negative for color coding
        maxDownhillSlopeElement.style.color = getSlopeColor(-history.maxDownhillSlope);
    }

    // Update weather statistics (temperature)
    // Note: We allow negative temperatures and 0.0°C as valid values
    if (temperature !== undefined && temperature !== null) {
        console.log(`[DEBUG] Temperature data received: ${temperature}°C for session ${sessionId}`);
        history.temperatures.push(temperature);

        // Update min temperature
        if (history.minTemperature === null || temperature < history.minTemperature) {
            history.minTemperature = temperature;
        }

        // Update max temperature
        if (history.maxTemperature === null || temperature > history.maxTemperature) {
            history.maxTemperature = temperature;
        }

        // Calculate average temperature
        history.avgTemperature = history.temperatures.reduce((a, b) => a + b, 0) / history.temperatures.length;
        console.log(`[DEBUG] Temperature stats - Min: ${history.minTemperature}, Max: ${history.maxTemperature}, Avg: ${history.avgTemperature}`);

        // Update DOM elements
        const avgTempElement = document.getElementById(`avgTemperature-${sessionId}`);
        if (avgTempElement) {
            avgTempElement.textContent = history.avgTemperature.toFixed(1);
            avgTempElement.style.color = getTemperatureColor(history.avgTemperature);
            console.log(`[DEBUG] Updated avgTemperature DOM element to ${history.avgTemperature.toFixed(1)}`);
        } else {
            console.log(`[DEBUG] avgTemperature DOM element not found for session ${sessionId}`);
        }

        const maxTempElement = document.getElementById(`maxTemperature-${sessionId}`);
        if (maxTempElement) {
            maxTempElement.textContent = history.maxTemperature.toFixed(1);
            maxTempElement.style.color = getTemperatureColor(history.maxTemperature);
        }

        const minTempElement = document.getElementById(`minTemperature-${sessionId}`);
        if (minTempElement) {
            minTempElement.textContent = history.minTemperature.toFixed(1);
            minTempElement.style.color = getTemperatureColor(history.minTemperature);
        }
    } else {
        console.log(`[DEBUG] No temperature data in message for session ${sessionId}. temperature: ${temperature}`);
    }

    // Update barometer statistics (pressure)
    if (pressure !== undefined && pressure !== null && pressure !== 0) {
        console.log(`[DEBUG] Pressure data received: ${pressure} hPa for session ${sessionId}`);
        history.pressures.push(pressure);

        // Update min pressure
        if (history.minPressure === null || pressure < history.minPressure) {
            history.minPressure = pressure;
        }

        // Update max pressure
        if (history.maxPressure === null || pressure > history.maxPressure) {
            history.maxPressure = pressure;
        }

        // Calculate average pressure
        history.avgPressure = history.pressures.reduce((a, b) => a + b, 0) / history.pressures.length;
        console.log(`[DEBUG] Pressure stats - Min: ${history.minPressure}, Max: ${history.maxPressure}, Avg: ${history.avgPressure}`);

        // Update DOM elements
        const avgPressureElement = document.getElementById(`avgPressure-${sessionId}`);
        if (avgPressureElement) {
            avgPressureElement.textContent = history.avgPressure.toFixed(1);
            avgPressureElement.style.color = getPressureColor(history.avgPressure);
            console.log(`[DEBUG] Updated avgPressure DOM element to ${history.avgPressure.toFixed(1)}`);
        } else {
            console.log(`[DEBUG] avgPressure DOM element not found for session ${sessionId}`);
        }

        const maxPressureElement = document.getElementById(`maxPressure-${sessionId}`);
        if (maxPressureElement) {
            maxPressureElement.textContent = history.maxPressure.toFixed(1);
            maxPressureElement.style.color = getPressureColor(history.maxPressure);
        }

        const minPressureElement = document.getElementById(`minPressure-${sessionId}`);
        if (minPressureElement) {
            minPressureElement.textContent = history.minPressure.toFixed(1);
            minPressureElement.style.color = getPressureColor(history.minPressure);
        }
    } else {
        console.log(`[DEBUG] No pressure data in message for session ${sessionId}. pressure: ${pressure}`);
    }

    cleanupOldSessions();
}

function getDistanceColor(distance) {
    if (distance < 1) return '#2196F3';
    if (distance < 5) return '#4CAF50';
    return '#FF9800';
}

function getTemperatureColor(temperature) {
    if (temperature < 0) return '#2196F3';    // Freezing - blue
    if (temperature < 10) return '#00BCD4';   // Cold - cyan
    if (temperature < 20) return '#4CAF50';   // Cool - green
    if (temperature < 25) return '#FF9800';   // Warm - orange
    return '#FF5722';                         // Hot - deep orange
}

function getPressureColor(pressure) {
    if (pressure < 980) return '#F44336';     // Very low pressure - red
    if (pressure < 1000) return '#FF9800';    // Low pressure - orange
    if (pressure < 1020) return '#4CAF50';    // Normal pressure - green
    if (pressure < 1030) return '#2196F3';    // High pressure - blue
    return '#9C27B0';                         // Very high pressure - purple
}

function cleanupOldSessions() {
    const now = new Date();
    for (const sessionId in speedHistory) {
        const timeDiff = now - speedHistory[sessionId].lastUpdate;
        const shouldCleanup = timeDiff > 300000 || !shouldDisplaySession(sessionId);

        if (shouldCleanup) {
            const container = document.getElementById(`speed-container-${sessionId}`);
            if (container) {
                container.remove();
            }
            delete speedHistory[sessionId];

            if (!shouldDisplaySession(sessionId)) {
                addDebugMessage(`Cleaned up filtered session from speed display: ${sessionId}`, 'system');
            }
        }
    }
}

function getColorForUser(sessionId) {
    if (!userColors[sessionId]) {
        const ranges = [
            [150, 255, 0, 100, 0, 100],
            [0, 100, 150, 255, 0, 100],
            [0, 100, 0, 100, 150, 255],
            [150, 255, 150, 255, 0, 100],
            [150, 255, 0, 100, 150, 255],
            [0, 100, 150, 255, 150, 255]
        ];

        const hash = sessionId.split('').reduce((acc, char) => {
            return char.charCodeAt(0) + ((acc << 5) - acc);
        }, 0);

        const selectedRange = ranges[Math.abs(hash) % ranges.length];

        const r = getRandomInRange(selectedRange[0], selectedRange[1]);
        const g = getRandomInRange(selectedRange[2], selectedRange[3]);
        const b = getRandomInRange(selectedRange[4], selectedRange[5]);

        userColors[sessionId] = `rgb(${r}, ${g}, ${b})`;
    }
    return userColors[sessionId];
}

function createDisplayId(sessionId, personName) {
    if (!personName) return sessionId;

    // Only show the first name (part before the underscore)
    return personName.split('_')[0] || personName;
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

function toggleSessionVisibility(sessionId) {
    addDebugMessage(`Toggle visibility requested for session ${sessionId}`, 'system');

    const personName = sessionPersonNames[sessionId] || "";
    const displayId = createDisplayId(sessionId, personName);

    let currentVisibility = null;

    // Check visibility of datasets belonging to this session
    const altDatasets = altitudeChart.data.datasets;
    for (let i = 0; i < altDatasets.length; i++) {
        const dataset = altDatasets[i];

        // Check if this dataset belongs to the session (handle both single and multi-segment)
        let datasetSessionId;
        if (dataset.label.includes(' (') && dataset.label.includes(')')) {
            datasetSessionId = extractSessionIdFromDisplayId(dataset.label.split(' (')[0]);
        } else {
            datasetSessionId = extractSessionIdFromDisplayId(dataset.label);
        }

        if (datasetSessionId === sessionId || dataset.label === displayId || dataset.label.includes(sessionId)) {
            if (currentVisibility === null) {
                currentVisibility = altitudeChart.isDatasetVisible(i);
            }
            altitudeChart.setDatasetVisibility(i, !currentVisibility);
        }
    }

    // Apply same logic to speed chart
    const speedDatasets = speedChart.data.datasets;
    for (let i = 0; i < speedDatasets.length; i++) {
        const dataset = speedDatasets[i];

        let datasetSessionId;
        if (dataset.label.includes(' (') && dataset.label.includes(')')) {
            datasetSessionId = extractSessionIdFromDisplayId(dataset.label.split(' (')[0]);
        } else {
            datasetSessionId = extractSessionIdFromDisplayId(dataset.label);
        }

        if (datasetSessionId === sessionId || dataset.label === displayId || dataset.label.includes(sessionId)) {
            speedChart.setDatasetVisibility(i, !currentVisibility);
        }
    }

    altitudeChart.update();
    speedChart.update();

    // Toggle map elements visibility
    toggleMapElementsVisibility(sessionId, !currentVisibility);

    if (currentVisibility === null) {
        toggleMapElementsVisibility(sessionId, true);
        addDebugMessage(`No datasets found for session ${sessionId}, defaulting to visible`, 'warning');
    } else {
        addDebugMessage(`Toggled visibility of session ${sessionId} to ${!currentVisibility ? 'visible' : 'hidden'}`, 'system');
    }

    updateVisibilityButtonAppearance(sessionId, !currentVisibility);
}

function updateVisibilityButtonAppearance(sessionId, isVisible) {
    const sessionItem = document.querySelector(`[data-session-id="${sessionId}"]`);
    if (sessionItem) {
        const visibilityBtn = sessionItem.querySelector('.toggle-visibility');
        if (visibilityBtn) {
            visibilityBtn.innerHTML = isVisible ? '👁' : '👁‍🗨';
            visibilityBtn.style.opacity = isVisible ? '1' : '0.5';
        }
    }
}

function zoomToSession(sessionId) {
    addDebugMessage(`Zoom to session requested: ${sessionId}`, 'system');

    const sessionTrackPoints = trackPoints[sessionId];
    if (!sessionTrackPoints || sessionTrackPoints.length === 0) {
        addDebugMessage(`No track points found for session ${sessionId}`, 'warning');
        showNotification(`No track data available for session ${sessionId}`, 'warning');
        return;
    }

    // Filter out invalid coordinates and convert to MapLibre format
    const validCoordinates = sessionTrackPoints
        .filter(point => isValidCoordinate(point.lat, point.lng))
        .map(point => [point.lng, point.lat]); // MapLibre uses [lng, lat]

    if (validCoordinates.length === 0) {
        addDebugMessage(`No valid coordinates found for session ${sessionId}`, 'warning');
        showNotification(`No valid coordinates for session ${sessionId}`, 'warning');
        return;
    }

    try {
        if (validCoordinates.length === 1) {
            // Single point - just center on it
            map.jumpTo({
                center: validCoordinates[0],
                zoom: 15
            });
            addDebugMessage(`Centered on single point for session ${sessionId}`, 'system');
        } else {
            // Multiple points - calculate bounds manually for MapLibre GL JS
            let minLng = validCoordinates[0][0], maxLng = validCoordinates[0][0];
            let minLat = validCoordinates[0][1], maxLat = validCoordinates[0][1];

            validCoordinates.forEach(([lng, lat]) => {
                minLng = Math.min(minLng, lng);
                maxLng = Math.max(maxLng, lng);
                minLat = Math.min(minLat, lat);
                maxLat = Math.max(maxLat, lat);
            });

            // Add some padding to the bounds
            const latPadding = (maxLat - minLat) * 0.1 || 0.001;
            const lngPadding = (maxLng - minLng) * 0.1 || 0.001;

            const bounds = [
                [minLng - lngPadding, minLat - latPadding], // southwest
                [maxLng + lngPadding, maxLat + latPadding]  // northeast
            ];

            // Validate bounds before applying
            if (isValidCoordinate(bounds[0][1], bounds[0][0]) && isValidCoordinate(bounds[1][1], bounds[1][0])) {
                map.fitBounds(bounds, {
                    padding: { top: 20, bottom: 20, left: 20, right: 20 },
                    maxZoom: 16
                });

                addDebugMessage(`Zoomed to session ${sessionId} - ${validCoordinates.length} valid points`, 'system');
            } else {
                throw new Error('Calculated bounds are invalid');
            }
        }

        // Optional: Flash the track briefly to show which one we zoomed to
        const polylineRef = polylines[sessionId];
        if (polylineRef && map.getLayer(polylineRef.layerId)) {
            const originalColor = map.getPaintProperty(polylineRef.layerId, 'line-color');
            const originalWidth = map.getPaintProperty(polylineRef.layerId, 'line-width');

            // Flash the track
            map.setPaintProperty(polylineRef.layerId, 'line-color', '#FF0000');
            map.setPaintProperty(polylineRef.layerId, 'line-width', 6);

            setTimeout(() => {
                if (map.getLayer(polylineRef.layerId)) {
                    map.setPaintProperty(polylineRef.layerId, 'line-color', originalColor);
                    map.setPaintProperty(polylineRef.layerId, 'line-width', originalWidth);
                }
            }, 1000);
        }

    } catch (error) {
        addDebugMessage(`Error zooming to session ${sessionId}: ${error.message}`, 'error');
        showNotification(`Error zooming to session: ${error.message}`, 'warning');
        console.error('Zoom error details:', error, { sessionId, validCoordinates });
    }
}

function updateSessionList() {
    const sessionsList = document.getElementById('sessionsList');

    if (!availableSessions || availableSessions.length === 0) {
        sessionsList.innerHTML = '<p class="no-sessions">No sessions found.</p>';
        return;
    }

    const filteredSessions = availableSessions.filter(session => {
        const sessionId = session.sessionId;
        return !sessionId.includes('_reset_') && !sessionId.includes('_archived_');
    });

    if (filteredSessions.length === 0) {
        sessionsList.innerHTML = '<p class="no-sessions">No primary sessions found.</p>';
        return;
    }

    let html = '';

    filteredSessions.forEach(session => {
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
                    <button class="session-action-btn zoom-to-session"
                            onclick="zoomToSession('${sessionId}')"
                            title="Zoom to Track">
                        🎯
                    </button>
                </div>
            </div>
        `;
    });

    sessionsList.innerHTML = html;

    addDebugMessage(`Session list updated: ${filteredSessions.length} primary sessions displayed (${availableSessions.length - filteredSessions.length} reset/archived sessions filtered out)`, 'system');
}

function confirmDeleteSession(sessionId) {
    const session = availableSessions.find(s => s.sessionId === sessionId);
    if (session && session.isActive) {
        showNotification('Cannot delete an active session. Wait for the session to complete first.', 'warning');
        addDebugMessage(`Attempted to delete active session: ${sessionId}`, 'warning');
        return;
    }

    const overlay = document.createElement('div');
    overlay.id = 'confirmOverlay';
    overlay.style.position = 'fixed';
    overlay.style.top = '0';
    overlay.style.left = '0';
    overlay.style.width = '100%';
    overlay.style.height = '100%';
    overlay.style.backgroundColor = 'rgba(0, 0, 0, 0.5)';
    overlay.style.zIndex = '2000';

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

    document.body.appendChild(overlay);
    document.body.appendChild(dialog);

    addDebugMessage(`Showing delete confirmation for session ${sessionId}`, 'system');
}

function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `notification ${type}`;
    notification.innerHTML = message;

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

function handleSessionDeleted(sessionId) {
    addDebugMessage(`Handling deletion of session ${sessionId}`, 'system');

    availableSessions = availableSessions.filter(session => session.sessionId !== sessionId);

    if (trackPoints[sessionId]) {
        delete trackPoints[sessionId];
    }

    if (speedHistory[sessionId]) {
        delete speedHistory[sessionId];
    }

    // Remove all datasets for this session from altitude chart (including segments)
    const altDatasets = altitudeChart.data.datasets;
    for (let i = altDatasets.length - 1; i >= 0; i--) {
        const dataset = altDatasets[i];

        // Check if this dataset belongs to the deleted session
        let datasetSessionId;
        if (dataset.label.includes(' (') && dataset.label.includes(')')) {
            datasetSessionId = extractSessionIdFromDisplayId(dataset.label.split(' (')[0]);
        } else {
            datasetSessionId = extractSessionIdFromDisplayId(dataset.label);
        }

        if (datasetSessionId === sessionId || dataset.label.includes(sessionId)) {
            addDebugMessage(`Removing altitude dataset: ${dataset.label}`, 'system');
            altDatasets.splice(i, 1);
        }
    }
    altitudeChart.update();

    // Remove all datasets for this session from speed chart (including segments)
    const speedDatasets = speedChart.data.datasets;
    for (let i = speedDatasets.length - 1; i >= 0; i--) {
        const dataset = speedDatasets[i];

        let datasetSessionId;
        if (dataset.label.includes(' (') && dataset.label.includes(')')) {
            datasetSessionId = extractSessionIdFromDisplayId(dataset.label.split(' (')[0]);
        } else {
            datasetSessionId = extractSessionIdFromDisplayId(dataset.label);
        }

        if (datasetSessionId === sessionId || dataset.label.includes(sessionId)) {
            addDebugMessage(`Removing speed dataset: ${dataset.label}`, 'system');
            speedDatasets.splice(i, 1);
        }
    }
    speedChart.update();

    // Remove from map - MapLibre GL JS version
    const polylineRef = polylines[sessionId];
    if (polylineRef) {
        if (map.getLayer(polylineRef.layerId)) {
            map.removeLayer(polylineRef.layerId);
        }
        if (map.getSource(polylineRef.sourceId)) {
            map.removeSource(polylineRef.sourceId);
        }
        delete polylines[sessionId];
    }

    if (startMarkers[sessionId]) {
        startMarkers[sessionId].remove();
        delete startMarkers[sessionId];
    }

    if (endMarkers[sessionId]) {
        endMarkers[sessionId].remove();
        delete endMarkers[sessionId];
    }

    const speedContainer = document.getElementById(`speed-container-${sessionId}`);
    if (speedContainer) {
        speedContainer.remove();
    }

    if (sessionPersonNames[sessionId]) {
        delete sessionPersonNames[sessionId];
    }

    updateSessionList();

    addDebugMessage(`Session ${sessionId} deleted and removed from all displays`, 'system');
    showNotification(`Session ${sessionId} deleted successfully`, 'info');
}

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

function closeConfirmDialog() {
    const overlay = document.getElementById('confirmOverlay');
    const dialog = document.querySelector('.confirm-delete');

    if (overlay) overlay.remove();
    if (dialog) dialog.remove();
}

function setupChartEventListeners() {
    const altitudeContainer = document.getElementById('altitudeChartContainer');
    const speedContainer = document.getElementById('speedChartContainer');

    [altitudeContainer, speedContainer].forEach(container => {
        if (container) {
            container.addEventListener('mouseleave', () => {
                console.log('🖱️ Mouse left chart container');
                hideHoverMarker();
                hideInfoPopup();
            });

            container.addEventListener('mouseenter', () => {
                console.log('🖱️ Mouse entered chart container');
            });
        }
    });

    const chartsContainer = document.querySelector('.charts-container');
    if (chartsContainer) {
        chartsContainer.addEventListener('mouseleave', () => {
            console.log('🖱️ Mouse left charts area');
            hideHoverMarker();
            hideInfoPopup();
        });
    }

    addDebugMessage('Chart event listeners configured', 'system');
}

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

function logWeatherData(data) {
    if (data.temperature !== undefined && data.temperature !== null) {
        const weatherDesc = getWeatherDescription(data.weatherCode || 0);
        const windDir = getWindDirectionText(data.windDirection || 0);

        addDebugMessage(
            `Weather Update: ${data.temperature}°C, ${data.relativeHumidity}% humidity, ` +
            `${data.windSpeed}km/h ${windDir} wind, ${weatherDesc}`,
            'weather'
        );
    }
}

function validateWeatherData(point) {
    const weatherFields = ['temperature', 'windSpeed', 'windDirection', 'relativeHumidity', 'weatherCode'];
    const hasWeatherData = weatherFields.some(field =>
        point[field] !== undefined && point[field] !== null && point[field] !== 0
    );

    if (hasWeatherData) {
        addDebugMessage(`Valid weather data found in point: ${JSON.stringify({
            temp: point.temperature,
            wind: point.windSpeed,
            humidity: point.relativeHumidity,
            code: point.weatherCode
        })}`, 'weather');
    } else {
        addDebugMessage('No weather data found in this point', 'weather');
    }

    return hasWeatherData;
}

function updateWeatherStats(point) {
    if (point.temperature !== undefined && point.temperature !== null) {
        weatherStats.totalUpdates++;
        weatherStats.lastTemperature = point.temperature;
        weatherStats.lastUpdate = new Date();

        if (weatherStats.temperatureRange.min === null || point.temperature < weatherStats.temperatureRange.min) {
            weatherStats.temperatureRange.min = point.temperature;
        }
        if (weatherStats.temperatureRange.max === null || point.temperature > weatherStats.temperatureRange.max) {
            weatherStats.temperatureRange.max = point.temperature;
        }

        if (weatherStats.totalUpdates % 10 === 0) {
            addDebugMessage(
                `Weather Stats: ${weatherStats.totalUpdates} updates, ` +
                `Temp range: ${weatherStats.temperatureRange.min}°C - ${weatherStats.temperatureRange.max}°C, ` +
                `Last: ${weatherStats.lastTemperature}°C`,
                'weather-stats'
            );
        }
    }
}

function shouldDisplaySession(sessionId) {
    return !sessionId.includes('_reset_') && !sessionId.includes('_archived_');
}

document.addEventListener('DOMContentLoaded', () => {
    try {
        console.log("DOM content loaded, initializing application...");

        initMap();

        addDebugMessage('Running Tracker application initialized with MapLibre GL JS, session management and weather data support', 'system');

        setupChartEventListeners();

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