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

**优先级**: P1 | **状态**: 待实施（使用 `/code-review` skill 辅助）

**问题**:
1. `ImageDao.kt` — `listFeaturesBySceneTag(tag)` 无任何调用者（场景筛选已走 Kotlin post-filter）
2. `ImageGrid.kt` — `ImageCard()` private 函数在 LazyVerticalGrid → Column+Row 重构后不再使用

**方案**: 直接删除。使用 code-review skill 确认无残留引用。

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/data/db/ImageDao.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ImageGrid.kt`

**验证**: `.\gradlew app:assembleDebug` 通过，Grep 确认无残留引用。

---

## 产品任务

### Prod-1: 修复 formatTimeRange 编译冲突

**优先级**: P0 | **状态**: 待实施

**问题**: `SearchFilterPanel.kt` 和 `ActiveFilterTags.kt` 各定义了 `formatTimeRange()` top-level 函数，Kotlin 编译器报歧义错误。

**方案**: 保留 `ActiveFilterTags.kt` 中的为唯一 public 函数；`SearchFilterPanel.kt` 删除重复定义，改为 import。

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/ui/component/SearchFilterPanel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ActiveFilterTags.kt`

**验证**: `.\gradlew app:assembleDebug` 通过。

---

### Prod-2: 全量索引进度可视化

**优先级**: P0 | **状态**: 待实施

**问题**: 索引进度覆盖层只在 `count < 100` 时显示，全量索引阶段用户看不到进度。

**方案**:
1. `IndexWorker` 定期写入 SharedPreferences: `{indexedCount, totalCount}`
2. `MainViewModel` 新增 `fullIndexProgress: StateFlow<Pair<Int, Int>?>` 轮询
3. 底部索引按钮区域改为进度条样式

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/worker/IndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

---

### Prod-3: 增量索引（含删除同步）

**优先级**: P0 | **状态**: 待实施

**问题**: 每次点击"索引照片"都全量扫描 MediaStore，已索引的照片被跳过但查询仍遍历全部。

**方案**:
1. 记录 `lastIndexTimestamp`，Worker 只扫描 `DATE_ADDED > lastIndexTimestamp`
2. 删除检测：对比 DB URI 集合与 MediaStore 当前 URI，移除已删除记录
3. 保留长按触发"全量重建"用于异常恢复

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/worker/QuickIndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/worker/IndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/data/db/ImageDao.kt`
- Modify: `app/src/main/java/com/example/picsearch/data/repository/ImageRepository.kt`

---

### Prod-4: 最近搜索记录

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

**优先级**: P1 | **状态**: 待实施

**问题**: 新用户不知道如何描述照片。

**方案**: 无结果页增加 3-5 个可点击示例查询 chip："日落时的海滩"、"猫咪的照片"、"和朋友聚餐"、"去年夏天的旅行"、"风景照片"。

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/ui/component/EmptyStateView.kt`

---

### Prod-6: 场景标签显示照片数量

**优先级**: P1 | **状态**: 待实施

**问题**: 筛选面板中场景标签不显示照片数量。

**方案**:
1. `ImageDao` 新增聚合查询 `countBySceneTag()`
2. `MainViewModel` 新增 `sceneTagCounts: StateFlow<Map<String, Int>>`
3. `SearchFilterPanel` FilterChip label 改为 `"🌅 风景 (156)"`

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/data/db/ImageDao.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/component/SearchFilterPanel.kt`

---

### Prod-7: 下拉刷新

**优先级**: P2 | **状态**: 待实施

**方案**: Compose `pullRefresh` modifier，下拉触发 `vm.refresh()`。

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

---

### Prod-8: 结果图片长按菜单

**优先级**: P2 | **状态**: 待实施

**方案**: `Modifier.combinedClickable(onLongClick = ...)` + `DropdownMenu`（分享、查看详情）。

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ImageGrid.kt`

---

### Prod-9: 搜索结果排序选项

**优先级**: P2 | **状态**: 待实施

**方案**: 支持"相似度优先" / "时间优先"两种排序。

**文件**:
- Create: `app/src/main/java/com/example/picsearch/data/SearchSort.kt`
- Modify: `app/src/main/java/com/example/picsearch/MainViewModel.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/screen/MainScreen.kt`

---

### Prod-10: Google Play 上架准备

**优先级**: P2 | **状态**: 待实施

**内容**:
- [ ] 隐私政策页面（GitHub Pages）
- [ ] Data Safety Section（不收集任何数据）
- [ ] App 图标（替换 `ic_menu_gallery`）
- [ ] 截图 ×4 + Feature Graphic 1024×500
- [ ] 应用描述（全本地、隐私安全、Chinese-CLIP、场景分类、离线地名）
- [ ] ProGuard 启用（`isMinifyEnabled = true`）

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