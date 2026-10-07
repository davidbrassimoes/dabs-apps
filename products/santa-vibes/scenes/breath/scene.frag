// Breath — the whole frame is one slowly rotating two-colour gradient that breathes
// (luminance and a slight warm shift) on a long cycle. No shapes at all: this is the
// scene for "just light the room". Cost: trivial; happy at renderScale 0.25.
#include "lib/color.glsl"
#include "lib/react.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    float t = uTime * p_speed * 0.04;
    // Reactive variant (p_react): beat swells with the tempo and bass + beat deepens the breath; brightness only.
    float beat = mix(p_pulse * uBeat.x, p_pulse * 4.0 * groove(), p_react);
    float drive = p_react * (0.4 * uBands.x + 0.6 * groove());

    // Breathing: eased sine on a long period, deepened by the parameter.
    float breath = 0.5 + 0.5 * sin(uTime * 6.2831853 / max(p_period, 2.0));
    breath = smoothstep(0.0, 1.0, breath);

    // Gradient axis rotates very slowly; the gradient also bows a little.
    float ang = t * 0.35;
    vec2 dir = vec2(cos(ang), sin(ang));
    float g = dot(p, dir) * p_spread + 0.5 + 0.08 * sin(p.x * 2.0 + t * 1.3) * sin(p.y * 2.0 - t);
    g = smoothstep(0.0, 1.0, clamp(g, 0.0, 1.0));

    // Two palette coordinates drifting independently -> colour pairs keep changing.
    float a = tri(t * 0.17 + 0.1);
    float b = tri(t * 0.13 + 0.6);
    vec3 c1 = palette(0.1 + 0.35 * a);
    vec3 c2 = palette(0.6 + 0.4 * b);
    vec3 col = mix(c1, c2, g);

    // Breath: dimmer and cooler on the exhale, brighter and warmer on the inhale.
    float lum = mix(1.0 - 0.45 * p_depth, 1.0, breath) * (1.0 - 0.12 * p_react + 0.2 * drive);
    col *= lum;
    col = mix(col, col * vec3(1.06, 0.99, 0.9), breath * 0.4 * p_depth);

    col *= 0.85 + 0.3 * uMood.x;
    col *= 1.0 + beat * 0.08;
    fragColor = vec4(col, 1.0);
}
