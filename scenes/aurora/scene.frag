// Aurora — three ribbons of light whose centre lines wander with sines and low-octave
// fbm; vertical "folds" come from noise stretched along y. Brighter at the lower edge
// like a real curtain. Cost: 3 x fbm(3) + 3 x vnoise per pixel.
#include "lib/noise.glsl"
#include "lib/color.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    float x = uv.x * aspect;
    float t = uTime * p_speed * 0.12;
    float energy = 0.7 + 0.6 * uMood.x;

    // Night sky: deep base with a slow tilt, never pure black.
    vec3 col = mix(palette(0.0), palette(0.2), 0.35 + 0.25 * sin(t * 0.3 + uv.x)) * 0.9;

    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float yc = 0.28 + 0.2 * fi
                 + 0.12 * sin(x * (0.9 + fi * 0.35) + t * (1.0 + fi * 0.41) + fi * 2.1)
                 + (fbm(vec2(x * 0.6 + fi * 5.3, t * 0.6 + fi), 3) - 0.5) * 0.45 * p_wave;
        float w = p_width * (0.05 + 0.03 * fi);
        float d = (uv.y - yc) / w;
        // Sharp lower edge, long fade upward: the classic curtain profile.
        float band = exp(-d * d * (d < 0.0 ? 3.0 : 0.35));
        float folds = mix(1.0, 0.3 + 0.7 * vnoise(vec2(x * 7.0 + fi * 3.0 + t * 0.9, uv.y * 0.6)), p_folds);
        folds *= mix(1.0, 0.6 + 0.4 * vnoise(vec2(x * 19.0 - t * 1.3 + fi, uv.y * 0.4)), p_folds * 0.6);
        float shape = band * folds;
        // Green at the bottom edge, drifting to the palette top (pink) as it fades upward.
        vec3 c = palette(clamp(0.45 + 0.1 * fi + 0.35 * smoothstep(-0.5, 3.0, d), 0.0, 1.0));
        col += c * shape * energy * 1.1;
    }

    // Horizon glow and a faint top haze keep the frame luminous for projectors.
    float h = 1.0 - uv.y;
    col += palette(0.35) * p_glow * 0.9 * h * h * h;
    col += palette(0.9) * 0.06 * uv.y;

    col *= 1.0 + p_pulse * 0.12 * uBeat.x;
    fragColor = vec4(col, 1.0);
}
