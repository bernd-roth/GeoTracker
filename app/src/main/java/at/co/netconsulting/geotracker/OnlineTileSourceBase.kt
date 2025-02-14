package at.co.netconsulting.geotracker

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

object TileSourceConfig {
    val customTileSource = object : OnlineTileSourceBase(
        "SelfHostedTileSource",
        0, 20, 256, ".png",
        arrayOf("https://tile.openstreetmap.org/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            // Using MapTileIndex to get zoom (z), x, and y for the tile URL
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)

            return "$baseUrl$zoom/$x/$y.png"
        }
    }
}