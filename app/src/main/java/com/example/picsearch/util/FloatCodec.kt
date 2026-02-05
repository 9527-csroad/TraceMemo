package com.example.picsearch.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

object FloatCodec {
    fun toBytes(v: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.asFloatBuffer().put(v)
        return bb.array()
    }

    fun fromBytes(b: ByteArray): FloatArray {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(b.size / 4)
        bb.asFloatBuffer().get(out)
        return out
    }
}

