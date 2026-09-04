# GeoTracker App

GeoTracker is a professional-grade Android fitness tracking application designed for serious athletes and fitness enthusiasts. Whether you're running, cycling, hiking, or training for competitions, GeoTracker provides comprehensive GPS tracking, real-time analysis, and advanced data visualization capabilities. With its integrated WebSocket server, you can share your activities with others and follow their progress in real-time.

> **📖 For end users**: See the [User Manual](USER_MANUAL.md) for detailed step-by-step instructions, tips, and troubleshooting.
> **👨‍💻 For developers**: Continue reading below for technical specifications, installation, and contributing guidelines.

---

## ✨ Latest Features (Version 10.06)

### New in v10.x (May–July 2026)

- **🆕 End-to-End Cadence Tracking** - Running cadence is captured from Android's step detector, stored with each metric, exported in GPX-compatible form, transmitted through WebSocket, and retained by the Flask/PostgreSQL stack. Running cadence is displayed in steps per minute, while cycling-compatible data uses cycles per minute.
- **🆕 Cadence Analysis** - Event cards now show average cadence and a preview graph. A dedicated analysis screen provides minimum/average/maximum cadence, interactive cadence-vs-time, distance, altitude, and speed charts, plus a map for the selected sample.
- **🆕 Web Cadence Monitoring** - Live and analysis pages include cadence in stats, charts, range summaries, and hover popups. The live stats panel preserves the most recent cadence when a partial update omits it.
- **🆕 Web Route Rerun** - Replay any stored session on an animated map with play/pause, stop, seeking, 1×–250× playback, keyboard shortcuts, live metrics, and Overview, Follow, or pitched Fly-Over camera modes.
- **🆕 Route Rerun Enhancements** - The Android route-rerun view adds an altitude profile with a moving reference marker, current-position marker, loaded-track profile support, and a Reverse Direction option.
- **🆕 Wings for Life Run** - Dedicated running mode with the official virtual catcher-car schedule, live map overlay, distance/headway updates, catch announcements, and catch-point details while recording continues.
- **🆕 Event Route Previews** - Expanded event cards show a read-only OpenStreetMap mini-map with the recorded route, start/end markers, automatic bounds, and bounded downsampling for long tracks.
- **🆕 Google Calendar Integration** - Export one recording or all recordings to a selected writable Google Calendar, then remove individual or bulk exports without deleting the original GeoTracker activities.
- **🆕 Multi-Day Competitions** - Planned events support an optional end date, native start/end date pickers, and display across every covered calendar day. Server schema checks, synchronization, and owner-scoped remote deletion were updated with the new field.
- **🆕 Flexible Backup Destinations** - Database and GPX backups can use separate Android document-tree folders or direct SMB shares, with connection testing, cleanup support, and a restored Downloads fallback.
- **🆕 More Activity Presets** - Added Wings for Life Run, half-marathon, inline skating, and ice hockey choices.
- **🆕 Improved GPX Import** - Choose whether an imported track becomes one of My Events or a Ghost Racer. Validation now accepts valid GPX files that do not include an XML declaration.
- **🆕 Web Chart Controls** - Live and analysis charts support wheel/pinch zoom, Ctrl+drag pan, double-click reset, collapsible chart panels, compact navigation between Live, Analysis, Heatmap, and Rerun, and recorder names in session lists.
- **🆕 Following Mini-Map** - A picture-in-picture map follows the selected athlete and shows their current position and recent trail.
- **🆕 Organized Event Actions** - The Events toolbar now groups commands under Calendar, Statistics, Server, and Tools, adds concise descriptions, and clearly marks destructive actions. The obsolete invalid-event cleanup flow and duplicate Events-tab slope legend were removed.
- **🆕 Achievements Overview** - Personal bests grouped by sport family, with official Running distances (1/5/10 km, half marathon, marathon, 50/100 km) and 6/12/24/48/72-hour/6-day efforts. The activity summary now lives in the same screen, and implausible GPX timestamp gaps cannot create false timed records.
- **🆕 Structured Sport Classification** - Activities now store sport family, optional discipline, and optional event format separately. Achievement milestones are derived from recorded metrics; only Backyard Ultra and Wings for Life Run appear as special recording modes because they change recorder behavior.
- **🆕 Redis Live-History Cache** - The WebSocket service uses Redis for the rolling 48-hour live-tracking history while PostgreSQL remains the durable source for analysis and historical sessions. Cache cleanup and session deletion stay synchronized.

#### Reliability and Correctness

- **Recording Startup** - Fixed races between background and foreground GPS services that could leave a new recording frozen after its first location, plus a later GPS callback race at recording start.
- **Recording State** - Corrected notification time drift, stale metrics during stop, live-track gaps after resets/restarts, and visibility of recording controls while following users.
- **Live Web Sessions** - Reset fragments are merged into one logical route, live sessions reload after WebSocket restarts, cadence survives partial messages, and chart/map state remains consistent.
- **Events and Statistics** - Event search remains correct while data loads asynchronously, Statistics graphs load the full event history, and downloaded sessions are filtered per user.
- **Server Synchronization** - Re-upload detection works after server-side deletion, incomplete live sessions can be atomically replaced from the complete local recording without wiping PostgreSQL, live-streamed session media uploads correctly, planned-event deletes no longer leave stale remote rows, and failures are surfaced to the user.
- **Map and GPX Handling** - Fixed route-preview arrow cleanup, added a covered-path speed legend, and hardened GPX validation and import behavior.

