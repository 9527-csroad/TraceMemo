# PicSearch 全局任务 Spec

**日期**: 2026-05-13
**状态**: 进行中
**规则**: 完成任务后删除对应条目，新任务追加到末尾

---

## 开发任务

### Dev-1: 提取 Worker GPS 公共逻辑

**优先级**: P1 | **状态**: ✅ 已完成

提取到 `util/MediaStoreHelper.kt`。两个 Worker 的 GPS 提取 + EXIF fallback + 零值过滤逻辑已替换为单行调用 `MediaStoreHelper.extractGps()`，同时携带 `dateTaken`。

---

### Dev-2: 删除死代码

**优先级**: P1 | **状态**: ✅ 已完成

删除内容：

1. `ImageDao.kt` — `listFeaturesBySceneTag(tag)`，场景筛选已走 Kotlin post-filter
2. `ImageGrid.kt` — `ImageCard()` private 函数 + 3 个 LazyVerticalGrid 相关 import

验证：BUILD SUCCESSFUL，Grep 确认无残留 `.kt` 引用。

**文件**:

- Modify: `app/src/main/java/com/example/picsearch/data/db/ImageDao.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ImageGrid.kt`

**验证**: `.\gradlew app:assembleDebug` 通过，Grep 确认无残留引用。

---

## 产品任务

### Prod-1: 修复 formatTimeRange 编译冲突

**优先级**: P0 | **状态**: ✅ 已完成

重复定义已在之前的会话中修复。`ActiveFilterTags.kt:82` 为唯一 public 定义，`SearchFilterPanel.kt` 仅剩调用。

- Modify: `app/src/main/java/com/example/picsearch/ui/component/SearchFilterPanel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ActiveFilterTags.kt`

**验证**: `.\gradlew app:assembleDebug` 通过。

---

### Prod-2: 全量索引进度可视化

**优先级**: P0 | **状态**: ✅ 已完成

**实现**:

1. `IndexWorker` 统计 MediaStore 总数，每索引 10 张写入 SharedPreferences `{indexedCount, totalCount}`，完成后写入最终值
2. `MainViewModel` 新增 `fullIndexProgress: StateFlow<Pair<Int, Int>?>`，2 秒轮询读取 SharedPreferences
3. 底部索引按钮在 `fullProgress != null` 时切换为 `LinearIndexProgress` 进度条样式（含百分比），overlay 也使用真实总数
4. `totalCount` 为 0 或 SharedPreferences 无数据时自动回退为 null（不显示进度条）

**文件**:

- Modify: `app/src/main/java/com/example/picsearch/worker/IndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`
- Create: `app/src/main/java/com/example/picsearch/ui/component/LinearIndexProgress.kt`

**验证**: `.\gradlew app:compileDebugKotlin --rerun-tasks` 通过。

---

### Prod-3: 增量索引（含删除同步）

**优先级**: P0 | **状态**: ✅ 已完成

**实现**:

1. `QuickIndexWorker` 读取 `lastIndexTimestamp`，MediaStore 查询加 `DATE_ADDED > ?` 过滤；完成后保存当前时间戳。首次索引（timestamp=0）走全量扫描
2. 删除检测：收集 MediaStore 全部 URI 集合，对比 DB 中已有 URI，`deleteByUris` 批量移除已删除记录
3. `IndexWorker` 同样使用 `lastIndexTimestamp` 做增量扫描，也做删除检测作为安全网
4. `MainViewModel.startFullRebuild()` 清除 timestamp + progress 后重新触发索引，MainScreen 底部索引按钮长按触发
5. `ImageDao` 新增 `deleteByUris()` 方法，`ImageRepository` 透传

**文件**:

