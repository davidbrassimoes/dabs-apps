// Santa Vibes reactive helpers. Requires header.glsl (uBeat).

// A smooth swell that peaks on every beat, 0..1. With a tempo it follows the beat
// phase (raised cosine, no jumps, so the scene pulses with the music without
// flashing); without one it falls back to the beat envelope.
float groove() {
    if (uBeat.z <= 0.0) return uBeat.x;
    float c = 0.5 + 0.5 * cos(6.2831853 * uBeat.y);
    return c * c;
}
