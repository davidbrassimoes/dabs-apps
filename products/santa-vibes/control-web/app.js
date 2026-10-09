// Control page (3.2/3.3): talks to the box's ControlServer (docs/ARQUITETURA.md, Controlo).
// No framework: one state object, one render per change, sliders patched in place while dragged.
'use strict';

const STYLE_ORDER = ['ambient', 'psicadélico clássico', 'psicadélico tech', 'glitch', 'geométrico'];
const IMAGE_SOURCES = [['server', 'A minha pasta'], ['met', 'The Met'], ['nasa', 'NASA']];
const SOURCES = [['auto', 'Automático'], ['pc', 'PC'], ['phone', 'Telemóvel'], ['mic', 'Microfone'], ['none', 'Nenhum']];
const MINUTES = [5, 10, 20, 30, 60];

const TABS = ['scenes', 'now', 'mine', 'sources'];
const ui = { tab: TABS.includes(location.hash.slice(1)) ? location.hash.slice(1) : 'now', styleFilter: '', saving: false, exportJson: null, confirmDel: '', toast: '', toastErr: false, busyUntil: 0 };
let scenes = [];
let st = null; // state from the box
let presets = [];
const $screen = document.getElementById('screen');
const $conn = document.getElementById('conn');

// ------------------------------------------------------------------ API

async function api(method, path, body) {
  const r = await fetch(path, { method, headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined });
  if (!r.ok) throw new Error((await r.text()) || `${r.status}`);
  setConn(true);
  return r.status === 204 ? null : r.json();
}
function setConn(ok) {
  $conn.classList.toggle('off', !ok);
  $conn.lastChild.textContent = ok ? 'TV ligada' : 'sem ligação à TV';
}
async function act(fn, okText) {
  try {
    await fn();
    ui.toast = okText || '';
    ui.toastErr = false;
  } catch (e) {
    ui.toast = e instanceof TypeError ? 'Sem ligação à TV. Estás na mesma rede?' : `Não deu: ${e.message}`;
    ui.toastErr = true;
    if (e instanceof TypeError) setConn(false);
  }
  render();
}
const patch = (p) => api('PATCH', '/api/state', p).then((s) => { st = s; });
const loadPresets = () => api('GET', '/api/presets').then((p) => { presets = p; });

// Sliders: send at most every 120 ms while dragging, and the last value always.
let pending = {};
let timer = 0;
function queueValue(kind, id, v) {
  ui.busyUntil = Date.now() + 1500;
  if (kind === 'param') (pending.values ||= {})[id] = v; else pending[id] = v;
  if (!timer) timer = setTimeout(flush, 120);
}
function flush() {
  timer = 0;
  const p = pending;
  pending = {};
  if (Object.keys(p).length) patch(p).catch(() => setConn(false));
}

// ------------------------------------------------------------------ helpers