### Previously in v9.x (April 2026)

- **🆕 Connect Events** - Combine multiple recorded events into a single event, merging locations, metrics, laps, and recalculating totals
- **🆕 BMI (Body Mass Index)** - Auto-computed from height and weight in Settings, persisted in Room database (User table) and transmitted via WebSocket; server stores BMI in Postgres and includes it in history queries
- **🆕 Workout Stats Photo Overlay** - Strava-like feature to select a photo or take one with the camera, overlay workout stats (distance, duration, elevation, heart rate) with a semi-transparent bar, and share via Android share sheet; handles EXIF orientation
- **🆕 Lactate Threshold Test** - New "Fitness Test" sport category with a 30-minute time trial protocol; requires HR sensor, auto-collects samples during the last 20 minutes, calculates average LTHR and LT Pace, with countdown overlay and result dialog
- **🆕 Training Calendar** - Strava-style month-by-month calendar with colored sport-type dots, year selector with navigation, year summary (activities, active days, sport types), and tap-to-expand day details
- **🆕 Calendar Competitions Management** - Add, edit, and delete competitions directly from the calendar; full form with reminders (date/time pickers, recurring options), amber diamond dots for competitions, and server sync dialog (Upload/Download/Test Connection)
- **🆕 Calendar–Events Navigation** - Tap a recorded event in calendar day details to navigate to EventsScreen filtered to that date; bidirectional navigation between Calendar and Events
- **🆕 Live Map Heart Rate Chart** - Third chart (distance × HR) on the live tracking page with toggleable altitude overlay, automatic layout switch from 2-chart to 3-chart mode, and corrected multi-session chart hover (nearest-point interaction)
- **🆕 Analysis Page Heart Rate Chart** - Third HR chart with altitude overlay toggle on the analysis page, matching live map implementation
- **🆕 Live Map Session Info** - Session start time and elapsed duration displayed in the speed panel
- **🆕 Download Events Grouped by User** - Sessions grouped by user with section headers, per-user All/None selection, per-user Check and Download buttons; no longer limited to 200 sessions
- **🆕 Analysis Page Extended Search** - Increased to 200 sessions per page with broader search filtering
- **🆕 Improved Crash Recovery** - Duplicate initialization guard, broadened event-reuse checks on system restarts, and distance preservation across service restarts
- **🆕 Lap Timing Reliability** - Multiple fixes: database as source of truth after service restarts, phantom lap burst prevention, and correct fastest-lap calculation excluding incomplete laps
- **🆕 Server-Side Lap Backfill Fix** - Correct first-lap duration calculation using actual session start time; even time distribution when backfilling multiple laps
- **🆕 Chart Visibility Fixes** - HR chart correctly toggles with session visibility; Refresh Sessions restores all hidden chart datasets and map elements

### Previously in v8.x (January–March 2026)

- **Web Analysis Page** - Full-featured analysis dashboard with session browser, interactive map, elevation/speed/HR charts, range selection with aggregated stats, weather and barometer overlays, and media gallery with video streaming
- **Global Heatmap** - Web-based heatmap page showing GPS activity density across all sessions, with real-time radius and opacity sliders
- **Backyard Ultra Mode** - Dedicated sport type with manual lap control, per-lap polylines, and fixed lap distance tracking
- **Multisport Race Support** - Duathlon, Triathlon, and Ultratriathlon subcategories with discipline-grouped lap display
- **Home Screen Widget** - Android widget showing duration, distance, speed, altitude, temperature, barometer, total activity time, and inactivity timer
- **Download Events from Server** - Browse, filter, search, and import remote sessions into the local database; blocks download of actively recording sessions
- **Flask REST API** - Full server-side REST API for session management, media upload, and event synchronization
- **Geocoding** - Automatic reverse geocoding of start and end locations (city, country, address) for every event
- **Waypoint Photos** - Attach photos to waypoints with dedicated `waypoint_photos` table and cascade delete
- **Media Sync** - Background media upload worker with retry logic; server-side session media storage with thumbnails
- **Pace Graph** - Canvas-based pace (min/km) chart on event cards with 5th–95th percentile filtering
- **Heart Rate Charts** - Speed vs Heart Rate, HR vs Altitude, and HR vs Speed charts in Statistics and live following
- **Map Satellite Toggle** - Switch between satellite and street map view on the main screen
- **Directional Arrows** - Arrow overlays showing travel direction along recorded routes
- **Yearly Stats Breakdown** - Per-sport-type distance breakdown in weekly expandable view with total distance sum
- **Event Source Tracking** - Distinguish recorded events from imported Ghost Racer GPX tracks; ghost racers excluded from yearly statistics
- **Barometer QNH Calibration** - Improved barometric altitude using QNH-based sea level pressure correction
- **Organized Exports** - Database backup, GPX, KML, and FIT files saved to separate folders
- **Edit Event Enhancements** - Comment (multiline) and Clothing fields added to event edit form
- **Live Tracking Website Overhaul** - Dark mode with CSS variables, split/lap table, mini HR chart, collapsible stats panel, and polyline persistence across theme toggles
- **Following Path Display** - Configurable path display mode (full path or from current position) for followed users
- **Thread Safety** - `@Volatile` annotations on cross-thread fields in ForegroundService for data race prevention
- **Gradle 9.0** - Upgraded Android Gradle Plugin to 9.0.0

