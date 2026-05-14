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
        // 优先用 FileDescriptor 读取 EXIF，支持随机访问，能正确解析 GPS 数据
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val fd = pfd.fileDescriptor
            val exif = ExifInterface(fd)

            val latLong = exif.latLong
            val lat = latLong?.getOrNull(0)
            val lon = latLong?.getOrNull(1)
            val time = parseExifDate(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))

            // 如果 latLong 为空，尝试手动解析 GPS 标签
            val (manualLat, manualLon) = if (lat == null || lon == null) {
                parseGpsTags(exif)
            } else {
                null to null
            }

            return ExifInfo(
                dateTaken = time,
                latitude = manualLat ?: lat,
                longitude = manualLon ?: lon,
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

    /**
     * 手动解析 EXIF GPS 标签。
     * GPS 数据以 DMS（度分秒）格式存储：
     *   TAG_GPS_LATITUDE:  [度, 分, 秒] 三个 Rational
     *   TAG_GPS_LATITUDE_REF: "N" 或 "S"
     *   TAG_GPS_LONGITUDE: [度, 分, 秒] 三个 Rational
     *   TAG_GPS_LONGITUDE_REF: "E" 或 "W"
     */
    private fun parseGpsTags(exif: ExifInterface): Pair<Double?, Double?> {
        val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
        val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
        val latRational = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
        val lonRational = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)

        if (latRef == null || lonRef == null || latRational == null || lonRational == null) {
            return null to null
        }

        val lat = parseDmsToDouble(latRational)
        val lon = parseDmsToDouble(lonRational)

        if (lat == null || lon == null) return null to null

        val signedLat = if (latRef.uppercase() == "S") -lat else lat
        val signedLon = if (lonRef.uppercase() == "W") -lon else lon

        return signedLat to signedLon
    }

    /**
     * 解析 DMS 字符串为十进制度数。
     * ExifInterface.getAttribute() 返回的格式如 "120/1,30/1,45/1000"
     * 分别表示 度/分/秒
     */
    private fun parseDmsToDouble(dmsString: String): Double? {
        return runCatching {
            val parts = dmsString.split(",")
            if (parts.size != 3) return null

            val degrees = parseRational(parts[0])
            val minutes = parseRational(parts[1])
            val seconds = parseRational(parts[2])

            if (degrees == null || minutes == null || seconds == null) return null

            degrees + minutes / 60.0 + seconds / 3600.0
        }.getOrNull()
    }

    /**
     * 解析 Rational 字符串如 "120/1" 为 Double。
     */
    private fun parseRational(rationalStr: String): Double? {
        return runCatching {
            val (num, den) = rationalStr.split("/")
            num.toDouble() / den.toDouble()
        }.getOrNull()
    }
}
