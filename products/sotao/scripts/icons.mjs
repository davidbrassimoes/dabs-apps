#!/usr/bin/env node
// Draws the app icons into static/: a card, tilted, on Night fog's ground.
//
//   node scripts/icons.mjs
//
// It is drawn here rather than in an editor so its colours are the palette's (src/routes/layout.css, Night) and
// it can be redrawn in one command when they move. No dependencies: shapes are
// signed-distance tests, 4×4 supersampled, written as PNG with node's zlib.

import { writeFileSync } from 'node:fs';
import { deflateSync } from 'node:zlib';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const STATIC = join(dirname(fileURLToPath(import.meta.url)), '..', 'static');

// Night fog (layout.css). Ochre is the product's accent; the card's first line
// is its lamp.
const GROUND = '#151d23';
const PANEL = '#1b242c';
const LINE = '#3e4c58';
const INK = '#dde5ea';
const LAMP = '#cb9633';
const SOURCE = '#74a79a';

const rgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));

/** Distance from p to a rounded rectangle centred at the origin, half-size (w, h), radius r. */
function roundedRect(x, y, w, h, r) {
	const qx = Math.abs(x) - w + r;
	const qy = Math.abs(y) - h + r;
	return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
}

/** Distance from p to a capsule (a stroked line with round caps) from a to b, radius r. */
function capsule(x, y, ax, ay, bx, by, r) {
	const [px, py, dx, dy] = [x - ax, y - ay, bx - ax, by - ay];
	const t = Math.max(0, Math.min(1, (px * dx + py * dy) / (dx * dx + dy * dy)));
	return Math.hypot(px - dx * t, py - dy * t) - r;
}

// The drawing, in a 192-unit square, card centred and turned 8° anticlockwise —
// the original icon's composition, in the new palette. Everything sits inside
// the central 80% so the maskable crop keeps it.
const ANGLE = (-8 * Math.PI) / 180;
const CX = 96;
const CY = 98;

function colourAt(ux, uy) {
	// Into the card's own frame.
	const dx = ux - CX;
	const dy = uy - CY;
	const x = dx * Math.cos(-ANGLE) - dy * Math.sin(-ANGLE);
	const y = dx * Math.sin(-ANGLE) + dy * Math.cos(-ANGLE);

	const outer = roundedRect(x, y, 46, 56, 6);
	if (outer > 0) return GROUND;
	if (outer > -2) return LINE;

	// The card's lines: a short accent, two ink rules, and a source-green tag.
	if (capsule(x, y, -30, -34, -18, -34, 2.2) < 0) return LAMP;
	if (capsule(x, y, -30, -24, 20, -24, 2.2) < 0) return INK;
	if (capsule(x, y, -30, -14, 8, -14, 2.2) < 0) return INK;
	if (capsule(x, y, -30, 34, -16, 34, 2.2) < 0) return SOURCE;
	return PANEL;
}

function render(size) {
	const S = 4; // supersampling per axis
	const rows = [];
	for (let py = 0; py < size; py++) {
		const row = Buffer.alloc(1 + size * 3); // filter byte 0, then RGB
		for (let px = 0; px < size; px++) {
			const acc = [0, 0, 0];
			for (let sy = 0; sy < S; sy++) {
				for (let sx = 0; sx < S; sx++) {
					const ux = ((px + (sx + 0.5) / S) / size) * 192;
					const uy = ((py + (sy + 0.5) / S) / size) * 192;
					const c = rgb(colourAt(ux, uy));
					for (let i = 0; i < 3; i++) acc[i] += c[i];
				}
			}
			for (let i = 0; i < 3; i++) row[1 + px * 3 + i] = Math.round(acc[i] / (S * S));
		}
		rows.push(row);
	}
	return png(size, size, Buffer.concat(rows));
}

// --- PNG ---------------------------------------------------------------------

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
	let c = n;
	for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
	return c >>> 0;
});

function crc32(buf) {
	let c = 0xffffffff;
	for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
	return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
	const len = Buffer.alloc(4);
	len.writeUInt32BE(data.length);
	const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(body));
	return Buffer.concat([len, body, crc]);
}

function png(width, height, raw) {
	const header = Buffer.alloc(13);
	header.writeUInt32BE(width, 0);
	header.writeUInt32BE(height, 4);
	header[8] = 8; // bit depth
	header[9] = 2; // truecolour
	return Buffer.concat([
		Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
		chunk('IHDR', header),
		chunk('IDAT', deflateSync(raw, { level: 9 })),
		chunk('IEND', Buffer.alloc(0))
	]);
}

for (const [name, size] of [
	['favicon.png', 64],
	['apple-touch-icon.png', 180],
	['icon-192.png', 192],
	['icon-512.png', 512]
]) {
	writeFileSync(join(STATIC, name), render(size));
	console.log(`static/${name}  ${size}×${size}`);
}
