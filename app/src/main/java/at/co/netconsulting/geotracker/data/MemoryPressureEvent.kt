package at.co.netconsulting.geotracker.data

data class MemoryPressureEvent(val level: Int)
data class MemoryPressureReliefEvent(val previousLevel: Int)