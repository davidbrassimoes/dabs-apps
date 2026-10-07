// Spiral Melt — concentric rings with optional spiral twist, drifting outward and
// melted by a slow fbm warp. Bands are wide and soft (projector-friendly); colour
// cycles along the radius. Cost: 2 x fbm(3) per pixel.
//
// Seamless spiral: atan() jumps by 2*pi on the negative x axis, so the angular term
// must shift k by a whole number of tri() periods (2) there: 2n arms. A drifting
// p_twist crossfades between n and n+1 so the arm count never pops. Colour depends
// on radius, not on the band index, which would also change across the cut.
#include "lib/noise.glsl"
#include "lib/color.glsl"
#include "lib/react.glsl"

const float TAU = 6.28318530718;

vec3 spiral(float r, float turn, float arms, float t, float cycle) {
    float k = r * p_rings - t * 2.0 + turn * arms;
    float band = smoothstep(0.5 - p_soft, 0.5 + p_soft, tri(k));
    // One band walks the dark half of the palette, the other the light half, so
    // neighbouring bands always contrast.
    float c = r * 0.9 + cycle;
    return mix(palette(0.1 + 0.45 * tri(c)), palette(0.55 + 0.45 * tri(c + 0.7)), band);
}

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    float t = uTime * p_speed * 0.12;
    // Reactive variant (p_react): beat swells with the tempo, bass + beat melts the rings, mids shift the colour cycle.
    float beat = mix(p_pulse * uBeat.x, p_pulse * 4.0 * groove(), p_react);
    float drive = p_react * (0.4 * uBands.x + 0.6 * groove());

    // Melt: warp the plane so rings sag and drip.
    vec2 w = vec2(fbm(p * 1.2 + t * 0.4, 3), fbm(p * 1.2 - t * 0.3 + 5.0, 3)) - 0.5;
    vec2 q = p + w * (p_melt + 1.0 * drive) * 0.45;

    float r = length(q);
    float turn = atan(q.y, q.x) / TAU; // -0.5..0.5, jumps by 1 on the cut
    float cycle = t * 0.08 + uMood.y * 0.3 + 0.12 * p_react * uBands.y;

    // p_twist 0..3 -> 0..~4.7 arm pairs; each integer step is seamless.
    float s = p_twist * 1.5708;
    float n = floor(s);
    float f = smoothstep(0.0, 1.0, fract(s));
    vec3 col = mix(spiral(r, turn, 2.0 * n, t, cycle), spiral(r, turn, 2.0 * n + 2.0, t, cycle), f);

    // Centre glows a little; edges stay luminous.
    col *= 0.85 + 0.3 * exp(-r * 2.0) * (0.7 + 0.5 * uMood.x);
    col *= 1.0 + beat * 0.12;
    fragColor = vec4(col, 1.0);
}
