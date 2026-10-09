// Tests for the preset lab: variations (src/mutate.ts) and writing presets into scene.json
// (lib/scene-presets.mjs).
//   npm test

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { mutate, presetDiff, presetId, turnHue } from '../src/mutate.ts';
import { defaultValues, scenePalette, validateScene } from '../src/scene.ts';
import { insertPreset } from '../lib/scene-presets.mjs';
import { srgbToOklab } from '../src/palette.ts';

const SCENES = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'scenes');
const read = (id) => fs.readFileSync(path.join(SCENES, id, 'scene.json'), 'utf8');
const nebula = JSON.parse(read('nebula'));

test('variations stay in range, skip the host param and repeat with the same seed', () => {
  const values = defaultValues(nebula);
  const pal = scenePalette(nebula);
  const a = mutate(nebula, values, pal, { amount: 0.5, palette: true, seed: 7 });
  const b = mutate(nebula, values, pal, { amount: 0.5, palette: true, seed: 7 });
  assert.deepEqual([...a.values], [...b.values]);
  assert.deepEqual(a.palette, b.palette);
  for (const p of nebula.params) {
    const v = a.values.get(p.id);
    assert.ok(v >= p.min && v <= p.max, `${p.id}=${v}`);
  }
  assert.equal(a.values.get('react'), values.get('react'));
  assert.notDeepEqual([...a.values], [...values]);
  const c = mutate(nebula, values, pal, { amount: 0.5, palette: false, seed: 8 });
  assert.deepEqual(c.palette, pal);
});

test('turning the hue keeps lightness', () => {
  const pal = scenePalette(nebula);
  const turned = turnHue(pal, 1.2);
  const L = (c) => { const o = new Float64Array(3); srgbToOklab(c[0], c[1], c[2], o, 0); return o[0]; };
  pal.forEach((c, i) => assert.ok(Math.abs(L(c) - L(turned[i])) < 0.03, `colour ${i}`));
  assert.notDeepEqual(turned, pal);
});

test('a saved preset keeps only what differs from the defaults', () => {
  const values = defaultValues(nebula);
  values.set('warp', 4.2);
  values.set('react', 1);
  const p = presetDiff(nebula, 'x', 'X', values, scenePalette(nebula));
  assert.deepEqual(p.params, { warp: 4.2 });
  assert.equal(p.palette.length, 5);
  assert.equal(presetId('Céu de Março', ['ceu-de-marco']), 'ceu-de-marco-2');
  assert.equal(presetId('!!', []), 'preset');
});

test('inserting a preset changes only the presets array, for every scene', () => {
  const preset = { id: 'lab-test', name: 'Lab test', params: {}, palette: ['#000000', '#111111', '#222222', '#333333', '#444444'] };
  for (const id of fs.readdirSync(SCENES)) {
    const text = read(id);
    const out = insertPreset(text, preset);
    const json = JSON.parse(out);
    assert.deepEqual(json.presets.at(-1), preset, id);
    assert.deepEqual({ ...json, presets: json.presets.slice(0, -1) }, { ...JSON.parse(text), presets: JSON.parse(text).presets ?? [] }, id);
    assert.ok(out.startsWith(text.slice(0, text.indexOf('"presets"') > 0 ? text.indexOf('"presets"') : 50)), `${id}: head untouched`);
    assert.deepEqual(validateScene(json, id), [], id);
  }
  assert.throws(() => insertPreset(read('nebula'), { ...preset, id: 'fumo' }), /already exists/);
});

test('a scene.json without presets gets the array', () => {
  const text = '{\n  "format": 4,\n  "id": "x",\n  "name": "X",\n  "params": []\n}\n';
  const out = insertPreset(text, { id: 'a', name: 'A', params: {} });
  assert.deepEqual(JSON.parse(out).presets, [{ id: 'a', name: 'A', params: {} }]);
  assert.ok(out.startsWith('{\n  "format": 4,'));
});
