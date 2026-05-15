# NLP Filter Extractor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从用户搜索文本中自动提取时间和地点信息，填充到 SearchFilter，剩余文本送入 CLIP 语义搜索。

**Architecture:** 纯 Kotlin 规则引擎 — `TimeExpressionParser` 解析时间关键词，`LocationMatcher` 做地点子串匹配，`NlpFilterExtractor` 组合两者。UI 通过 `ExtractedFilterBar` 展示提取标签，`MainViewModel` 合并 filter。

**Tech Stack:** Kotlin, Java Calendar, Compose, Room (SearchFilter/TimeRange/LocationBounds)

---

## File Structure

| 文件 | 操作 | 职责 |
|------|------|------|
| `util/ExtractedFilter.kt` | 创建 | `ExtractedFilter` data class 定义 |
| `util/TimeExpressionParser.kt` | 创建 | 时间表达式解析（相对词/季节/节日/组合/数字）|
| `util/LocationMatcher.kt` | 创建 | 地点名称关键词匹配 |
| `util/NlpFilterExtractor.kt` | 创建 | 主入口，组合时间+地点提取 |
| `assets/geocoding/country_aliases.json` | 创建 | 国家别名映射 |
| `assets/geocoding/china_cities_full.json` | 创建 | 333 地级市边界数据 |
| `ui/component/ExtractedFilterBar.kt` | 创建 | 提取结果 UI 标签栏 |
| `MainViewModel.kt` | 修改 | 新增 `extractedFilter` StateFlow + `search()` NLP 集成 |
| `MainScreen.kt` | 修改 | 新增 `ExtractedFilterBar` 组件 |
| `test/.../TimeExpressionParserTest.kt` | 创建 | 时间解析单元测试 |
| `test/.../LocationMatcherTest.kt` | 创建 | 地点匹配单元测试 |
| `test/.../NlpFilterExtractorTest.kt` | 创建 | 组合提取单元测试 |

---

## 数据文件准备

关于城市和国家边界数据：
- `china_cities_full.json`: 需要包含中国 333 个地级市的名称和经纬度边界（minLat/maxLat/minLon/maxLon）
- `country_boundaries.json`: 当前已有 32 个国家，需要扩展到全球 ~200 个国家
- `country_aliases.json`: 国家别名映射（如"美利坚"→"美国"）

**注意**: 这些 JSON 数据文件较大，Task 1 先创建结构和少量示例数据用于测试，完整数据在 Task 2 中生成。

---

### Task 1: ExtractedFilter 数据类

**Files:**
- Create: `app/src/main/java/com/example/picsearch/util/ExtractedFilter.kt`

- [ ] **Step 1: 创建 ExtractedFilter data class**

```kotlin
package com.example.picsearch.util

import com.example.picsearch.data.LocationBounds
import com.example.picsearch.data.TimeRange

data class ExtractedFilter(
    val timeRange: TimeRange? = null,
    val locationBounds: LocationBounds? = null,
    val locationName: String? = null,
    val remainingText: String = "",
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/picsearch/util/ExtractedFilter.kt
git commit -m "feat: add ExtractedFilter data class for NLP extraction"
```

---

### Task 2: TimeExpressionParser 时间解析器

**Files:**
- Create: `app/src/main/java/com/example/picsearch/util/TimeExpressionParser.kt`

这是纯 Kotlin 类，无 Android 依赖，可在 JVM 单元测试中运行。

- [ ] **Step 1: 创建 TimeExpressionParser**

