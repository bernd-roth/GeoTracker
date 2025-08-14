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

// Fixed initCharts function with better hover detection
// TARGETED FIX: Replace your initCharts function with this version
// This preserves your original working settings while fixing the hover issues
function initCharts() {
    // Keep your original working chart options - these were fine!
    const baseChartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        // KEEP original interaction settings that were working
        interaction: {
            intersect: false,
            mode: 'index'  // Keep original working mode
        },
        elements: {
            line: {
                tension: 0
            },
            point: {
                radius: 0,        // Keep original - this was working
                hoverRadius: 6    // Keep original - this was working
            }
        },
        plugins: {
            legend: {
                onClick: handleLegendClick
            },
            tooltip: {
                enabled: false  // Keep Chart.js tooltips disabled
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
        // ENHANCED: Better hover event handling with more debugging
        onHover: (event, activeElements) => {
            // Add immediate console logging to verify events are firing
            console.log('🎯 CHART HOVER EVENT FIRED:', {
                elementCount: activeElements.length,
                hasEvent: !!event,
                eventType: event?.type,
                chartCanvas: event?.native?.target?.id || 'unknown'
            });

            addDebugMessage(`Chart hover event triggered with ${activeElements.length} active elements`, 'interaction');

            // Change cursor to indicate interactivity
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

    // Create altitude chart
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

    // Create speed chart with same settings
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
        // Speed chart specific hover handlers
        onHover: (event, activeElements) => {
            console.log('🎯 SPEED CHART HOVER EVENT FIRED:', {
                elementCount: activeElements.length,
                hasEvent: !!event,
                chartCanvas: event?.native?.target?.id || 'unknown'
            });

            addDebugMessage(`Speed chart hover event triggered with ${activeElements.length} active elements`, 'interaction');

            // Change cursor to indicate interactivity
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

    // Create info popup element with enhanced error checking
    createInfoPopup();

    // Test the setup after creation
    setTimeout(() => {
        testChartSetup();
    }, 100);
}

// Create the floating info popup
function createInfoPopup() {
    // Remove existing popup if it exists
    const existingPopup = document.getElementById('chartInfoPopup');
    if (existingPopup) {
        existingPopup.remove();
        console.log('🗑️ Removed existing popup');
    }

    try {
        infoPopup = document.createElement('div');
        infoPopup.id = 'chartInfoPopup';

        // Use your original working CSS with some improvements
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

        // Verify it was added
        const verifyPopup = document.getElementById('chartInfoPopup');
        if (!verifyPopup) {
            throw new Error('Popup not found in DOM after creation');
        }

    } catch (error) {
        console.error('❌ Error creating info popup:', error);
        addDebugMessage(`Error creating info popup: ${error.message}`, 'error');
    }
}

// Handle chart hover events
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

        // Get the chart and dataset
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

        // Session ID matching (keep your original logic)
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

        // Show hover effects
        showHoverMarker(point.lat, point.lng, sessionId);
        showInfoPopup(event, point, sessionId, chartType);

        console.log('✅ Hover processing completed successfully');
        addDebugMessage(`Hover successful - Session: ${sessionId}, Point: ${dataIndex}`, 'interaction');

    } catch (error) {
        console.error('❌ Error in handleChartHover:', error);
        addDebugMessage(`Hover error: ${error.message}`, 'error');
    }
}

function findSessionIdFromDataset(datasetLabel) {
    // Direct match first
    if (trackPoints[datasetLabel]) {
        return datasetLabel;
    }

    // Try extracting session ID if it contains underscore (person_sessionId format)
    if (datasetLabel.includes('_')) {
        const parts = datasetLabel.split('_');
        if (parts.length > 1) {
            const extractedId = parts.slice(1).join('_');
            if (trackPoints[extractedId]) {
                return extractedId;
            }
        }
    }

    // Try partial matching
    const availableKeys = Object.keys(trackPoints);
    for (const key of availableKeys) {
        if (key.includes(datasetLabel) || datasetLabel.includes(key)) {
            return key;
        }
    }

    return datasetLabel; // fallback to original
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

function hideInfoPopup() {
    if (infoPopup) {
        infoPopup.style.display = 'none';
        infoPopup.classList.remove('popup-visible');
    }
}

// Helper function to get weather description from weather code
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

// Helper function to get wind direction text
function getWindDirectionText(degrees) {
    if (degrees === undefined || degrees === null || degrees === 0) {
        return 'N/A';
    }

    // Ensure degrees is a number
    const deg = parseFloat(degrees);
    if (isNaN(deg)) {
        return 'N/A';
    }

    const directions = ["N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                       "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"];

    // Normalize degrees to 0-360 range
    const normalizedDeg = ((deg % 360) + 360) % 360;
    const index = Math.round(normalizedDeg / 22.5) % 16;

    return `${directions[index]} (${normalizedDeg.toFixed(0)}°)`;
}

// Helper function to format weather time
function formatWeatherTime(weatherTime) {
    if (!weatherTime) return "N/A";

    try {
        const date = new Date(weatherTime);
        return date.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
    } catch (e) {
        return weatherTime;
    }
}

// Helper function to get weather emoji based on weather code
function getWeatherEmoji(weatherCode) {
    const weatherEmojis = {
        0: "☀️",   // Clear sky
        1: "🌤️",   // Mainly clear
        2: "⛅",   // Partly cloudy
        3: "☁️",   // Overcast
        45: "🌫️",  // Fog
        48: "🌫️",  // Depositing rime fog
        51: "🌦️",  // Light drizzle
        53: "🌦️",  // Moderate drizzle
        55: "🌧️",  // Dense drizzle
        61: "🌧️",  // Slight rain
        63: "🌧️",  // Moderate rain
        65: "⛈️",  // Heavy rain
        71: "🌨️",  // Slight snow
        73: "❄️",  // Moderate snow
        75: "❄️",  // Heavy snow
        77: "❄️",  // Snow grains
        80: "🌦️",  // Slight rain showers
        81: "🌧️",  // Moderate rain showers
        82: "⛈️",  // Violent rain showers
        85: "🌨️",  // Slight snow showers
        86: "❄️",  // Heavy snow showers
        95: "⛈️",  // Thunderstorm
        96: "⛈️",  // Thunderstorm with hail
        99: "⛈️"   // Thunderstorm with heavy hail
    };

    return weatherEmojis[weatherCode] || "🌤️";
}

// Show info popup with point details including weather data
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
        // Get display information
        const personName = sessionPersonNames[sessionId] || "";
        let displayId = sessionId;
        if (personName) {
            displayId = createDisplayId(sessionId, personName);
        }

        // Format time
        const timeStr = point.timestamp ? point.timestamp.toLocaleTimeString() : 'N/A';
        const dateStr = point.timestamp ? point.timestamp.toLocaleDateString() : 'N/A';
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

        // Extract weather and barometer data
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

            // add Barometer data rows
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
        } else {
            // Show "No data" message
            weatherSection = `
                <div class="weather-section" style="background-color: #f8f9fa; border-radius: 4px; padding: 8px; margin: 8px 0; border-left: 3px solid #999;">
                    <div class="weather-header" style="font-weight: bold; color: #999; margin-bottom: 4px; display: flex; align-items: center; gap: 4px; font-size: 11px;">
                        🌤️ Weather & Atmospheric Conditions
                    </div>
                    <div style="text-align: center; color: #999; font-style: italic; font-size: 10px; padding: 6px;">
                        No weather or barometer data available for this point
                    </div>
                </div>
            `;
        }

        // Create popup content with weather and barometer data
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
            ${weatherSection}
            <div style="margin-top: 8px; padding-top: 6px; border-top: 1px solid #ddd; font-size: 10px; color: #666;">
                Coordinates: ${point.lat.toFixed(6)}, ${point.lng.toFixed(6)}
            </div>
            ${isGPXTrack ? '<div style="font-size: 9px; color: #999; margin-top: 4px;">📁 GPX File Data</div>' : ''}
        `;

        infoPopup.innerHTML = content;

        // Get mouse position and display popup
        let x, y;
        if (event.native) {
            x = event.native.pageX || event.native.clientX + window.scrollX || 0;
            y = event.native.pageY || event.native.clientY + window.scrollY || 0;
        } else {
            x = event.pageX || event.clientX + window.scrollX || 0;
            y = event.pageY || event.clientY + window.scrollY || 0;
        }

        // Position popup
        const finalX = Math.max(10, Math.min(x + 15, window.innerWidth - 420));
        const finalY = Math.max(10, Math.min(y - 10, window.innerHeight - 350)); // Increased height allowance for barometer data

        infoPopup.style.left = finalX + 'px';
        infoPopup.style.top = finalY + 'px';
        infoPopup.style.display = 'block';
        infoPopup.classList.add('popup-visible');

        console.log('✅ Popup with weather and barometer data displayed at:', finalX, finalY);
        addDebugMessage(`Info popup with weather and barometer data shown at coordinates: ${finalX}, ${finalY}`, 'interaction');

        // Adjust position if popup goes off screen
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

// Helper function to extract and validate weather data
function extractWeatherData(point) {
    const hasTemperature = point.temperature !== undefined && point.temperature !== null && point.temperature > 0;
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

    // Test if hover events are properly bound
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

// Helper function to format weather section
function formatWeatherSection(weatherData) {
    if (!weatherData.hasData) {
        return `
            <div style="background-color: #f8f9fa; border-radius: 4px; padding: 6px; margin: 6px 0; border-left: 3px solid #999;">
                <div style="font-weight: bold; color: #999; font-size: 11px;">🌤️ No weather data</div>
            </div>
        `;
    }

    const weatherDescription = getWeatherDescription(weatherData.weatherCode || 0);
    const weatherEmoji = getWeatherEmoji(weatherData.weatherCode || 0);
    const windDirectionText = getWindDirectionText(weatherData.windDirection || 0);

    return `
        <div style="background-color: #f8f9fa; border-radius: 4px; padding: 6px; margin: 6px 0; border-left: 3px solid #FF5722;">
            <div style="font-weight: bold; color: #FF5722; margin-bottom: 4px; font-size: 11px;">
                ${weatherEmoji} Weather Data
            </div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 4px; font-size: 10px;">
                <div><strong>Temp:</strong> ${weatherData.temperature ? weatherData.temperature.toFixed(1) + '°C' : 'N/A'}</div>
                <div><strong>Humidity:</strong> ${weatherData.humidity ? weatherData.humidity + '%' : 'N/A'}</div>
                <div><strong>Wind:</strong> ${weatherData.windSpeed ? weatherData.windSpeed.toFixed(1) + ' km/h' : 'N/A'}</div>
                <div><strong>Dir:</strong> ${windDirectionText}</div>
            </div>
            ${weatherDescription !== 'Code 0' ? `<div style="font-size: 9px; color: #666; margin-top: 2px;">${weatherDescription}</div>` : ''}
        </div>
    `;
}

// Function to test chart interaction functionality
function testChartInteraction() {
    console.log('🧪 Testing chart interaction setup...');

    const tests = {
        altitudeChart: !!altitudeChart,
        speedChart: !!speedChart,
        infoPopup: !!infoPopup,
        trackPoints: Object.keys(trackPoints).length > 0,
        hasCanvasElements: !!document.getElementById('altitudeChart') && !!document.getElementById('speedChart')
    };

    console.log('🧪 Chart interaction test results:', tests);
    addDebugMessage(`Chart interaction test: ${JSON.stringify(tests)}`, 'system');

    if (tests.altitudeChart && tests.speedChart) {
        addDebugMessage('✅ Charts properly initialized with hover support', 'system');
    } else {
        addDebugMessage('❌ Chart initialization issues detected', 'error');
    }

    if (!tests.infoPopup) {
        addDebugMessage('❌ Info popup not created, attempting recreation', 'error');
        createInfoPopup();
    }
}

// Weather data logging function
function logWeatherData(data) {
    if (data.temperature !== undefined && data.temperature > 0) {
        const weatherDesc = getWeatherDescription(data.weatherCode || 0);
        const windDir = getWindDirectionText(data.windDirection || 0);

        addDebugMessage(
            `Weather Update: ${data.temperature}°C, ${data.relativeHumidity}% humidity, ` +
            `${data.windSpeed}km/h ${windDir} wind, ${weatherDesc}`,
            'weather'
        );
    }
}

// Weather data validation function
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

// Weather statistics update function
function updateWeatherStats(point) {
    if (point.temperature !== undefined && point.temperature > 0) {
        weatherStats.totalUpdates++;
        weatherStats.lastTemperature = point.temperature;
        weatherStats.lastUpdate = new Date();

        if (weatherStats.temperatureRange.min === null || point.temperature < weatherStats.temperatureRange.min) {
            weatherStats.temperatureRange.min = point.temperature;
        }
        if (weatherStats.temperatureRange.max === null || point.temperature > weatherStats.temperatureRange.max) {
            weatherStats.temperatureRange.max = point.temperature;
        }

        // Log weather statistics every 10 updates
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

        // Log weather data if present in the message
        if (message.type === 'update' && message.point) {
            logWeatherData(message.point);
        } else if (message.type === 'history_batch' && message.points) {
            // Log weather data from batch points (just the first one to avoid spam)
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

function handleInvalidCoordinates(message) {
    const sessionId = message.sessionId;
    const reason = message.reason || 'Invalid GPS coordinates';
    const otherData = message.otherData || {};
    
    addDebugMessage(`Invalid coordinates for session ${sessionId}: ${reason}`, 'warning');
    
    // Update speed display with non-GPS data if available
    if (otherData.currentSpeed !== undefined) {
        const speedContainer = document.getElementById(`speed-container-${sessionId}`);
        if (speedContainer) {
            // Update speed even with invalid coordinates
            const currentSpeedElement = document.getElementById(`currentSpeed-${sessionId}`);
            if (currentSpeedElement) {
                currentSpeedElement.textContent = parseFloat(otherData.currentSpeed || 0).toFixed(1);
                currentSpeedElement.style.color = getSpeedColor(parseFloat(otherData.currentSpeed || 0));
            }
            
            // Update heart rate if available
            if (otherData.heartRate !== undefined) {
                const heartRateElement = document.getElementById(`heartRate-${sessionId}`);
                if (heartRateElement) {
                    heartRateElement.textContent = otherData.heartRate;
                    heartRateElement.style.color = getHeartRateColor(otherData.heartRate);
                }
            }
            
            // Add visual indicator for invalid GPS
            let gpsIndicator = speedContainer.querySelector('.gps-status');
            if (!gpsIndicator) {
                gpsIndicator = document.createElement('div');
                gpsIndicator.className = 'gps-status';
                gpsIndicator.style.cssText = `
                    position: absolute;
                    top: 5px;
                    right: 5px;
                    width: 12px;
                    height: 12px;
                    border-radius: 50%;
                    background-color: #ff4444;
                    border: 2px solid white;
                    box-shadow: 0 0 4px rgba(0,0,0,0.3);
                    animation: blink 1s infinite;
                `;
                speedContainer.style.position = 'relative';
                speedContainer.appendChild(gpsIndicator);
                
                // Add CSS animation for blinking
                if (!document.getElementById('gps-blink-style')) {
                    const style = document.createElement('style');
                    style.id = 'gps-blink-style';
                    style.textContent = `
                        @keyframes blink {
                            0%, 50% { opacity: 1; }
                            51%, 100% { opacity: 0.3; }
                        }
                    `;
                    document.head.appendChild(style);
                }
            }
            
            gpsIndicator.title = `GPS Error: ${reason}`;
            gpsIndicator.style.backgroundColor = '#ff4444'; // Red for invalid
            
            // Remove the indicator after 5 seconds if coordinates become valid again
            setTimeout(() => {
                if (gpsIndicator && gpsIndicator.parentNode) {
                    gpsIndicator.style.backgroundColor = '#44ff44'; // Green for recovered
                    setTimeout(() => {
                        if (gpsIndicator && gpsIndicator.parentNode) {
                            gpsIndicator.remove();
                        }
                    }, 2000);
                }
            }, 5000);
        }
    }
    
    // Show a temporary notification
    showNotification(`GPS signal lost for ${sessionPersonNames[sessionId] || sessionId}: ${reason}`, 'warning');
    
    // Keep session active in the UI
    const sessionItem = document.querySelector(`[data-session-id="${sessionId}"]`);
    if (sessionItem && !sessionItem.classList.contains('active')) {
        sessionItem.classList.add('active');
        
        // Add a GPS warning indicator to the session item
        const sessionName = sessionItem.querySelector('.session-item-name');
        if (sessionName && !sessionName.querySelector('.gps-warning')) {
            const gpsWarning = document.createElement('span');
            gpsWarning.className = 'gps-warning';
            gpsWarning.innerHTML = ' 📡❌';
            gpsWarning.title = 'GPS signal issues';
            gpsWarning.style.color = '#ff4444';
            sessionName.appendChild(gpsWarning);
            
            // Remove warning after 10 seconds
            setTimeout(() => {
                if (gpsWarning && gpsWarning.parentNode) {
                    gpsWarning.remove();
                }
            }, 10000);
        }
    }
}

function handleHistoryBatch(points) {
    if (!points || points.length === 0) return;

    console.log('📦 Processing history batch:', points.length, 'points');

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

        // Enhanced timestamp parsing to handle multiple formats
        let timestamp;
        try {
            if (point.timestamp.includes('T')) {
                // ISO format from Android device (e.g., "2025-08-13T19:02:27.822717")
                timestamp = new Date(point.timestamp);
            } else if (point.timestamp.includes('-') && point.timestamp.match(/\d{2}-\d{2}-\d{4}/)) {
                // DD-MM-YYYY HH:mm:ss format from server
                timestamp = new Date(point.timestamp.replace(/(\d{2})-(\d{2})-(\d{4})/, '$3-$2-$1'));
            } else {
                // Try parsing as-is (fallback)
                timestamp = new Date(point.timestamp);
            }

            // Validate the parsed timestamp
            if (isNaN(timestamp.getTime())) {
                throw new Error('Invalid timestamp');
            }
        } catch (e) {
            console.warn('Error parsing timestamp:', point.timestamp, e);
            timestamp = new Date(); // Fallback to current time
            addDebugMessage(`Failed to parse timestamp: ${point.timestamp}, using current time`, 'warning');
        }

        // Convert data types and add to trackPoints - WITH WEATHER AND BAROMETER DATA
        const processedPoint = {
            lat: parseFloat(point.latitude),
            lng: parseFloat(point.longitude),
            distance: parseFloat(point.distance) / 1000, // Convert to kilometers
            altitude: parseFloat(point.altitude || 0),
            speed: parseFloat(point.currentSpeed || 0),
            averageSpeed: parseFloat(point.averageSpeed || 0),
            cumulativeElevationGain: parseFloat(point.cumulativeElevationGain || 0),
            heartRate: parseInt(point.heartRate || 0),
            timestamp: timestamp,
            personName: personName,

            // Weather data
            temperature: parseFloat(point.temperature || 0),
            windSpeed: parseFloat(point.windSpeed || 0),
            windDirection: parseFloat(point.windDirection || 0),
            relativeHumidity: parseInt(point.relativeHumidity || point.humidity || 0),
            weatherCode: parseInt(point.weatherCode || 0),
            weatherTime: point.weatherTime || "",

            // Barometer data
            pressure: parseFloat(point.pressure || 0),
            altitudeFromPressure: parseFloat(point.altitudeFromPressure || 0),
            seaLevelPressure: parseFloat(point.seaLevelPressure || 0),
            pressureAccuracy: parseInt(point.pressureAccuracy || 0)
        };

        console.log('🌤️ Processed point weather & barometer data:', {
            sessionId,
            temperature: processedPoint.temperature,
            windSpeed: processedPoint.windSpeed,
            pressure: processedPoint.pressure,
            altitudeFromPressure: processedPoint.altitudeFromPressure,
            timestamp: processedPoint.timestamp.toISOString()
        });

        // Validate and track weather data
        validateWeatherData(processedPoint);
        updateWeatherStats(processedPoint);

        trackPoints[sessionId].push(processedPoint);
    });
}

function finalizeBatchProcessing() {
    if (!isProcessingBatch) return;

    // Sort all sessions by timestamp
    Object.keys(trackPoints).forEach(sessionId => {
        trackPoints[sessionId].sort((a, b) => a.timestamp - b.timestamp);
    });

    // Update visualizations only for sessions that should be displayed
    requestAnimationFrame(() => {
        Object.keys(trackPoints).forEach(sessionId => {
            if (shouldDisplaySession(sessionId)) {
                updateMapTrack(sessionId);
                updateCharts(sessionId);

                // Update speed display with the latest point
                const latestPoint = trackPoints[sessionId][trackPoints[sessionId].length - 1];
                if (latestPoint) {
                    updateSpeedDisplay(sessionId, latestPoint.speed, {
                        averageSpeed: latestPoint.averageSpeed,
                        cumulativeElevationGain: latestPoint.cumulativeElevationGain,
                        heartRate: latestPoint.heartRate,
                        distance: latestPoint.distance,
                        personName: latestPoint.personName ||
                            (window.sessionPersonNames && window.sessionPersonNames[sessionId]) || "",
                        // Pass weather data to display function
                        weather: {
                            temperature: latestPoint.temperature,
                            windSpeed: latestPoint.windSpeed,
                            windDirection: latestPoint.windDirection,
                            relativeHumidity: latestPoint.relativeHumidity,
                            weatherCode: latestPoint.weatherCode,
                            weatherTime: latestPoint.weatherTime
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

    // Enhanced timestamp parsing to handle multiple formats
    let timestamp;
    try {
        if (data.timestamp.includes('T')) {
            // ISO format from Android device (e.g., "2025-08-13T19:02:27.822717")
            timestamp = new Date(data.timestamp);
        } else if (data.timestamp.includes('-') && data.timestamp.match(/\d{2}-\d{2}-\d{4}/)) {
            // DD-MM-YYYY HH:mm:ss format from server
            timestamp = new Date(data.timestamp.replace(/(\d{2})-(\d{2})-(\d{4})/, '$3-$2-$1'));
        } else {
            // Try parsing as-is (fallback)
            timestamp = new Date(data.timestamp);
        }

        // Validate the parsed timestamp
        if (isNaN(timestamp.getTime())) {
            throw new Error('Invalid timestamp');
        }
    } catch (e) {
        console.warn('Error parsing timestamp:', data.timestamp, e);
        timestamp = new Date(); // Fallback to current time
        addDebugMessage(`Failed to parse timestamp: ${data.timestamp}, using current time`, 'warning');
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
        timestamp: timestamp,
        personName: personName,

        // Weather data
        temperature: parseFloat(data.temperature || 0),
        windSpeed: parseFloat(data.windSpeed || 0),
        windDirection: parseFloat(data.windDirection || 0),
        relativeHumidity: parseInt(data.relativeHumidity || 0),
        weatherCode: parseInt(data.weatherCode || 0),
        weatherTime: data.weatherTime || "",

        // Barometer data
        pressure: parseFloat(data.pressure || 0),
        altitudeFromPressure: parseFloat(data.altitudeFromPressure || 0),
        seaLevelPressure: parseFloat(data.seaLevelPressure || 0),
        pressureAccuracy: parseInt(data.pressureAccuracy || 0)
    };

    console.log('🕐 Processed timestamp:', {
        original: data.timestamp,
        parsed: processedPoint.timestamp.toISOString(),
        displayTime: processedPoint.timestamp.toLocaleTimeString(),
        displayDate: processedPoint.timestamp.toLocaleDateString()
    });

    // Validate and track weather data
    validateWeatherData(processedPoint);
    updateWeatherStats(processedPoint);

    if (!trackPoints[sessionId]) {
        trackPoints[sessionId] = [];
    }

    trackPoints[sessionId].push(processedPoint);

    // Clear GPS warning indicators since we received valid coordinates
    clearGPSWarnings(sessionId);

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
            distance: processedPoint.distance,
            personName: processedPoint.personName,
            // Pass weather AND barometer data to display function
            weather: {
                temperature: processedPoint.temperature,
                windSpeed: processedPoint.windSpeed,
                windDirection: processedPoint.windDirection,
                relativeHumidity: processedPoint.relativeHumidity,
                weatherCode: processedPoint.weatherCode,
                weatherTime: processedPoint.weatherTime
            },
            // Barometer data
            barometer: {
                pressure: processedPoint.pressure,
                altitudeFromPressure: processedPoint.altitudeFromPressure,
                seaLevelPressure: processedPoint.seaLevelPressure,
                pressureAccuracy: processedPoint.pressureAccuracy
            }
        });
    });
}

// Add this new helper function
function clearGPSWarnings(sessionId) {
    // Remove GPS status indicator from speed container
    const speedContainer = document.getElementById(`speed-container-${sessionId}`);
    if (speedContainer) {
        const gpsIndicator = speedContainer.querySelector('.gps-status');
        if (gpsIndicator) {
            gpsIndicator.style.backgroundColor = '#44ff44'; // Green for good
            gpsIndicator.title = 'GPS signal restored';
            setTimeout(() => {
                if (gpsIndicator && gpsIndicator.parentNode) {
                    gpsIndicator.remove();
                }
            }, 2000);
        }
    }
    
    // Remove GPS warning from session item
    const sessionItem = document.querySelector(`[data-session-id="${sessionId}"]`);
    if (sessionItem) {
        const gpsWarning = sessionItem.querySelector('.gps-warning');
        if (gpsWarning) {
            gpsWarning.remove();
        }
    }
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
    // Don't update charts for reset or archived sessions
    if (!shouldDisplaySession(sessionId)) {
        addDebugMessage(`Skipping chart update for filtered session: ${sessionId}`, 'system');
        return;
    }

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
    // Don't create speed displays for reset or archived sessions
    if (!shouldDisplaySession(sessionId)) {
        addDebugMessage(`Skipping speed display update for filtered session: ${sessionId}`, 'system');
        return;
    }

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
            totalDistance: 0,
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

    // Rest of the function remains the same for updating values...
    // [Previous updateSpeedDisplay implementation continues here]
    
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
        // Clean up both old sessions and reset/archived sessions
        const timeDiff = now - speedHistory[sessionId].lastUpdate;
        const shouldCleanup = timeDiff > 300000 || !shouldDisplaySession(sessionId); // 5 minutes timeout OR reset/archived session
        
        if (shouldCleanup) {
            // Remove the container instead of just the element
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

        // Clear all data structures (including filtered sessions)
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
            'totalDistance': '0.0',
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

        addDebugMessage('Map reset completed successfully with session filtering', 'system');
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
            activityType: customPoint.type,
            // GPX files typically don't have weather data, but we can add empty fields for consistency
            temperature: 0,
            windSpeed: 0,
            windDirection: 0,
            relativeHumidity: 0,
            weatherCode: 0,
            weatherTime: ""
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

// Update the visibility button appearance in the session list
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

// Zoom to a specific session's track
function zoomToSession(sessionId) {
    addDebugMessage(`Zoom to session requested: ${sessionId}`, 'system');

    // Check if we have track points for this session
    const sessionTrackPoints = trackPoints[sessionId];
    if (!sessionTrackPoints || sessionTrackPoints.length === 0) {
        addDebugMessage(`No track points found for session ${sessionId}`, 'warning');
        showNotification(`No track data available for session ${sessionId}`, 'warning');
        return;
    }

    // Create coordinate array from track points
    const coordinates = sessionTrackPoints.map(point => [point.lat, point.lng]);

    if (coordinates.length === 0) {
        addDebugMessage(`No valid coordinates found for session ${sessionId}`, 'warning');
        return;
    }

    try {
        // Create a temporary polyline to get bounds
        const tempPolyline = L.polyline(coordinates);
        const bounds = tempPolyline.getBounds();

        // Fit map to the track bounds with some padding
        map.fitBounds(bounds, {
            padding: [20, 20],
            maxZoom: 16 // Prevent zooming in too much for short tracks
        });

        addDebugMessage(`Zoomed to session ${sessionId} - ${coordinates.length} points`, 'system');

        // Optional: Flash the track briefly to show which one we zoomed to
        if (polylines[sessionId]) {
            const originalColor = polylines[sessionId].options.color;
            const originalWeight = polylines[sessionId].options.weight;

            // Flash the track
            polylines[sessionId].setStyle({ color: '#FF0000', weight: 6 });
            setTimeout(() => {
                if (polylines[sessionId]) {
                    polylines[sessionId].setStyle({ color: originalColor, weight: originalWeight });
                }
            }, 1000);
        }

    } catch (error) {
        addDebugMessage(`Error zooming to session ${sessionId}: ${error.message}`, 'error');
        showNotification(`Error zooming to session: ${error.message}`, 'warning');
    }
}

// Update the session list UI
function updateSessionList() {
    const sessionsList = document.getElementById('sessionsList');

    if (!availableSessions || availableSessions.length === 0) {
        sessionsList.innerHTML = '<p class="no-sessions">No sessions found.</p>';
        return;
    }

    // Filter out reset and archived sessions
    const filteredSessions = availableSessions.filter(session => {
        const sessionId = session.sessionId;
        // Exclude sessions that contain "_reset_" or "_archived_" in their ID
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

    // Log the filtering results for debugging
    addDebugMessage(`Session list updated: ${filteredSessions.length} primary sessions displayed (${availableSessions.length - filteredSessions.length} reset/archived sessions filtered out)`, 'system');
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

// Setup additional event listeners for chart interactions
function setupChartEventListeners() {
    const altitudeContainer = document.getElementById('altitudeChartContainer');
    const speedContainer = document.getElementById('speedChartContainer');

    [altitudeContainer, speedContainer].forEach(container => {
        if (container) {
            // Hide hover effects when mouse leaves chart
            container.addEventListener('mouseleave', () => {
                console.log('🖱️ Mouse left chart container');
                hideHoverMarker();
                hideInfoPopup();
            });

            // Additional event to ensure hover detection works
            container.addEventListener('mouseenter', () => {
                console.log('🖱️ Mouse entered chart container');
            });
        }
    });

    // Global mouse leave for charts container
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

// Document ready event listener
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

        addDebugMessage('Running Tracker application initialized with interactive chart features and weather data support (distance markers removed)', 'system');

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

// Helper function to check if a session should be displayed (not reset or archived)
function shouldDisplaySession(sessionId) {
    return !sessionId.includes('_reset_') && !sessionId.includes('_archived_');
}