### Previously in v6.x

- **Pause/Resume Functionality** - Pause activities without losing data
- **Max Speed Tracking** - Real-time maximum speed with historical comparison
- **Ghost Racer** - Import GPX files and race against previous performances
- **Third-Party Integration** - Sync with Strava, Garmin Connect, and TrainingPeaks
- **Following System** - Track multiple athletes in real-time on your map
- **Auto Backup Service** - Scheduled automatic database backups
- **Bicycle Configuration** - Wheel size and sprocket settings for accurate cycling metrics
- **Planned Events & Reminders** - Schedule competitions with customizable notifications
- **Clothing Logging** - Track gear worn during activities
- **Route Matching Engine** - Automatic detection of similar routes with similarity scoring
- **Enhanced Database** - 7 new tables for expanded functionality

---

## 🚀 Core Features

### 🏃‍♂️ Multi-Sport Activity Support
- **Running** - Road, trail, ultra, marathon, half-marathon, orienteering, Backyard Ultra, and Wings for Life Run modes with cadence and stride tracking
- **Cycling** - Full bike support with configurable wheel size and sprocket settings
- **Hiking** - Trail and outdoor activity tracking with elevation profile analysis
- **Skating & Ice Hockey** - Dedicated skating category with inline skating plus an ice hockey winter-sport preset
- **🆕 Backyard Ultra** - Dedicated mode with manual lap control, per-lap polylines, and fixed lap distance
- **🆕 Wings for Life Run** - Virtual catcher car with live distance/headway comparison, map position, and catch announcements
- **🆕 Multisport Race** - Duathlon, Triathlon, and Ultratriathlon with discipline-grouped lap display
- **🆕 Fitness Test** - Lactate Threshold 30-min time trial with HR sensor requirement, phase countdown, and automatic LTHR/LT Pace calculation
- **General Sports** - Customizable tracking for any outdoor activity

### 📊 Advanced Metrics & Data Collection

#### GPS & Location Tracking
- **High-precision GPS tracking** with satellite count monitoring
- **Real-time coordinates** (latitude/longitude) with accuracy assessment
- **Dual altitude tracking** - GPS and barometric pressure-based
- **Speed monitoring** with accuracy measurements and moving averages
- **Distance calculation** with cumulative and segmented tracking
- **GPS signal quality** evaluation and automatic correction
- **🆕 Reverse Geocoding** - Automatic start/end city, country, and address lookup for events

#### Performance Metrics
- **Heart Rate Monitoring** - Bluetooth LE sensor integration with real-time display
- **Speed Analysis** - Current, maximum, average, and moving speed calculations
- **Cadence Tracking** - Step-detector-based running cadence in steps per minute, with stored/exported/WebSocket cadence data and cycling-compatible cycles-per-minute display
- **Lap Timing** - Manual and automatic lap detection with detailed analysis
- **Elevation Analysis** - Gain/loss calculations with gradient analysis
- **🆕 Slope Percentage** - Real-time slope calculations with smoothed elevation data
- **Stride Length** - Dynamic stride calculation for running efficiency
- **Step Counting** - Integration with device pedometer
- **🆕 BMI Tracking** - Auto-computed Body Mass Index from height and weight, synced via WebSocket

#### Environmental Data
- **Weather Integration** - Real-time weather data via REST API
  - Temperature, wind speed/direction, humidity tracking
  - Weather correlation with performance metrics
- **Barometric Pressure Sensing** - High-precision atmospheric monitoring
  - Pressure readings in hPa/mbar with accuracy tracking
  - 🆕 QNH-based altitude calibration for improved barometric accuracy
  - Altitude calculation from pressure with sea level correction
  - Pressure trend analysis for weather prediction
- **Temperature Logging** - Device and environmental temperature tracking

### 🗺️ Advanced Visualization & Analysis

#### Interactive Maps
- **OpenStreetMap Integration** - High-quality map tiles with dark mode support
- **Real-time Route Tracking** - Live path drawing with GPS breadcrumbs
- **🆕 Speed-Colored Routes** - Visual speed analysis with color gradients
  - Red: <2km/h, Yellow: 2-4km/h, Blue: 4-6km/h, Green: >6km/h
- **🆕 Slope-Colored Visualization** - Terrain difficulty mapping
  - Color-coded route segments showing elevation gradients