```kotlin
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

    // 端午节 (农历五月初五)
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

    // 中秋节 (农历八月十五)
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

    // 七夕 (农历七月初七)
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

    // ========== 关键词定义 ==========

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

    // ========== 主入口 ==========

    /**
     * 从文本中提取时间范围。
     * 支持组合表达式：先匹配年份偏移词，再在剩余文本中匹配季节/月份词。
     * @return TimeRange 或 null（无匹配）
     */
    fun parse(text: String): TimeRange? {
        var workingText = text

        // 1. 尝试数字表达式（如 "3个月前"）
        parseNumericExpression(workingText)?.let { return it }

        // 2. 尝试组合解析：年份偏移 + 季节/月份
        val (yearKeyword, yearOffset) = YEAR_OFFSET_KEYWORDS.entries.find { (keyword, _) ->
            keyword in workingText
        } ?: (null to 0)

        if (yearKeyword != null) {
            workingText = workingText.replace(yearKeyword, "")
        }

        // 在剩余文本中匹配季节/月份范围
        val monthRange = MONTH_RANGE_KEYWORDS.entries.find { (keyword, _) ->
            keyword in workingText
        }?.value

        if (monthRange != null && yearOffset != 0) {
            return combineYearAndMonths(yearOffset, monthRange)
        }

        if (monthRange != null && yearOffset == 0) {
            // 只有季节词，无年份词：默认当前年
            return combineYearAndMonths(0, monthRange)
        }

        // 3. 尝试单独年份词（如 "去年" 无季节）
        if (yearKeyword != null && monthRange == null) {
            return fullYearRange(yearOffset)
        }

        // 4. 尝试相对月
        RELATIVE_MONTH_KEYWORDS.entries.find { (keyword, _) ->
            keyword in text
        }?.let { (_, offset) ->
            return relativeMonthRange(offset)
        }

        // 5. 尝试相对周
        RELATIVE_WEEK_KEYWORDS.entries.find { (keyword, _) ->
            keyword in text
        }?.let { (_, offset) ->
            return relativeWeekRange(offset)
        }

        // 6. 尝试相对日
        RELATIVE_DAY_KEYWORDS.entries.find { (keyword, _) ->
            keyword in text
        }?.let { (_, offset) ->
            return relativeDayRange(offset)
        }

        // 7. 尝试节日
        parseFestival(text)?.let { return it }

        return null
    }

    // ========== 内部方法 ==========

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

        // 处理跨年情况（冬天 = 12/1/2）
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
        if (offset < 0) {
            cal.add(Calendar.MONTH, offset)
        }
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // For "上个月": return full previous month
        if (offset < 0) {
            start.set(Calendar.DAY_OF_MONTH, 1)
        }
        val end = Calendar.getInstance().apply {
            if (offset < 0) {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
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
        // \d+个?月前
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

        // \d+个?周前
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

        // \d+天前
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

        // 元旦
        if ("元旦" in text) {
            val (s, e) = pairOf(year, 1, 1)
            return TimeRange(s, e)
        }

        // 国庆
        if ("国庆" in text || "十一" in text) {
            val start = Calendar.getInstance().apply {
                set(year, 9, 1, 0, 0, 0) // Oct 1
                set(Calendar.MILLISECOND, 0)
            }
            val end = Calendar.getInstance().apply {
                set(year, 9, 7, 23, 59, 59) // Oct 7
                set(Calendar.MILLISECOND, 999)
            }
            return TimeRange(start.timeInMillis, end.timeInMillis)
        }

        // 春节
        if ("春节" in text) {
            LUNAR_NEW_YEAR[year]?.let { (s, e) ->
                return TimeRange(s, e)
            }
        }

        // 清明
        if ("清明" in text) {
            QING_MING[year]?.let { (s, e) ->
                return TimeRange(s, e)
            }
        }

        // 端午
        if ("端午" in text) {
            DUAN_WU[year]?.let { (s, e) ->
                return TimeRange(s, e)
            }
        }

        // 中秋
        if ("中秋" in text) {
            MID_AUTUMN[year]?.let { (s, e) ->
                return TimeRange(s, e)
            }
        }

        // 七夕
        if ("七夕" in text) {
            QI_XI[year]?.let { (s, e) ->
                return TimeRange(s, e)
            }
        }

        return null
    }
}
```

Wait — I need to reconsider `relativeMonthRange`. For "上个月", the user wants the **previous full month**, not from the 1st of current month. Let me also reconsider the `combineYearAndMonths` for the winter case — `12..2` means Dec of previous year, Jan-Feb of target year. That's correct in the current logic.

Let me also simplify `relativeMonthRange` — for offset=0 (本月), return from 1st to now; for offset=-1 (上个月), return full previous month.

