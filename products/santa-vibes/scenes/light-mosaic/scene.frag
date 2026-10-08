// Light Mosaic — the image seen through a grid of soft round lights in offset rows. Each light
// takes the blurred colour of its patch of the image (a coarse mip level), pulled towards the
// palette, and breathes on its own slow phase; the beat swells them. Big soft shapes, no fine
// detail: good on a projector. Without an image the lights take a slow palette field instead.
#include "lib/noise.glsl"
#include "lib/color.glsl"
#include "lib/image.glsl"
#include "lib/react.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    float t = uTime * p_speed * 0.1;
    // Reactive variant (p_react): beat swells with the tempo, bass + beat grow the lights.
    float beat = mix(p_pulse * uBeat.x, p_pulse * 4.0 * groove(), p_react);
    float drive = p_react * (0.4 * uBands.x + 0.6 * groove());

    // Cells per screen height; every other row shifted by half a cell.
    vec2 g = vec2(uv.x * aspect, uv.y) * p_cells;
    float row = floor(g.y);
    float shift = 0.5 * mod(row, 2.0);
    g.x += shift;
    vec2 cell = floor(g);
    vec2 f = fract(g) - 0.5;
    vec2 cuv = vec2((cell.x + 0.5 - shift) / aspect, cell.y + 0.5) / p_cells; // the cell's centre

    vec3 c = palette(0.2 + 0.8 * tri(fbm(cuv * 3.0 + t, 3) * 1.5 + t * 0.3));
    if (uImageInfo.x > 0.5) {
        vec3 img = textureLod(uImage, imageCoverUV(cuv), 2.5).rgb;
        c = mix(c, img, uImageInfo.z);
    }
    float h = hash12(cell + 3.1);
    // Dark patches (night skies, deep shadows) become palette lights: the room never goes dark.
    float dark = 1.0 - smoothstep(0.04, 0.2, luma(c));
    c = mix(c, palette(0.5 + 0.45 * h), dark * 0.7);
    c = mix(c, palette(0.1 + 0.9 * luma(c)), p_tint);

    float breathe = 0.5 + 0.5 * sin(uTime * (0.2 + 0.3 * h) * (0.5 + p_speed) + h * 6.2831);
    float r = p_size * (0.75 + 0.25 * breathe) * (1.0 + 0.25 * beat + 0.2 * drive);
    float d = length(f);
    float light = 1.0 - smoothstep(r - p_soft, r + p_soft, d);

    vec3 bg = palette(0.05) * 0.6 + c * 0.12;
    vec3 col = mix(bg, c * (0.85 + 0.3 * breathe), light);
    col += c * p_glow * 0.3 * exp(-max(d - r, 0.0) * 7.0);
    col *= 1.0 + beat * 0.06;
    fragColor = vec4(col, 1.0);
}
