// Headless smoke test for the authoring page, driven over the Chrome DevTools Protocol.
// Zero dependencies (Node >= 22 has a global WebSocket). Usage:
//
//   node scripts/smoke.mjs [--browser <path-to-chrome-or-edge>] [--url http://localhost:5180/] [--out <dir>] [--gpu]
//
//   --gpu          use the real GPU through ANGLE/D3D11 instead of SwiftShader (fps and GPU ms become meaningful)
//   --quick        skip the hot-reload and error-overlay checks (just load, run, screenshot)
//   --only a,b     restrict to these scene ids
//   --settle N     seconds to let each scene run before the screenshot (default 4)
//
// For every scene listed by /api/scenes: loads it, waits, collects console output and
// the in-page error overlay, reads the stats line and saves a screenshot <out>/<scene>.png.
// Exit code 1 when any scene shows an error or produces no frames.

import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));

const args = process.argv.slice(2);
const opt = (name, def) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : def;
};
const URL_BASE = opt('--url', 'http://localhost:5180/');
const OUT = opt('--out', path.join(os.tmpdir(), 'santa-vibes-smoke'));
const CANDIDATES = [
  opt('--browser', ''),
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  ...fs.existsSync('C:/Program Files (x86)/Microsoft/EdgeCore')
    ? fs.readdirSync('C:/Program Files (x86)/Microsoft/EdgeCore').map((v) => `C:/Program Files (x86)/Microsoft/EdgeCore/${v}/msedge.exe`)
    : [],
].filter((p) => p && fs.existsSync(p));
const BROWSER = CANDIDATES[0];
const USE_GPU = args.includes('--gpu');
const QUICK = args.includes('--quick');
const ONLY = opt('--only', '').split(',').filter(Boolean);
const SETTLE = Number(opt('--settle', '4')) * 1000;
if (!BROWSER) {
  console.error('No Chromium browser found; pass --browser <path>.');
  process.exit(2);
}
fs.mkdirSync(OUT, { recursive: true });
const PORT = 9333;
const profileDir = fs.mkdtempSync(path.join(os.tmpdir(), 'sv-smoke-'));

const proc = spawn(
  BROWSER,
  [
    '--headless=new',
    '--no-first-run',
    '--disable-extensions',
    ...(USE_GPU ? ['--use-angle=d3d11', '--enable-gpu-rasterization'] : ['--use-angle=swiftshader', '--enable-unsafe-swiftshader']),
    '--ignore-gpu-blocklist',
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${profileDir}`,
    '--window-size=1600,900',
    'about:blank',
  ],
  { stdio: 'ignore' },
);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function waitForBrowser() {
  for (let i = 0; i < 100; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${PORT}/json/version`);
      if (res.ok) return (await res.json()).webSocketDebuggerUrl;
    } catch {
      /* not yet */
    }
    await sleep(100);
  }
  throw new Error('browser did not expose DevTools');
}

class Cdp {
  constructor(ws) {
    this.ws = ws;
    this.id = 0;
    this.pending = new Map();
    this.listeners = [];
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data);
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result);
      } else if (msg.method) {
        for (const l of this.listeners) l(msg);
      }
    });
  }
  send(method, params = {}, sessionId) {
    const id = ++this.id;
    this.ws.send(JSON.stringify({ id, method, params, sessionId }));
    return new Promise((resolve, reject) => this.pending.set(id, { resolve, reject }));
  }
  on(fn) {
    this.listeners.push(fn);
  }
}

function fmtRemote(v) {
  return v.value !== undefined ? String(v.value) : v.description ?? v.type;
}

