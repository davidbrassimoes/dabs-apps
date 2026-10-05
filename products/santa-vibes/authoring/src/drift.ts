// Parameter drift — the reference implementation of docs/ARQUITETURA.md, section
// "Drift de parâmetros". The tv-app (Kotlin) MUST produce the same numbers; the cases
// in test/fixtures/drift-cases.json are the shared contract.
//
// Every float/int param with drift > 0 wanders around the user's value:
//   value = clamp(base + offset(t) * 0.5 * drift * amount * (max - min), min, max)
// i.e. drift is the peak-to-peak swing as a fraction of the range.
// (int params are rounded after clamping). offset(t) in [-1, 1] is a sum of two sines
// whose period (30..120 s) and phases come from a seed hashed from "<scene>/<param>",
// so every param moves on its own and the motion is the same on every host.
// No allocation per frame: specs are computed once per scene.

import type { ParamDef } from './scene';

export interface DriftSpec {
  f1: number; // Hz
  f2: number; // Hz
  ph1: number; // radians
  ph2: number; // radians
}

const TAU = Math.PI * 2;

/** FNV-1a, 32 bits, over the UTF-16 code units of `s` (ids are ASCII in practice). */
export function fnv1a(s: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 0x01000193) >>> 0;
  }
  return h >>> 0;
}

/** xorshift32 step; returns the next state (uint32, never 0 for a non-zero input). */
export function xorshift32(x: number): number {
  x ^= x << 13;
  x >>>= 0;
  x ^= x >>> 17;
  x ^= x << 5;
  return x >>> 0;
}

export function driftSpec(sceneId: string, paramId: string): DriftSpec {
  let x = fnv1a(`${sceneId}/${paramId}`) || 1;
  const next = (): number => {
    x = xorshift32(x);
    return x / 4294967296;
  };
  const u1 = next();
  const u2 = next();
  const u3 = next();
  const f1 = 1 / (30 + 90 * u1);
  return { f1, f2: f1 * 2.618, ph1: TAU * u2, ph2: TAU * u3 };
}

/** Smooth offset in [-1, 1] at drift time t (seconds). */
export function driftOffset(s: DriftSpec, t: number): number {
  return 0.65 * Math.sin(TAU * s.f1 * t + s.ph1) + 0.35 * Math.sin(TAU * s.f2 * t + s.ph2);
}

/** Effective value of a float/int param; `amount` is the global drift multiplier (1 = as authored). */
export function driftValue(p: ParamDef, base: number, s: DriftSpec, t: number, amount: number): number {
  const d = p.drift ?? 0;
  if (d <= 0 || amount <= 0 || (p.type !== 'float' && p.type !== 'int')) return base;
  const min = p.min ?? 0;
  const max = p.max ?? 1;
  const v = Math.min(max, Math.max(min, base + driftOffset(s, t) * 0.5 * d * amount * (max - min)));
  return p.type === 'int' ? Math.round(v) : v;
}
