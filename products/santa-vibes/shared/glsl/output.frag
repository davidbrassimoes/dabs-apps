#version 300 es
// Santa Vibes output pass — applies the output profile (TV / Projector).
// Reads the scene render target (at renderScale) and writes the screen at native resolution.
//
// Runs on every screen pixel, so it is kept minimal: the host folds the profile's linear
// steps (contrast, saturation, temperature) into uGrade/uGradeOffset and black floor +
// brightness into uLevels (authoring/src/grade.ts, tv-app core OutputGrade.kt). Measured on
// the box's GE9215 at 1080p: the previous per-pixel version cost ~15.7 ms per frame.
precision highp float;

uniform sampler2D uScene;
uniform sampler2D uScenePrev; // outgoing scene during a transition
uniform float uMix;           // transition progress, already eased: 1 = only uScene
uniform sampler2D uNoise;     // the scenes' noise lattice (256x256 R8), reused for dithering
uniform vec2  uResolution;    // screen size
uniform vec2  uDitherOffset;  // whole texels, changes every frame (animated dither)

uniform mat3  uGrade;         // contrast * saturation * temperature
uniform vec3  uGradeOffset;
uniform float uInvGamma;      // 1 / gamma; exactly 1.0 skips the pow
uniform vec2  uLevels;        // scale, offset: black floor + brightness
uniform float uDither;        // 0/1

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    vec3 c = texture(uScene, uv).rgb;
    // Scene crossfade (host-driven); uniform branch, so no cost outside transitions.
    if (uMix < 1.0) c = mix(texture(uScenePrev, uv).rgb, c, uMix);

    c = max(uGrade * c + uGradeOffset, 0.0);
    if (uInvGamma != 1.0) c = pow(c, vec3(uInvGamma));
    c = c * uLevels.x + uLevels.y;

    // Triangular dither (±1 LSB) to hide banding in slow gradients — essential on projectors.
    // gl_FragCoord sits on pixel centres, so these fetches hit texel centres exactly.
    if (uDither > 0.0) {
        vec2 q = (gl_FragCoord.xy + uDitherOffset) / 256.0;
        float n = texture(uNoise, q).r + texture(uNoise, q + vec2(0.5, 0.25)).r - 1.0;
        c += n * (uDither / 255.0);
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
