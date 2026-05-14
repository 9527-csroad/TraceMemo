package com.example.picsearch.data.repository

import com.example.picsearch.data.SearchFilter
import com.example.picsearch.data.db.ImageDao
import com.example.picsearch.data.db.ImageEntity
import com.example.picsearch.data.db.SceneTagCount

class ImageRepository(private val dao: ImageDao) {
    suspend fun upsert(entity: ImageEntity) = dao.upsert(entity)

    suspend fun listFeatures(): List<ImageDao.UriFeature> = dao.listFeatures()

    suspend fun listFeaturesFiltered(filter: SearchFilter): List<ImageDao.UriFeature> {
        if (filter.isEmpty) return dao.listFeatures()
        val t = filter.timeRange
        val g = filter.locationBounds
        return dao.listFeaturesFiltered(
            useTime = if (t != null) 1 else 0,
            timeStart = t?.startMillis ?: 0L,
            timeEnd = t?.endMillis ?: Long.MAX_VALUE,
            useGeo = if (g != null) 1 else 0,
            latMin = g?.latMin ?: 0.0,
            latMax = g?.latMax ?: 0.0,
            lonMin = g?.lonMin ?: 0.0,
            lonMax = g?.lonMax ?: 0.0,
        )
    }

    suspend fun listLocationClusters(): List<ImageDao.ClusterRow> = dao.listLocationClusters()

    suspend fun countUnlocated(): Int = dao.countUnlocated()

    suspend fun listUris(): List<String> = dao.listUris()

    suspend fun count(): Int = dao.count()

    suspend fun listEntitiesByUris(uris: List<String>): List<ImageEntity> = dao.listEntitiesByUris(uris)

    suspend fun deleteByUris(uris: List<String>) = dao.deleteByUris(uris)

    suspend fun countBySceneTags(): List<SceneTagCount> = dao.countBySceneTags()
}
