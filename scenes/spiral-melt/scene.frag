// Spiral Melt — concentric rings with optional spiral twist, drifting outward and
// melted by a slow fbm warp. Bands are wide and soft (projector-friendly); colour
// cycles along the rings. Cost: 2 x fbm(3) per pixel.
#include "lib/noise.glsl"
#include "lib/color.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    float t = uTime * p_speed * 0.12;

    // Melt: warp the plane so rings sag and drip.
    vec2 w = vec2(fbm(p * 1.2 + t * 0.4, 3), fbm(p * 1.2 - t * 0.3 + 5.0, 3)) - 0.5;
    vec2 q = p + w * p_melt * 0.45;

    float r = length(q);
    float a = atan(q.y, q.x);
    float k = r * p_rings - t * 2.0 + a * p_twist * 0.5;

    // Two interleaved palette bands with a soft boundary.
    float band = smoothstep(0.5 - p_soft, 0.5 + p_soft, tri(k));
    float cycle = t * 0.08 + uMood.y * 0.3;
    vec3 c1 = palette(tri(floor(k) * 0.21 + cycle));
    vec3 c2 = palette(tri(floor(k + 0.5) * 0.21 + 0.5 + cycle));
    vec3 col = mix(c1, c2, band);

    // Centre glows a little; edges stay luminous.
    col *= 0.85 + 0.3 * exp(-r * 2.0) * (0.7 + 0.5 * uMood.x);
    col *= 1.0 + p_pulse * 0.12 * uBeat.x;
    fragColor = vec4(col, 1.0);
}
