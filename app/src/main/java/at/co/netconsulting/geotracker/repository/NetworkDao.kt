package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import at.co.netconsulting.geotracker.domain.Network

@Dao
interface NetworkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetwork(network: Network): Long

    @Update
    suspend fun updateNetwork(network: Network)

    @Delete
    suspend fun deleteNetwork(network: Network)

    @Query("SELECT * FROM network WHERE networkId = :networkId")
    suspend fun getNetworkById(networkId: Int): Network?

    @Query("SELECT * FROM network WHERE eventId = :eventId ORDER BY networkId DESC LIMIT 1")
    suspend fun getNetworkForEvent(eventId: Int): Network?

    @Query("SELECT * FROM network WHERE eventId = :eventId")
    suspend fun getAllNetworksForEvent(eventId: Int): List<Network>

    @Query("DELETE FROM network WHERE eventId = :eventId")
    suspend fun deleteNetworksForEvent(eventId: Int)

    @Query("SELECT COUNT(*) FROM network WHERE eventId = :eventId")
    suspend fun getNetworkCountForEvent(eventId: Int): Int
}