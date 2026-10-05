// Bars — vertical colour stripes sliding slowly across the frame. Each stripe keeps
// its own colour (hashed by stripe id, cycling through the palette) and breathes in
// luminance on its own phase; a gentle vertical wave bends the edges. Cost: trivial.
#include "lib/noise.glsl"
#include "lib/color.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    float t = uTime * p_speed * 0.06;

    float k = uv.x * aspect * p_count + t * 2.0;
    k += p_wave * 0.4 * sin(uv.y * 3.0 + t * 3.0) * sin(uv.y * 1.3 - t * 2.0);
    float id = floor(k);
    float f = fract(k);

    float h1 = hash12(vec2(id + 0.5, 1.5));
    float h2 = hash12(vec2(id + 1.5, 1.5));
    float cyc = t * 0.3 + uMood.y * 0.25;
    vec3 c1 = palette(tri(h1 + cyc));
    vec3 c2 = palette(tri(h2 + cyc));

    float edge = smoothstep(1.0 - p_soft, 1.0, f);
    vec3 col = mix(c1, c2, edge);

    // Each stripe breathes slowly on its own phase.
    float breathe = 0.8 + 0.3 * (0.5 + 0.5 * sin(uTime * 0.18 + h1 * 6.2831));
    col *= breathe * (0.85 + 0.3 * uMood.x);
    col *= 1.0 + p_pulse * 0.1 * uBeat.x;
    fragColor = vec4(col, 1.0);
}
