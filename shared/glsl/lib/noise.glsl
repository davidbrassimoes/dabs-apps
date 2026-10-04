// Santa Vibes shared noise library.
// Hashes: "Hash without Sine" (Dave Hoskins), MIT-style permissive.

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

// Smooth value noise, range ~[0, 1].
float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Fractal Brownian motion. Keep octaves low (3-5) for performance.
float fbm(vec2 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 8; i++) {
        if (i >= octaves) break;
        sum += amp * vnoise(p);
        p = rot * p * 2.02;
        amp *= 0.5;
    }
    return sum;
}

// Divergence-free flow from the gradient of fbm, rotated 90 degrees.
vec2 curlNoise(vec2 p, int octaves) {
    const float e = 0.01;
    float nx1 = fbm(p + vec2(e, 0.0), octaves);
    float nx0 = fbm(p - vec2(e, 0.0), octaves);
    float ny1 = fbm(p + vec2(0.0, e), octaves);
    float ny0 = fbm(p - vec2(0.0, e), octaves);
    return vec2(ny1 - ny0, -(nx1 - nx0)) / (2.0 * e);
}
