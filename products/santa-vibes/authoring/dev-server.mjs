// Santa Vibes authoring dev server.
// Zero runtime dependencies: Node built-ins only, plus esbuild (dev) for TypeScript bundling.
//
//   node dev-server.mjs            serve + watch + hot reload (default port 5180)
//   node dev-server.mjs --port N   custom port
//   node dev-server.mjs --build    bundle once and exit (CI / sanity check)
//
// Routes
//   /                 authoring page (index.html)
//   /style.css        page styles
//   /dist/main.js     TypeScript bundle (built by esbuild)
//   /scenes/...       read-only view of <repo>/scenes   (single source of truth)
//   /shared/...       read-only view of <repo>/shared
//   /api/scenes       JSON [{id, name, style}] for folders in scenes/ with a scene.json
//   /api/profiles     JSON list of profile ids (shared/profiles/*.json)
//   POST /api/presets/<scene>  preset lab: append a preset (JSON body) to scenes/<scene>/scene.json
//   /events           Server-Sent Events: {type:"change", path} | {type:"bundle", errors}

import http from 'node:http';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import * as esbuild from 'esbuild';
import { insertPreset } from './lib/scene-presets.mjs';
import { validateScene } from './src/scene.ts';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..');
const SCENES_DIR = path.join(ROOT, 'scenes');
const SHARED_DIR = path.join(ROOT, 'shared');
const DIST_DIR = path.join(HERE, 'dist');

const args = process.argv.slice(2);
const buildOnly = args.includes('--build');
const portArg = args.indexOf('--port');
const PORT = portArg >= 0 ? Number(args[portArg + 1]) : 5180;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.frag': 'text/plain; charset=utf-8',
  '.glsl': 'text/plain; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
};

// ---------------------------------------------------------------- esbuild

const clients = new Set();

function broadcast(event) {
  const data = `data: ${JSON.stringify(event)}\n\n`;
  for (const res of clients) res.write(data);
}

const buildOptions = {
  entryPoints: [path.join(HERE, 'src', 'main.ts')],
  bundle: true,
  format: 'esm',
  target: 'es2022',
  sourcemap: true,
  outfile: path.join(DIST_DIR, 'main.js'),
  logLevel: 'silent',
  plugins: [
    {
      name: 'notify',
      setup(build) {
        build.onEnd((result) => {
          const errors = result.errors.map((e) => ({
            text: e.text,
            file: e.location?.file ?? '',
            line: e.location?.line ?? 0,
          }));
          if (errors.length) {
            console.error(`[esbuild] ${errors.length} error(s):`);
            for (const e of errors) console.error(`  ${e.file}:${e.line}: ${e.text}`);
          } else {
            console.log(`[esbuild] bundle ok (${new Date().toLocaleTimeString()})`);
          }
          broadcast({ type: 'bundle', errors });
        });
      },
    },
  ],
};

if (buildOnly) {
  const result = await esbuild.build({ ...buildOptions, logLevel: 'info' });
  process.exit(result.errors.length ? 1 : 0);
}

const ctx = await esbuild.context(buildOptions);
await ctx.watch();

// ---------------------------------------------------------------- file watching

function watchTree(dir, prefix) {
  const pending = new Map();
  const onChange = (_event, filename) => {
    if (!filename) return;
    const rel = `${prefix}/${String(filename).split(path.sep).join('/')}`;
    // Debounce: editors often emit several events per save.
    clearTimeout(pending.get(rel));
    pending.set(
      rel,
      setTimeout(() => {
        pending.delete(rel);
        console.log(`[watch] ${rel}`);
        broadcast({ type: 'change', path: rel });
      }, 60),
    );
  };
  try {
    fs.watch(dir, { recursive: true }, onChange);
  } catch (err) {
    console.error(`[watch] cannot watch ${dir}: ${err.message}`);
  }
}

watchTree(SCENES_DIR, 'scenes');
watchTree(SHARED_DIR, 'shared');

// ---------------------------------------------------------------- helpers

function send(res, status, body, type = 'text/plain; charset=utf-8') {
  res.writeHead(status, {
    'Content-Type': type,
    'Cache-Control': 'no-store',
    'Content-Length': Buffer.byteLength(body),
  });
  res.end(body);
}

