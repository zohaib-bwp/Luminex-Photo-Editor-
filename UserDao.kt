package com.luminex.photoenhancer.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.luminex.photoenhancer.models.User

@Dao
interface UserDao {

    // Get user by email
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    // Get current user (first user in database)
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUserSync(): User?

    // Get current user as LiveData
    @Query("SELECT * FROM users LIMIT 1")
    fun getCurrentUser(): LiveData<User?>

    // Insert or replace user
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    // Update user
    @Update
    suspend fun updateUser(user: User)

    // Delete user
    @Delete
    suspend fun deleteUser(user: User)

    // Delete all users
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    // Get user by ID
    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): User?

    // Check if user exists
    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)")
    suspend fun userExists(email: String): Boolean

    // Increment user stats (for photo editing count)
    @Query("UPDATE users SET photosEdited = photosEdited + 1 WHERE userId = :userId")
    suspend fun incrementUserStats(userId: String)

    // Get all users (for debugging)
    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>
}