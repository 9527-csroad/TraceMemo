package com.example.picsearch.util

import com.example.picsearch.data.TimeRange
import java.util.Calendar

/**
 * 从中文文本中提取时间表达式。
 * 支持：相对时间词、季节词、中国节日、组合表达式、数字表达式。
 */
object TimeExpressionParser {

    // ========== 农历节日预计算表 (2020-2030) ==========
    private val LUNAR_NEW_YEAR = mapOf(
        2020 to pairOf(2020, 1, 25),
        2021 to pairOf(2021, 2, 12),
        2022 to pairOf(2022, 2, 1),
        2023 to pairOf(2023, 1, 22),
        2024 to pairOf(2024, 2, 10),
        2025 to pairOf(2025, 1, 29),
        2026 to pairOf(2026, 2, 17),
        2027 to pairOf(2027, 2, 6),
        2028 to pairOf(2028, 1, 26),
        2029 to pairOf(2029, 2, 13),
        2030 to pairOf(2030, 2, 3),
    )

    private val QING_MING = mapOf(
        2020 to pairOf(2020, 4, 4),
        2021 to pairOf(2021, 4, 4),
        2022 to pairOf(2022, 4, 5),
        2023 to pairOf(2023, 4, 5),
        2024 to pairOf(2024, 4, 4),
        2025 to pairOf(2025, 4, 4),
        2026 to pairOf(2026, 4, 5),
        2027 to pairOf(2027, 4, 5),
        2028 to pairOf(2028, 4, 4),
        2029 to pairOf(2029, 4, 4),
        2030 to pairOf(2030, 4, 5),
    )

    private val DUAN_WU = mapOf(
        2020 to pairOf(2020, 6, 25),
        2021 to pairOf(2021, 6, 14),
        2022 to pairOf(2022, 6, 3),
        2023 to pairOf(2023, 6, 22),
        2024 to pairOf(2024, 6, 10),
        2025 to pairOf(2025, 5, 31),
        2026 to pairOf(2026, 6, 19),
        2027 to pairOf(2027, 6, 9),
        2028 to pairOf(2028, 5, 28),
        2029 to pairOf(2029, 6, 16),
        2030 to pairOf(2030, 6, 5),
    )

    private val MID_AUTUMN = mapOf(
        2020 to pairOf(2020, 10, 1),
        2021 to pairOf(2021, 9, 21),
        2022 to pairOf(2022, 9, 10),
        2023 to pairOf(2023, 9, 29),
        2024 to pairOf(2024, 9, 17),
        2025 to pairOf(2025, 10, 6),
        2026 to pairOf(2026, 9, 25),
        2027 to pairOf(2027, 9, 15),
        2028 to pairOf(2028, 10, 3),
        2029 to pairOf(2029, 9, 22),
        2030 to pairOf(2030, 9, 12),
    )

    private val QI_XI = mapOf(
        2020 to pairOf(2020, 8, 25),
        2021 to pairOf(2021, 8, 14),
        2022 to pairOf(2022, 8, 4),
        2023 to pairOf(2023, 8, 22),
        2024 to pairOf(2024, 8, 10),
        2025 to pairOf(2025, 8, 29),
        2026 to pairOf(2026, 8, 19),
        2027 to pairOf(2027, 8, 9),
        2028 to pairOf(2028, 8, 28),
        2029 to pairOf(2029, 8, 16),
        2030 to pairOf(2030, 8, 6),
    )

    private val YEAR_OFFSET_KEYWORDS = mapOf(
        "去年" to -1, "前年" to -2, "大前年" to -3,
        "明年" to 1, "后年" to 2,
        "今年" to 0, "本年" to 0,
    )

    private val MONTH_RANGE_KEYWORDS = mapOf(
        "春天" to 3..5, "春季" to 3..5, "春日" to 3..5,
        "夏天" to 6..8, "夏季" to 6..8, "夏日" to 6..8,
        "秋天" to 9..11, "秋季" to 9..11, "秋日" to 9..11,
        "冬天" to 12..2, "冬季" to 12..2, "冬日" to 12..2,
        "上半年" to 1..6,
        "下半年" to 7..12,
    )

    private val RELATIVE_MONTH_KEYWORDS = mapOf(
        "上个月" to -1, "上月" to -1, "近30天" to -1,
        "这个月" to 0, "本月" to 0,
    )

    private val RELATIVE_WEEK_KEYWORDS = mapOf(
        "上周" to -1, "本周" to 0, "近7天" to 0,
    )

    private val RELATIVE_DAY_KEYWORDS = mapOf(
        "昨天" to -1, "前天" to -2, "明天" to 1,
    )

