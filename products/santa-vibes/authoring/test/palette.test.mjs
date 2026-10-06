// Tests for host-side palette variation (src/palette.ts).
//   npm test

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PaletteVariation, oklabToSrgb, rotationPalettes, srgbToOklab } from '../src/palette.ts';

const lab = (r, g, b) => {
  const o = new Float64Array(3);
  srgbToOklab(r, g, b, o, 0);
  return [...o];
};

test('OKLab reference values: white, black, sRGB red', () => {
  const w = lab(1, 1, 1);
  assert.ok(Math.abs(w[0] - 1) < 1e-4 && Math.abs(w[1]) < 1e-4 && Math.abs(w[2]) < 1e-4);
  assert.deepEqual(lab(0, 0, 0).map((v) => Math.abs(v) < 1e-9), [true, true, true]);
  const r = lab(1, 0, 0); // published: L 0.6279, a 0.2249, b 0.1258
  assert.ok(Math.abs(r[0] - 0.6279) < 1e-3 && Math.abs(r[1] - 0.2249) < 1e-3 && Math.abs(r[2] - 0.1258) < 1e-3);
});

test('sRGB -> OKLab -> sRGB round-trips', () => {
  const out = new Float64Array(3);
  for (const c of [[0.1, 0.5, 0.9], [0.95, 0.2, 0.4], [0.02, 0.03, 0.05], [0.5, 0.5, 0.5]]) {
    const l = lab(...c);
    oklabToSrgb(l[0], l[1], l[2], out, 0);
    for (let i = 0; i < 3; i++) assert.ok(Math.abs(out[i] - c[i]) < 1e-6, `${c} -> ${[...out]}`);
  }
});

const pal = (hex) => hex.map((h) => [parseInt(h.slice(1, 3), 16) / 255, parseInt(h.slice(3, 5), 16) / 255, parseInt(h.slice(5, 7), 16) / 255]);
const A = pal(['#102030', '#204060', '#3070a0', '#80c0e0', '#f0f8ff']);
const B = pal(['#301010', '#602020', '#b04030', '#f09060', '#fff0d0']);

test('without variation the palette passes through unchanged', () => {
  const v = new PaletteVariation();
  v.setPalettes('s', [A, B], 0);
  const out = new Float32Array(15);
  v.write(out, 1234);
  A.flat().forEach((c, i) => assert.ok(Math.abs(out[i] - c) < 1e-5));
});

test('rotation holds, then crossfades, then holds the next palette', () => {
  const v = new PaletteVariation();
  Object.assign(v.settings, { rotate: true, holdS: 100, fadeS: 20 });
  v.setPalettes('s', [A, B], 50); // epoch at t = 50
  const out = new Float32Array(15);
  v.write(out, 50 + 99);
  assert.equal(v.current, 0);
  assert.equal(v.mix, 0);
  v.write(out, 50 + 110);
  assert.ok(v.mix > 0.4 && v.mix < 0.6);
  v.write(out, 50 + 121); // second cycle: holding B
  assert.equal(v.current, 1);
  assert.equal(v.mix, 0);
  B.flat().forEach((c, i) => assert.ok(Math.abs(out[i] - c) < 1e-5));
  v.write(out, 50 + 240 + 1); // wraps back to A
  assert.equal(v.current, 0);
});

test('hue drift keeps OKLab lightness and chroma', () => {
  const v = new PaletteVariation();
  v.settings.hueDeg = 40;
  v.setPalettes('s', [B], 0);
  const out = new Float32Array(15);
  let moved = false;
  for (const t of [10, 40, 75]) {
    v.write(out, t);
    for (let k = 1; k < 4; k++) {
      // skip the near-black and near-white ends, where sRGB clamping is allowed to bite
      const before = lab(...B[k]);
      const after = lab(out[k * 3], out[k * 3 + 1], out[k * 3 + 2]);
      assert.ok(Math.abs(before[0] - after[0]) < 0.02, `L ${before[0]} -> ${after[0]}`);
      if (Math.abs(before[1] - after[1]) > 0.01) moved = true;
    }
  }
  assert.ok(moved, 'hue should actually move');
});

test('rotationPalettes puts the current palette first and drops duplicates', () => {
  const list = rotationPalettes(B, [A, B, A, []]);
  assert.equal(list.length, 2);
  assert.equal(list[0], B);
});

// test/fixtures/palette-cases.json (scripts/palette-cases.mjs) is the shared contract with
// the tv-app; the reference must still reproduce it.
const fixture = JSON.parse(fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'fixtures', 'palette-cases.json'), 'utf8'));
for (const c of fixture.cases) {
  test(`fixture palette: ${c.name}`, () => {
    const v = new PaletteVariation();
    Object.assign(v.settings, c.settings);
    v.setPalettes(fixture.scene, fixture.palettes.map(pal), fixture.epoch);
    const out = new Float32Array(15);
    for (const s of c.samples) {
      v.write(out, s.t);
      assert.equal(v.current, s.current, `current at ${s.t}`);
      assert.equal(v.next, s.next, `next at ${s.t}`);
      assert.ok(Math.abs(v.mix - s.mix) < 1e-12, `mix at ${s.t}`);
      s.palette.forEach((x, i) => assert.ok(Math.abs(out[i] - x) < 1e-6, `palette[${i}] at ${s.t}`));
    }
  });
}
