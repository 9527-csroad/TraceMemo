# CLAUDE.md

本文件为 Claude Code 在此仓库中工作提供指导。

## 项目概述

**PicSearch** — 基于 Chinese-CLIP (RN50) + NCNN 的 Android 端侧语义图像搜索应用。用户用中文描述想要查找的照片，应用与已索引的照片嵌入做匹配。所有 ML 推理完全在本地运行，无需网络。

- **包名**: `com.example.picsearch`
- **minSdk**: 26, **targetSdk**: 36, **compileSdk**: 36
- **Kotlin**: 2.0.21, **Java**: 11
- **NDK ABI**: `arm64-v8a`, `armeabi-v7a`

## 构建命令

所有构建通过 Gradle wrapper 在 Windows 上执行：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# 构建 Debug APK
.\gradlew app:assembleDebug

# 构建 Release APK
.\gradlew app:assembleRelease

# 运行单元测试
.\gradlew test

# 运行插桩测试（需设备/模拟器）
.\gradlew connectedAndroidTest

# 清理构建
.\gradlew clean
```

**注意**:
- Windows 上 Gradle wrapper 是 `gradlew.bat`，运行 `gradlew`（无 `.bat`）或 `./gradlew`（Unix 风格）会失败。
- 必须设置 `JAVA_HOME` 指向 Android Studio 自带的 JDK。
- 修改 `app/src/main/cpp/` 后直接 `assembleDebug` 即可，CMake 自动处理。

## 架构

### MVVM + Repository 模式

```
MainActivity → PicSearchTheme → MainScreen(vm) → MainViewModel
                                                    │
                                          ┌─────────┼──────────┐
                                          ▼         ▼          ▼
                                    NcnnClip   ImageRepository  SceneClassifier
                                    tokenizer                      │
                                    extractor                      ▼
                                                         预计算场景向量
