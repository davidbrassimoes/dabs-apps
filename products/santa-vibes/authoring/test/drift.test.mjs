// Tests for the parameter drift reference implementation (src/drift.ts).
//   npm test
//
// test/fixtures/drift-cases.json is the language-neutral contract: the tv-app (Kotlin)
// test suite must load the same file and match every number within 1e-9.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { driftOffset, driftSpec, driftValue, fnv1a, xorshift32 } from '../src/drift.ts';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const cases = JSON.parse(fs.readFileSync(path.join(HERE, 'fixtures', 'drift-cases.json'), 'utf8'));
const EPS = 1e-9;
const close = (a, b, what) => assert.ok(Math.abs(a - b) <= EPS, `${what}: ${a} vs ${b}`);

for (const c of cases.specs) {
  test(`fixture spec: "${c.scene}/${c.param}"`, () => {
    assert.equal(fnv1a(`${c.scene}/${c.param}`), c.hash);
    const s = driftSpec(c.scene, c.param);
    for (const k of ['f1', 'f2', 'ph1', 'ph2']) close(s[k], c.spec[k], k);
    for (const [t, v] of c.offsets) close(driftOffset(s, t), v, `offset(${t})`);
  });
}

for (const c of cases.values) {
  test(`fixture value: ${c.name}`, () => {
    const s = driftSpec(c.scene, c.param.id);
    for (const [t, v] of c.values) close(driftValue(c.param, c.base, s, t, c.amount), v, `value(${t})`);
  });
}

test('fnv1a matches the published 32-bit FNV-1a vectors', () => {
  assert.equal(fnv1a(''), 0x811c9dc5);
  assert.equal(fnv1a('a'), 0xe40c292c);
  assert.equal(fnv1a('foobar'), 0xbf9cf968);
});

test('xorshift32 stays in uint32 and never reaches 0', () => {
  let x = 1;
  for (let i = 0; i < 10000; i++) {
    x = xorshift32(x);
    assert.ok(Number.isInteger(x) && x > 0 && x < 2 ** 32);
  }
});

test('offset is in [-1, 1], slow and smooth; periods 30..120 s', () => {
  for (const id of ['speed', 'warp', 'scale', 'size', 'glow', 'rings']) {
    const s = driftSpec('scene', id);
    assert.ok(1 / s.f1 >= 30 && 1 / s.f1 <= 120);
    let prev = driftOffset(s, 0);
    for (let t = 1 / 30; t < 600; t += 1 / 30) {
      const v = driftOffset(s, t);
      assert.ok(v >= -1 && v <= 1);
      assert.ok(Math.abs(v - prev) < 0.01, 'step per frame at 30 fps must be tiny');
      prev = v;
    }
  }
});

test('peak-to-peak swing is drift * range, centred on the base', () => {
  const p = { id: 'w', label: 'w', type: 'float', min: 0, max: 10, default: 5, drift: 0.2 };
  const s = driftSpec('x', 'w');
  let lo = Infinity;
  let hi = -Infinity;
  for (let t = 0; t < 2000; t += 0.25) {
    const v = driftValue(p, 5, s, t, 1);
    lo = Math.min(lo, v);
    hi = Math.max(hi, v);
  }
  assert.ok(hi - lo <= 2 + EPS && hi - lo > 1.2, `swing ${hi - lo}`);
  assert.ok(lo >= 4 - EPS && hi <= 6 + EPS);
});

test('bool and color params never drift', () => {
  const s = driftSpec('x', 'b');
  assert.equal(driftValue({ id: 'b', label: 'b', type: 'bool', default: true, drift: 1 }, 1, s, 12, 1), 1);
});
