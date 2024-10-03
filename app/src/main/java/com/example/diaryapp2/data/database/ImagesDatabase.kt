package com.example.diaryapp2.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.diaryapp2.data.database.entity.ImageToDelete
import com.example.diaryapp2.data.database.entity.ImageToUpload

@Database(
    entities = [ImageToUpload::class, ImageToDelete::class],
    version = 2,
    exportSchema = false
)
abstract class ImagesDatabase: RoomDatabase() {
    abstract fun imageToUploadDao(): ImageToUploadDAO
    abstract fun imageToDeleteDao(): ImageToDeleteDAO
}