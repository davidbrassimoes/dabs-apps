#version 300 es
// Santa Vibes output pass — applies the output profile (TV / Projector).
// Reads the scene render target (at renderScale) and writes the screen at native resolution.
precision highp float;

uniform sampler2D uScene;
uniform sampler2D uScenePrev; // outgoing scene during a transition
uniform float uMix;           // transition progress, already eased: 1 = only uScene
uniform vec2  uResolution;   // screen size
uniform float uTime;

uniform float uBrightness;   // overall cap, 0..1.5
uniform float uContrast;     // 1 = neutral
uniform float uSaturation;   // 1 = neutral
uniform float uGamma;        // 1 = neutral; >1 lifts shadows
uniform float uBlackFloor;   // 0..0.1, minimum output level
uniform float uTemperature;  // -1 cool .. 0 neutral .. +1 warm
uniform float uDither;       // 0/1

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 applyTemperature(vec3 c, float t) {
    vec3 warm = vec3(1.00, 0.86, 0.70);
    vec3 cool = vec3(0.82, 0.91, 1.00);
    vec3 tint = t >= 0.0 ? warm : cool;
    return c * mix(vec3(1.0), tint, abs(t));
}

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    vec3 c = texture(uScene, uv).rgb;
    // Scene crossfade (host-driven); uniform branch, so no cost outside transitions.
    if (uMix < 1.0) c = mix(texture(uScenePrev, uv).rgb, c, uMix);

    c = (c - 0.5) * uContrast + 0.5;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(l), c, uSaturation);
    c = applyTemperature(max(c, 0.0), uTemperature);
    c = pow(c, vec3(1.0 / uGamma));
    c = uBlackFloor + (1.0 - uBlackFloor) * c;
    c *= uBrightness;

    // Triangular dither (±1 LSB) to hide banding in slow gradients — essential on projectors.
    vec2 seed = gl_FragCoord.xy + fract(uTime) * 61.0;
    float n = hash12(seed) + hash12(seed + 17.13) - 1.0;
    c += n * (uDither / 255.0);

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
