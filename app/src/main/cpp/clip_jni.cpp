#include <jni.h>

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <algorithm>
#include <cfloat>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>

#include <ncnn/net.h>
#include <ncnn/mat.h>

static ncnn::Net g_visual;
static ncnn::Net g_text;
static bool g_inited = false;

static bool asset_contains(AAssetManager* mgr, const char* assetpath, const char* token)
{
    if (!mgr || !assetpath || !token) return false;
    AAsset* asset = AAssetManager_open(mgr, assetpath, AASSET_MODE_BUFFER);
    if (!asset) return false;
    const off_t len = AAsset_getLength(asset);
    if (len <= 0)
    {
        AAsset_close(asset);
        return false;
    }
    std::vector<char> buf((size_t)len + 1);
    const int n = AAsset_read(asset, buf.data(), (size_t)len);
    AAsset_close(asset);
    if (n <= 0) return false;
    buf[(size_t)n] = 0;
    return std::strstr(buf.data(), token) != nullptr;
}

// Bicubic weight (Catmull-Rom, a=-0.5, 与 PIL.Image.BICUBIC 同款)
static inline float bicubic_w(float x)
{
    x = std::abs(x);
    if (x < 1.f) return ((1.5f * x - 2.5f) * x) * x + 1.f;
    if (x < 2.f) return ((-0.5f * x + 2.5f) * x - 4.f) * x + 2.f;
    return 0.f;
}

// 把 Android Bitmap 的 RGBA_8888 像素缓冲（可能含 stride）抽成紧凑 HWC RGB uint8。
// 之所以保留 alpha 不变：模型只看 RGB 三通道，alpha 在 ARGB_8888 里总是 0xFF，丢弃即可。
static std::vector<unsigned char> rgba_to_rgb_hwc(
        const unsigned char* pixels, int w, int h, int stride)
{
    std::vector<unsigned char> out((size_t)w * h * 3);
    for (int y = 0; y < h; y++)
    {
        const unsigned char* row = pixels + (size_t)y * stride;
        unsigned char* dst = out.data() + (size_t)y * w * 3;
        for (int x = 0; x < w; x++)
        {
            dst[x * 3 + 0] = row[x * 4 + 0];
            dst[x * 3 + 1] = row[x * 4 + 1];
            dst[x * 3 + 2] = row[x * 4 + 2];
        }
    }
    return out;
}

// 一维 PIL-compatible bicubic resample 系数预计算。
//
// 为什么需要 filter_scale：
//   朴素 bicubic 固定采样 4 个源像素。但当大幅缩小（比如 1920→224，scale≈8.6）时，
//   4 个像素只覆盖 src 里 4 个位置，中间大量像素根本没参与，导致严重 aliasing。
//   PIL 的做法：缩小时把 filter 支持范围按比例扩展 (support = 2*scale)，
//   每个输出像素会覆盖 ~2*support 个源像素，起到低通抗走样作用。
//   权重也要按 filter_scale 归一化：w = bicubic((x - center) / filter_scale)。
//
// 这是 cn_clip 训练/PT 推理时实际用到的 preprocess；不走这条路图像特征会漂移。
struct ResampleCoefs1D {
    int dst_size = 0;
    int k_max = 0;                  // 单个 dst 位置最大采样宽度
    std::vector<int> xmin;          // [dst_size] 每个 dst 的采样起点
    std::vector<int> xlen;          // [dst_size] 每个 dst 的采样长度
    std::vector<float> weights;     // [dst_size * k_max] 已归一化的权重
};

