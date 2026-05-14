package com.example.picsearch.util

import com.example.picsearch.data.LocationBounds
import com.example.picsearch.data.TimeRange

data class ExtractedFilter(
    val timeRange: TimeRange? = null,
    val locationBounds: LocationBounds? = null,
    val locationName: String? = null,
    val remainingText: String = "",
)