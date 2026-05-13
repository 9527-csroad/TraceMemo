# 地名替换 + 场景芯片筛选 设计文档

**日期**: 2026-05-13
**状态**: 待实现

## 背景

PicSearch 已具备离线逆地理编码（ReverseGeocoder）和 CLIP 场景分类（SceneClassifier），但两者在筛选流程中存在断裂：

1. **位置集群显示原始坐标** — `LocationCluster.displayName` 输出 "约 39.9°N, 116.4°E · 12 张"，用户无法理解
2. **场景标签筛选完全不可用** — `selectedScenes` 永远为空；DAO 有 `listFeaturesBySceneTag()` 但是死代码；主查询 SQL 无场景子句；UI 无场景筛选面板

## 方案选择

**方案 B：预计算地名** — 在 `loadClusters()` 时一次性调用 ReverseGeocoder 为每个集群生成 `readableName`，存入数据类字段。场景筛选一步到位。

选择理由：避免 UI 上坐标→地名的闪烁；ReverseGeocoder 在 ViewModel init 时已初始化，只需调整 init 顺序即可。

## 设计

### Section 1：位置集群地名替换

**目标**: `LocationCluster.displayName` 从坐标格式变为可读地名

**改动文件**:

- `SearchFilter.kt` — `LocationCluster` 新增 `readableName: String?` 字段。`displayName` 优先返回 `readableName`，null 时回退到坐标格式：
  ```kotlin
  val displayName: String
      get() = readableName ?: "约 %.1f°%s, %.1f°%s · %d 张".format(abs(centerLat), latDir, abs(centerLon), lonDir, count)
  ```
- `MainViewModel.kt` — `loadClusters()` 中为每个集群调用 `ReverseGeocoder.lookup(centerLat, centerLon)`，结果赋给 `readableName`
- `MainViewModel.kt` — Init 顺序调整：ReverseGeocoder 初始化移到 `loadClusters()` 之前

**Fallback**: ReverseGeocoder 无法识别的区域（海洋、未覆盖地区），`readableName = null`，displayName 回退到坐标格式。

### Section 2：场景芯片筛选 UI

**目标**: 在 SearchFilterPanel 中新增场景标签多选芯片行

**改动文件**:

- `MainViewModel.kt` — 新增 `sceneLabels: StateFlow<List<String>>`，暴露 `SceneClassifier.SCENES` 的 10 个 displayName
- `MainScreen.kt` — `selectedScenes` 状态绑定芯片选中逻辑（toggle in/out）
- 新增 Composable `SceneFilterChipRow`（放在 `ui/component/` 或内联在 `SearchFilterPanel` 中）：
  - 接收 `sceneLabels`, `selectedScenes`, `onSceneToggle`
  - Material 3 `FilterChip`，选中态高亮，未选中态灰色
  - `LazyRow` 水平排列，可滑动查看
- `MainScreen.kt` — 把 `sceneLabels`, `selectedScenes`, `onSceneToggle` 传入 `SearchFilterPanel`
- `ActiveFilterTags` — 新增场景标签的清除显示（点击可移除单个）

**交互逻辑**: 多选 OR — 选 "风景" + "海滩水景"，搜索返回匹配任一标签的图片。

### Section 3：场景筛选逻辑

**目标**: 场景标签筛选真正影响搜索结果

**方案选择**: Kotlin post-filter（不在 SQL 层做场景筛选）

理由：Room `@Query` 不支持动态数量的 OR 子句参数。使用 `@RawQuery` 会失去编译时检查。数据量通常几百到几千行，在 Kotlin 层 `filter` 性能完全足够。场景筛选是 OR 逻辑（放宽范围），先让 SQL 返回时间+地点过滤后的候选集，再在 Kotlin 层按 sceneTags 过滤。

**改动文件**:

- `MainViewModel.kt` — `search()` 方法中，在 SQL 返回结果后增加 Kotlin 层场景过滤：
  ```kotlin
  val rows = repo.listFeaturesFiltered(filter)
  val filteredRows = if (filter.sceneTags.isEmpty()) rows
      else rows.filter { row ->
          val tags = row.sceneTags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
          filter.sceneTags.any { tag -> tags.contains(tag) }
      }
  ```
  后续对 `filteredRows` 计算 cosine similarity 并排序。

- `ImageDao.kt` — 不改动。`listFeaturesBySceneTag()` 死代码保留（后续清理）。
- `ImageRepository.kt` — 不改动。

**逻辑**: OR — 多选场景匹配任一标签即保留，符合用户直觉。

**性能**: SQL 先过滤时间+地点（通常几百到几千行），Kotlin 再按 sceneTags 过滤，O(n*m) 但 n 小，m 最大 10，性能无忧。

## 改动文件清单

| 文件 | 改动 |
|------|------|
| `SearchFilter.kt` | `LocationCluster` 新增 `readableName` 字段，`displayName` 逻辑更新 |
| `MainViewModel.kt` | Init 顺序调整 + `loadClusters()` 地名预计算 + `sceneLabels` StateFlow + `search()` 场景 post-filter |
| `MainScreen.kt` | `selectedScenes` toggle 绑定 + 传参到 SearchFilterPanel + ActiveFilterTags 场景标签 |
| `SearchFilterPanel`（内联在 MainScreen 或独立组件） | 新增 `SceneFilterChipRow` |

**不改的文件**: `ImageDao.kt`, `ImageRepository.kt`, `ImageGrid.kt`, `ImageDetailSheet.kt`

## 不做的事（YAGNI）

- 不改网格缩略图（不加地名/第2个场景标签）
- 不加地图可视化
- 不改详情页
- 不改时间筛选逻辑
- 不删 `listFeaturesBySceneTag()` 死代码（留待后续清理）