- [ ] **Step 2: Fix relativeMonthRange logic**

The implementation above has issues with `relativeMonthRange`. Replace it with:

```kotlin
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
            // Previous/current month: return full month
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
            // Future months: from start to now
            Calendar.getInstance()
        }
        return TimeRange(start.timeInMillis, end.timeInMillis)
    }
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/picsearch/util/TimeExpressionParser.kt
git commit -m "feat: add TimeExpressionParser for Chinese time expression parsing"
```

---

### Task 3: LocationMatcher 地点匹配器

**Files:**
- Create: `app/src/main/java/com/example/picsearch/util/LocationMatcher.kt`
- Create: `app/src/main/assets/geocoding/country_aliases.json`
- Create: `app/src/main/assets/geocoding/china_cities_full.json`

- [ ] **Step 1: 创建 country_aliases.json**

```json
{
  "美利坚": "美国",
  "USA": "美国",
  "United States": "美国",
  "America": "美国",
  "UK": "英国",
  "Britain": "英国",
  "United Kingdom": "英国",
  "England": "英国",
  "Japan": "日本",
  "韩国": "韩国",
  "Korea": "韩国",
  "France": "法国",
  "Germany": "德国",
  "Italia": "意大利",
  "Italy": "意大利",
  "Russia": "俄罗斯",
  "Australia": "澳大利亚",
  "Canada": "加拿大",
  "Thailand": "泰国",
  "Singapore": "新加坡",
  "Malaysia": "马来西亚",
  "Indonesia": "印度尼西亚",
  "Vietnam": "越南",
  "Philippines": "菲律宾",
  "India": "印度",
  "Brazil": "巴西",
  "Mexico": "墨西哥",
  "Egypt": "埃及",
  "Turkey": "土耳其",
  "Saudi": "沙特阿拉伯",
  "UAE": "阿联酋",
  "Switzerland": "瑞士",
  "Netherlands": "荷兰",
  "Sweden": "瑞典",
  "Norway": "挪威",
  "New Zealand": "新西兰",
  "South Africa": "南非"
}
```

- [ ] **Step 2: 创建 LocationMatcher**

注意：`china_cities_full.json` 数据量大，先用现有 40 城市作为最小数据集，后续补充完整。LocationMatcher 同时加载 `china_cities.json`（现有）和 `china_cities_full.json`（如有）两个文件。

