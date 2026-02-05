package com.example.picsearch.util

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object ExifHelper {
    data class ExifInfo(
        val dateTaken: Long?,
        val latitude: Double?,
        val longitude: Double?,
    )

    fun read(resolver: ContentResolver, uri: Uri): ExifInfo {
        resolver.openInputStream(uri)?.use { ins ->
            val exif = ExifInterface(ins)
            val latLong = exif.latLong
            val lat = latLong?.getOrNull(0)
            val lon = latLong?.getOrNull(1)
            val time = parseExifDate(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            return ExifInfo(
                dateTaken = time,
                latitude = lat,
                longitude = lon,
            )
        }
        return ExifInfo(null, null, null)
    }

    private fun parseExifDate(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching {
            val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getDefault()
            fmt.parse(s)?.time
        }.getOrNull()
    }
}