```

### 各层职责

**UI 层** (`ui/`):
- `screen/MainScreen.kt` — 单屏幕应用：搜索栏、筛选入口、结果网格、详情面板
- `component/` — 可复用组件：`ImageGrid`、`FilterEntryRow`、`ActiveFilterTags`、`EmptyStateView`、`ImageDetailSheet`、`IndexProgressView`、`SkeletonPlaceholder`
- `theme/` — Gallery Black 配色（Exaggerated Minimalism）：`Color.kt`、`Theme.kt`、`Type.kt`

**ML 层** (`ml/`):
- `NcnnClip.kt` — JNI 封装，`init(useVulkan)` 控制 GPU/CPU，返回 768 维 L2 归一化嵌入
- `ChineseTokenizer.kt` — 中文字符 → token ID 映射
- `FeatureExtractor.kt` — 门面类：`encodeImage(resolver, uri)` 和 `encodeText(text)`

**数据层** (`data/`):
- `db/` — Room 数据库 v2：`ImageEntity`（uri, feature BLOB, dateTaken, lat/lon, sceneTags），`ImageDao` 含过滤查询和位置聚类
- `repository/ImageRepository.kt` — DAO 薄封装
- `SearchFilter.kt` — 过滤数据类：`TimeRange`、`LocationBounds`、`SearchFilter`、`LocationCluster`
- `SceneClassifier.kt` — 10 个预设场景标签，预计算文本向量，余弦相似度匹配，Top-2（阈值 0.5）

**Worker 层** (`worker/`):
- `QuickIndexWorker.kt` — 前 100 张快速索引，前台通知（~10s），完成后入队 IndexWorker
- `IndexWorker.kt` — 后台全量索引

**工具类** (`util/`):
- `BitmapLoader.kt` — 图片解码 + 采样
- `ExifHelper.kt` — EXIF 日期/位置提取（FileDescriptor + DMS fallback）
- `ReverseGeocoder.kt` — 离线逆地理编码（lat/lon → 可读地名）
- `FloatCodec.kt` — FloatArray ↔ ByteArray 序列化（LE）

### 索引流程

1. 用户授权照片 + 位置权限 → `MainViewModel.startIndex()` 入队 `QuickIndexWorker`
2. QuickIndexWorker：MediaStore 查询（`setRequireOriginal`），GPS 优先走 MediaStore LATITUDE/LONGITUDE 列，备选 EXIF fallback
3. 快速索引完成后入队 `IndexWorker` 做全量后台索引
4. 两个 Worker 均使用 `clip.init(false)`（CPU 模式）保证推理稳定性

### 搜索流程

1. 用户输入文本 → `MainViewModel.search(query, filter, topK)`
2. 文本经 `extractor.encodeText(query)` 编码为 768 维查询向量
3. DAO 返回经过滤的图像特征（时间/地点/场景）
4. 在 IO 线程做余弦相似度打分，排序后取 topK
5. DB 中的 scene_tags 解析后附加到 `ImageScore`
6. 图片详情面板通过 `ReverseGeocoder.lookup()` 显示可读地名

### 状态管理

`MainViewModel` 对外暴露的 StateFlow：
- `ready` — CLIP 模型是否初始化完成
- `indexedCount` — DB 中照片数量
- `results` — 当前搜索结果 `List<ImageScore>`
- `isSearching` — 搜索进行中
- `workProgress` — 索引进度
- `clusters` — 位置聚类列表
- `unlocatedCount` — 无 GPS 的照片数
- `sceneLabels` — 10 个场景标签 displayName

## 原生代码

JNI 库位于 `app/src/main/cpp/`：
- `clip_jni.cpp` — CLIP 模型推理 JNI 绑定
- `CMakeLists.txt` — CMake 构建配置，链接 NCNN 静态库

模型文件从 `assets/` 运行时加载（不提交到 Git）。

## 数据库

Room 数据库：`picsearch.db`，版本 2，`fallbackToDestructiveMigration()`（当前阶段安全，发布前需正式 Migration）。

核心表：`images`（uri PK, feature BLOB, dateTaken, latitude, longitude, displayName, width, height, indexedAt, scene_tags）

## 任务追踪

所有待实施任务统一维护在 `docs/v2/global-spec.md`（产品 + 开发合并为单一文档）。
- 完成任务后从文档中删除对应条目
- 新任务追加到文档末尾

## 已知技术决策

- **Vulkan 禁用**（`clip.init(false)`）— 临时用 CPU 模式验证推理精度，验证通过后可开启 GPU 加速
- **无 ProGuard**（`isMinifyEnabled = false`）— 发布前需开启
- **单 Activity** — 整个应用由 `MainActivity` + Compose 状态导航组成
- **前台服务** — WorkManager `SystemForegroundService`，`dataSync` 类型（API 34+）
- **Android 10+ GPS 读取** — 必须同时持有 `ACCESS_MEDIA_LOCATION` 权限 + `MediaStore.setRequireOriginal()`，否则系统静默剥离 GPS 数据
- **离线逆地理编码** — 使用 `assets/geocoding/` 下的 JSON 边界文件，无需网络或 Google Play Services。覆盖中国城市（区县级）+ 全球国家
- **NCNN SDK** — 已升级到 20260113 版本，修复 SDPA 精度问题（Android/PC CosSim > 0.99）

## 重要文件

| 文件 | 用途 |
|------|------|
| `MainViewModel.kt` | 核心状态管理、搜索逻辑、CLIP/ReverseGeocoder 初始化 |
| `ui/screen/MainScreen.kt` | 唯一页面 — 搜索、筛选、结果、详情、双权限请求 |
| `worker/QuickIndexWorker.kt` | 快速索引（Top 100）+ 前台通知，GPS 提取 |
| `worker/IndexWorker.kt` | 后台全量索引 |
| `data/SceneClassifier.kt` | 10 个预设场景标签，预计算向量 |
| `data/db/ImageDao.kt` | 所有 DB 查询含场景标签过滤 |
| `ml/NcnnClip.kt` | JNI 入口 — 调用 Native CLIP 模型 |
| `ml/ChineseTokenizer.kt` | 中文分词（CJK 单字 + WordPiece） |
| `util/ReverseGeocoder.kt` | 离线逆地理编码 |
| `util/ExifHelper.kt` | EXIF 读取（FileDescriptor + DMS fallback） |