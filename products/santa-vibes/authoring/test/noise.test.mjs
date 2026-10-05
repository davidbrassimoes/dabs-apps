// The uNoise lattice (src/noise.ts) must match test/fixtures/noise-lattice.json, which the
// tv-app (core NoiseLattice.kt) checks too.
//   npm test

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { NOISE_SEED, NOISE_SIZE, generateNoiseLattice } from '../src/noise.ts';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const fx = JSON.parse(fs.readFileSync(path.join(HERE, 'fixtures', 'noise-lattice.json'), 'utf8'));

test('noise lattice matches the shared fixture', () => {
  const b = generateNoiseLattice();
  assert.equal(NOISE_SEED, fx.seed);
  assert.equal(NOISE_SIZE, fx.size);
  assert.equal(b.length, fx.size * fx.size);
  assert.deepEqual([...b.slice(0, 16)], fx.first16);
  assert.deepEqual([...b.slice(-4)], fx.last4);
  let h = 0x811c9dc5;
  let sum = 0;
  for (const v of b) {
    h = Math.imul(h ^ v, 0x01000193) >>> 0;
    sum += v;
  }
  assert.equal(h, fx.fnv1a32);
  assert.equal(sum, fx.sum);
});

test('noise lattice is roughly uniform', () => {
  const b = generateNoiseLattice();
  const hist = new Array(4).fill(0);
  for (const v of b) hist[v >> 6]++;
  for (const n of hist) assert.ok(Math.abs(n - b.length / 4) < b.length * 0.01, `quartiles ${hist}`);
});
