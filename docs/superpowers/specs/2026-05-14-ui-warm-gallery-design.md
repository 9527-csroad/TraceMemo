# PicSearch UI "温暖画廊" 优化设计

**日期**: 2026-05-14 | **范围**: UI 层全部组件 | **优先级**: 高

## 目标

将 PicSearch 从当前"极简素色"升级为"温暖画廊"风格：暖色调注入、双列网格恢复、图标专业化、详情弹窗增强。所有改动仅限于 UI 层，不涉及数据层或 ML 层。

## 配色调整（暖色调）

在现有 `Color.kt` Gallery Black 基础上注入暖色倾向，不改变整体架构。

| 用途 | 旧值 | 新值 | 说明 |
|------|------|------|------|
| Background | `#FAFAFA` | `#F8F6F3` | 极淡暖米白 |
| SurfaceWhite | `#FFFFFF` | `#FFFEFC` | 暖白卡片 |
| BorderColor | `#E4E4E7` | `#E8E4DF` | 暖灰边框 |
| TextSecondary | `#71717A` | `#6B6560` | 暖灰副文本 |
| Primary | `#18181B` | `#1C1917` | 暖深灰按钮 |
| AccentGreen | `#22C55E` | 不变 | 保留，降低使用频率 |

不改动 `Theme.kt` 中的 dynamic color 和 light/dark scheme 架构，只替换 Color.kt 中的色值。

## 搜索栏 & 过滤器

- **搜索框**: 去掉黑色下划线 indicatorColor，改用 `shadow(elevation = 2.dp)` 浮起效果，圆角保持 12dp
- **过滤按钮**: Emoji → SVG Material Icons（日历/定位/标签），改为 `OutlinedButton` 样式，按钮间距从当前紧凑改为 `spacedBy(8.dp)`
- 搜索框和过滤器之间增加 12dp 垂直间距

## 搜索结果双列网格（核心）

- `ImageGrid.kt` 恢复 `columns = 2`（代码已有 `columns = 2`，确认构建后生效）
- 间距：horizontal 6dp → 8dp，vertical 3dp → 8dp
- 圆角：`MaterialTheme.shapes.medium` → `16.dp`
- 保持 `aspectRatio(1f)` 正方形裁切
- **场景标签**: 左上角，字号 10sp → 11sp，背景从 `0xE6FFFFFF` 改为 `0xF0FFFFFF` + 1dp `BorderColor` 边框，文字颜色从硬编码 `#3F3F46` 改为 `MaterialTheme.colorScheme.onSurface`
- **相似度分数**: 右下角，字号 10sp → 11sp，加 `fontWeight.Medium`，背景从 `0xCC000000` 改为 `0xB3000000`
- **点击反馈**: 加入 `indication = ripple()` 或 `scale` 微动画（按下 0.97x）
- **入场动画**: duration 300ms → 200ms，更轻快

## 详情弹窗（ImageDetailSheet）

- 图片区域：`aspectRatio(4f/3f)` + `ContentScale.Crop` → `fillMaxWidth()` + `ContentScale.Fit`，原始比例展示
- 图片下方加 1dp `BorderColor` 分割线
- 场景标签：Row → FlowRow（`androidx.compose.foundation.layout.FlowRow`），支持多行换行
- 元数据 InfoGrid：去掉 `chunked(2)` 2 列布局，改为垂直列表，每项左侧 20dp SVG 图标 + 右侧文字，左对齐
- **左右滑动**: 加入 `HorizontalPager`（`androidx.compose.foundation.pager`），传入 `results` 列表 + 当前索引，支持左右滑切换相邻搜索结果
- 增加拖拽手柄：`ModalBottomSheet` 的 `dragHandle` 参数设为默认显示

## 图标全面替换

所有 Emoji → Material Icons（`androidx.compose.material.icons`），24dp，颜色 `TextMuted`，激活态 `TextPrimary`。

| 位置 | 旧 | 新 |
|------|----|-----|
| 时间过滤 | 📅 | `Icons.Outlined.CalendarMonth` |
| 地点过滤 | 📍 | `Icons.Outlined.Place` |
| 场景过滤 | 🏷️ | `Icons.Outlined.Label` |
| 搜索按钮 | 🔍 | `Icons.Default.Search` |
| 索引按钮 |  | `Icons.Outlined.AddPhotoAlternate` |
| EmptyStateView | 📸 | `Icons.Outlined.PhotoCamera` |
| NoResultsView | 无图标 → | `Icons.Outlined.SearchOff` |

检查所有组件文件中硬编码的 Emoji，确保无遗漏。

## 不做的（YAGNI）

- 不引入下拉刷新
- 不做瀑布流不等高布局
- 不做全屏沉浸式查看器
- 不改 MaterialTheme 整体架构
- 不改数据库 schema
- 不改 ML 推理逻辑

## 影响文件清单

| 文件 | 改动类型 | 范围 |
|------|---------|------|
| `ui/theme/Color.kt` | 修改 | 色值更新 |
| `ui/screen/MainScreen.kt` | 修改 | 搜索栏样式、图标替换、间距调整 |
| `ui/component/ImageGrid.kt` | 修改 | 间距、圆角、浮层优化、动画调优 |
| `ui/component/ImageDetailSheet.kt` | 修改 | 图片展示、FlowRow、滑动、元数据重排 |
| `ui/component/FilterEntryRow.kt` | 修改 | 图标替换、按钮样式 |
| `ui/component/ActiveFilterTags.kt` | 修改 | 图标替换 |
| `ui/component/EmptyStateView.kt` | 修改 | 图标替换 |
| `ui/component/NoResultsView.kt` | 修改 | 图标替换 |
| `ui/component/SkeletonCard.kt` | 可能修改 | 圆角同步 |
| `build.gradle.kts` (app) | 可能修改 | 确认 Compose Foundation Pager 依赖 |
