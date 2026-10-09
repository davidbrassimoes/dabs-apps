// Skyspace — after James Turrell's skyspaces: a rounded opening of "sky" cut into a lit wall.
// The sky walks the palette over minutes, the wall takes the other end of it, and the edge is
// so soft the eye cannot tell how far away the sky is. Pure light, no detail: made for the
// projector and the cheapest scene of all. Cost: trivial.
#include "lib/color.glsl"
#include "lib/react.glsl"

float roundBox(vec2 p, vec2 b, float r) {
    vec2 d = abs(p) - b + r;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / uResolution.y;
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    float t = uTime * p_speed * 0.01;
    // Reactive variant (p_react): the opening breathes with the tempo, bass + beat light the wall.
    float beat = mix(p_pulse * uBeat.x, p_pulse * 4.0 * groove(), p_react);
    float drive = p_react * (0.5 * uBands.x + 0.5 * groove());

    float size = p_size * (1.0 + 0.04 * beat);
    vec2 hs = vec2(size * mix(1.0, aspect, p_shape) * 0.5, size * 0.5); // square .. wide
    float d = roundBox(p, hs, p_round * min(hs.x, hs.y));
    float edge = 0.002 + p_softness * 0.08;
    float sky = 1.0 - smoothstep(-edge, edge, d);

    float k = tri(t);
    vec3 skyCol = palette(0.35 + 0.6 * k + 0.05 * drive);
    vec3 wallCol = palette(0.15 + 0.5 * tri(t + 0.5));
    // The wall is lit from the opening: brighter next to it, falling off towards the corners.
    float lit = exp(-max(d, 0.0) * 3.0) * p_glow;
    vec3 wall = wallCol * (0.7 + 0.3 * lit) + skyCol * lit * 0.25 * (1.0 + drive);
    // A faint vertical gradient inside the opening, like a real sky.
    vec3 skyGrad = mix(skyCol, palette(min(1.0, 0.45 + 0.6 * k)), (p.y / max(hs.y, 1e-3)) * 0.15 + 0.15);
    vec3 col = mix(wall, skyGrad, sky);
    col *= 0.88 + 0.12 * uMood.y;
    fragColor = vec4(col, 1.0);
}
