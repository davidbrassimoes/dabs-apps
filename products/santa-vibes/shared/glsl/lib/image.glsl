// Santa Vibes shared image helpers. Requires header.glsl (uResolution, uImageInfo).

// Map screen uv (0..1) to uImage coordinates so the image covers the whole target
// with its own aspect ratio (uImageInfo.y), centred and cropped, never stretched.
vec2 imageCoverUV(vec2 uv) {
    float screen = uResolution.x / uResolution.y;
    float image = max(uImageInfo.y, 1e-3);
    vec2 s = (uv - 0.5) * vec2(screen, 1.0);
    float k = max(screen / image, 1.0);
    return s / (k * vec2(image, 1.0)) + 0.5;
}
