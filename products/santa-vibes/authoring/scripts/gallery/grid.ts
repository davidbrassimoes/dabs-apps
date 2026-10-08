// Live thumbnails: one Renderer per scene, all on one shared WebGL context, copied to 2D canvases.

import { createGL } from '../../src/gl';
import { Renderer } from '../../src/renderer';
import { Signals } from '../../src/signals';
import { scenePalette, type SceneJson } from '../../src/scene';
import { $, D, composeScene, configureSignals, paletteFor, state, swatches, valuesFor } from './common';

const thumbGLCanvas = document.createElement('canvas');
const thumbGL = createGL(thumbGLCanvas);
export interface Thumb { s: SceneJson; r: Renderer; sig: Signals; ctx: CanvasRenderingContext2D; visible: boolean; ready: boolean; preset: string }
export const thumbs: Thumb[] = [];

async function initThumb(s: SceneJson, canvas: HTMLCanvasElement): Promise<Thumb> {
  const r = new Renderer(thumbGL.gl, thumbGL.info, thumbGLCanvas);
  r.setOutputShader(D.files['shared/glsl/output.frag']!);
  const t: Thumb = { s, r, sig: new Signals(), ctx: canvas.getContext('2d')!, visible: false, ready: false, preset: '' };
  try {
    r.setScene(s, await composeScene(s));
    t.ready = true;
  } catch (e) {
    console.error(s.id, e);
  }
  pushThumb(t);
  return t;
}
export function pushThumb(t: Thumb): void {
  t.r.setParamValues(valuesFor(t.s, t.preset));
  t.r.setPalette(paletteFor(t.s, t.preset));
  const img = state.image && t.s.image !== 'none' ? state.image : null;
  t.r.setImage(img ? img.src : null, img?.w ?? 0, img?.h ?? 0);
  t.r.setProfile(D.profiles[state.profile]!);
  t.r.setSettings({ width: 384, height: 216, renderScale: t.s.feedback ? 0.75 : 0.5, fps: 0 });
  configureSignals(t.sig);
}

let rr = 0;
export function renderThumbs(now: number): void {
  const vis = thumbs.filter((t) => t.visible && t.ready);
  if (!vis.length) return;
  const per = Math.min(vis.length, 3);
  for (let k = 0; k < per; k++) {
    const t = vis[(rr + k) % vis.length]!;
    if (t.r.frame(now, t.sig)) t.ctx.drawImage(thumbGLCanvas, 0, 0, t.ctx.canvas.width, t.ctx.canvas.height);
  }
  rr = (rr + per) % vis.length;
}

const STYLE_ORDER = ['ambient', 'psicadélico clássico', 'psicadélico tech', 'glitch', 'geométrico'];
export function buildGrid(onPick: (idx: number, preset: string) => void): void {
  const host = $('#grid');
  const groups = new Map<string, SceneJson[]>();
  for (const s of D.scenes) {
    const st = s.tags?.[0] ?? 'outro';
    groups.set(st, [...(groups.get(st) ?? []), s]);
  }
  const order = [...groups.keys()].sort((a, b) => (STYLE_ORDER.indexOf(a) + 99) % 99 - (STYLE_ORDER.indexOf(b) + 99) % 99);
  const io = new IntersectionObserver((es) => {
    for (const e of es) {
      const t = thumbs.find((x) => x.ctx.canvas === e.target);
      if (t) t.visible = e.isIntersecting;
    }
  });
  for (const g of order) {
    const sec = document.createElement('div');
    sec.className = 'group';
    const list = groups.get(g)!;
    sec.innerHTML = `<h3>${g} <span class="count">${list.length}</span></h3>`;
    const row = document.createElement('div');
    row.className = 'cards';
    for (const s of list) {
      const card = document.createElement('article');
      card.className = 'card';
      card.dataset.id = s.id;
      const cv = document.createElement('canvas');
      cv.width = 384;
      cv.height = 216;
      cv.setAttribute('aria-hidden', 'true');
      const btn = document.createElement('button');
      btn.className = 'thumb';
      btn.title = `Ver ${s.name} na TV`;
      btn.appendChild(cv);
      btn.onclick = () => {
        onPick(D.scenes.indexOf(s), '');
        $('#tv-wrap').scrollIntoView({ behavior: 'smooth', block: 'center' });
      };
      card.appendChild(btn);
      const badges = [s.feedback ? 'feedback' : '', s.image === 'optional' ? 'imagem' : s.image === 'required' ? 'só com imagem' : ''].filter(Boolean);
      const meta = document.createElement('div');
      meta.className = 'meta';
      meta.innerHTML = `<div class="nm"><b>${s.name}</b> <code>${s.id}</code></div>
        <div class="badges">${badges.map((b) => `<span class="badge">${b}</span>`).join('')}<span class="badge dim">${s.fps ?? 30} fps · ×${s.renderScale ?? 0.5}</span></div>`;
      const pre = document.createElement('div');
      pre.className = 'presets';
      const mk = (id: string, name: string) => {
        const b = document.createElement('button');
        b.className = 'pchip';
        b.dataset.preset = id;
        b.appendChild(swatches(scenePalette(s, s.presets?.find((p) => p.id === id))));
        b.append(name);
        b.onclick = () => {
          const t = thumbs.find((x) => x.s === s);
          if (t) {
            t.preset = id;
            pushThumb(t);
          }
          pre.querySelectorAll('.pchip').forEach((x) => x.classList.toggle('on', x === b));
          onPick(D.scenes.indexOf(s), id);
        };
        if (!id) b.classList.add('on');
        pre.appendChild(b);
      };
      mk('', 'Defeito');
      for (const p of s.presets ?? []) mk(p.id, p.name);
      meta.appendChild(pre);
      card.appendChild(meta);
      row.appendChild(card);
      void initThumb(s, cv).then((t) => {
        thumbs.push(t);
        io.observe(cv);
      });
    }
    sec.appendChild(row);
    host.appendChild(sec);
  }
}