const esc = (t) => String(t ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
const scene = (id) => scenes.find((s) => s.id === id) || scenes[0];
const pal = (s, pid) => (s.presets || []).find((p) => p.id === pid)?.palette || s.defaultPalette || ['#222', '#333', '#444', '#555', '#666'];
const sw = (p) => `<span class="sw">${(p || []).map((c) => `<i style="background:${esc(c)}"></i>`).join('')}</span>`;
const thumb = (s) => { const p = pal(s, ''); return `linear-gradient(135deg, ${p[0]}, ${p[2]} 45%, ${p[3]} 75%, ${p[4]})`; };
const fmt = (d, v) => (d.type === 'int' ? String(Math.round(v)) : Number(v).toFixed(2));
const styleRank = (x) => { const i = STYLE_ORDER.indexOf(x); return i < 0 ? 99 : i; };
const imageNames = (l) => l.map((v) => IMAGE_SOURCES.find(([k]) => k === v)?.[1] || v).join(', ') || 'nenhuma';
const toast = () => (ui.toast ? `<div class="toast ${ui.toastErr ? 'err' : ''}">${esc(ui.toast)}</div>` : '');

// ------------------------------------------------------------------ views

const views = {
  scenes() {
    const styles = [...new Set(scenes.map((s) => s.tags?.[0] || 'outro'))].sort((a, b) => styleRank(a) - styleRank(b));
    const list = scenes.filter((s) => !ui.styleFilter || s.tags?.[0] === ui.styleFilter);
    return `<div class="h">Cenas</div>
      <div class="chips"><button class="chip ${ui.styleFilter ? '' : 'on'}" data-style="">Todas</button>${styles.map((x) => `<button class="chip ${ui.styleFilter === x ? 'on' : ''}" data-style="${esc(x)}">${esc(x)}</button>`).join('')}</div>
      <div class="grp">${list.map((s) => `<button class="scene ${s.id === st.scene ? 'cur' : ''}" data-scene="${esc(s.id)}"><span class="th" style="background:${thumb(s)}"></span><span><span class="nm">${esc(s.name)}</span><br><span class="mt">${esc(s.tags?.[0] || '')} · ${(s.presets || []).length} presets${s.image && s.image !== 'none' ? ' · imagem' : ''}</span></span><span class="play">${s.id === st.scene ? 'na TV' : '▶'}</span></button>`).join('')}</div>`;
  },
  now() {
    const s = scene(st.scene);
    const groups = {};
    for (const d of s.params) if (d.id !== 'react') (groups[d.group || 'Forma'] ||= []).push(d);
    const control = (d) => {
      const v = st.values[d.id] ?? d.default;
      if (d.type === 'color') return `<div class="kv"><span>${esc(d.label)}</span><span class="sw"><i style="background:${esc(v)}"></i></span></div>`;
      if (d.type === 'bool') return `<div class="kv"><span>${esc(d.label)}</span><button class="chip ${v ? 'on' : ''}" data-bool="${esc(d.id)}">${v ? 'sim' : 'não'}</button></div>`;
      const step = d.step || (d.type === 'int' ? 1 : (d.max - d.min) / 100);
      return `<label class="sl"><span class="l">${esc(d.label)}<em id="v-${esc(d.id)}">${fmt(d, v)}</em></span><input type="range" data-param="${esc(d.id)}" min="${d.min}" max="${d.max}" step="${step}" value="${v}"></label>`;
    };
    const i = scenes.indexOf(s);
    const save = ui.saving
      ? `<div class="sheet"><div class="field"><label class="lbl" for="pname">Nome do preset</label><input type="text" id="pname" placeholder="Ex.: Serão de inverno" maxlength="40"></div>
        <p class="sub">Guarda na TV: ${esc(s.name)}, estes valores, a paleta, a versão ${st.look === 'reactive' ? 'reativa' : 'atual'} e as imagens (${esc(imageNames(st.images))}).</p>
        <div class="row2"><button class="btn alt" data-act="cancel-save">Cancelar</button><button class="btn" data-act="do-save">Guardar</button></div></div>`
      : `<div class="row2"><button class="btn alt" data-act="reset">Repor</button><button class="btn" data-act="save">Guardar preset</button></div>`;
    return `<div class="now"><button class="arrow" data-step="-1" aria-label="Cena anterior">◀</button><div class="t"><div class="h">${esc(s.name)}</div><div class="sub">${i + 1}/${scenes.length} · na TV agora</div></div><button class="arrow" data-step="1" aria-label="Cena seguinte">▶</button></div>
      <div class="seg" role="group" aria-label="Versão"><button class="${st.look === 'reactive' ? '' : 'on'}" data-look="current">Atual</button><button class="${st.look === 'reactive' ? 'on' : ''}" data-look="reactive">Reativa</button></div>
      <div class="grp"><span class="lbl">Presets do autor</span><div class="chips"><button class="pchip ${st.preset ? '' : 'on'}" data-preset="">${sw(pal(s, ''))}Defeito</button>${(s.presets || []).map((p) => `<button class="pchip ${st.preset === p.id ? 'on' : ''}" data-preset="${esc(p.id)}">${sw(p.palette || pal(s, ''))}${esc(p.name)}</button>`).join('')}</div></div>
      ${Object.entries(groups).map(([g, ds]) => `<div class="grp"><span class="lbl">${esc(g)}</span>${ds.map(control).join('')}</div>`).join('')}
      ${toast()}${save}`;
  },
  mine() {
    const exp = ui.exportJson
      ? `<div class="sheet"><span class="lbl">Exportar</span><pre id="json">${esc(ui.exportJson)}</pre><p class="sub">Cola-o numa conversa com o Claude e ele junta-o aos presets da cena num commit.</p>
        <div class="row2"><button class="btn alt" data-act="close-export">Fechar</button><button class="btn" data-act="copy">Copiar</button></div></div>`
      : '';
    const list = presets.length
      ? presets.map((m) => `<div class="mine"><b>${esc(m.name)}</b><div class="sub">${sw(m.palette)} ${esc(scene(m.scene)?.name || m.scene)} · ${m.look === 'reactive' ? 'reativa' : 'atual'} · imagens: ${esc(imageNames(m.images))}</div>
        <div class="acts">${ui.confirmDel === m.id
          ? `<span class="sub">Apagar "${esc(m.name)}"?</span><button class="danger" data-del-yes="${esc(m.id)}">Apagar</button><button data-act="del-no">Não</button>`
          : `<button data-apply="${esc(m.id)}">Aplicar</button><button data-export="${esc(m.id)}">Exportar</button><button class="danger" data-del="${esc(m.id)}">Apagar</button>`}</div></div>`).join('')
      : '<p class="sub">Ainda não tens presets. Em "Agora", afina uma cena e carrega em "Guardar preset".</p>';
    return `<div class="h">Os meus presets</div><p class="sub">Guardados na TV. Funcionam sem internet.</p>${exp}${toast()}
      <div class="grp">${list}</div>
      <div class="grp"><span class="lbl">Rotação automática</span>
        <div class="kv"><span>${presets.length >= 2 ? 'Rodar pelos meus presets' : 'Rodar pelas cenas'}</span><button class="chip ${st.rotation ? 'on' : ''}" data-rotation="${!st.rotation}">${st.rotation ? 'ligada' : 'desligada'}</button></div>
        <div class="chips">${MINUTES.map((m) => `<button class="chip ${st.rotationMin === m ? 'on' : ''}" data-minutes="${m}">${m} min</button>`).join('')}</div>
        <p class="sub">Com menos de dois presets teus, roda por todas as cenas.</p></div>`;
  },
  sources() {
    const live = st.live || {};
    const one = (key, opts) => `<div class="chips">${opts.map(([v, l]) => `<button class="chip ${st[key] === v ? 'on' : ''}" data-set="${key}" data-v="${v}">${l}</button>`).join('')}</div>`;
    const range = (key, label, min, max, step, value) => `<label class="sl"><span class="l">${label}<em id="v-${key}">${Number(value).toFixed(2)}</em></span><input type="range" data-state="${key}" min="${min}" max="${max}" step="${step}" value="${value}"></label>`;
    const signal = live.source && live.source !== 'none'
      ? `<span class="dot"></span>${esc(live.source)} · ${Math.round(live.packetsPerS)} pkt/s · ${live.bpm > 0 ? live.bpm.toFixed(1) + ' bpm' : 'sem tempo'}`
      : '<span class="dot" style="background:var(--mute)"></span>sem sinal: movimento sintético';
    const pd = st.profileDefaults || {};
    return `<div class="h">Fontes</div>
      <div class="grp"><span class="lbl">Sinal</span>${one('source', SOURCES)}<div class="status">${signal}</div>${range('reactivity', 'Reatividade', 0, 1, 0.05, st.reactivity)}</div>
      <div class="grp"><span class="lbl">Imagens</span><div class="chips">${IMAGE_SOURCES.map(([v, l]) => `<button class="chip ${st.images.includes(v) ? 'on' : ''}" data-image="${v}">${l}</button>`).join('')}</div>
        <p class="sub">${st.images.length ? `Agora: ${esc(live.image || '—')} · ${live.imagesCached || 0} em cache` : 'Sem imagens: as cenas usam as suas paletas.'}</p></div>
      <div class="grp"><span class="lbl">Saída</span>${one('profile', [['tv', 'TV'], ['projector', 'Projetor']])}
        ${range('brightness', 'Brilho', 0.2, 1, 0.01, st.brightness ?? pd.brightness ?? 0.85)}${range('temperature', 'Temperatura (frio → quente)', -1, 1, 0.05, st.temperature ?? pd.temperature ?? 0)}</div>
      <div class="grp"><span class="lbl">Paleta</span><div class="kv"><span>Rodar entre as paletas da cena</span><button class="chip ${st.paletteRotate ? 'on' : ''}" data-palrot="${!st.paletteRotate}">${st.paletteRotate ? 'ligada' : 'desligada'}</button></div></div>`;
  },
};

function render() {
  if (!st || !scenes.length) return;
  $screen.innerHTML = views[ui.tab]();
  if (location.hash !== '#' + ui.tab) history.replaceState(null, '', '#' + ui.tab);
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('on', t.dataset.tab === ui.tab));
}

