package com.luminex.photoenhancer.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import com.luminex.photoenhancer.models.Photo

@Dao
interface PhotoDao {

    // Insert photo
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: Photo)

    // Get all photos (LiveData for automatic UI updates)
    @Query("SELECT * FROM photos ORDER BY timestamp DESC")
    fun getAllPhotos(): LiveData<List<Photo>>

    // Get photos by user ID
    @Query("SELECT * FROM photos WHERE userId = :userId ORDER BY timestamp DESC")
    fun getPhotosByUser(userId: String): LiveData<List<Photo>>

    // Get photo count
    @Query("SELECT COUNT(*) FROM photos")
    suspend fun getPhotoCount(): Int

    // Get total count (for DashboardFragment)
    @Query("SELECT COUNT(*) FROM photos")
    suspend fun getTotalCount(): Int


    // Delete photo
    @Delete
    suspend fun deletePhoto(photo: Photo)

    // Update photo
    @Update
    suspend fun updatePhoto(photo: Photo)

    // Delete all photos
    @Query("DELETE FROM photos")
    suspend fun deleteAllPhotos()

    // Get recent photos (last 10)
    @Query("SELECT * FROM photos ORDER BY timestamp DESC LIMIT 10")
    fun getRecentPhotos(): LiveData<List<Photo>>
}