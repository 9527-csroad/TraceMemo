package com.example.picsearch.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * 只做"粗解码 + EXIF 旋转"，不做 resize/crop/normalize。
 *
 * 之前这里用 [Bitmap.createScaledBitmap] 做过 BILINEAR 短边缩放和中心裁剪，
 * 但 cn_clip 官方 preprocess 用的是 PIL BICUBIC；两者不一致会让 CLIP 图像向量
 * 与 PC 基准的 CosSim 掉到 ~0.936，严重影响检索质量。
 *
 * 现在的分工：
 *   - Kotlin 侧 (这里)：inSampleSize 粗解码 + EXIF 旋转；短边保留 ≥ target*2 的分辨率，
 *                       为 C++ 侧的高精度 bicubic 采样留出足够信息量。
 *   - C++ 侧 (clip_jni.cpp)：bicubic 短边 resize + center crop + normalize，
 *                           完全对齐 PIL BICUBIC 语义。
 */
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

        // inSampleSize 仅做粗采样以控内存；阈值 target*2 保证后续 bicubic 输入信息量充足
        val sample = calcInSampleSize(bounds.outWidth, bounds.outHeight, target * 2, target * 2)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rotated = applyExifOrientation(decoded, orientation)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun calcInSampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (h > reqH || w > reqW) {
            val halfH = h / 2
            val halfW = w / 2
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
}

