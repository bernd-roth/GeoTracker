**GeoTracker**

The intention of GeoTracker is to track, and analyse your covered track.
It is designed to track almost all kind of sports like running, cycling, climbing, kayakig, swimming, ...
Furthermore, the things below you can track with this app, and display some of them on an OpenStreetMap with a Websocket server, written in Python.

* covered path
* covered Kilometers
* speed
* average speed
* altitude
* elevation gain and loss
* record weather data
* heart rate

**Websocket server**

The websocket server receives all data from all connected users and forwards this data to a webserver where the current covered path, speed, altitude, distance, etc. is shown to all users.
