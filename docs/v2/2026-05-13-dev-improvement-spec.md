# PicSearch 开发改进 Spec

**日期**: 2026-05-13
**状态**: 待实施
**来源**: 开发 Agent 技术债务审计 + 产品 Agent 分析交叉验证

---

## 背景

PicSearch 核心功能已实现，但代码质量、测试覆盖、架构整洁度存在可衡量的技术债务。本 Spec 聚焦于非用户可见的代码层面改进，与产品 Spec 互补。

产品 Spec（用户可见改进）: `docs/v2/2026-05-13-product-optimization-spec.md`

---

## Phase 1: 关键修复（P0）

### Task 1.1: NCNN SDK 升级到 20260113

**问题**: 当前使用 ncnn-20250916，缺少内置 SDPA 层支持。Android 端图像向量与 PC 端 CosSim 仅 ~0.93（正常应 > 0.99）。详见 `docs/issue_image_vector_mismatch.md`。

**方案**:
1. 替换 `app/src/main/jni/` 下的 ncnn 库为 20260113 版本
2. 更新 `CMakeLists.txt` 中的 include/lib 路径
3. 移除 `clip_jni.cpp` 中的 SDPA 层手动检测逻辑（20260113 可能已内置支持）
4. 在 PC 端跑同一个图像，对比两端 CosSim，确认差异 < 0.01

**文件**:
- Modify: `app/src/main/cpp/clip_jni.cpp`
- Modify: `app/src/main/cpp/CMakeLists.txt`
- Replace: `app/src/main/jni/ncnn-20250916-android-vulkan/` → `ncnn-20260113-android-vulkan/`

**验证**: 同一张图片在 PC 和 Android 端的 CLIP 向量 CosSim >= 0.99。

**备注**: 如果 20260113 的 SDPA + Vulkan 有兼容性问题，先保持 `clip.init(false)` CPU 模式验证精度，再单独排查 Vulkan。

---

### Task 1.2: 提交当前未提交的改动

**问题**: 工作区有 12 个未提交文件的改动（+258/-46 行），包含多个 bug 修复（clip_jni.cpp mutex 线程安全、ImageGrid 崩溃修复、ExifHelper FileDescriptor 改进、ImageDao 批量查询等）。这些改动应在开始新任务前提交。

**方案**:
1. 分组合并提交（按功能分组，不是一个大 commit）
2. 建议分组:
   - commit 1: CLAUDE.md 更新 + known-issues.md 索引
   - commit 2: AndroidManifest 权限 + clip_jni.cpp mutex + input 检查
   - commit 3: ImageDao + ImageRepository 批量查询
   - commit 4: ImageDetailSheet 地名显示 + ImageGrid 布局修复
   - commit 5: ExifHelper FileDescriptor + Worker GPS 双路径

**验证**: `git status` 干净，所有改动已提交。

---

## Phase 2: 代码质量（P1）

### Task 2.1: 提取 Worker GPS 公共逻辑

**问题**: `QuickIndexWorker.kt` 和 `IndexWorker.kt` 各有一份 ~40 行完全相同的 GPS 提取逻辑:
- MediaStore `LATITUDE`/`LONGITUDE` 列读取
- `setRequireOriginal()` EXIF fallback
- 零值过滤（MIUI 兼容）

场景分类逻辑（创建 `SceneClassifier` → `initialize()` → `classify()`）也是两处重复。

**方案**: 提取到工具类或共享 companion object:

```kotlin
// 新建 util/MediaStoreHelper.kt
object MediaStoreHelper {
    data class GpsResult(val latitude: Double?, val longitude: Double?)

    fun extractGps(resolver: ContentResolver, uri: Uri): GpsResult {
        // 优先 MediaStore 列
        // fallback 到 ExifHelper (setRequireOriginal)
        // 返回 GpsResult
    }
}
```

**文件**:
- Create: `app/src/main/java/com/example/picsearch/util/MediaStoreHelper.kt`
- Modify: `app/src/main/java/com/example/picsearch/worker/QuickIndexWorker.kt` — 替换为 `MediaStoreHelper.extractGps()`
- Modify: `app/src/main/java/com/example/picsearch/worker/IndexWorker.kt` — 同上

