// Santa Vibes authoring — entry point. Wires loaders, renderer, panel and hot reload.

import { collectIncludes, compose, mapErrorLog, type Composed, type MappedError } from './compose';
import { createGL, ShaderError } from './gl';
import { connectHot } from './hot';
import { Renderer, type RenderSettings } from './renderer';
import {
  defaultValues,
  fetchJson,
  fetchText,
  loadProfile,
  loadSceneFrag,
  loadSceneJson,
  presetFromValues,
  presetValues,
  rgbToHex,
  hexToRgb,
  scenePalette,
  validateScene,
  type ParamValues,
  type Preset,
  type Profile,
  type Rgb,
  type SceneJson,
} from './scene';
import { PaletteVariation, rotationPalettes } from './palette';
import { Signals } from './signals';
import {
  button,
  buildParamsPanel,
  checkbox,
  colorInput,
  el,
  group,
  row,
  section,
  select,
  slider,
  type Handle,
  type ParamsPanel,
} from './ui';

// ---------------------------------------------------------------- DOM

const $ = <T extends HTMLElement>(id: string): T => {
  const e = document.getElementById(id);
  if (!e) throw new Error(`missing #${id}`);
  return e as T;
};

const canvas = $<HTMLCanvasElement>('canvas');
const errorsBox = $('errors');
const statsBox = $('stats');
const hotStatus = $('hot-status');
const secScene = $('sec-scene');
const secParams = $('sec-params');
const secSignals = $('sec-signals');
const secRender = $('sec-render');
const secProfile = $('sec-profile');
const secImage = $('sec-image');
const secVary = $('sec-vary');

// Debug hook for scripts/smoke.mjs (state is read-only; applyPreset drives the preset captures).
const debug = {
  compiles: 0,
  lastCompileAt: 0,
  lastChangeAt: 0,
  renderer: '',
  halfFloat: false,
  presets: (): string[] => scene?.presets?.map((p) => p.id) ?? [],
  applyPreset: (id: string): void => applyPreset(id),
};
(window as unknown as { __sv: typeof debug }).__sv = debug;

// ---------------------------------------------------------------- state

const { gl, info } = createGL(canvas);
const renderer = new Renderer(gl, info, canvas);
const signals = new Signals();
const variation = new PaletteVariation();
renderer.paletteVariation = variation;

let header = '';
let sceneIds: string[] = [];
let sceneId = '';
let scene: SceneJson | null = null;
let values: ParamValues = new Map();
let paramsPanel: ParamsPanel | null = null;
let presetId = ''; // '' = scene defaults
let palette: Rgb[] = [];
let profileId = 'tv';
let profile: Profile | null = null;
let profileFromFile: Profile | null = null;
const overrides: { renderScale?: number; fps?: number } = {};
let simWidth = 1920;
let simHeight = 1080;

const RESOLUTIONS = [
  { value: '1280x720', label: '1280 × 720' },
  { value: '1920x1080', label: '1920 × 1080 (box na TV atual)' },
  { value: '3840x2160', label: '3840 × 2160' },
];
const FPS_OPTIONS = [
  { value: '15', label: '15' },
  { value: '24', label: '24' },
  { value: '30', label: '30' },
  { value: '60', label: '60' },
  { value: '0', label: 'sem limite' },
];

// ---------------------------------------------------------------- errors

function showErrors(title: string, errs: MappedError[] | string[]): void {
  errorsBox.replaceChildren(el('div', 'err-title', title));
  for (const e of errs) {
    if (typeof e === 'string') errorsBox.appendChild(el('div', 'err-line', e));
    else {
      const where = e.file ? `${e.file}:${e.line}` : '';
      const line = el('div', 'err-line');
      if (where) line.appendChild(el('span', 'err-where', where));
      line.appendChild(el('span', 'err-msg', e.message));
      errorsBox.appendChild(line);
    }
  }
  errorsBox.hidden = false;
}

function clearErrors(): void {
  errorsBox.hidden = true;
  errorsBox.replaceChildren();
}