// ------------------------------------------------------------------ events

document.getElementById('tabs').addEventListener('click', (e) => {
  const t = e.target.closest('.tab');
  if (!t) return;
  Object.assign(ui, { tab: t.dataset.tab, toast: '', exportJson: null, saving: false, confirmDel: '' });
  render();
  $screen.scrollTop = 0;
});

$screen.addEventListener('input', (e) => {
  const el = e.target;
  if (el.dataset.param) {
    const d = scene(st.scene).params.find((x) => x.id === el.dataset.param);
    st.values[d.id] = Number(el.value);
    document.getElementById('v-' + d.id).textContent = fmt(d, el.value);
    queueValue('param', d.id, Number(el.value));
  } else if (el.dataset.state) {
    st[el.dataset.state] = Number(el.value);
    document.getElementById('v-' + el.dataset.state).textContent = Number(el.value).toFixed(2);
    queueValue('state', el.dataset.state, Number(el.value));
  }
});

$screen.addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (!b) return;
  const d = b.dataset;
  ui.toast = '';
  if (d.scene) return act(() => patch({ scene: d.scene }).then(() => { ui.tab = 'now'; }));
  if (d.style !== undefined) { ui.styleFilter = d.style; return render(); }
  if (d.step) return act(() => api('POST', '/api/step', { delta: Number(d.step) }).then((s) => { st = s; }));
  if (d.look) return act(() => patch({ look: d.look }));
  if (d.preset !== undefined) return act(() => patch({ preset: d.preset }));
  if (d.bool) return act(() => patch({ values: { [d.bool]: !st.values[d.bool] } }));
  if (d.set) return act(() => patch({ [d.set]: d.v }));
  if (d.image) return act(() => patch({ images: st.images.includes(d.image) ? st.images.filter((x) => x !== d.image) : [...st.images, d.image] }));
  if (d.palrot) return act(() => patch({ paletteRotate: d.palrot === 'true' }));
  if (d.rotation) return act(() => patch({ rotation: d.rotation === 'true' }));
  if (d.minutes) return act(() => patch({ rotationMin: Number(d.minutes) }));
  if (d.apply) return act(() => api('POST', `/api/presets/${encodeURIComponent(d.apply)}/apply`).then((s) => { st = s; ui.tab = 'now'; }), 'Na TV.');
  if (d.export) return act(() => api('GET', `/api/presets/${encodeURIComponent(d.export)}/export`).then((o) => { ui.exportJson = JSON.stringify(o, null, 2); }));
  if (d.del) { ui.confirmDel = d.del; return render(); }
  if (d.delYes) return act(() => api('DELETE', `/api/presets/${encodeURIComponent(d.delYes)}`).then(loadPresets).then(() => { ui.confirmDel = ''; }), 'Apagado.');
  switch (d.act) {
    case 'reset': return act(() => patch({ preset: st.preset }), 'Valores repostos.');
    case 'save': ui.saving = true; render(); document.getElementById('pname')?.focus(); return;
    case 'cancel-save': ui.saving = false; return render();
    case 'do-save': {
      const input = document.getElementById('pname');
      const name = input.value.trim();
      if (!name) { input.placeholder = 'Dá-lhe um nome primeiro'; input.focus(); return; }
      return act(() => api('POST', '/api/presets', { name }).then(loadPresets).then(() => { ui.saving = false; }), `"${name}" guardado em Os meus.`);
    }
    case 'close-export': ui.exportJson = null; return render();
    case 'copy': {
      const done = () => { ui.toast = 'Copiado.'; ui.toastErr = false; render(); };
      const select = () => { const r = document.createRange(); r.selectNodeContents(document.getElementById('json')); getSelection().removeAllRanges(); getSelection().addRange(r); };
      if (navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(ui.exportJson).then(done, select); else select();
      return;
    }
    case 'del-no': ui.confirmDel = ''; return render();
  }
});

// ------------------------------------------------------------------ start and refresh

async function refresh() {
  if (Date.now() < ui.busyUntil || ui.saving || document.activeElement?.tagName === 'INPUT') return;
  try {
    st = await api('GET', '/api/state');
    render();
  } catch {
    setConn(false);
  }
}

(async () => {
  try {
    [scenes, st] = await Promise.all([api('GET', '/api/scenes'), api('GET', '/api/state')]);
    await loadPresets();
    render();
  } catch {
    setConn(false);
    $screen.innerHTML = '<p class="sub">Não consegui falar com a TV. Abre a app Santa Vibes na TV e volta a carregar esta página.</p>';
  }
  setInterval(refresh, 3000);
})();
