# PicSearch UI "温暖画廊" 优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 PicSearch UI 升级为温暖画廊风格 — 暖色调配色、双列网格恢复、图标专业化、详情弹窗增强

**Architecture:** 所有改动仅限 UI 层 Compose 组件。配色值在 `Color.kt` 集中修改，图标替换通过 `androidx.compose.material.icons`，详情滑动使用 Compose Foundation 的 `HorizontalPager`。不涉及数据层、ML 层或 ViewModel。

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Compose Foundation (HorizontalPager), Material Icons, Coil

---

## 文件变更总览

| 文件 | 操作 | 说明 |
|------|------|------|
| `ui/theme/Color.kt` | 修改 | 6 个色值调整为暖色调 |
| `ui/screen/MainScreen.kt` | 修改 | 搜索栏 shadow、图标替换、间距调整 |
| `ui/component/ImageGrid.kt` | 修改 | 间距/圆角/浮层/动画优化 |
| `ui/component/ImageDetailSheet.kt` | 修改 | 图片原始比例、HorizontalPager、FlowRow、元数据重排 |
| `ui/component/FilterEntryCard.kt` | 修改 | Emoji → Material Icons |
| `ui/component/ActiveFilterTags.kt` | 修改 | Emoji → 纯文本 |
| `ui/component/EmptyStateView.kt` | 修改 | Emoji → Material Icon |
| `ui/component/SkeletonPlaceholder.kt` | 修改 | 圆角 medium → 16dp 同步 |

---

### Task 1: 暖色调配色

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/theme/Color.kt`

- [ ] 修改 `Color.kt` 中以下色值：

```kotlin
val Primary = Color(0xFF1C1917)           // #18181B → #1C1917 暖深灰
val Secondary = Color(0xFF292524)         // #27272A → #292524 暖深灰
val TextPrimary = Color(0xFF0C0A09)       // #09090B → #0C0A09 暖黑
val TextSecondary = Color(0xFF6B6560)     // #71717A → #6B6560 暖灰
val BorderColor = Color(0xFFE8E4DF)       // #E4E4E7 → #E8E4DF 暖灰
val Background = Color(0xFFF8F6F3)        // #FAFAFA → #F8F6F3 暖米白
val SurfaceWhite = Color(0xFFFFFEFC)      // #FFFFFF → #FFFEFC 暖白
val SurfaceFaint = Color(0xFFF2F0ED)      // #F4F4F5 → #F2F0ED 暖浅灰
```

其余色值（AccentGreen、ScoreBg、SceneTagBg、TextMuted）保持不变。

- [ ] 验证编译: `PowerShell('$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew app:assembleDebug')`
- [ ] 提交: `git add app/src/main/java/com/example/picsearch/ui/theme/Color.kt && git commit -m "ui: adjust color palette to warm tones"`

---

### Task 2: 过滤器图标替换 (FilterEntryCard)

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/component/FilterEntryCard.kt`

