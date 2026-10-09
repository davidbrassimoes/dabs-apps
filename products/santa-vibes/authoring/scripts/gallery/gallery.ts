// Santa Vibes screens gallery: the real authoring renderer, fed from an embedded copy of
// scenes/ and shared/ (build.mjs). One big "TV" plus live thumbnails of every scene.

import { createGL } from '../../src/gl';
import { Renderer } from '../../src/renderer';
import { Signals } from '../../src/signals';
import { scenePalette, type ParamValues } from '../../src/scene';
import { $, D, composeScene, configureSignals, paletteFor, state, swatches, valuesFor } from './common';
import { buildGrid, pushThumb, renderThumbs, thumbs } from './grid';
import { exampleImage, paletteOf } from './image';

// ------------------------------------------------------------------ the TV

const tvCanvas = $<HTMLCanvasElement>('#tv');
const tvGL = createGL(tvCanvas);
const tv = new Renderer(tvGL.gl, tvGL.info, tvCanvas);
tv.setOutputShader(D.files['shared/glsl/output.frag']!);
const tvSig = new Signals();
let tvValues: ParamValues = new Map();
const overlayEl = $<HTMLPreElement>('#overlay');

async function showScene(idx: number, presetId = '', fade = 1.2): Promise<void> {
  const n = D.scenes.length;
  state.idx = ((idx % n) + n) % n;
  state.preset = presetId;
  const s = D.scenes[state.idx]!;
  tv.transitionNext(fade);
  tv.setScene(s, await composeScene(s));
  pushTv();
  tv.resetTime();
  tv.clearFeedback();
  renderInfo();
}

function pushTv(): void {
  const s = D.scenes[state.idx]!;
  tvValues = valuesFor(s, state.preset);
  tv.setParamValues(tvValues);
  tv.setPalette(paletteFor(s, state.preset));
  const img = state.image && s.image !== 'none' ? state.image : null;
  tv.setImage(img ? img.src : null, img?.w ?? 0, img?.h ?? 0);
  tv.setProfile(D.profiles[state.profile]!);
  const w = tvCanvas.clientWidth > 900 ? 1920 : 1280;
  const scale = (s.renderScale ?? 0.5) * (1920 / w);
  tv.setSettings({ width: w, height: Math.round((w * 9) / 16), renderScale: Math.min(1, scale), fps: s.fps ?? 30 });
  configureSignals(tvSig);
}

function renderInfo(): void {
  const s = D.scenes[state.idx]!;
  const p = s.presets?.find((x) => x.id === state.preset);
  $('#now-name').textContent = s.name + (p ? ` · ${p.name}` : '');
  $('#now-desc').textContent = s.description ?? '';
  $('#now-pos').textContent = `${state.idx + 1}/${D.scenes.length}`;
  const pc = $('#now-presets');
  pc.replaceChildren();
  const mk = (id: string, name: string) => {
    const b = document.createElement('button');
    b.className = 'pchip' + (id === state.preset ? ' on' : '');
    b.appendChild(swatches(scenePalette(s, s.presets?.find((x) => x.id === id))));
    b.append(name);
    b.onclick = () => {
      state.preset = id;
      pushTv();
      renderInfo();
    };
    pc.appendChild(b);
  };
  mk('', 'Defeito');
  for (const x of s.presets ?? []) mk(x.id, x.name);
  const params = $('#now-params');
  params.replaceChildren();
  for (const d of s.params) {
    if (d.type === 'color' || d.type === 'bool') continue;
    const row = document.createElement('div');
    row.className = 'prow';
    row.dataset.id = d.id;
    row.innerHTML = `<span>${d.label}</span><span class="bar"><i></i></span><em></em>`;
    params.appendChild(row);
  }
  document.querySelectorAll<HTMLButtonElement>('[data-look]').forEach((b) => b.classList.toggle('on', Number(b.dataset.look) === state.react));
  document.querySelectorAll<HTMLButtonElement>('[data-profile]').forEach((b) => b.classList.toggle('on', b.dataset.profile === state.profile));
  $<HTMLButtonElement>('#music').classList.toggle('on', state.music);
  $('#music').setAttribute('aria-pressed', String(state.music));
  $('#img-state').textContent = state.image ? state.image.name : 'sem imagem';
  $('#img-note').hidden = !(state.image && s.image === 'none');
}