- **Route Replay** - Playback functionality for recorded activities
- **🆕 Route Rerun** - Altitude profile, current/reference markers, and optional reverse-direction racing against a loaded track
- **🆕 Event Route Mini-Maps** - Read-only route previews with start/end markers on expanded event cards
- **🆕 Following Mini-Map** - Picture-in-picture view of a followed athlete's current position and trail
- **Waypoint Management** - Custom markers and points of interest
- **🆕 Waypoint Photos** - Attach photos to waypoints for route documentation
- **Interactive Selection** - Click-to-select points for detailed analysis
- **🆕 Satellite/Street Toggle** - Switch between satellite imagery and street map view
- **🆕 Directional Arrows** - Arrow overlays showing travel direction along routes

#### Comprehensive Charts & Analytics
- **Heart Rate Analysis**
  - HR vs. time/distance graphs with zone analysis
  - 🆕 HR vs. speed and HR vs. altitude correlation charts
  - 🆕 HR chart with toggleable altitude overlay on live map and analysis page
  - Heart rate trend tracking and zone distribution
- **Elevation Profiles**
  - Detailed altitude charts with gain/loss visualization
  - 🆕 GPS vs. barometric altitude comparison
- **Speed Analysis Charts**
  - Speed distribution and pace analysis
  - Moving average calculations with smoothing
- **🆕 Cadence Analysis**
  - Cadence vs. time, distance, altitude, and speed
  - Interactive point selection linked to the route map
  - Minimum, average, and maximum cadence summaries
- **🆕 Barometric Pressure Graphs**
  - Pressure trends during activity
  - Altitude correlation analysis
- **Weather Correlation Charts**
  - Performance vs. weather condition analysis
- **🆕 Pace Analysis**
  - Pace (min/km) chart with 5th–95th percentile filtering
  - Best and slowest pace tracking per event
- **Statistical Dashboards**
  - Weekly, monthly, and yearly trend analysis
  - 🆕 Per-sport-type distance breakdown in weekly view
  - 🆕 Total covered distance summary
  - Performance comparison and goal tracking
- **🆕 Interactive Web Charts**
  - Wheel/pinch zoom, Ctrl+drag pan, and double-click reset on live and analysis charts

#### Lap & Performance Analysis
- **🆕 Detailed Lap Analysis** - Comprehensive lap-by-lap breakdowns
- **Route Comparison** - Compare multiple activities on same route
- **Performance Trends** - Long-term progress tracking
- **Interactive Path Maps** - Detailed point-by-point analysis
- **Slope Analysis** - Gradient calculations with filtering

### 🔄 Data Management & Export

#### Export Capabilities
- **GPX Export** - Industry-standard GPS exchange format
- **KML Export** - Google Earth compatible format
- **Batch Operations** - Export multiple activities simultaneously
- **Database Backup** - Automatic and manual backup scheduling
- **Route Sharing** - Share routes via standard formats
- **🆕 Organized Export Folders** - Separate directories for database backups, GPX, KML, and FIT files
- **🆕 Configurable Backup Targets** - Separate Android document-tree folders or direct SMB destinations for database and GPX backups, with Downloads fallback
- **🆕 Google Calendar Export** - Add individual or all recorded events to a selected Google Calendar and remove exported copies later

#### Event Management
- **🆕 Connect Events** - Merge multiple recorded events into one, combining locations, metrics, laps, and recalculating totals

#### Import Features
- **GPX Import** - Full track parsing with metadata preservation
- **🆕 Import Destination Choice** - Import a GPX track into My Events or the Ghost Racer library
- **🆕 Tolerant GPX Validation** - Accept valid GPX documents with or without an XML declaration
- **Route Visualization** - Imported track display and analysis
- **Ghost Racer** - Race against imported GPX tracks with real-time comparison
- **🆕 Event Source Tracking** - Distinguish recorded events from imported ghost racer tracks
- **🆕 Ghost Racer Statistics Exclusion** - Imported tracks excluded from yearly statistics
- **Progress Tracking** - Import operation monitoring
- **Data Validation** - Integrity checking for imported data
- **Event Customization** - Configure sport type and event name during import
- **🆕 Download from Server** - Browse, filter, and import remote sessions with search, incomplete session filtering, active recording detection, and user-grouped session display with per-user download controls

#### Third-Party Platform Integration
- **🆕 Strava Sync** - Automatic activity upload to Strava
- **🆕 Garmin Connect Integration** - Export to Garmin ecosystem
- **🆕 TrainingPeaks Support** - Training data synchronization
- **Multi-Platform Export** - Batch sync to multiple services
- **Authentication Management** - Secure credential storage

### 🌐 Real-Time Sharing & Web Interface

#### WebSocket Server Integration
- **Live Activity Broadcasting** - Real-time sharing with followers
- **Multi-User Support** - Follow multiple athletes simultaneously
- **Web Dashboard** - Responsive web interface for viewing
- **Session Management** - Automatic connection recovery
- **Docker Deployment** - Containerized server deployment
- **🆕 Flask REST API** - Full server-side REST API for session management, event synchronization, and media upload/download
- **🆕 Media Upload** - Background media sync worker with retry logic and per-file progress tracking
- **🆕 Session History** - Server-side full session history queries for analysis page
- **🆕 BMI Storage** - Server-side BMI column in Postgres users table, included in history queries
- **🆕 Lap Backfill Fix** - Correct first-lap duration using actual session start time
- **🆕 Log Rotation** - Server-side log rotation for long-running deployments
- **🆕 Redis Live Cache** - Rolling 48-hour cache for live tracking history, with PostgreSQL retained as the durable analysis store

