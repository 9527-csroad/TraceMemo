package com.example.picsearch.data

import com.example.picsearch.ml.FeatureExtractor

class SceneClassifier(
    private val extractor: FeatureExtractor,
) {
    companion object {
        val SCENES = listOf(
            SceneLabel("🌅 风景", "自然风光，日出日落，户外"),
            SceneLabel("🏔️ 自然风光", "山川，森林，湖泊，草原，瀑布"),
            SceneLabel("🌊 海滩水景", "海滩，海洋，游泳，冲浪，海岸"),
            SceneLabel("🏙️ 城市建筑", "城市天际线，高楼大厦，街道，夜景"),
            SceneLabel("👤 人像", "人物肖像，自拍，人脸，特写"),
            SceneLabel("👥 群体聚会", "一群人，家庭，派对，聚会，朋友"),
            SceneLabel("🐾 动物宠物", "猫，狗，野生动物，宠物，鸟类"),
            SceneLabel("🍜 美食", "食物，餐厅，烹饪，美食，甜点"),
            SceneLabel("🎭 艺术展览", "艺术品，博物馆，展览，绘画，雕塑"),
            SceneLabel("🏃 运动健身", "运动，跑步，健身，瑜伽，篮球"),
        )

        const val TOP_K = 2
        const val SIMILARITY_THRESHOLD = 0.5f
    }

    data class SceneLabel(val displayName: String, val prompt: String)

    private lateinit var sceneVectors: List<FloatArray>

    suspend fun initialize() {
        sceneVectors = SCENES.map { label ->
            extractor.encodeText(label.prompt)
        }
    }

    fun classify(imageFeature: FloatArray): List<String> {
        if (!::sceneVectors.isInitialized) return emptyList()
        if (imageFeature.isEmpty()) return emptyList()

        val scores = sceneVectors.mapIndexed { index, sceneVec ->
            val sim = cosineSimilarity(imageFeature, sceneVec)
            index to sim
        }

        return scores
            .filter { it.second >= SIMILARITY_THRESHOLD }
            .sortedByDescending { it.second }
            .take(TOP_K)
            .map { SCENES[it.first].displayName }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) dot += a[i] * b[i]
        return dot
    }
}