function updateLive(): void {
  const s = D.scenes[state.idx]!;
  document.querySelectorAll<HTMLElement>('#now-params .prow').forEach((row) => {
    const d = s.params.find((x) => x.id === row.dataset.id);
    const v = tv.liveValue(row.dataset.id!);
    if (!d || v === undefined) return;
    const lo = d.min ?? 0, hi = d.max ?? 1;
    (row.querySelector('i') as HTMLElement).style.width = `${(100 * (v - lo)) / (hi - lo || 1)}%`;
    row.querySelector('em')!.textContent = v.toFixed(2);
  });
  if (state.overlay) {
    const st = tv.stats;
    overlayEl.textContent =
      `${s.name} (${s.id}) · ${state.idx + 1}/${D.scenes.length}\n` +
      `${st.fps.toFixed(1)} fps · ${st.intervalMs.toFixed(1)} ms/frame · cap ${s.fps ?? 30}\n` +
      `${st.renderWidth}x${st.renderHeight} ${st.format} · renderScale ${(s.renderScale ?? 0.5).toFixed(2)} · t=${tv.time.toFixed(0)}s\n` +
      `sinal: ${state.music ? 'música simulada · 112.0 bpm' : 'sintético'}\n` +
      `versão: ${state.react ? 'reativa' : 'atual'}\n` +
      (state.image ? `imagem: ${state.image.name}\n` : '') +
      `◀ ▶ cena · ▲ versão · OK overlay`;
  }
}

function refreshAll(): void {
  pushTv();
  for (const t of thumbs) pushThumb(t);
  renderInfo();
}

function setImage(src: HTMLCanvasElement | null, w: number, h: number, name: string): void {
  state.image = src ? { src, w, h, palette: paletteOf(src, w, h), name } : null;
  document.querySelectorAll<HTMLButtonElement>('[data-img]').forEach((b) => b.classList.toggle('on', b.dataset.img === (!src ? 'none' : name.startsWith('pintura') ? 'example' : 'file')));
  refreshAll();
}

// ------------------------------------------------------------------ wiring

function wire(): void {
  $('#k-prev').onclick = () => void showScene(state.idx - 1);
  $('#k-next').onclick = () => void showScene(state.idx + 1);
  $('#k-up').onclick = () => {
    state.react = 1 - state.react;
    refreshAll();
  };
  $('#k-ok').onclick = () => {
    state.overlay = !state.overlay;
    overlayEl.hidden = !state.overlay;
    updateLive();
  };
  document.querySelectorAll<HTMLButtonElement>('[data-look]').forEach((b) => (b.onclick = () => {
    state.react = Number(b.dataset.look);
    refreshAll();
  }));
  document.querySelectorAll<HTMLButtonElement>('[data-profile]').forEach((b) => (b.onclick = () => {
    state.profile = b.dataset.profile!;
    refreshAll();
  }));
  $('#music').onclick = () => {
    state.music = !state.music;
    refreshAll();
  };
  $('[data-img="none"]').onclick = () => setImage(null, 0, 0, '');
  $('[data-img="example"]').onclick = () => {
    const c = exampleImage();
    setImage(c, c.width, c.height, 'pintura de exemplo (gerada)');
  };
  const file = $<HTMLInputElement>('#img-file');
  file.onchange = async () => {
    const f = file.files?.[0];
    if (!f) return;
    try {
      const bmp = await createImageBitmap(f);
      const k = Math.min(1, 1280 / bmp.width);
      const c = document.createElement('canvas');
      c.width = Math.round(bmp.width * k);
      c.height = Math.round(bmp.height * k);
      c.getContext('2d')!.drawImage(bmp, 0, 0, c.width, c.height);
      setImage(c, c.width, c.height, f.name);
    } catch {
      $('#img-state').textContent = 'não consegui ler esse ficheiro';
    }
  };
  $('#fs').onclick = () => void $('#tv-wrap').requestFullscreen?.().catch(() => undefined);
  addEventListener('keydown', (e) => {
    if ((e.target as HTMLElement).closest('input,textarea')) return;
    if (e.key === 'ArrowLeft') $('#k-prev').click();
    else if (e.key === 'ArrowRight') $('#k-next').click();
    else if (e.key === 'ArrowUp') $('#k-up').click();
    else if (e.key === 'Enter' && (e.target as HTMLElement) === document.body) $('#k-ok').click();
    else return;
    e.preventDefault();
  });
}

let tvVisible = true;
new IntersectionObserver((es) => (tvVisible = es[0]!.isIntersecting)).observe(tvCanvas);
let lastUi = 0;
function loop(now: number): void {
  if (tvVisible) tv.frame(now, tvSig);
  renderThumbs(now);
  if (now - lastUi > 250) {
    lastUi = now;
    updateLive();
  }
  requestAnimationFrame(loop);
}

const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
wire();
$('#n-scenes').textContent = String(D.scenes.length);
$('#n-presets').textContent = String(D.scenes.reduce((a, s) => a + (s.presets?.length ?? 0), 0));
$('#n-variants').textContent = String(D.scenes.reduce((a, s) => a + (1 + (s.presets?.length ?? 0)) * 2 * (s.image === 'none' ? 1 : 2), 0));
buildGrid((idx, preset) => void showScene(idx, preset));
void showScene(D.scenes.findIndex((s) => s.id === 'nebula'), '', 0).then(() => {
  if (reduce) {
    state.music = false;
    refreshAll();
  }
  requestAnimationFrame(loop);
});