#### Web Features
- **Interactive Web Map** - Real-time route visualization
- **Live Metrics Display** - Speed, elevation, and distance streaming
- **Following System** - Track friends and training partners
- **Mobile-Responsive Design** - Optimized for all devices
- **🆕 Analysis Page** - Session browser with interactive map, elevation/speed/HR charts, range selection, weather overlays, and media gallery with video streaming; extended search with 200 sessions per page
- **🆕 Global Heatmap** - GPS activity density visualization across all sessions with adjustable radius and opacity
- **🆕 Dark Mode** - CSS variable-based theme system with light/dark toggle
- **🆕 Split/Lap Table** - Live lap data display during tracking
- **🆕 Mini HR Chart** - Real-time heart rate sparkline in the stats panel
- **🆕 Live Map HR Chart** - Third chart (distance × HR) with toggleable altitude overlay and nearest-point hover interaction
- **🆕 Session Info Display** - Start time and elapsed duration in the live map speed panel
- **🆕 Route Rerun Page** - Animated session playback with seeking, speed controls, live metrics, and Overview/Follow/Fly-Over cameras
- **🆕 Cadence Charts** - Cadence monitoring in live stats, analysis charts, range summaries, and chart hover details
- **🆕 Zoomable Charts** - Wheel/pinch zoom, Ctrl+drag pan, double-click reset, and collapsible chart panels

#### Following System
- **Real-Time User Following** - Track multiple athletes simultaneously
- **Live Position Overlay** - See followed users on your map
- **Metrics Streaming** - View followed users' speed, HR, and elevation
- **User Selection Dialog** - Easy management of followed connections
- **Following Service** - Background service for persistent tracking
- **🆕 Path Display Modes** - Full path or from-current-position display for followed users
- **🆕 Following Widget Updates** - Widget continues updating while recording
- **🆕 Followed User Mini-Map** - Bottom-left picture-in-picture map centered on the selected athlete and their trail

### 📱 User Interface & Navigation

#### Main Application Screens
- **Map Screen** - Real-time tracking with route visualization
- **Statistics Screen** - Comprehensive data analysis and charts
- **Events Screen** - Activity history with filtering and search
- **🆕 Calendar Screen** - Strava-style training calendar with activity and competition visualization
- **Competitions Screen** - Planned events and race management
- **Settings Screen** - Configuration and preferences

#### Event & Competition Management
- **🆕 Training Calendar** - Strava-style month-by-month calendar with colored sport-type dots, year selector, year summary, and tap-to-expand day details
- **🆕 Calendar Competitions** - Add, edit, and delete competitions from the calendar with full form, reminders, amber diamond dots, and server sync
- **🆕 Calendar–Events Navigation** - Bidirectional navigation: tap calendar events to filter Events screen, return to calendar from Events
- **🆕 Competition Planning** - Schedule races and training events
- **Event Reminders** - Customizable notifications and alarms
- **Recurring Events** - Daily, weekly, monthly, yearly schedules
- **Location-Based Events** - GPS-based event triggers
- **Activity History** - Comprehensive event database with search
- **🆕 Date Range Filtering** - Persistent date filter state across navigation
- **🆕 Event Edit Form** - Comment (multiline) and Clothing fields for detailed activity logging
- **🆕 Media Sections** - Separate pictures and videos sections in event details
- **🆕 Multi-Day Competitions** - Optional end dates and native date pickers, with events painted across their full date range
- **🆕 Google Calendar Management** - Per-event and bulk export/delete actions that leave GeoTracker recordings untouched
- **🆕 Categorized Event Tools** - Calendar, Statistics, Server, and Tools menus with descriptions and destructive-action styling

### 🎯 Special Features

#### Activity Control
- **Pause/Resume Functionality** - Pause recording without losing session
- **Max Speed Tracking** - Real-time maximum speed with historical comparison
- **Session Recovery** - Automatic crash recovery and data preservation with improved distance preservation across service restarts
- **Lap Management** - Manual and automatic lap detection with reliable persistence
- **🆕 Home Screen Widget** - Live dashboard showing duration, distance, speed, altitude, temperature, barometer, and activity/inactivity timers
- **🆕 Live View Fix** - Continuous track display without breaks when app restarts during recording
- **🆕 Workout Stats Photo Overlay** - Strava-like photo overlay with distance, duration, elevation, and heart rate stats; share via Android share sheet
- **🆕 Connect Events** - Combine multiple recorded events into a single merged event
- **🆕 Wings for Life Run** - Track the virtual catcher car on the map and in the foreground notification

#### Route Analysis & Comparison
- **🆕 Route Matching** - Automatic detection of similar routes
- **🆕 Route Similarity Scoring** - Quantify how similar two routes are
- **🆕 Speed Differential Analysis** - Compare performance across route attempts
- **🆕 Waypoint System** - Create custom markers and points of interest
- **🆕 Enhanced Route Rerun** - Altitude profile, moving reference/current-position markers, and reverse-direction support

