// Shared state of the gallery: embedded scene data, composition, values and palettes.

import { collectIncludes, compose, type Composed } from '../../src/compose';
import type { Signals } from '../../src/signals';
import { defaultValues, presetValues, scenePalette, type ParamValues, type Profile, type Rgb, type SceneJson } from '../../src/scene';

interface Data {
  files: Record<string, string>; // "shared/glsl/..." and "scenes/<id>/scene.frag"
  scenes: SceneJson[];
  profiles: Record<string, Profile>;
}
declare const SV_DATA: Data;
export const D = SV_DATA;

export const $ = <T extends HTMLElement>(s: string): T => document.querySelector(s) as T;
const fetchInclude = async (p: string) => {
  const t = D.files[`shared/glsl/${p}`];
  if (t === undefined) throw new Error(p);
  return t;
};

const composed = new Map<string, Composed>();
export async function composeScene(s: SceneJson): Promise<Composed> {
  let c = composed.get(s.id);
  if (!c) {
    const body = D.files[`scenes/${s.id}/scene.frag`]!;
    c = compose(D.files['shared/glsl/header.glsl']!, s.params, body, `scenes/${s.id}/scene.frag`, await collectIncludes(body, fetchInclude));
    composed.set(s.id, c);
  }
  return c;
}

export const state = {
  idx: 0,
  preset: '' as string,
  react: 1, // the box default since 2026-10-07
  music: true,
  image: null as null | { src: TexImageSource; w: number; h: number; palette: Rgb[]; name: string },
  profile: 'tv',
  overlay: false,
};

export function paletteFor(s: SceneJson, presetId: string): Rgb[] {
  if (state.image && s.image !== 'none') return state.image.palette;
  return scenePalette(s, s.presets?.find((p) => p.id === presetId));
}
export function valuesFor(s: SceneJson, presetId: string): ParamValues {
  const p = s.presets?.find((x) => x.id === presetId);
  const v = p ? presetValues(s, p) : defaultValues(s);
  if (v.has('react')) v.set('react', state.react);
  return v;
}
export function configureSignals(sig: Signals): void {
  // The box: reactive look has no synthetic metronome; with music the real beat drives it.
  // Here "music" stands in for a real source with a 112 BPM metronome.
  sig.bpm = 112;
  sig.beatEnabled = state.music || state.react === 0;
  sig.punch = state.music ? 0.5 : state.react === 0 ? 0.5 : 0;
}

const hex = (c: Rgb) => '#' + c.map((x) => Math.round(Math.max(0, Math.min(1, x)) * 255).toString(16).padStart(2, '0')).join('');
export function swatches(p: Rgb[]): HTMLElement {
  const d = document.createElement('span');
  d.className = 'sw';
  for (const c of p) {
    const i = document.createElement('i');
    i.style.background = hex(c);
    d.appendChild(i);
  }
  return d;
}

