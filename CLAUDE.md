# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**PicSearch** — Android image search app using Chinese-CLIP (RN50) + NCNN for on-device semantic image retrieval. Users describe what they want to find in Chinese text; the app matches against indexed photo embeddings. All ML inference runs locally — no cloud API.

- **Package**: `com.example.picsearch`
- **minSdk**: 26, **targetSdk**: 36, **compileSdk**: 36
- **Kotlin**: 2.0.21, **Java**: 11
- **NDK ABIs**: `arm64-v8a`, `armeabi-v7a`

## Build Commands

All builds must be done from Android Studio or via Gradle wrapper on Windows:

```powershell
# Set JAVA_HOME (required on Windows — point to Android Studio's bundled JBR)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Build debug APK
.\gradlew app:assembleDebug

# Build release APK
.\gradlew app:assembleRelease

# Run tests
.\gradlew test

# Run Android instrumented tests (requires device/emulator)
.\gradlew connectedAndroidTest

# Clean build
.\gradlew clean
```

**Important**:
- On Windows, the Gradle wrapper script is `gradlew.bat`. Running `gradlew` (without `.bat`) or `./gradlew` (Unix style) will fail.
- `JAVA_HOME` must be set to Android Studio's bundled JDK path. Without it, Gradle exits with "JAVA_HOME is not set".
- After any change to `app/src/main/cpp/`, rebuild via `.\gradlew app:assembleDebug` (CMake handles it automatically).

## Architecture

### MVVM + Repository Pattern

```
MainActivity → PicSearchTheme → MainScreen(vm) → MainViewModel
                                                    │
                                          ┌─────────┼──────────┐
                                          ▼         ▼          ▼
                                    NcnnClip   ImageRepository  SceneClassifier
                                    tokenizer                      │
                                    extractor                      ▼
                                                         pre-computed scene vectors
```

### Key Layers

**UI Layer** (`ui/`):
- `screen/MainScreen.kt` — Single-screen app with search bar, filter entry, results grid, detail sheet
- `component/` — Reusable Composables: `ImageGrid`, `FilterEntryRow`, `ActiveFilterTags`, `EmptyStateView`, `ImageDetailSheet`, `IndexProgressView`, `SkeletonPlaceholder`
- `theme/` — Gallery Black配色 (Exaggerated Minimalism): `Color.kt`, `Theme.kt`, `Type.kt`

**ML Layer** (`ml/`):
- `NcnnClip.kt` — JNI wrapper around NCNN Chinese-CLIP RN50 model. `init(useVulkan)` controls GPU/CPU. Returns 768-dim L2-normalized embeddings.
- `ChineseTokenizer.kt` — Simple Chinese character → token ID mapper
- `FeatureExtractor.kt` — Convenience facade: `encodeImage(resolver, uri)` and `encodeText(text)`

**Data Layer** (`data/`):
- `db/` — Room database (`AppDatabase` v2): `ImageEntity` (uri, feature bytes, dateTaken, lat/lon, sceneTags), `ImageDao` with filtered queries and location clustering
- `repository/ImageRepository.kt` — Thin DAO wrapper
- `SearchFilter.kt` — Filter data classes: `TimeRange`, `LocationBounds`, `SearchFilter`, `LocationCluster`
- `SceneClassifier.kt` — CLIP-based scene classification: 10 preset labels, pre-computed text embeddings, cosine similarity, Top-2 selection (threshold 0.5)

**Workers** (`worker/`):
- `QuickIndexWorker.kt` — Indexes Top 100 most recent photos with foreground notification (~10s). Enqueues `IndexWorker` after completion.
- `IndexWorker.kt` — Background full indexing of all remaining photos.

**Utils** (`util/`):
- `BitmapLoader.kt` — Bitmap decoding with sampling
- `ExifHelper.kt` — EXIF date/location extraction (FileDescriptor + DMS fallback)
- `ReverseGeocoder.kt` — Offline reverse geocoding: lat/lon → readable address (Chinese cities + world countries)
- `FloatCodec.kt` — FloatArray ↔ ByteArray serialization (LE)

### Indexing Flow

