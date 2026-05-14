package com.example.picsearch.util

import android.content.Context
import org.json.JSONArray
import java.io.InputStream

/**
 * 离线反向地理编码：经纬度 → 可读地址文字。
 * 中国境内精确到市级，境外精确到国家级。
 */
object ReverseGeocoder {

    private data class Boundary(
        val name: String,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
    )

    @Volatile
    private var initialized = false
    private lateinit var countryBoundaries: List<Boundary>
    private lateinit var chinaCities: List<Boundary>

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            countryBoundaries = loadBoundaries(
                context.assets.open("geocoding/country_boundaries.json"),
                "name",
            )
            chinaCities = loadBoundaries(
                context.assets.open("geocoding/china_cities.json"),
                "name",
            )
            initialized = true
        }
    }

    /**
     * 将经纬度转换为可读地址。
     * @return "北京市" / "日本" / "美国" / null（海洋/未覆盖区域）
     */
    fun lookup(latitude: Double, longitude: Double): String? {
        if (!initialized) return null

        // 先匹配中国城市
        for (city in chinaCities) {
            if (inBounds(latitude, longitude, city)) return city.name
        }

        // 匹配国家
        for (country in countryBoundaries) {
            if (inBounds(latitude, longitude, country)) return country.name
        }

        return null
    }

    private fun inBounds(lat: Double, lon: Double, b: Boundary): Boolean {
        return lat >= b.minLat && lat <= b.maxLat && lon >= b.minLon && lon <= b.maxLon
    }

    private fun loadBoundaries(stream: InputStream, nameKey: String): List<Boundary> {
        stream.use {
            val text = it.bufferedReader().use { reader -> reader.readText() }
            val array = JSONArray(text)
            return (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                val name = obj.optString(nameKey)
                if (name.isBlank()) return@mapNotNull null
                Boundary(
                    name = name,
                    minLat = obj.getDouble("minLat"),
                    maxLat = obj.getDouble("maxLat"),
                    minLon = obj.getDouble("minLon"),
                    maxLon = obj.getDouble("maxLon"),
                )
            }
        }
    }
}
