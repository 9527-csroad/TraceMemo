# PicSearch

基于 Chinese-CLIP (RN50) + NCNN 的 Android 端侧语义图像搜索应用。用中文自然语言描述照片，全部 ML 推理在本地完成，无需网络、保护隐私。

## 项目结构

```
picsearch/
├── app/src/main/
│   ├── java/com/example/picsearch/
│   │   ├── MainActivity.kt          # 唯一 Activity，入口
│   │   ├── MainViewModel.kt         # 核心状态管理、搜索逻辑
│   │   ├── data/                    # 数据层
│   │   │   ├── db/                  # Room 数据库（Entity + DAO）
│   │   │   ├── repository/          # DAO 薄封装
│   │   │   ├── SearchFilter.kt      # 筛选数据模型
│   │   │   └── SceneClassifier.kt   # CLIP 场景分类器（10 标签）
│   │   ├── ml/                      # ML 推理层
│   │   │   ├── NcnnClip.kt          # JNI 封装，NCNN 模型调用
│   │   │   ├── ChineseTokenizer.kt  # 中文分词（CJK + WordPiece）
│   │   │   └── FeatureExtractor.kt  # 特征提取门面
│   │   ├── ui/                      # UI 层
│   │   │   ├── screen/              # MainScreen — 唯一页面
│   │   │   ├── component/           # 可复用 Compose 组件（10 个）
│   │   │   └── theme/               # Gallery Black 配色方案
│   │   ├── worker/                  # 后台索引 Worker
│   │   │   ├── QuickIndexWorker.kt  # Top 100 快速预览（前台通知）
│   │   │   └── IndexWorker.kt       # 全量后台索引
│   │   └── util/                    # 工具类
│   │       ├── ExifHelper.kt        # EXIF 日期/位置提取
│   │       ├── ReverseGeocoder.kt   # 离线逆地理编码
│   │       ├── BitmapLoader.kt      # 图片解码采样
│   │       └── FloatCodec.kt        # Float ↔ Byte 序列化
│   ├── cpp/                         # JNI 原生层
│   │   ├── clip_jni.cpp             # CLIP 推理 JNI 绑定
│   │   └── CMakeLists.txt           # CMake 构建配置
│   └── assets/                      # 运行时资源（不提交 Git）
│       ├── clip_vision.bin/param    # 图像编码模型
│       ├── clip_text.bin/param      # 文本编码模型
│       ├── vocab.txt                # 中文词表（22K）
│       └── geocoding/               # 离线地名 JSON
├── docs/                            # 设计文档和任务追踪
├── gradle/                          # Gradle wrapper
├── CLAUDE.md                        # Claude Code 协作指南
└── README.md                        # 本文件
```

## 技术栈

| 层面 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.21 / C++ (JNI) |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room 2.6.1 |
| 后台任务 | WorkManager 2.9.0 |
| 推理引擎 | NCNN 20260113 |
| 图像加载 | Coil Compose |
| 模型 | Chinese-CLIP RN50（768 维） |

## 构建

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew app:assembleDebug
```

详见 `CLAUDE.md`。

## 特性

- 中文语义搜索 — Chinese-CLIP 原生中文理解
- 全本地推理 — 无需网络，隐私安全
- 混合索引 — 前 100 张 ~10 秒可搜索，后台全量静默完成
- 多维筛选 — 时间范围 + 地点聚类 + 场景标签（10 个预设）
- 场景分类 — CLIP 双编码器自动标注照片场景
- 离线地名 — lat/lon → 可读地址，无 Google Play Services 依赖