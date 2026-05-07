# 图像向量端侧与 PC 不一致问题记录

## 问题描述

模型从 PT 转为 NCNN 后，PC 端（Linux x86_64）测试时，同一张图像和同一段文本输出的向量完全一致。但部署到 Android 端后，**文本向量仍然一致，图像向量出现明显偏差**（CosSim ~0.93）。

## 排查过程

### 1. 预处理链路排查（已排除）

早期怀疑 Android 端 `BitmapLoader.inSampleSize` 的 JPEG DCT 降采样与 PIL bicubic 不一致，以及 EXIF 旋转未对齐。经分析，当前 `BitmapLoader.kt` 仅做 `inSampleSize` 粗解码 + EXIF 旋转，真正的 bicubic 短边 resize + center crop + normalize 在 C++ 侧 `clip_jni.cpp` 已完成，与 PIL 完全对齐。预处理差异预估 CosSim 影响 < 0.01，不足以解释 0.07 的偏差。

### 2. 根因定位：SDPA 算子实现不一致

| 端 | ncnn 版本 | SDPA 实现 | 来源 |
|---|---|---|---|
| Linux x86_64 | 较新（含 SDPA） | **ncnn 内置 SDPA layer** | `load_param rc=0`，无需手动注册 |
| Android ARM | `ncnn-20250916-android-vulkan` | **手写 fallback SDPA** | `clip_jni.cpp:41-139` 朴素三重循环实现 |

关键日志证据：
```
ncnn: layer SDPA not exists or registered    ← 注释掉 register_custom_layer 后崩溃
```

`clip_jni.cpp` 中：
```cpp
g_visual.register_custom_layer("SDPA", SDPA_layer_creator, SDPA_layer_destroyer);
g_text.register_custom_layer("SDPA", SDPA_layer_creator, SDPA_layer_destroyer);
```

这两行代码强制将所有 SDPA 层替换为手写 fallback 实现。Android ncnn `20250916` 版本**没有内置 SDPA**，不注册就 crash。Linux 端 ncnn 版本较新，内置了 SDPA，`load_param rc=0` 说明直接识别了该 layer，无需注册。

**两边用的是两份不同的 SDPA 代码**，数值精度自然不同。RN50 vision tower 有大量 Attention 层，每层微小差异累积到输出，导致 CosSim 下降。

### 3. 为什么文本不受影响？

Text 模型的 `.param` 中**不含 SDPA 层**。`onnx2ncnn` 转换 text 模型时，Attention 被拆成了 `MatMul + Softmax + MatMul` 的标准算子组合。这些都是 ncnn 最成熟的内置 layer，PC/Android 两端实现一致。

## 解决方案

### 方案 A：升级 Android ncnn SDK（推荐，一行配置改动）

1. 从 https://github.com/Tencent/ncnn/releases/tag/20260113 下载 `ncnn-20260113-android-vulkan.zip`
2. 替换 `app/src/main/jni/ncnn-20250916-android-vulkan/` 目录
3. 更新 `app/src/main/cpp/CMakeLists.txt` 中的 `ncnn_DIR` 路径（版本号变更）
4. 删掉 `clip_jni.cpp` 中的 `register_custom_layer("SDPA", ...)` 两行
5. 可以保留手写 SDPA 类（不注册就不生效），或整个 SDPA 类（41-149 行）直接删掉
6. 重新 build & install

**预期结果**：Android/Linux 两端都走 ncnn 内置 SDPA，CosSim ≥ 0.9999。

### 方案 B：ONNX 导出阶段拆开 SDPA（兜底，不改 ncnn 版本）

修改 `convert_to_ncnn.py` 的 `ImageEncoder.forward`，在导出前替换 `F.scaled_dot_product_attention` 为等价的 `matmul → scale → softmax → matmul` 显式实现。这样生成的 ONNX 不含 SDPA 算子，转换出的 .param 只有 MatMul/Softmax/MatMul。

缺点是：
- 改动 Python 导出脚本，需要重新导出模型
- ONNX 图膨胀（每层 Attention 展开为 5-6 个算子）
- 不能享受 ncnn 内置 SDPA 的性能优化

### 方案 C：同步更新 Android 端手写 SDPA 实现（临时，不推荐）

把手写 SDPA 改成与 Linux ncnn 内置 SDPA 完全相同的数值实现。缺点是需要逆向 ncnn 内置实现，且每次 ncnn 更新都要同步，维护成本高。

## 相关代码文件

| 文件 | 作用 |
|---|---|
| `app/src/main/cpp/clip_jni.cpp` | SDPA 手写实现（第 41-149 行）、注册（第 440-441 行） |
| `app/src/main/cpp/CMakeLists.txt` | ncnn SDK 路径引用 |
| `app/src/main/jni/ncnn-20250916-android-vulkan/` | 当前 Android ncnn SDK |
| `convert_to_ncnn.py` | ONNX 导出脚本（方案 B 需要改动） |

## 参考链接

- ncnn releases: https://github.com/Tencent/ncnn/releases
- ncnn 20260113 release notes（首次引入 SDPA）: https://github.com/Tencent/ncnn/releases/tag/20260113