#### Activity Planning
- **🆕 Planned Events** - Schedule races and competitions
- **🆕 Event Reminders** - Customizable notifications with recurring schedules
- **🆕 Location-Based Events** - GPS-triggered event notifications
- **Event History** - Track entered and finished competitions

#### Equipment & Configuration
- **🆕 Bicycle Configuration** - Wheel size and sprocket settings for accurate metrics
- **🆕 Clothing Logging** - Track gear worn during activities
- **Multiple Bike Profiles** - Switch between different bicycle configurations
- **Equipment History** - Correlate performance with equipment choices

### ⚙️ Advanced Configuration

#### User Profile & Settings
- **Personal Metrics** - Height, weight, age, max heart rate configuration
- **🆕 BMI** - Auto-computed Body Mass Index from height and weight, synced to server
- **🆕 Lactate Threshold Test** - 30-minute time trial with HR sensor to determine LTHR and LT Pace
- **Training Zones** - Customizable heart rate and pace zones
- **Voice Announcements** - Configurable interval notifications
- **Dark Mode Support** - System-wide dark theme
- **Language Support** - Multi-language interface

#### Technical Configuration
- **GPS Accuracy Settings** - Precision vs. battery optimization
- **Recording Intervals** - Customizable data collection frequency
- **Battery Management** - Intelligent power usage optimization
- **WebSocket Configuration** - Server settings and connection management
- **Notification Preferences** - Granular notification control
- **🆕 Auto Backup Scheduling** - Automated database backups with configurable timing
- **🆕 Backup Destination Settings** - Configure and test independent SMB or Android folder targets for GPX and database backups
- **🆕 Data Retention** - Selective event deletion and cleanup results

### 🔧 Hardware Integration & Services

#### Sensor Support
- **Bluetooth LE Heart Rate Monitors** - Full HRM integration
- **GPS/GNSS Receivers** - Multi-constellation satellite support
- **Barometric Pressure Sensors** - High-precision altitude tracking
- **Accelerometer Integration** - Motion detection and analysis
- **Step Detector** - Low-power sensor input for live running cadence
- **Temperature Sensors** - Environmental monitoring

#### Background Services
- **Foreground Tracking Service** - Persistent location tracking with notification
- **Background Location Service** - Location tracking in background mode
- **🆕 Barometer Sensor Service** - Continuous pressure data collection
- **🆕 Auto Backup Service** - Scheduled automatic database backups
- **🆕 Following Service** - Real-time user following and position tracking
- **Weather Service** - Automatic weather data collection via API
- **Database Backup Service** - Manual and automated backup operations
- **GPX Export Service** - Background GPX file generation
- **🆕 Media Sync Worker** - Background media upload with retry and per-file progress tracking

#### Broadcast Receivers
- **🆕 Auto Backup Receiver** - Triggers scheduled backups
- **🆕 Boot Completed Receiver** - Restarts services on device boot
- **🆕 Reminder Broadcast Receiver** - Event reminder notifications
- **🆕 Package Update Receiver** - Handles app updates

---

## 📋 Technical Specifications

### System Requirements
- **Current App Version**: 10.06
- **Android Version**: Android 10 (API level 29) or higher
- **Compile SDK**: Android API level 36
- **Target SDK**: Android 14 (API level 34)
- **Architecture**: ARM64, ARM32 support
- **RAM**: Minimum 2GB, Recommended 4GB+
- **Storage**: 100MB+ free space for app and data
- **Network**: WiFi or mobile data for weather and WebSocket features

### Hardware Requirements
- **GPS/GNSS**: Required for location tracking
- **Sensors**:
  - Accelerometer (motion detection)
  - Barometric pressure sensor (optional, for enhanced altitude)
  - Temperature sensor (optional)
- **Bluetooth**: Bluetooth 4.0+ for heart rate monitor support
- **Camera**: Optional, for route photos and documentation

### Database & Storage
- **Database**: SQLite with Room ORM
- **Schema Version**: 28 (with automatic migrations)
- **Data Types**: GPS tracks, metrics, weather data, user preferences
- **Backup**: Automatic/manual backups to Downloads, user-selected folders, or SMB shares

#### Database Entities (16 Tables)
- **User Table** - Profile data (height, weight, birth date, max HR, BMI)
- **Event Table** - Activity records with sport family, discipline, event format, legacy sport type, name, date, comments, and geocoding fields
- **Metric Table** - Time-series data (HR, speed, distance, cadence, elevation, pressure, etc.)
- **Location Table** - GPS coordinates linked to events with backyard lap support
- **Weather Table** - Weather API data with temperature, wind, humidity
- **DeviceStatus Table** - Satellite count, signal strength, battery level
- **CurrentRecording Table** - Session state for crash recovery
- **LapTime Table** - Lap timing with start/end times and distance
- **PlannedEvent Table** - Competitions and scheduled events
- **Clothing Table** - Gear worn during activities
- **WheelSprocket Table** - Bicycle configuration data
- **Network Table** - WebSocket and REST API configuration
- **Waypoint Table** - Custom markers with coordinates and descriptions
- **EventMedia Table** - Pictures and videos associated with recorded events
- **DisciplineTransition Table** - Discipline changes within multisport races
- **🆕 WaypointPhoto Table** - Photos attached to waypoints with cascade delete

