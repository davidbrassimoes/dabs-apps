// Veils — up to four translucent silk bands, each a soft gaussian around a wandering
// centre line, with a sheen from stretched noise. Layers combine with a screen blend
// so overlaps glow instead of muddying. Cost: up to 4 x (fbm(3) + vnoise) per pixel.
#include "lib/noise.glsl"
#include "lib/color.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    float x = uv.x * aspect;
    float t = uTime * p_speed * 0.1;

    vec3 col = mix(palette(0.0), palette(0.2), 0.3 + 0.2 * sin(t * 0.4 + uv.y)) * 0.9;

    for (int i = 0; i < 4; i++) {
        if (float(i) >= p_layers) break;
        float fi = float(i);
        float ph = t * (0.5 + 0.17 * fi) + fi * 1.9;
        float c = 0.5 + 0.26 * sin(x * (1.3 + 0.4 * fi) + ph)
                + (fbm(vec2(x * 0.8 + fi * 7.0, t * 0.5 + fi * 3.0), 3) - 0.5) * 0.5 * p_wave;
        float d = abs(uv.y - c) / (p_width * (0.11 + 0.04 * fi));
        float veil = exp(-d * d * 1.5);

        // Sheen: folds catching light, moving at their own pace.
        float sheen = 0.25 + 0.75 * vnoise(vec2(x * 6.0 + t * 0.7 + fi * 2.0, uv.y * 2.0 - t * 0.3));
        vec3 vc = palette(0.4 + 0.18 * fi + 0.12 * sin(t * 0.8 + fi * 1.3 + uMood.y));

        // Screen blend: translucent layers brighten where they overlap.
        vec3 layer = vc * veil * sheen * p_opacity * (0.8 + 0.4 * uMood.x);
        col = 1.0 - (1.0 - col) * (1.0 - layer);
    }

    col *= 1.0 + p_pulse * 0.1 * uBeat.x;
    fragColor = vec4(col, 1.0);
}
