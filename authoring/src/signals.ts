// Synthetic signals for authoring: approximates what the tv-app host will feed the
// scenes (uBands / uMood / uBeat) with no real source. Two modes:
//   auto   — slow LFOs + a metronome beat (stand-in for the host's Synth)
//   manual — slider values, plus the metronome beat if enabled
// Both keep the arrays below filled every frame; the renderer uploads them as-is.

export interface ManualSignals {
  low: number;
  mid: number;
  high: number;
  energy: number;
  moodEnergy: number;
  moodBrightness: number;
  centroid: number;
}

export class Signals {
  readonly bands = new Float32Array(4); // low, mid, high, energy
  readonly mood = new Float32Array(4); // moodEnergy, moodBrightness, centroid, reserved
  readonly beat = new Float32Array(4); // envelope, phase, bpm, onset

  auto = true;
  beatEnabled = true;
  bpm = 100;
  /** 0..1, how much the beat envelope leaks into the fast bands in auto mode. */
  punch = 0.5;

  readonly manual: ManualSignals = {
    low: 0.35,
    mid: 0.3,
    high: 0.2,
    energy: 0.3,
    moodEnergy: 0.45,
    moodBrightness: 0.5,
    centroid: 0.5,
  };

  private env = 0;
  private phase = 0;
  private pendingTap = false;

  /** Force an onset on the next update (the "Tap" button). */
  tap(): void {
    this.pendingTap = true;
  }

  update(dt: number, t: number): void {
    // --- beat metronome
    let onset = 0;
    if (this.beatEnabled && this.bpm > 0) {
      const next = this.phase + (dt * this.bpm) / 60;
      if (next >= 1) onset = 1;
      this.phase = next - Math.floor(next);
    } else {
      this.phase = 0;
    }
    if (this.pendingTap) {
      onset = 1;
      this.phase = 0;
      this.pendingTap = false;
    }
    this.env = onset ? 1 : this.env * Math.exp(-dt * 5.5);

    // --- bands and mood
    let low: number, mid: number, high: number, energy: number, mE: number, mB: number, cen: number;
    if (this.auto) {
      energy = 0.35 + 0.22 * Math.sin(t * 0.13) + 0.08 * Math.sin(t * 0.71 + 1.0);
      low = energy + 0.12 * Math.sin(t * 0.9 + 1.0) + this.punch * 0.45 * this.env;
      mid = energy * 0.85 + 0.1 * Math.sin(t * 1.3 + 2.0) + this.punch * 0.15 * this.env;
      high = energy * 0.6 + 0.08 * Math.sin(t * 2.1 + 0.5) + this.punch * 0.1 * this.env;
      energy += this.punch * 0.25 * this.env;
      mE = 0.45 + 0.25 * Math.sin(t * 0.021);
      mB = 0.5 + 0.2 * Math.sin(t * 0.017 + 2.0);
      cen = 0.5 + 0.15 * Math.sin(t * 0.05 + 1.0);
    } else {
      const m = this.manual;
      low = m.low;
      mid = m.mid;
      high = m.high;
      energy = m.energy;
      mE = m.moodEnergy;
      mB = m.moodBrightness;
      cen = m.centroid;
    }

    this.bands[0] = clamp01(low);
    this.bands[1] = clamp01(mid);
    this.bands[2] = clamp01(high);
    this.bands[3] = clamp01(energy);
    this.mood[0] = clamp01(mE);
    this.mood[1] = clamp01(mB);
    this.mood[2] = clamp01(cen);
    this.mood[3] = 0;
    this.beat[0] = this.env;
    this.beat[1] = this.phase;
    this.beat[2] = this.beatEnabled ? this.bpm : 0;
    this.beat[3] = onset;
  }
}

function clamp01(v: number): number {
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
