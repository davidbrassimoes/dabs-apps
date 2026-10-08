// Real signals from the pc-bridge (ROADMAP 1.6): packet parsing and the mixer port must match
// the TV (src/sign.ts, src/mixer.ts).
//   npm test

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseSign } from '../src/sign.ts';
import { SignalMixer } from '../src/mixer.ts';

const here = path.dirname(fileURLToPath(import.meta.url));
const read = (p) => JSON.parse(fs.readFileSync(path.join(here, p), 'utf8'));
const bytes = (hex) => new Uint8Array(hex.match(/../g).map((h) => parseInt(h, 16))).buffer;

test('parses the Rust encoder output', () => {
  const fx = read('../../signals-core/tests/fixtures/sign-cases.json');
  for (const c of fx.sign) {
    const p = parseSign(bytes(c.hex));
    assert.ok(p, c.hex);
    assert.equal(p.kind, c.kind);
    assert.equal(p.flags, c.flags);
    assert.equal(p.sequence, c.sequence);
    assert.equal(p.senderTimeMs, c.senderTimeMs);
    c.values.forEach((v, i) => assert.equal(p.values[i], Math.fround(v), `value ${i}`));
  }
});

test('rejects wrong size, magic and version', () => {
  const good = new Uint8Array(bytes(read('../../signals-core/tests/fixtures/sign-cases.json').sign[1].hex));
  assert.equal(parseSign(good.slice(0, 55).buffer), null);
  const badVersion = good.slice();
  badVersion[4] = 2;
  assert.equal(parseSign(badVersion.buffer), null);
  const badMagic = good.slice();
  badMagic[0] = 0x58;
  assert.equal(parseSign(badMagic.buffer), null);
});

test('mixer matches the TV frame by frame (mixer-cases.json)', () => {
  const fx = read('fixtures/mixer-cases.json');
  const m = new SignalMixer();
  m.reactivity = fx.reactivity;
  let worst = 0;
  fx.frames.forEach((f, n) => {
    if (f.packet) m.onPacket({ kind: 1, flags: f.packet.flags, sequence: n, senderTimeMs: 0, values: new Float32Array(f.packet.values) });
    const synth = { bands: new Float32Array(f.synth.slice(0, 4)), mood: new Float32Array(f.synth.slice(4, 8)), beat: new Float32Array(f.synth.slice(8, 12)) };
    m.update(fx.dt, synth);
    const out = [...m.bands, ...m.mood, ...m.beat];
    out.forEach((v, i) => {
      worst = Math.max(worst, Math.abs(v - f.out[i]));
      assert.ok(Math.abs(v - f.out[i]) < 1e-4, `frame ${n} value ${i}: ${v} vs ${f.out[i]}`);
    });
  });
  assert.ok(worst < 1e-4, `worst ${worst}`);
});
