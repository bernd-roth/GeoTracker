package at.co.netconsulting.geotracker

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.infowindow.InfoWindow

class ComposeInfoWindow(
    mapView: MapView,
    private val composeContent: @Composable () -> Unit
) : InfoWindow(0, mapView) {

    private var composeView: ComposeView = ComposeView(mapView.context).apply {
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    init {
        // Set the view to be used for the info window
        mView = composeView
    }

    override fun onOpen(item: Any?) {
        // Set the compose content
        composeView.setContent {
            composeContent()
        }

        // Make sure the view is measured correctly
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)
    }

    override fun onClose() {
    }
}