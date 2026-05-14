package com.example.picsearch.util

import android.content.Context
import com.example.picsearch.data.LocationBounds
import org.json.JSONArray

/**
 * 从中文文本中匹配地点名称。
 * 支持中国城市（地级市）和全球国家的子串匹配。
 */
class LocationMatcher(context: Context) {

    data class LocationMatch(
        val name: String,
        val bounds: LocationBounds,
    )

    private val cityBounds = mutableListOf<Pair<String, LocationBounds>>()
    private val countryBounds = mutableListOf<Pair<String, LocationBounds>>()

    init {
        loadCities(context)
        loadCountries(context)
        loadAliases(context)
    }

    /**
     * 在文本中匹配地点名称。返回第一个匹配项。
     * 自动处理"市"后缀："北京"和"北京市"都能匹配。
     */
    fun match(text: String): LocationMatch? {
        // 先尝试城市匹配（优先，因为更精确）
        for ((name, bounds) in cityBounds) {
            if (text.contains(name)) {
                return LocationMatch(name, bounds)
            }
        }

        // 再尝试国家匹配
        for ((name, bounds) in countryBounds) {
            if (text.contains(name)) {
                return LocationMatch(name, bounds)
            }
        }

        return null
    }

    private fun loadCities(context: Context) {
        loadCityFile(context, "geocoding/china_cities.json")
        try {
            loadCityFile(context, "geocoding/china_cities_full.json")
        } catch (_: Exception) {
            // Full file not yet available, skip
        }
    }

    private fun loadCityFile(context: Context, path: String) {
        context.assets.open(path).use { stream ->
            val text = stream.bufferedReader().readText()
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.getString("name")
                val bounds = LocationBounds(
                    latMin = obj.getDouble("minLat"),
                    latMax = obj.getDouble("maxLat"),
                    lonMin = obj.getDouble("minLon"),
                    lonMax = obj.getDouble("maxLon"),
                )
                cityBounds.add(name to bounds)
                if (name.endsWith("市")) {
                    cityBounds.add(name.removeSuffix("市") to bounds)
                }
            }
        }
    }

    private fun loadCountries(context: Context) {
        context.assets.open("geocoding/country_boundaries.json").use { stream ->
            val text = stream.bufferedReader().readText()
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.getString("name")
                val nameEn = obj.optString("nameEn", "")
                val bounds = LocationBounds(
                    latMin = obj.getDouble("minLat"),
                    latMax = obj.getDouble("maxLat"),
                    lonMin = obj.getDouble("minLon"),
                    lonMax = obj.getDouble("maxLon"),
                )
                countryBounds.add(name to bounds)
                if (nameEn.isNotBlank()) {
                    countryBounds.add(nameEn to bounds)
                }
            }
        }

        // Also load aliases
        for ((alias, canonical) in aliases) {
            val canonicalBounds = countryBounds.find { it.first == canonical }
            if (canonicalBounds != null) {
                countryBounds.add(alias to canonicalBounds.second)
            }
        }
    }

    private fun loadAliases(context: Context) {
        try {
            context.assets.open("geocoding/country_aliases.json").use { stream ->
                val text = stream.bufferedReader().readText()
                val obj = org.json.JSONObject(text)
                obj.keys().forEach { alias ->
                    aliases[alias] = obj.getString(alias)
                }
            }
        } catch (_: Exception) {
            // Aliases file not available yet
        }
    }

    private val aliases = mutableMapOf<String, String>()
}