static ResampleCoefs1D compute_bicubic_coefs(int src_size, int dst_size)
{
    constexpr float filter_support_base = 2.0f;
    const float scale = (float)src_size / (float)dst_size;
    const float filter_scale = std::max(scale, 1.0f);
    const float support = filter_support_base * filter_scale;
    const float inv_fs = 1.0f / filter_scale;
    const int k_max = (int)std::ceil(2.0f * support) + 1;

    ResampleCoefs1D c;
    c.dst_size = dst_size;
    c.k_max = k_max;
    c.xmin.resize(dst_size);
    c.xlen.resize(dst_size);
    c.weights.assign((size_t)dst_size * k_max, 0.0f);

    for (int xx = 0; xx < dst_size; xx++)
    {
        const float center = ((float)xx + 0.5f) * scale;
        int xmin = (int)std::ceil(center - support - 0.5f);
        int xmax = (int)std::floor(center + support - 0.5f);
        xmin = std::max(0, xmin);
        xmax = std::min(src_size - 1, xmax);
        const int xlen = std::max(0, xmax - xmin + 1);

        c.xmin[xx] = xmin;
        c.xlen[xx] = xlen;

        float* wptr = c.weights.data() + (size_t)xx * k_max;
        float ww = 0.0f;
        for (int x = 0; x < xlen; x++)
        {
            const float w = bicubic_w(((float)(x + xmin) + 0.5f - center) * inv_fs);
            wptr[x] = w;
            ww += w;
        }
        // 对权重总和归一化，避免边界累积误差
        if (ww != 0.0f)
        {
            const float inv_ww = 1.0f / ww;
            for (int x = 0; x < xlen; x++) wptr[x] *= inv_ww;
        }
    }
    return c;
}

// 水平 resample: src (sh×sw×3 uint8 HWC) → mid (sh×rw×3 float32 HWC)
static void apply_resample_h_u8_to_f32(
    const unsigned char* src, int sw, int sh,
    const ResampleCoefs1D& cx,
    float* mid, int rw)
{
    for (int y = 0; y < sh; y++)
    {
        const unsigned char* src_row = src + (size_t)y * sw * 3;
        float* dst_row = mid + (size_t)y * rw * 3;
        for (int x = 0; x < rw; x++)
        {
            const int xmin = cx.xmin[x];
            const int xlen = cx.xlen[x];
            const float* wptr = cx.weights.data() + (size_t)x * cx.k_max;
            float r = 0.f, g = 0.f, b = 0.f;
            for (int i = 0; i < xlen; i++)
            {
                const float w = wptr[i];
                const unsigned char* p = src_row + (size_t)(xmin + i) * 3;
                r += w * (float)p[0];
                g += w * (float)p[1];
                b += w * (float)p[2];
            }
            dst_row[x * 3 + 0] = r;
            dst_row[x * 3 + 1] = g;
            dst_row[x * 3 + 2] = b;
        }
    }
}

// 垂直 resample + center crop + normalize：
//   mid (sh×rw×3 float32 HWC) → ncnn::Mat (3×target×target float32 CHW, 已归一化)
// 垂直方向只需要产出 [cy_start, cy_start+target) 行，水平方向只需要 [cx_start, cx_start+target) 列，
// 避免分配完整的 rh×rw 中间 buffer。
static ncnn::Mat apply_resample_v_crop_norm(
    const float* mid, int sh, int rw,
    const ResampleCoefs1D& cy,
    int target, int cx_start, int cy_start)
{
    static const float MEAN[3] = { 0.48145466f, 0.4578275f, 0.40821073f };
    static const float STD [3] = { 0.26862954f, 0.26130258f, 0.27577711f };

    ncnn::Mat out(target, target, 3, (size_t)4u);
    if (out.empty()) return out;

    float* dp[3];
    for (int c = 0; c < 3; c++) dp[c] = (float*)out.channel(c).data;

    for (int dy = 0; dy < target; dy++)
    {
        const int global_y = cy_start + dy;
        const int ymin = cy.xmin[global_y];
        const int ylen = cy.xlen[global_y];
        const float* wptr = cy.weights.data() + (size_t)global_y * cy.k_max;

        for (int dx = 0; dx < target; dx++)
        {
            const int global_x = cx_start + dx;
            float r = 0.f, g = 0.f, b = 0.f;
            for (int i = 0; i < ylen; i++)
            {
                const float w = wptr[i];
                const float* p = mid + ((size_t)(ymin + i) * rw + global_x) * 3;
                r += w * p[0];
                g += w * p[1];
                b += w * p[2];
            }
            // Clamp 到 [0,255] 再归一化，等价 PIL 的 Clip8 + ToTensor + Normalize
            r = std::max(0.f, std::min(255.f, r));
            g = std::max(0.f, std::min(255.f, g));
            b = std::max(0.f, std::min(255.f, b));

            dp[0][(size_t)dy * target + dx] = (r - MEAN[0] * 255.f) / (STD[0] * 255.f);
            dp[1][(size_t)dy * target + dx] = (g - MEAN[1] * 255.f) / (STD[1] * 255.f);
            dp[2][(size_t)dy * target + dx] = (b - MEAN[2] * 255.f) / (STD[2] * 255.f);
        }
    }
    return out;
}

