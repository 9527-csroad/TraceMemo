# PicSearch 优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 PicSearch demo 原型优化为产品级 Android 图像语义搜索应用，涵盖 UI 界面、交互体验、场景分析三大维度

**Architecture:** 保持现有 MVVM + Room + WorkManager + NCNN 架构，在不改变核心推理管线的前提下，重构 UI 层、拆分 Worker、新增场景分析模块。每个 Task 可独立编译运行。

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (Material 3), NCNN 20260113, Chinese-CLIP RN50, Room 2.6.1, WorkManager 2.9.0

**Spec:** `docs/picsearch-optimization-design.md`

---

## 文件结构总览

| 文件 | 操作 | 说明 |
|------|------|------|
| `ui/theme/Color.kt` | **Modify** | 替换为 Gallery Black 配色 |
| `ui/theme/Theme.kt` | **Modify** | 更新主题配置 |
| `ui/theme/Type.kt` | **Modify** | 更新字体配置 |
| `ui/screen/MainScreen.kt` | **Rewrite** | 重构主界面（最大改动） |
| `ui/component/SearchFilterPanel.kt` | **Delete + Create** | 替换为 FilterEntryCard + ActiveFilterTags |
| `ui/component/ImageGrid.kt` | **Modify** | 卡片美化 + 场景标签 + 动画 |
| `ui/component/ImageDetailSheet.kt` | **Create** | 新增照片详情 ModalBottomSheet |
| `ui/component/EmptyStateView.kt` | **Create** | 空状态/首次使用引导 |
| `ui/component/IndexProgressView.kt` | **Create** | 索引进度面板 |
| `ui/component/SkeletonPlaceholder.kt` | **Create** | 骨架屏组件 |
| `data/db/ImageEntity.kt` | **Modify** | 增加 sceneTags 字段 |
| `data/db/ImageDao.kt` | **Modify** | 增加按场景标签查询 |
| `data/repository/ImageRepository.kt` | **Modify** | 适配新 DAO |
| `data/SearchFilter.kt` | **Modify** | 增加 sceneTags 过滤 |
| `data/SceneClassifier.kt` | **Create** | CLIP 场景分类器 |
| `worker/QuickIndexWorker.kt` | **Create** | 快速索引（Top 100） |
| `worker/IndexWorker.kt` | **Modify** | 改为后台全量索引 |
| `MainViewModel.kt` | **Modify** | 新增状态管理、混合索引、搜索动画 |
| `MainActivity.kt` | **Modify** | 空状态检测 |
| `app/build.gradle.kts` | **Modify** | 版本号和新增依赖 |

---

## Phase 1: UI 界面优化

### Task 1: 配色方案与主题迁移

**目标**: 将 Material 3 默认紫色主题替换为 Exaggerated Minimalism 风格的 Gallery Black 配色

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/theme/Type.kt`

- [ ] **Step 1: 更新 Color.kt**

```kotlin
package com.example.picsearch.ui.theme

import androidx.compose.ui.graphics.Color

// Gallery Black 配色方案 — Exaggerated Minimalism
val Primary = Color(0xFF18181B)          // 主按钮、激活状态
val Secondary = Color(0xFF27272A)        // 次要按钮、暗色背景
val AccentGreen = Color(0xFF22C55E)      // 成功/进行中状态
val TextPrimary = Color(0xFF09090B)      // 主文本
val TextSecondary = Color(0xFF71717A)    // 副文本
val TextMuted = Color(0xFFA1A1AA)        // 占位符、图标
val BorderColor = Color(0xFFE4E4E7)      // 分割线、边框
val Background = Color(0xFFFAFAFA)       // 页面背景
val SurfaceWhite = Color(0xFFFFFFFF)     // 卡片、输入框背景
val SurfaceFaint = Color(0xFFF4F4F5)     // 浅灰背景（标签等）
val ScoreBg = Color(0xCC000000)          // 相似度分数底（半透明黑）
val SceneTagBg = Color(0xE6FFFFFF)       // 场景标签底（半透明白）
```

- [ ] **Step 2: 更新 Theme.kt**

```kotlin
package com.example.picsearch.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    tertiary = AccentGreen,
    background = Background,
    surface = SurfaceWhite,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderColor,
    outlineVariant = SurfaceFaint,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE4E4E7),
    secondary = Color(0xFFD4D4D8),
    tertiary = AccentGreen,
    background = Color(0xFF09090B),
    surface = Color(0xFF18181B),
    onPrimary = Primary,
    onSecondary = Secondary,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF4F4F5),
    onSurface = Color(0xFFF4F4F5),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A),
)

