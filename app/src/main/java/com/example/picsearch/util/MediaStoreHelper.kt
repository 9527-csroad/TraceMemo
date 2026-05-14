package com.example.picsearch.util

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

object MediaStoreHelper {
    data class GpsResult(
        val latitude: Double?,
        val longitude: Double?,
        val dateTaken: Long? = null,
    )

    /**
     * Extract GPS coordinates and dateTaken from image.
     * Priority: MediaStore columns → EXIF fallback (setRequireOriginal).
     * Zero values are filtered to null.
     *
     * @param resolver ContentResolver
     * @param uri image URI from MediaStore
     * @param msLat MediaStore LATITUDE column value (0.0 = unavailable)
     * @param msLon MediaStore LONGITUDE column value (0.0 = unavailable)
     */
    fun extractGps(
        resolver: ContentResolver,
        uri: Uri,
        msLat: Double = 0.0,
        msLon: Double = 0.0,
    ): GpsResult {
        val mediaStoreLat = msLat.takeIf { it != 0.0 }
        val mediaStoreLon = msLon.takeIf { it != 0.0 }

        // If MediaStore has both GPS, no EXIF read needed
        if (mediaStoreLat != null && mediaStoreLon != null) {
            return GpsResult(
                latitude = mediaStoreLat,
                longitude = mediaStoreLon,
            )
        }

        // Fallback: read from EXIF
        val originalUri = if (Build.VERSION.SDK_INT >= 30) {
            MediaStore.setRequireOriginal(uri)
        } else {
            uri
        }
        val exif = ExifHelper.read(resolver, originalUri)
        return GpsResult(
            latitude = exif.latitude,
            longitude = exif.longitude,
            dateTaken = exif.dateTaken,
        )
    }
}