function sendJson(res, obj) {
  send(res, 200, JSON.stringify(obj), MIME['.json']);
}

async function sendFile(res, baseDir, relPath) {
  // Resolve inside baseDir only; reject traversal.
  const abs = path.resolve(baseDir, '.' + path.posix.normalize('/' + relPath));
  if (!abs.startsWith(baseDir + path.sep) && abs !== baseDir) return send(res, 403, 'forbidden');
  try {
    const st = await fsp.stat(abs);
    if (!st.isFile()) return send(res, 404, 'not found');
    const data = await fsp.readFile(abs);
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(abs)] ?? 'application/octet-stream',
      'Cache-Control': 'no-store',
      'Content-Length': data.length,
    });
    res.end(data);
  } catch {
    send(res, 404, 'not found');
  }
}

// Scenes: [{ id, name, style }] — style is, by convention, the first tag in scene.json.
async function listScenes() {
  const scenes = [];
  for (const entry of await fsp.readdir(SCENES_DIR, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    try {
      const json = JSON.parse(await fsp.readFile(path.join(SCENES_DIR, entry.name, 'scene.json'), 'utf8'));
      scenes.push({ id: entry.name, name: json.name ?? entry.name, style: json.tags?.[0] ?? '' });
    } catch {
      /* folder without a readable scene.json: ignore */
    }
  }
  return scenes.sort((a, b) => a.style.localeCompare(b.style) || a.id.localeCompare(b.id));
}

async function listProfiles() {
  const files = await fsp.readdir(path.join(SHARED_DIR, 'profiles'));
  return files.filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)).sort();
}

// Preset lab (decision 16): the only write to scenes/. Appends one preset to the scene's own
// scene.json (no copy), keeping the file's formatting; the result must still validate.
async function savePreset(req, res, sceneId) {
  if (!/^[a-z0-9-]+$/.test(sceneId)) return send(res, 400, 'bad scene id');
  let body = '';
  for await (const chunk of req) body += chunk;
  try {
    const preset = JSON.parse(body);
    const file = path.join(SCENES_DIR, sceneId, 'scene.json');
    const out = insertPreset(await fsp.readFile(file, 'utf8'), preset);
    const problems = validateScene(JSON.parse(out), sceneId);
    if (problems.length) return send(res, 400, problems.join('\n'));
    await fsp.writeFile(file, out);
    console.log(`[lab] preset ${preset.id} added to scenes/${sceneId}/scene.json`);
    return sendJson(res, { ok: true, id: preset.id });
  } catch (e) {
    return send(res, 400, String(e.message ?? e));
  }
}

// ---------------------------------------------------------------- server

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', 'http://localhost');
  const p = decodeURIComponent(url.pathname);

  if (p === '/' || p === '/index.html') return sendFile(res, HERE, 'index.html');
  if (p === '/style.css') return sendFile(res, HERE, 'style.css');
  if (p === '/favicon.ico') return send(res, 204, '');
  if (p.startsWith('/dist/')) return sendFile(res, DIST_DIR, p.slice('/dist/'.length));
  if (p.startsWith('/scenes/')) return sendFile(res, SCENES_DIR, p.slice('/scenes/'.length));
  if (p.startsWith('/shared/')) return sendFile(res, SHARED_DIR, p.slice('/shared/'.length));

  if (p === '/api/scenes') return sendJson(res, await listScenes());
  if (p === '/api/profiles') return sendJson(res, await listProfiles());
  if (req.method === 'POST' && p.startsWith('/api/presets/')) return savePreset(req, res, p.slice('/api/presets/'.length));

  if (p === '/events') {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-store',
      Connection: 'keep-alive',
    });
    res.write('retry: 500\n\n');
    clients.add(res);
    req.on('close', () => clients.delete(res));
    return;
  }

  send(res, 404, 'not found');
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Santa Vibes authoring  http://localhost:${PORT}/`);
  console.log(`  scenes: ${SCENES_DIR}`);
  console.log(`  shared: ${SHARED_DIR}`);
});
