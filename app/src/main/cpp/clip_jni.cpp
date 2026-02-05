#include <jni.h>

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

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

class SDPA final : public ncnn::Layer
{
public:
    SDPA()
    {
        one_blob_only = false;
        support_inplace = false;
        support_vulkan = false;
        support_packing = false;
    }

    int load_param(const ncnn::ParamDict& pd) override
    {
        causal = pd.get(5, 0);
        return 0;
    }

    int forward(const std::vector<ncnn::Mat>& bottom_blobs, std::vector<ncnn::Mat>& top_blobs, const ncnn::Option& opt) const override
    {
        if (bottom_blobs.size() < 3) return -1;
        const ncnn::Mat& q = bottom_blobs[0];
        const ncnn::Mat& k = bottom_blobs[1];
        const ncnn::Mat& v = bottom_blobs[2];
        if (q.dims != 3 || k.dims != 3 || v.dims != 3) return -1;
        if (q.w != k.w || q.h != k.h || q.c != k.c) return -1;
        if (q.w != v.w || q.h != v.h || q.c != v.c) return -1;

        const int d = q.w;
        const int n = q.h;
        const int heads = q.c;
        if (d <= 0 || n <= 0 || heads <= 0) return -1;

        ncnn::Mat& out = top_blobs[0];
        out.create(d, n, heads, 4u, 1, opt.blob_allocator);
        if (out.empty()) return -100;

        const float scale = 1.f / std::sqrt((float)d);
        std::vector<float> attn((size_t)n);

        for (int hc = 0; hc < heads; hc++)
        {
            const ncnn::Mat qh = q.channel(hc);
            const ncnn::Mat kh = k.channel(hc);
            const ncnn::Mat vh = v.channel(hc);
            ncnn::Mat oh = out.channel(hc);

            for (int i = 0; i < n; i++)
            {
                const float* qi = qh.row(i);
                float maxv = -FLT_MAX;
                for (int j = 0; j < n; j++)
                {
                    if (causal && j > i)
                    {
                        attn[(size_t)j] = -FLT_MAX;
                        continue;
                    }
                    const float* kj = kh.row(j);
                    float dot = 0.f;
                    for (int t = 0; t < d; t++) dot += qi[t] * kj[t];
                    const float s = dot * scale;
                    attn[(size_t)j] = s;
                    if (s > maxv) maxv = s;
                }

                float sum = 0.f;
                for (int j = 0; j < n; j++)
                {
                    float x = attn[(size_t)j];
                    if (x <= -FLT_MAX / 2)
                    {
                        attn[(size_t)j] = 0.f;
                        continue;
                    }
                    x = std::exp(x - maxv);
                    attn[(size_t)j] = x;
                    sum += x;
                }
                const float invsum = sum > 0.f ? (1.f / sum) : 0.f;
                for (int j = 0; j < n; j++) attn[(size_t)j] *= invsum;

                float* oi = oh.row(i);
                for (int t = 0; t < d; t++) oi[t] = 0.f;
                for (int j = 0; j < n; j++)
                {
                    const float a = attn[(size_t)j];
                    if (a == 0.f) continue;
                    const float* vj = vh.row(j);
                    for (int t = 0; t < d; t++) oi[t] += a * vj[t];
                }
            }
        }

        return 0;
    }

private:
    int causal = 0;
};

ncnn::Layer* SDPA_layer_creator(void* /*userdata*/)
{
    return new SDPA;
}

void SDPA_layer_destroyer(ncnn::Layer* layer, void* /*userdata*/)
{
    delete layer;
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
    net.opt.use_int8_inference = false;
    net.opt.use_bf16_storage = false;
    net.opt.use_fp16_packed = false;
    net.opt.use_fp16_storage = false;
    net.opt.use_fp16_arithmetic = false;
    net.opt.use_int8_packed = false;
    net.opt.use_int8_storage = false;
    net.opt.use_int8_arithmetic = false;
    net.opt.use_packing_layout = false;
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

    g_visual.register_custom_layer("SDPA", SDPA_layer_creator, SDPA_layer_destroyer);
    const bool visual_has_sdpa = asset_contains(mgr, "clip_vision.param", "SDPA");
    set_net_opt(g_visual, (useVulkan == JNI_TRUE) && !visual_has_sdpa);
    set_net_opt(g_text, useVulkan == JNI_TRUE);

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

    // CLIP expects RGB
    ncnn::Mat in;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888)
    {
        in = ncnn::Mat::from_pixels_resize((const unsigned char*)pixels, ncnn::Mat::PIXEL_RGBA2RGB,
                                           (int)info.width, (int)info.height, (int)info.stride, 224, 224);
    }
    else if (info.format == ANDROID_BITMAP_FORMAT_RGB_565)
    {
        const int w = (int)info.width;
        const int h = (int)info.height;
        std::vector<unsigned char> rgb((size_t)w * h * 3);
        for (int y = 0; y < h; y++)
        {
            const uint16_t* row = (const uint16_t*)((const unsigned char*)pixels + (size_t)y * info.stride);
            unsigned char* out = rgb.data() + (size_t)y * w * 3;
            for (int x = 0; x < w; x++)
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
        in = ncnn::Mat::from_pixels_resize(rgb.data(), ncnn::Mat::PIXEL_RGB, w, h, 224, 224);
    }
    else
    {
        AndroidBitmap_unlockPixels(env, bitmap);
        return nullptr;
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    const float mean_vals[3] = {122.77f, 116.75f, 104.09f};
    const float norm_vals[3] = {0.01459f, 0.01500f, 0.01422f};
    in.substract_mean_normalize(mean_vals, norm_vals);

    ncnn::Extractor ex = g_visual.create_extractor();
    ex.input("in0", in);

    ncnn::Mat out;
    if (ex.extract("out0", out) != 0) return nullptr;

    std::vector<float> feat;
    if (!mat_to_float_vec(out, feat)) return nullptr;
    const int dim = (int)feat.size();
    l2_normalize(feat.data(), dim);

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

    // Embed expects int token ids
    ncnn::Mat in(52, (size_t)4u);
    int* pids = (int*)in.data;
    for (int i = 0; i < 52; i++) pids[i] = (int)ids[i];
    env->ReleaseIntArrayElements(tokenIds, ids, JNI_ABORT);

    ncnn::Extractor ex = g_text.create_extractor();
    ex.input("in0", in);

    ncnn::Mat out;
    if (ex.extract("out0", out) != 0) return nullptr;

    std::vector<float> feat;
    if (!mat_to_float_vec(out, feat)) return nullptr;
    const int dim = (int)feat.size();
    l2_normalize(feat.data(), dim);

    jfloatArray jarr = env->NewFloatArray(dim);
    if (!jarr) return nullptr;
    env->SetFloatArrayRegion(jarr, 0, dim, feat.data());
    return jarr;
}