    fun parse(text: String): TimeRange? {
        var workingText = text

        parseNumericExpression(workingText)?.let { return it }

        val yearMatch = YEAR_OFFSET_KEYWORDS.entries.find { it.key in workingText }
        val yearKeyword = yearMatch?.key
        val yearOffset = yearMatch?.value ?: 0

        if (yearKeyword != null) {
            workingText = workingText.replace(yearKeyword, "")
        }

        val monthRange = MONTH_RANGE_KEYWORDS.entries.find { it.key in workingText }?.value

        if (monthRange != null && yearOffset != 0) {
            return combineYearAndMonths(yearOffset, monthRange)
        }

        if (monthRange != null && yearOffset == 0) {
            return combineYearAndMonths(0, monthRange)
        }

        if (yearKeyword != null && monthRange == null) {
            return fullYearRange(yearOffset)
        }

        RELATIVE_MONTH_KEYWORDS.entries.find { it.key in text }
        ?.let { entry ->
            return relativeMonthRange(entry.value)
        }

        RELATIVE_WEEK_KEYWORDS.entries.find { it.key in text }
        ?.let { entry ->
            return relativeWeekRange(entry.value)
        }

        RELATIVE_DAY_KEYWORDS.entries.find { it.key in text }
        ?.let { entry ->
            return relativeDayRange(entry.value)
        }

        parseFestival(text)?.let { return it }

        return null
    }

    private fun pairOf(year: Int, month: Int, day: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        return start to cal.timeInMillis
    }

    private fun fullYearRange(offset: Int): TimeRange {
        val now = Calendar.getInstance()
        val targetYear = now.get(Calendar.YEAR) + offset
        val start = Calendar.getInstance().apply {
            set(targetYear, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance().apply {
            set(targetYear, Calendar.DECEMBER, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return TimeRange(start.timeInMillis, end.timeInMillis)
    }

    private fun combineYearAndMonths(offset: Int, months: IntRange): TimeRange {
        val now = Calendar.getInstance()
        val targetYear = now.get(Calendar.YEAR) + offset
        val startMonth = months.first
        val endMonth = months.last

        val (actualStartYear, actualStartMonth) = if (startMonth > endMonth) {
            targetYear - 1 to startMonth
        } else {
            targetYear to startMonth
        }
        val (actualEndYear, actualEndMonth) = if (startMonth > endMonth) {
            targetYear to endMonth
        } else {
            targetYear to endMonth
        }

        val start = Calendar.getInstance().apply {
            set(actualStartYear, actualStartMonth - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance().apply {
            set(actualEndYear, actualEndMonth - 1, 1)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return TimeRange(start.timeInMillis, end.timeInMillis)
    }

    private fun relativeMonthRange(offset: Int): TimeRange {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, offset)
        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.MONTH, cal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = if (offset <= 0) {
            Calendar.getInstance().apply {
                set(Calendar.YEAR, cal.get(Calendar.YEAR))
                set(Calendar.MONTH, cal.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
        } else {
            Calendar.getInstance()
        }
        return TimeRange(start.timeInMillis, end.timeInMillis)
    }

    private fun relativeWeekRange(offset: Int): TimeRange {
        val cal = Calendar.getInstance()
        cal.add(Calendar.WEEK_OF_YEAR, offset)
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.add(Calendar.DAY_OF_WEEK, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return TimeRange(start, end)
    }

    private fun relativeDayRange(offset: Int): TimeRange {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, offset)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return TimeRange(start, end)
    }

    private fun parseNumericExpression(text: String): TimeRange? {
        val monthRegex = """(\d+)个?月前""".toRegex()
        monthRegex.find(text)?.let { match ->
            val months = match.groupValues[1].toIntOrNull() ?: return null
            val cal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -months)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            return TimeRange(start, cal.timeInMillis)
        }

        val weekRegex = """(\d+)个?周前""".toRegex()
        weekRegex.find(text)?.let { match ->
            val weeks = match.groupValues[1].toIntOrNull() ?: return null
            val cal = Calendar.getInstance().apply {
                add(Calendar.WEEK_OF_YEAR, -weeks)
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            return TimeRange(start, cal.timeInMillis)
        }

        val dayRegex = """(\d+)天前""".toRegex()
        dayRegex.find(text)?.let { match ->
            val days = match.groupValues[1].toIntOrNull() ?: return null
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -days)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            return TimeRange(start, cal.timeInMillis)
        }

        return null
    }

    private fun parseFestival(text: String): TimeRange? {
        val year = Calendar.getInstance().get(Calendar.YEAR)

        if ("元旦" in text) {
            val (s, e) = pairOf(year, 1, 1)
            return TimeRange(s, e)
        }

        if ("国庆" in text || "十一" in text) {
            val start = Calendar.getInstance().apply {
                set(year, 9, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val end = Calendar.getInstance().apply {
                set(year, 9, 7, 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return TimeRange(start.timeInMillis, end.timeInMillis)
        }

        if ("春节" in text) {
            LUNAR_NEW_YEAR[year]?.let { (s, e) ->
                return TimeRange(s, e)
            }
        }

        if ("清明" in text) {
            QING_MING[year]?.let { (s, e) ->
                return TimeRange(s, e)
            }
        }

        if ("端午" in text) {
            DUAN_WU[year]?.let { (s, e) ->
                return TimeRange(s, e)
            }
        }

        if ("中秋" in text) {
            MID_AUTUMN[year]?.let { (s, e) ->
                return TimeRange(s, e)
            }
        }

        if ("七夕" in text) {
            QI_XI[year]?.let { (s, e) ->
                return TimeRange(s, e)
            }
        }

        return null
    }
}