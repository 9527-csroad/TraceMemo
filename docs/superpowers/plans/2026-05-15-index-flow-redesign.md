# 索引流程重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将全屏索引遮罩改为 Header Pill 指示器，重构首次/再次启动流程，修复索引按钮逻辑。

**Architecture:** 移除 `workRunning` 全屏遮罩，新增 `IndexChoicePage`（首次选择页）和 `HeaderIndexPill`（状态指示器）。ViewModel 新增 `totalPhotoCount` 首次扫描 MediaStore 总数、`isIndexComplete` 完成判断、`continueIndexing()` 智能 Worker 入队。索引页改为可退出的独立页面。

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow, WorkManager, Room, MediaStore

---

## File Structure

| 文件 | 操作 | 职责 |
|------|------|------|
| `ui/component/HeaderIndexPill.kt` | 新建 | Header 右侧索引状态指示器，三种状态（idle/indexing/done） |
| `ui/component/IndexChoicePage.kt` | 新建 | 首次启动索引选择页面（快速 Top 100 / 索引全部 + 显示总数） |
| `ui/component/IndexProgressView.kt` | 修改 | 改为可退出的独立页面，增加返回按钮和 onDismiss 回调 |
| `MainViewModel.kt` | 修改 | 新增 totalPhotoCount, isIndexComplete, continueIndexing(), hasShownResumeDialog |
| `ui/screen/MainScreen.kt` | 修改 | 移除全屏遮罩，新增 Header Pill，新增选择页/弹窗逻辑，移除底部按钮 |

---

### Task 1: 新建 HeaderIndexPill 组件

**Files:**
- Create: `app/src/main/java/com/example/picsearch/ui/component/HeaderIndexPill.kt`

- [ ] **Step 1: 创建 HeaderIndexPill 组件**

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picsearch.ui.theme.AccentGreen
import com.example.picsearch.ui.theme.Primary

sealed class IndexPillState {
    data object Idle : IndexPillState()
    data class Indexing(val indexed: Int, val total: Int) : IndexPillState()
    data object Done : IndexPillState()
}

