package com.example.picsearch.data

import kotlin.math.abs

data class TimeRange(val startMillis: Long, val endMillis: Long)

data class LocationBounds(
    val latMin: Double,
    val latMax: Double,
    val lonMin: Double,
    val lonMax: Double,
) {
    companion object {
        fun fromBucket(latBucket: Double, lonBucket: Double, halfSize: Double = 0.05): LocationBounds =
            LocationBounds(
                latMin = latBucket - halfSize,
                latMax = latBucket + halfSize,
                lonMin = lonBucket - halfSize,
                lonMax = lonBucket + halfSize,
            )
    }
}

data class SearchFilter(
    val timeRange: TimeRange? = null,
    val locationBounds: LocationBounds? = null,
) {
    val isEmpty: Boolean get() = timeRange == null && locationBounds == null
    val selectedCount: Int get() = (if (timeRange != null) 1 else 0) + (if (locationBounds != null) 1 else 0)
}

data class LocationCluster(
    val latBucket: Double,
    val lonBucket: Double,
    val centerLat: Double,
    val centerLon: Double,
    val count: Int,
) {
    val displayName: String
        get() {
            val latDir = if (centerLat >= 0) "N" else "S"
            val lonDir = if (centerLon >= 0) "E" else "W"
            return "约 %.1f°%s, %.1f°%s · %d 张".format(
                abs(centerLat), latDir, abs(centerLon), lonDir, count,
            )
        }
}