### Architecture & Codebase Structure

The project follows **MVVM (Model-View-ViewModel)** architecture with clean separation of concerns:

- **Composables (60 files)** - Jetpack Compose UI components and screens
  - Main screens: Map, Statistics, Events, Calendar, Competitions, Settings
  - Detail screens: Heart Rate, Cadence, Barometer, Altitude, Weather, Lap Analysis
  - Dialogs: Recording, Sensor pairing, Tracking selection

- **ViewModels (6 files)** - State management and business logic
  - MVVM pattern implementation
  - LiveData/StateFlow for reactive UI updates

- **Domain Layer (16 Room entities)** - Database schema and data models
  - Room database entities
  - Data Transfer Objects (DTOs)

- **Data & Repository Layer (64 files)** - Repository pattern implementation
  - 16 DAO (Data Access Object) interfaces
  - Repository classes for data access abstraction
  - State management classes

- **Services (15 files)** - Background processing
  - Foreground/Background location tracking
  - Sensor data collection
  - WebSocket communication
  - Automatic backups

- **Tools & Utilities (16 files)** - Helper functions
  - GPS calculations and smoothing algorithms
  - Route matching engine
  - Distance/speed conversions
  - Date/time formatting

- **Export/Import (6+ files)** - File format handling
  - GPX parser and generator
  - KML export functionality
  - Data validation

- **Sync (6 files)** - Third-party platform integration
  - Strava API integration
  - Garmin Connect support
  - TrainingPeaks sync

- **Receivers (5 files)** - System event handling
  - Boot completion
  - Backup scheduling
  - Event reminders
  - Package updates

**Total Codebase**: 219 production Kotlin files, plus Android and unit tests, organized in a scalable architecture

---

## 🛠️ Installation and Setup

### Prerequisites
- **Android Studio**: Latest stable version (recommended)
- **Android SDK**: API level 36 with build tools
- **Gradle / Android Gradle Plugin**: Gradle 9.5 wrapper with AGP 9.3.1
- **Java / Kotlin**: OpenJDK 17+ for the build; Java bytecode target 11; Kotlin 2.2.10

### Mobile App Installation

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/bernd-roth/GeoTracker.git
   cd GeoTracker
   ```

2. **Open in Android Studio**:
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned GeoTracker directory
   - Wait for Gradle sync to complete

3. **Configure Build Environment**:
   - Ensure Android SDK 36 is installed
   - Set up emulator or connect physical device
   - Enable USB debugging on physical device

4. **Build and Install**:
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```
   Or use Android Studio's "Run" button

### WebSocket Server Setup

1. **Configure Python Environment**:
   ```bash
   cd app/src/main/java/at/co/netconsulting/geotracker/websocket/
   python -m pip install websockets asyncpg python-dateutil redis
   ```

2. **Configure Services**:
   - Start PostgreSQL for durable session/history storage
   - Start Redis for the rolling live-history cache
   - Set `POSTGRES_*`, `REDIS_*`, and `DATA_RETENTION_HOURS` environment variables as needed
   - The WebSocket server listens on port `6789`

3. **Start WebSocket Server**:
   ```bash
   python websocket_server.py
   ```

4. **Access Web Interface**:
   ```
   http://[YOUR-SERVER-IP]/
   ```
   When Nginx or the Docker stack is running, it serves the Live, Analysis, Heatmap, and Rerun pages and proxies WebSocket traffic. A standalone WebSocket process is available directly at `ws://[YOUR-SERVER-IP]:6789`.

### Docker Deployment (Optional)

1. **Review Configuration**:
   - Replace example PostgreSQL credentials, host volume paths, and the Flask build context in `docker-compose.yml`
   - Provide Redis credentials through the configured environment file
   - Ensure the external Redis network referenced by the Compose file exists

2. **Start the Stack**:
   ```bash
   cd app/src/main/java/at/co/netconsulting/geotracker/websocket/
   docker compose up -d --build
   ```

### Initial Configuration

1. **App Permissions**:
   - Grant location permissions (required)
   - Allow background app refresh
   - Enable notification permissions
   - Grant Bluetooth permissions for heart rate monitors

2. **User Profile Setup**:
   - Enter personal metrics (height, weight, age)
   - Configure maximum heart rate
   - Set preferred units and language
   - Configure voice announcement intervals

3. **Hardware Setup**:
   - Pair Bluetooth heart rate monitor (optional)
   - Calibrate barometric sensor (automatic)
   - Test GPS accuracy and satellite reception

---

## 🎯 Key Advantages

### Professional-Grade Accuracy
- **Elevation Smoothing**: Advanced weighted moving average filter reduces GPS noise
- **Barometric Integration**: Pressure sensor prioritization for enhanced altitude accuracy
- **Dual-Source Validation**: GPS and barometric data cross-validation
- **Outlier Detection**: Automatic filtering of GPS jumps and signal anomalies

### Real-Time Performance
- **Live Slope Calculation**: Instant gradient analysis with smoothed elevation data
- **WebSocket Streaming**: Sub-second data transmission to followers
- **Redis Live History**: Fast rolling live-session recovery separated from durable PostgreSQL analysis data
- **Background Processing**: Efficient multi-threaded location processing
- **Session Recovery**: Automatic crash protection and data preservation

