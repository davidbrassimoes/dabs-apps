// The example "painting" and the image palette (k-means, the box's PaletteExtract twin).

import { extractPalette } from '../../src/extract';
import type { Rgb } from '../../src/scene';

export function exampleImage(): HTMLCanvasElement {
  // A made-up "painting": warm sky, dark hills, a sun. Stands in for a Met / NASA picture.
  const c = document.createElement('canvas');
  c.width = 640;
  c.height = 400;
  const g = c.getContext('2d')!;
  const sky = g.createLinearGradient(0, 0, 0, 400);
  sky.addColorStop(0, '#24345e');
  sky.addColorStop(0.45, '#c8735a');
  sky.addColorStop(0.7, '#f0c27a');
  g.fillStyle = sky;
  g.fillRect(0, 0, 640, 400);
  g.fillStyle = '#fff1c4';
  g.beginPath();
  g.arc(420, 230, 42, 0, Math.PI * 2);
  g.fill();
  let seed = 7;
  const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
  for (let i = 0; i < 260; i++) {
    g.strokeStyle = `rgba(${200 + rnd() * 55},${120 + rnd() * 80},${80 + rnd() * 60},0.18)`;
    g.lineWidth = 2 + rnd() * 6;
    const y = rnd() * 260;
    g.beginPath();
    g.moveTo(rnd() * 640, y);
    g.lineTo(rnd() * 640, y + rnd() * 20 - 10);
    g.stroke();
  }
  for (const [col, base, amp] of [['#3d2a3e', 280, 40], ['#1e1a2a', 320, 30], ['#0f2a2a', 360, 22]] as const) {
    g.fillStyle = col;
    g.beginPath();
    g.moveTo(0, 400);
    for (let x = 0; x <= 640; x += 16) g.lineTo(x, base + Math.sin(x * 0.012 + base) * amp + Math.sin(x * 0.05) * 6);
    g.lineTo(640, 400);
    g.fill();
  }
  return c;
}

export function paletteOf(src: CanvasImageSource, w: number, h: number): Rgb[] {
  const c = document.createElement('canvas');
  c.width = 64;
  c.height = Math.max(5, Math.round((64 * h) / w));
  const g = c.getContext('2d')!;
  g.drawImage(src, 0, 0, c.width, c.height);
  const p = extractPalette(g.getImageData(0, 0, c.width, c.height).data);
  return [0, 1, 2, 3, 4].map((k) => [p[k * 3]!, p[k * 3 + 1]!, p[k * 3 + 2]!] as Rgb);
}

