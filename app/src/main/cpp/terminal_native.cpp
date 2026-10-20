#include <jni.h>
#include <cmath>
#include <cstdint>

extern "C" {

/**
 * Fast native Look-Up Table (LUT) color mapper for Bookmap-style heatmap.
 * Maps normalized volume intensity (0.0 to 1.0) into 32-bit ARGB pixels.
 * Uses a precalculated thermal palette:
 * - Low: Transparent Black (0x00000000)
 * - Medium-Low: Deep Blue (0xFF003366)
 * - Medium: Cyan / Aqua (0xFF00E5FF)
 * - Medium-High: Orange (0xFFFF9100)
 * - Extreme: Hot White (0xFFFFFFFF)
 */
JNIEXPORT void JNICALL
Java_com_trading_terminal_core_engine_NativeHeatmapMapper_mapIntensityToPixels(
    JNIEnv *env,
    jclass clazz,
    jfloatArray volumes,
    jintArray outPixels,
    jint length,
    jfloat maxVolume
) {
    if (length <= 0 || maxVolume <= 0.00001f) return;

    jfloat *v = env->GetFloatArrayElements(volumes, nullptr);
    jint *p = env->GetIntArrayElements(outPixels, nullptr);

    float logMax = std::log1p(maxVolume);

    for (int i = 0; i < length; ++i) {
        float val = v[i];
        if (val <= 0.0f) {
            p[i] = 0x00000000; // transparent
            continue;
        }

        // Logarithmic normalization (CRITICAL for order book power-law distribution)
        float norm = std::log1p(val) / logMax;
        if (norm > 1.0f) norm = 1.0f;

        uint8_t a = (uint8_t)(norm * 240.0f + 15.0f);
        uint8_t r = 0, g = 0, b = 0;

        if (norm < 0.25f) {
            // Black to Deep Blue
            float t = norm / 0.25f;
            b = (uint8_t)(t * 180.0f);
        } else if (norm < 0.5f) {
            // Deep Blue to Cyan
            float t = (norm - 0.25f) / 0.25f;
            g = (uint8_t)(t * 220.0f);
            b = (uint8_t)(180.0f + t * 75.0f);
        } else if (norm < 0.75f) {
            // Cyan to Yellow / Orange
            float t = (norm - 0.5f) / 0.25f;
            r = (uint8_t)(t * 255.0f);
            g = (uint8_t)(220.0f - t * 40.0f);
            b = (uint8_t)(255.0f * (1.0f - t));
        } else {
            // Orange to White
            float t = (norm - 0.75f) / 0.25f;
            r = 255;
            g = (uint8_t)(180.0f + t * 75.0f);
            b = (uint8_t)(t * 255.0f);
        }

        p[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }

    env->ReleaseFloatArrayElements(volumes, v, 0);
    env->ReleaseIntArrayElements(outPixels, p, 0);
}

}
