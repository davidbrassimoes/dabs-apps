// Flow Trails — ink-like trails advected by a curl-noise flow field.
// Placeholder for a true particle system: uses feedback (uPrev) for trails
// and sparse random emission. Step 0.3 decides whether to replace it with
// instanced particles whose state lives in a texture.
//
// Resolution independence: emission lives on a grid in normalised (height = 1)
// units and each emitted dot has a fixed size in those units, so the image is the
// same at any renderScale; the flow step is in uv units too. Trails fade towards
// a dim palette background instead of black, so the room never goes dark.
#include "lib/noise.glsl"
#include "lib/color.glsl"

const float CELLS = 48.0; // emission cells per screen height

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    vec2 aspect = vec2(uResolution.x / uResolution.y, 1.0);
    vec2 n = uv * aspect; // normalised: height = 1

    // Flow field.
    vec2 p = n * p_scale + vec2(0.0, uTime * 0.02);
    float speed = p_flow * (0.6 + 0.8 * uMood.x);
    vec2 v = curlNoise(p, 3) * speed * 0.006;

    // Background the trails decay towards: a slow, dim palette wash.
    float bgk = vnoise(n * 1.5 + uTime * 0.015);
    vec3 bg = mix(palette(0.0), palette(0.25), bgk) * 0.9;

    // Advect previous frame and fade towards the background.
    vec3 prev = texture(uPrev, uv - v / aspect).rgb;
    vec3 col = bg + (prev - bg) * p_decay;

    // Sparse emission of soft dots, re-rolled 12 times per second; more on beats.
    vec2 g = n * CELLS;
    vec2 cell = floor(g);
    float tick = floor(uTime * 12.0);
    float seed = hash12(cell + tick * 13.7);
    float rate = p_density * 0.035 * (1.0 + 2.0 * uBeat.x * p_pulse);
    vec2 centre = 0.25 + 0.5 * hash22(cell + tick * 7.3);
    float spot = smoothstep(0.4, 0.0, length(g - cell - centre)) * step(1.0 - rate, seed);

    float pc = tri(vnoise(p * 0.5 + uTime * 0.01) + uMood.y * 0.3);
    col += spot * palette(0.45 + 0.55 * pc) * 0.75;

    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
