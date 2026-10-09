// Preset lab (decision 16): nine live variations of the current scene around a starting point.
// "Daqui" makes a tile the new starting point and deals a new round; "Usar" sends it back to the
// editor; "Guardar" appends it as a preset to the scene's scene.json through the dev server.
// The tiles share one WebGL context (one Renderer each), copied to 2D canvases in turn.

import type { Composed } from './compose';
import { createGL } from './gl';
import { mutate, presetDiff, presetId } from './mutate';
import { Renderer } from './renderer';
import type { ParamValues, Profile, Rgb, SceneJson } from './scene';
import { Signals } from './signals';
import { el } from './ui';

export interface LabHost {
  scene: SceneJson;
  composed: Composed;
  outputFrag: string;
  profile: Profile | null;
  values: ParamValues;
  palette: Rgb[];
  /** Put a variation in the editor. */
  use(values: ParamValues, palette: Rgb[]): void;
}

interface Tile { r: Renderer; ctx: CanvasRenderingContext2D; values: ParamValues; palette: Rgb[]; box: HTMLElement }

const TILE_W = 480;
const TILE_H = 270;

export function openLab(h: LabHost): void {
  const glCanvas = document.createElement('canvas');
  const { gl, info } = createGL(glCanvas);
  const signals = new Signals();
  let base = { values: new Map(h.values), palette: h.palette.map((c) => [...c] as Rgb) };
  let amount = 0.2;
  let turnPalette = true;
  let round = 1;
  let selected = 4;
  let open = true;

  const root = el('div', 'lab');
  const head = el('div', 'lab-head');
  head.appendChild(el('h2', '', `Laboratório de presets · ${h.scene.name}`));
  const amountLabel = el('label', 'lab-ctl');
  const amountInput = el('input');
  Object.assign(amountInput, { type: 'range', min: '0.05', max: '0.6', step: '0.05', value: String(amount) });
  const amountValue = el('span', 'mono', `${Math.round(amount * 100)} %`);
  amountLabel.append('Variação ', amountInput, amountValue);
  const palLabel = el('label', 'lab-ctl');
  const palInput = el('input');
  Object.assign(palInput, { type: 'checkbox', checked: turnPalette });
  palLabel.append(palInput, ' rodar a paleta');
  const again = el('button', '', 'Nova ronda');
  const close = el('button', '', 'Fechar');
  head.append(amountLabel, palLabel, again, close);
  const grid = el('div', 'lab-grid');
  const foot = el('div', 'lab-foot');
  const name = el('input');
  Object.assign(name, { type: 'text', placeholder: 'Nome do preset', maxLength: 40 });
  const save = el('button', '', 'Guardar no scene.json');
  const status = el('span', 'muted');
  foot.append(el('span', 'muted', 'Selecionado: o quadro com contorno.'), name, save, status);
  root.append(head, grid, foot);
  document.body.appendChild(root);

  const tiles: Tile[] = [];
  for (let i = 0; i < 9; i++) {
    const r = new Renderer(gl, info, glCanvas);
    r.setOutputShader(h.outputFrag);
    r.setScene(h.scene, h.composed);
    if (h.profile) r.setProfile(h.profile);
    r.setSettings({ width: TILE_W, height: TILE_H, renderScale: h.scene.feedback ? 1 : 0.75, fps: 0 });
    const box = el('div', 'lab-tile');
    const cv = el('canvas');
    cv.width = TILE_W;
    cv.height = TILE_H;
    const bar = el('div', 'lab-bar');
    const from = el('button', '', 'Daqui');
    const use = el('button', '', 'Usar');
    bar.append(el('span', 'muted', i === 4 ? 'ponto de partida' : ''), from, use);
    box.append(cv, bar);
    grid.appendChild(box);
    const t: Tile = { r, ctx: cv.getContext('2d')!, values: base.values, palette: base.palette, box };
    tiles.push(t);
    cv.onclick = () => select(i);
    from.onclick = () => {
      base = { values: new Map(t.values), palette: t.palette.map((c) => [...c] as Rgb) };
      deal();
    };
    use.onclick = () => {
      h.use(new Map(t.values), t.palette.map((c) => [...c] as Rgb));
      status.textContent = 'No editor.';
    };
  }

  function select(i: number): void {
    selected = i;
    tiles.forEach((t, k) => t.box.classList.toggle('on', k === i));
  }

  /** Centre = the starting point; the other eight are variations of it. */
  function deal(): void {
    round++;
    tiles.forEach((t, i) => {
      const v = i === 4 ? { values: new Map(base.values), palette: base.palette } : mutate(h.scene, base.values, base.palette, { amount, palette: turnPalette, seed: round * 101 + i });
      t.values = v.values;
      t.palette = v.palette;
      t.r.setParamValues(t.values);
      t.r.setPalette(t.palette);
    });
    select(4);
  }

  amountInput.oninput = () => {
    amount = Number(amountInput.value);
    amountValue.textContent = `${Math.round(amount * 100)} %`;
  };
  palInput.onchange = () => (turnPalette = palInput.checked);
  again.onclick = deal;
  close.onclick = () => {
    open = false;
    root.remove();
    gl.getExtension('WEBGL_lose_context')?.loseContext();
  };
  save.onclick = async () => {
    const t = tiles[selected]!;
    const label = name.value.trim();
    if (!label) {
      status.textContent = 'Dá um nome ao preset.';
      return name.focus();
    }
    const preset = presetDiff(h.scene, presetId(label, (h.scene.presets ?? []).map((p) => p.id)), label, t.values, t.palette);
    const res = await fetch(`/api/presets/${h.scene.id}`, { method: 'POST', body: JSON.stringify(preset) }).catch(() => null);
    status.textContent = res?.ok ? `Guardado como "${preset.id}" em scenes/${h.scene.id}/scene.json. Falta o commit.` : `Não guardou: ${res ? await res.text() : 'sem servidor'}`;
  };

  deal();
  let rr = 0;
  const loop = (now: number) => {
    if (!open) return;
    for (let k = 0; k < 3; k++) {
      const t = tiles[rr]!;
      rr = (rr + 1) % tiles.length;
      if (t.r.frame(now, signals)) t.ctx.drawImage(glCanvas, 0, 0, TILE_W, TILE_H);
    }
    requestAnimationFrame(loop);
  };
  requestAnimationFrame(loop);
}
