package at.co.netconsulting.geotracker.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log
import at.co.netconsulting.geotracker.data.FollowedUserPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline

class FollowedUsersOverlay(
    private val context: Context,
    private val mapView: MapView
) : Overlay() {

    private val followedUserMarkers = mutableMapOf<String, Marker>()
    private val followedUserStartMarkers = mutableMapOf<String, Marker>()
    private val followedUserPolylines = mutableMapOf<String, Polyline>()
    private val userColors = mutableMapOf<String, Int>()
    private val availableColors = listOf(
        Color.RED,
        Color.BLUE,
        Color.GREEN,
        Color.MAGENTA,
        Color.CYAN,
        Color.YELLOW,
        Color.parseColor("#FF5722"), // Deep Orange
        Color.parseColor("#9C27B0"), // Purple
        Color.parseColor("#2196F3"), // Blue
        Color.parseColor("#4CAF50")  // Green
    )
    private var colorIndex = 0

    companion object {
        private const val TAG = "FollowedUsersOverlay"
    }

    /**
     * Update with individual points (backward compatibility)
     */
    fun updateFollowedUsers(followedUserData: Map<String, FollowedUserPoint>) {
        Log.d(TAG, "updateFollowedUsers called with ${followedUserData.size} users")
        // Convert to trails format for consistency
        val trailsData = followedUserData.mapValues { (_, point) -> listOf(point) }
        updateFollowedUsersWithTrails(trailsData)
    }

    /**
     * Update with full trails (new method)
     */
    fun updateFollowedUsersWithTrails(followedUserTrails: Map<String, List<FollowedUserPoint>>) {
        Log.d(TAG, "🎯 updateFollowedUsersWithTrails called with ${followedUserTrails.size} users")

        followedUserTrails.forEach { (sessionId, trail) ->
            Log.d(TAG, "  User $sessionId: ${trail.size} points")
            if (trail.size >= 2) {
                Log.d(TAG, "    ✅ Trail has ${trail.size} points - will create polyline")
                Log.d(TAG, "    Start: ${trail.first().latitude},${trail.first().longitude}")
                Log.d(TAG, "    End: ${trail.last().latitude},${trail.last().longitude}")
            } else {
                Log.d(TAG, "    ⚠️ Trail has only ${trail.size} points - no polyline")
            }
        }

        // Remove markers and polylines for users no longer being followed
        val currentSessionIds = followedUserTrails.keys
        val markersToRemove = followedUserMarkers.keys - currentSessionIds

        if (markersToRemove.isNotEmpty()) {
            Log.d(TAG, "Removing markers for users: $markersToRemove")
        }

        for (sessionId in markersToRemove) {
            // Remove current position marker
            followedUserMarkers[sessionId]?.let { marker ->
                mapView.overlays.remove(marker)
                Log.d(TAG, "Removed current marker for $sessionId")
            }
            followedUserMarkers.remove(sessionId)

            // Remove start marker
            followedUserStartMarkers[sessionId]?.let { marker ->
                mapView.overlays.remove(marker)
                Log.d(TAG, "Removed start marker for $sessionId")
            }
            followedUserStartMarkers.remove(sessionId)

            // Remove polyline
            followedUserPolylines[sessionId]?.let { polyline ->
                mapView.overlays.remove(polyline)
                Log.d(TAG, "Removed polyline for $sessionId")
            }
            followedUserPolylines.remove(sessionId)

            userColors.remove(sessionId)
        }

        // Add or update markers and polylines for followed users
        for ((sessionId, trail) in followedUserTrails) {
            if (trail.isNotEmpty()) {
                Log.d(TAG, "Processing trail for $sessionId")
                updateUserTrail(sessionId, trail)
            }
        }

        Log.d(TAG, "Final overlay state:")
        Log.d(TAG, "  Current markers: ${followedUserMarkers.size}")
        Log.d(TAG, "  Start markers: ${followedUserStartMarkers.size}")
        Log.d(TAG, "  Polylines: ${followedUserPolylines.size}")
        Log.d(TAG, "  Map overlays count: ${mapView.overlays.size}")

        mapView.invalidate()
        Log.d(TAG, "Map invalidated")
    }

    private fun updateUserTrail(sessionId: String, trail: List<FollowedUserPoint>) {
        val currentPoint = trail.last()
        val startPoint = trail.first()

        Log.d(TAG, "updateUserTrail for $sessionId: ${trail.size} points")

        // Assign a color to this user if not already assigned
        if (sessionId !in userColors) {
            userColors[sessionId] = availableColors[colorIndex % availableColors.size]
            colorIndex++
            Log.d(TAG, "Assigned color ${userColors[sessionId]} to $sessionId")
        }

        // Update or create polyline if trail has multiple points
        if (trail.size > 1) {
            Log.d(TAG, "Creating/updating polyline for $sessionId")
            updateUserPolyline(sessionId, trail)

            // Update or create start marker
            Log.d(TAG, "Creating/updating start marker for $sessionId")
            updateStartMarker(sessionId, startPoint)
        } else {
            Log.d(TAG, "Not creating polyline for $sessionId - only ${trail.size} points")
        }

        // Update or create current position marker
        Log.d(TAG, "Creating/updating current marker for $sessionId")
        updateCurrentMarker(sessionId, currentPoint)
    }

    private fun updateUserPolyline(sessionId: String, trail: List<FollowedUserPoint>) {
        val color = userColors[sessionId] ?: Color.RED

        Log.d(TAG, "🔗 Creating polyline for $sessionId with ${trail.size} points, color: $color")

        // Remove existing polyline
        followedUserPolylines[sessionId]?.let { existingPolyline ->
            mapView.overlays.remove(existingPolyline)
            Log.d(TAG, "Removed existing polyline for $sessionId")
        }

        // Create new polyline
        val geoPoints = trail.map { point -> GeoPoint(point.latitude, point.longitude) }
        Log.d(TAG, "GeoPoints: ${geoPoints.take(3)}${if (geoPoints.size > 3) "..." else ""}")

        val polyline = Polyline().apply {
            setPoints(geoPoints)
            this.color = color
            width = 8f // Increased width for better visibility
            title = "${trail.last().person}'s trail (${trail.size} points)"
        }

        // Add polyline to map
        mapView.overlays.add(polyline)
        followedUserPolylines[sessionId] = polyline

        Log.d(TAG, "✅ Added polyline to map for $sessionId")
        Log.d(TAG, "   Points: ${polyline.actualPoints.size}")
        Log.d(TAG, "   Width: ${polyline.width}")
        Log.d(TAG, "   Color: ${polyline.color}")
        Log.d(TAG, "   Total overlays on map: ${mapView.overlays.size}")
    }

    private fun updateStartMarker(sessionId: String, startPoint: FollowedUserPoint) {
        val geoPoint = GeoPoint(startPoint.latitude, startPoint.longitude)

        val startMarker = followedUserStartMarkers.getOrPut(sessionId) {
            Log.d(TAG, "Creating new start marker for $sessionId")
            createStartMarker(sessionId, startPoint).also { newMarker ->
                mapView.overlays.add(newMarker)
                Log.d(TAG, "Added start marker to map for $sessionId")
            }
        }

        // Update marker position and info
        startMarker.position = geoPoint
        startMarker.title = "${startPoint.person} - Start"
        startMarker.snippet = "Started at ${startPoint.timestamp}"
        Log.d(TAG, "Updated start marker for $sessionId")
    }

    private fun updateCurrentMarker(sessionId: String, currentPoint: FollowedUserPoint) {
        val geoPoint = GeoPoint(currentPoint.latitude, currentPoint.longitude)

        val marker = followedUserMarkers.getOrPut(sessionId) {
            Log.d(TAG, "Creating new current marker for $sessionId")
            createUserMarker(sessionId, currentPoint).also { newMarker ->
                mapView.overlays.add(newMarker)
                Log.d(TAG, "Added current marker to map for $sessionId")
            }
        }

        // Update marker position and info
        marker.position = geoPoint
        marker.title = "${currentPoint.person}"
        marker.snippet = buildUserInfo(currentPoint)

        // Update the marker icon with latest data
        marker.icon = createUserIcon(sessionId, currentPoint, isCurrentPosition = true)
        Log.d(TAG, "Updated current marker for $sessionId at ${geoPoint.latitude},${geoPoint.longitude}")
    }

    private fun createStartMarker(sessionId: String, startPoint: FollowedUserPoint): Marker {
        val marker = Marker(mapView)
        marker.position = GeoPoint(startPoint.latitude, startPoint.longitude)
        marker.title = "${startPoint.person} - Start"
        marker.snippet = "Started at ${startPoint.timestamp}"
        marker.icon = createStartIcon(sessionId)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        return marker
    }

    private fun createUserMarker(sessionId: String, userPoint: FollowedUserPoint): Marker {
        val marker = Marker(mapView)
        marker.position = GeoPoint(userPoint.latitude, userPoint.longitude)
        marker.title = userPoint.person
        marker.snippet = buildUserInfo(userPoint)
        marker.icon = createUserIcon(sessionId, userPoint, isCurrentPosition = true)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        return marker
    }

    private fun buildUserInfo(userPoint: FollowedUserPoint): String {
        val speedKmh = String.format("%.1f", userPoint.currentSpeed)
        val distanceKm = String.format("%.2f", userPoint.distance / 1000)

        return buildString {
            append("Speed: ${speedKmh} km/h\n")
            append("Distance: ${distanceKm} km")
            if (userPoint.heartRate != null) {
                append("\nHeart Rate: ${userPoint.heartRate} bpm")
            }
            if (userPoint.timestamp.isNotEmpty()) {
                append("\nLast update: ${userPoint.timestamp}")
            }
        }
    }

    private fun createStartIcon(sessionId: String): android.graphics.drawable.Drawable {
        val color = userColors[sessionId] ?: Color.RED
        val size = 60
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Create paint for the flag
        val flagPaint = Paint().apply {
            isAntiAlias = true
            this.color = color
            style = Paint.Style.FILL
        }

        // Create paint for the border
        val borderPaint = Paint().apply {
            isAntiAlias = true
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val centerX = size / 2f
        val centerY = size / 2f

        // Draw flag shape
        val flagPath = android.graphics.Path().apply {
            moveTo(centerX - 15, centerY - 15)
            lineTo(centerX + 15, centerY - 10)
            lineTo(centerX + 15, centerY + 5)
            lineTo(centerX - 15, centerY + 10)
            close()
        }

        canvas.drawPath(flagPath, flagPaint)
        canvas.drawPath(flagPath, borderPaint)

        // Draw flag pole
        val polePaint = Paint().apply {
            isAntiAlias = true
            this.color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawLine(centerX - 15, centerY - 15, centerX - 15, centerY + 20, polePaint)

        return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
    }

    private fun createUserIcon(sessionId: String, userPoint: FollowedUserPoint, isCurrentPosition: Boolean): android.graphics.drawable.Drawable {
        val color = userColors[sessionId] ?: Color.RED
        val size = if (isCurrentPosition) 80 else 60
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Create paint for the circle
        val circlePaint = Paint().apply {
            isAntiAlias = true
            this.color = color
            style = Paint.Style.FILL
        }

        // Create paint for the border
        val borderPaint = Paint().apply {
            isAntiAlias = true
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        // Create paint for the text
        val textPaint = Paint().apply {
            isAntiAlias = true
            this.color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = if (isCurrentPosition) 24f else 18f
            typeface = Typeface.DEFAULT_BOLD
        }

        val centerX = size / 2f
        val centerY = size / 2f
        val radius = (size - 8) / 2f

        // Draw circle
        canvas.drawCircle(centerX, centerY, radius, circlePaint)
        canvas.drawCircle(centerX, centerY, radius, borderPaint)

        // Draw user initial or icon
        val initial = userPoint.person.firstOrNull()?.toString()?.uppercase() ?: "?"
        val textBounds = Rect()
        textPaint.getTextBounds(initial, 0, initial.length, textBounds)
        val textY = centerY + textBounds.height() / 2f

        canvas.drawText(initial, centerX, textY, textPaint)

        // Add speed indicator if moving (only for current position)
        if (isCurrentPosition && userPoint.currentSpeed > 1.0f) {
            val speedPaint = Paint().apply {
                isAntiAlias = true
                this.color = Color.GREEN
                style = Paint.Style.FILL
            }

            // Draw small speed indicator dot
            canvas.drawCircle(centerX + radius - 8, centerY - radius + 8, 6f, speedPaint)
        }

        return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
    }

    fun clearAllMarkers() {
        Log.d(TAG, "clearAllMarkers called")

        // Remove current position markers
        for (marker in followedUserMarkers.values) {
            mapView.overlays.remove(marker)
        }
        followedUserMarkers.clear()

        // Remove start markers
        for (marker in followedUserStartMarkers.values) {
            mapView.overlays.remove(marker)
        }
        followedUserStartMarkers.clear()

        // Remove polylines
        for (polyline in followedUserPolylines.values) {
            mapView.overlays.remove(polyline)
        }
        followedUserPolylines.clear()

        userColors.clear()
        colorIndex = 0
        mapView.invalidate()

        Log.d(TAG, "All markers and polylines cleared")
    }

    fun getFollowedUserPositions(): List<GeoPoint> {
        return followedUserMarkers.values.map { it.position }
    }

    fun getUserColor(sessionId: String): Int? {
        return userColors[sessionId]
    }

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        // This overlay doesn't draw anything directly as we use individual markers and polylines
    }
}