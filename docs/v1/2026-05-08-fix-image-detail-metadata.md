# 解决图像信息不完整问题

## 问题描述

搜索结果详情页（ImageDetailSheet）中，所有图像的元数据均显示为"未知"或"0×0"：
- 拍摄时间：未知
- 拍摄地点：未知
- 尺寸：0 × 0
- 文件名：未知

## 根因分析

`MainViewModel.search()` 方法在缓存搜索结果到 `_imageDetails` 时，硬编码了所有元数据字段：

```kotlin
// search() 中
val details = scored.associate { score ->
    score.uri to ImageDetailData(
        uri = score.uri,
        displayName = null,       // ← 硬编码 null
        width = 0,                // ← 硬编码 0
        height = 0,               // ← 硬编码 0
        dateTaken = null,         // ← 硬编码 null
        latitude = null,          // ← 硬编码 null
        longitude = null,         // ← 硬编码 null
        sceneTags = score.sceneTags,
    )
}
```

**原因**：`listFeaturesFiltered()` 查询只返回 `UriFeature`（uri, feature, sceneTags），没有完整的 `ImageEntity` 元数据字段（displayName, width, height, dateTaken, latitude, longitude）。

## 修复方案

### 方案：批量查询完整实体数据

1. 在 `ImageDao` 中新增 `listEntitiesByUris(uris: List<String>)` 查询，用 `SELECT * FROM images WHERE uri IN (:uris)` 批量获取完整实体
2. 在 `ImageRepository` 中包装此方法
3. 在 `MainViewModel.search()` 中，搜索完成后用 topK 的 uris 批量查询完整实体，将真实元数据填充到 `ImageDetailData`

### 代码变更

**ImageDao.kt** — 新增查询：
```kotlin
@Query("SELECT * FROM images WHERE uri IN (:uris)")
suspend fun listEntitiesByUris(uris: List<String>): List<ImageEntity>
```

**ImageRepository.kt** — 新增方法：
```kotlin
suspend fun listEntitiesByUris(uris: List<String>): List<ImageEntity> = dao.listEntitiesByUris(uris)
```

**MainViewModel.kt** — search() 中填充真实数据：
```kotlin
val topUris = scored.take(k).map { it.uri }
val entities = repo.listEntitiesByUris(topUris).associateBy { it.uri }
val details = scored.take(k).associate { score ->
    val entity = entities[score.uri]
    score.uri to ImageDetailData(
        uri = score.uri,
        displayName = entity?.displayName,
        width = entity?.width ?: 0,
        height = entity?.height ?: 0,
        dateTaken = entity?.dateTaken,
        latitude = entity?.latitude,
        longitude = entity?.longitude,
        sceneTags = score.sceneTags,
    )
}
```

### 附加修复：底部"索引照片"按钮无响应

底部按钮通过权限启动器触发 `vm.startIndex()`。权限已授予时 Android 立即返回 `true`，调用 `vm.startIndex()`。但 `startIndex()` 使用 `ExistingWorkPolicy.KEEP`，如果已有同名 work 在运行则静默忽略，按钮表现为"点了没反应"。

**修复**：改为 `ExistingWorkPolicy.REPLACE`，确保每次点击都能触发新的索引任务。

```kotlin
// MainViewModel.kt startIndex()
workManager.enqueueUniqueWork("quick_index", ExistingWorkPolicy.REPLACE, quickRequest)
```

## 涉及文件

- `app/src/main/java/com/example/picsearch/data/db/ImageDao.kt`
- `app/src/main/java/com/example/picsearch/data/repository/ImageRepository.kt`
- `app/src/main/java/com/example/picsearch/MainViewModel.kt`

## 修复日期

2026-05-08
