package com.luminex.photoenhancer.database // FIXED

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "enhanced_photos")
data class EnhancedPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val path: String,
    val timestamp: Long
)