// Every scene.json under scenes/ must pass validation, presets included.
//   npm test

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { presetFromValues, presetValues, scenePalette, validateScene } from '../src/scene.ts';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SCENES = path.resolve(HERE, '..', '..', 'scenes');

const ids = fs.readdirSync(SCENES).filter((d) => fs.existsSync(path.join(SCENES, d, 'scene.json')));

for (const id of ids) {
  test(`scene.json valid: ${id}`, () => {
    const json = JSON.parse(fs.readFileSync(path.join(SCENES, id, 'scene.json'), 'utf8'));
    assert.deepEqual(validateScene(json, id), []);
  });
}

const base = {
  format: 2,
  id: 's',
  name: 'S',
  defaultPalette: ['#000000', '#111111', '#222222', '#333333', '#444444'],
  params: [
    { id: 'speed', label: 'speed', type: 'float', min: 0, max: 1, default: 0.5 },
    { id: 'count', label: 'count', type: 'int', min: 1, max: 8, default: 3 },
    { id: 'on', label: 'on', type: 'bool', default: false },
    { id: 'tint', label: 'tint', type: 'color', default: '#ff0000' },
  ],
};

test('preset overrides only the params it names; palette falls back to defaultPalette', () => {
  const preset = { id: 'p', name: 'P', params: { speed: 0.2, on: true } };
  const v = presetValues(base, preset);
  assert.equal(v.get('speed'), 0.2);
  assert.equal(v.get('count'), 3);
  assert.equal(v.get('on'), 1);
  assert.deepEqual(v.get('tint'), [1, 0, 0]);
  assert.deepEqual(scenePalette(base, preset), scenePalette(base));
});

test('presetFromValues round-trips through presetValues', () => {
  const preset = {
    id: 'p',
    name: 'P',
    params: { speed: 0.25, count: 5, on: true, tint: '#00ff80' },
    palette: ['#010203', '#102030', '#405060', '#708090', '#a0b0c0'],
  };
  const back = presetFromValues(base, 'p', 'P', presetValues(base, preset), scenePalette(base, preset));
  assert.deepEqual(back, preset);
});

test('validation catches bad presets', () => {
  const bad = {
    ...base,
    presets: [
      { id: 'Bad Id', name: 'x' },
      { id: 'a', name: 'A', params: { speed: 2, nope: 1, count: 1.5, on: 1, tint: 'red' }, palette: ['#000000'] },
      { id: 'a', name: '' },
    ],
  };
  const errs = validateScene(bad, 's');
  for (const frag of ['kebab-case', 'outside', 'unknown param "nope"', 'integer', 'true/false', '#rrggbb', '5 colors', 'duplicate preset', 'missing name'])
    assert.ok(errs.some((e) => e.includes(frag)), `expected an error mentioning ${frag}:\n${errs.join('\n')}`);
  assert.ok(validateScene({ ...base, format: 1, presets: [] }, 's').some((e) => e.includes('format 2')));
});
