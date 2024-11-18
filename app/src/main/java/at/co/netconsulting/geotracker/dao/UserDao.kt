package at.co.netconsulting.geotracker.dao

import androidx.room.*
import at.co.netconsulting.geotracker.db.User

@Dao
interface UserDao {
    // Insert a user into the database
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    // Insert multiple users
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>): List<Long>

    // Update a user
    @Update
    suspend fun updateUser(user: User)

    // Delete a user
    @Delete
    suspend fun deleteUser(user: User)

    // Get a user by ID
    @Query("SELECT * FROM User WHERE userId = :id")
    suspend fun getUserById(id: Int): User?

    // Get all users
    @Query("SELECT * FROM User")
    suspend fun getAllUsers(): List<User>

    // Delete all users
    @Query("DELETE FROM User")
    suspend fun deleteAllUsers()
}