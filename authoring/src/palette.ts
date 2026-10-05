// Host-side palette variation — the reference for docs/ARQUITETURA.md, section
// "Variação de paleta". Scenes keep reading uPalette only; the host decides what is in it.
//
//   rotate: walk the scene's palettes (current one first, then defaultPalette and the
//           presets' palettes, without duplicates), holding each for `holdS` seconds and
//           crossfading for `fadeS` seconds. Mixing is done in OKLab.
//   hue:    rotate the hue of the whole palette by hueDeg * offset(t) in OKLab (a, b),
//           offset(t) being the drift offset of the pseudo-param "@palette"; lightness and
//           chroma stay, so the amount of light in the room does not change.
//
// write() runs every frame and allocates nothing.

import { driftOffset, driftSpec, type DriftSpec } from './drift.ts'; // .ts: also loaded by node --test
import type { Rgb } from './scene';

// ---------------------------------------------------------------- colour

function toLinear(c: number): number {
  return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

function toSrgb(c: number): number {
  const v = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
  return Math.min(1, Math.max(0, v));
}

/** sRGB (display, 0..1) -> OKLab, written into out[o..o+2]. */
export function srgbToOklab(r: number, g: number, b: number, out: Float64Array, o: number): void {
  const lr = toLinear(r);
  const lg = toLinear(g);
  const lb = toLinear(b);
  const l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb);
  const m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb);
  const s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb);
  out[o] = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
  out[o + 1] = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
  out[o + 2] = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;
}

/** OKLab -> sRGB (display, clamped to 0..1), written into out[o..o+2]. */
export function oklabToSrgb(L: number, a: number, b: number, out: Float32Array | Float64Array, o: number): void {
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (L - 0.0894841775 * a - 1.291485548 * b) ** 3;
  out[o] = toSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s);
  out[o + 1] = toSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s);
  out[o + 2] = toSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s);
}

// ---------------------------------------------------------------- variation

export interface PaletteSettings {
  rotate: boolean;
  holdS: number; // seconds on each palette
  fadeS: number; // crossfade seconds
  hueDeg: number; // 0 = no hue drift
}

export class PaletteVariation {
  readonly settings: PaletteSettings = { rotate: false, holdS: 180, fadeS: 20, hueDeg: 0 };
  /** Index of the palette shown (or being faded from) at the last write(); for the panel. */
  current = 0;
  next = 0;
  mix = 0;

  private lab = new Float64Array(15); // OKLab of every palette, 15 numbers each
  private count = 0;
  private epoch = 0;
  private hueSpec: DriftSpec = driftSpec('', '@palette');

  /** Palettes to rotate through (first = current); `t` is the host's drift time now. */
  setPalettes(sceneId: string, palettes: Rgb[][], t: number): void {
    this.count = palettes.length;
    if (this.lab.length < this.count * 15) this.lab = new Float64Array(this.count * 15);
    palettes.forEach((pal, i) => {
      for (let k = 0; k < 5; k++) {
        const c = pal[k] ?? [0, 0, 0];
        srgbToOklab(c[0], c[1], c[2], this.lab, i * 15 + k * 3);
      }
    });
    this.epoch = t;
    this.hueSpec = driftSpec(sceneId, '@palette');
  }

  get size(): number {
    return this.count;
  }

  /** Fill `out` (15 floats, sRGB) with the palette for drift time `t`. */
  write(out: Float32Array, t: number): void {
    if (this.count === 0) return;
    const st = this.settings;
    let from = 0;
    let to = 0;
    let f = 0;
    if (st.rotate && this.count > 1) {
      const cycle = Math.max(1, st.holdS + st.fadeS);
      const local = Math.max(0, t - this.epoch);
      const k = Math.floor(local / cycle);
      const x = (local - k * cycle - st.holdS) / Math.max(1e-3, st.fadeS);
      f = x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x);
      from = k % this.count;
      to = (k + 1) % this.count;
    }
    this.current = from;
    this.next = to;
    this.mix = f;
    const ang = st.hueDeg !== 0 ? (st.hueDeg * Math.PI) / 180 * driftOffset(this.hueSpec, t) : 0;
    const ca = Math.cos(ang);
    const sa = Math.sin(ang);
    const lab = this.lab;
    for (let k = 0; k < 5; k++) {
      const i = from * 15 + k * 3;
      const j = to * 15 + k * 3;
      const L = lab[i]! + (lab[j]! - lab[i]!) * f;
      const a = lab[i + 1]! + (lab[j + 1]! - lab[i + 1]!) * f;
      const b = lab[i + 2]! + (lab[j + 2]! - lab[i + 2]!) * f;
      oklabToSrgb(L, a * ca - b * sa, a * sa + b * ca, out, k * 3);
    }
  }
}

/** The scene's palettes for rotation: `first`, then defaultPalette and preset palettes, deduplicated. */
export function rotationPalettes(first: Rgb[], others: Rgb[][]): Rgb[][] {
  const key = (p: Rgb[]) => p.map((c) => c.map((v) => Math.round(v * 255)).join(',')).join(';');
  const seen = new Set([key(first)]);
  const out = [first];
  for (const p of others) {
    const k = key(p);
    if (p.length === 5 && !seen.has(k)) {
      seen.add(k);
      out.push(p);
    }
  }
  return out;
}
