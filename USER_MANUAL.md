# GeoTracker User Manual

**Version 6.12**

## Table of Contents

1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [Core Tracking Features](#core-tracking-features)
4. [Data Visualization & Analysis](#data-visualization--analysis)
5. [Data Management](#data-management)
6. [Real-Time Sharing](#real-time-sharing)
7. [Settings & Configuration](#settings--configuration)
8. [Hardware Integration](#hardware-integration)
9. [Advanced Features](#advanced-features)

---

## Introduction

GeoTracker is a comprehensive fitness tracking application for Android that provides professional-grade activity tracking, analysis, and visualization. The app supports multiple sports, integrates with external sensors, and offers extensive data export capabilities.

### Supported Activities
- Running (with cadence and stride tracking)
- Cycling (with wheel size and sprocket configuration)
- Hiking (trail and outdoor activity tracking)
- General Sports (customizable for any outdoor activity)

---

## Getting Started

### First Launch

1. **Grant Permissions**: Allow GeoTracker to access:
   - Location (required for GPS tracking)
   - Bluetooth (for heart rate monitors)
   - Storage (for data backup and export)
   - Notifications (for activity alerts and reminders)

2. **Set Up Your Profile**:
   - Navigate to Settings
   - Enter your personal information (height, weight, birth date)
   - Configure your maximum heart rate
   - Set preferred units and language

3. **Configure Battery Optimization**:
   - Exclude GeoTracker from battery optimization
   - This ensures accurate tracking during activities

### Starting Your First Activity

1. Open the app and navigate to the Map Screen
2. Tap the tracking selection button
3. Choose your sport type
4. Wait for GPS signal to stabilize
5. Press Start to begin recording

---

## Core Tracking Features

### GPS & Location Tracking

GeoTracker provides high-precision GPS tracking with:
- **Real-time coordinates capture** (latitude/longitude)
- **Satellite count monitoring** for signal quality assessment
- **Dual altitude tracking**: GPS-based and barometric pressure-based
- **Speed monitoring** with accuracy measurements
- **Distance calculation** (cumulative and segmented)
- **Automatic GPS signal correction** and anomaly detection

The app intelligently smooths GPS data to provide accurate measurements even in challenging conditions.

### Heart Rate Monitoring

Connect Bluetooth LE heart rate monitors to track:
- **Real-time heart rate** during activities
- **Heart rate zones** analysis
- **HR trends** and historical data
- **Multiple device support** for easy switching between sensors

**To pair a heart rate monitor:**
1. Go to Settings or tap the HR sensor icon during recording
2. Enable Bluetooth on your device
3. Select your heart rate monitor from the list
4. Wait for connection confirmation

### Barometric Pressure Sensing

Built-in barometer support provides:
- **High-precision atmospheric pressure monitoring** (hPa/mbar)
- **Altitude calculation** from pressure data (more accurate than GPS altitude)
- **Sea level pressure reference** calibration
- **Pressure trend analysis** for weather prediction
- **Sensor accuracy tracking** (levels 0-3)

### Performance Metrics

During activities, GeoTracker automatically tracks:
- **Cadence**: Steps/minute for running, pedaling rate for cycling
- **Lap timing**: Manual and automatic lap detection
- **Elevation gain/loss**: Total climbing and descending
- **Real-time slope percentage**: Current gradient with smoothed elevation
- **Stride length**: Dynamic calculation for running efficiency
- **Step counting**: Integration with device pedometer
- **Temperature**: Device and environmental temperature
- **Speed analysis**: Current, maximum, average, and moving speed

### Pause/Resume Functionality

- **Pause recording** without losing your session
- **Resume** from pause state with all data preserved
- **Pause time tracking**: See how long you paused
- **Session state persistence**: Data saved even if app closes

### Max Speed Tracking

- Real-time maximum speed calculation and display
- Historical max speed storage for each activity
- Speed comparison across routes
- Speed differential analysis in route comparisons

---

## Data Visualization & Analysis

### Interactive Maps

**OpenStreetMap Integration** with:
- Real-time route tracking with live path drawing
- Dark mode support for night-time activities
- Interactive waypoint management
- Route replay for recorded activities
- Multi-user overlay for following friends

**Speed-Colored Routes**

Routes are colored based on speed for easy visual analysis:
- **Red**: < 2 km/h (stopped or very slow)
- **Yellow**: 2-4 km/h (walking pace)
- **Blue**: 4-6 km/h (brisk walk/slow jog)
- **Green**: > 6 km/h (running/cycling pace)

**Slope-Colored Visualization**

Terrain difficulty mapping with color-coded elevation gradients helps you understand the challenges of your route at a glance.

### Comprehensive Charts & Analytics

**Heart Rate Analysis:**
- HR vs. time graphs
- HR vs. distance graphs
- Heart rate zone distribution
- Zone-based performance visualization
- HR trend tracking across activities

**Elevation & Altitude Analysis:**
- Detailed altitude charts with gain/loss visualization
- GPS vs. barometric altitude comparison
- Altitude profile graphs
- Elevation change rate analysis
- Speed vs. altitude correlation

**Speed & Performance Analysis:**
- Speed distribution graphs
- Pace analysis with moving averages
- Speed vs. altitude correlation
- Interactive speed-distance graphs

**Barometric Pressure Graphs:**
- Pressure trends during activity
- Altitude correlation analysis
- Pressure vs. time/distance visualization

**Weather Correlation Charts:**
- Performance vs. weather condition analysis
- Temperature vs. time/distance graphs
- Wind speed correlation tracking

**Statistical Dashboards:**
- Weekly trend analysis
- Monthly trend analysis
- Yearly trend analysis
- Performance comparison and goal tracking

### Lap & Performance Analysis

Access detailed lap-by-lap breakdowns:
- Lap times and splits
- Distance per lap
- Average speed per lap
- Elevation change per lap
- Lap comparison across activities

**Route Comparison:**
- Compare multiple activities on the same or similar routes
- Speed differential analysis
- Time comparison
- Route similarity scoring
- Visual overlay of multiple tracks

---

## Data Management

### Export Capabilities

GeoTracker supports multiple export formats:

**GPX Export** (Industry-standard GPS exchange format)
- Compatible with most fitness platforms
- Includes all track points and metadata
- Batch export for multiple activities

**KML Export** (Google Earth compatible)
- Visualize routes in Google Earth
- Includes elevation and metadata
- Suitable for sharing and presentation

**Database Backup**
- Complete app data backup
- Manual and automatic scheduled backups
- Restore functionality for data recovery

**To export an activity:**
1. Navigate to Events Screen
2. Long-press or select an activity
3. Choose export format (GPX/KML)
4. Select destination folder
5. Confirm export

### Import Features

**GPX Import**
- Import activities from other devices or apps
- Full track parsing with metadata preservation
- Automatic route visualization
- Event name and sport type customization during import
- Data validation and integrity checking

**Ghost Racer Functionality**
- Import GPX tracks to race against
- Replay imported tracks during current activities
- Real-time comparison of your performance

**To import a GPX file:**
1. Navigate to Events Screen
2. Tap the import button
3. Select GPX file from storage
4. Review and confirm import
5. Customize event details if needed

### Third-Party Integration

**Strava Integration**
- Sync activities to Strava
- Automatic upload support
- Authentication management

**Garmin Connect Integration**
- Export to Garmin platforms
- Compatible with Garmin ecosystem

**TrainingPeaks Integration**
- Training data synchronization
- Workout planning support

**To sync with third-party platforms:**
1. Go to Settings > Export Sync
2. Select platform (Strava/Garmin/TrainingPeaks)
3. Authenticate your account
4. Enable automatic sync or sync manually
5. Choose activities to upload

### Auto Backup

Configure automatic backups to protect your data:
1. Go to Settings > Auto Backup
2. Enable auto backup
3. Set backup time (hour and minute)
4. Choose backup frequency
5. View last and next backup times

---

## Real-Time Sharing

### WebSocket Server Integration

Share your activities in real-time with friends and coaches:
- **Live activity broadcasting**
- **Multi-user support** for following multiple athletes
- **Real-time data streaming** to web dashboard
- **Automatic connection recovery**

**To configure WebSocket sharing:**
1. Go to Settings > WebSocket Configuration
2. Enter server IP address/hostname
3. Set port number
4. Save configuration
5. Enable live sharing during activities

### Following System

Follow friends and training partners:
- Real-time position tracking on map
- Live metrics display from followed users
- Multi-user overlay
- User selection dialog for managing connections

**To follow a user:**
1. Tap the Following button on Map Screen
2. Select users to follow from the list
3. View their positions and metrics in real-time
4. Toggle following on/off as needed

---

## Settings & Configuration

### User Profile Settings

Configure your personal information for accurate calculations:
- **Height**: Used for stride length and calorie estimation
- **Weight**: Used for calorie calculations
- **Birth Date/Age**: For age-based heart rate zones
- **Maximum Heart Rate**: For zone calculation
- **Name**: First and last name for exports
- **Preferred Units**: Metric or Imperial
- **Language**: App display language

### Training Zones Configuration

Customize zones for personalized training:
- **Heart Rate Zones**: Configure 5 zones based on max HR
- **Pace Zones**: Set target pace ranges
- **Zone Alerts**: Optional notifications when entering/exiting zones

### Recording Settings

Fine-tune data collection:
- **Recording Interval**: Time between GPS points (1-60 seconds)
  - Lower = more detail, higher battery usage
  - Higher = less detail, better battery life
- **GPS Accuracy**: Precision vs. battery optimization
- **Data Collection Frequency**: Sensor sampling rate

### Voice Announcements

Configure audio feedback during activities:
- **Interval Notifications**: Time-based announcements (every X minutes)
- **Distance Announcements**: Distance-based updates (every X km/miles)
- **Metric Selection**: Choose which metrics to announce
  - Distance, time, speed, pace, heart rate, etc.

### Battery Management

Optimize battery usage:
- **Battery Optimization Exclusion**: Prevent Android from killing the app
- **Wake Lock Management**: Keep screen on during activities
- **Background Refresh Settings**: Control background activity
- **Notification Preferences**: Configure tracking notifications

### Dark Mode

- **System-Wide Dark Theme**: Toggle dark mode
- **Dark Map Tiles**: Optimized for night-time tracking
- **Theme Persistence**: Setting saved across sessions

### Database Cleanup & Maintenance

Manage storage and data:
- **Manual Database Cleanup**: Remove old or unnecessary data
- **Selective Event Deletion**: Delete specific activities
- **Storage Management**: View database size
- **Cleanup Results**: See what was removed

---

## Hardware Integration

### Bluetooth Connectivity

Connect external sensors:
- **BLE Heart Rate Monitors**: Chest straps and wrist-based HRMs
- **Multi-Device Support**: Switch between multiple sensors
- **Connection Status Monitoring**: Real-time connection feedback
- **Automatic Reconnection**: Reconnect dropped sensors

### Sensor Integration

GeoTracker uses your device's built-in sensors:
- **GPS/GNSS Receiver**: Multi-constellation support (GPS, GLONASS, Galileo, BeiDou)
- **Barometric Pressure Sensor**: For accurate altitude
- **Accelerometer**: Motion detection and step counting
- **Temperature Sensor**: Environmental temperature logging
- **Pedometer**: Device step counter integration

### Device Status Tracking

Monitor hardware performance:
- **Satellite Count**: Number of GPS satellites in view
- **Sensor Accuracy**: Real-time accuracy levels (0-3)
- **Signal Strength**: GPS signal quality
- **Battery Level**: Track battery drain during activities

---

## Advanced Features

### Session Recovery

Automatic crash recovery protects your data:
- Session state persistence to database
- Automatic recovery after app crashes
- Data preservation even if device restarts
- Seamless continuation of interrupted activities

### Route Matching & Comparison

Intelligent route analysis:
- **Automatic Similar Route Detection**: Finds previous activities on same route
- **Route Similarity Scoring**: Quantifies how similar two routes are
- **Metrics Comparison**: Compare speed, time, elevation across routes
- **Waypoint Matching**: Identify routes by key landmarks

### Planned Events & Competitions

Manage your event calendar:
- **Event Scheduling**: Plan races and competitions
- **Event Reminders**: Customizable notifications before events
- **Recurring Events**: Daily, weekly, monthly, yearly, or custom intervals
- **Location-Based Events**: Store event coordinates
- **Event Details**: Website, comments, and entry status
- **Event Types**: Race, training, competition, or custom

**To create a planned event:**
1. Navigate to Competitions Screen
2. Tap "Add Event"
3. Enter event details (name, date, location)
4. Set reminder preferences
5. Configure recurrence if needed
6. Save event

### Waypoint Management

Create custom markers during activities:
- Add waypoints at current location
- Name and describe waypoints
- Store elevation data
- View waypoints on map overlay
- Use waypoints for route navigation

**To add a waypoint:**
1. During recording, tap the waypoint button
2. Enter waypoint name and description
3. Confirm creation
4. Waypoint appears on map

### Bicycle Configuration

Optimize cycling metrics:
- **Wheel Size Configuration**: For accurate distance calculation
- **Sprocket Settings**: For precise cadence calculation
- **Multiple Bicycle Profiles**: Switch between bikes
- **Configuration Persistence**: Settings saved per bike

**To configure your bicycle:**
1. Go to Settings > Bicycle Configuration
2. Enter wheel circumference (mm)
3. Set front and rear sprocket tooth count
4. Save configuration
5. Select bike before cycling activities

### Clothing Logging

Track what you wore:
- Log clothing items for each activity
- Correlate clothing with weather conditions
- Review post-activity for future planning
- Build a history of gear performance

### Elevation Smoothing

Advanced altitude processing:
- **Weighted Moving Average Filter**: Reduces GPS noise
- **Barometric Preference**: Uses barometer data when available
- **Outlier Detection**: Filters impossible altitude changes
- **Configurable Smoothing**: Adjust sensitivity in settings

### Real-Time Calculations

Live metrics computed during activities:
- **Slope Percentage**: Current gradient based on smoothed elevation
- **Moving Averages**: Smoothed speed and pace
- **Distance Accumulation**: Total and lap distance
- **Duration Tracking**: Movement time vs. total time

---

## Main Application Screens

### Map Screen

Real-time tracking display:
- Live position on OpenStreetMap
- Route path with color coding
- Current metrics overlay
- Control buttons (start/pause/stop)
- Waypoint overlay
- Following users overlay

### Statistics Screen

Live metrics dashboard during activity:
- Current speed and pace
- Distance and duration
- Heart rate and zones
- Elevation gain/loss
- Cadence and stride length
- Battery and GPS status

### Events Screen

Activity history management:
- List of all recorded activities
- Filtering and search capabilities
- Sort by date, distance, duration
- Quick actions (export, delete, analyze)
- Activity details preview

### Detail Screens

Access detailed analysis for each activity:
- **Heart Rate Detail**: HR zones, graphs, and statistics
- **Barometer Detail**: Pressure data and altitude analysis
- **Altitude Detail**: Elevation profile with gain/loss
- **Weather Detail**: Weather correlation with performance
- **Route Comparison**: Side-by-side route analysis
- **Lap Analysis**: Detailed lap-by-lap breakdown

### Competitions Screen

Event planning and management:
- List of planned events
- Calendar view of upcoming competitions
- Event details and reminders
- Entry status tracking
- Quick access to event websites

### Settings Screen

Comprehensive configuration:
- User profile
- Training zones
- Recording settings
- Voice announcements
- Auto backup
- WebSocket configuration
- Database maintenance
- About and version info

---

## Tips & Best Practices

### For Accurate GPS Tracking
1. Wait for GPS signal to stabilize before starting (10+ satellites)
2. Keep device screen facing sky when possible
3. Avoid starting in dense urban areas or under heavy tree cover
4. Enable high-accuracy location mode in device settings

### For Battery Optimization
1. Increase recording interval for longer activities (30s instead of 5s)
2. Disable features you don't need (following, live sharing)
3. Use dark mode to reduce screen power consumption
4. Close other apps running in background

### For Heart Rate Monitoring
1. Wet chest strap electrodes before use for better contact
2. Ensure tight fit but not uncomfortable
3. Pair sensor before starting activity
4. Check battery level in sensor regularly

### For Data Safety
1. Enable auto backup
2. Periodically export activities to GPX/KML
3. Sync with cloud platforms (Strava, Garmin)
4. Keep manual backups of important activities

### For Performance Analysis
1. Use route comparison to track improvement
2. Review heart rate zones to optimize training
3. Monitor elevation profiles for trail planning
4. Check yearly statistics for long-term trends

---

## Troubleshooting

### GPS Not Acquiring Signal
- Move to open area away from buildings
- Ensure location permissions are granted
- Check that location services are enabled
- Restart device to refresh GPS

### Heart Rate Monitor Not Connecting
- Ensure Bluetooth is enabled
- Check sensor battery
- Move sensor closer to device during pairing
- Try removing and re-pairing sensor

### App Stops Recording in Background
- Exclude GeoTracker from battery optimization
- Grant background location permission
- Disable battery saver mode during activities
- Check notification settings (tracking notification must be visible)

### Export/Import Failing
- Verify storage permissions are granted
- Ensure sufficient storage space
- Check file format compatibility
- Try smaller batch sizes for batch operations

### WebSocket Connection Issues
- Verify server IP and port are correct
- Check network connectivity
- Ensure server is running
- Try reconnecting from settings

---

## Technical Information

### Database
- SQLite database with Room ORM
- Current schema version: 17
- Automatic migrations supported
- Data integrity validation

### Supported File Formats
- **Import**: GPX
- **Export**: GPX, KML
- **Backup**: SQLite database files

### System Requirements
- Android 6.0 (Marshmallow) or higher
- GPS/GNSS receiver
- Recommended: Barometric pressure sensor
- Optional: Bluetooth LE for external sensors

### Permissions Required
- Location (fine and coarse)
- Bluetooth (for heart rate monitors)
- Storage (read/write for backups and exports)
- Notifications (for activity tracking)
- Wake lock (to keep app running)
- Background location (for continuous tracking)

---

## Support & Feedback

For questions, feature requests, or bug reports, please contact the developer or visit the project repository.

**Current Version**: 6.12

---

**Last Updated**: December 2025
