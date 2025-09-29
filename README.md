# GeoTracker App

GeoTracker is a professional-grade Android fitness tracking application designed for serious athletes and fitness enthusiasts. Whether you're running, cycling, hiking, or training for competitions, GeoTracker provides comprehensive GPS tracking, real-time analysis, and advanced data visualization capabilities. With its integrated WebSocket server, you can share your activities with others and follow their progress in real-time.

---

## 🚀 Core Features

### 🏃‍♂️ Multi-Sport Activity Support
- **Running** - Optimized for road running and trail running with cadence and stride tracking
- **Cycling** - Full bike support with configurable wheel size and sprocket settings
- **Hiking** - Trail and outdoor activity tracking with elevation profile analysis
- **General Sports** - Customizable tracking for any outdoor activity

### 📊 Advanced Metrics & Data Collection

#### GPS & Location Tracking
- **High-precision GPS tracking** with satellite count monitoring
- **Real-time coordinates** (latitude/longitude) with accuracy assessment
- **Dual altitude tracking** - GPS and barometric pressure-based
- **Speed monitoring** with accuracy measurements and moving averages
- **Distance calculation** with cumulative and segmented tracking
- **GPS signal quality** evaluation and automatic correction

#### Performance Metrics
- **Heart Rate Monitoring** - Bluetooth LE sensor integration with real-time display
- **Speed Analysis** - Current, maximum, average, and moving speed calculations
- **Cadence Tracking** - Steps per minute for running, pedaling rate for cycling
- **Lap Timing** - Manual and automatic lap detection with detailed analysis
- **Elevation Analysis** - Gain/loss calculations with gradient analysis
- **🆕 Slope Percentage** - Real-time slope calculations with smoothed elevation data
- **Stride Length** - Dynamic stride calculation for running efficiency
- **Step Counting** - Integration with device pedometer

#### Environmental Data
- **Weather Integration** - Real-time weather data via REST API
  - Temperature, wind speed/direction, humidity tracking
  - Weather correlation with performance metrics
- **🆕 Barometric Pressure Sensing** - High-precision atmospheric monitoring
  - Pressure readings in hPa/mbar with accuracy tracking
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
- **Waypoint Management** - Custom markers and points of interest
- **Interactive Selection** - Click-to-select points for detailed analysis

#### Comprehensive Charts & Analytics
- **Heart Rate Analysis**
  - HR vs. time/distance graphs with zone analysis
  - Heart rate trend tracking and zone distribution
- **Elevation Profiles**
  - Detailed altitude charts with gain/loss visualization
  - 🆕 GPS vs. barometric altitude comparison
- **Speed Analysis Charts**
  - Speed distribution and pace analysis
  - Moving average calculations with smoothing
- **🆕 Barometric Pressure Graphs**
  - Pressure trends during activity
  - Altitude correlation analysis
- **Weather Correlation Charts**
  - Performance vs. weather condition analysis
- **Statistical Dashboards**
  - Weekly, monthly, and yearly trend analysis
  - Performance comparison and goal tracking

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

#### Import Features
- **GPX Import** - Full track parsing with metadata preservation
- **Route Visualization** - Imported track display and analysis
- **Progress Tracking** - Import operation monitoring
- **Data Validation** - Integrity checking for imported data

### 🌐 Real-Time Sharing & Web Interface

#### WebSocket Server Integration
- **Live Activity Broadcasting** - Real-time sharing with followers
- **Multi-User Support** - Follow multiple athletes simultaneously
- **Web Dashboard** - Responsive web interface for viewing
- **Session Management** - Automatic connection recovery
- **Docker Deployment** - Containerized server deployment

#### Web Features
- **Interactive Web Map** - Real-time route visualization
- **Live Metrics Display** - Speed, elevation, and distance streaming
- **Following System** - Track friends and training partners
- **Mobile-Responsive Design** - Optimized for all devices

### 📱 User Interface & Navigation

#### Main Application Screens
- **Map Screen** - Real-time tracking with route visualization
- **Statistics Screen** - Comprehensive data analysis and charts
- **Events Screen** - Activity history with filtering and search
- **Competitions Screen** - Planned events and race management
- **Settings Screen** - Configuration and preferences

#### Event & Competition Management
- **🆕 Competition Planning** - Schedule races and training events
- **Event Reminders** - Customizable notifications and alarms
- **Recurring Events** - Daily, weekly, monthly, yearly schedules
- **Location-Based Events** - GPS-based event triggers
- **Activity History** - Comprehensive event database with search

### ⚙️ Advanced Configuration

#### User Profile & Settings
- **Personal Metrics** - Height, weight, age, max heart rate configuration
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

### 🔧 Hardware Integration & Services

#### Sensor Support
- **Bluetooth LE Heart Rate Monitors** - Full HRM integration
- **GPS/GNSS Receivers** - Multi-constellation satellite support
- **Barometric Pressure Sensors** - High-precision altitude tracking
- **Accelerometer Integration** - Motion detection and analysis
- **Temperature Sensors** - Environmental monitoring

#### Background Services
- **Foreground Tracking Service** - Persistent location tracking
- **Session Recovery** - Automatic crash recovery and data preservation
- **Background Sync** - Data synchronization and backup
- **Weather Service** - Automatic weather data collection
- **Database Management** - Intelligent data management and cleanup

---

## 📋 Technical Specifications

### System Requirements
- **Android Version**: Android 10 (API level 29) or higher
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
- **Schema Version**: 17 (with automatic migrations)
- **Data Types**: GPS tracks, metrics, weather data, user preferences
- **Backup**: Automatic local and manual export options

---

## 🛠️ Installation and Setup

### Prerequisites
- **Android Studio**: Latest stable version (recommended)
- **Android SDK**: API level 34 with build tools
- **Gradle**: Version 8.0+ (handled by wrapper)
- **Java/Kotlin**: OpenJDK 17+ with Kotlin 1.9+

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
   - Ensure Android SDK 34 is installed
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
   pip install websockets asyncio
   ```

2. **Update Server Configuration**:
   - Edit `websocket_server.py`
   - Replace `[IP-ADDRESS]` with your server's IP address
   - Configure port (default: 8765)

3. **Start WebSocket Server**:
   ```bash
   python websocket_server.py
   ```

4. **Access Web Interface**:
   ```
   http://[YOUR-SERVER-IP]
   ```

### Docker Deployment (Optional)

1. **Build Docker Image**:
   ```bash
   cd app/src/main/java/at/co/netconsulting/geotracker/websocket/
   docker build -t geotracker-websocket .
   ```

2. **Run Container**:
   ```bash
   docker-compose down && sudo docker-compose up -d
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
- **Background Processing**: Efficient multi-threaded location processing
- **Session Recovery**: Automatic crash protection and data preservation

### Comprehensive Analysis
- **Multi-Dimensional Visualization**: Speed, elevation, and slope-colored route mapping
- **Statistical Dashboards**: Weekly, monthly, and yearly performance trends
- **Interactive Charts**: Heart rate, altitude, and performance correlation analysis
- **Export Flexibility**: GPX, KML, and database backup options

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

This project is licensed under the [MIT License](LICENSE).

---

## 🙋‍♂️ Support
For any issues or feature requests, please open an [issue on GitHub](https://github.com/yourusername/GeoTracker/issues) or contact us at berndroth0@gmail.com

---

## 🌟 Acknowledgments
- OpenStreetMap for the map integration.
- All contributors who make this project better!