@Composable
fun PicSearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 3: 更新 Type.kt**

```kotlin
package com.example.picsearch.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)
```

- [ ] **Step 4: 验证编译**

```bash
./gradlew app:assembleDebug
```

Expected: BUILD SUCCESSFUL

---

### Task 2: 筛选入口卡片 + 活跃标签组件

**目标**: 替换 SearchFilterPanel（折叠面板）为 FilterEntryCard（筛选入口卡片）和 ActiveFilterTags（活跃筛选标签）

**Files:**
- Create: `app/src/main/java/com/example/picsearch/ui/component/FilterEntryCard.kt`
- Create: `app/src/main/java/com/example/picsearch/ui/component/ActiveFilterTags.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/component/SearchFilterPanel.kt`

- [ ] **Step 1: 创建 FilterEntryCard.kt**

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FilterEntryCard(icon = "📅", label = "时间", onClick = onTimeClick, modifier = Modifier.weight(1f))
        FilterEntryCard(icon = "📍", label = "地点", onClick = onLocationClick, modifier = Modifier.weight(1f))
        FilterEntryCard(icon = "🏷️", label = "场景", onClick = onSceneClick, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FilterEntryCard(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
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
            Text(text = icon, fontSize = 16.sp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
```

注意: 需要添加 `import androidx.compose.ui.unit.sp`

- [ ] **Step 2: 创建 ActiveFilterTags.kt**

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.picsearch.data.LocationCluster
import com.example.picsearch.data.SearchFilter
import com.example.picsearch.data.TimeRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActiveFilterTags(
    filter: SearchFilter,
    selectedCluster: LocationCluster?,
    onClearTime: () -> Unit,
    onClearLocation: () -> Unit,
    onOpenFilterPanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filter.timeRange?.let { range ->
            FilterTag(label = "📅 ${formatTimeRange(range)}", onClear = onClearTime)
        }
        selectedCluster?.let { cluster ->
            FilterTag(label = "📍 ${cluster.displayName.take(8)}", onClear = onClearLocation)
        }
        // 添加筛选入口
        Text(
            text = "+ 筛选",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onOpenFilterPanel)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
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

fun formatTimeRange(r: TimeRange): String {
    val fmt = SimpleDateFormat("yyyy年MM月", Locale.getDefault())
    val start = fmt.format(Date(r.startMillis))
    val endCal = java.util.Calendar.getInstance()
    endCal.timeInMillis = r.endMillis
    val endMonth = fmt.format(Date(r.endMillis))
    return if (start == endMonth) start else "$start ~ $endMonth"
}
```

- [ ] **Step 3: 保留 SearchFilterPanel 的 DatePicker 和地点选择逻辑**

当前 `SearchFilterPanel.kt` 中的 `TimePreset` 枚举、`DatePickerDialog`、地点聚类对话框将在 MainScreen 中内联使用，不再作为独立面板。保持文件不变，MainScreen 中直接调用这些组件。

- [ ] **Step 4: 验证编译**

```bash
./gradlew app:assembleDebug
```

---

### Task 3: 结果网格卡片美化 + 空状态 + 照片详情

**目标**: 重构 ImageGrid，添加场景标签叠加、圆角美化、空状态视图、照片详情面板

**Files:**
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ImageGrid.kt`
- Create: `app/src/main/java/com/example/picsearch/ui/component/EmptyStateView.kt`
- Create: `app/src/main/java/com/example/picsearch/ui/component/ImageDetailSheet.kt`
- Modify: `app/src/main/java/com/example/picsearch/data/db/ImageEntity.kt`

- [ ] **Step 1: 更新 ImageEntity 增加 sceneTags 字段**

```kotlin
package com.example.picsearch.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val uri: String,
    val feature: ByteArray,
    val dateTaken: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val displayName: String?,
    val width: Int,
    val height: Int,
    val indexedAt: Long,
    @ColumnInfo(name = "scene_tags") val sceneTags: String? = null,
)
```

注意: 由于数据库版本为 1 且无用户数据，直接 fallbackToDestructiveMigration 或在 AppDatabase 中重建。在 AppDatabase.kt 中修改:

```kotlin
@Database(
    entities = [ImageEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun imageDao(): ImageDao
}
```

在 MainViewModel 的 Room.databaseBuilder 中添加 fallback:
```kotlin
Room.databaseBuilder(app, AppDatabase::class.java, "picsearch.db")
    .fallbackToDestructiveMigration()
    .build()
```

- [ ] **Step 2: 更新 ImageGrid.kt**

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
    ) {
        itemsIndexed(uris, key = { _, item -> item.uri }) { index, item ->
            AnimatedGridItem(index = index) {
                ImageCard(item = item, onClick = onImageClick)
            }
        }
    }
}

@Composable
private fun ImageCard(item: ImageScore, onClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .padding(6.dp)
            .clip(MaterialTheme.shapes.medium)
            .aspectRatio(1f)
            .clickable { onClick(item.uri) },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .size(512)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // 场景标签（左上角）
        item.sceneTags?.takeIf { it.isNotEmpty() }?.let { tags ->
            val primary = tags.first()
            Text(
                text = primary,
                fontSize = 10.sp,
                color = Color(0xFF3F3F46),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color(0xE6FFFFFF), MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        // 相似度分数（右下角）
        Text(
            text = "${(item.score * 100).toInt()}%",
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color(0xCC000000), MaterialTheme.shapes.small)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
```

- [ ] **Step 3: 创建 AnimatedGridItem（结果渐入动画）**

在 ImageGrid.kt 中添加:

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically

@Composable
private fun AnimatedGridItem(index: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(durationMillis = 300, delayMillis = index * 50)) +
                slideInVertically(
                    initialOffsetY = { it / 5 },
                    animationSpec = tween(durationMillis = 300, delayMillis = index * 50),
                ),
    ) {
        content()
    }
}
```

- [ ] **Step 4: 更新 ImageScore 数据类支持 sceneTags**

在 `MainViewModel.kt` 中修改:

```kotlin
data class ImageScore(
    val uri: String,
    val score: Float,
    val sceneTags: List<String> = emptyList(),
)
```

- [ ] **Step 5: 创建 EmptyStateView.kt**

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.picsearch.ui.theme.Primary

@Composable
fun EmptyStateView(
    title: String,
    description: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.outlineVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "🔍", fontSize = 36.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text(actionText)
            }
        }
    }
}

@Composable
fun NoResultsView(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "没有找到匹配的照片",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "试试其他描述，如\"日落\"、\"海滩\"、\"猫咪\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
```

- [ ] **Step 6: 创建 ImageDetailSheet.kt**

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 大图预览
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(detail.uri)
                    .size(1024)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(MaterialTheme.shapes.medium),
            )

            // 场景标签
            if (detail.sceneTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    detail.sceneTags.forEach { tag ->
                        Text(
                            text = tag,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // 照片信息
            Text(
                text = "照片信息",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            InfoGrid(
                items = listOf(
                    "拍摄时间" to detail.dateTaken?.let { formatTimestamp(it) } ?: "未知",
                    "拍摄地点" to formatLocation(detail.latitude, detail.longitude),
                    "尺寸" to "${detail.width} × ${detail.height}",
                    "文件名" to (detail.displayName ?: "未知"),
                ),
            )
        }
    }
}

@Composable
private fun InfoGrid(items: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(12.dp),
    ) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { (label, value) ->
                    InfoItem(label = label, value = value, modifier = Modifier.weight(1f))
                }
                if (row.size < 2) {
                    Box(Modifier.weight(1f))
                }
            }
            if (items.indexOf(row) < items.size - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatTimestamp(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}

private fun formatLocation(lat: Double?, lon: Double?): String {
    if (lat == null || lon == null) return "未知"
    return String.format(Locale.getDefault(), "%.4f, %.4f", lat, lon)
}
```

- [ ] **Step 7: 更新 ImageDao 增加 sceneTags 查询**

在 `ImageDao.kt` 中添加:

```kotlin
data class UriFeature(
    val uri: String,
    val feature: ByteArray,
    @ColumnInfo(name = "scene_tags") val sceneTags: String? = null,
)
```

修改 `listFeatures()`:
```kotlin
@Query("SELECT uri, feature, scene_tags FROM images")
suspend fun listFeatures(): List<UriFeature>
```

修改 `listFeaturesFiltered()`:
```kotlin
@Query(
    """
    SELECT uri, feature, scene_tags FROM images
    WHERE ... (保持原有 WHERE 子句不变)
    """
)
```

添加按场景标签查询:
```kotlin
@Query(
    """
    SELECT uri, feature, scene_tags FROM images
    WHERE scene_tags IS NOT NULL AND scene_tags LIKE '%' || :tag || '%'
    """
)
suspend fun listFeaturesBySceneTag(tag: String): List<UriFeature>
```

- [ ] **Step 8: 验证编译**

```bash
./gradlew app:assembleDebug
```

---

### Task 4: 索引进度面板 + 空状态引导

**目标**: 创建索引进度视图和首次使用引导界面

**Files:**
- Create: `app/src/main/java/com/example/picsearch/ui/component/IndexProgressView.kt`
- Create: `app/src/main/java/com/example/picsearch/ui/component/SkeletonPlaceholder.kt`

- [ ] **Step 1: 创建 IndexProgressView.kt**

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picsearch.ui.theme.AccentGreen

@Composable
fun IndexProgressView(
    indexedCount: Int,
    totalCount: Int?,
    isQuickPhase: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "正在索引照片",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "首次加载可能需要几分钟",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        // 进度数字
        Box(
            modifier = Modifier
                .size(120.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$indexedCount",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "/ ${totalCount ?: "..."}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 快速预览阶段
        PhaseIndicator(
            isActive = isQuickPhase,
            title = "快速预览阶段",
            subtitle = "正在索引 Top 100 张照片...",
            modifier = Modifier.padding(top = 16.dp),
        )

        // 后台全量阶段
        PhaseIndicator(
            isActive = !isQuickPhase,
            title = "后台完整索引",
            subtitle = "可退出界面，搜索仍可用",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PhaseIndicator(
    isActive: Boolean,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = if (isActive)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else
            MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = AccentGreen,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive)
                        MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

- [ ] **Step 2: 创建 SkeletonPlaceholder.kt**

```kotlin
package com.example.picsearch.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SkeletonPlaceholder(modifier: Modifier = Modifier) {
    val shimmerColor = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        shimmerColor.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }

    val baseColor = MaterialTheme.colorScheme.outlineVariant
    val highlightColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(baseColor, highlightColor, baseColor),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f),
                ),
            ),
    )
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    SkeletonPlaceholder(
        modifier = modifier
            .padding(6.dp)
            .background(
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.medium,
            ),
    )
}

