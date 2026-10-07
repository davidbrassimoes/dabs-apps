// Marble — iridescent marbled paper / oil slick. Stripes along a twice-warped coordinate
// become veins; the palette coordinate comes from the second warp level so colour and
// vein structure are correlated but not identical. Cost: 4 x fbm(3) per pixel.
#include "lib/noise.glsl"
#include "lib/color.glsl"
#include "lib/react.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0) * p_scale;
    float t = uTime * p_speed * 0.06;
    // Reactive variant (p_react): beat swells with the tempo, bass + beat deepens the warp, mids light the highlights.
    float beat = mix(p_pulse * uBeat.x, p_pulse * 4.0 * groove(), p_react);
    float drive = p_react * (0.4 * uBands.x + 0.6 * groove());
    float warp = p_warp + 1.5 * drive;

    vec2 q = vec2(fbm(p + vec2(t, 0.3 * t), 3),
                  fbm(p + vec2(3.7, 1.2) - t * 0.8, 3));
    vec2 r = vec2(fbm(p + warp * q + vec2(1.3, 7.1) + t * 0.4, 3),
                  fbm(p + warp * q + vec2(5.9, 2.4) - t * 0.3, 3));

    // Veins: a stripe field along a warped axis, sharpened by contrast.
    float s = 0.5 + 0.5 * sin((p.x + p.y * 0.6 + (r.x - 0.5) * p_veins) * 3.0 + t * 2.0);
    float veins = pow(s, p_contrast);

    // Iridescence: palette coordinate from the second warp level and the slow mood.
    float pc = tri(r.y * 1.6 + s * 0.25 + uMood.y * 0.3 + t * 0.15);
    vec3 col = palette(pc);
    col *= 0.55 + 0.65 * veins;

    // Pearly highlights where the warp piles up.
    col += palette(1.0) * pow(max(r.x - 0.58, 0.0), 2.0) * 2.0 * (0.7 + 0.5 * uMood.x + 1.2 * p_react * uBands.y);

    col *= 1.0 + beat * 0.12;
    fragColor = vec4(col, 1.0);
}
