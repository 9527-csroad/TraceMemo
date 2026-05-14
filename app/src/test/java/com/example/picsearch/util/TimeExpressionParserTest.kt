package com.example.picsearch.util

import com.example.picsearch.data.TimeRange
import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

class TimeExpressionParserTest {

    @Test
    fun `parse 去年 returns full last year range`() {
        val result = TimeExpressionParser.parse("去年")
        assertNotNull(result)
        val expectedYear = Calendar.getInstance().get(Calendar.YEAR) - 1
        val cal = Calendar.getInstance()
        cal.set(expectedYear, Calendar.JANUARY, 1)
        cal.set(Calendar.MILLISECOND, 0)
        val expectedStart = cal.timeInMillis
        assertTrue("start should be close to expected", kotlin.math.abs(result!!.startMillis - expectedStart) < 86400000L)
    }

    @Test
    fun `parse 前年 returns full year range for two years ago`() {
        val result = TimeExpressionParser.parse("前年")
        assertNotNull(result)
        val expectedYear = Calendar.getInstance().get(Calendar.YEAR) - 2
        val cal = Calendar.getInstance()
        cal.set(expectedYear, Calendar.JANUARY, 1)
        cal.set(Calendar.MILLISECOND, 0)
        val expectedStart = cal.timeInMillis
        assertTrue("start should be close to expected", kotlin.math.abs(result!!.startMillis - expectedStart) < 86400000L)
    }

    @Test
    fun `parse 去年夏天 returns June-August of last year`() {
        val result = TimeExpressionParser.parse("去年夏天")
        assertNotNull(result)
        val expectedYear = Calendar.getInstance().get(Calendar.YEAR) - 1
        val cal = Calendar.getInstance()
        cal.set(expectedYear, 5, 1) // June (0-indexed)
        cal.set(Calendar.MILLISECOND, 0)
        val expectedStart = cal.timeInMillis
        assertTrue("start should be close to June $expectedYear", kotlin.math.abs(result!!.startMillis - expectedStart) < 86400000L * 5)
    }

    @Test
    fun `parse 前年上半年 returns Jan-June of two years ago`() {
        val result = TimeExpressionParser.parse("前年上半年")
        assertNotNull(result)
        val expectedYear = Calendar.getInstance().get(Calendar.YEAR) - 2
        val cal = Calendar.getInstance()
        cal.set(expectedYear, Calendar.JANUARY, 1)
        cal.set(Calendar.MILLISECOND, 0)
        val expectedStart = cal.timeInMillis
        assertTrue("start should be close to Jan $expectedYear", kotlin.math.abs(result!!.startMillis - expectedStart) < 86400000L)
    }

    @Test
    fun `parse 国庆 returns Oct 1-7 range`() {
        val result = TimeExpressionParser.parse("国庆节")
        assertNotNull(result)
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val cal = Calendar.getInstance()
        cal.set(year, 9, 1) // October (0-indexed)
        cal.set(Calendar.MILLISECOND, 0)
        val expectedStart = cal.timeInMillis
        assertTrue("start should be close to Oct 1", kotlin.math.abs(result!!.startMillis - expectedStart) < 86400000L)
    }

    @Test
    fun `parse 春节 returns Lunar New Year date`() {
        val result = TimeExpressionParser.parse("春节")
        assertNotNull(result)
        val year = Calendar.getInstance().get(Calendar.YEAR)
        if (year in 2020..2030) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = result!!.startMillis
            val month = cal.get(Calendar.MONTH)
            assertTrue("Spring festival should be Jan/Feb", month == Calendar.JANUARY || month == Calendar.FEBRUARY)
        }
    }

    @Test
    fun `parse 3个月前 returns 3 months ago full month`() {
        val result = TimeExpressionParser.parse("3个月前")
        assertNotNull(result)
        val expectedCal = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }
        val expectedYear = expectedCal.get(Calendar.YEAR)
        val expectedMonth = expectedCal.get(Calendar.MONTH)
        val cal = Calendar.getInstance()
        cal.set(expectedYear, expectedMonth, 1)
        cal.set(Calendar.MILLISECOND, 0)
        val expectedStart = cal.timeInMillis
        assertTrue("start should be close to 3 months ago", kotlin.math.abs(result!!.startMillis - expectedStart) < 86400000L * 5)
    }

    @Test
    fun `parse no time keyword returns null`() {
        val result = TimeExpressionParser.parse("你好世界")
        assertNull(result)
    }

    @Test
    fun `parse text with embedded time returns range`() {
        val result = TimeExpressionParser.parse("我去年去旅游的照片")
        assertNotNull(result)
    }

    @Test
    fun `parse 今天 returns null as not supported`() {
        val result = TimeExpressionParser.parse("今天的照片")
        assertNull(result)
    }

    @Test
    fun `parse 去年春节 returns Spring Festival of last year`() {
        val result = TimeExpressionParser.parse("去年春节")
        assertNotNull(result)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val lastYear = currentYear - 1
        // 2025 Spring Festival is Jan 29, 2026 is Feb 17
        // So for lastYear in 2020..2030, it should be Jan or Feb
        val cal = Calendar.getInstance()
        cal.timeInMillis = result!!.startMillis
        val year = cal.get(Calendar.YEAR)
        assertEquals("festival should be for last year ($lastYear)", lastYear, year)
    }
}