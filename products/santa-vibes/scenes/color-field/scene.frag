// Color Field — soft horizontal bands in the spirit of Rothko.
// Slow, minimal, projector-friendly: large shapes, mid luminance.
#include "lib/noise.glsl"
#include "lib/color.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float t = uTime * p_speed * 0.05;

    // Wobbly band edges.
    float n = fbm(vec2(uv.x * 1.5, uv.y * 3.0) + vec2(t, t * 0.7), 4);
    float y = uv.y + (n - 0.5) * p_wobble * 0.3;

    // Stepped bands with soft transitions.
    float k = y * p_bands;
    float w = clamp(p_softness, 0.05, 0.95);
    float stepped = floor(k) + smoothstep(1.0 - w, 1.0, fract(k));

    // Palette coordinate drifts with time and slow mood.
    float pc = tri(stepped / p_bands + t * 0.3 + uMood.x * 0.25);
    vec3 col = palette(pc);

    // Gentle inner glow per band and subtle beat "breathing".
    float glow = 1.0 - abs(fract(k) - 0.5) * 2.0;
    col *= 0.85 + 0.15 * glow;
    col *= 1.0 + p_pulse * 0.12 * uBeat.x;

    // Soft vignette.
    vec2 d = uv - 0.5;
    col *= 1.0 - dot(d, d) * p_vignette;

    fragColor = vec4(col, 1.0);
}
