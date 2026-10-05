// Output grade — the host-side half of shared/glsl/output.frag. The profile's linear
// steps (contrast, saturation, temperature) fold into one 3x3 matrix + offset, and black
// floor + brightness into a scale/offset, so the full-resolution pass does a handful of
// ALU ops per pixel. Exactly equivalent to applying the steps one by one:
//   c = (c - 0.5) * contrast + 0.5                 -> contrast * c + k,  k = 0.5 (1 - contrast)
//   c = mix(luma(c), c, saturation)                -> Sat * c   (Sat * (k,k,k) = (k,k,k))
//   c = max(c, 0) * tint                           -> max(T * c, 0)  (T diagonal, positive)
//   c = pow(c, 1 / gamma)                          (skipped in the shader when gamma = 1)
//   c = (floor + (1 - floor) * c) * brightness     -> c * levels.x + levels.y
// The tv-app (core OutputGrade.kt) must produce the same numbers:
// test/fixtures/output-grade-cases.json.

export interface GradeProfile {
  brightness: number;
  contrast: number;
  saturation: number;
  gamma: number;
  blackFloor: number;
  temperature: number;
}

export const LUMA = [0.2126, 0.7152, 0.0722] as const;
export const WARM = [1.0, 0.86, 0.7] as const;
export const COOL = [0.82, 0.91, 1.0] as const;

/** Writes the column-major mat3 (9), offset (3) and levels (2: scale, offset); returns 1/gamma. */
export function computeGrade(p: GradeProfile, mat: Float32Array, offset: Float32Array, levels: Float32Array): number {
  const t = p.temperature;
  const tint = t >= 0 ? WARM : COOL;
  const a = Math.abs(t);
  const k = 0.5 * (1 - p.contrast);
  for (let row = 0; row < 3; row++) {
    const T = 1 + ((tint[row] ?? 1) - 1) * a;
    for (let col = 0; col < 3; col++) {
      const sat = (row === col ? p.saturation : 0) + (1 - p.saturation) * (LUMA[col] ?? 0);
      mat[col * 3 + row] = T * p.contrast * sat;
    }
    offset[row] = T * k;
  }
  levels[0] = (1 - p.blackFloor) * p.brightness;
  levels[1] = p.blackFloor * p.brightness;
  return 1 / p.gamma;
}
