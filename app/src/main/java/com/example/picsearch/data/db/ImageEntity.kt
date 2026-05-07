package com.example.picsearch.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val uri: String,
    val feature: ByteArray,
    val dateTaken: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val displayName: String?,
    val width: Int,
    val height: Int,
    val indexedAt: Long,
    @ColumnInfo(name = "scene_tags") val sceneTags: String? = null,
)
