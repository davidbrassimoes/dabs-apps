// Liquid Light — oil-and-water overhead projector. Immiscible blobs (inverse-square
// metaballs) drift on slow incommensurate orbits inside a gently warped space, so the
// picture never repeats. A thin bright rim near the iso-threshold mimics the lens edge
// of oil on water. Cost: 2 x fbm(3) + up to 8 blob evaluations per pixel.
#include "lib/noise.glsl"
#include "lib/color.glsl"

float blobField(vec2 p, float t, float count, float size) {
    float f = 0.0;
    for (int i = 0; i < 8; i++) {
        if (float(i) >= count) break;
        float fi = float(i);
        vec2 h = hash22(vec2(fi * 7.31 + 1.0, fi * 3.17 + 2.0));
        // Lissajous orbit with per-blob frequencies -> no two blobs ever sync up.
        vec2 c = vec2(sin(t * (0.21 + h.x * 0.23) + fi * 1.7),
                      cos(t * (0.17 + h.y * 0.19) + fi * 2.3)) * vec2(0.6, 0.42);
        float r = size * (0.6 + 0.8 * h.x);
        float d2 = dot(p - c, p - c);
        f += r * r / (d2 + 0.002);
    }
    return f;
}

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    float t = uTime * p_speed * 0.5;

    // Liquid warp: blobs become drops, not circles.
    vec2 w = vec2(fbm(p * 1.4 + t * 0.15, 3), fbm(p * 1.4 - t * 0.11 + 3.1, 3)) - 0.5;
    vec2 q = p + w * p_warp * 0.35;

    float size = p_size * (1.0 + p_pulse * 0.15 * uBeat.x) * (0.9 + 0.2 * uMood.x);
    float f = blobField(q, t, p_blobs, size);

    float inside = smoothstep(0.9, 1.3, f);
    float rim = smoothstep(0.6, 1.0, f) * (1.0 - smoothstep(1.0, 1.7, f));
    float core = smoothstep(1.3, 4.0, f);

    // Oil colour drifts through the palette with the field value and the mood.
    float hue = tri(f * 0.12 + t * 0.05 + uMood.y * 0.3 + q.x * 0.1);
    vec3 oil = palette(0.45 + 0.55 * hue);
    oil = mix(oil, palette(1.0), core * 0.35);

    // Background: a warm wash that slowly tilts, never black.
    float bgMix = 0.5 + 0.5 * sin(t * 0.37 + uv.y * 1.5 + w.x * 2.0);
    vec3 bg = mix(palette(0.05), palette(0.3), bgMix) * (0.75 + 0.25 * uMood.x);

    vec3 col = mix(bg, oil, inside);
    col += rim * p_rim * palette(0.95) * 0.5;

    // Projector lamp hot spot.
    col *= 0.88 + 0.24 * (1.0 - length(uv - 0.5));
    fragColor = vec4(col, 1.0);
}
