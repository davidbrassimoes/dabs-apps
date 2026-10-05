// Glitch Drift — a calm ambient gradient with occasional, slow glitches: horizontal
// slices slide sideways, RGB channels separate, and blocks quantise, each fading in
// and out over a "tick" so nothing snaps. Cost: 3 x fbm(3) per pixel.
#include "lib/noise.glsl"
#include "lib/color.glsl"

void main() {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float t = uTime * p_speed * 0.1;

    // Glitch clock: slow ticks; each slice decides per tick whether it is active.
    float tickRate = mix(0.25, 2.0, p_rate);
    float tick = floor(uTime * tickRate);
    float phase = fract(uTime * tickRate);
    float env = sin(phase * 3.14159); // fade in / out within the tick
    float slice = floor(uv.y * p_slices);
    float h = hash12(vec2(slice + 1.0, tick + 1.0));
    float tear = step(1.0 - 0.35 * p_amount, h) * env;
    tear = max(tear, uBeat.w * p_pulse); // the beat can trigger a one-frame tear

    // Slice displacement and channel split.
    float shift = (hash12(vec2(slice * 3.1 + 2.0, tick * 1.7 + 3.0)) - 0.5) * 0.25 * tear;
    vec2 suv = vec2(uv.x + shift, uv.y);
    float split = 0.006 * p_amount * (0.25 + tear);

    // Base: slow drifting gradient, sampled per channel with the split.
    float gr = fbm((suv + vec2(split, 0.0)) * 1.4 + t * 0.35, 3);
    float gg = fbm(suv * 1.4 + t * 0.35, 3);
    float gb = fbm((suv - vec2(split, 0.0)) * 1.4 + t * 0.35, 3);
    float cyc = t * 0.12 + uMood.y * 0.25;
    vec3 col = vec3(palette(0.2 + 0.8 * tri(gr * 1.3 + cyc)).r,
                    palette(0.2 + 0.8 * tri(gg * 1.3 + cyc)).g,
                    palette(0.2 + 0.8 * tri(gb * 1.3 + cyc)).b);

    // Block quantisation inside active slices.
    vec2 cell = floor(uv * vec2(32.0, 18.0));
    float block = hash12(cell + tick);
    col = mix(col, col * (0.7 + 0.6 * block), tear * 0.6 * p_amount);

    col *= 0.85 + 0.3 * uMood.x;
    fragColor = vec4(col, 1.0);
}