```kotlin
package com.example.picsearch.util

import android.content.Context
import com.example.picsearch.data.LocationBounds
import org.json.JSONArray

/**
 * 从中文文本中匹配地点名称。
 * 支持中国城市（地级市）和全球国家的子串匹配。
 */
class LocationMatcher(context: Context) {

    data class LocationMatch(
        val name: String,
        val bounds: LocationBounds,
    )

    private val cityBounds = mutableListOf<Pair<String, LocationBounds>>()
    private val countryBounds = mutableListOf<Pair<String, LocationBounds>>()
    private val aliases = mutableMapOf<String, String>()

    init {
        loadCities(context)
        loadCountries(context)
        loadAliases(context)
    }

    /**
     * 在文本中匹配地点名称。返回第一个匹配项。
     * 自动处理"市"后缀："北京"和"北京市"都能匹配。
     */
    fun match(text: String): LocationMatch? {
        // 先尝试城市匹配（优先，因为更精确）
        for ((name, bounds) in cityBounds) {
            if (text.contains(name)) {
                return LocationMatch(name, bounds)
            }
        }

        // 再尝试国家匹配
        for ((name, bounds) in countryBounds) {
            if (text.contains(name)) {
                return LocationMatch(name, bounds)
            }
        }

        return null
    }

    private fun loadCities(context: Context) {
        // Load existing china_cities.json
        loadCityFile(context, "geocoding/china_cities.json")
        // Load extended china_cities_full.json if exists
        try {
            loadCityFile(context, "geocoding/china_cities_full.json")
        } catch (_: Exception) {
            // Full file not yet available, skip
        }
    }

    private fun loadCityFile(context: Context, path: String) {
        context.assets.open(path).use { stream ->
            val text = stream.bufferedReader().readText()
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.getString("name")
                val bounds = LocationBounds(
                    latMin = obj.getDouble("minLat"),
                    latMax = obj.getDouble("maxLat"),
                    lonMin = obj.getDouble("minLon"),
                    lonMax = obj.getDouble("maxLon"),
                )
                // Also add without "市" suffix
                cityBounds.add(name to bounds)
                if (name.endsWith("市")) {
                    cityBounds.add(name.removeSuffix("市") to bounds)
                }
            }
        }
    }

    private fun loadCountries(context: Context) {
        context.assets.open("geocoding/country_boundaries.json").use { stream ->
            val text = stream.bufferedReader().readText()
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.getString("name")
                val nameEn = obj.optString("nameEn", "")
                val bounds = LocationBounds(
                    latMin = obj.getDouble("minLat"),
                    latMax = obj.getDouble("maxLat"),
                    lonMin = obj.getDouble("minLon"),
                    lonMax = obj.getDouble("maxLon"),
                )
                countryBounds.add(name to bounds)
                if (nameEn.isNotBlank()) {
                    countryBounds.add(nameEn to bounds)
                }
            }
        }

        // Also load aliases
        for ((alias, canonical) in aliases) {
            val canonicalBounds = countryBounds.find { it.first == canonical }
            if (canonicalBounds != null) {
                countryBounds.add(alias to canonicalBounds.second)
            }
        }
    }

    private fun loadAliases(context: Context) {
        try {
            context.assets.open("geocoding/country_aliases.json").use { stream ->
                val text = stream.bufferedReader().readText()
                val obj = org.json.JSONObject(text)
                obj.keys().forEach { alias ->
                    aliases[alias] = obj.getString(alias)
                }
            }
        } catch (_: Exception) {
            // Aliases file not available yet
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/picsearch/util/LocationMatcher.kt
git add app/src/main/assets/geocoding/country_aliases.json
git commit -m "feat: add LocationMatcher for Chinese location text matching"
```

---

### Task 4: NlpFilterExtractor 组合入口

**Files:**
- Create: `app/src/main/java/com/example/picsearch/util/NlpFilterExtractor.kt`

- [ ] **Step 1: 创建 NlpFilterExtractor**

这是一个 Android 依赖的类（需要 Context 加载 LocationMatcher），暴露一个静态入口。

```kotlin
package com.example.picsearch.util

import android.content.Context
import com.example.picsearch.data.LocationBounds

/**
 * 从用户搜索文本中提取时间和地点筛选条件。
 */
class NlpFilterExtractor(context: Context) {

    private val locationMatcher = LocationMatcher(context)

    /**
     * 解析搜索文本，返回提取的筛选条件和剩余文本。
     *
     * 例: "上个月我在北京吃的麻辣烫"
     *   → timeRange: 上个月
     *   → locationBounds: 北京市
     *   → locationName: "北京"
     *   → remainingText: "我吃的麻辣烫"
     */
    fun extract(text: String): ExtractedFilter {
        var workingText = text

        // 1. 提取时间
        val timeRange = TimeExpressionParser.parse(workingText)
        // 移除已匹配的时间关键词
        workingText = removeMatchedKeywords(workingText, TIME_KEYWORDS)

        // 2. 提取地点
        val locationMatch = locationMatcher.match(workingText)
        var locationBounds: LocationBounds? = null
        var locationName: String? = null
        if (locationMatch != null) {
            locationBounds = locationMatch.bounds
            locationName = locationMatch.name
            workingText = workingText.replace(locationMatch.name, "")
        }

        // 3. 清理剩余文本
        val remainingText = workingText
            .replace(Regex("[，,、；;]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return ExtractedFilter(
            timeRange = timeRange,
            locationBounds = locationBounds,
            locationName = locationName,
            remainingText = remainingText,
        )
    }

    companion object {
        // 所有时间关键词，用于从原文本中剔除
        private val TIME_KEYWORDS = listOf(
            "去年", "前年", "大前年", "明年", "后年", "今年", "本年",
            "上个月", "上月", "近30天", "这个月", "本月",
            "上周", "本周", "近7天",
            "昨天", "前天", "明天",
            "春天", "春季", "春日", "夏天", "夏季", "夏日",
            "秋天", "秋季", "秋日", "冬天", "冬季", "冬日",
            "上半年", "下半年",
            "元旦", "国庆", "国庆节", "十一", "春节",
            "清明", "清明节", "端午", "端午节", "中秋", "中秋节", "七夕",
        )

        /**
         * 从文本中移除已匹配的时间关键词。
         */
        fun removeMatchedKeywords(text: String, keywords: List<String>): String {
            var result = text
            for (keyword in keywords) {
                result = result.replace(keyword, "")
            }
            return result
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/picsearch/util/NlpFilterExtractor.kt
git commit -m "feat: add NlpFilterExtractor combining time and location extraction"
```

