package com.example.picsearch.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ImageDao {
    data class UriFeature(
        val uri: String,
        val feature: ByteArray,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ImageEntity)

    @Query("SELECT uri, feature FROM images")
    suspend fun listFeatures(): List<UriFeature>

    @Query("SELECT uri FROM images")
    suspend fun listUris(): List<String>

    @Query("SELECT COUNT(*) FROM images")
    suspend fun count(): Int
}