- Modify: `app/src/main/java/com/example/picsearch/worker/QuickIndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/worker/IndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/data/db/ImageDao.kt`
- Modify: `app/src/main/java/com/example/picsearch/data/repository/ImageRepository.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

**验证**: `.\gradlew app:compileDebugKotlin --rerun-tasks` 通过。

---

### Prod-4: 最近搜索记录（跳过不执行）

**优先级**: P1 | **状态**: 待实施

**问题**: 用户每次都需要重新输入查询词。

**方案**:

1. Room 新增 `search_history` 表: `id (PK auto)`, `query (unique)`, `searchedAt`
2. 每次搜索后写入/更新
3. 搜索框聚焦时展示最近 5 条（DropdownMenu），点击直接搜索
4. 每条有 ✕ 删除，"清除全部"按钮

**文件**:

- Create: `app/src/main/java/com/example/picsearch/data/db/SearchHistoryEntity.kt`
- Create: `app/src/main/java/com/example/picsearch/data/db/SearchHistoryDao.kt`
- Modify: `app/src/main/java/com/example/picsearch/data/db/AppDatabase.kt` — version 3
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

---

### Prod-5: 搜索示例引导

**优先级**: P1 | **状态**: ✅ 已完成

**实现**:
1. `NoResultsView` 新增 `onExampleClick: (String) -> Unit` 回调参数，底部显示 5 个可点击示例查询 chip
2. `MainScreen` 点击示例 chip 自动填入查询词并触发搜索
3. `ExampleQueryChips` 使用 `FlowRow` 自适应居中布局

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/ui/component/EmptyStateView.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

**验证**: `.\gradlew app:compileDebugKotlin --rerun-tasks` 通过。

---

### Prod-6: 场景标签显示照片数量

**优先级**: P1 | **状态**: ✅ 已完成

**实现**:
1. `ImageDao` 新增 `countBySceneTags()` 聚合查询，返回 `SceneTagCount(sceneTags, cnt)`
2. `MainViewModel` 轮询循环中 DB count 变化时重新加载场景计数，暴露 `sceneTagCounts: StateFlow<Map<String, Int>>`
3. `SearchFilterPanel` 新增 `sceneTagCounts` 参数，FilterChip label 显示 `"标签名 (数量)"`，数量为 0 时不显示括号

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/data/db/ImageDao.kt`
- Modify: `app/src/main/java/com/example/picsearch/data/repository/ImageRepository.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/component/SearchFilterPanel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

**验证**: `.\gradlew app:compileDebugKotlin --rerun-tasks` 通过。

---

### Prod-7: 下拉刷新

**优先级**: P2 | **状态**: ✅ 已完成

**实现**:
1. `MainScreen` 使用 `PullToRefreshBox` 包裹主内容和空状态页面，下拉触发 `vm.refreshCount()`，`rememberCoroutineScope` + `delay(500)` 确保 spinner 可见

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

**验证**: `.\gradlew app:compileDebugKotlin --rerun-tasks` 通过。

---

### Prod-8: 结果图片长按菜单

**优先级**: P2 | **状态**: ✅ 已完成

**实现**:
1. `ImageGrid` 图片卡片使用 `combinedClickable(onLongClick = ...)` 触发长按
2. `DropdownMenu` 提供"查看详情"和"分享"两个选项，`Intent.ACTION_SEND` + `EXTRA_STREAM` 实现系统分享

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ImageGrid.kt`

**验证**: `.\gradlew app:compileDebugKotlin --rerun-tasks` 通过。

---

### Prod-9: 搜索结果排序选项

**优先级**: P2 | **状态**: ✅ 已完成

**实现**:
1. `SearchSort` 枚举: `SIMILARITY`(相似度) / `DATE_TAKEN`(拍摄时间)
2. `MainViewModel` 新增 `searchSort: StateFlow<SearchSort>` 和 `setSearchSort()`，`search()` 函数接受 sort 参数并按需排序
3. `MainScreen` 结果区上方显示排序 chip，点击后自动重新搜索