// CLIP 图像预处理（完全对齐 cn_clip 官方 torchvision/PIL preprocess）：
//   Resize(short_side=224, BICUBIC with filter scaling)
//   → CenterCrop(224)
//   → ToTensor (/255)
//   → Normalize(mean, std)
// 输入：src HWC uint8 RGB（紧凑），sw×sh
// 输出：ncnn::Mat CHW float32，3×224×224
static ncnn::Mat preprocess_clip_vision(const unsigned char* src, int sw, int sh)
{
    constexpr int target = 224;

    // 短边 resize 到 target
    const int shorter = std::min(sw, sh);
    const float scale = (float)target / (float)shorter;
    const int rw = std::max(target, (int)std::lroundf((float)sw * scale));
    const int rh = std::max(target, (int)std::lroundf((float)sh * scale));

    const int cx_start = (rw - target) / 2;
    const int cy_start = (rh - target) / 2;

    // 预计算两个方向的重采样系数
    const ResampleCoefs1D coefs_x = compute_bicubic_coefs(sw, rw);
    const ResampleCoefs1D coefs_y = compute_bicubic_coefs(sh, rh);

    __android_log_print(ANDROID_LOG_DEBUG, "CLIP_DEBUG",
        "[preprocess v2 pil-bicubic] src=%dx%d resize=%dx%d kx=%d ky=%d",
        sw, sh, rw, rh, coefs_x.k_max, coefs_y.k_max);

    // 中间 buffer：(sh × rw × 3) float32；避免 full (rh × rw × 3) 分配
    std::vector<float> mid((size_t)sh * rw * 3);
    apply_resample_h_u8_to_f32(src, sw, sh, coefs_x, mid.data(), rw);

    return apply_resample_v_crop_norm(mid.data(), sh, rw, coefs_y, target, cx_start, cy_start);
}

static inline void l2_normalize(float* v, int n)
{
    float s = 0.f;
    for (int i = 0; i < n; i++) s += v[i] * v[i];
    s = std::sqrt(s);
    if (s <= 0.f) return;
    const float inv = 1.f / s;
    for (int i = 0; i < n; i++) v[i] *= inv;
}

static bool mat_to_float_vec(const ncnn::Mat& m, std::vector<float>& out)
{
    if (m.empty()) return false;

    ncnn::Mat fp32;
    const ncnn::Mat* src = &m;

    if (m.elemsize == 4u)
    {
        // ok
    }
    else if (m.elemsize == 2u)
    {
        ncnn::cast_float16_to_float32(m, fp32);
        src = &fp32;
    }
    else if (m.elemsize == 1u)
    {
        ncnn::cast_int8_to_float32(m, fp32);
        src = &fp32;
    }
    else
    {
        return false;
    }

    if (src->elemsize != 4u) return false;

    const int dim = (int)src->total();
    if (dim <= 0) return false;

    out.resize(dim);
    const float* p = (const float*)src->data;
    for (int i = 0; i < dim; i++) out[i] = p[i];
    return true;
}