### Advanced Data Processing
- **🆕 Elevation Smoothing Algorithm**: Weighted moving average filter with configurable sensitivity
- **🆕 GPS Anomaly Detection**: Automatic outlier filtering and signal correction
- **🆕 Moving Average Calculations**: Real-time smoothing for speed and pace
- **🆕 Route Matching Engine**: Intelligent similarity detection and scoring
- **🆕 Distance Normalization**: Time-based comparison across different route lengths
- **🆕 Multi-Threaded Processing**: Parallel data collection and analysis

### Comprehensive Analysis
- **Multi-Dimensional Visualization**: Speed, elevation, and slope-colored route mapping
- **Statistical Dashboards**: Weekly, monthly, and yearly performance trends
- **Interactive Charts**: Heart rate, altitude, cadence, and performance correlation analysis with zoom and pan
- **Export Flexibility**: GPX, KML, and database backup options

### Extensive Feature Set
- **80+ Major Features** across all categories
- **60 Compose UI files** for the comprehensive user interface
- **16 Room database tables** for complete data management
- **15 Background Services** for reliable operation
- **219 Production Kotlin Files** across UI, data, services, sensors, and utilities
- **Flask REST API** with session management and media endpoints
- **Web Live, Analysis, Heatmap, and Rerun Pages** with interactive charts and playback
- **Redis + PostgreSQL Server Architecture** for live history and durable analysis
- **Multi-Platform Integration** (Strava, Garmin, TrainingPeaks)
- **Training Calendar** with activity and competition visualization
- **Lactate Threshold Testing** with guided protocol and result persistence
- **Cadence Tracking and Analysis** from Android sensor capture through web visualization
- **Google Calendar and SMB Integration** for planning and backup workflows
- **Professional Architecture** with MVVM pattern and Room ORM

---

## 📸 Screenshots

*Coming Soon - App screenshots showcasing the user interface and key features*

---

## 🚀 Getting Started Guide

### First Run Checklist
1. **Install the app** and grant all required permissions
2. **Complete profile setup** with personal metrics
3. **Test GPS reception** in open area for accuracy verification
4. **Pair heart rate monitor** (optional) for complete metrics
5. **Start your first activity** and explore the real-time features

### Pro Tips
- **Enable barometric sensor** for enhanced altitude accuracy
- **Use voice announcements** to stay focused during activities
- **Set up WebSocket sharing** to track friends in real-time
- **Export data regularly** for backup and analysis in other tools
- **Calibrate max heart rate** for accurate zone calculations
- **🆕 Configure auto backups** to protect your activity data
- **🆕 Import GPX tracks** to use Ghost Racer for performance comparison
- **🆕 Sync to Strava/Garmin** for multi-platform activity sharing
- **🆕 Set up bicycle configuration** for accurate cycling metrics
- **🆕 Use pause/resume** during activities without losing data
- **🆕 Add the home screen widget** for at-a-glance stats during recording
- **🆕 Try Backyard Ultra mode** for fixed-distance lap-based training
- **🆕 Use the web analysis page** for detailed post-activity review with charts and media
- **🆕 Download server events** to keep your local database in sync across devices
- **🆕 Run a Lactate Threshold test** to determine your LTHR and LT Pace with a guided 30-minute protocol
- **🆕 Use the training calendar** for a visual overview of your activities and competitions
- **🆕 Create workout stats photos** to share your achievements with a Strava-like photo overlay
- **🆕 Connect events** to merge split recordings into a single activity
- **🆕 Use Route Rerun** in the app or web dashboard to replay a course, follow the camera, or race it in reverse
- **🆕 Review cadence analysis** to correlate running form with time, distance, altitude, and speed
- **🆕 Export recordings to Google Calendar** and remove the calendar copies without affecting app data
- **🆕 Configure SMB or custom-folder backups** separately for GPX files and the Room database
- **🆕 Try Wings for Life Run** to train against the virtual catcher car

### Troubleshooting Common Issues
- **GPS Accuracy**: Ensure clear sky view and allow 1-2 minutes for satellite lock
- **Battery Optimization**: Disable battery optimization for GeoTracker in system settings
- **WebSocket Connection**: Check firewall settings and network connectivity
- **Heart Rate Pairing**: Ensure device is in pairing mode and close to phone

---

## 🤝 Contributing
We welcome contributions! To contribute:

1. Fork the repository.
2. Create a feature branch:
   ```bash
   git checkout -b feature-name
   ```
3. Commit your changes:
   ```bash
   git commit -m "Add feature description"
   ```
4. Push to the branch:
   ```bash
   git push origin feature-name
   ```
5. Open a Pull Request.

---

## 📜 License

This project is licensed under the MIT License.

---

## 🙋‍♂️ Support
For any issues or feature requests, please open an [issue on GitHub](https://github.com/bernd-roth/GeoTracker/issues) or contact us at berndroth0@gmail.com

---

## 🌟 Acknowledgments
- OpenStreetMap for the map integration.
- All contributors who make this project better!