**验证**: 两个 Worker 中的 GPS 代码替换为一行调用，编译通过，索引功能正常。

---

### Task 2.2: 删除死代码

**问题**: 以下代码无任何调用者:
1. `ImageDao.kt:54-55` — `listFeaturesBySceneTag(tag: String)` — 场景筛选已走 Kotlin post-filter
2. `ImageGrid.kt:125-168` — `ImageCard()` private 函数 — LazyVerticalGrid → Column+Row 重构后不再使用

**方案**: 直接删除。设计文档中已明确标注"留待后续清理"。

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/data/db/ImageDao.kt`
- Modify: `app/src/main/java/com/example/picsearch/ui/component/ImageGrid.kt`

**验证**: `.\gradlew app:assembleDebug` 通过，Grep 验证无残留引用。

---

### Task 2.3: 移除调试日志 + 消除重复零向量检查

**问题**:
1. `FeatureExtractor.kt:21` — 硬编码 `Log.d("CLIP_DEBUG", "Token IDs: ...")` 
2. Worker 层和 `SceneClassifier.classify()` 各做了一次零向量检查 (`mag < 1e-6`)

**方案**:
1. `FeatureExtractor` 中的日志改为 `if (BuildConfig.DEBUG) Log.d(...)` 或直接移除
2. 零向量检查只保留在 `SceneClassifier.classify()` 中（作为分类器的输入验证），Worker 层移除重复检查

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/ml/FeatureExtractor.kt`
- Modify: `app/src/main/java/com/example/picsearch/worker/QuickIndexWorker.kt`
- Modify: `app/src/main/java/com/example/picsearch/worker/IndexWorker.kt`

**验证**: Release build 无 CLIP_DEBUG 日志；零向量图片在分类器中正确返回空标签。

---

## Phase 3: 测试覆盖（P1）

### Task 3.1: 核心逻辑单元测试

**问题**: 测试目录仅有示例代码（`2+2` 和 package name 断言）。以下核心逻辑完全无测试覆盖:
- `ChineseTokenizer.encode()` — 中文分词
- `SceneClassifier.classify()` — 场景分类
- `SearchFilter.isEmpty` / `selectedCount` — 筛选逻辑
- `FloatCodec.toBytes()` / `fromBytes()` — 序列化往返
- Kotlin 层场景 post-filter 逻辑（`search()` 中的 `filteredRows`）

**方案**: 在 `app/src/test/` 下添加 JVM 单元测试（不依赖 Android 框架）：

```kotlin
// ChineseTokenizerTest.kt
class ChineseTokenizerTest {
    @Test fun `tokenize simple Chinese text`() { ... }
    @Test fun `tokenize mixed CJK and ASCII`() { ... }
    @Test fun `tokenize empty string`() { ... }
}

// SceneClassifierTest.kt
class SceneClassifierTest {
    @Test fun `classify returns top 2 labels above threshold`() { ... }
    @Test fun `classify returns empty for near-zero vector`() { ... }
    @Test fun `all 10 scene vectors are valid`() { ... }
}

// SearchFilterTest.kt
class SearchFilterTest {
    @Test fun `isEmpty true when all fields null or empty`() { ... }
    @Test fun `selectedCount counts all active filters`() { ... }
    @Test fun `scene tag filter OR logic`() { ... }
}

// FloatCodecTest.kt
class FloatCodecTest {
    @Test fun `roundtrip preserves values`() { ... }
    @Test fun `empty array roundtrip`() { ... }
}
```

**文件**:
- Create: `app/src/test/java/com/example/picsearch/ml/ChineseTokenizerTest.kt`
- Create: `app/src/test/java/com/example/picsearch/data/SceneClassifierTest.kt`
- Create: `app/src/test/java/com/example/picsearch/data/SearchFilterTest.kt`
- Create: `app/src/test/java/com/example/picsearch/util/FloatCodecTest.kt`

**验证**: `.\gradlew test` 全部通过，覆盖率 >= 60% 对核心逻辑。

