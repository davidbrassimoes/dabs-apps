// Flow Trails — ink-like trails advected by a curl-noise flow field.
// Placeholder for a true particle system: uses feedback (uPrev) for trails
// and sparse random emission. Step 0.3 decides whether to replace it with
// instanced particles whose state lives in a texture.
#include "lib/noise.glsl"
#include "lib/color.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    vec2 aspect = vec2(uResolution.x / uResolution.y, 1.0);

    // Flow field.
    vec2 p = uv * aspect * p_scale + vec2(0.0, uTime * 0.02);
    float speed = p_flow * (0.6 + 0.8 * uMood.x);
    vec2 v = curlNoise(p, 3) * speed * 0.0015;

    // Advect previous frame and fade.
    vec3 prev = texture(uPrev, uv - v / aspect).rgb;
    prev *= p_decay;

    // Sparse emission; more on beats.
    vec2 cell = floor(gl_FragCoord.xy * 0.5);
    float seed = hash12(cell + floor(uTime * 24.0) * 13.7);
    float rate = p_density * 0.004 * (1.0 + 2.0 * uBeat.x * p_pulse);
    float emit = step(1.0 - rate, seed);

    float pc = tri(vnoise(p * 0.5 + uTime * 0.01) + uMood.y * 0.3);
    vec3 col = prev + emit * palette(0.4 + 0.6 * pc) * 0.9;

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