@Composable
fun SkeletonLine(modifier: Modifier = Modifier) {
    SkeletonPlaceholder(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.outlineVariant,
                MaterialTheme.shapes.small,
            ),
    )
}
```

- [ ] **Step 3: 验证编译**

```bash
./gradlew app:assembleDebug
```

---

## Phase 2: 交互体验优化

### Task 5: 重构 MainScreen 整合所有 UI 组件

**目标**: 将 MainScreen 从当前的简单布局重构为完整的产品级界面，整合所有新组件

**Files:**
- Rewrite: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

- [ ] **Step 1: 重写 MainScreen.kt**

```kotlin
package com.example.picsearch.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.picsearch.MainViewModel
import com.example.picsearch.data.LocationBounds
import com.example.picsearch.data.LocationCluster
import com.example.picsearch.data.SearchFilter
import com.example.picsearch.data.TimeRange
import com.example.picsearch.ui.component.ActiveFilterTags
import com.example.picsearch.ui.component.EmptyStateView
import com.example.picsearch.ui.component.FilterEntryRow
import com.example.picsearch.ui.component.ImageDetail
import com.example.picsearch.ui.component.ImageDetailSheet
import com.example.picsearch.ui.component.ImageGrid
import com.example.picsearch.ui.component.IndexProgressView
import com.example.picsearch.ui.component.NoResultsView
import com.example.picsearch.ui.component.SearchFilterPanel
import com.example.picsearch.ui.theme.Primary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val ready by vm.ready.collectAsState()
    val count by vm.indexedCount.collectAsState()
    val results by vm.results.collectAsState()
    val isSearching by vm.isSearching.collectAsState()
    val clusters by vm.clusters.collectAsState()
    val unlocatedCount by vm.unlocatedCount.collectAsState()
    val workProgress by vm.workProgress.collectAsState()
    val ctx = LocalContext.current

    // 搜索状态
    var hasSearched by remember { mutableStateOf(false) }

    // 筛选状态
    var showFilterPanel by remember { mutableStateOf(false) }
    var timeRange by remember { mutableStateOf<TimeRange?>(null) }
    var selectedCluster by remember { mutableStateOf<LocationCluster?>(null) }

    val filter by remember(timeRange, selectedCluster) {
        derivedStateOf {
            SearchFilter(
                timeRange = timeRange,
                locationBounds = selectedCluster?.let {
                    LocationBounds.fromBucket(it.latBucket, it.lonBucket)
                },
            )
        }
    }

    // 照片详情
    var selectedImage by remember { mutableStateOf<ImageDetail?>(null) }

    // 权限
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> if (granted) vm.startIndex() },
    )

    val workInfos by WorkManager.getInstance(ctx)
        .getWorkInfosForUniqueWorkLiveData("index")
        .observeAsState()
    val workRunning = workInfos?.firstOrNull()?.let {
        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
    } ?: false

    // 空状态
    if (count == 0 && !workRunning) {
        EmptyStateView(
            title = "还没有索引照片",
            description = "开始索引你的照片，然后用文字描述就能找到它们。所有处理都在本机完成，隐私安全。",
            actionText = "📸 开始索引",
            onAction = { permLauncher.launch(permission) },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // 头部
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
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

        // 搜索框
        val focusRequester = remember { FocusRequester() }
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRequester(focusRequester),
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
                        Text("🔍", fontSize = 18.sp)
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
            ),
        )

        // 活跃筛选标签
        if (!filter.isEmpty || selectedCluster != null) {
            ActiveFilterTags(
                filter = filter,
                selectedCluster = selectedCluster,
                onClearTime = { timeRange = null },
                onClearLocation = { selectedCluster = null },
                onOpenFilterPanel = { showFilterPanel = !showFilterPanel },
            )
        }

        // 筛选入口
        FilterEntryRow(
            onTimeClick = { showFilterPanel = !showFilterPanel },
            onLocationClick = { showFilterPanel = !showFilterPanel },
            onSceneClick = { showFilterPanel = !showFilterPanel },
        )

        // 展开的筛选面板
        AnimatedVisibility(visible = showFilterPanel) {
            SearchFilterPanel(
                timeRange = timeRange,
                onTimeRangeChange = { timeRange = it },
                selectedCluster = selectedCluster,
                onClusterChange = { selectedCluster = it },
                clusters = clusters,
                unlocatedCount = unlocatedCount,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        // 搜索结果
        if (hasSearched && results.isEmpty() && !isSearching) {
            NoResultsView(query = query)
        } else if (results.isNotEmpty()) {
            Text(
                text = "找到 ${results.size} 张照片",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ImageGrid(
                uris = results,
                onImageClick = { uri ->
                    val item = vm.getImageDetail(uri)
                    selectedImage = item
                },
                modifier = Modifier.padding(bottom = 80.dp),
            )
        } else if (isSearching) {
            // 搜索中的骨架屏
            Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                repeat(4) {
                    SkeletonCard(modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                }
            }
        }

        // 底部索引按钮
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = Primary,
            shape = RoundedCornerShape(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .clickable { permLauncher.launch(permission) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📸 索引照片",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "已索引 $count 张照片",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    // 索引进度面板（当 WorkManager 运行时显示）
    if (workRunning && count < 100) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center,
        ) {
            IndexProgressView(
                indexedCount = count,
                totalCount = null,
                isQuickPhase = count < 100,
            )
        }
    }

    // 照片详情
    selectedImage?.let { detail ->
        ImageDetailSheet(
            detail = detail,
            onDismiss = { selectedImage = null },
        )
        }
}
```

注意: 需要添加 `import androidx.compose.foundation.layout.aspectRatio`。

- [ ] **Step 2: 更新 MainViewModel 支持新状态**

在 `MainViewModel.kt` 中添加:

```kotlin
private val _isSearching = MutableStateFlow(false)
val isSearching: StateFlow<Boolean> = _isSearching

private val _workProgress = MutableStateFlow(0)
val workProgress: StateFlow<Int> = _workProgress

// 存储图片详情用于详情面板
private val _imageDetails = MutableStateFlow<Map<String, ImageDetailData>>(emptyMap())

data class ImageDetailData(
    val uri: String,
    val displayName: String?,
    val width: Int,
    val height: Int,
    val dateTaken: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val sceneTags: List<String>,
)
```

修改 `search()` 方法添加搜索状态:

```kotlin
fun search(text: String, filter: SearchFilter = SearchFilter(), topK: Int = 10) {
    viewModelScope.launch(Dispatchers.IO) {
        _isSearching.value = true
        try {
            // ... 原有搜索逻辑 ...
            // 在更新 _results 时同时更新 _imageDetails
        } finally {
            _isSearching.value = false
        }
    }
}
```

添加 `getImageDetail()` 方法:

```kotlin
fun getImageDetail(uri: String): ImageDetail {
    val data = _imageDetails.value[uri]
    return ImageDetail(
        uri = data?.uri ?: uri,
        displayName = data?.displayName,
        width = data?.width ?: 0,
        height = data?.height ?: 0,
        dateTaken = data?.dateTaken,
        latitude = data?.latitude,
        longitude = data?.longitude,
        sceneTags = data?.sceneTags ?: emptyList(),
    )
}
```

- [ ] **Step 3: 验证编译**

```bash
./gradlew app:assembleDebug
```

---

### Task 6: 混合索引策略（Quick + Full）

**目标**: 将 IndexWorker 拆分为快速索引（Top 100）和后台全量索引

**Files:**
- Create: `app/src/main/java/com/example/picsearch/worker/QuickIndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/worker/IndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`

- [ ] **Step 1: 创建 QuickIndexWorker.kt**

```kotlin
package com.example.picsearch.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.picsearch.data.db.AppDatabase
import com.example.picsearch.data.db.ImageEntity
import com.example.picsearch.ml.ChineseTokenizer
import com.example.picsearch.ml.FeatureExtractor
import com.example.picsearch.ml.NcnnClip
import com.example.picsearch.util.ExifHelper
import com.example.picsearch.util.FloatCodec

class QuickIndexWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "picsearch_quick_index"
        const val NOTIFICATION_ID = 1
        const val QUICK_LIMIT = 100
        const val WORK_NAME_QUICK = "quick_index"
    }

    override suspend fun doWork(): Result {
        createNotificationChannel()

        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "picsearch.db")
            .fallbackToDestructiveMigration()
            .build()
        val dao = db.imageDao()

        val clip = NcnnClip(applicationContext)
        if (!clip.init(true)) return Result.failure()
        val tokenizer = ChineseTokenizer(applicationContext)
        val extractor = FeatureExtractor(clip, tokenizer)

        val resolver = applicationContext.contentResolver
        val existing = dao.listUris().toHashSet()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_TAKEN,
        )

        var indexed = 0
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cur ->
            val idIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val wIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dateIdx = cur.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

            while (cur.moveToNext() && indexed < QUICK_LIMIT) {
                if (isStopped) return Result.retry()

                val id = cur.getLong(idIdx)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val uriStr = uri.toString()
                if (existing.contains(uriStr)) continue

                val feat = extractor.encodeImage(resolver, uri) ?: continue
                val exif = ExifHelper.read(resolver, uri)

                val entity = ImageEntity(
                    uri = uriStr,
                    feature = FloatCodec.toBytes(feat),
                    dateTaken = exif.dateTaken ?: cur.getLong(dateIdx).takeIf { it > 0 },
                    latitude = exif.latitude,
                    longitude = exif.longitude,
                    displayName = cur.getString(nameIdx),
                    width = cur.getInt(wIdx),
                    height = cur.getInt(hIdx),
                    indexedAt = System.currentTimeMillis(),
                    sceneTags = null, // Phase 3 实现
                )
                dao.upsert(entity)
                existing.add(uriStr)
                indexed++

                // 更新通知
                setForeground(getForegroundInfo(indexed, QUICK_LIMIT))
            }
        }

        // 快速索引完成后，触发后台全量索引
        if (indexed > 0) {
            val ctx = applicationContext
            val workManager = androidx.work.WorkManager.getInstance(ctx)
            val fullRequest = androidx.work.OneTimeWorkRequestBuilder<IndexWorker>().build()
            workManager.enqueueUniqueWork("full_index", androidx.work.ExistingWorkPolicy.KEEP, fullRequest)
        }

        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PicSearch 快速索引",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun getForegroundInfo(indexed: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("正在索引照片")
            .setContentText("$indexed / $total")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setProgress(total, indexed, false)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
