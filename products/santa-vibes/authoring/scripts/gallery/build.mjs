// Builds the screens gallery (the "Santa Vibes Ecrãs" artifact) as one self-contained HTML file:
// embeds scenes/, shared/glsl and shared/profiles and bundles gallery.ts with the authoring sources.
//
//   node scripts/gallery/build.mjs [--out <file>]   (from authoring/; default %TEMP%/santa-vibes-gallery.html)
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import * as esbuild from 'esbuild';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SV = path.resolve(HERE, '..', '..', '..');
const outArg = process.argv.indexOf('--out');
const OUT = outArg >= 0 ? path.resolve(process.argv[outArg + 1]) : path.join(os.tmpdir(), 'santa-vibes-gallery.html');

const files = {};
const walk = (dir, rel) => {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    const r = `${rel}/${e.name}`;
    if (e.isDirectory()) walk(p, r);
    else if (/\.(glsl|frag)$/.test(e.name)) files[r] = fs.readFileSync(p, 'utf8');
  }
};
walk(path.join(SV, 'shared', 'glsl'), 'shared/glsl');

const scenes = [];
for (const id of fs.readdirSync(path.join(SV, 'scenes')).sort()) {
  const dir = path.join(SV, 'scenes', id);
  if (!fs.existsSync(path.join(dir, 'scene.json'))) continue;
  scenes.push(JSON.parse(fs.readFileSync(path.join(dir, 'scene.json'), 'utf8')));
  files[`scenes/${id}/scene.frag`] = fs.readFileSync(path.join(dir, 'scene.frag'), 'utf8');
}

const profiles = {};
for (const f of fs.readdirSync(path.join(SV, 'shared', 'profiles'))) {
  const p = JSON.parse(fs.readFileSync(path.join(SV, 'shared', 'profiles', f), 'utf8'));
  profiles[p.id] = p;
}

const r = await esbuild.build({
  entryPoints: [path.join(HERE, 'gallery.ts')],
  bundle: true,
  format: 'iife',
  target: 'es2022',
  minify: true,
  write: false,
});
const data = `const SV_DATA=${JSON.stringify({ files, scenes, profiles }).replace(/</g, '\u003c')};`;
const js = r.outputFiles[0].text.replace(/<\/script/gi, '<\/script');
const html = fs
  .readFileSync(path.join(HERE, 'page.html'), 'utf8')
  .replace('/*__DATA__*/', () => data)
  .replace('/*__JS__*/', () => js);
fs.writeFileSync(OUT, html);
console.log(`${OUT} · ${(html.length / 1024).toFixed(0)} KB · ${scenes.length} scenes`);