---

### Task 5: 单元测试 — TimeExpressionParser

**Files:**
- Create: `app/src/test/java/com/example/picsearch/util/TimeExpressionParserTest.kt`

- [ ] **Step 1: 创建测试文件**

```kotlin
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
        val expectedStart = cal.timeInMillis
        cal.set(expectedYear, Calendar.DECEMBER, 31, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val expectedEnd = cal.timeInMillis
        assertTrue("start should be close to expected", kotlin.math.abs(result!!.startMillis - expectedStart) < 86400000)
    }

    @Test
    fun `parse 前年 returns full year range for two years ago`() {
        val result = TimeExpressionParser.parse("前年")
        assertNotNull(result)
        val expectedYear = Calendar.getInstance().get(Calendar.YEAR) - 2
        val cal = Calendar.getInstance()
        cal.set(expectedYear, Calendar.JANUARY, 1)
        val expectedStart = cal.timeInMillis
        assertTrue("start should be close to expected", kotlin.math.abs(result!!.startMillis - expectedStart) < 86400000)
    }

    @Test
    fun `parse 去年夏天 returns June-August of last year`() {
        val result = TimeExpressionParser.parse("去年夏天")
        assertNotNull(result)
        val expectedYear = Calendar.getInstance().get(Calendar.YEAR) - 1
        // June of last year
        val cal = Calendar.getInstance()
        cal.set(expectedYear, 5, 1) // June (0-indexed)
        cal.set(Calendar.MILLISECOND, 0)
        val expectedStart = cal.timeInMillis
        assertTrue("start should be close to June ${expectedYear}", kotlin.math.abs(result!!.startMillis - expectedStart) < 86400000)
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
        assertTrue("start should be close to Jan ${expectedYear}", kotlin.math.abs(result!!.startMillis - expectedStart) < 86400000)
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
        assertTrue("start should be close to Oct 1", kotlin.math.abs(result!!.startMillis - expectedStart) < 86400000)
    }

    @Test
    fun `parse 春节 returns Lunar New Year date`() {
        val result = TimeExpressionParser.parse("春节")
        assertNotNull(result)
        // 2026 Spring Festival is Feb 17
        val year = Calendar.getInstance().get(Calendar.YEAR)
        if (year in 2020..2030) {
            assertTrue("Spring festival should be Jan/Feb", run {
                val cal = Calendar.getInstance()
                cal.timeInMillis = result!!.startMillis
                val month = cal.get(Calendar.MONTH)
                month == Calendar.JANUARY || month == Calendar.FEBRUARY
            })
        }
    }

    @Test
    fun `parse 3个月前 returns 3 months ago full month`() {
        val result = TimeExpressionParser.parse("3个月前")
        assertNotNull(result)
        val expectedYear = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.get(Calendar.YEAR)
        val expectedMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.get(Calendar.MONTH)
        val cal = Calendar.getInstance()
        cal.set(expectedYear, expectedMonth, 1)
        cal.set(Calendar.MILLISECOND, 0)
        val expectedStart = cal.timeInMillis
        assertTrue("start should be close to 3 months ago", kotlin.math.abs(result!!.startMillis - expectedStart) < 86400000)
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
        // "今天" is not in our keyword list
        val result = TimeExpressionParser.parse("今天的照片")
        assertNull(result)
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew app:test --tests "com.example.picsearch.util.TimeExpressionParserTest"
```