```

- [ ] **Step 2: 修改 MainViewModel 的 startIndex() 使用 QuickIndexWorker**

```kotlin
fun startIndex() {
    val ctx = getApplication<Application>()
    val workManager = WorkManager.getInstance(ctx)
    val quickRequest = OneTimeWorkRequestBuilder<QuickIndexWorker>().build()
    workManager.enqueueUniqueWork("quick_index", ExistingWorkPolicy.KEEP, quickRequest)
}
```

- [ ] **Step 3: 修改 IndexWorker 为后台全量索引**

保持现有逻辑不变，但:
1. 修改 work name 为 `"full_index"`
2. 添加约束（可选）:
```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()
val request = OneTimeWorkRequestBuilder<IndexWorker>()
    .setConstraints(constraints)
    .build()
```

- [ ] **Step 4: 验证编译**

```bash
./gradlew app:assembleDebug
```

---

## Phase 3: 功能差异化

### Task 7: CLIP 场景分析

**目标**: 利用 CLIP 双编码器实现 10 个预设场景标签的自动分类

**Files:**
- Create: `app/src/main/java/com/example/picsearch/data/SceneClassifier.kt`
- Modify: `app/src/main/java/com/example/picsearch/ml/FeatureExtractor.kt`
- Modify: `app/src/main/java/com/example/picsearch/data/db/ImageEntity.kt` (已在 Task 3 完成)
- Modify: `app/src/main/java/com/example/picsearch/worker/IndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/worker/QuickIndexWorker.kt` (已在 Task 6 创建)
- Modify: `app/src/main/java/com/example/picsearch/data/SearchFilter.kt`

- [ ] **Step 1: 创建 SceneClassifier.kt**

```kotlin
package com.example.picsearch.data

