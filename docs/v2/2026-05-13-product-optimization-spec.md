# PicSearch 产品优化 Spec

**日期**: 2026-05-13
**状态**: 待实施
**来源**: 产品 Agent 分析 + 开发 Agent 技术债务审计

---

## 背景

PicSearch 核心功能（语义搜索 + 混合索引 + 场景分类 + 离线地名）已基本完成，处于 Alpha → Beta 过渡期。当前存在若干体验缺口和技术债务，需在产品侧补齐后方可上架。

## 目标

将 PicSearch 从"功能可用的 Demo"提升为"体验完整、可上架 Google Play 的产品"。

---

## Phase 1: 核心体验闭环（P0）

### Task 1.1: 修复 formatTimeRange 编译冲突

**问题**: `SearchFilterPanel.kt` 和 `ActiveFilterTags.kt` 各定义了 `formatTimeRange()` top-level 函数，Kotlin 编译器报歧义错误，阻塞构建。

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/ui/component/SearchFilterPanel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ActiveFilterTags.kt`

**方案**: 保留 `ActiveFilterTags.kt` 中的 `formatTimeRange()` 为唯一的 public 函数；`SearchFilterPanel.kt` 删除重复定义，改为 import 引用。

**验证**: `.\gradlew app:assembleDebug` 通过。

---

### Task 1.2: 全量索引进度可视化

**问题**: 索引进度覆盖层只在 `count < 100` 时显示（`MainScreen.kt:312`）。QuickIndex 完成后的全量索引阶段，用户看不到任何进度反馈。

**方案**: 
1. `IndexWorker` 定期写入 SharedPreferences: `{indexedCount, totalCount, isRunning}`
2. `MainViewModel` 新增 `fullIndexProgress: StateFlow<Pair<Int, Int>?>` 轮询 SharedPreferences
3. 底部索引按钮区域改为进度条样式：显示 "正在索引 234 / 1,567 张照片"

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/worker/IndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

**验证**: 启动索引后，底部按钮区域实时显示 `当前/总数` 进度，索引进度不再"消失"。

---

### Task 1.3: 增量索引（含删除同步）

**问题**: 每次点击"索引照片"都是全量扫描 MediaStore + 全量重新编码，已索引的照片被跳过但 MediaStore 查询仍遍历全部。用户第二次索引时体验无改善。

**方案**:
1. `ImageEntity` 或 SharedPreferences 记录 `lastIndexTimestamp`
2. Worker 查询时增加条件: `DATE_ADDED > lastIndexTimestamp` (仅扫描新增照片)
3. 删除检测: 对比 DB 中所有 URI 与 MediaStore 当前 URI 集合，移除已删除的照片记录
4. 保留"全量重建"按钮（长按索引按钮触发）用于异常恢复

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/worker/QuickIndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/worker/IndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/data/db/ImageDao.kt` — 新增 `deleteByUri()` 和 `listUris()` 已有
- Modify: `app/src/main/java/com/example/picsearch/data/repository/ImageRepository.kt`

**验证**: 
- 首次索引 1000 张照片 → 完成
- 新增 5 张照片 → 再次点击索引 → 仅处理 5 张新照片，耗时 < 3 秒
- 手动删除 3 张照片 → 再次索引 → DB 中对应记录被清除

---

## Phase 2: 搜索体验增强（P1）

### Task 2.1: 最近搜索记录

**问题**: 用户每次都需要重新输入查询词，不符合搜索工具的常见预期。

**方案**:
1. Room 新增 `search_history` 表: `id (PK auto)`, `query (String, unique)`, `searchedAt (Long)`
2. 每次搜索后将 query 写入（已存在则更新 `searchedAt`）
3. 搜索框聚焦时展示最近 5 条记录（DropdownMenu），点击直接搜索
4. 每条记录右侧有 ✕ 删除按钮，底部有"清除全部"按钮

