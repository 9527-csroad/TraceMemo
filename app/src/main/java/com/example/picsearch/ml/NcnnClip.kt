package com.example.picsearch.ml

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap

class NcnnClip(context: Context) {
    private val assetManager: AssetManager = context.assets

    init {
        System.loadLibrary("picsearch_jni")
    }

    fun init(useVulkan: Boolean): Boolean = initNative(assetManager, useVulkan)

    fun encodeImage(bitmap: Bitmap): FloatArray = encodeImageNative(bitmap)

    fun encodeText(tokenIds: IntArray): FloatArray = encodeTextNative(tokenIds)

    private external fun initNative(assetManager: AssetManager, useVulkan: Boolean): Boolean
    private external fun encodeImageNative(bitmap: Bitmap): FloatArray
    private external fun encodeTextNative(tokenIds: IntArray): FloatArray
}

