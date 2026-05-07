package com.example.picsearch.worker

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.picsearch.data.SceneClassifier
import com.example.picsearch.data.db.AppDatabase
import com.example.picsearch.data.db.ImageEntity
import com.example.picsearch.data.repository.ImageRepository
import com.example.picsearch.ml.ChineseTokenizer
import com.example.picsearch.ml.FeatureExtractor
import com.example.picsearch.ml.NcnnClip
import com.example.picsearch.util.ExifHelper
import com.example.picsearch.util.FloatCodec

class IndexWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "picsearch.db").build()
        val repo = ImageRepository(db.imageDao())

        val clip = NcnnClip(applicationContext)
        if (!clip.init(false)) return Result.failure()
        val tokenizer = ChineseTokenizer(applicationContext)
        val extractor = FeatureExtractor(clip, tokenizer)
        val classifier = SceneClassifier(extractor)
        classifier.initialize()

        val resolver = applicationContext.contentResolver
        val existing = repo.listUris().toHashSet()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_TAKEN,
        )

        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cur ->
            val idIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val wIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dateIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

            while (cur.moveToNext()) {
                if (isStopped) return Result.retry()

                val id = cur.getLong(idIdx)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val uriStr = uri.toString()
                if (existing.contains(uriStr)) continue

                val feat = extractor.encodeImage(resolver, uri) ?: continue
                var mag = 0f; for (v in feat) mag += v * v
                if (mag < 1e-6f) continue
                val exif = ExifHelper.read(resolver, uri)

                val sceneTags = classifier.classify(feat)
                val sceneTagsStr = if (sceneTags.isNotEmpty()) sceneTags.joinToString(",") else null

                val entity = ImageEntity(
                    uri = uriStr,
                    feature = FloatCodec.toBytes(feat),
                    dateTaken = exif.dateTaken ?: cur.getLong(dateIdx).takeIf { it > 0 },
                    latitude = exif.latitude,
                    longitude = exif.longitude,
                    displayName = cur.getString(nameIdx),
                    width = cur.getInt(wIdx),
                    height = cur.getInt(hIdx),
                    indexedAt = System.currentTimeMillis(),
                    sceneTags = sceneTagsStr,
                )
                repo.upsert(entity)
                existing.add(uriStr)
            }
        }

        return Result.success()
    }
}