@Composable
fun HeaderIndexPill(
    state: IndexPillState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "indexPillState",
    ) { pillState ->
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .then(
                    when (pillState) {
                        is IndexPillState.Idle -> Modifier.background(Primary)
                        is IndexPillState.Indexing -> Modifier.background(Color(0xFF1a1a1a))
                        is IndexPillState.Done -> Modifier.background(Color(0xFF1a3320))
                    }
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (pillState) {
                is IndexPillState.Idle -> {
                    Text(
                        text = "开始索引",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = " ▸",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                    )
                }
                is IndexPillState.Indexing -> {
                    // Breathing dot
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(AccentGreen, CircleShape),
                    )
                    Text(
                        text = " ${pillState.indexed} / ${pillState.total}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = " ▸",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                    )
                }
                is IndexPillState.Done -> {
                    Text(
                        text = "✓ 已完成",
                        color = AccentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew app:compileDebugKotlin --rerun-tasks
```

Expected: BUILD SUCCESSFUL

---

### Task 2: 新建 IndexChoicePage 组件

**Files:**
- Create: `app/src/main/java/com/example/picsearch/ui/component/IndexChoicePage.kt`

- [ ] **Step 1: 创建 IndexChoicePage 组件**

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picsearch.ui.theme.Primary
import java.text.NumberFormat

@Composable
fun IndexChoicePage(
    totalCount: Int,
    onQuickIndex: () -> Unit,
    onFullIndex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "开始索引照片",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "需要读取手机照片权限\n所有处理在本机完成，隐私安全",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            if (totalCount > 0) {
                Text(
                    text = "检测到 ${NumberFormat.getInstance().format(totalCount)} 张照片",
                    style = MaterialTheme.typography.titleMedium,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(24.dp))
            }

            // Quick index button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onQuickIndex)
                    .background(Primary)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "快速索引 Top 100",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "约 1-2 分钟，先用起来",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Full index button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onFullIndex)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "索引全部照片",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "后台运行，可中断",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew app:compileDebugKotlin --rerun-tasks
```

Expected: BUILD SUCCESSFUL

---

### Task 3: 修改 IndexProgressView 为可退出页面

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/component/IndexProgressView.kt`

- [ ] **Step 1: 添加 onDismiss 回调和返回按钮**

在 `IndexProgressView` 函数签名中添加 `onDismiss: () -> Unit` 参数，在顶部增加返回按钮：

```kotlin
// 在 IndexProgressView 函数签名中新增:
@Composable
fun IndexProgressView(
    indexedCount: Int,
    totalCount: Int?,
    isQuickPhase: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material.icons.Icons.Default.ArrowBack
            Text(
                text = " 返回搜索",
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Subtitle: remind user indexing continues in background
        Text(
            text = "正在索引照片",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "可返回搜索，索引在后台继续",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        // ... rest of existing code unchanged ...
```

需要新增 import：

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
```

- [ ] **Step 2: 验证编译**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew app:compileDebugKotlin --rerun-tasks
```

Expected: BUILD SUCCESSFUL

---

### Task 4: MainViewModel 新增状态和方法

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`

- [ ] **Step 1: 新增 imports**

```kotlin
// 在已有 import 下方添加:
import android.provider.MediaStore
```

- [ ] **Step 2: 新增 StateFlow 属性**

在 `_extractedFilter` 下方添加：

```kotlin
private val _totalPhotoCount = MutableStateFlow(0)
val totalPhotoCount: StateFlow<Int> = _totalPhotoCount

private val _isIndexComplete = MutableStateFlow(false)
val isIndexComplete: StateFlow<Boolean> = _isIndexComplete

private val _hasShownResumeDialog = MutableStateFlow(false)
val hasShownResumeDialog: StateFlow<Boolean> = _hasShownResumeDialog
```

- [ ] **Step 3: 在 init 中获取 MediaStore 总数**

在 `clip.init(false)` 成功之后、`_ready.value = true` 之前，添加：

```kotlin
// Fetch total photo count from MediaStore (lightweight COUNT query)
var totalPhotos = 0
app.contentResolver.query(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    arrayOf(MediaStore.Images.Media._ID),
    null, null, null,
)?.use { cursor ->
    totalPhotos = cursor.count
}
_totalPhotoCount.value = totalPhotos
_isIndexComplete.value = totalPhotos > 0 && repo.count() >= totalPhotos
```

- [ ] **Step 4: 在轮询循环中更新 isIndexComplete**

在 `while (isActive)` 循环内，`_indexedCount.value = newCount` 之后添加：

```kotlin
_isIndexComplete.value = _totalPhotoCount.value > 0 && newCount >= _totalPhotoCount.value
```

- [ ] **Step 5: 新增 continueIndexing() 方法**

在 `startFullRebuild()` 方法之后添加：

```kotlin
fun continueIndexing() {
    val ctx = getApplication<Application>()
    val workManager = WorkManager.getInstance(ctx)
    val dbCount = repo.count()
    val total = _totalPhotoCount.value

    if (total > 0 && dbCount >= total) {
        // Already complete, do nothing
        return
    }

    if (dbCount < 100 || dbCount < total * 0.1f) {
        // Less than 100 or less than 10% → quick index first
        val quickRequest = androidx.work.OneTimeWorkRequestBuilder<QuickIndexWorker>().build()
        workManager.enqueueUniqueWork("quick_index", ExistingWorkPolicy.REPLACE, quickRequest)
    } else {
        // Already passed quick phase → continue full index
        val fullRequest = androidx.work.OneTimeWorkRequestBuilder<IndexWorker>().build()
        workManager.enqueueUniqueWork("full_index", ExistingWorkPolicy.REPLACE, fullRequest)
    }
}
```

需要新增 import：

```kotlin
import androidx.work.IndexWorker
```

等等，`IndexWorker` 不在 `androidx.work` 包，它在我们自己的 `worker` 包中。所以不需要额外 import，因为它已经在同一个包的 `worker` 子包中。但 `QuickIndexWorker` 已在文件顶部 import 了，所以 `IndexWorker` 也需要 import：

```kotlin
import com.example.picsearch.worker.IndexWorker
```

- [ ] **Step 6: 新增 markResumeDialogShown() 方法**

```kotlin
fun markResumeDialogShown() {
    _hasShownResumeDialog.value = true
}
```

- [ ] **Step 7: 验证编译**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew app:compileDebugKotlin --rerun-tasks
```

Expected: BUILD SUCCESSFUL

---

### Task 5: MainScreen 重构 — 移除全屏遮罩，整合新组件

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

这是最大的变更，分多个步骤。

- [ ] **Step 1: 新增 imports**

```kotlin
// 在现有 import 中添加:
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
```

已有的 `import androidx.compose.animation.AnimatedVisibility` 已存在，不需要重复。

- [ ] **Step 2: 新增 State 变量**

在 `var selectedImage by remember { mutableStateOf<ImageDetail?>(null) }` 之前添加：

```kotlin
// Index state
val totalPhotos by vm.totalPhotoCount.collectAsState()
val isIndexDone by vm.isIndexComplete.collectAsState()
val hasShownDialog by vm.hasShownResumeDialog.collectAsState()
var showIndexChoice by remember { mutableStateOf(false) }
var showIndexProgress by remember { mutableStateOf(false) }
var showResumeDialog by remember { mutableStateOf(false) }
var showIndexDetailFromPill by remember { mutableStateOf(false) }
```

- [ ] **Step 3: 移除 workRunning 相关代码**

删除以下代码块：

```kotlin
// DELETE these lines:
val quickWorkInfos by WorkManager.getInstance(ctx)
    .getWorkInfosForUniqueWorkLiveData("quick_index")
    .observeAsState()
val fullWorkInfos by WorkManager.getInstance(ctx)
    .getWorkInfosForUniqueWorkLiveData("full_index")
    .observeAsState()
val workRunning = (quickWorkInfos?.firstOrNull()?.let {
    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
} ?: false) || (fullWorkInfos?.firstOrNull()?.let {
    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
} ?: false)
```

改为使用 `fullProgress` 判断索引是否在进行：

```kotlin
val isIndexing = fullProgress != null
```

- [ ] **Step 4: 重写空状态/首次启动逻辑**

将 `if (count == 0 && !workRunning)` 及其后的 `EmptyStateView` 块替换为：

```kotlin
// First launch: show index choice page when DB is empty
if (count == 0 && !isIndexing) {
    if (totalPhotos > 0) {
        IndexChoicePage(
            totalCount = totalPhotos,
            onQuickIndex = {
                showIndexChoice = false
                startIndexWithPermission()
            },
            onFullIndex = {
                showIndexChoice = false
                // Enqueue full index directly
                val fullRequest = androidx.work.OneTimeWorkRequestBuilder<IndexWorker>().build()
                androidx.work.WorkManager.getInstance(ctx).enqueueUniqueWork(
                    "full_index",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    fullRequest,
                )
                showIndexProgress = true
            },
        )
        return
    }
    // No photos detected yet — show empty state
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                vm.refreshCount()
                delay(500)
                isRefreshing = false
            }
        },
    ) {
        EmptyStateView(
            title = "还没有索引照片",
            description = "开始索引你的照片，然后用文字描述就能找到它们。所有处理都在本机完成，隐私安全。",
            actionText = "开始索引",
            onAction = { startIndexWithPermission() },
        )
    }
    return
}
```

需要新增 import：

```kotlin
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
```

同时需要 import IndexWorker：

```kotlin
import com.example.picsearch.worker.IndexWorker
```

- [ ] **Step 5: 添加 Header Pill 到 Header 区域**

在 Header Column 中，将现有的：

```kotlin
Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
    Text(text = "PicSearch", ...)
    Text(text = "用文字找到你的照片", ...)
}
```

替换为：

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top,
) {
    Column {
        Text(
            text = "PicSearch",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "用文字找到你的照片",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val pillState = when {
        isIndexDone -> IndexPillState.Done
        isIndexing -> IndexPillState.Indexing(fullProgress?.first ?: count, fullProgress?.second ?: totalPhotos.coerceAtLeast(count))
        count == 0 -> IndexPillState.Idle
        else -> IndexPillState.Idle
    }
    HeaderIndexPill(
        state = pillState,
        onClick = {
            when (pillState) {
                is IndexPillState.Idle -> { startIndexWithPermission() }
                is IndexPillState.Indexing -> { showIndexProgress = true }
                is IndexPillState.Done -> { /* tap done pill to re-index */ startIndexWithPermission() }
            }
        },
    )
}
```

- [ ] **Step 6: 移除底部索引按钮**

删除整个底部按钮区块（`// Bottom index button / progress` 注释后的 `Surface` 及其内容，从大约 line 386 到 439）：

```kotlin
// DELETE the entire Surface block:
// Surface(modifier = Modifier.fillMaxWidth()...) { ... }
```

- [ ] **Step 7: 替换全屏遮罩为可退出的索引进度页**

将原有的 `if (workRunning) { IndexProgressView(...) }` 遮罩块替换为：

```kotlin
// Index progress as a dismissible page
if (showIndexProgress) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        val (overlayIndexed, overlayTotal) = fullProgress ?: (count to totalPhotos)
        val isQuickPhase = fullProgress == null && count < 100
        IndexProgressView(
            indexedCount = overlayIndexed,
            totalCount = overlayTotal,
            isQuickPhase = isQuickPhase,
            onDismiss = { showIndexProgress = false },
        )
    }
    return
}
```

- [ ] **Step 8: 添加 resume dialog 逻辑**

在主 `PullToRefreshBox` 内容之后、`selectedImage` 的 detail sheet 之前，添加：

```kotlin
// Resume indexing dialog (shown on return launch if indexing incomplete)
if (showResumeDialog && !isIndexDone && !hasShownDialog) {
    val remaining = (totalPhotos - count).coerceAtLeast(0)
    AlertDialog(
        onDismissRequest = {
            showResumeDialog = false
            vm.markResumeDialogShown()
        },
        title = { Text("继续索引？") },
        text = {
            Column {
                Text("您还有 ${remaining} 张照片未完成索引。是否继续索引？")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "退出 App 将停止索引",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                showResumeDialog = false
                vm.markResumeDialogShown()
                vm.continueIndexing()
            }) {
                Text("继续索引")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                showResumeDialog = false
                vm.markResumeDialogShown()
            }) {
                Text("稍后")
            }
        },
    )
}
```

- [ ] **Step 9: 在首次进入时触发 resume dialog**

在 `startIndexWithPermission()` 函数之后、`PullToRefreshBox` 之前添加：

```kotlin
// Show resume dialog on return launch when indexing is incomplete
if (count > 0 && !isIndexing && !isIndexDone && !hasShownDialog) {
    LaunchedEffect(Unit) {
        showResumeDialog = true
    }
}
```

需要新增 import：

```kotlin
import androidx.compose.runtime.LaunchedEffect
```

- [ ] **Step 10: 移除不再需要的 import**

如果 `WorkInfo` 不再被使用（Step 3 中我们替换了 workRunning），删除：

```kotlin
// DELETE: import androidx.work.WorkInfo
```

- [ ] **Step 11: 验证编译**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew app:assembleDebug
```

Expected: BUILD SUCCESSFUL

---

## Self-Review

**1. Spec coverage check:**
- ✅ 首次启动显示索引选择页 (Task 2 + Task 5 Step 4)
- ✅ Top 100 完成后自动进入搜索页 (由 Worker 逻辑 + Task 5 控制，无需额外代码)
- ✅ Header Pill 三种状态 (Task 1 + Task 5 Step 5)
- ✅ 再次启动弹窗提示 (Task 4 + Task 5 Step 8-9)
- ✅ 关闭 App 停止索引 (Worker 由 WorkManager 管理，App 退出时 Worker 自然停止)
- ✅ 底部无按钮 (Task 5 Step 6)
- ✅ 索引页可退出 (Task 3)
- ✅ MediaStore 总数 COUNT 查询 (Task 4 Step 3)
- ✅ continueIndexing() 智能入队 (Task 4 Step 5)
- ✅ 索引选择页显示总数 (Task 2)

**2. Placeholder scan:** 无 TBD/TODO。所有代码步骤包含完整代码。

**3. Type consistency:**
- `IndexPillState` sealed class 在 Task 1 定义，Task 5 Step 5 中使用 — 一致
- `fullProgress` 类型为 `Pair<Int, Int>?` 已存在，用法一致
- `totalPhotoCount` 为 `StateFlow<Int>`，Task 4 定义，Task 5 Step 2 中 collectAsState — 一致
- `continueIndexing()` 在 Task 4 定义，Task 5 Step 8 中调用 — 一致
- `IndexWorker` 需要在 MainScreen 中 import（Task 5 Step 4）

**4. One gap found:** Task 5 Step 5 中 `IndexPillState` 是 `com.example.picsearch.ui.component.IndexPillState`，需要在 MainScreen 中 import。补充到 Task 5 Step 1 的 import 列表中：

```kotlin
import com.example.picsearch.ui.component.IndexPillState
```
