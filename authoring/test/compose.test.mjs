// Tests for the shader composition reference implementation (src/compose.ts).
// Runs on Node's built-in test runner with native type stripping — no dependencies:
//   npm test
//
// test/fixtures/compose-cases.json is the language-neutral contract: the tv-app (Kotlin)
// test suite must load the same file and produce byte-identical `expected.source`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { collectIncludes, compose, mapErrorLog } from '../src/compose.ts';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..');
const cases = JSON.parse(fs.readFileSync(path.join(HERE, 'fixtures', 'compose-cases.json'), 'utf8'));

for (const c of cases) {
  test(`fixture: ${c.name}`, () => {
    const includes = new Map(Object.entries(c.includes));
    const r = compose(c.header, c.params, c.body, c.bodyFile, includes);
    assert.equal(r.source, c.expected.source);
    assert.deepEqual(
      r.origins.map((o) => [o.file, o.line]),
      c.expected.origins,
    );
    // One origin per composed line (source ends with a single trailing newline).
    assert.equal(r.origins.length, r.source.split('\n').length - 1);
  });
}

test('error log lines map back to the author file and line', () => {
  const c = cases.find((x) => x.name.startsWith('nested includes'));
  const r = compose(c.header, c.params, c.body, c.bodyFile, new Map(Object.entries(c.includes)));
  const mapped = mapErrorLog("ERROR: 0:6: 'fa' : no matching overloaded function found\nWARNING: 0:8: something\nunparsed line", r);
  assert.deepEqual(mapped, [
    { file: 'shared/glsl/lib/b.glsl', line: 2, message: "'fa' : no matching overloaded function found" },
    { file: 'scenes/d/scene.frag', line: 3, message: 'warning: something' },
    { file: '', line: 0, message: 'unparsed line' },
  ]);
});

test('every scene in scenes/ composes with the real header and library', async () => {
  const header = fs.readFileSync(path.join(ROOT, 'shared', 'glsl', 'header.glsl'), 'utf8');
  const readInclude = async (p) => fs.readFileSync(path.join(ROOT, 'shared', 'glsl', p), 'utf8');
  const sceneDirs = fs
    .readdirSync(path.join(ROOT, 'scenes'), { withFileTypes: true })
    .filter((d) => d.isDirectory() && fs.existsSync(path.join(ROOT, 'scenes', d.name, 'scene.json')))
    .map((d) => d.name);
  assert.ok(sceneDirs.length >= 1, 'no scenes found');

  for (const id of sceneDirs) {
    const json = JSON.parse(fs.readFileSync(path.join(ROOT, 'scenes', id, 'scene.json'), 'utf8'));
    const body = fs.readFileSync(path.join(ROOT, 'scenes', id, 'scene.frag'), 'utf8');
    assert.equal(json.id, id, `${id}: scene.json id must match the folder`);
    assert.ok(!/^\s*#version/m.test(body), `${id}: scene.frag must not contain #version`);

    const includes = await collectIncludes(body, readInclude);
    const r = compose(header, json.params, body, `scenes/${id}/scene.frag`, includes);
    const lines = r.source.split('\n');
    assert.equal(lines[0], '#version 300 es', `${id}: composed shader must start with #version 300 es`);
    assert.ok(!lines.some((l) => l.startsWith('#error')), `${id}: unresolved #include`);
    assert.ok(!lines.some((l) => /^\s*#include/.test(l)), `${id}: #include left in output`);
    for (const p of json.params) {
      const decl = `uniform ${p.type === 'color' ? 'vec3' : 'float'} p_${p.id};`;
      assert.ok(lines.includes(decl), `${id}: missing declaration ${decl}`);
    }
    assert.equal(r.origins.length, lines.length - 1, `${id}: origins/lines mismatch`);
  }
});
