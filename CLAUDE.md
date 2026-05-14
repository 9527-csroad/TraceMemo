# CLAUDE.md

本文件为 Claude Code 在此仓库中工作提供指导。

## 项目概述

**PicSearch** — 基于 Chinese-CLIP (RN50) + NCNN 的 Android 端侧语义图像搜索。用户用中文描述照片，应用与已索引的照片嵌入做余弦匹配。所有 ML 推理本地运行，无需网络。

- **包名**: `com.example.picsearch` | **minSdk**: 26 | **targetSdk**: 36
- **Kotlin**: 2.0.21 | **Java**: 11 | **NDK ABI**: `arm64-v8a`, `armeabi-v7a`

## 构建命令

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew app:assembleDebug    # 构建 Debug
.\gradlew app:assembleRelease  # 构建 Release
.\gradlew test                 # 单元测试
.\gradlew clean                # 清理
```

- **必须使用 PowerShell 工具执行构建命令**，不要用 Bash（`gradlew.bat` 在 `/usr/bin/bash` 下不可用）
- 示例：`PowerShell('$env:JAVA_HOME = "..."; .\gradlew.bat app:assembleDebug')`
- Windows 上 `gradlew`（无后缀）PowerShell 可自动查找 `.bat`，但 Bash 不行
- 必须设置 `JAVA_HOME` 指向 Android Studio 自带的 JDK
- 修改 `app/src/main/cpp/` 后直接 `assembleDebug`，CMake 自动处理

## 禁止引入

除非明确要求，不得引入：
- **Hilt / Dagger / Koin** — 手动 DI 已足够
- **Navigation Compose** — 单 Activity 单屏幕
- **Retrofit / OkHttp** — 全离线，无网络请求
- **RxJava / LiveData** — 全部用 StateFlow + Compose
- **不新建 ViewModel** — 只有一个 MainViewModel，新逻辑加在里面

## 架构（MVVM + Repository，单 Activity）

```
MainActivity → MainScreen(vm) → MainViewModel → NcnnClip / ImageRepository / SceneClassifier
```

各层代码即文档，入口：

| 层 | 入口文件 | 目录 |
|----|---------|------|
| UI | `MainScreen.kt` | `ui/screen/`, `ui/component/`, `ui/theme/` |
| 状态 | `MainViewModel.kt` | 根目录单文件 |
| 数据 | `ImageRepository.kt` → `ImageDao.kt` | `data/db/`, `data/repository/`, `data/SearchFilter.kt`, `data/SceneClassifier.kt` |
| ML | `NcnnClip.kt` → `FeatureExtractor.kt` | `ml/` |
| Worker | `QuickIndexWorker.kt`, `IndexWorker.kt` | `worker/` |
| 工具 | `ExifHelper.kt`, `ReverseGeocoder.kt`, `BitmapLoader.kt`, `FloatCodec.kt` | `util/` |
| 原生 | `clip_jni.cpp` | `app/src/main/cpp/` |
| 模型 | clip_vision/ clip_text/ vocab.txt/ geocoding/ | `app/src/main/assets/`（不提交 Git） |

### 核心流程

**索引**: 权限授权 → `startIndex()` 入队 QuickIndexWorker（Top 100, ~10s, 前台通知）→ 完成后入队 IndexWorker（后台全量）。两个 Worker 均 `clip.init(false)` CPU 模式。

**搜索**: 中文输入 → `extractor.encodeText()` → 768 维查询向量 → DAO 过滤（时间/地点）→ IO 线程余弦相似度 → Kotlin 层场景 post-filter（OR 逻辑）→ topK 排序 → `ImageScore` 含 sceneTags。

**关键 StateFlow**: `ready`, `indexedCount`, `results`, `isSearching`, `clusters`, `unlocatedCount`, `sceneLabels`

## 行为约束

- 推理用 `clip.init(false)` CPU 模式，不要改成 Vulkan
- Android 10+ GPS 必须同时用 `ACCESS_MEDIA_LOCATION` + `MediaStore.setRequireOriginal()`
- DB schema 变更用 `fallbackToDestructiveMigration()`，不写 Migration（当前阶段）
- 场景筛选在 Kotlin 层做 post-filter，不改 SQL
- 新增 DB 表通过 `AppDatabase.kt`，version 号递增
- 不改动 `app/src/main/cpp/` 除非明确要求（CMake 重编成本高）
- 每次代码修改后运行 `.\gradlew app:assembleDebug` 验证编译

## 数据库

Room `picsearch.db` v2，核心表 `images`（uri PK, feature BLOB, dateTaken, latitude, longitude, displayName, width, height, indexedAt, scene_tags）。

## 任务追踪

所有待实施任务在 `docs/v2/global-spec.md`。完成后删除条目，新任务追加到末尾。

## 跨会话记忆

`MEMORY.md` 记录之前任务中的关键发现、陷阱和最佳实践。新任务前读取，任务后有新发现则追加。

## 协作风格

- 先给方案再写代码，不直接动手
- 不确定时列出选项，不猜测
- 回复用中文，代码用英文
- 不需写的注释不留，不留注释掉的代码
- 不做未被要求的功能和重构

## 文档索引

- 全局任务：`docs/v2/global-spec.md`
- 优化设计：`docs/picsearch-optimization-design.md`
- GPS 方案：`docs/GPS/GPS定位方案.md`
- 问题排查：`docs/issue_image_vector_mismatch.md`（SDPA 精度）