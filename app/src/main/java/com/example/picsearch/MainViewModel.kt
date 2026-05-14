package com.example.picsearch

import android.content.Context
import android.content.SharedPreferences
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.picsearch.data.SceneClassifier
import com.example.picsearch.data.LocationCluster
import com.example.picsearch.data.SearchFilter
import com.example.picsearch.data.db.AppDatabase
import com.example.picsearch.data.db.SceneTagCount
import com.example.picsearch.data.repository.ImageRepository
import com.example.picsearch.ml.ChineseTokenizer
import com.example.picsearch.ml.FeatureExtractor
import com.example.picsearch.ml.NcnnClip
import com.example.picsearch.util.ReverseGeocoder
import com.example.picsearch.util.FloatCodec
import com.example.picsearch.worker.KEY_INDEXED_COUNT
import com.example.picsearch.worker.KEY_TOTAL_COUNT
import com.example.picsearch.worker.PREFS_NAME
import com.example.picsearch.worker.QuickIndexWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
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

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _workProgress = MutableStateFlow(0)
    val workProgress: StateFlow<Int> = _workProgress

    private val _fullIndexProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val fullIndexProgress: StateFlow<Pair<Int, Int>?> = _fullIndexProgress

    private val _sceneLabels = MutableStateFlow<List<String>>(emptyList())
    val sceneLabels: StateFlow<List<String>> = _sceneLabels

    private val _sceneTagCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val sceneTagCounts: StateFlow<Map<String, Int>> = _sceneTagCounts

    private lateinit var sceneClassifier: SceneClassifier

    data class ImageDetailData(
        val uri: String,
        val displayName: String?,
        val width: Int,
        val height: Int,
        val dateTaken: Long?,
        val latitude: Double?,
        val longitude: Double?,
        val sceneTags: List<String>,
    )

    private val _imageDetails = MutableStateFlow<Map<String, ImageDetailData>>(emptyMap())

    init {
        ReverseGeocoder.init(app)
        viewModelScope.launch(Dispatchers.IO) {
            // 临时排查：关闭 Vulkan 走纯 CPU 推理，验证文本/图像向量与 PC CPU 路径是否对齐。
            // 验证完若无精度差异，应改回 clip.init(true) 以享受 GPU 加速。
            val ok = clip.init(false)
            if (!ok) {
                _ready.value = false
                return@launch
            }
            sceneClassifier = SceneClassifier(extractor)
            sceneClassifier.initialize()
            _sceneLabels.value = SceneClassifier.SCENES.map { it.displayName }
            _ready.value = true
            _indexedCount.value = repo.count()
            loadClusters()

            // 每 2 秒轮询数据库 count + SharedPreferences 全量索引进度
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var lastSceneCountVersion = -1
            while (isActive) {
                delay(2000)
                val newCount = repo.count()
                if (newCount != _indexedCount.value) {
                    _indexedCount.value = newCount
                    loadClusters()
                    // Reload scene tag counts when DB changes
                    val sceneCounts = mutableMapOf<String, Int>()
                    repo.countBySceneTags().forEach { row ->
                        row.sceneTags.split(",").filter { it.isNotEmpty() }.forEach { tag ->
                            sceneCounts[tag] = sceneCounts.getOrDefault(tag.trim(), 0) + row.cnt
                        }
                    }
                    _sceneTagCounts.value = sceneCounts
                    lastSceneCountVersion = newCount
                }

                // Read full index progress from SharedPreferences
                val indexed = prefs.getInt(KEY_INDEXED_COUNT, -1)
                val total = prefs.getInt(KEY_TOTAL_COUNT, -1)
                if (indexed >= 0 && total > 0) {
                    _fullIndexProgress.value = indexed to total
                } else {
                    _fullIndexProgress.value = null
                }
            }
        }
    }

    fun startIndex() {
        val ctx = getApplication<Application>()
        val workManager = WorkManager.getInstance(ctx)
        val quickRequest = OneTimeWorkRequestBuilder<QuickIndexWorker>().build()
        workManager.enqueueUniqueWork("quick_index", ExistingWorkPolicy.REPLACE, quickRequest)
    }

    fun startFullRebuild() {
        // Clear the last index timestamp to force full scan
        val ctx = getApplication<Application>()
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(QuickIndexWorker.KEY_LAST_INDEX_TIMESTAMP, 0L)
            .putInt(KEY_INDEXED_COUNT, 0)
            .putInt(KEY_TOTAL_COUNT, 0)
            .apply()
        startIndex()
    }

    fun refreshCount() {
        viewModelScope.launch(Dispatchers.IO) {
            _indexedCount.value = repo.count()
            loadClusters()
        }
    }

    private suspend fun loadClusters() {
        _clusters.value = repo.listLocationClusters().map { row ->
            val name = ReverseGeocoder.lookup(row.centerLat, row.centerLon)
            LocationCluster(
                latBucket = row.latBucket,
                lonBucket = row.lonBucket,
                centerLat = row.centerLat,
                centerLon = row.centerLon,
                count = row.count,
                readableName = name,
            )
        }
        _unlocatedCount.value = repo.countUnlocated()
    }

    fun search(text: String, filter: SearchFilter = SearchFilter(), topK: Int = 10) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            try {
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
                val filteredRows = if (filter.sceneTags.isEmpty()) rows
                    else rows.filter { row ->
                        val tags = row.sceneTags
                            ?.split(",")
                            ?.map { it.trim() }
                            ?.filter { it.isNotEmpty() }
                            ?: emptyList()
                        filter.sceneTags.any { tag -> tags.contains(tag) }
                    }
                val scored = ArrayList<ImageScore>(filteredRows.size)
                for (r in filteredRows) {
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

                // Cache image details for detail sheet — batch lookup full entity metadata
                val topUris = scored.take(k).map { it.uri }
                val entities = repo.listEntitiesByUris(topUris).associateBy { it.uri }
                val details = scored.take(k).associate { score ->
                    val entity = entities[score.uri]
                    score.uri to ImageDetailData(
                        uri = score.uri,
                        displayName = entity?.displayName,
                        width = entity?.width ?: 0,
                        height = entity?.height ?: 0,
                        dateTaken = entity?.dateTaken,
                        latitude = entity?.latitude,
                        longitude = entity?.longitude,
                        sceneTags = score.sceneTags,
                    )
                }
                _imageDetails.value = _imageDetails.value + details
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun getImageDetail(uri: String): com.example.picsearch.ui.component.ImageDetail {
        val data = _imageDetails.value[uri]
        return com.example.picsearch.ui.component.ImageDetail(
            uri = data?.uri ?: uri,
            displayName = data?.displayName,
            width = data?.width ?: 0,
            height = data?.height ?: 0,
            dateTaken = data?.dateTaken,
            latitude = data?.latitude,
            longitude = data?.longitude,
            sceneTags = data?.sceneTags ?: emptyList(),
        )
    }
}
