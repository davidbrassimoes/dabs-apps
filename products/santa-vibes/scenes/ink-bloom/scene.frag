// Ink Bloom — ink dropped into water. The previous frame is advected by a slow curl
// field and blurred (5 taps) so the ink diffuses; decay pulls everything back toward
// the water colour, so the frame is never black. A new drop lands every few seconds
// at a hashed position; the beat can add drops. Cost: curlNoise(3) + 5 texture taps.
#include "lib/noise.glsl"
#include "lib/color.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    vec2 px = 1.0 / uResolution;
    vec2 aspect = vec2(uResolution.x / uResolution.y, 1.0);
    vec2 p = uv * aspect * p_scale;
    float t = uTime * 0.05 * p_speed;

    // Slow currents.
    vec2 v = curlNoise(p + vec2(t, -t * 0.7), 3) * 0.0012 * p_flow;
    vec2 q = uv - v / aspect;

    // Diffusion: cross blur of the advected previous frame.
    vec2 dx = vec2(px.x, 0.0) * p_diffuse;
    vec2 dy = vec2(0.0, px.y) * p_diffuse;
    vec3 prev = (texture(uPrev, q).rgb * 2.0
               + texture(uPrev, q + dx).rgb + texture(uPrev, q - dx).rgb
               + texture(uPrev, q + dy).rgb + texture(uPrev, q - dy).rgb) / 6.0;

    // Everything relaxes toward the water tone (not black).
    vec3 water = mix(palette(0.0), palette(0.25), 0.45 + 0.25 * uMood.y);
    prev = water + (prev - water) * p_decay;

    // Scheduled drop: one every `interval` seconds, each with its own position and colour.
    // The drop blooms outward over ~2 s (expanding ring) and then is left to the currents.
    float interval = mix(8.0, 2.0, p_rate);
    float k = floor(uTime / interval);
    float age = fract(uTime / interval) * interval;
    vec2 c1 = hash22(vec2(k * 1.37 + 0.5, k * 0.61 + 2.5)) * 0.7 + 0.15;
    float r1 = 0.03 + 0.16 * (1.0 - exp(-age * 1.2));
    float d1 = length((uv - c1) * aspect);
    float ring = smoothstep(r1, r1 * 0.75, d1) * smoothstep(r1 * 0.35, r1 * 0.7, d1);
    float drop1 = (ring * 0.8 + smoothstep(r1 * 0.6, 0.0, d1) * 0.5) * exp(-age * 1.1);
    vec3 ink1 = palette(0.45 + 0.55 * tri(k * 0.31 + uMood.y * 0.2));

    // Beat drop: appears on the onset frame, a smaller splash elsewhere.
    float kb = floor(uTime * 6.0);
    vec2 c2 = hash22(vec2(kb * 0.77 + 9.1, kb * 1.91 + 4.2)) * 0.7 + 0.15;
    float d2 = length((uv - c2) * aspect);
    float drop2 = uBeat.w * p_pulse * smoothstep(0.07, 0.02, d2);
    vec3 ink2 = palette(0.75 + 0.25 * tri(kb * 0.17));

    // Ink is added, not blended, so overlapping drops get richer rather than greyer.
    vec3 col = prev + (ink1 - water * 0.5) * drop1 * 0.35 + (ink2 - water * 0.5) * drop2 * 0.9;
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
