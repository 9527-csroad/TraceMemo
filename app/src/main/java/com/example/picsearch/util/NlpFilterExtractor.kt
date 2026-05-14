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
     */
    fun extract(text: String): ExtractedFilter {
        var workingText = text

        // 1. 提取时间
        val timeRange = TimeExpressionParser.parse(workingText)
        // 移除已匹配的时间关键词
        workingText = NlpFilterExtractor.removeMatchedKeywords(workingText, TIME_KEYWORDS)

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
