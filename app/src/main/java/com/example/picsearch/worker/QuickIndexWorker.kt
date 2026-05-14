package com.example.picsearch.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.MediaStore
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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

class QuickIndexWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "picsearch_quick_index"
        const val NOTIFICATION_ID = 1
        const val QUICK_LIMIT = 100
        const val WORK_NAME_QUICK = "quick_index"
        const val WORK_NAME_FULL = "full_index"
        const val KEY_LAST_INDEX_TIMESTAMP = "last_index_timestamp"
    }

    @Suppress("DEPRECATION")
    override suspend fun doWork(): Result {
        createNotificationChannel()

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
        val lastIndexTimestamp = prefs.getLong(KEY_LAST_INDEX_TIMESTAMP, 0L)

        // Step 1: Collect all current MediaStore URIs (for deletion detection)
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

        // Step 2: Delete images no longer in MediaStore
        val dbUris = repo.listUris().toHashSet()
        val toDelete = dbUris - mediaUris
        if (toDelete.isNotEmpty()) {
            repo.deleteByUris(toDelete.toList())
        }

        // Step 3: Incremental scan — only images added after last index run
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
                if (indexed >= QUICK_LIMIT) break

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

                setForeground(getForegroundInfo(indexed, QUICK_LIMIT))
            }
        }

        // Save timestamp for next incremental run
        prefs.edit().putLong(KEY_LAST_INDEX_TIMESTAMP, System.currentTimeMillis() / 1000).apply()

        if (indexed > 0) {
            val fullRequest = OneTimeWorkRequestBuilder<IndexWorker>().build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                WORK_NAME_FULL,
                androidx.work.ExistingWorkPolicy.KEEP,
                fullRequest,
            )
        }

        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quick Indexing",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun getForegroundInfo(indexed: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("快速索引中")
            .setContentText("已索引 $indexed / $total 张照片")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setProgress(total, indexed, false)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