- [ ] 添加 Material Icons 导入，将 `FilterEntryCard` 的 `icon: String` 参数改为 `icon: ImageVector`，替换 Emoji 为 SVG 图标：

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FilterEntryRow(
    onTimeClick: () -> Unit,
    onLocationClick: () -> Unit,
    onSceneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FilterEntryCard(
            icon = androidx.compose.material.icons.Icons.Outlined.CalendarMonth,
            label = "时间",
            onClick = onTimeClick,
            modifier = Modifier.weight(1f)
        )
        FilterEntryCard(
            icon = androidx.compose.material.icons.Icons.Outlined.Place,
            label = "地点",
            onClick = onLocationClick,
            modifier = Modifier.weight(1f)
        )
        FilterEntryCard(
            icon = androidx.compose.material.icons.Icons.Outlined.Label,
            label = "场景",
            onClick = onSceneClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FilterEntryCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
```

注意：`FilterEntryRow` 的 vertical padding 从 `4.dp` 改为 `8.dp`（搜索栏和过滤器之间间距增加）。`FilterEntryCard` 的 padding 从 `4.dp` 全向改为 `horizontal = 4.dp`（去掉 vertical padding，让 Row 的 padding 统一管理）。

- [ ] 修改 `MainScreen.kt` 中 `FilterEntryRow` 调用处，确认间距 — 在 `MainScreen.kt:235-239` 的 `FilterEntryRow` 调用保持不变，但去掉其上方多余的 `Spacer`（如果有的话）。实际上搜索框和过滤器之间已经在 `MainScreen.kt:220` 搜索框的 `padding(horizontal = 16.dp)` 后自然有间距，加上 `FilterEntryRow` 自身的 `vertical = 8.dp` 就够了。
- [ ] 验证编译
- [ ] 提交: `git commit -m "ui: replace emoji icons with Material Icons in filter row"`

---

### Task 3: 搜索栏精致化 (MainScreen)

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

- [ ] 搜索栏改动：
  1. 去掉 `TextFieldDefaults.colors` 中的 `focusedIndicatorColor` 和 `unfocusedIndicatorColor`（移除下划线指示器）
  2. 添加 `shadow(elevation = 2.dp, shape = RoundedCornerShape(12.dp))` 到 TextField 的 modifier
  3. 搜索按钮 Emoji → Material Icon

- [ ] 具体修改（在 `MainScreen.kt:181-220` 范围内）：

```kotlin
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.shadow

// Search bar
TextField(
    value = query,
    onValueChange = { query = it },
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .height(52.dp)
        .shadow(elevation = 2.dp, shape = RoundedCornerShape(12.dp)),
    placeholder = { Text("搜索 \"日落时的海滩\"...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(
        onSearch = {
            if (query.isNotBlank()) {
                vm.search(query.trim(), filter, topK = 30)
                hasSearched = true
            }
        },
    ),
    trailingIcon = {
        if (isSearching) {
            CircularProgressIndicator(
                modifier = Modifier.padding(4.dp).size(20.dp),
                strokeWidth = 2.dp,
            )
        } else if (query.isNotEmpty()) {
            IconButton(onClick = {
                vm.search(query.trim(), filter, topK = 30)
                hasSearched = true
            }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    },
    shape = RoundedCornerShape(12.dp),
    colors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    ),
)
```

- [ ] 搜索框和过滤器之间间距调整：在搜索框 TextField 的 `padding(horizontal = 16.dp)` 后，确保和 `FilterEntryRow` 之间有适当间距。由于 `FilterEntryRow` 已经有 `vertical = 8.dp`，且搜索框底部有 padding，间距应该自然合理。如果需要额外间距，在搜索框后加 `Spacer(Modifier.height(4.dp))`。

- [ ] 底部索引按钮图标替换：在 `MainScreen.kt:322-335`，将 `📸 索引照片` 的 Emoji 替换为 Material Icon：

```kotlin
import androidx.compose.material.icons.outlined.AddPhotoAlternate

// 在 Column 中图标上方添加：
Icon(
    imageVector = Icons.Outlined.AddPhotoAlternate,
    contentDescription = null,
    tint = Color.White,
    modifier = Modifier.size(24.dp),
)
Spacer(Modifier.height(4.dp))
```

- [ ] 验证编译
- [ ] 提交: `git commit -m "ui: refine search bar with shadow and SVG icons"`

---

### Task 4: 搜索结果双列网格优化 (ImageGrid)

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ImageGrid.kt`

- [ ] 改动列表：
  1. 间距：horizontal 6dp → 8dp，vertical 3dp → 8dp
  2. 圆角：`MaterialTheme.shapes.medium` → `16.dp`（硬编码）
  3. 场景标签：字号 10sp → 11sp，背景更透明白 + 1dp 暖灰边框，文字改用 theme color
  4. 相似度：字号 10sp → 11sp，加 fontWeight.Medium，背景更透明
  5. 入场动画：duration 300ms → 200ms

- [ ] 完整替换 `ImageGrid.kt`：

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.picsearch.MainViewModel.ImageScore

@Composable
fun ImageGrid(
    uris: List<ImageScore>,
    onImageClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (uris.isEmpty()) return

    Column(modifier = modifier) {
        val columns = 2
        val rowCount = (uris.size + columns - 1) / columns
        for (row in 0 until rowCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index < uris.size) {
                        AnimatedGridItem(index = index) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(
                                        indication = ripple(),
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { onImageClick(uris[index].uri) },
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(uris[index].uri)
                                        .size(512)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize(),
                                )
                                uris[index].sceneTags.takeIf { it.isNotEmpty() }?.let { tags ->
                                    Text(
                                        text = tags.first(),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .background(Color(0xF0FFFFFF), MaterialTheme.shapes.small)
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                                Text(
                                    text = "${(uris[index].score * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color(0xB3000000), MaterialTheme.shapes.small)
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedGridItem(index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = index * 40)) +
                slideInVertically(
                    initialOffsetY = { it / 5 },
                    animationSpec = tween(durationMillis = 200, delayMillis = index * 40),
                ),
    ) {
        content()
    }
}
```

注意：需要添加 `import androidx.compose.foundation.shape.RoundedCornerShape` 和 `import androidx.compose.material3.ripple`。

- [ ] 验证编译
- [ ] 提交: `git commit -m "ui: refine image grid spacing, rounding, and floating labels"`

---

### Task 5: ActiveFilterTags Emoji 清理

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ActiveFilterTags.kt`

- [ ] 将 `ActiveFilterTags` 中的 Emoji 标签改为纯文本（去掉 📅 和 📍）：

```kotlin
// 第 39 行：
FilterTag(label = formatTimeRange(range), onClear = onClearTime)
// 第 42 行：
FilterTag(label = cluster.displayName.take(8), onClear = onClearLocation)
```

- [ ] `FilterTag` 中的 " ✕" 清除符号保持不变（它是文本操作符，不是图标）

- [ ] 验证编译
- [ ] 提交: `git commit -m "ui: clean up emoji in active filter tags"`

---

### Task 6: 空状态图标替换 (EmptyStateView)

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/component/EmptyStateView.kt`

- [ ] 将 `EmptyStateView` 中的 `🔍` Emoji 替换为 Material Icon：

```kotlin
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera

// 替换第 51 行：
Icon(
    imageVector = Icons.Outlined.PhotoCamera,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.size(40.dp),
)
```

- [ ] 将 `actionText` 中的 Emoji（如 `📸 开始索引`）在调用处替换 — 实际上 `actionText` 是参数，由调用方传入。在 `MainScreen.kt:155` 将 `"📸 开始索引"` 改为 `"开始索引"`。

- [ ] 验证编译
- [ ] 提交: `git commit -m "ui: replace emoji with Material Icons in empty state"`

---

### Task 7: 详情弹窗增强 (ImageDetailSheet)

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ImageDetailSheet.kt`

这是改动最大的文件，需要：
1. 图片从 4:3 Crop → 原始比例 Fit
2. 添加 HorizontalPager 支持左右滑动
3. 场景标签 Row → FlowRow
4. 元数据 InfoGrid 改为垂直列表带图标
5. 添加 dragHandle

- [ ] 完整替换 `ImageDetailSheet.kt`：

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.picsearch.MainViewModel.ImageScore
import com.example.picsearch.util.ReverseGeocoder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImageDetail(
    val uri: String,
    val displayName: String?,
    val width: Int,
    val height: Int,
    val dateTaken: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val sceneTags: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageDetailSheet(
    detail: ImageDetail,
    onDismiss: () -> Unit,
    results: List<ImageScore>,
    modifier: Modifier = Modifier,
) {
    val initialIndex = results.indexOfFirst { it.uri == detail.uri }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) { results.size }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${pagerState.currentPage + 1} / ${results.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
        },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = modifier,
        ) { pageIndex ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val item = results[pageIndex]
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .size(1024)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                )

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color(0xFFE8E4DF),
                )

                if (item.sceneTags.isNotEmpty()) {
                    SceneTagsFlowRow(tags = item.sceneTags)
                }

                Text(
                    text = "照片信息",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )

                // Need ImageDetail data — we'll get it from the VM via the detail parameter
                // For now, pass the original detail when pageIndex matches initialIndex
                val currentDetail = if (pageIndex == initialIndex) detail else null
                MetadataList(currentDetail)
            }
        }
    }
}
```

等等，这里有个问题 — `ImageDetailSheet` 接收单个 `ImageDetail`，但滑动需要列表。同时 Metadata 需要每个图片的详细信息。让我重新思考架构。

实际上 `MainViewModel` 的 `results` 是 `List<ImageScore>`，而 `ImageDetail` 是通过 `vm.getImageDetail(uri)` 获取的。在滑动切换时，需要为每个索引获取对应的 `ImageDetail`。

更合理的做法：让 `ImageDetailSheet` 接收 `results: List<ImageScore>` 和当前索引，然后在内部通过 `getImageDetail` 获取每个项的详情。但这需要 ViewModel 引用，或者让调用方传入详情列表。

让我调整方案：传入 `results: List<ImageScore>` + 当前 `ImageDetail`，滑动时只显示基本信息（uri + sceneTags），详细元数据只显示当前选中项的。这样简化实现。

修正后的方案：

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.picsearch.MainViewModel.ImageScore
import com.example.picsearch.util.ReverseGeocoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImageDetail(
    val uri: String,
    val displayName: String?,
    val width: Int,
    val height: Int,
    val dateTaken: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val sceneTags: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageDetailSheet(
    results: List<ImageScore>,
    initialUri: String,
    detail: ImageDetail,
    onDismiss: () -> Unit,
    onDetailChange: (String) -> ImageDetail,
    modifier: Modifier = Modifier,
) {
    val initialIndex = results.indexOfFirst { it.uri == initialUri }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialIndex) { results.size }

    // 跟踪当前显示的 detail（滑动切换时更新）
    var currentDetail by remember { mutableStateOf(detail) }
    var currentUri by remember { mutableStateOf(initialUri) }

    // 页面切换时更新 detail
    LaunchedEffect(pagerState.currentPage) {
        val newUri = results[pagerState.currentPage].uri
        if (newUri != currentUri) {
            currentUri = newUri
            currentDetail = onDetailChange(newUri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${pagerState.currentPage + 1} / ${results.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
        },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = modifier,
        ) { pageIndex ->
            val item = results[pageIndex]
            val showFullDetail = (item.uri == currentUri)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .size(1024)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                )

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color(0xFFE8E4DF),
                )

                if (item.sceneTags.isNotEmpty()) {
                    SceneTagsFlowRow(tags = item.sceneTags)
                }

                if (showFullDetail) {
                    Text(
                        text = "照片信息",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    MetadataList(detail = currentDetail)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneTagsFlowRow(tags: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        tags.forEach { tag ->
            Text(
                text = tag,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MetadataList(detail: ImageDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetadataItem(
            icon = androidx.compose.material.icons.Icons.Outlined.AccessTime,
            label = "拍摄时间",
            value = detail.dateTaken?.let { formatTimestamp(it) } ?: "未知",
        )
        MetadataItem(
            icon = androidx.compose.material.icons.Icons.Outlined.Place,
            label = "拍摄地点",
            value = formatLocation(detail.latitude, detail.longitude),
        )
        MetadataItem(
            icon = androidx.compose.material.icons.Icons.Outlined.ImageAspectRatio,
            label = "尺寸",
            value = "${detail.width} × ${detail.height}",
        )
        MetadataItem(
            icon = androidx.compose.material.icons.Icons.Outlined.InsertDriveFile,
            label = "文件名",
            value = detail.displayName ?: "未知",
        )
    }
}

@Composable
private fun MetadataItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = value,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}

private fun formatLocation(lat: Double?, lon: Double?): String {
    if (lat == null || lon == null) return "未知位置"
    val address = ReverseGeocoder.lookup(lat, lon)
    return address ?: "未知位置"
}
```

**签名变更说明：**
- `results: List<ImageScore>` — 搜索结果列表（用于 HorizontalPager）
- `initialUri: String` — 初始图片 URI（用于定位 pager 起始页）
- `detail: ImageDetail` — 当前图片的完整详情
- `onDetailChange: (String) -> ImageDetail` — 滑动切换时，传入新 URI，返回对应详情
- 内部用 `LaunchedEffect(pagerState.currentPage)` + `mutableStateOf` 跟踪当前页的 detail，避免 pagerState 重置

**关键功能：**
- `HorizontalPager` 显示所有搜索结果，当前页显示完整元数据，其他页只显示图片+标签
- 顶部 dragHandle 显示 "当前页 / 总数"
- 图片用 `ContentScale.Fit` 保持原始比例
- `SceneTagsFlowRow` 用 FlowRow 支持多行
- `MetadataList` 用垂直列表替代 2 列 chunked 布局，每项带 SVG 图标

- [ ] 修改 `MainScreen.kt` 中 `ImageDetailSheet` 调用处（第 359-364 行）：

```kotlin
selectedImage?.let { detail ->
    ImageDetailSheet(
        results = results,
        initialUri = detail.uri,
        detail = detail,
        onDismiss = { selectedImage = null },
        onDetailChange = { uri -> vm.getImageDetail(uri) },
    )
}
```

注意：`onNavigate` 在 HorizontalPager 页面切换时调用。但 `HorizontalPager` 本身没有直接的 onPageChanged 回调。需要用 `LaunchedEffect(pagerState.currentPage)` 来检测页面变化。

实际上更好的做法：在 `ImageDetailSheet` 内部用 `LaunchedEffect(pagerState.currentPage)` 调用 `onNavigate`，这样外部不需要感知分页逻辑。

修正 `ImageDetailSheet` 内部：

```kotlin
import androidx.compose.runtime.LaunchedEffect

// 在 HorizontalPager 之后添加：
LaunchedEffect(pagerState.currentPage) {
    if (pagerState.currentPage != initialIndex) {
        onNavigate(results[pagerState.currentPage])
    }
}
```

- [ ] 验证编译
- [ ] 提交: `git commit -m "ui: enhance detail sheet with original aspect ratio, pager, and metadata list"`

---

### Task 8: SkeletonCard 圆角同步

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/component/SkeletonPlaceholder.kt`

- [ ] 将 `SkeletonCard` 的圆角从 `MaterialTheme.shapes.medium` 改为 `RoundedCornerShape(16.dp)`，与 ImageGrid 保持一致：

```kotlin
// 第 58 行：
.background(
    MaterialTheme.colorScheme.outlineVariant,
    RoundedCornerShape(16.dp),
),
```

需要添加 `import androidx.compose.foundation.shape.RoundedCornerShape`。

- [ ] 验证编译
- [ ] 提交: `git commit -m "ui: sync skeleton card rounding with image grid"`

---

### Task 9: 最终验证 & 构建

- [ ] 完整构建: `PowerShell('$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew app:assembleDebug')`
- [ ] 确认所有改动已提交
- [ ] 在设备上验证以下场景：
  1. 首页空状态 → 索引按钮 → 索引进度 → 搜索 → 双列结果 → 点击看详情 → 左右滑动
  2. 过滤器面板展开 → 选择时间/地点/场景 → 搜索 → 活跃标签显示
  3. 搜索无结果 → 示例推荐
  4. 暗色模式（如有）下的显示效果