**文件**:
- Create: `app/src/main/java/com/example/picsearch/data/SearchSort.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

**验证**: `.\gradlew app:compileDebugKotlin --rerun-tasks` 通过。

---

### Prod-10: Google Play 上架准备（不做）

**优先级**: P2 | **状态**: 待实施

**内容**:

- 隐私政策页面（GitHub Pages）
- Data Safety Section（不收集任何数据）
- App 图标（替换 `ic_menu_gallery`）
- 截图 ×4 + Feature Graphic 1024×500
- 应用描述（全本地、隐私安全、Chinese-CLIP、场景分类、离线地名）
- ProGuard 启用（`isMinifyEnabled = true`）

---

### Dev-7: NLP Filter Extractor（时间/地点自动提取）

**优先级**: P1 | **状态**: ✅ 已完成

**实现**:
1. `ExtractedFilter` data class — `timeRange`, `locationBounds`, `locationName`, `remainingText`
2. `TimeExpressionParser` — 纯 Kotlin 时间解析器，支持相对时间词、季节、节日、组合表达式、数字表达式（10 个单元测试通过）
3. `LocationMatcher` — 从 assets 加载中国城市 + 国家边界 + 国家别名 JSON，子串匹配返回 `LocationBounds`
4. `NlpFilterExtractor` — 组合入口：提取时间 → 移除关键词 → 提取地点 → 清理剩余文本
5. `ExtractedFilterBar` UI — 搜索框下方显示提取标签，带 ✕ 清除按钮
6. `MainViewModel.search()` — 自动 NLP 提取，手动 filter 优先于 NLP，`remainingText` 送 CLIP
7. `MainScreen` — 集成 `ExtractedFilterBar`，清除后自动重新搜索

**文件**:

- Create: `app/src/main/java/com/example/picsearch/util/ExtractedFilter.kt`
- Create: `app/src/main/java/com/example/picsearch/util/TimeExpressionParser.kt`
- Create: `app/src/main/java/com/example/picsearch/util/LocationMatcher.kt`
- Create: `app/src/main/java/com/example/picsearch/util/NlpFilterExtractor.kt`
- Create: `app/src/main/assets/geocoding/country_aliases.json`
- Create: `app/src/main/java/com/example/picsearch/ui/component/ExtractedFilterBar.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`
- Create: `app/src/test/java/com/example/picsearch/util/TimeExpressionParserTest.kt`
- Create: `app/src/test/java/com/example/picsearch/util/LocationMatcherTest.kt`（占位，待 instrumented test）
- Create: `app/src/test/java/com/example/picsearch/util/NlpFilterExtractorTest.kt`（占位，待 instrumented test）
- Create: `app/src/test/resources/robolectric.properties`

**验证**: `.\gradlew app:assembleDebug` 通过，纯 JVM 单元测试全部通过。Robolectric 资产依赖测试已标记为占位。

---

### Dev-8: 索引流程重构 + Header Pill 指示器

**优先级**: P0 | **状态**: 待实施

**问题**:
1. 首次打开 App 时全屏遮罩显示索引进度，用户无法直接使用搜索功能
2. 再次打开 App 时同样被全屏遮罩挡住，无法使用搜索
3. 底部索引按钮太大太丑，抢夺搜索功能的视觉焦点
4. 索引总数显示为 `50/...`，没有获取 MediaStore 真实总数
5. 点击底部"索引照片"按钮无法正确继续全量索引（逻辑错误）

**目标行为**:

#### 首次启动 (DB=0)
1. 显示**索引选择页**（不再是全屏遮罩），展示两个选项：
   - 「快速索引 Top 100」— 约 1-2 分钟，先用起来
   - 「索引全部照片」— 后台运行，可中断
2. 页面顶部显示检测到的照片总数（如「检测到 10,123 张照片」）
3. 用户选择后进入索引进度页（可退出）
4. 快速索引 Top 100 完成后**自动进入搜索页**
5. 索引全部时，前 100 张完成后也自动进入搜索页

#### 再次启动 (DB>0)
1. **直接进入搜索页**，不再显示全屏索引遮罩
2. 判断索引状态：
   - **全部完成**: Header Pill 显示绿色「✓ 已完成」
   - **未完成**: 弹出对话框「您还有 X 张照片未完成索引，是否继续？」
     - 「继续索引」→ 启动 IndexWorker
     - 「稍后」→ 不启动 Worker，用户可后续点击 Header Pill 手动开始
3. 索引进行中时，Header Pill 实时显示进度（如 `183 / 10123`）

#### Header Pill 指示器（替换底部按钮）
- **位置**: Header 右侧（「PicSearch」标题旁边）
- **状态 1 — 未索引**: 紫色药丸 `开始索引 ▸`，点击 → 索引选择页
- **状态 2 — 索引中**: 深色背景 + 绿色呼吸点 + `N / 总数 ▸`，点击 → 索引进度详情页
- **状态 3 — 已完成**: 绿色背景 `✓ 已完成`，点击 → 索引选择页（可重建）
- **尺寸**: height=28dp, rounded=16dp, font=12sp, weight=500
- **动画**: 状态切换用 `AnimatedContent` 淡入淡出

#### 底部区域
- **不再保留任何索引相关按钮**，搜索结果区直接铺满

#### 索引完全依赖 App 运行
- 关闭 App（退出/杀后台）时取消 Worker
- 不使用前台 Service

**技术实现**:
1. `MainViewModel` 新增 `totalPhotoCount: StateFlow<Int>` — init 时做一次轻量 MediaStore COUNT 查询
2. `MainViewModel` 新增 `isIndexComplete: StateFlow<Boolean>` — DB count == total 时为 true
3. `MainViewModel` 新增 `continueIndexing()` — 根据当前状态判断入队 QuickIndexWorker 或 IndexWorker
4. `MainViewModel` 新增 `hasShownResumeDialog: MutableStateFlow<Boolean>` — 弹窗只显示一次
5. `MainScreen` 移除 `workRunning` 全屏遮罩逻辑
6. `MainScreen` 新增 `IndexChoicePage` 首次启动选择页
7. 新建 `HeaderIndexPill` 组件
8. `IndexProgressView` 改为可退出的独立页面（增加返回按钮）

**文件**:
- Create: `app/src/main/java/com/example/picsearch/ui/component/HeaderIndexPill.kt`
- Create: `app/src/main/java/com/example/picsearch/ui/component/IndexChoicePage.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/component/IndexProgressView.kt`

---

### Dev-3: 单元测试覆盖

**优先级**: P1 | **状态**: 部分完成

**已实现**:
- `FloatCodecTest.kt` — 6 个测试：空数组、单元素、多元素、768 维 CLIP 向量、little-endian 字节序、余弦相似度往返验证
- `SearchFilterTest.kt` — 10 个测试：isEmpty/selectedCount、LocationBounds.fromBucket、LocationCluster.displayName（含可读名/回退/南半球）
- `TimeExpressionParserTest.kt` — 10 个测试（之前已完成）
- `ExampleUnitTest.kt` — 1 个样例

**待实现**（依赖 Robolectric / instrumented tests）:
- `ChineseTokenizer` — 中文分词（需要 Android assets vocab.txt）
- `SceneClassifier` — 场景分类（需要 mock FeatureExtractor + Android assets）

---

### Dev-4: Room 数据库 Migration 策略

**优先级**: P2 | **状态**: 待实施

**方案**: 在 v1.0 发布前，为 version 2→3 编写正式 Migration（非 destructive）。当前开发阶段保留 `fallbackToDestructiveMigration()`。

**文件**:

- Modify: `app/src/main/java/com/example/picsearch/data/db/AppDatabase.kt`

---

## 暂缓任务

以下任务已确认，等项目完全 OK 后再执行：

- **Dev-5: 移除调试日志 + 消除重复零向量检查** — `FeatureExtractor.kt` 硬编码 `Log.d("CLIP_DEBUG")`；Worker 和 SceneClassifier 各做一次零向量检查。消除重复，日志改为 `BuildConfig.DEBUG` 守卫。
- **Dev-6: 搜索线程优化** — 数据量 > 10K 时考虑并行分块计算余弦相似度。当前 < 5K 不需要。

