// Geo Tiles — a slowly rotating, slowly panning grid. Each cell holds a soft circle or
// square (chosen per cell) whose size pulses on its own slow phase and whose colour
// cycles through the palette. Cost: trivial (a few hashes per pixel).
#include "lib/noise.glsl"
#include "lib/color.glsl"
#include "lib/react.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    float t = uTime * p_speed * 0.1;
    // Reactive variant (p_react): beat swells with the tempo and bass + beat grow the shapes, mids shift their colour.
    float beat = mix(p_pulse * uBeat.x, p_pulse * 4.0 * groove(), p_react);
    float drive = p_react * (0.4 * uBands.x + 0.6 * groove());

    float ang = t * 0.25 * p_rotate;
    mat2 rot = mat2(cos(ang), -sin(ang), sin(ang), cos(ang));
    vec2 q = rot * p * p_grid + vec2(t * 0.3, t * 0.17);

    vec2 cell = floor(q);
    vec2 f = fract(q) - 0.5;
    float h1 = hash12(cell + 0.5);
    float h2 = hash12(cell + 7.5);
    float h3 = hash12(cell + 13.5);

    // Per-cell slow pulse of size; the beat adds a small breath.
    float size = 0.22 + 0.18 * sin(uTime * 0.12 * (0.5 + h1) + h2 * 6.2831);
    size *= (0.92 + 0.16 * uMood.x) * (1.0 + beat * 0.1) * (1.0 + 0.3 * drive);

    float dCircle = length(f) - size;
    float dSquare = max(abs(f.x), abs(f.y)) - size * 0.9;
    float d = mix(dSquare, dCircle, step(0.5, h3));
    float shape = 1.0 - smoothstep(0.0, p_soft, d);

    vec3 bg = mix(palette(0.0), palette(0.2), 0.5 + 0.5 * sin(t * 0.5 + uv.y * 2.0 + uv.x));
    vec3 fg = palette(0.4 + 0.6 * tri(h2 * 0.9 + t * 0.15 + uMood.y * 0.3 + 0.15 * p_react * uBands.y));

    vec3 col = mix(bg, fg, shape * p_opacity);
    fragColor = vec4(col, 1.0);
}
