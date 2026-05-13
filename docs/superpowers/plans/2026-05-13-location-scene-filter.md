# 地名替换 + 场景芯片筛选 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让位置筛选显示可读地名代替坐标数字，让场景标签筛选真正可用（芯片多选 + Kotlin post-filter）。

**Architecture:** 预计算方案 — `loadClusters()` 时调用 ReverseGeocoder 为每个集群生成 `readableName`；场景筛选用 Material3 FilterChip 芯片行 + Kotlin 层 post-filter（不改 SQL）。

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Room DAO

---

### Task 1: LocationCluster 新增 readableName 字段

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/data/SearchFilter.kt:36-51`

- [ ] **Step 1: 给 LocationCluster 添加 readableName 参数并改 displayName**

当前 `LocationCluster` (SearchFilter.kt:36-51):
```kotlin
data class LocationCluster(
    val latBucket: Double,
    val lonBucket: Double,
    val centerLat: Double,
    val centerLon: Double,
    val count: Int,
) {
    val displayName: String
        get() {
            val latDir = if (centerLat >= 0) "N" else "S"
            val lonDir = if (centerLon >= 0) "E" else "W"
            return "约 %.1f°%s, %.1f°%s · %d 张".format(
                abs(centerLat), latDir, abs(centerLon), lonDir, count,
            )
        }
}
```

改为:
```kotlin
data class LocationCluster(
    val latBucket: Double,
    val lonBucket: Double,
    val centerLat: Double,
    val centerLon: Double,
    val count: Int,
    val readableName: String? = null,
) {
    val displayName: String
        get() = readableName ?: run {
            val latDir = if (centerLat >= 0) "N" else "S"
            val lonDir = if (centerLon >= 0) "E" else "W"
            "约 %.1f°%s, %.1f°%s · %d 张".format(
                abs(centerLat), latDir, abs(centerLon), lonDir, count,
            )
        }
}
```

- [ ] **Step 2: Build 验证编译通过**

Run: `.\gradlew app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/picsearch/data/SearchFilter.kt
git commit -m "feat: add readableName field to LocationCluster with fallback"
```

---

### Task 2: loadClusters 预计算地名

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt:118-129`

- [ ] **Step 1: 改 loadClusters() 为每个集群调用 ReverseGeocoder.lookup**

当前 `loadClusters()` (MainViewModel.kt:118-129):
```kotlin
private suspend fun loadClusters() {
    _clusters.value = repo.listLocationClusters().map { row ->
        LocationCluster(
            latBucket = row.latBucket,
            lonBucket = row.lonBucket,
            centerLat = row.centerLat,
            centerLon = row.centerLon,
            count = row.count,
        )
    }
    _unlocatedCount.value = repo.countUnlocated()
}
```

改为:
```kotlin
private suspend fun loadClusters() {
    _clusters.value = repo.listLocationClusters().map { row ->
        val name = ReverseGeocoder.lookup(row.centerLat, row.centerLon)
        LocationCluster(
            latBucket = row.latBucket,
            lonBucket = row.lonBucket,
            centerLat = row.centerLat,
            centerLon = row.centerLon,
            count = row.count,
            readableName = name,
        )
    }
    _unlocatedCount.value = repo.countUnlocated()
}
```

- [ ] **Step 2: Build 验证编译通过**

Run: `.\gradlew app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/picsearch/MainViewModel.kt
git commit -m "feat: precompute readable location names in loadClusters via ReverseGeocoder"
```

---

### Task 3: ViewModel 暴露 sceneLabels StateFlow

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`

- [ ] **Step 1: 新增 sceneLabels StateFlow**

在 MainViewModel.kt 中，在 `_workProgress` 定义后（约 line 59），新增:
```kotlin
private val _sceneLabels = MutableStateFlow<List<String>>(emptyList())
val sceneLabels: StateFlow<List<String>> = _sceneLabels
```

在 init block 中，`sceneClassifier.initialize()` 之后（约 line 87），新增:
```kotlin
_sceneLabels.value = SceneClassifier.SCENES.map { it.displayName }
```

- [ ] **Step 2: Build 验证编译通过**

Run: `.\gradlew app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/picsearch/MainViewModel.kt
git commit -m "feat: expose sceneLabels StateFlow from SceneClassifier"
```

---

### Task 4: search() 添加场景 post-filter

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt:131-188`