static inline void set_net_opt(ncnn::Net& net, bool use_vulkan)
{
    net.opt = ncnn::Option();
#if NCNN_VULKAN
    net.opt.use_vulkan_compute = use_vulkan && (ncnn::get_gpu_count() > 0);
#else
    (void)use_vulkan;
#endif
    net.opt.lightmode = true;
    net.opt.num_threads = 4;
    net.opt.use_packing_layout = false;
    net.opt.use_fp16_packed = false;
    net.opt.use_fp16_storage = false;
    net.opt.use_fp16_arithmetic = false;
    net.opt.use_bf16_storage = false;
    net.opt.use_int8_inference = false;
    net.opt.use_int8_packed = false;
    net.opt.use_int8_storage = false;
    net.opt.use_int8_arithmetic = false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_picsearch_ml_NcnnClip_initNative(
        JNIEnv* env, jobject /*thiz*/, jobject assetManager, jboolean useVulkan)
{
    if (g_inited) return JNI_TRUE;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) return JNI_FALSE;

    g_visual.clear();
    g_text.clear();

    const bool visual_has_sdpa = asset_contains(mgr, "clip_vision.param", "SDPA");
    const bool text_has_sdpa = asset_contains(mgr, "clip_text.param", "SDPA");
    set_net_opt(g_visual, (useVulkan == JNI_TRUE) && !visual_has_sdpa);
    set_net_opt(g_text, (useVulkan == JNI_TRUE) && !text_has_sdpa);

    if (g_visual.load_param(mgr, "clip_vision.param") != 0) return JNI_FALSE;
    if (g_visual.load_model(mgr, "clip_vision.bin") != 0) return JNI_FALSE;
    if (g_text.load_param(mgr, "clip_text.param") != 0) return JNI_FALSE;
    if (g_text.load_model(mgr, "clip_text.bin") != 0) return JNI_FALSE;

    g_inited = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_picsearch_ml_NcnnClip_encodeImageNative(
        JNIEnv* env, jobject /*thiz*/, jobject bitmap)
{
    if (!g_inited || !bitmap) return nullptr;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return nullptr;
    if (info.width == 0 || info.height == 0) return nullptr;

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return nullptr;
    if (!pixels)
    {
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }

    // 第一步：把输入 bitmap 统一成 HWC uint8 RGB（紧凑布局），便于后续 bicubic 采样
    std::vector<unsigned char> rgb_hwc;
    const int src_w = (int)info.width;
    const int src_h = (int)info.height;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888)
    {
        const unsigned char* p = (const unsigned char*)pixels;
        __android_log_print(ANDROID_LOG_DEBUG, "CLIP_DEBUG",
            "Pixel[0] bytes: %02x %02x %02x %02x (RGBA or BGRA?), size=%dx%d stride=%d",
            p[0], p[1], p[2], p[3], src_w, src_h, (int)info.stride);
        rgb_hwc = rgba_to_rgb_hwc(p, src_w, src_h, (int)info.stride);
    }
    else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565)
    {
        rgb_hwc.resize((size_t)src_w * src_h * 3);
        for (int y = 0; y < src_h; y++)
        {
            const uint16_t* row = (const uint16_t*)((const unsigned char*)pixels + (size_t)y * info.stride);
            unsigned char* out = rgb_hwc.data() + (size_t)y * src_w * 3;
            for (int x = 0; x < src_w; x++)
            {
                const uint16_t p = row[x];
                const unsigned char r5 = (p >> 11) & 31;
                const unsigned char g6 = (p >> 5) & 63;
                const unsigned char b5 = p & 31;
                out[x * 3 + 0] = (unsigned char)((r5 << 3) | (r5 >> 2));
                out[x * 3 + 1] = (unsigned char)((g6 << 2) | (g6 >> 4));
                out[x * 3 + 2] = (unsigned char)((b5 << 3) | (b5 >> 2));
            }
        }
    }
    else
    {
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    // 第二步：bicubic 短边 resize + center crop + normalize，完全对齐 PIL preprocess
    ncnn::Mat in = preprocess_clip_vision(rgb_hwc.data(), src_w, src_h);
    if (in.empty()) return nullptr;

    const float* pR = (const float*)in.channel(0).data;
    const float* pG = (const float*)in.channel(1).data;
    const float* pB = (const float*)in.channel(2).data;
    __android_log_print(ANDROID_LOG_DEBUG, "CLIP_DEBUG",
                        "Pre-net R[0..4] = %.6f %.6f %.6f %.6f %.6f", pR[0], pR[1], pR[2], pR[3], pR[4]);
    __android_log_print(ANDROID_LOG_DEBUG, "CLIP_DEBUG",
                        "Pre-net G[0..4] = %.6f %.6f %.6f %.6f %.6f", pG[0], pG[1], pG[2], pG[3], pG[4]);
    __android_log_print(ANDROID_LOG_DEBUG, "CLIP_DEBUG",
                        "Pre-net B[0..4] = %.6f %.6f %.6f %.6f %.6f", pB[0], pB[1], pB[2], pB[3], pB[4]);

    ncnn::Extractor ex = g_visual.create_extractor();
    ex.input("in0", in);

    ncnn::Mat out;
    if (ex.extract("out0", out) != 0) return nullptr;

    std::vector<float> feat;
    if (!mat_to_float_vec(out, feat)) return nullptr;
    const int dim = (int)feat.size();

    __android_log_print(ANDROID_LOG_DEBUG, "CLIP_DEBUG", "Image feat before L2 [0..4]: %f %f %f %f %f", 
        feat[0], feat[1], feat[2], feat[3], feat[4]);

    l2_normalize(feat.data(), dim);

    __android_log_print(ANDROID_LOG_DEBUG, "CLIP_DEBUG", "Image feat after L2 [0..4]: %f %f %f %f %f, dim=%d", 
        feat[0], feat[1], feat[2], feat[3], feat[4], dim);

    jfloatArray jarr = env->NewFloatArray(dim);
    if (!jarr) return nullptr;
    env->SetFloatArrayRegion(jarr, 0, dim, feat.data());
    return jarr;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_picsearch_ml_NcnnClip_encodeTextNative(
        JNIEnv* env, jobject /*thiz*/, jintArray tokenIds)
{
    if (!g_inited || !tokenIds) return nullptr;

    const jsize n = env->GetArrayLength(tokenIds);
    if (n != 52) return nullptr;

    jint* ids = env->GetIntArrayElements(tokenIds, nullptr);
    if (!ids) return nullptr;

    // in0: int32 token IDs (Embed bitcast)
    ncnn::Mat in0(52, (size_t)4u);
    int* pids = (int*)in0.data;
    for (int i = 0; i < 52; i++) pids[i] = (int)ids[i];

    // in1: float32 attention mask
    ncnn::Mat in1(52, (size_t)4u);
    float* mask = (float*)in1.data;
    for (int i = 0; i < 52; i++) mask[i] = (ids[i] != 0) ? 1.0f : 0.0f;

    env->ReleaseIntArrayElements(tokenIds, ids, JNI_ABORT);

    ncnn::Extractor ex = g_text.create_extractor();
    ex.input("in0", in0);
    ex.input("in1", in1);

    ncnn::Mat out;
    if (ex.extract("out0", out) != 0) return nullptr;

    std::vector<float> feat;
    if (!mat_to_float_vec(out, feat)) return nullptr;
    const int dim = (int)feat.size();

    __android_log_print(ANDROID_LOG_DEBUG, "CLIP_DEBUG", "Text feat before L2 [0..4]: %f %f %f %f %f", 
        feat[0], feat[1], feat[2], feat[3], feat[4]);

    l2_normalize(feat.data(), dim);

    __android_log_print(ANDROID_LOG_DEBUG, "CLIP_DEBUG", "Text feat after L2 [0..4]: %f %f %f %f %f, dim=%d", 
        feat[0], feat[1], feat[2], feat[3], feat[4], dim);

    jfloatArray jarr = env->NewFloatArray(dim);
    if (!jarr) return nullptr;
    env->SetFloatArrayRegion(jarr, 0, dim, feat.data());
    return jarr;
}

