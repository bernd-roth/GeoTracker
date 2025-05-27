package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import at.co.netconsulting.geotracker.domain.Clothing

@Dao
interface ClothingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClothing(clothing: Clothing): Long

    @Update
    suspend fun updateClothing(clothing: Clothing)

    @Delete
    suspend fun deleteClothing(clothing: Clothing)

    @Query("SELECT * FROM clothing WHERE clothingId = :clothingId")
    suspend fun getClothingById(clothingId: Int): Clothing?

    @Query("SELECT * FROM clothing WHERE eventId = :eventId")
    suspend fun getClothingForEvent(eventId: Int): List<Clothing>

    @Query("DELETE FROM clothing WHERE eventId = :eventId")
    suspend fun deleteClothingForEvent(eventId: Int)

    @Query("SELECT COUNT(*) FROM clothing WHERE eventId = :eventId")
    suspend fun getClothingCountForEvent(eventId: Int): Int
}