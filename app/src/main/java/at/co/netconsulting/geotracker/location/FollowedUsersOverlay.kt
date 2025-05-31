package at.co.netconsulting.geotracker.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import at.co.netconsulting.geotracker.data.FollowedUserPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

class FollowedUsersOverlay(
    private val context: Context,
    private val mapView: MapView
) : Overlay() {

    private val followedUserMarkers = mutableMapOf<String, Marker>()
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

    fun updateFollowedUsers(followedUserData: Map<String, FollowedUserPoint>) {
        // Remove markers for users no longer being followed
        val currentSessionIds = followedUserData.keys
        val markersToRemove = followedUserMarkers.keys - currentSessionIds

        for (sessionId in markersToRemove) {
            followedUserMarkers[sessionId]?.let { marker ->
                mapView.overlays.remove(marker)
            }
            followedUserMarkers.remove(sessionId)
            userColors.remove(sessionId)
        }

        // Add or update markers for followed users
        for ((sessionId, userPoint) in followedUserData) {
            updateUserMarker(sessionId, userPoint)
        }

        mapView.invalidate()
    }

    private fun updateUserMarker(sessionId: String, userPoint: FollowedUserPoint) {
        val geoPoint = GeoPoint(userPoint.latitude, userPoint.longitude)

        val marker = followedUserMarkers.getOrPut(sessionId) {
            createUserMarker(sessionId, userPoint).also { newMarker ->
                mapView.overlays.add(newMarker)
            }
        }

        // Update marker position and info
        marker.position = geoPoint
        marker.title = "${userPoint.person}"
        marker.snippet = buildUserInfo(userPoint)

        // Update the marker icon with latest data
        marker.icon = createUserIcon(sessionId, userPoint)
    }

    private fun createUserMarker(sessionId: String, userPoint: FollowedUserPoint): Marker {
        val marker = Marker(mapView)

        // Assign a color to this user if not already assigned
        if (sessionId !in userColors) {
            userColors[sessionId] = availableColors[colorIndex % availableColors.size]
            colorIndex++
        }

        marker.position = GeoPoint(userPoint.latitude, userPoint.longitude)
        marker.title = userPoint.person
        marker.snippet = buildUserInfo(userPoint)
        marker.icon = createUserIcon(sessionId, userPoint)
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

    private fun createUserIcon(sessionId: String, userPoint: FollowedUserPoint): android.graphics.drawable.Drawable {
        val color = userColors[sessionId] ?: Color.RED
        val size = 80
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
            textSize = 24f
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

        // Add speed indicator if moving
        if (userPoint.currentSpeed > 1.0f) {
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
        for (marker in followedUserMarkers.values) {
            mapView.overlays.remove(marker)
        }
        followedUserMarkers.clear()
        userColors.clear()
        colorIndex = 0
        mapView.invalidate()
    }

    fun getFollowedUserPositions(): List<GeoPoint> {
        return followedUserMarkers.values.map { it.position }
    }

    fun getUserColor(sessionId: String): Int? {
        return userColors[sessionId]
    }

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        // This overlay doesn't draw anything directly as we use individual markers
    }
}