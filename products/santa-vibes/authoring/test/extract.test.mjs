// Palette extraction (src/extract.ts) must match the TV (tv-app PaletteExtract.kt).
//   npm test

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractPalette } from '../src/extract.ts';

const here = path.dirname(fileURLToPath(import.meta.url));
const fx = JSON.parse(fs.readFileSync(path.join(here, 'fixtures/palette-extract-cases.json'), 'utf8'));

test('same palettes as the TV (palette-extract-cases.json)', () => {
  fx.cases.forEach((c, n) => {
    const px = c.w * c.h;
    const rgba = new Uint8Array(px * 4);
    for (let i = 0; i < px; i++) {
      const v = parseInt(c.rgb.slice(i * 6, i * 6 + 6), 16);
      rgba[i * 4] = v >> 16;
      rgba[i * 4 + 1] = (v >> 8) & 0xff;
      rgba[i * 4 + 2] = v & 0xff;
      rgba[i * 4 + 3] = 255;
    }
    const pal = extractPalette(rgba);
    c.palette.forEach((v, i) => assert.ok(Math.abs(pal[i] - v) < 1e-4, `case ${n} value ${i}: ${pal[i]} vs ${v}`));
  });
});
