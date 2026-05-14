package com.example.picsearch.worker

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
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
import com.example.picsearch.util.MediaStoreHelper
import com.example.picsearch.util.FloatCodec

const val PREFS_NAME = "index_progress"
const val KEY_INDEXED_COUNT = "indexed_count"
const val KEY_TOTAL_COUNT = "total_count"

class IndexWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @Suppress("DEPRECATION")
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
        val prefs: SharedPreferences = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastIndexTimestamp = prefs.getLong(QuickIndexWorker.KEY_LAST_INDEX_TIMESTAMP, 0L)

        // Collect all current MediaStore URIs (for deletion detection)
        val mediaUris = mutableSetOf<String>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            null, null, null,
        )?.use { cur ->
            val idIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cur.moveToNext()) {
                val id = cur.getLong(idIdx)
                mediaUris.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString())
            }
        }

        // Delete images no longer in MediaStore
        val dbUris = repo.listUris().toHashSet()
        val toDelete = dbUris - mediaUris
        if (toDelete.isNotEmpty()) {
            repo.deleteByUris(toDelete.toList())
        }

        // Incremental scan
        val existing = repo.listUris().toHashSet()
        val selection = if (lastIndexTimestamp > 0) {
            "${MediaStore.Images.Media.DATE_ADDED} > ?"
        } else {
            null
        }
        val selectionArgs = if (lastIndexTimestamp > 0) {
            arrayOf(lastIndexTimestamp.toString())
        } else {
            null
        }

        // Count total new images to process
        val totalCursor = resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection, selectionArgs, null,
        )
        val totalNewCount = totalCursor?.count ?: 0
        totalCursor?.close()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.LATITUDE,
            MediaStore.Images.Media.LONGITUDE,
        )

        var indexed = 0
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cur ->
            val idIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val wIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dateIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val latIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.LATITUDE)
            val lonIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.LONGITUDE)

            while (cur.moveToNext()) {
                if (isStopped) return Result.retry()

                val id = cur.getLong(idIdx)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val uriStr = uri.toString()
                if (existing.contains(uriStr)) continue

                val feat = extractor.encodeImage(resolver, uri) ?: continue
                var mag = 0f; for (v in feat) mag += v * v
                if (mag < 1e-6f) continue

                val gps = MediaStoreHelper.extractGps(
                    resolver, uri,
                    cur.getDouble(latIdx),
                    cur.getDouble(lonIdx),
                )
                var dateTaken: Long? = gps.dateTaken

                if (dateTaken == null) {
                    dateTaken = cur.getLong(dateIdx).takeIf { it > 0 }
                }

                val sceneTags = classifier.classify(feat)
                val sceneTagsStr = if (sceneTags.isNotEmpty()) sceneTags.joinToString(",") else null

                val entity = ImageEntity(
                    uri = uriStr,
                    feature = FloatCodec.toBytes(feat),
                    dateTaken = dateTaken,
                    latitude = gps.latitude,
                    longitude = gps.longitude,
                    displayName = cur.getString(nameIdx),
                    width = cur.getInt(wIdx),
                    height = cur.getInt(hIdx),
                    indexedAt = System.currentTimeMillis(),
                    sceneTags = sceneTagsStr,
                )
                repo.upsert(entity)
                existing.add(uriStr)
                indexed++

                // Update progress in SharedPreferences every 10 images
                if (indexed % 10 == 0) {
                    prefs.edit()
                        .putInt(KEY_INDEXED_COUNT, repo.count())
                        .putInt(KEY_TOTAL_COUNT, totalNewCount)
                        .apply()
                }
            }
        }

        // Final progress update
        prefs.edit()
            .putInt(KEY_INDEXED_COUNT, repo.count())
            .putInt(KEY_TOTAL_COUNT, totalNewCount)
            .apply()

        return Result.success()
    }
}