1. User grants photo + location permission → `MainViewModel.startIndex()` enqueues `QuickIndexWorker`
2. QuickIndexWorker: queries MediaStore with `setRequireOriginal`, extracts GPS via MediaStore LATITUDE/LONGITUDE columns (primary) or EXIF fallback (Android 10+ requires `ACCESS_MEDIA_LOCATION` permission)
3. After quick indexing completes, enqueues `IndexWorker` for full background indexing
4. Both workers use `clip.init(false)` (CPU mode) for stable inference

### Search Flow

1. User enters text → `MainViewModel.search(query, filter, topK)`
2. Text encoded via `extractor.encodeText(query)` → 768-dim query vector
3. DAO returns filtered image features (by time/location/scene)
4. Cosine similarity scored on-main thread, sorted descending, topK returned
5. Scene tags from DB are parsed and attached to each `ImageScore`
6. Image detail sheet shows address text via `ReverseGeocoder.lookup()` instead of raw lat/lon coordinates

### State Management

`MainViewModel` exposes these StateFlows:
- `ready` — CLIP model initialized
- `indexedCount` — photos in DB
- `results` — current search results (`List<ImageScore>`)
- `isSearching` — search in progress
- `workProgress` — indexing progress (for Task 6 hybrid indexing)
- `clusters` — location clusters for map display
- `unlocatedCount` — photos without GPS

### Native Code

JNI library: `app/src/main/cpp/`
- `clip_jni.cpp` — JNI bindings for CLIP model inference
- `CMakeLists.txt` — CMake build config, links NCNN static libraries

Models are loaded from `assets/` at runtime (not committed to repo).

## Database

Room database: `picsearch.db`, version 2. Uses `fallbackToDestructiveMigration()` — any schema change wipes and rebuilds. Safe for development; will need proper migration for production.

Key table: `images` (uri PK, feature BLOB, dateTaken, latitude, longitude, displayName, width, height, indexedAt, scene_tags)

## Known Issues

有 3 个已知问题，详见 `.claude/rules/known-issues.md`：
1. ✅ 索引进度数字不更新（已修复：ViewModel 轮询机制）
2. ✅ 搜索不到结果（已修复：同轮询机制）
3. ✅ 图像元数据/GPS 获取失败（已修复：ACCESS_MEDIA_LOCATION + setRequireOriginal + ExifInterface FileDescriptor）

## Task Tracking

所有待实施任务统一维护在 `docs/v2/global-spec.md`（产品 + 开发合并为单一文档）。
- 完成任务后从文档中删除对应条目
- 新任务追加到文档末尾
- 每个任务一个 checkbox，完成后勾掉并删除

## Known Technical Decisions

- **Vulkan disabled** (`clip.init(false)`) — temporary for accuracy verification against PC CPU path. Re-enable with `clip.init(true)` once verified for GPU acceleration.
- **No ProGuard** (`isMinifyEnabled = false`) — release builds not minified yet.
- **Single Activity** — entire app is `MainActivity` + Compose navigation state in `MainScreen`.
- **Foreground service** — WorkManager's `SystemForegroundService` with `dataSync` type for API 34+ compliance.
- **Android 10+ GPS** — requires `ACCESS_MEDIA_LOCATION` permission + `MediaStore.setRequireOriginal()` to read EXIF location data. Without both, the system silently strips GPS from MediaStore results.
- **Offline reverse geocoding** — uses JSON boundary files in `assets/geocoding/` for lat/lon → address conversion. No network or Google Play Services dependency. Chinese cities (district-level) + world countries.

## Important Files

| File | Purpose |
|------|---------|
| `MainViewModel.kt` | Central state management, search logic, CLIP init, ReverseGeocoder init |
| `ui/screen/MainScreen.kt` | Only screen — search, filters, results, detail sheet, dual permission request |
| `worker/QuickIndexWorker.kt` | Fast indexing (Top 100) with notification, GPS via MediaStore setRequireOriginal |
| `worker/IndexWorker.kt` | Full background indexing, same GPS extraction as QuickIndexWorker |
| `data/SceneClassifier.kt` | 10 preset scene labels, pre-computed vectors |
| `data/db/ImageDao.kt` | All DB queries including scene-tag filtering |
| `ml/NcnnClip.kt` | JNI wrapper — entry point to native CLIP model |
| `util/ReverseGeocoder.kt` | Offline reverse geocoding (lat/lon → address text) |
| `assets/geocoding/` | JSON boundary files for country and Chinese city matching |
