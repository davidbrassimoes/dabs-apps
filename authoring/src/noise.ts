// The uNoise texture (docs/FORMATO-CENA.md, v3): 256x256 single-channel bytes from
// xorshift32 seeded with 0x9E3779B9; byte i (row-major, row 0 uploaded first) is the top
// 8 bits of the state after step i+1. The tv-app (core NoiseLattice.kt) must produce the
// same bytes; test/fixtures/noise-lattice.json is the shared check.

export const NOISE_SIZE = 256;
export const NOISE_SEED = 0x9e3779b9;

export function generateNoiseLattice(): Uint8Array {
  const out = new Uint8Array(NOISE_SIZE * NOISE_SIZE);
  let x = NOISE_SEED >>> 0;
  for (let i = 0; i < out.length; i++) {
    x ^= x << 13;
    x >>>= 0;
    x ^= x >>> 17;
    x ^= x << 5;
    x >>>= 0;
    out[i] = x >>> 24;
  }
  return out;
}
