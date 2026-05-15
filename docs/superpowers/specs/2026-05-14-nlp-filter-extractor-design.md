# 自然语言筛选器设计文档

**日期**: 2026-05-14
**状态**: 设计中
**任务**: 从用户搜索文本中自动提取时间和地点信息，填充到 SearchFilter

---

## 需求

用户输入自然语言描述如"去年的夏天"或"上个月我在北京吃的麻辣烫"，系统自动提取：
- **时间范围**：去年 6/7/8 月
- **地点**：北京市的 GPS 边界
- **剩余文本**："麻辣烫" 送入 CLIP 做语义搜索

提取结果在搜索框下方以标签形式展示，用户可点击 × 取消。

---

## 架构

```
用户输入 → NlpFilterExtractor.extract()
    ↓
ExtractedFilter {
    timeRange: TimeRange?
    locationBounds: LocationBounds?
    locationName: String?
    remainingText: String          // 剔除已匹配词后的文本
}
    ↓
remainingText → CLIP 语义搜索
timeRange + locationBounds → 合并到 SearchFilter
```

### 新增文件

| 文件 | 目录 | 职责 |
|------|------|------|
| `NlpFilterExtractor.kt` | `util/` | 主入口，组合时间/地点提取 |
| `TimeExpressionParser.kt` | `util/` | 时间表达式解析 |
| `LocationMatcher.kt` | `util/` | 地点名称关键词匹配 |
| `china_cities_full.json` | `assets/geocoding/` | ~333 个地级市边界数据 |
| `country_aliases.json` | `assets/geocoding/` | 国家名称别称映射 |
| `ExtractedFilterBar.kt` | `ui/component/` | 提取结果展示 UI |

---

## 时间解析规则 (TimeExpressionParser)

### 相对时间词

| 关键词 | 解析逻辑 |
|--------|----------|
| 去年/前年/大前年 | Calendar.add(YEAR, -1/-2/-3)，全年范围 |
| 今年/本年 | 当年 1/1 ~ 12/31 |
| 上个月/上月/近30天 | Calendar.add(MONTH, -1) |
| 本月/这个月 | 当月 1 日 ~ 月末 |
| 上周/本周/近7天 | 对应周范围（周一~周日）|
| 前天/昨天/明天 | 具体日期 |

### 季节词

| 关键词 | 月份范围 |
|--------|----------|
| 春天/春季/春日 | 3/4/5 月 |
| 夏天/夏季/夏日 | 6/7/8 月 |
| 秋天/秋季/秋日 | 9/10/11 月 |
| 冬天/冬季/冬日 | 12/1/2 月（跨年）|
| 上半年 | 1-6 月 |
| 下半年 | 7-12 月 |

### 中国节日（预计算 2020-2030 公历日期）

| 节日 | 固定日期 / 预计算 |
|------|-------------------|
| 元旦 | 1月1日（±1天容差）|
| 国庆/国庆节/十一 | 10月1日~10月7日 |
| 春节 | 预计算农历正月初一（2020~2030 公历映射表）|
| 清明/清明节 | 公历 4月4-6日，预计算 |
| 端午/端午节 | 预计算农历五月初五 |
| 中秋/中秋节 | 预计算农历八月十五 |
| 七夕 | 预计算农历七月初七 |

农历节日预计算表硬编码在代码中，不引入农历算法库。

### 组合解析

用户输入"去年夏天"：
1. 匹配到"去年"→ 得到年份偏移 -1
2. 剩余文本"夏天"→ 匹配到季节 6/7/8 月
3. 合并：偏移年 + 月份范围 → 完整 TimeRange

用户输入"前年上半年"：
1. "前年"→ 年份偏移 -2
2. "上半年"→ 1-6 月
3. 合并：前年的 1-6 月

### 数字表达式

正则匹配：`\d+个?月前`、`\d+个?周前`、`\d+天前`
如"3个月前"→ Calendar.add(MONTH, -3)，取整月范围。

---

## 地点匹配 (LocationMatcher)

### 匹配逻辑

1. 从 `china_cities_full.json` 加载 ~333 个地级市（名称 + 经纬度边界）
2. 从 `country_boundaries.json` 加载 ~200 个国家（扩展现有 32 个）
3. 从 `country_aliases.json` 加载别名映射（如"美国"→"美国"，"美利坚"→"美国"）
4. 在用户文本中做**子串匹配**：遍历所有城市名和国家名，取第一个匹配项
5. 自动去除"市"后缀："北京市"→ "北京"也能匹配
6. 匹配到后返回对应的 `LocationBounds`