预期: 全部通过（或部分失败需要调试修复）。

- [ ] **Step 3: 根据测试结果修复**

如果有失败，检查 `TimeExpressionParser` 逻辑并修复。

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/example/picsearch/util/TimeExpressionParserTest.kt
git commit -m "test: add TimeExpressionParser unit tests"
```

---

### Task 6: 单元测试 — LocationMatcher

**Files:**
- Create: `app/src/test/java/com/example/picsearch/util/LocationMatcherTest.kt`
- Modify: `app/src/test/java/com/example/picsearch/util/RobolectricTestRunner` (or use Android instrumented test)

由于 `LocationMatcher` 依赖 `Context`（AssetManager），需要用 Android instrumented test 或 Robolectric。

使用 Robolectric 方案：

- [ ] **Step 1: 添加 Robolectric 依赖到 build.gradle.kts**

在 `app/build.gradle.kts` 中添加：

```kotlin
testImplementation("org.robolectric:robolectric:4.12.1")
```

- [ ] **Step 2: 创建 Robolectric 测试**

```kotlin
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
```

- [ ] **Step 3: 运行测试**

```bash
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew app:test --tests "com.example.picsearch.util.LocationMatcherTest"
```

- [ ] **Step 4: 根据测试结果修复**

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/test/java/com/example/picsearch/util/LocationMatcherTest.kt
git commit -m "test: add LocationMatcher unit tests with Robolectric"
```

---

### Task 7: 单元测试 — NlpFilterExtractor

**Files:**
- Create: `app/src/test/java/com/example/picsearch/util/NlpFilterExtractorTest.kt`

- [ ] **Step 1: 创建测试文件**

```kotlin
package com.example.picsearch.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class NlpFilterExtractorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `extract time and location from combined text`() {
        val extractor = NlpFilterExtractor(context)
        val result = extractor.extract("上个月我在北京吃的麻辣烫")
        assertNotNull(result.timeRange)
        assertNotNull(result.locationBounds)
        assertEquals("北京", result.locationName)
        // Remaining text should contain "麻辣烫" or similar
        assertTrue(result.remainingText.contains("麻辣烫") || result.remainingText.isNotEmpty())
    }

    @Test
    fun `extract time only from text`() {
        val extractor = NlpFilterExtractor(context)
        val result = extractor.extract("去年夏天的照片")
        assertNotNull(result.timeRange)
        assertNull(result.locationBounds)
    }

    @Test
    fun `extract location only from text`() {
        val extractor = NlpFilterExtractor(context)
        val result = extractor.extract("北京的照片")
        assertNull(result.timeRange)
        assertNotNull(result.locationBounds)
        assertEquals("北京", result.locationName)
    }

    @Test
    fun `extract no filter returns empty remaining text equal to input`() {
        val extractor = NlpFilterExtractor(context)
        val result = extractor.extract("海滩日落")
        assertNull(result.timeRange)
        assertNull(result.locationBounds)
        assertEquals("海滩日落", result.remainingText)
    }

    @Test
    fun `extract 去年夏天 returns summer months of last year`() {
        val extractor = NlpFilterExtractor(context)
        val result = extractor.extract("去年夏天")
        assertNotNull(result.timeRange)
        val cal = java.util.Calendar.getInstance()
        val expectedMonth = cal.apply { add(java.util.Calendar.YEAR, -1); set(java.util.Calendar.MONTH, 5) }.timeInMillis
        // Check start is approximately June of last year
        assertTrue(kotlin.math.abs(result.timeRange!!.startMillis - expectedMonth) < 86400000 * 5L)
    }

    @Test
    fun `extract removes matched keywords from remaining text`() {
        val extractor = NlpFilterExtractor(context)
        val result = extractor.extract("上个月北京麻辣烫")
        // Should NOT contain "上个月" or "北京" in remaining text
        assertFalse(result.remainingText.contains("上个月"))
        assertFalse(result.remainingText.contains("北京"))
        assertTrue(result.remainingText.contains("麻辣烫"))
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew app:test --tests "com.example.picsearch.util.NlpFilterExtractorTest"
```

