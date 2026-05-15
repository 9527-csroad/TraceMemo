# 索引流程重新设计 Spec

**日期**: 2026-05-15
**状态**: 待实施
**相关文件**: `docs/v2/global-spec.md` (Dev-8)

## 问题

1. 每次打开 App 都全屏遮罩显示索引进度，用户无法使用搜索功能
2. 点击底部"索引照片"按钮不能正确继续全量索引
3. 底部按钮太大太丑，抢夺搜索功能视觉焦点
4. 索引总数显示为 `50/...` 而非真实数字

## 设计决策

### 1. 索引完全依赖 App 运行

关闭 App（退出/杀后台）即取消 Worker，不使用前台 Service。Worker 取消通过 `WorkManager.cancelAllWorkByTag()` 或 `cancelWorkById()` 实现。

### 2. Header Pill 指示器（替换底部按钮）

将底部全宽按钮改为 Header 右侧的紧凑状态指示器。

三种状态：
- **未索引**: 紫色 `开始索引 ▸` → 点击进入索引选择页
- **索引中**: 深色背景 + 绿色呼吸点 + `N / 总数 ▸` → 点击进入索引详情页
- **已完成**: 绿色 `✓ 已完成` → 点击可重建索引

尺寸: height=28dp, rounded=16dp, font=12sp, weight=500

### 3. 首次启动流程

DB=0 时显示索引选择页：
- 显示检测到的照片总数（首次轻量 COUNT 查询 MediaStore）
- 两个选项：快速 Top 100 / 索引全部
- 快速 Top 100 完成后自动进入搜索页
- 索引全部时，前 100 张完成后也自动进入搜索页

### 4. 再次启动流程

- 直接进入搜索页
- 判断索引状态：
  - 全部完成 → Header Pill 显示「已完成」
  - 未完成 → 弹窗提示「还有 X 张未索引」
    - 继续 → 启动 IndexWorker
    - 稍后 → 不启动 Worker

### 5. 索引进度页

改为可退出的独立页面，不再是全屏遮罩。用户点击返回回到搜索页，索引继续在后台运行。

### 6. 底部区域

不再保留任何索引相关按钮，搜索结果区直接铺满。

## 状态机

| 触发 | UI 页面 | Header Pill | 弹窗 | Worker |
|------|---------|-------------|------|--------|
| 首次打开 (DB=0) | 索引选择页 | 无 | 无 | 无 |
| 点击「快速 Top 100」 | 索引进度页 | 无（被遮挡） | 无 | QuickIndexWorker |
| Top 100 完成 | 搜索页 | 100/总数 ▸ | 无 | IndexWorker 入队 |
| 再次打开 (未完成) | 搜索页 | 当前状态 | 是 | 按用户选择 |
| 全部完成 | 搜索页 | ✓ 已完成 | 无 | 无 |

## 涉及文件

- Create: `ui/component/HeaderIndexPill.kt`
- Create: `ui/component/IndexChoicePage.kt`
- Modify: `MainViewModel.kt` — totalPhotoCount, isIndexComplete, continueIndexing, hasShownResumeDialog
- Modify: `ui/screen/MainScreen.kt` — 移除全屏遮罩, 新增 Header Pill, 新增选择页, 新增弹窗
- Modify: `ui/component/IndexProgressView.kt` — 改为可退出页面
