// Scene / profile types and loaders. Mirrors docs/FORMATO-CENA.md (format 4).

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
  presets?: Preset[];
}

/** A named starting point: overrides some param values and/or the palette. */
export interface Preset {
  id: string;
  name: string;
  params?: Record<string, number | boolean | string>;
  palette?: string[];
}

/** Scene format versions this loader accepts; presets need format 2+, uNoise is provided from 3. */
export const SCENE_FORMATS = [1, 2, 3, 4];

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

export function scenePalette(scene: SceneJson, preset?: Preset): Rgb[] {
  const src =
    preset?.palette && preset.palette.length === 5
      ? preset.palette
      : scene.defaultPalette && scene.defaultPalette.length === 5
        ? scene.defaultPalette
        : FALLBACK_PALETTE;
  return src.map(hexToRgb);
}

/** Runtime value of a param from a JSON value (its `default` or a preset override). */
export function paramValue(p: ParamDef, raw: number | boolean | string): ParamValue {
  switch (p.type) {
    case 'color':
      return hexToRgb(String(raw));
    case 'bool':
      return raw === true || raw === 1 || raw === 'true' ? 1 : 0;
    default:
      return Number(raw);
  }
}

/** Default runtime value for a param. */
export function paramDefault(p: ParamDef): ParamValue {
  return paramValue(p, p.default);
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

/** Values for a preset: scene defaults, overridden by the preset's params. */
export function presetValues(scene: SceneJson, preset: Preset): ParamValues {
  const out: ParamValues = new Map();
  for (const p of scene.params) {
    const raw = preset.params?.[p.id];
    out.set(p.id, raw === undefined ? paramDefault(p) : paramValue(p, raw));
  }
  return out;
}

/** The current values and palette as a preset object, ready to paste into scene.json. */
export function presetFromValues(scene: SceneJson, id: string, name: string, values: ParamValues, palette: Rgb[]): Preset {
  const params: Record<string, number | boolean | string> = {};
  for (const p of scene.params) {
    const v = values.get(p.id);
    if (v === undefined) continue;
    if (Array.isArray(v)) params[p.id] = rgbToHex(v);
    else if (p.type === 'bool') params[p.id] = v === 1;
    else if (p.type === 'int') params[p.id] = Math.round(v);
    else params[p.id] = Math.round(v * 1000) / 1000;
  }
  return { id, name, params, palette: palette.map(rgbToHex) };
}

const HEX_RE = /^#[0-9a-fA-F]{6}$/;
const PRESET_ID_RE = /^[a-z0-9]+(-[a-z0-9]+)*$/;

function checkPalette(errs: string[], where: string, pal: unknown): void {
  if (!Array.isArray(pal) || pal.length !== 5) errs.push(`${where} must have 5 colors`);
  else for (const c of pal) if (typeof c !== 'string' || !HEX_RE.test(c)) errs.push(`${where}: "${String(c)}" is not #rrggbb`);
}

/** Minimal validation; returns human-readable problems (empty when fine). */
export function validateScene(scene: SceneJson, folderId: string): string[] {
  const errs: string[] = [];
  if (!SCENE_FORMATS.includes(scene.format))
    errs.push(`format must be one of ${SCENE_FORMATS.join(', ')} (got ${String(scene.format)})`);
  if (scene.id !== folderId) errs.push(`id "${scene.id}" does not match folder "${folderId}"`);
  if (!scene.name) errs.push('missing name');
  const byId = new Map<string, ParamDef>();
  if (!Array.isArray(scene.params)) errs.push('params must be an array');
  else {
    for (const p of scene.params) {
      if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(p.id)) errs.push(`param id "${p.id}" is not a valid identifier`);
      if (byId.has(p.id)) errs.push(`duplicate param id "${p.id}"`);
      byId.set(p.id, p);
      if (!['float', 'int', 'bool', 'color'].includes(p.type)) errs.push(`param "${p.id}": unknown type "${p.type}"`);
      if ((p.type === 'float' || p.type === 'int') && (p.min === undefined || p.max === undefined))
        errs.push(`param "${p.id}": min/max required`);
    }
  }
  if (scene.defaultPalette) checkPalette(errs, 'defaultPalette', scene.defaultPalette);
  if (scene.presets !== undefined) {
    if (scene.format < 2) errs.push('presets need format 2');
    if (!Array.isArray(scene.presets)) errs.push('presets must be an array');
    else {
      const seen = new Set<string>();
      for (const pr of scene.presets) {
        const where = `preset "${String(pr.id)}"`;
        if (typeof pr.id !== 'string' || !PRESET_ID_RE.test(pr.id)) errs.push(`${where}: id must be kebab-case`);
        if (seen.has(pr.id)) errs.push(`duplicate preset id "${pr.id}"`);
        seen.add(pr.id);
        if (!pr.name) errs.push(`${where}: missing name`);
        if (pr.palette !== undefined) checkPalette(errs, `${where}: palette`, pr.palette);
        for (const [k, v] of Object.entries(pr.params ?? {})) {
          const p = byId.get(k);
          if (!p) errs.push(`${where}: unknown param "${k}"`);
          else if (p.type === 'color') {
            if (typeof v !== 'string' || !HEX_RE.test(v)) errs.push(`${where}: "${k}" must be #rrggbb`);
          } else if (p.type === 'bool') {
            if (typeof v !== 'boolean') errs.push(`${where}: "${k}" must be true/false`);
          } else if (typeof v !== 'number' || v < (p.min ?? -Infinity) || v > (p.max ?? Infinity))
            errs.push(`${where}: "${k}" = ${String(v)} outside [${String(p.min)}, ${String(p.max)}]`);
          else if (p.type === 'int' && !Number.isInteger(v)) errs.push(`${where}: "${k}" must be an integer`);
        }
      }
    }
  }
  return errs;
}
