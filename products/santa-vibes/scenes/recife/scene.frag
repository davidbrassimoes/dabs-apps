// Recife — Turing patterns, the look of reaction-diffusion: soft labyrinths of two palette tones
// that grow, bend and wander very slowly, like coral or animal skin. Procedural on purpose: a real
// reaction-diffusion needs hidden state, and a scene's feedback buffer is also what is shown.
// Stripes of a domain-warped noise field; a slow bias turns spots into stripes and back.
// Cost: 3 fbm of 3 octaves + 1 vnoise per pixel.
#include "lib/color.glsl"
#include "lib/noise.glsl"
#include "lib/react.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0) * p_scale;
    float t = uTime * p_speed * 0.02;
    // Reactive variant (p_react): the beat softens the edges in time, bass + beat warps harder.
    float beat = mix(p_pulse * uBeat.x, p_pulse * 4.0 * groove(), p_react);
    float drive = p_react * (0.5 * uBands.x + 0.5 * groove());

    // Slow warp: the labyrinth bends and migrates instead of sliding across the screen.
    vec2 w = vec2(fbm(p * 0.7 + vec2(t, -t * 0.7), 3), fbm(p * 0.7 + vec2(5.2 - t * 0.6, 1.3 + t), 3));
    vec2 q = p + (w - 0.5) * p_warp * (1.0 + 0.6 * drive);
    float f = fbm(q * 1.3 + vec2(-t * 0.4, t * 0.3), 3);

    float bands = sin((f - 0.5) * 6.2831853 * p_density * 4.0); // fbm spans ~0.3: x4 gives real stripes
    float bias = (vnoise(p * 0.35 + t * 0.5) - 0.5) * 1.2 * p_spots;
    float edge = max(p_softness, 0.02) * (1.0 + beat * 0.5);
    float v = bands + bias;
    float aa = fwidth(v); // where the stripes crowd, widen the edge instead of aliasing
    float s = smoothstep(-edge - aa, edge + aa, v);

    // Two tones that slide along the palette; the edges get a light rim.
    float hue = tri(t * 0.6 + f * 0.4);
    vec3 a = palette(0.18 + 0.2 * hue);
    vec3 b = palette(0.62 + 0.25 * hue + 0.1 * drive);
    vec3 col = mix(a, b, s);
    float rim = 1.0 - abs(s * 2.0 - 1.0);
    col += palette(0.95) * rim * 0.12 * (1.0 + beat);
    col *= 0.85 + 0.15 * uMood.y;
    fragColor = vec4(col, 1.0);
}
