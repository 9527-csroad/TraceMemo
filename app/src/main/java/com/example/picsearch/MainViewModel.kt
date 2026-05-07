package com.example.picsearch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.picsearch.data.LocationCluster
import com.example.picsearch.data.SearchFilter
import com.example.picsearch.data.db.AppDatabase
import com.example.picsearch.data.repository.ImageRepository
import com.example.picsearch.ml.ChineseTokenizer
import com.example.picsearch.ml.FeatureExtractor
import com.example.picsearch.ml.NcnnClip
import com.example.picsearch.util.FloatCodec
import com.example.picsearch.worker.IndexWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    data class ImageScore(val uri: String, val score: Float, val sceneTags: List<String> = emptyList())

    private val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "picsearch.db")
        .fallbackToDestructiveMigration()
        .build()
    private val repo = ImageRepository(db.imageDao())

    private val clip = NcnnClip(app)
    private val tokenizer = ChineseTokenizer(app)
    private val extractor = FeatureExtractor(clip, tokenizer)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val _indexedCount = MutableStateFlow(0)
    val indexedCount: StateFlow<Int> = _indexedCount

    private val _results = MutableStateFlow<List<ImageScore>>(emptyList())
    val results: StateFlow<List<ImageScore>> = _results

    private val _clusters = MutableStateFlow<List<LocationCluster>>(emptyList())
    val clusters: StateFlow<List<LocationCluster>> = _clusters

    private val _unlocatedCount = MutableStateFlow(0)
    val unlocatedCount: StateFlow<Int> = _unlocatedCount

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // 临时排查：关闭 Vulkan 走纯 CPU 推理，验证文本/图像向量与 PC CPU 路径是否对齐。
            // 验证完若无精度差异，应改回 clip.init(true) 以享受 GPU 加速。
            val ok = clip.init(false)
            _ready.value = ok
            _indexedCount.value = repo.count()
            loadClusters()
        }
    }

    fun startIndex() {
        val ctx = getApplication<Application>()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "index",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<IndexWorker>().build(),
        )
    }

    fun refreshCount() {
        viewModelScope.launch(Dispatchers.IO) {
            _indexedCount.value = repo.count()
            loadClusters()
        }
    }

    private suspend fun loadClusters() {
        _clusters.value = repo.listLocationClusters().map { row ->
            LocationCluster(
                latBucket = row.latBucket,
                lonBucket = row.lonBucket,
                centerLat = row.centerLat,
                centerLon = row.centerLon,
                count = row.count,
            )
        }
        _unlocatedCount.value = repo.countUnlocated()
    }

    fun search(text: String, filter: SearchFilter = SearchFilter(), topK: Int = 10) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_ready.value) return@launch
            val q = text.trim()
            if (q.isEmpty()) {
                _results.value = emptyList()
                return@launch
            }
            val qv = extractor.encodeText(q)
            if (qv.isEmpty()) {
                _results.value = emptyList()
                return@launch
            }
            val rows = repo.listFeaturesFiltered(filter)
            val scored = ArrayList<ImageScore>(rows.size)
            for (r in rows) {
                val fv = FloatCodec.fromBytes(r.feature)
                if (fv.isEmpty()) continue
                var s = 0f
                val n = minOf(qv.size, fv.size)
                for (i in 0 until n) s += qv[i] * fv[i]
                if (!s.isFinite()) continue
                val tags = r.sceneTags
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()
                scored.add(ImageScore(r.uri, s, tags))
            }
            scored.sortByDescending { it.score }

            val k = topK.coerceAtLeast(1)
            _results.value = scored.take(k)
        }
    }
}