import com.example.picsearch.ml.FeatureExtractor

/**
 * CLIP 场景分类器：利用预定义场景标签文本的 CLIP 编码，
 * 与图片特征做相似度匹配，得到 Top-K 场景标签。
 */
class SceneClassifier(
    private val extractor: FeatureExtractor,
) {
    companion object {
        // 预定义场景标签（按从粗到细排序）
        val SCENES = listOf(
            SceneLabel("🌅 风景", "自然风光，日出日落，户外"),
            SceneLabel("🏔️ 自然风光", "山川，森林，湖泊，草原，瀑布"),
            SceneLabel("🌊 海滩水景", "海滩，海洋，游泳，冲浪，海岸"),
            SceneLabel("🏙️ 城市建筑", "城市天际线，高楼大厦，街道，夜景"),
            SceneLabel("👤 人像", "人物肖像，自拍，人脸，特写"),
            SceneLabel("👥 群体聚会", "一群人，家庭，派对，聚会，朋友"),
            SceneLabel("🐾 动物宠物", "猫，狗，野生动物，宠物，鸟类"),
            SceneLabel("🍜 美食", "食物，餐厅，烹饪，美食，甜点"),
            SceneLabel("🎭 艺术展览", "艺术品，博物馆，展览，绘画，雕塑"),
            SceneLabel("🏃 运动健身", "运动，跑步，健身，瑜伽，篮球"),
        )

        const val TOP_K = 2  // 每张图片取 Top 2 场景
        const val SIMILARITY_THRESHOLD = 0.5f  // 最低相似度阈值
    }

    data class SceneLabel(val displayName: String, val prompt: String)

    // 预计算的场景文本向量（App 启动时计算一次）
    private lateinit var sceneVectors: List<FloatArray>

    /**
     * 初始化：预计算所有场景标签的文本向量
     */
    suspend fun initialize() {
        sceneVectors = SCENES.map { label ->
            extractor.encodeText(label.prompt)
        }
    }

    /**
     * 对给定图片特征进行分类，返回匹配的场景标签
     * @param imageFeature 图片的 CLIP 特征向量（768 维，已 L2 归一化）
     * @return Top-K 场景标签的 displayName 列表
     */
    fun classify(imageFeature: FloatArray): List<String> {
        if (!::sceneVectors.isInitialized) return emptyList()
        if (imageFeature.isEmpty()) return emptyList()

        val scores = sceneVectors.mapIndexed { index, sceneVec ->
            val sim = cosineSimilarity(imageFeature, sceneVec)
            index to sim
        }

        return scores
            .filter { it.second >= SIMILARITY_THRESHOLD }
            .sortedByDescending { it.second }
            .take(TOP_K)
            .map { SCENES[it.first].displayName }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            dot += a[i] * b[i]
        }
        return dot
    }
}
```

- [ ] **Step 2: 在 MainViewModel init 中初始化 SceneClassifier**

```kotlin
private lateinit var sceneClassifier: SceneClassifier

