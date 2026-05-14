package com.example.picsearch.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class LocationMatcherTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `match 北京 returns Beijing bounds`() {
        val matcher = LocationMatcher(context)
        val result = matcher.match("北京")
        assertNotNull(result)
        assertEquals("北京", result!!.name)
        // Beijing bounds: lat 39.44-41.06, lon 115.25-117.50
        assertTrue(result.bounds.latMin > 39.0)
        assertTrue(result.bounds.latMax < 42.0)
    }

    @Test
    fun `match 北京市 also matches Beijing`() {
        val matcher = LocationMatcher(context)
        val result = matcher.match("北京市")
        assertNotNull(result)
    }

    @Test
    fun `match embedded location text`() {
        val matcher = LocationMatcher(context)
        val result = matcher.match("上个月我在北京吃的麻辣烫")
        assertNotNull(result)
        assertEquals("北京", result!!.name)
    }

    @Test
    fun `match Japan`() {
        val matcher = LocationMatcher(context)
        val result = matcher.match("日本旅游")
        assertNotNull(result)
        assertEquals("日本", result!!.name)
    }

    @Test
    fun `match country alias USA`() {
        val matcher = LocationMatcher(context)
        val result = matcher.match("USA照片")
        assertNotNull(result)
    }

    @Test
    fun `match no location returns null`() {
        val matcher = LocationMatcher(context)
        val result = matcher.match("你好世界")
        assertNull(result)
    }

    @Test
    fun `match 上海 returns Shanghai bounds`() {
        val matcher = LocationMatcher(context)
        val result = matcher.match("上海")
        assertNotNull(result)
        // Shanghai: lat 30.40-31.53
        assertTrue(result!!.bounds.latMin > 30.0)
    }
}
