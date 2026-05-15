package com.example.picsearch.util

import org.junit.Test
import org.junit.Assert.*

class FloatCodecTest {

    private fun assertFloatArrayEquals(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals(expected[i], actual[i], 0.0001f)
        }
    }

    @Test
    fun `roundTrip empty array`() {
        val bytes = FloatCodec.toBytes(FloatArray(0))
        assertEquals(0, bytes.size)
        assertFloatArrayEquals(FloatArray(0), FloatCodec.fromBytes(bytes))
    }

    @Test
    fun `roundTrip single element`() {
        val input = floatArrayOf(0.5f)
        val bytes = FloatCodec.toBytes(input)
        assertEquals(4, bytes.size)
        assertFloatArrayEquals(input, FloatCodec.fromBytes(bytes))
    }

    @Test
    fun `roundTrip multiple elements`() {
        val input = floatArrayOf(1.0f, -2.5f, 0.0f, 3.14159f, -0.001f)
        val bytes = FloatCodec.toBytes(input)
        assertEquals(input.size * 4, bytes.size)
        assertFloatArrayEquals(input, FloatCodec.fromBytes(bytes))
    }

    @Test
    fun `roundTrip CLIP dimension 768`() {
        val input = FloatArray(768) { it / 768f }
        val bytes = FloatCodec.toBytes(input)
        assertEquals(768 * 4, bytes.size)
        assertFloatArrayEquals(input, FloatCodec.fromBytes(bytes))
    }

    @Test
    fun `littleEndian byte order`() {
        val input = floatArrayOf(1.0f)
        val bytes = FloatCodec.toBytes(input)
        // 1.0f in little-endian: 00 00 80 3F
        assertArrayEquals(byteArrayOf(0, 0, 0x80.toByte(), 0x3F.toByte()), bytes)
    }

    @Test
    fun `normalized cosine similarity vectors`() {
        val a = floatArrayOf(0.6f, 0.8f, 0f, 0f)
        val b = floatArrayOf(0.8f, 0.6f, 0f, 0f)
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        assertEquals(0.96f, dot, 0.001f)

        val bytesA = FloatCodec.toBytes(a)
        val decodedA = FloatCodec.fromBytes(bytesA)
        var dotDecoded = 0f
        for (i in decodedA.indices) dotDecoded += decodedA[i] * b[i]
        assertEquals(0.96f, dotDecoded, 0.001f)
    }
}
