// Writes test/fixtures/palette-cases.json from the reference implementation (src/palette.ts).
//   node scripts/palette-cases.mjs
//
// The fixture is the language-neutral contract for palette variation: the tv-app (Kotlin)
// test suite loads it and must match every colour within 1e-6. Regenerate only when the
// algorithm changes on purpose (and say so in the commit).

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PaletteVariation } from '../src/palette.ts';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(HERE, '..', 'test', 'fixtures', 'palette-cases.json');

const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16) / 255);
const palettes = [
  ['#2b0f1e', '#6b1d2f', '#b23a3a', '#e07b39', '#f3c27a'],
  ['#141a33', '#26346a', '#3d5aa0', '#7a8fc8', '#c9d3ee'],
  ['#1a2318', '#3a4a2a', '#6f7d3a', '#b8a65a', '#ece0b0'],
];

const settings = [
  { name: 'passthrough', rotate: false, holdS: 180, fadeS: 20, hueDeg: 0 },
  { name: 'rotate', rotate: true, holdS: 100, fadeS: 20, hueDeg: 0 },
  { name: 'hue', rotate: false, holdS: 180, fadeS: 20, hueDeg: 40 },
  { name: 'rotate + hue', rotate: true, holdS: 30, fadeS: 10, hueDeg: 25 },
];
const epoch = 12.5;
const times = [12.5, 50, 117.25, 125, 160, 333.3, 3600, 86400];

const cases = settings.map((s) => {
  const v = new PaletteVariation();
  const { name, ...st } = s;
  Object.assign(v.settings, st);
  v.setPalettes('color-field', palettes.map((p) => p.map(hex)), epoch);
  const out = new Float32Array(15);
  const samples = times.map((t) => {
    v.write(out, t);
    return { t, current: v.current, next: v.next, mix: v.mix, palette: [...out] };
  });
  return { name, settings: st, samples };
});

fs.writeFileSync(OUT, JSON.stringify({ scene: 'color-field', epoch, palettes, cases }, null, 1) + '\n');
console.log(`wrote ${path.relative(process.cwd(), OUT)}: ${cases.length} cases x ${times.length} times`);
