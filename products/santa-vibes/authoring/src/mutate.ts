// Preset lab (decision 16): variations around a starting point, reproducible from a seed.
// Params move by up to `amount` of their range; the palette turns its hue in OKLab, keeping
// lightness, so a dark-to-light palette stays dark-to-light (projector-friendly).

import { oklabToSrgb, srgbToOklab } from './palette.ts';
import { paramDefault, rgbToHex, type ParamValues, type Preset, type Rgb, type SceneJson } from './scene.ts';

/** The param the host forces (FORMATO-CENA.md): never varied, never saved in a preset. */
export const HOST_PARAM = 'react';

export interface MutateOpts {
  amount: number; // 0..1, fraction of each param's range
  palette: boolean; // also turn the palette's hue
  seed: number;
}

/** xorshift32 in [0, 1); state kept in the closure. */
export function rng(seed: number): () => number {
  let x = seed >>> 0 || 0x9e3779b9;
  return () => {
    x ^= x << 13;
    x >>>= 0;
    x ^= x >>> 17;
    x ^= x << 5;
    x >>>= 0;
    return x / 4294967296;
  };
}

export function mutate(scene: SceneJson, values: ParamValues, palette: Rgb[], o: MutateOpts): { values: ParamValues; palette: Rgb[] } {
  const r = rng(o.seed);
  const out: ParamValues = new Map(values);
  for (const p of scene.params) {
    if (p.id === HOST_PARAM || p.type === 'color' || p.type === 'bool') continue;
    const v = values.get(p.id);
    if (typeof v !== 'number' || p.min === undefined || p.max === undefined) continue;
    let nv = v + (r() * 2 - 1) * o.amount * (p.max - p.min);
    nv = Math.min(p.max, Math.max(p.min, nv));
    out.set(p.id, p.type === 'int' ? Math.round(nv) : Math.round(nv * 1000) / 1000);
  }
  const pal = o.palette ? turnHue(palette, (r() * 2 - 1) * Math.PI * o.amount) : palette.map((c) => [...c] as Rgb);
  return { values: out, palette: pal };
}

/** Rotate every colour's hue by `angle` radians in OKLab; lightness and chroma stay. */
export function turnHue(palette: Rgb[], angle: number): Rgb[] {
  const lab = new Float64Array(3);
  const rgb = new Float64Array(3);
  const cs = Math.cos(angle);
  const sn = Math.sin(angle);
  return palette.map((c) => {
    srgbToOklab(c[0], c[1], c[2], lab, 0);
    const a = lab[1]! * cs - lab[2]! * sn;
    const b = lab[1]! * sn + lab[2]! * cs;
    oklabToSrgb(lab[0]!, a, b, rgb, 0);
    return [rgb[0]!, rgb[1]!, rgb[2]!].map((x) => Math.min(1, Math.max(0, x))) as Rgb;
  });
}

/**
 * A scene.json preset: only the params that differ from the scene defaults (the format's rule),
 * never the host param, and the palette.
 */
export function presetDiff(scene: SceneJson, id: string, name: string, values: ParamValues, palette: Rgb[]): Preset {
  const params: Record<string, number | boolean | string> = {};
  for (const p of scene.params) {
    if (p.id === HOST_PARAM) continue;
    const v = values.get(p.id);
    if (v === undefined) continue;
    const d = paramDefault(p);
    if (Array.isArray(v)) {
      if (Array.isArray(d) && rgbToHex(v) === rgbToHex(d)) continue;
      params[p.id] = rgbToHex(v);
    } else if (p.type === 'bool') {
      if (v === d) continue;
      params[p.id] = v === 1;
    } else {
      const n = p.type === 'int' ? Math.round(v) : Math.round(v * 1000) / 1000;
      if (typeof d === 'number' && Math.abs(n - d) < 1e-9) continue;
      params[p.id] = n;
    }
  }
  return { id, name, params, palette: palette.map(rgbToHex) };
}

/** kebab-case id from a name, unique among `taken`. */
export function presetId(name: string, taken: string[]): string {
  const base =
    name
      .normalize('NFD')
      .replace(/\p{M}+/gu, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '') || 'preset';
  let id = base;
  for (let n = 2; taken.includes(id); n++) id = `${base}-${n}`;
  return id;
}