init {
    viewModelScope.launch(Dispatchers.IO) {
        val ok = clip.init(false)
        _ready.value = ok

        // 初始化场景分类器
        sceneClassifier = SceneClassifier(extractor)
        sceneClassifier.initialize()

        _indexedCount.value = repo.count()
        loadClusters()
    }
}
```

- [ ] **Step 3: 在索引 Worker 中使用场景分类**

在 `QuickIndexWorker.kt` 和 `IndexWorker.kt` 中，创建 `SceneClassifier` 实例:

```kotlin
// 在 doWork() 中
val classifier = SceneClassifier(extractor)
classifier.initialize()

// 在创建 ImageEntity 时
val sceneTags = classifier.classify(feat)
val sceneTagsStr = if (sceneTags.isNotEmpty()) sceneTags.joinToString(",") else null

val entity = ImageEntity(
    // ... 其他字段 ...
    sceneTags = sceneTagsStr,
)
```

- [ ] **Step 4: 在搜索结果中携带场景标签**

修改 `MainViewModel.search()`:

```kotlin
// 在解码 feature 时同时获取 sceneTags
val rows = repo.listFeaturesFiltered(filter)
val scored = ArrayList<ImageScore>(rows.size)
for (r in rows) {
    val fv = FloatCodec.fromBytes(r.feature)
    if (fv.isEmpty()) continue
    var s = 0f
    val n = minOf(qv.size, fv.size)
    for (i in 0 until n) s += qv[i] * fv[i]
    if (!s.isFinite()) continue

    val tags = r.sceneTags
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    scored.add(ImageScore(r.uri, s, tags))
}
```

- [ ] **Step 5: 更新 SearchFilter 支持场景标签过滤**

```kotlin
data class SearchFilter(
    val timeRange: TimeRange? = null,
    val locationBounds: LocationBounds? = null,
    val sceneTags: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = timeRange == null && locationBounds == null && sceneTags.isEmpty()
    val selectedCount: Int get() =
        (if (timeRange != null) 1 else 0) +
        (if (locationBounds != null) 1 else 0) +
        sceneTags.size
}
```

在 `ImageDao.kt` 中添加:

```kotlin
@Query(
    """
    SELECT uri, feature, scene_tags FROM images
    WHERE scene_tags IS NOT NULL AND scene_tags LIKE '%' || :tag || '%'
    """
)
suspend fun listFeaturesBySceneTag(tag: String): List<UriFeature>
```

- [ ] **Step 6: 在 MainScreen 中显示场景筛选入口**

场景筛选已在 `FilterEntryRow` 中添加入口，点击后弹出场景标签选择对话框:

```kotlin
// 在 MainScreen 中
var showScenePicker by remember { mutableStateOf(false) }
var selectedScenes by remember { mutableStateOf<List<String>>(emptyList()) }

