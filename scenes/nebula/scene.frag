// Nebula — domain-warped fbm (after Inigo Quilez's warping technique).
// Optionally blends a source image displaced by the same warp field.
// Brightness: the palette walk starts above the darkest entry and the fbm shading
// never drops below ~0.6, so the scene lights the room instead of reading as black.
#include "lib/noise.glsl"
#include "lib/color.glsl"
#include "lib/image.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0) * p_scale;

    float t = uTime * p_speed * 0.08;
    float energy = mix(0.6, 1.4, uMood.x);

    vec2 q = vec2(fbm(p + vec2(0.0, t), 4),
                  fbm(p + vec2(5.2, 1.3) - t, 4));
    vec2 r = vec2(fbm(p + 4.0 * q + vec2(1.7, 9.2) + t * 0.5, 4),
                  fbm(p + 4.0 * q + vec2(8.3, 2.8) - t * 0.3, 4));
    float f = fbm(p + p_warp * energy * r, 4);

    float pc = tri(f * 1.2 + length(q) * 0.4 + uMood.y * 0.3 + t * 0.05);
    vec3 col = palette(0.15 + 0.85 * pc);
    col *= 0.6 + 0.9 * f;
    col += palette(1.0) * pow(max(r.x - 0.55, 0.0), 2.0) * 2.0 * (0.6 + 0.6 * uBands.y);

    // Hybrid mode: image (cover-fitted, aspect kept) displaced by the warp field.
    if (uImageInfo.x > 0.5) {
        vec2 iuv = imageCoverUV(uv) + (r - 0.5) * 0.08 * p_warp;
        vec3 img = texture(uImage, iuv).rgb;
        col = mix(col, img * (0.5 + 1.0 * f), p_imageMix * uImageInfo.z);
    }

    col *= 1.0 + p_pulse * 0.15 * uBeat.x;
    fragColor = vec4(col, 1.0);
}
