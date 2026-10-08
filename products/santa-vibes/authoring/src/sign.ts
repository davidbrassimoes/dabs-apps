// `SIGN` packets (docs/PROTOCOLO-SIGNALS.md v1, little-endian), as the pc-bridge sends them over
// its local WebSocket (ROADMAP 1.6). Mirrors tv-app SignPacket.kt and signals-core packet.rs;
// all three are checked against signals-core/tests/fixtures/sign-cases.json.

export const SIGN_LEN = 56;
export const FIELDS = 10;
export const FLAG_ONSET = 1;
export const FLAG_TEMPO_VALID = 2;
export const FLAG_SILENCE = 8;

// Indices into `values`.
export const ENERGY = 0;
export const LOW = 1;
export const MID = 2;
export const HIGH = 3;
export const CENTROID = 4;
export const BEAT = 5;
export const PHASE = 6;
export const BPM = 7;
export const MOOD_ENERGY = 8;
export const MOOD_BRIGHTNESS = 9;

export interface SignPacket {
  kind: number;
  flags: number;
  sequence: number;
  senderTimeMs: number;
  values: Float32Array;
}

/** null for a wrong size, magic or version. */
export function parseSign(buf: ArrayBuffer): SignPacket | null {
  if (buf.byteLength !== SIGN_LEN) return null;
  const v = new DataView(buf);
  if (v.getUint32(0, false) !== 0x5349474e || v.getUint8(4) !== 1) return null; // "SIGN", v1
  const values = new Float32Array(FIELDS);
  for (let i = 0; i < FIELDS; i++) values[i] = v.getFloat32(16 + i * 4, true);
  return {
    kind: v.getUint8(5),
    flags: v.getUint16(6, true),
    sequence: v.getUint32(8, true),
    senderTimeMs: v.getUint32(12, true),
    values,
  };
}
