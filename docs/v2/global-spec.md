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

### Dev-3: 单元测试覆盖

**优先级**: P1 | **状态**: 待实施

**目标**: 对以下核心逻辑添加 JVM 单元测试（`app/src/test/`）:

- `ChineseTokenizer` — 中文分词（用 mock vocab 或直接测 basicTokenize/wordpiece）
- `SceneClassifier` — 场景分类（mock FeatureExtractor）
- `SearchFilter` — isEmpty/selectedCount/filter 逻辑
- `FloatCodec` — 序列化往返

**验证**: `.\gradlew test` 全部通过。

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

