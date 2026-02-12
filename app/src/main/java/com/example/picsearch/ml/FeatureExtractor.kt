package com.example.picsearch.ml

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import com.example.picsearch.util.BitmapLoader

class FeatureExtractor(
    private val clip: NcnnClip,
    private val tokenizer: ChineseTokenizer,
) {
    fun encodeImage(resolver: ContentResolver, uri: Uri): FloatArray? {
        val bmp = BitmapLoader.decodeSampled(resolver, uri, 224) ?: return null
        return encodeImage(bmp)
    }

    fun encodeImage(bitmap: Bitmap): FloatArray = clip.encodeImage(bitmap)

    fun encodeText(text: String): FloatArray {
        val ids = tokenizer.encode(text, 52)
        android.util.Log.d("CLIP_DEBUG", "Token IDs: ${ids.toList().take(20).joinToString()}")
        return clip.encodeText(ids)
    }
}

