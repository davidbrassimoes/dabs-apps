// The precomputed output grade (src/grade.ts) must equal the step-by-step profile maths
// that output.frag used to do per pixel. test/fixtures/output-grade-cases.json carries the
// numbers the tv-app (core OutputGrade.kt) must reproduce.
//   npm test

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { computeGrade } from '../src/grade.ts';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const cases = JSON.parse(fs.readFileSync(path.join(HERE, 'fixtures', 'output-grade-cases.json'), 'utf8'));

// The original per-pixel formula (output.frag before the optimisation), minus dithering.
function reference(p, c) {
  c = c.map((v) => (v - 0.5) * p.contrast + 0.5);
  const l = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
  c = c.map((v) => l + (v - l) * p.saturation);
  const tint = p.temperature >= 0 ? [1.0, 0.86, 0.7] : [0.82, 0.91, 1.0];
  c = c.map((v, i) => Math.max(v, 0) * (1 + (tint[i] - 1) * Math.abs(p.temperature)));
  c = c.map((v) => Math.pow(v, 1 / p.gamma));
  c = c.map((v) => (p.blackFloor + (1 - p.blackFloor) * v) * p.brightness);
  return c.map((v) => Math.min(1, Math.max(0, v)));
}

function graded(p, c) {
  const m = new Float32Array(9);
  const o = new Float32Array(3);
  const lv = new Float32Array(2);
  const inv = computeGrade(p, m, o, lv);
  let r = [0, 1, 2].map((row) => Math.max(0, m[row] * c[0] + m[3 + row] * c[1] + m[6 + row] * c[2] + o[row]));
  if (inv !== 1) r = r.map((v) => Math.pow(v, inv));
  return r.map((v) => Math.min(1, Math.max(0, v * lv[0] + lv[1])));
}

test('precomputed grade equals the step-by-step formula', () => {
  let seed = 12345;
  const rnd = () => ((seed = (seed * 1103515245 + 12345) >>> 0) / 2 ** 32);
  for (let i = 0; i < 2000; i++) {
    const p = {
      brightness: 0.5 + rnd(),
      contrast: 0.5 + rnd(),
      saturation: rnd() * 2,
      gamma: 0.5 + rnd() * 1.5,
      blackFloor: rnd() * 0.1,
      temperature: rnd() * 2 - 1,
    };
    const c = [rnd(), rnd(), rnd()];
    const a = reference(p, c);
    const b = graded(p, c);
    for (let k = 0; k < 3; k++) assert.ok(Math.abs(a[k] - b[k]) < 1e-5, `${JSON.stringify(p)} ${c}: ${a} vs ${b}`);
  }
});

for (const c of cases) {
  test(`fixture grade: ${c.name}`, () => {
    const m = new Float32Array(9);
    const o = new Float32Array(3);
    const lv = new Float32Array(2);
    const inv = computeGrade(c.profile, m, o, lv);
    const close = (x, y) => Math.abs(x - y) < 1e-6;
    assert.ok([...m].every((v, i) => close(v, c.mat[i])));
    assert.ok([...o].every((v, i) => close(v, c.offset[i])));
    assert.ok([...lv].every((v, i) => close(v, c.levels[i])));
    assert.ok(close(inv, c.invGamma));
  });
}
