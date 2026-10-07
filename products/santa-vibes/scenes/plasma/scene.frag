// Plasma — the demoscene classic, slowed down: five sine waves from moving sources
// interfere; the result is softly posterised into levels with glowing contour lines,
// which reads as "digital" without any fine texture. Cost: a handful of sines.
#include "lib/color.glsl"
#include "lib/react.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0) * p_scale;
    float t = uTime * p_speed * 0.4;
    // Reactive variant (p_react): beat swells with the tempo, bass + beat pushes the wave sources apart, highs light the contours.
    float beat = mix(p_pulse * uBeat.x, p_pulse * 4.0 * groove(), p_react);
    float drive = p_react * (0.4 * uBands.x + 0.6 * groove());
    float spread = 0.8 * (1.0 + 0.5 * drive);

    vec2 s1 = vec2(sin(t * 0.41), cos(t * 0.33)) * spread;
    vec2 s2 = vec2(cos(t * 0.27), sin(t * 0.37)) * spread;
    float v = sin(p.x * 1.7 + t)
            + sin(p.y * 1.3 - t * 0.7)
            + sin((p.x + p.y) * 1.1 + t * 0.5)
            + sin(length(p - s1) * 2.6 - t * 1.1)
            + sin(length(p + s2) * 2.1 + t * 0.8);
    v = v * 0.1 + 0.5; // ~0..1

    // Soft posterisation: blend between the continuous value and stepped levels.
    float k = v * p_levels;
    float f = fract(k);
    float stepped = (floor(k) + smoothstep(0.35, 0.65, f)) / p_levels;
    float vq = mix(v, stepped, p_digital);

    // Stay in the luminous part of the palette: neon, not navy.
    vec3 col = palette(0.3 + 0.7 * tri(vq * 1.6 + t * 0.06 + uMood.y * 0.25));

    // Contour glow at each level boundary.
    float iso = 1.0 - smoothstep(0.0, 0.15, abs(f - 0.5));
    col += palette(0.95) * iso * (p_lines + 0.8 * p_react * uBands.z) * 0.6 * p_digital;

    col *= 0.9 + 0.3 * uMood.x;
    col *= 1.0 + beat * 0.12;
    fragColor = vec4(col, 1.0);
}
