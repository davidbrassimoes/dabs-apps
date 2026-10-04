// Scene / profile types and loaders. Mirrors docs/FORMATO-CENA.md (format 1).

export type ParamType = 'float' | 'int' | 'bool' | 'color';

export interface ParamDef {
  id: string;
  label: string;
  type: ParamType;
  min?: number;
  max?: number;
  default: number | boolean | string;
  step?: number;
  drift?: number;
  group?: string;
}

export interface SceneJson {
  format: number;
  id: string;
  name: string;
  description?: string;
  tags?: string[];
  fps?: number;
  renderScale?: number;
  feedback?: boolean;
  image?: 'none' | 'optional' | 'required';
  defaultPalette?: string[];
  params: ParamDef[];
}

export interface Profile {
  format: number;
  id: string;
  name: string;
  brightness: number;
  contrast: number;
  saturation: number;
  gamma: number;
  blackFloor: number;
  temperature: number;
  dither: number;
  renderScale: number;
  maxFps: number;
}

export type Rgb = [number, number, number];
/** Runtime value of a param: numbers for float/int/bool (bool as 0/1), Rgb for color. */
export type ParamValue = number | Rgb;
export type ParamValues = Map<string, ParamValue>;

const FALLBACK_PALETTE = ['#101018', '#2a2a48', '#5a4a8a', '#c07a6a', '#f0d0a0'];

export async function fetchText(url: string): Promise<string> {
  const res = await fetch(`${url}?t=${Date.now()}`, { cache: 'no-store' });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText} for ${url}`);
  return res.text();
}

export async function fetchJson<T>(url: string): Promise<T> {
  return JSON.parse(await fetchText(url)) as T;
}

export function loadSceneJson(id: string): Promise<SceneJson> {
  return fetchJson<SceneJson>(`/scenes/${id}/scene.json`);
}

export function loadSceneFrag(id: string): Promise<string> {
  return fetchText(`/scenes/${id}/scene.frag`);
}

export function loadProfile(id: string): Promise<Profile> {
  return fetchJson<Profile>(`/shared/profiles/${id}.json`);
}

export function hexToRgb(hex: string): Rgb {
  const h = hex.replace('#', '');
  const n = parseInt(h.length === 3 ? h.split('').map((c) => c + c).join('') : h, 16);
  if (Number.isNaN(n)) return [0, 0, 0];
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

export function rgbToHex(c: Rgb): string {
  const q = (v: number) => Math.round(Math.max(0, Math.min(1, v)) * 255).toString(16).padStart(2, '0');
  return `#${q(c[0])}${q(c[1])}${q(c[2])}`;
}

export function scenePalette(scene: SceneJson): Rgb[] {
  const src = scene.defaultPalette && scene.defaultPalette.length === 5 ? scene.defaultPalette : FALLBACK_PALETTE;
  return src.map(hexToRgb);
}

/** Default runtime value for a param. */
export function paramDefault(p: ParamDef): ParamValue {
  switch (p.type) {
    case 'color':
      return hexToRgb(String(p.default));
    case 'bool':
      return p.default === true || p.default === 1 || p.default === 'true' ? 1 : 0;
    default:
      return Number(p.default);
  }
}

/** Default values for all params; keeps values from `previous` when id and type still match. */
export function defaultValues(scene: SceneJson, previous?: ParamValues): ParamValues {
  const out: ParamValues = new Map();
  for (const p of scene.params) {
    const prev = previous?.get(p.id);
    const isColor = p.type === 'color';
    if (prev !== undefined && Array.isArray(prev) === isColor) out.set(p.id, prev);
    else out.set(p.id, paramDefault(p));
  }
  return out;
}

/** Minimal validation; returns human-readable problems (empty when fine). */
export function validateScene(scene: SceneJson, folderId: string): string[] {
  const errs: string[] = [];
  if (scene.format !== 1) errs.push(`format must be 1 (got ${String(scene.format)})`);
  if (scene.id !== folderId) errs.push(`id "${scene.id}" does not match folder "${folderId}"`);
  if (!scene.name) errs.push('missing name');
  if (!Array.isArray(scene.params)) errs.push('params must be an array');
  else {
    const seen = new Set<string>();
    for (const p of scene.params) {
      if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(p.id)) errs.push(`param id "${p.id}" is not a valid identifier`);
      if (seen.has(p.id)) errs.push(`duplicate param id "${p.id}"`);
      seen.add(p.id);
      if (!['float', 'int', 'bool', 'color'].includes(p.type)) errs.push(`param "${p.id}": unknown type "${p.type}"`);
      if ((p.type === 'float' || p.type === 'int') && (p.min === undefined || p.max === undefined))
        errs.push(`param "${p.id}": min/max required`);
    }
  }
  if (scene.defaultPalette && scene.defaultPalette.length !== 5) errs.push('defaultPalette must have 5 colors');
  return errs;
}
