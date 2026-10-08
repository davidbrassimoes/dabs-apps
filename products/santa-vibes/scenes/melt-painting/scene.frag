// Melt Painting — the current image slowly melts. The previous frame is advected by a curl
// flow with a gentle downward drip, and the image is fed back in a little every frame (more in
// slowly wandering patches), so the picture keeps its identity while it flows. Without an image
// the source is a palette field, so the scene still works offline or with images off.
// Feedback: nothing here multiplies the frame (that would compound); the music moves the flow.
// Frame-rate independent through uDeltaTime (k = frames at 30 fps).
#include "lib/noise.glsl"
#include "lib/color.glsl"
#include "lib/image.glsl"
#include "lib/react.glsl"

void main() {
    float k = uDeltaTime * 30.0;
    vec2 uv = gl_FragCoord.xy / uResolution;
    vec2 aspect = vec2(uResolution.x / uResolution.y, 1.0);
    float t = uTime * p_speed * 0.05;
    // Reactive variant (p_react): the beat and bass stir the flow harder.
    float beat = mix(p_pulse * uBeat.x, p_pulse * 4.0 * groove(), p_react);
    float drive = p_react * (0.4 * uBands.x + 0.6 * groove());

    vec2 p = uv * aspect * p_scale + vec2(t, -t * 0.6);
    vec2 v = curlNoise(p, 3) * 0.0025 * p_flow * (1.0 + 0.8 * beat + 2.0 * drive) - vec2(0.0, 0.0012 * p_drip);
    vec3 prev = texture(uPrev, uv - v * k / aspect).rgb;

    // Source: the image (covering the frame, aspect kept), else a slow palette field.
    vec3 src = palette(0.15 + 0.8 * tri(fbm(uv * aspect * 2.0 + t * 0.7, 3) * 1.4 + t * 0.2));
    if (uImageInfo.x > 0.5) {
        vec3 img = texture(uImage, imageCoverUV(uv)).rgb;
        src = mix(src, img, uImageInfo.z);
    }
    // Very dark areas lean to the palette's middle, so a night sky still lights the room.
    float dark = 1.0 - smoothstep(0.03, 0.18, luma(src));
    src = mix(src, palette(0.35 + 0.3 * tri(t + uv.x)), dark * 0.45);
    // A glaze of the palette, so the image sits in the room's colours.
    src = mix(src, palette(0.1 + 0.9 * luma(src)), 0.25 * p_glaze);

    float patchy = smoothstep(0.35, 0.75, vnoise(uv * aspect * 3.0 - t * 0.5));
    float feed = 1.0 - pow(1.0 - (0.004 + 0.03 * p_hold * patchy), k);
    vec3 col = mix(prev, src, feed);
    col = mix(src, col, smoothstep(0.0, 1.5, uTime)); // uPrev starts black: show the source first
    fragColor = vec4(col, 1.0);
}
