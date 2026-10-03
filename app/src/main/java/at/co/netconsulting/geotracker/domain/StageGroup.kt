package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "stage_groups", indices = [Index(value = ["name"], unique = true)])
data class StageGroup(
    @PrimaryKey(autoGenerate = true) val stageGroupId: Long = 0,
    val name: String
)
