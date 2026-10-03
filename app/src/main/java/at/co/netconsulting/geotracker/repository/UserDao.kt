package at.co.netconsulting.geotracker.repository

import androidx.room.*
import at.co.netconsulting.geotracker.domain.User

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("SELECT * FROM User WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: Long): User?

    @Query("SELECT * FROM User WHERE firstName = :firstName AND lastName = :lastName AND birthDate = :birthDate ORDER BY userId LIMIT 1")
    suspend fun findByProfile(firstName: String, lastName: String, birthDate: String): User?

    @Query("SELECT COUNT(*) FROM User")
    suspend fun getUserCount(): Int

    @Query("SELECT userId FROM User LIMIT 1")
    suspend fun getFirstUserId(): Long
}