### 多地点处理

用户输入"上个月我在北京和上海"：
- 当前只取第一个匹配项（北京）
- 后续可扩展为多地点 OR 筛选（不在本次范围内）

### 数据来源

- 中国地级市边界：[Natural Earth](https://github.com/nvkelso/natural-earth-vector) + [GADM](https://gadm.org/) 开源数据，转换为 JSON 格式
- 国家边界：在现有 `country_boundaries.json` 基础上补充至全球 ~200 个国家

---

## UI 设计 (ExtractedFilterBar)

在搜索框下方、现有 `FilterEntryRow` 上方新增一行：

```
┌─────────────────────────────────────────┐
│ [🔍 TextField: 上个月北京麻辣烫        ] │
├─────────────────────────────────────────┤
│ 📅 去年夏天 ✕  📍 北京 ✕               │  ← ExtractedFilterBar
├─────────────────────────────────────────┤
│ [时间] [地点] [场景]                    │  ← FilterEntryRow（现有）
└─────────────────────────────────────────┘
```

- 每个提取到的筛选条件显示为一个标签（chip），带 × 按钮
- 点击 × 移除该条件，同时更新 SearchFilter 并重新搜索
- 样式与现有 `ActiveFilterTags` 保持一致（Material3 风格）
- 无提取结果时不显示该行

---

## 搜索流程集成

### MainViewModel 变更

在 `search()` 方法入口增加 NLP 提取步骤：

```kotlin
fun search(text: String, filter: SearchFilter = SearchFilter(), topK: Int = 10, sort: SearchSort = _searchSort.value) {
    viewModelScope.launch(Dispatchers.IO) {
        _isSearching.value = true
        try {
            // 1. NLP 提取
            val extracted = NlpFilterExtractor.extract(text)

            // 2. 合并 filter：手动选择 + NLP 提取
            val mergedFilter = filter.copy(
                timeRange = filter.timeRange ?: extracted.timeRange,
                locationBounds = filter.locationBounds ?: extracted.locationBounds,
            )

            // 3. 更新 UI 状态（显示提取标签）
            _extractedFilter.value = extracted

            // 4. 用剩余文本做 CLIP 搜索
            val queryText = extracted.remainingText.takeIf { it.isNotBlank() } ?: text.trim()
            val qv = extractor.encodeText(queryText)
            // ... 后续逻辑不变
        }
    }
}
```

**Filter 合并规则**：手动选择的优先级高于 NLP 提取。如果用户手动选了时间范围，NLP 提取的时间不生效。

### MainScreen 变更

- 新增 `ExtractedFilterBar` 组件，绑定 `vm.extractedFilter` 状态
- `doSearch()` 中调用 `vm.search()` 后，UI 自动显示提取标签
- 用户点击标签 × 时，调用 `vm.clearExtractedFilter(type)` 清除对应提取条件

### 新增 StateFlow

```kotlin
data class ExtractedFilter(
    val timeRange: TimeRange? = null,
    val locationBounds: LocationBounds? = null,
    val locationName: String? = null,
    val remainingText: String = "",
)

private val _extractedFilter = MutableStateFlow(ExtractedFilter())
val extractedFilter: StateFlow<ExtractedFilter> = _extractedFilter

fun clearExtractedTime() { _extractedFilter.value = _extractedFilter.value.copy(timeRange = null) }
fun clearExtractedLocation() { _extractedFilter.value = _extractedFilter.value.copy(locationBounds = null, locationName = null) }
```

---

## 边界情况

1. **纯提取词无剩余文本**："去年夏天"→ 剩余文本为空 → 用原文本作为 CLIP 查询
2. **无匹配结果**：不显示 ExtractedFilterBar，正常走 CLIP 全文搜索
3. **提取冲突**：用户已手动选时间 → NLP 提取的时间不生效（手动优先）
4. **重复匹配**：用户说"北京的北京烤鸭"→ 只匹配一次"北京"
5. **误匹配**：用户说"我喜欢北京烤鸭"→ 匹配到"北京"但用户可能不想要地点筛选 → 用户可点击 × 取消

---

## 测试策略

- `TimeExpressionParser` JVM 单元测试：覆盖所有时间词 + 组合 + 边界情况
- `LocationMatcher` JVM 单元测试：覆盖精确匹配、别名匹配、子串匹配、无匹配
- `NlpFilterExtractor` JVM 单元测试：覆盖完整输入 → 提取结果验证
- 无需 instrumented test（Compose UI 变更简单）
