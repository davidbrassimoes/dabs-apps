// Halo — one soft orb of light breathing over a slowly tilting two-tone field.
// Deliberately minimal (Turrell / Rothko): maximum light, no fine detail, so it looks
// good on a weak projector and can run at very low renderScale. Cost: trivial.
#include "lib/color.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    float t = uTime * p_speed * 0.08;

    // The orb wanders on a slow Lissajous path and breathes on a 25 s cycle.
    vec2 c = vec2(sin(t * 0.7), cos(t * 0.53)) * vec2(0.18, 0.12) * p_wander;
    float breathe = 1.0 + 0.06 * sin(uTime * 0.25) + p_pulse * 0.08 * uBeat.x;
    float radius = p_size * breathe * (0.9 + 0.2 * uMood.x);
    float r = length(p - c);

    float orb = 1.0 - smoothstep(radius - p_softness, radius + p_softness, r);
    float g = smoothstep(0.0, radius, r);
    vec3 inner = palette(0.78 + 0.18 * sin(t * 1.3));
    vec3 outer = palette(0.55 + 0.2 * sin(t * 0.9 + 2.0));
    vec3 orbCol = mix(inner, outer, g);

    // Backdrop: two palette tones, a slow diagonal tilt.
    float fieldMix = 0.5 + 0.5 * sin(uv.y * 2.0 + t * 0.8 + 0.4 * sin(uv.x * 3.0 + t * 0.6));
    vec3 field = mix(palette(0.08), palette(0.32), fieldMix) * (0.75 + 0.25 * uMood.y);

    vec3 col = mix(field, orbCol, orb);
    col += inner * p_glow * 0.35 * exp(-max(r - radius, 0.0) * 4.5);
    fragColor = vec4(col, 1.0);
}
