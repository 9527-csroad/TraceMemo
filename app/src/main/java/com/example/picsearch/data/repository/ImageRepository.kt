package com.example.picsearch.data.repository

import com.example.picsearch.data.db.ImageDao
import com.example.picsearch.data.db.ImageEntity

class ImageRepository(private val dao: ImageDao) {
    suspend fun upsert(entity: ImageEntity) = dao.upsert(entity)

    suspend fun listFeatures(): List<ImageDao.UriFeature> = dao.listFeatures()

    suspend fun listUris(): List<String> = dao.listUris()

    suspend fun count(): Int = dao.count()
}