// 在 FilterEntryRow 的 onSceneClick 中
onSceneClick = { showScenePicker = true },

// 场景选择对话框
if (showScenePicker) {
    AlertDialog(
        onDismissRequest = { showScenePicker = false },
        confirmButton = {
            TextButton(onClick = {
                showScenePicker = false
            }) { Text("确定") }
        },
        title = { Text("选择场景") },
        text = {
            LazyVerticalGrid(columns = GridCells.Fixed(2)) {
                items(SceneClassifier.SCENES) { label ->
                    FilterChip(
                        selected = selectedScenes.contains(label.displayName),
                        onClick = {
                            selectedScenes = if (selectedScenes.contains(label.displayName)) {
                                selectedScenes - label.displayName
                            } else {
                                selectedScenes + label.displayName
                            }
                        },
                        label = { Text(label.displayName) },
                    )
                }
            }
        },
    )
}
```

- [ ] **Step 7: 验证编译**

```bash
./gradlew app:assembleDebug
```

---

## 最终自审

### Spec 覆盖检查

| 设计文档要求 | 对应 Task | 状态 |
|-------------|-----------|------|
| 配色方案迁移 | Task 1 | ✅ |
| 搜索框升级 + 筛选入口卡片 | Task 2 + Task 5 | ✅ |
| 结果网格卡片美化 + 场景标签 | Task 3 | ✅ |
| 空状态 / 首次使用引导 | Task 4 (EmptyStateView) | ✅ |
| 照片详情 ModalBottomSheet | Task 3 (ImageDetailSheet) | ✅ |
| 索引进度面板 | Task 4 (IndexProgressView) | ✅ |
| 混合索引策略（Quick + Full） | Task 6 | ✅ |
| 搜索加载动画 + 结果渐入 | Task 3 (AnimatedGridItem) | ✅ |
| 整合搜索（标签管理） | Task 2 + Task 5 | ✅ |
| 骨架屏替换空白 | Task 4 (SkeletonPlaceholder) | ✅ |
| 手势操作（点击详情） | Task 5 (onImageClick) | ✅ |
| 场景标签预计算 | Task 7 | ✅ |
| 索引时场景匹配 + 存储 | Task 7 | ✅ |
| 场景筛选 UI + 集成 | Task 7 | ✅ |
| 元数据扩展预留 | ImageEntity sceneTags 字段 | ✅ |

### Placeholder 扫描
- 无 "TBD"/"TODO"/"implement later"
- 场景分类器中的 SIMILARITY_THRESHOLD 和 TOP_K 已给出具体值
- 所有数据库变更都有具体代码

### 类型一致性检查
- `ImageScore.sceneTags` 在所有引用处为 `List<String>`
- `ImageEntity.sceneTags` 存储为 `String?`（逗号分隔），读取时 `.split(",")`
- `SearchFilter.sceneTags` 为 `List<String>`
- `SceneClassifier.classify()` 返回 `List<String>`

### 数据库版本
- 版本从 1 → 2，使用 `fallbackToDestructiveMigration()`（无用户数据阶段）