function describeError(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

// ---------------------------------------------------------------- shader pipeline

const fetchInclude = (p: string) => fetchText(`/shared/glsl/${p}`);

async function composeScene(id: string, json: SceneJson): Promise<Composed> {
  const [hdr, body] = await Promise.all([fetchText('/shared/glsl/header.glsl'), loadSceneFrag(id)]);
  header = hdr;
  const includes = await collectIncludes(body, fetchInclude);
  return compose(header, json.params, body, `scenes/${id}/scene.frag`, includes);
}

/** Compile the current scene's shader; shows mapped errors, keeps the previous program on failure. */
async function recompile(): Promise<boolean> {
  if (!scene) return false;
  try {
    const composed = await composeScene(sceneId, scene);
    try {
      renderer.setScene(scene, composed);
      clearErrors();
      debug.compiles++;
      debug.lastCompileAt = performance.now();
      return true;
    } catch (err) {
      if (err instanceof ShaderError) showErrors(`Erro de compilação em ${sceneId}`, mapErrorLog(err.log, composed));
      else showErrors('Erro', [describeError(err)]);
      return false;
    }
  } catch (err) {
    showErrors('Erro a carregar a cena', [describeError(err)]);
    return false;
  }
}

async function loadOutputPass(): Promise<void> {
  try {
    renderer.setOutputShader(await fetchText('/shared/glsl/output.frag'));
  } catch (err) {
    if (err instanceof ShaderError) showErrors('Erro em shared/glsl/output.frag', err.log.split('\n').filter(Boolean));
    else showErrors('Erro', [describeError(err)]);
  }
}

// ---------------------------------------------------------------- scene switching

function applySettings(): void {
  const s: RenderSettings = {
    width: simWidth,
    height: simHeight,
    renderScale: overrides.renderScale ?? scene?.renderScale ?? profile?.renderScale ?? 0.5,
    fps: overrides.fps ?? scene?.fps ?? profile?.maxFps ?? 30,
  };
  renderer.setSettings(s);
  renderUi.scale.set(s.renderScale);
  renderUi.fps.set(String(s.fps));
}

async function switchScene(id: string, keepValues = false): Promise<void> {
  let json: SceneJson;
  try {
    json = await loadSceneJson(id);
  } catch (err) {
    showErrors(`Erro a ler scenes/${id}/scene.json`, [describeError(err)]);
    return;
  }
  const problems = validateScene(json, id);
  if (problems.length) {
    showErrors(`scene.json inválido em ${id}`, problems);
    return;
  }
  const changed = id !== sceneId;
  sceneId = id;
  scene = json;
  // On a hot reload of the same scene, re-apply the selected preset so edits to it show up at once.
  const preset = changed ? undefined : findPreset(json, presetId);
  presetId = preset?.id ?? '';
  values = preset ? presetValues(json, preset) : defaultValues(json, keepValues ? values : undefined);
  palette = scenePalette(json, preset);
  renderer.setParamValues(values);
  pushPalette();
  if (changed) {
    delete overrides.renderScale;
    delete overrides.fps;
    renderer.resetTime();
    renderer.clearFeedback();
  }
  applySettings();
  const ok = await recompile();
  if (ok && changed) renderer.clearFeedback();
  buildScenePanel();
  paramsPanel = buildParamsPanel(secParams, json, values, (pid, v) => values.set(pid, v));
  sceneUi.select.set(id);
  location.hash = id;
}

/** Send the current palette to the renderer and rebuild the rotation set (current palette first). */
function pushPalette(): void {
  renderer.setPalette(palette);
  if (!scene) return;
  const others = [scene.defaultPalette ?? [], ...(scene.presets ?? []).map((p) => p.palette ?? [])]
    .filter((p) => p.length === 5)
    .map((p) => p.map(hexToRgb));
  variation.setPalettes(sceneId, rotationPalettes(palette, others), renderer.driftClock);
  varyUi.count.textContent = `${variation.size} paleta(s) nesta cena`;
}

function findPreset(json: SceneJson, id: string): Preset | undefined {
  return id ? json.presets?.find((p) => p.id === id) : undefined;
}

/** Apply a preset (or the scene defaults for '') to values, palette and the panels. */
function applyPreset(id: string): void {
  if (!scene) return;
  const preset = findPreset(scene, id);
  presetId = preset?.id ?? '';
  values = preset ? presetValues(scene, preset) : defaultValues(scene);
  palette = scenePalette(scene, preset);
  renderer.setParamValues(values);
  pushPalette();
  for (const [pid, v] of values) paramsPanel?.set(pid, v);
  buildPresetPanel();
}

async function copyAsPreset(): Promise<void> {
  if (!scene) return;
  const current = findPreset(scene, presetId);
  const p = presetFromValues(scene, current?.id ?? 'new-preset', current?.name ?? 'Novo preset', values, palette);
  const text = JSON.stringify(p, null, 2);
  try {
    await navigator.clipboard.writeText(text);
    sceneUi.copied.textContent = 'copiado';
  } catch {
    sceneUi.copied.textContent = 'sem acesso à área de transferência — ver consola';
  }
  console.log(text);
}

interface SceneEntry {
  id: string;
  name: string;
  style: string;
}

async function refreshSceneList(): Promise<void> {
  const entries = await fetchJson<SceneEntry[]>('/api/scenes');
  sceneIds = entries.map((e) => e.id);
  sceneUi.select.setOptions(
    entries.map((e) => ({ value: e.id, label: `${e.name} (${e.id})`, group: e.style || 'sem estilo' })),
    sceneId,
  );
}

function stepScene(delta: number): void {
  if (!sceneIds.length) return;
  const i = Math.max(0, sceneIds.indexOf(sceneId));
  const next = sceneIds[(i + delta + sceneIds.length) % sceneIds.length];
  if (next) void switchScene(next);
}

// ---------------------------------------------------------------- panels

const sceneUi = (() => {
  section('Cena', secScene);
  const sel = select(secScene, 'Cena', [], '', (v) => void switchScene(v));
  const meta = el('div', 'meta');
  secScene.appendChild(meta);
  const r = row(secScene);
  button(r, 'Repor parâmetros', () => applyPreset(presetId));
  button(r, '◀', () => stepScene(-1));
  button(r, '▶', () => stepScene(1));
  const presets = el('div');
  secScene.appendChild(presets);
  const r2 = row(secScene);
  button(r2, 'Copiar como preset', () => void copyAsPreset());
  const copied = el('span', 'muted');
  r2.appendChild(copied);
  return { select: sel, meta, presets, copied };
})();

function buildScenePanel(): void {
  if (!scene) return;
  sceneUi.meta.replaceChildren();
  sceneUi.meta.appendChild(el('div', 'scene-name', scene.name));
  if (scene.description) sceneUi.meta.appendChild(el('div', 'muted', scene.description));
  const flags: string[] = [];
  if (scene.feedback) flags.push('feedback');
  if (scene.image && scene.image !== 'none') flags.push(`imagem: ${scene.image}`);
  if (scene.tags?.length) flags.push(...scene.tags);
  sceneUi.meta.appendChild(el('div', 'tags', flags.join(' · ')));
  buildPresetPanel();
}

function buildPresetPanel(): void {
  const box = sceneUi.presets;
  box.replaceChildren();
  sceneUi.copied.textContent = '';
  if (!scene) return;
  const list = scene.presets ?? [];
  if (list.length) {
    const opts = [{ value: '', label: '— valores da cena —' }, ...list.map((p) => ({ value: p.id, label: p.name }))];
    select(box, 'Preset', opts, presetId, (v) => applyPreset(v));
  }
  const g = group(box, 'Paleta (uPalette)');
  palette.forEach((c, i) =>
    colorInput(g, String(i), rgbToHex(c), (hex) => {
      palette[i] = hexToRgb(hex);
      pushPalette();
    }),
  );
}

const signalsUi = (() => {
  section('Sinais sintéticos', secSignals);
  const manualHandles: Handle<number>[] = [];
  const setManualEnabled = (auto: boolean) => manualHandles.forEach((h) => h.setDisabled(auto));
  checkbox(secSignals, 'Automático (LFOs)', signals.auto, (v) => {
    signals.auto = v;
    setManualEnabled(v);
  });
  const m = signals.manual;
  const mk = (key: keyof typeof m, label: string) =>
    manualHandles.push(slider(secSignals, { label, min: 0, max: 1, step: 0.01, value: m[key], onInput: (v) => (m[key] = v) }));
  mk('energy', 'energy');
  mk('low', 'low');
  mk('mid', 'mid');
  mk('high', 'high');
  mk('moodEnergy', 'moodEnergy');
  mk('moodBrightness', 'moodBrightness');
  mk('centroid', 'centroid');
  setManualEnabled(signals.auto);

  const beat = el('div', 'group');
  beat.appendChild(el('h3', undefined, 'Beat'));
  secSignals.appendChild(beat);
  checkbox(beat, 'Metrónomo', signals.beatEnabled, (v) => (signals.beatEnabled = v));
  slider(beat, { label: 'BPM', min: 40, max: 200, step: 1, value: signals.bpm, onInput: (v) => (signals.bpm = v) });
  slider(beat, { label: 'Punch (auto)', min: 0, max: 1, step: 0.01, value: signals.punch, onInput: (v) => (signals.punch = v) });
  const r = row(beat);
  button(r, 'Tap', () => signals.tap());
  const live = el('div', 'live mono');
  secSignals.appendChild(live);
  return { live };
})();

const varyUi = (() => {
  section('Drift e variação', secVary);
  const drift = group(secVary, 'Drift de parâmetros');
  slider(drift, {
    label: 'Intensidade',
    min: 0,
    max: 2,
    step: 0.05,
    value: renderer.driftAmount,
    format: (v) => (v === 0 ? 'desligado' : `×${v.toFixed(2)}`),
    onInput: (v) => (renderer.driftAmount = v),
  });
  slider(drift, {
    label: 'Velocidade',
    min: 0,
    max: 4,
    step: 1,
    value: 0,
    format: (v) => `×${2 ** v}`,
    onInput: (v) => (renderer.driftSpeed = 2 ** v),
  });
  drift.appendChild(el('p', 'muted', 'Parâmetros com ~ vagueiam à volta do valor definido; o número a laranja é o valor em uso. A velocidade também acelera a paleta; > ×1 só para pré-visualizar.'));

  const pal = group(secVary, 'Paleta');
  const st = variation.settings;
  checkbox(pal, 'Rodar entre as paletas da cena', st.rotate, (v) => (st.rotate = v));
  slider(pal, { label: 'Mudar a cada', min: 0.5, max: 10, step: 0.5, value: st.holdS / 60, format: (v) => `${v} min`, onInput: (v) => (st.holdS = v * 60) });
  slider(pal, { label: 'Transição', min: 5, max: 60, step: 1, value: st.fadeS, format: (v) => `${v} s`, onInput: (v) => (st.fadeS = v) });
  slider(pal, { label: 'Derivar matiz', min: 0, max: 60, step: 1, value: st.hueDeg, format: (v) => (v === 0 ? 'desligado' : `±${v}°`), onInput: (v) => (st.hueDeg = v) });
  const count = el('div', 'muted');
  const now = el('div', 'live mono');
  pal.append(count, now);
  return { drift, count, now };
})();

const renderUi = (() => {
  section('Render', secRender);
  select(secRender, 'Resolução simulada', RESOLUTIONS, `${simWidth}x${simHeight}`, (v) => {
    const [w, h] = v.split('x').map(Number);
    if (w && h) {
      simWidth = w;
      simHeight = h;
      applySettings();
    }
  });
  const scale = slider(secRender, {
    label: 'renderScale',
    min: 0.1,
    max: 1,
    step: 0.05,
    value: 0.5,
    onInput: (v) => {
      overrides.renderScale = v;
      applySettings();
    },
  });
  const fps = select(secRender, 'Limite de fps', FPS_OPTIONS, '30', (v) => {
    overrides.fps = Number(v);
    applySettings();
  });
  const r = row(secRender);
  const pause = button(r, 'Pausa', () => {
    renderer.paused = !renderer.paused;
    pause.textContent = renderer.paused ? 'Continuar' : 'Pausa';
  });
  button(r, 'Repor tempo', () => renderer.resetTime());
  button(r, 'Limpar feedback', () => renderer.clearFeedback());
  button(r, 'Valores da cena', () => {
    delete overrides.renderScale;
    delete overrides.fps;
    applySettings();
  });
  return { scale, fps, pause };
})();

const profileUi = (() => {
  section('Perfil de saída', secProfile);
  const sel = select(secProfile, 'Perfil', [], profileId, (v) => void switchProfile(v));
  const handles = new Map<string, Handle<number>>();
  const mk = (key: keyof Profile, label: string, min: number, max: number, step: number) => {
    const h = slider(secProfile, {
      label,
      min,
      max,
      step,
      value: 0,
      onInput: (v) => {
        if (profile) (profile as unknown as Record<string, number>)[key] = v;
      },
    });
    handles.set(key, h);
  };
  mk('brightness', 'brightness', 0, 1.5, 0.01);
  mk('contrast', 'contrast', 0.5, 1.5, 0.01);
  mk('saturation', 'saturation', 0, 2, 0.01);
  mk('gamma', 'gamma', 0.5, 2, 0.01);
  mk('blackFloor', 'blackFloor', 0, 0.1, 0.001);
  mk('temperature', 'temperature', -1, 1, 0.01);
  mk('dither', 'dither', 0, 1, 1);
  const r = row(secProfile);
  button(r, 'Repor do ficheiro', () => {
    if (profileFromFile) setProfile({ ...profileFromFile });
  });
  return { select: sel, handles };
})();

function setProfile(p: Profile): void {
  profile = p;
  renderer.setProfile(p);
  for (const [key, h] of profileUi.handles) h.set(Number((p as unknown as Record<string, number>)[key] ?? 0));
  applySettings();
}

async function switchProfile(id: string): Promise<void> {
  try {
    const p = await loadProfile(id);
    profileId = id;
    profileFromFile = { ...p };
    setProfile(p);
  } catch (err) {
    showErrors(`Erro a ler shared/profiles/${id}.json`, [describeError(err)]);
  }
}

async function refreshProfileList(): Promise<void> {
  const ids = await fetchJson<string[]>('/api/profiles');
  profileUi.select.setOptions(
    ids.map((s) => ({ value: s, label: s })),
    profileId,
  );
}

const imageUi = (() => {
  section('Imagem (uImage)', secImage);
  secImage.appendChild(el('p', 'muted', 'Larga uma imagem na página ou escolhe um ficheiro. Só para cenas com "image" ≠ none.'));
  const input = el('input');
  input.type = 'file';
  input.accept = 'image/*';
  input.addEventListener('change', () => {
    const f = input.files?.[0];
    if (f) void loadImageFile(f);
  });
  secImage.appendChild(input);
  const r = row(secImage);
  const status = el('div', 'muted', 'sem imagem');
  button(r, 'Remover imagem', () => {
    renderer.setImage(null, 0, 0);
    status.textContent = 'sem imagem';
  });
  secImage.appendChild(status);
  return { status };
})();

async function loadImageFile(file: File): Promise<void> {
  try {
    const bmp = await createImageBitmap(file);
    renderer.setImage(bmp, bmp.width, bmp.height);
    imageUi.status.textContent = `${file.name} — ${bmp.width}×${bmp.height}`;
  } catch (err) {
    showErrors('Erro a carregar a imagem', [describeError(err)]);
  }
}

window.addEventListener('dragover', (e) => e.preventDefault());
window.addEventListener('drop', (e) => {
  e.preventDefault();
  const f = e.dataTransfer?.files[0];
  if (f && f.type.startsWith('image/')) void loadImageFile(f);
});

// ---------------------------------------------------------------- hot reload

connectHot({
  onStatus(connected) {
    hotStatus.textContent = connected ? '● hot' : '○ desligado';
    hotStatus.classList.toggle('off', !connected);
  },
  onBundle(errors) {
    if (errors.length) showErrors('Erro no bundle TypeScript', errors.map((e) => `${e.file}:${e.line}: ${e.text}`));
    else location.reload();
  },
  onChange(path) {
    debug.lastChangeAt = performance.now();
    if (path === `scenes/${sceneId}/scene.json`) void switchScene(sceneId, true);
    else if (path === `scenes/${sceneId}/scene.frag`) void recompile();
    else if (path.startsWith('scenes/')) void refreshSceneList();
    else if (path === 'shared/glsl/output.frag') void loadOutputPass();
    else if (path.startsWith('shared/glsl/')) void recompile();
    else if (path === `shared/profiles/${profileId}.json`) void switchProfile(profileId);
    else if (path.startsWith('shared/profiles/')) void refreshProfileList();
  },
});

window.addEventListener('hashchange', () => {
  const id = location.hash.slice(1);
  if (id && id !== sceneId && sceneIds.includes(id)) void switchScene(id);
});

// ---------------------------------------------------------------- keyboard

window.addEventListener('keydown', (e) => {
  const t = e.target as HTMLElement | null;
  if (t && (t.tagName === 'INPUT' || t.tagName === 'SELECT' || t.tagName === 'TEXTAREA')) return;
  if (e.key === 'ArrowRight') stepScene(1);
  else if (e.key === 'ArrowLeft') stepScene(-1);
  else if (e.key === ' ') {
    e.preventDefault();
    renderUi.pause.click();
  }
});

// ---------------------------------------------------------------- loop

function updateStats(): void {
  const s = renderer.stats;
  const gpu = renderer.timer.available ? `${s.gpuMs.toFixed(2)} ms GPU` : 'GPU n/d';
  statsBox.textContent =
    `${s.fps.toFixed(1)} fps · ${s.intervalMs.toFixed(1)} ms/frame · ${gpu} · ` +
    `${s.renderWidth}×${s.renderHeight} ${s.format} → ${canvas.width}×${canvas.height} · t=${renderer.time.toFixed(1)}s`;
  const b = signals.bands;
  const m = signals.mood;
  const bt = signals.beat;
  signalsUi.live.textContent =
    `bands ${fmt(b[0])} ${fmt(b[1])} ${fmt(b[2])} ${fmt(b[3])} · mood ${fmt(m[0])} ${fmt(m[1])} ${fmt(m[2])} · ` +
    `beat ${fmt(bt[0])} φ${fmt(bt[1])} ${bt[2]?.toFixed(0) ?? '0'}bpm`;
}

function updatePaletteStatus(): void {
  const st = variation.settings;
  const parts: string[] = [];
  if (st.rotate && variation.size > 1)
    parts.push(variation.mix > 0 ? `paleta ${variation.current + 1} → ${variation.next + 1} (${Math.round(variation.mix * 100)}%)` : `paleta ${variation.current + 1}/${variation.size}`);
  if (st.hueDeg > 0) parts.push(`matiz ±${st.hueDeg}°`);
  varyUi.now.textContent = parts.length ? parts.join(' · ') : 'sem variação';
}

function updateLiveParams(): void {
  if (!scene || !paramsPanel) return;
  for (const p of scene.params) {
    if (!p.drift) continue;
    paramsPanel.live(p.id, renderer.driftAmount > 0 ? renderer.liveValue(p.id) : undefined);
  }
}

function fmt(v: number | undefined): string {
  return (v ?? 0).toFixed(2);
}

let lastStats = 0;
function loop(now: number): void {
  renderer.frame(now, signals);
  if (now - lastStats > 250) {
    lastStats = now;
    updateStats();
    updateLiveParams();
    updatePaletteStatus();
  }
  requestAnimationFrame(loop);
}

window.addEventListener('error', (e) => showErrors('Erro de JavaScript', [`${e.message} (${e.filename}:${e.lineno})`]));
window.addEventListener('unhandledrejection', (e) => showErrors('Erro de JavaScript (promise)', [describeError(e.reason)]));

// ---------------------------------------------------------------- boot

async function boot(): Promise<void> {
  document.title = 'Santa Vibes — authoring';
  debug.renderer = info.renderer;
  debug.halfFloat = info.halfFloat;
  statsBox.textContent = `${info.renderer} · RGBA16F ${info.halfFloat ? 'ok' : 'indisponível (fallback RGBA8)'}`;
  await loadOutputPass();
  await refreshProfileList();
  await switchProfile(profileId);
  await refreshSceneList();
  const wanted = location.hash.slice(1);
  const first = sceneIds.includes(wanted) ? wanted : sceneIds[0];
  if (first) await switchScene(first);
  else showErrors('Sem cenas', ['Não há pastas em scenes/ com scene.json.']);
  requestAnimationFrame(loop);
}

void boot();
