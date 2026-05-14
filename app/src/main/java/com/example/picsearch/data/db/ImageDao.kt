package com.example.picsearch.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ImageDao {
    data class UriFeature(
        val uri: String,
        val feature: ByteArray,
        @ColumnInfo(name = "scene_tags") val sceneTags: String? = null,
    )

    data class ClusterRow(
        val latBucket: Double,
        val lonBucket: Double,
        val centerLat: Double,
        val centerLon: Double,
        val count: Int,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ImageEntity)

    @Query("SELECT uri, feature, scene_tags FROM images")
    suspend fun listFeatures(): List<UriFeature>

    @Query(
        """
        SELECT uri, feature, scene_tags FROM images
        WHERE
          (:useTime = 0 OR
            (dateTaken IS NOT NULL AND dateTaken >= :timeStart AND dateTaken <= :timeEnd) OR
            (dateTaken IS NULL AND indexedAt >= :timeStart AND indexedAt <= :timeEnd)) AND
          (:useGeo = 0 OR
            (latitude IS NOT NULL AND longitude IS NOT NULL AND
             latitude >= :latMin AND latitude <= :latMax AND
             longitude >= :lonMin AND longitude <= :lonMax))
        """
    )
    suspend fun listFeaturesFiltered(
        useTime: Int, timeStart: Long, timeEnd: Long,
        useGeo: Int, latMin: Double, latMax: Double, lonMin: Double, lonMax: Double,
    ): List<UriFeature>

    @Query(
        """
        SELECT ROUND(latitude, 1) AS latBucket, ROUND(longitude, 1) AS lonBucket,
               AVG(latitude) AS centerLat, AVG(longitude) AS centerLon,
               COUNT(*) AS count
        FROM images
        WHERE latitude IS NOT NULL AND longitude IS NOT NULL
        GROUP BY latBucket, lonBucket
        ORDER BY count DESC
        """
    )
    suspend fun listLocationClusters(): List<ClusterRow>

    @Query("SELECT COUNT(*) FROM images WHERE latitude IS NULL OR longitude IS NULL")
    suspend fun countUnlocated(): Int

    @Query("SELECT uri FROM images")
    suspend fun listUris(): List<String>

    @Query("SELECT COUNT(*) FROM images")
    suspend fun count(): Int

    @Query("SELECT * FROM images WHERE uri IN (:uris)")
    suspend fun listEntitiesByUris(uris: List<String>): List<ImageEntity>
}