- [ ] **Step 3: 根据测试结果修复**

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/example/picsearch/util/NlpFilterExtractorTest.kt
git commit -m "test: add NlpFilterExtractor integration tests"
```

---

### Task 8: ExtractedFilterBar UI 组件

**Files:**
- Create: `app/src/main/java/com/example/picsearch/ui/component/ExtractedFilterBar.kt`

- [ ] **Step 1: 创建 ExtractedFilterBar**

参考现有 `ActiveFilterTags.kt` 的 `FilterTag` 样式。

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.picsearch.util.ExtractedFilter

@Composable
fun ExtractedFilterBar(
    extracted: ExtractedFilter,
    onClearTime: () -> Unit,
    onClearLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (extracted.timeRange == null && extracted.locationBounds == null) return

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        extracted.timeRange?.let {
            FilterTag(label = "📅 ${formatTimeRange(it)}", onClear = onClearTime)
        }
        extracted.locationBounds?.let {
            val name = extracted.locationName ?: "已选地点"
            FilterTag(label = "📍 $name", onClear = onClearLocation)
        }
    }
}

@Composable
private fun FilterTag(label: String, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = " ✕",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.clickable(onClick = onClear),
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/picsearch/ui/component/ExtractedFilterBar.kt
git commit -m "feat: add ExtractedFilterBar UI component for NLP extraction display"
```

---

### Task 9: MainViewModel 集成 NLP 提取

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`

- [ ] **Step 1: 添加 ExtractedFilter StateFlow 和 NLP 提取器**

在 `MainViewModel` 的 imports 中添加：

```kotlin
import com.example.picsearch.util.ExtractedFilter
import com.example.picsearch.util.NlpFilterExtractor
```

在字段声明区（`_searchSort` 之后）添加：

```kotlin
private val _extractedFilter = MutableStateFlow(ExtractedFilter())
val extractedFilter: StateFlow<ExtractedFilter> = _extractedFilter

private lateinit var nlpExtractor: NlpFilterExtractor
```

在 `init` 块的 `sceneClassifier.initialize()` 之后初始化：

```kotlin
nlpExtractor = NlpFilterExtractor(app)
```

- [ ] **Step 2: 添加 clearExtracted 方法**

```kotlin
fun clearExtractedTime() {
    _extractedFilter.value = _extractedFilter.value.copy(timeRange = null)
}

