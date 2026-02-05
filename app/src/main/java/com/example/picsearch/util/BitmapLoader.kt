package com.example.picsearch.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object BitmapLoader {
    fun decodeSampled(resolver: ContentResolver, uri: Uri, target: Int = 224): Bitmap? {
        val orientation = resolver.openInputStream(uri)?.use { ins ->
            runCatching {
                ExifInterface(ins).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
            }.getOrNull() ?: ExifInterface.ORIENTATION_UNDEFINED
        } ?: ExifInterface.ORIENTATION_UNDEFINED

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = calcInSampleSize(bounds.outWidth, bounds.outHeight, target * 2, target * 2)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rotated = applyExifOrientation(decoded, orientation)
        if (rotated !== decoded) decoded.recycle()

        val resized = resizeShorterTo(rotated, target)
        if (resized !== rotated) rotated.recycle()

        val cropped = centerCrop(resized, target)
        if (cropped !== resized) resized.recycle()
        return cropped
    }

    private fun calcInSampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (h > reqH || w > reqW) {
            var halfH = h / 2
            var halfW = w / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun applyExifOrientation(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postScale(-1f, 1f)
                m.postRotate(270f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postScale(-1f, 1f)
                m.postRotate(90f)
            }
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun resizeShorterTo(src: Bitmap, target: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val shorter = minOf(w, h)
        if (shorter == target) return src
        val scale = target.toFloat() / shorter.toFloat()
        val nw = (w * scale).toInt().coerceAtLeast(target)
        val nh = (h * scale).toInt().coerceAtLeast(target)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private fun centerCrop(src: Bitmap, target: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w == target && h == target) return src
        if (w < target || h < target) return Bitmap.createScaledBitmap(src, target, target, true)
        val x = (w - target) / 2
        val y = (h - target) / 2
        return Bitmap.createBitmap(src, x, y, target, target)
    }
}