**注意事项**:
- `ChineseTokenizer` 需要 `assets/vocab.txt`，测试中需要用 mock 资源或直接测试 `basicTokenize()` 和 `wordpiece()` 方法
- `SceneClassifier` 依赖 `FeatureExtractor` → `NcnnClip`，测试中 mock `FeatureExtractor` 返回预定义向量即可

---

## Phase 4: 架构改进（P2）

### Task 4.1: Room 数据库 Migration 策略

**问题**: 当前 `fallbackToDestructiveMigration()` 意味着任何 schema 变更都会丢失所有索引数据。适合开发阶段，但发布前需要正式 Migration。

**方案**:
1. 保留 `fallbackToDestructiveMigration()` 直到版本稳定
2. 在 v1.0 发布前，为 version 2→3（新增 `search_history` 表）编写正式 Migration:
```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS search_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                query TEXT NOT NULL UNIQUE,
                searched_at INTEGER NOT NULL
            )
        """)
    }
}
```

**文件**:
- Modify: `app/src/main/java/com/example/picsearch/data/db/AppDatabase.kt`

**验证**: 从 version 2 数据库升级到 version 3，已有 `images` 表数据不丢失。

---

### Task 4.2: 搜索线程优化

**问题**: `MainViewModel.search()` 中 `for (r in filteredRows)` 的余弦相似度计算在 `Dispatchers.IO` 协程中执行（已正确），但如果未来数据量增长（> 10K 照片），可考虑:
- 并行分块: `filteredRows.chunked(500).map { chunk -> async { ... } }.awaitAll()`
- 或升级到 `Dispatchers.Default` 利用多核

当前阶段（< 5K 照片）不需要改，仅记录为技术债务标记。

**不做**: 当前不实施，仅在本 Spec 中记录。

---

## 技术债务跟踪

| # | 问题 | 优先级 | 对应 Task | 状态 |
|---|------|--------|-----------|------|
| 1 | Worker GPS 代码完全重复 | 高 | Task 2.1 | 待实施 |
| 2 | NCNN SDK 版本落后 | 高 | Task 1.1 | 待实施 |
| 3 | listFeaturesBySceneTag 死代码 | 高 | Task 2.2 | 待实施 |
| 4 | ImageCard() 死代码 | 高 | Task 2.2 | 待实施 |
| 5 | 调试日志硬编码 | 中 | Task 2.3 | 待实施 |
| 6 | 零向量检查重复 | 中 | Task 2.3 | 待实施 |
| 7 | 无单元测试 | 中 | Task 3.1 | 待实施 |
| 8 | 数据库无 Migrations | 中 | Task 4.1 | 待实施 |
| 9 | IndexWorker 无断点续传 | 中 | 产品 Spec Task 1.3 | 产品侧解决 |
| 10 | 硬编码 topK=30 | 低 | 产品 Spec Task 3.3 | 产品侧解决 |
| 11 | 轮询间隔固定 2 秒 | 低 | 后续迭代 | 暂缓 |
| 12 | Vulkan 仍禁用 | 低 | Task 1.1 调研 | 依赖 NCNN 升级 |

---

## 成功标准

| 指标 | 当前 | 目标 |
|------|------|------|
| PC/Android CosSim 差异 | ~0.07 | < 0.01 |
| Worker 层代码重复行数 | ~80 行 | 0 行 |
| 死代码函数 | 2 个 | 0 个 |
| 调试日志（Release build） | 有 | 无 |
| 单元测试覆盖（核心逻辑） | 0% | >= 60% |
| 数据库迁移策略 | destructive | 正式 Migration 就绪 |
| `git status` 未提交 | 12 文件 | 0 文件 |

---

## 不做的事（YAGNI）

- 不引入 DI 框架（Hilt/Koin）— 当前手动依赖注入足够简单
- 不拆分更多模块（`:core`, `:data`, `:ui`）— 单模块项目规模未到需要拆分的地步
- 不引入 Compose Navigation — 单屏幕不需要路由库
- 不迁移到 Kotlin Multiplatform — 纯 Android 项目
- 不添加 CI/CD — 个人项目，本地构建即可