let failed = false;
try {
  const wsUrl = await waitForBrowser();
  const ws = new WebSocket(wsUrl);
  await new Promise((r, j) => {
    ws.addEventListener('open', r);
    ws.addEventListener('error', j);
  });
  const cdp = new Cdp(ws);
  const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
  const logs = [];
  cdp.on((msg) => {
    if (msg.sessionId !== sessionId) return;
    if (msg.method === 'Runtime.consoleAPICalled')
      logs.push(`[console.${msg.params.type}] ${msg.params.args.map(fmtRemote).join(' ')}`);
    else if (msg.method === 'Runtime.exceptionThrown')
      logs.push(`[exception] ${msg.params.exceptionDetails.exception?.description ?? msg.params.exceptionDetails.text}`);
    else if (msg.method === 'Log.entryAdded') logs.push(`[${msg.params.entry.level}] ${msg.params.entry.text}`);
  });
  await cdp.send('Runtime.enable', {}, sessionId);
  await cdp.send('Log.enable', {}, sessionId);
  await cdp.send('Page.enable', {}, sessionId);

  const evaluate = async (expression) => {
    const r = await cdp.send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true }, sessionId);
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description ?? 'evaluate failed');
    return r.result.value;
  };

  let scenes = await (await fetch(new URL('/api/scenes', URL_BASE))).json();
  if (ONLY.length) scenes = scenes.filter((s) => ONLY.includes(s));
  console.log(`browser: ${BROWSER} (${USE_GPU ? 'GPU via ANGLE/D3D11' : 'SwiftShader, software'})`);
  console.log(`scenes: ${scenes.join(', ')}`);

  for (const id of scenes) {
    logs.length = 0;
    await cdp.send('Page.navigate', { url: `${URL_BASE}#${id}` }, sessionId);
    // Give boot + shader compile + a few frames time to happen (SwiftShader is slow).
    let state = null;
    for (let i = 0; i < 40; i++) {
      await sleep(500);
      state = await evaluate(`({
        name: document.querySelector('.scene-name')?.textContent ?? '',
        stats: document.getElementById('stats')?.textContent ?? '',
        errors: document.getElementById('errors')?.hidden ? '' : document.getElementById('errors')?.innerText ?? '',
        params: document.querySelectorAll('#sec-params .ctl').length,
      })`);
      if (state.errors || (state.name && /fps/.test(state.stats) && !/^0\\.0 fps/.test(state.stats))) break;
    }
    // Hot reload latency: append a comment to scene.frag, wait for a recompile, restore the file.
    let hotMs = null;
    let errorText = '';
    let errorMapped = true;
    let stillRunning = true;
    const fragPath = path.resolve(HERE, '..', '..', 'scenes', id, 'scene.frag');
    const original = fs.readFileSync(fragPath);
    if (!QUICK) try {
      const before = await evaluate('window.__sv.compiles');
      const t0 = performance.now();
      fs.writeFileSync(fragPath, Buffer.concat([original, Buffer.from('// smoke-test touch\n')]));
      for (let i = 0; i < 100; i++) {
        await sleep(50);
        if ((await evaluate('window.__sv.compiles')) > before) {
          hotMs = Math.round(performance.now() - t0);
          break;
        }
      }
    } finally {
      fs.writeFileSync(fragPath, original);
      await sleep(300); // let the restore recompile settle
    }

    // Error overlay: inject a syntax error, expect the overlay to name scene.frag with a line number.
    if (!QUICK) try {
      fs.writeFileSync(fragPath, Buffer.concat([original, Buffer.from('void broken( {\n')]));
      for (let i = 0; i < 100; i++) {
        await sleep(50);
        errorText = await evaluate(
          "document.getElementById('errors').hidden ? '' : document.getElementById('errors').innerText",
        );
        if (errorText) break;
      }
      if (errorText) {
        const errShot = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId);
        fs.writeFileSync(path.join(OUT, `${id}-error.png`), Buffer.from(errShot.data, 'base64'));
      }
    } finally {
      fs.writeFileSync(fragPath, original);
      await sleep(600);
    }
    if (!QUICK) {
      errorMapped = new RegExp(`scenes/${id}/scene.frag:\\d+`).test(errorText);
      stillRunning = await evaluate("document.getElementById('errors').hidden && window.__sv.compiles > 0");
    }
    // Let the scene run so the screenshot and stats are representative.
    await sleep(SETTLE);
    state = await evaluate(`({
      name: document.querySelector('.scene-name')?.textContent ?? '',
      stats: document.getElementById('stats')?.textContent ?? '',
      errors: document.getElementById('errors')?.hidden ? '' : document.getElementById('errors')?.innerText ?? '',
      params: document.querySelectorAll('#sec-params .ctl').length,
    })`);
    const shot = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId);
    const file = path.join(OUT, `${id}.png`);
    fs.writeFileSync(file, Buffer.from(shot.data, 'base64'));
    const ok = !!state && !state.errors && state.name !== '' && /fps/.test(state.stats);
    if (!ok) failed = true;
    console.log(`\n== ${id}: ${ok ? 'OK' : 'FAIL'}`);
    console.log(`   name: ${state?.name}  params: ${state?.params}`);
    if (id === scenes[0]) console.log(`   renderer: ${await evaluate('window.__sv.renderer')}`);
    console.log(`   stats: ${state?.stats}`);
    if (!QUICK) {
      console.log(`   hot reload (.frag -> recompiled): ${hotMs === null ? 'TIMEOUT' : hotMs + ' ms'}`);
      if (hotMs === null) failed = true;
      console.log(`   error overlay mapped to scene.frag:line: ${errorMapped ? 'yes' : 'NO'}  recovered after fix: ${stillRunning ? 'yes' : 'NO'}`);
      if (errorText) console.log(`   overlay: ${errorText.split('\n').slice(0, 2).join(' | ')}`);
      if (!errorMapped || !stillRunning) failed = true;
    }
    if (state?.errors) console.log(`   errors: ${state.errors.replace(/\n/g, '\n           ')}`);
    for (const l of logs) console.log(`   ${l}`);
    console.log(`   screenshot: ${file}`);
  }
  ws.close();
} catch (err) {
  failed = true;
  console.error(err);
} finally {
  // Wait for the browser to exit before removing its profile; Windows keeps the files locked until then.
  const exited = new Promise((r) => proc.once('exit', r));
  proc.kill();
  await Promise.race([exited, sleep(5000)]);
  try {
    fs.rmSync(profileDir, { recursive: true, force: true, maxRetries: 10, retryDelay: 200 });
  } catch (err) {
    console.warn(`could not remove temp profile ${profileDir}: ${err.message}`);
  }
}
process.exit(failed ? 1 : 0);