**文件**:
- Create: `app/src/main/java/com/example/picsearch/data/db/SearchHistoryEntity.kt`
- Modify: `app/src/main/java/com/example/picsearch/data/db/AppDatabase.kt` — version 3, 新增表
- Modify: `app/src/main/java/com/example/picsearch/data/db/ImageDao.kt` — 新增 search_history 相关查询
- Create: `app/src/main/java/com/example/picsearch/data/db/SearchHistoryDao.kt` — 独立 DAO
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt` — 搜索框聚焦时下拉历史

**验证**: 搜索"日落" → 再次点击搜索框 → 下拉显示"日落" → 点击直接搜索。

---

### Task 2.2: 搜索示例引导

**问题**: 新用户不知道如何描述照片，空结果页只有一句提示。

**方案**:
1. 空结果页增加 3-5 个示例查询 chip："日落时的海滩"、"猫咪的照片"、"和朋友聚餐"、"去年夏天的旅行"
2. 每个 chip 可点击，点击后自动填入搜索框并触发搜索
3. 示例从 `SceneClassifier.SCENES` 动态生成一部分（如"风景照片"、"美食照片"）

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/ui/component/EmptyStateView.kt` — `NoResultsView` 增加示例 chips
- 或新增 `SearchSuggestions` Composable

**验证**: 无结果时页面展示可点击的示例查询，点击后直接搜索。

---

### Task 2.3: 场景标签显示照片数量

**问题**: 用户在筛选面板看到 10 个场景标签，但不知道每个标签有多少照片。

**方案**:
1. `ImageDao` 新增查询: `countBySceneTag()` — `SELECT scene_tags, COUNT(*) FROM images GROUP BY scene_tags`
2. `MainViewModel` 新增 `sceneTagCounts: StateFlow<Map<String, Int>>`
3. `SearchFilterPanel` 的 `FilterChip` label 从 "🌅 风景" 变为 "🌅 风景 (156)"

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/data/db/ImageDao.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/component/SearchFilterPanel.kt`

**验证**: 筛选面板中每个场景标签后显示对应照片数量。

---

## Phase 3: 体验打磨（P2）

### Task 3.1: 下拉刷新

**目标**: 在结果页下拉触发 `refreshCount()` + 重新加载 clusters。

**方案**: Compose `pullRefresh` modifier，下拉时触发 `vm.refresh()`。

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

---

### Task 3.2: 结果图片长按菜单

**目标**: 长按结果图片弹出菜单（分享、查看详情）。

**方案**: `Modifier.combinedClickable(onLongClick = { showMenu = true })` + `DropdownMenu`。

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ImageGrid.kt`

---

### Task 3.3: 搜索结果排序选项

**目标**: 支持"相似度优先" / "时间优先"两种排序。

**方案**: `MainViewModel.search()` 增加 `sortBy: SortBy` 参数，`SearchSort.kt` 枚举。时间排序在 Kotlin 层 `sortedByDescending { it.dateTaken }`。

**文件**:
- Create: `app/src/main/java/com/example/picsearch/data/SearchSort.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

---

## Phase 4: Google Play 上架准备

### Task 4.1: 隐私政策

- 创建 `privacy-policy.md` 托管到 GitHub Pages
- 内容：声明不收集/不上传任何用户数据，全部处理在本地完成

### Task 4.2: Data Safety Section

- Play Console 中声明: 不收集任何用户数据

### Task 4.3: App 图标

- 设计一个辨识度高的图标（替换 `ic_menu_gallery` 系统图标）
- 格式: 512x512 PNG，自适应图标（adaptive icon）

### Task 4.4: Play Store 素材

- 至少 4 张截图: 搜索、结果、详情、筛选
- Feature Graphic: 1024x500
- 应用描述强调 5 个差异点: 全本地、隐私安全、Chinese-CLIP 中文理解、场景分类、离线地名

### Task 4.5: ProGuard 启用

- `isMinifyEnabled = true`，减小 APK 体积
- 配置 `proguard-rules.pro` 保留 NCNN JNI 和 Room 实体

---

## 成功标准

| 指标 | 当前 | 目标 |
|------|------|------|
| 构建状态 | 有编译冲突 | 零错误通过 |
| 索引进度可见性 | 仅 Top 100 可见 | 全量过程持续可见 |
| 重复索引耗时 | 全量扫描 N 张 | 仅扫描增量照片 |
| 搜索历史 | 无 | 最近 5 条可复用 |
| 新用户引导 | 空状态一句话 | 可点击示例查询 |
| Google Play 上架 | 未准备 | 隐私政策 + 图标 + 截图齐全 |
| APK 体积 | 未优化 | ProGuard 启用后缩小 |

---

## 不做的事（YAGNI）

- 不实现"查找相似照片"（以图搜图）— 留待 v1.2
- 不加地图可视化 — 当前地点聚类足够
- 不支持英文搜索 — Chinese-CLIP 的核心优势是中文
- 不加社交分享 — 系统分享已足够
- 不实现 ANN/FAISS 索引 — 当前数据量（< 10K）暴力点积足够