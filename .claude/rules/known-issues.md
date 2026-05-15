# Known Issues

## ✅ 问题 1：索引进度数字不更新（已修复）
- **文件**：`MainScreen.kt`, `MainViewModel.kt`
- **根因**：`MainViewModel.indexedCount` 只在 init 和 `refreshCount()` 中更新，Worker 运行期间不通知 ViewModel
- **修复**：`MainViewModel.init` 中添加 `while(isActive) { delay(2000); repo.count() }` 轮询循环

## ✅ 问题 2：搜索不到任何结果（已修复）
- **根因**：数据库为空时搜索无结果
- **修复**：同问题1的轮询机制

## ✅ 问题 3：图像元数据（拍摄地址/GPS）获取不到（已修复）
- **文件**：`ExifHelper.kt`, `QuickIndexWorker.kt`, `IndexWorker.kt`
- **根因**：`ExifInterface(InputStream)` 无法可靠读取 GPS 数据（InputStream 不支持随机访问），MediaStore `LATITUDE`/`LONGITUDE` 在 MIUI 上返回 0
- **修复**：`ExifHelper` 改用 `resolver.openFileDescriptor(uri, "r")` + `ExifInterface(FileDescriptor)`，支持随机访问；同时增加手动解析 GPS DMS 标签作为 fallback

## ✅ Compose LazyVerticalGrid 嵌套崩溃（已修复）
- **文件**：`ImageGrid.kt`, `MainScreen.kt`
- **根因**：`LazyVerticalGrid` 嵌套在 `Column(verticalScroll())` 中，产生无限高度约束
- **表现**：搜索后点击搜索按钮，App 直接崩溃退出
- **修复**：`ImageGrid` 改为非 lazy 的 `Column` + `Row` 布局

## ✅ 搜索结果详情页图像信息全部"未知"（已修复）
- **文件**：`MainViewModel.kt`, `ImageDao.kt`, `ImageRepository.kt`
- **根因**：`search()` 缓存 `ImageDetailData` 时硬编码了所有元数据为 null/0，因为 `listFeaturesFiltered()` 只返回 uri/feature/sceneTags，没有完整实体字段
- **修复**：新增 `listEntitiesByUris()` 批量查询，用真实 `ImageEntity` 数据填充 `ImageDetailData`
- **详情文档**：`docs/v1/2026-05-08-fix-image-detail-metadata.md`

## ✅ 底部"索引照片"按钮无响应（已修复）
- **文件**：`MainViewModel.kt`
- **根因**：`startIndex()` 使用 `ExistingWorkPolicy.KEEP`，已有任务在运行时静默忽略新请求
- **修复**：改为 `ExistingWorkPolicy.REPLACE`，确保每次点击都能触发新索引

## 修复记录
- 2026-05-08: 全部修复完成，待设备上验证
- 2026-05-08: 修复 Compose 崩溃 + GPS 读取方案优化（FileDescriptor + DMS fallback）
- 2026-05-08: 修复图像详情元数据 + 索引按钮无响应