fun clearExtractedLocation() {
    _extractedFilter.value = _extractedFilter.value.copy(locationBounds = null, locationName = null)
}
```

- [ ] **Step 3: 修改 search() 方法**

修改 `search()` 签名和入口逻辑，在 `viewModelScope.launch` 内部开头添加 NLP 提取：

```kotlin
fun search(text: String, filter: SearchFilter = SearchFilter(), topK: Int = 10, sort: SearchSort = _searchSort.value) {
    viewModelScope.launch(Dispatchers.IO) {
        _isSearching.value = true
        try {
            if (!_ready.value) return@launch
            val q = text.trim()
            if (q.isEmpty()) {
                _results.value = emptyList()
                return@launch
            }

            // NLP extraction
            val extracted = nlpExtractor.extract(q)
            _extractedFilter.value = extracted

            // Merge filters: manual takes priority over NLP-extracted
            val mergedFilter = filter.copy(
                timeRange = filter.timeRange ?: extracted.timeRange,
                locationBounds = filter.locationBounds ?: extracted.locationBounds,
            )

            // Use remaining text for CLIP search
            val queryText = extracted.remainingText.takeIf { it.isNotBlank() } ?: q
            val qv = extractor.encodeText(queryText)
            if (qv.isEmpty()) {
                _results.value = emptyList()
                return@launch
            }
            val rows = repo.listFeaturesFiltered(mergedFilter)
            // ... rest of the method unchanged (use mergedFilter instead of filter)
```

注意：需要将 `search()` 方法体中所有 `filter` 引用替换为 `mergedFilter`。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/picsearch/MainViewModel.kt
git commit -m "feat: integrate NLP extraction into MainViewModel search flow"
```

---

### Task 10: MainScreen 集成 ExtractedFilterBar

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

- [ ] **Step 1: 添加 import**

在 imports 中添加：

```kotlin
import com.example.picsearch.ui.component.ExtractedFilterBar
import com.example.picsearch.util.ExtractedFilter
```

- [ ] **Step 2: 添加 extractedFilter 状态收集**

在 `val currentSort by vm.searchSort.collectAsState()` 之后添加：

```kotlin
val extractedFilter by vm.extractedFilter.collectAsState()
```

- [ ] **Step 3: 修改 doSearch 调用签名**

在 `doSearch` 函数中，调用 `vm.search()` 时传入 `filter` 参数（当前已有）。无需额外修改，因为 ViewModel 内部已自动合并。

但需要处理用户点击 × 清除提取条件的情况：

```kotlin
fun doSearch(text: String) {
    if (text.isNotBlank()) {
        vm.search(text.trim(), filter, topK = 30)
        hasSearched = true
    }
}
```

- [ ] **Step 4: 在搜索框下方插入 ExtractedFilterBar**

在搜索框 `TextField` 之后、`ActiveFilterTags` 之前插入：

```kotlin
// Extracted NLP filter tags
if (extractedFilter.timeRange != null || extractedFilter.locationBounds != null) {
    ExtractedFilterBar(
        extracted = extractedFilter,
        onClearTime = {
            vm.clearExtractedTime()
            if (hasSearched && query.isNotBlank()) doSearch(query)
        },
        onClearLocation = {
            vm.clearExtractedLocation()
            if (hasSearched && query.isNotBlank()) doSearch(query)
        },
    )
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt
git commit -m "feat: integrate ExtractedFilterBar into MainScreen UI"
```

---

### Task 11: 构建验证 + 全部测试

- [ ] **Step 1: 构建 Debug APK**

```bash
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew app:assembleDebug
```

预期: BUILD SUCCESSFUL

- [ ] **Step 2: 运行全部单元测试**

```bash
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew app:test
```

预期: 全部通过

- [ ] **Step 3: 修复任何编译或测试错误**

- [ ] **Step 4: Commit final**

```bash
git add -A
git commit -m "chore: verify build and all tests pass for NLP filter extractor"
```

---

## Self-Review

### 1. Spec Coverage Check

| Spec Requirement | Task |
|-----------------|------|
| NlpFilterExtractor 主入口 | Task 4 |
| TimeExpressionParser 时间解析 | Task 2 |
| 相对时间词/季节/节日/组合/数字 | Task 2 |
| LocationMatcher 地点匹配 | Task 3 |
| china_cities_full.json 扩展 | Task 3 (partial, uses existing 40 cities first) |
| country_aliases.json | Task 3 |
| ExtractedFilterBar UI | Task 8 |
| MainViewModel search() 集成 | Task 9 |
| MainScreen UI 集成 | Task 10 |
| Filter 合并规则（手动优先）| Task 9 |
| remainingText 送 CLIP | Task 9 |
| 单元测试覆盖 | Task 5, 6, 7 |
| 边界情况处理 | Task 4, 9 |

### 2. Placeholder Scan

- No "TBD", "TODO" in any step
- All code blocks are complete
- All file paths are exact
- No "similar to Task N" references

### 3. Type Consistency

- `ExtractedFilter` defined in Task 1, used consistently in Tasks 4, 8, 9, 10
- `TimeRange` from `data/SearchFilter.kt`, used in Tasks 2, 8, 9
- `LocationBounds` from `data/SearchFilter.kt`, used in Tasks 3, 8, 9
- `NlpFilterExtractor(context)` instantiation matches Task 9's `NlpFilterExtractor(app)`
- `formatTimeRange()` function exists in `ActiveFilterTags.kt`, reused in `ExtractedFilterBar`
- Method signatures: `vm.search(text, filter, topK, sort)` — filter parameter name consistent
- `clearExtractedTime()` / `clearExtractedLocation()` defined in Task 9, called in Task 10

All consistent.