- [ ] **Step 1: 在 search() 中添加 Kotlin 层场景过滤**

当前 search() (MainViewModel.kt:131-165) 中，`val rows = repo.listFeaturesFiltered(filter)` 之后直接遍历 rows 计算 cosine similarity。

在 `val rows = repo.listFeaturesFiltered(filter)` 之后（约 line 146），插入场景 post-filter:
```kotlin
val rows = repo.listFeaturesFiltered(filter)
val filteredRows = if (filter.sceneTags.isEmpty()) rows
    else rows.filter { row ->
        val tags = row.sceneTags
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        filter.sceneTags.any { tag -> tags.contains(tag) }
    }
```

然后把后续的 `for (r in rows)` 改为 `for (r in filteredRows)`:
```kotlin
for (r in filteredRows) {
    val fv = FloatCodec.fromBytes(r.feature)
    ...
}
```

- [ ] **Step 2: Build 验证编译通过**

Run: `.\gradlew app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/picsearch/MainViewModel.kt
git commit -m "feat: add scene tag post-filter in search() using Kotlin layer OR logic"
```

---

### Task 5: SearchFilterPanel 新增场景芯片行

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/component/SearchFilterPanel.kt`

- [ ] **Step 1: 给 SearchFilterPanel 添加场景相关参数**

当前签名 (SearchFilterPanel.kt:77-85):
```kotlin
fun SearchFilterPanel(
    timeRange: TimeRange?,
    onTimeRangeChange: (TimeRange?) -> Unit,
    selectedCluster: LocationCluster?,
    onClusterChange: (LocationCluster?) -> Unit,
    clusters: List<LocationCluster>,
    unlocatedCount: Int,
    modifier: Modifier = Modifier,
)
```

改为:
```kotlin
fun SearchFilterPanel(
    timeRange: TimeRange?,
    onTimeRangeChange: (TimeRange?) -> Unit,
    selectedCluster: LocationCluster?,
    onClusterChange: (LocationCluster?) -> Unit,
    clusters: List<LocationCluster>,
    unlocatedCount: Int,
    sceneLabels: List<String>,
    selectedScenes: List<String>,
    onSceneToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: 在地点筛选后面添加场景芯片 UI 段**

在 SearchFilterPanel 的 Column 中，地点筛选段（约 line 186 的 `}` 之后），新增:
```kotlin
Spacer(Modifier.padding(top = 10.dp))
Text(
    "场景",
    fontWeight = FontWeight.SemiBold,
    style = MaterialTheme.typography.labelLarge,
)
Spacer(Modifier.padding(top = 4.dp))
Row(
    modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    sceneLabels.forEach { label ->
        FilterChip(
            selected = label in selectedScenes,
            onClick = { onSceneToggle(label) },
            label = { Text(label) },
        )
    }
    if (selectedScenes.isNotEmpty()) {
        TextButton(onClick = {
            selectedScenes.forEach { onSceneToggle(it) }
        }) {
            Text("×")
        }
    }
}
```

注意: 这段场景 UI 的"全部清除"按钮调用了 `selectedScenes.forEach { onSceneToggle(it) }`，逐个 toggle 来清空。因为 `onSceneToggle` 是 toggle in/out 语义，选中时 toggle 就移除。

- [ ] **Step 3: Build 验证编译通过**

Run: `.\gradlew app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/picsearch/ui/component/SearchFilterPanel.kt
git commit -m "feat: add scene filter chip row to SearchFilterPanel"
```

---

### Task 6: MainScreen 传参 + selectedScenes toggle

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

- [ ] **Step 1: 从 ViewModel 收集 sceneLabels**

在 MainScreen.kt 中，`val clusters by vm.clusters.collectAsState()` 之后（约 line 74），新增:
```kotlin
val sceneLabels by vm.sceneLabels.collectAsState()
```

- [ ] **Step 2: 给 selectedScenes 添加 toggle 函数**

在 `var selectedScenes by remember { mutableStateOf<List<String>>(emptyList()) }` 之后（约 line 83），新增 toggle lambda:
```kotlin
val onSceneToggle: (String) -> Unit = { label ->
    selectedScenes = if (label in selectedScenes)
        selectedScenes - label
    else
        selectedScenes + label
}
```

- [ ] **Step 3: 把新参数传给 SearchFilterPanel**

当前 SearchFilterPanel 调用 (MainScreen.kt:229-237):
```kotlin
SearchFilterPanel(
    timeRange = timeRange,
    onTimeRangeChange = { timeRange = it },
    selectedCluster = selectedCluster,
    onClusterChange = { selectedCluster = it },
    clusters = clusters,
    unlocatedCount = unlocatedCount,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
)
```

改为:
```kotlin
SearchFilterPanel(
    timeRange = timeRange,
    onTimeRangeChange = { timeRange = it },
    selectedCluster = selectedCluster,
    onClusterChange = { selectedCluster = it },
    clusters = clusters,
    unlocatedCount = unlocatedCount,
    sceneLabels = sceneLabels,
    selectedScenes = selectedScenes,
    onSceneToggle = onSceneToggle,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
)
```

- [ ] **Step 4: Build 验证编译通过**

Run: `.\gradlew app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt
git commit -m "feat: wire scene filter state and pass params to SearchFilterPanel"
```

---

### Task 7: ActiveFilterTags 显示场景标签清除

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ActiveFilterTags.kt`

- [ ] **Step 1: 给 ActiveFilterTags 添加场景相关参数**

当前签名 (ActiveFilterTags.kt:23-30):
```kotlin
fun ActiveFilterTags(
    filter: SearchFilter,
    selectedCluster: LocationCluster?,
    onClearTime: () -> Unit,
    onClearLocation: () -> Unit,
    onOpenFilterPanel: () -> Unit,
    modifier: Modifier = Modifier,
)
```

改为:
```kotlin
fun ActiveFilterTags(
    filter: SearchFilter,
    selectedCluster: LocationCluster?,
    onClearTime: () -> Unit,
    onClearLocation: () -> Unit,
    onClearScene: (String) -> Unit,
    onOpenFilterPanel: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: 在 ActiveFilterTags Row 中添加场景标签渲染**

在 `selectedCluster?.let { ... }` 之后（约 line 42），新增:
```kotlin
filter.sceneTags.forEach { tag ->
    FilterTag(label = tag, onClear = { onClearScene(tag) })
}
```

- [ ] **Step 3: 在 MainScreen 中给 ActiveFilterTags 传 onClearScene**

当前 ActiveFilterTags 调用 (MainScreen.kt:210-217):
```kotlin
ActiveFilterTags(
    filter = filter,
    selectedCluster = selectedCluster,
    onClearTime = { timeRange = null },
    onClearLocation = { selectedCluster = null },
    onOpenFilterPanel = { showFilterPanel = !showFilterPanel },
)
```

改为:
```kotlin
ActiveFilterTags(
    filter = filter,
    selectedCluster = selectedCluster,
    onClearTime = { timeRange = null },
    onClearLocation = { selectedCluster = null },
    onClearScene = { tag -> onSceneToggle(tag) },
    onOpenFilterPanel = { showFilterPanel = !showFilterPanel },
)
```

- [ ] **Step 4: Build 验证编译通过**

Run: `.\gradlew app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/picsearch/ui/component/ActiveFilterTags.kt app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt
git commit -m "feat: show scene tags in ActiveFilterTags with individual clear"
```

---

### Task 8: 最终集成验证

- [ ] **Step 1: 完整 build 验证**

Run: `.\gradlew app:assembleDebug`
Expected: BUILD SUCCESSFUL, 无编译错误

- [ ] **Step 2: 代码自检 — 确认所有改动完整**

检查清单:
1. `SearchFilter.kt` — `LocationCluster.readableName` 字段存在，`displayName` 有 fallback
2. `MainViewModel.kt` — `loadClusters()` 调用 `ReverseGeocoder.lookup()`；`sceneLabels` StateFlow 存在；`search()` 有 `filteredRows` post-filter
3. `SearchFilterPanel.kt` — 有 `sceneLabels`, `selectedScenes`, `onSceneToggle` 参数和场景芯片 UI
4. `MainScreen.kt` — `sceneLabels` collected；`onSceneToggle` lambda；SearchFilterPanel 传新参数；ActiveFilterTags 传 `onClearScene`
5. `ActiveFilterTags.kt` — 有 `onClearScene` 参数和场景标签渲染