// Port of tv-app SignalMixer.kt (docs/ARQUITETURA.md, "Mixer de reatividade"), so a scene fed
// by the pc-bridge looks the same in the browser as on the TV. Checked frame by frame against
// test/fixtures/mixer-cases.json, which the Kotlin test generates.
//
// out = mix(synth, real, eased(reactivity * presence)); values smoothed over ~30 ms; the beat
// phase runs on its own at the received bpm and is pulled towards each packet's phase;
// presence rises over RISE_S and falls over FALL_S once the source is absent or silent.

import { BEAT, BPM, CENTROID, ENERGY, FIELDS, FLAG_ONSET, FLAG_SILENCE, FLAG_TEMPO_VALID, HIGH, LOW, MID, MOOD_BRIGHTNESS, MOOD_ENERGY, PHASE, type SignPacket } from './sign.ts';

export const ABSENT_S = 1.5;
export const SILENT_S = 4.0;
export const RISE_S = 0.5;
export const FALL_S = 1.5;
export const SMOOTH_S = 0.03;
export const PHASE_GAIN = 0.3;

/** What the mixer blends towards when the source is absent: the authoring's own signals. */
export interface SynthArrays {
  readonly bands: Float32Array;
  readonly mood: Float32Array;
  readonly beat: Float32Array;
}

export class SignalMixer {
  readonly bands = new Float32Array(4);
  readonly mood = new Float32Array(4);
  readonly beat = new Float32Array(4);

  /** 0 = ignore the real source, 1 = only the real source while it is present. */
  reactivity = 1.0;
  realWeight = 0;
  present = false;

  private readonly target = new Float32Array(FIELDS);
  private readonly smooth = new Float32Array(FIELDS);
  private hasData = false;
  private sincePacket = Number.MAX_VALUE;
  private sinceSound = Number.MAX_VALUE;
  private presence = 0;
  private phase = 0;
  private packetPhase = 0;
  private freshPhase = false;
  private tempoValid = false;
  private pendingOnset = false;

  /** The source's tempo while it is present and has one; else 0. */
  get realBpm(): number {
    return this.tempoValid && this.present ? this.target[BPM]! : 0;
  }

  onPacket(p: SignPacket): void {
    this.target.set(p.values);
    if (!this.hasData) this.smooth.set(this.target);
    this.hasData = true;
    this.sincePacket = 0;
    if (!(p.flags & FLAG_SILENCE)) this.sinceSound = 0;
    if (p.flags & FLAG_ONSET) this.pendingOnset = true;
    const wasValid = this.tempoValid;
    this.tempoValid = (p.flags & FLAG_TEMPO_VALID) !== 0 && this.target[BPM]! > 0;
    this.packetPhase = this.target[PHASE]!;
    if (this.tempoValid && !wasValid) this.phase = this.packetPhase;
    this.freshPhase = true;
  }

  update(dt: number, synth: SynthArrays): void {
    this.sincePacket += dt;
    this.sinceSound += dt;
    this.present = this.hasData && this.sincePacket < ABSENT_S && this.sinceSound < SILENT_S;
    this.presence = this.present ? Math.min(1, this.presence + dt / RISE_S) : Math.max(0, this.presence - dt / FALL_S);

    const k = 1 - Math.exp(-dt / SMOOTH_S);
    for (let i = 0; i < FIELDS; i++) {
      if (i === PHASE || i === BPM) continue;
      this.smooth[i] = this.smooth[i]! + Math.fround((this.target[i]! - this.smooth[i]!) * k);
    }
    if (this.tempoValid) {
      this.phase += (dt * this.target[BPM]!) / 60;
      if (this.freshPhase) {
        let e = this.packetPhase - this.phase;
        e -= Math.floor(e + 0.5);
        this.phase += PHASE_GAIN * e;
      }
      this.phase -= Math.floor(this.phase);
    }
    this.freshPhase = false;

    const r = Math.min(1, Math.max(0, this.presence * this.reactivity));
    const w = r * r * (3 - 2 * r);
    this.realWeight = w;
    const wf = Math.fround(w);
    const s = this.smooth;
    this.bands[0] = lerp(synth.bands[0]!, s[LOW]!, wf);
    this.bands[1] = lerp(synth.bands[1]!, s[MID]!, wf);
    this.bands[2] = lerp(synth.bands[2]!, s[HIGH]!, wf);
    this.bands[3] = lerp(synth.bands[3]!, s[ENERGY]!, wf);
    this.mood[0] = lerp(synth.mood[0]!, s[MOOD_ENERGY]!, wf);
    this.mood[1] = lerp(synth.mood[1]!, s[MOOD_BRIGHTNESS]!, wf);
    this.mood[2] = lerp(synth.mood[2]!, s[CENTROID]!, wf);
    this.mood[3] = 0;
    this.beat[0] = lerp(synth.beat[0]!, s[BEAT]!, wf);
    // Phase, bpm and onset cannot be blended: the dominant source gives them.
    if (w >= 0.5) {
      this.beat[1] = this.tempoValid ? this.phase : 0;
      this.beat[2] = this.realBpm;
      this.beat[3] = this.pendingOnset ? 1 : 0;
    } else {
      this.beat[1] = synth.beat[1]!;
      this.beat[2] = synth.beat[2]!;
      this.beat[3] = synth.beat[3]!;
    }
    this.pendingOnset = false;
  }
}

function lerp(a: number, b: number, t: number): number {
  return Math.fround(a + Math.fround(Math.fround(b - a) * t));
}
