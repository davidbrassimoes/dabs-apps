// Santa Vibes shared color helpers. Requires header.glsl (uPalette).

// Sample the 5-color palette continuously, t in [0, 1].
vec3 palette(float t) {
    float x = clamp(t, 0.0, 1.0) * 4.0;
    int i = int(floor(x));
    int j = min(i + 1, 4);
    float f = smoothstep(0.0, 1.0, fract(x));
    return mix(uPalette[i], uPalette[j], f);
}

// Triangle wave in [0, 1]: lets palette coordinates drift forever without jumps.
float tri(float x) {
    return abs(fract(x * 0.5) * 2.0 - 1.0);
}

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}
