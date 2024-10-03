package com.example.diaryapp2.data.database.entity

import Constants.IMAGE_TO_DELETE_TABLE
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = IMAGE_TO_DELETE_TABLE)
data class ImageToDelete(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val remoteImagePath: String,
)
