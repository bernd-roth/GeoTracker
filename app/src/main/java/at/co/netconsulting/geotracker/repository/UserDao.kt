package at.co.netconsulting.geotracker.repository

import androidx.room.*
import at.co.netconsulting.geotracker.domain.User

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("SELECT COUNT(*) FROM User")
    suspend fun getUserCount(): Int

    @Query("SELECT userId FROM User LIMIT 1")
    suspend fun getFirstUserId(): Long
}