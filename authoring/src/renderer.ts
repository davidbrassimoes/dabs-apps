// Scene renderer: FBO at renderScale (ping-pong when the scene uses feedback),
// then the shared output pass to the canvas at the simulated display resolution.
// Mirrors what tv-app/SceneRenderer + OutputPass will do on the box.

import type { Composed } from './compose';
import {
  FULLSCREEN_VS,
  GpuTimer,
  ShaderError,
  compileProgram,
  createBlackTexture,
  createImageTexture,
  createTarget,
  destroyTarget,
  type GLInfo,
  type Target,
} from './gl';
import { driftSpec, driftValue, type DriftSpec } from './drift';
import type { PaletteVariation } from './palette';
import type { ParamDef, ParamValues, Profile, Rgb, SceneJson } from './scene';
import type { Signals } from './signals';

export interface RenderSettings {
  width: number; // simulated display width (the box surface)
  height: number;
  renderScale: number; // 0..1
  fps: number; // 0 = uncapped (display refresh)
}

export interface Stats {
  fps: number;
  intervalMs: number;
  gpuMs: number; // 0 when the timer extension is missing
  renderWidth: number;
  renderHeight: number;
  format: string;
}

interface ParamUniform {
  id: string;
  loc: WebGLUniformLocation;
  isColor: boolean;
  def: ParamDef;
  drift: DriftSpec;
  live: number; // last uploaded value (after drift), for the panel
}

interface SceneProgram {
  program: WebGLProgram;
  feedback: boolean;
  uTime: WebGLUniformLocation | null;
  uResolution: WebGLUniformLocation | null;
  uBands: WebGLUniformLocation | null;
  uMood: WebGLUniformLocation | null;
  uBeat: WebGLUniformLocation | null;
  uPalette: WebGLUniformLocation | null;
  uImage: WebGLUniformLocation | null;
  uImageInfo: WebGLUniformLocation | null;
  uPrev: WebGLUniformLocation | null;
  params: ParamUniform[];
}

interface OutputProgram {
  program: WebGLProgram;
  uScene: WebGLUniformLocation | null;
  uResolution: WebGLUniformLocation | null;
  uTime: WebGLUniformLocation | null;
  uBrightness: WebGLUniformLocation | null;
  uContrast: WebGLUniformLocation | null;
  uSaturation: WebGLUniformLocation | null;
  uGamma: WebGLUniformLocation | null;
  uBlackFloor: WebGLUniformLocation | null;
  uTemperature: WebGLUniformLocation | null;
  uDither: WebGLUniformLocation | null;
}

export class Renderer {
  readonly stats: Stats = { fps: 0, intervalMs: 0, gpuMs: 0, renderWidth: 0, renderHeight: 0, format: '-' };
  readonly timer: GpuTimer;
  paused = false;
  /** Global drift multiplier: 0 = off, 1 = as authored in scene.json. */
  driftAmount = 1;
  /** Drift time runs at this rate (1 = real time). */
  driftSpeed = 1;
  /** When set, fills uPalette every frame (rotation / hue drift); otherwise setPalette() holds. */
  paletteVariation: PaletteVariation | null = null;

  private scene: SceneProgram | null = null;
  private output: OutputProgram | null = null;
  private targets: Target[] = [];
  private ping = 0;
  private settings: RenderSettings = { width: 1920, height: 1080, renderScale: 0.5, fps: 30 };
  private values: ParamValues = new Map();
  private readonly palette = new Float32Array(15);
  private readonly imageInfo = new Float32Array([0, 1, 0, 0]);
  private readonly black: WebGLTexture;
  private image: WebGLTexture | null = null;
  private profile: Profile | null = null;

  private sceneTime = 0;
  private driftTime = 0;
  private lastRender = 0;
  private intervalEma = 0;

  constructor(
    private readonly gl: WebGL2RenderingContext,
    private readonly info: GLInfo,
    private readonly canvas: HTMLCanvasElement,
  ) {
    this.black = createBlackTexture(gl);
    this.timer = new GpuTimer(gl, info.timer);
    this.applyCanvasSize();
  }

  // ------------------------------------------------------------ configuration

  setOutputShader(fragSrc: string): void {
    const gl = this.gl;
    const program = compileProgram(gl, FULLSCREEN_VS, fragSrc); // throws ShaderError
    if (this.output) gl.deleteProgram(this.output.program);
    const u = (n: string) => gl.getUniformLocation(program, n);
    this.output = {
      program,
      uScene: u('uScene'),
      uResolution: u('uResolution'),
      uTime: u('uTime'),
      uBrightness: u('uBrightness'),
      uContrast: u('uContrast'),
      uSaturation: u('uSaturation'),
      uGamma: u('uGamma'),
      uBlackFloor: u('uBlackFloor'),
      uTemperature: u('uTemperature'),
      uDither: u('uDither'),
    };
  }

  /** Compile and swap the scene program. On failure throws and keeps the previous one. */
  setScene(scene: SceneJson, composed: Composed): void {
    const gl = this.gl;
    const program = compileProgram(gl, FULLSCREEN_VS, composed.source); // throws ShaderError
    if (this.scene) gl.deleteProgram(this.scene.program);
    const u = (n: string) => gl.getUniformLocation(program, n);
    const params: ParamUniform[] = [];
    for (const p of scene.params) {
      const loc = u(`p_${p.id}`);
      if (loc) params.push({ id: p.id, loc, isColor: p.type === 'color', def: p, drift: driftSpec(scene.id, p.id), live: 0 });
    }
    const feedback = scene.feedback === true;
    this.scene = {
      program,
      feedback,
      uTime: u('uTime'),
      uResolution: u('uResolution'),
      uBands: u('uBands'),
      uMood: u('uMood'),
      uBeat: u('uBeat'),
      uPalette: u('uPalette') ?? u('uPalette[0]'),
      uImage: u('uImage'),
      uImageInfo: u('uImageInfo'),
      uPrev: u('uPrev'),
      params,
    };
    this.ensureTargets();
  }

  setParamValues(values: ParamValues): void {
    this.values = values;
  }

  setPalette(colors: Rgb[]): void {
    for (let i = 0; i < 5; i++) {
      const c = colors[i] ?? [0, 0, 0];
      this.palette[i * 3] = c[0];
      this.palette[i * 3 + 1] = c[1];
      this.palette[i * 3 + 2] = c[2];
    }
  }

  setImage(src: TexImageSource | null, width: number, height: number): void {
    if (this.image) this.gl.deleteTexture(this.image);
    this.image = src ? createImageTexture(this.gl, src) : null;
    this.imageInfo[0] = src ? 1 : 0;
    this.imageInfo[1] = src && height > 0 ? width / height : 1;
    this.imageInfo[2] = src ? 1 : 0;
  }

  setProfile(p: Profile): void {
    this.profile = p;
  }

  setSettings(s: RenderSettings): void {
    const sizeChanged = s.width !== this.settings.width || s.height !== this.settings.height;
    const scaleChanged = s.renderScale !== this.settings.renderScale;
    this.settings = { ...s };
    if (sizeChanged) this.applyCanvasSize();
    if (sizeChanged || scaleChanged) this.ensureTargets(true);
  }

  getSettings(): RenderSettings {
    return { ...this.settings };
  }

  resetTime(): void {
    this.sceneTime = 0;
    this.lastRender = 0;
  }

  /** Host drift time (seconds × drift speed since start); drives drift and palette variation. */
  get driftClock(): number {
    return this.driftTime;
  }

  get time(): number {
    return this.sceneTime;
  }

  /** Value last sent to the shader for a float/int param (base + drift), or undefined. */
  liveValue(id: string): number | undefined {
    const p = this.scene?.params.find((x) => x.id === id);
    return p && !p.isColor ? p.live : undefined;
  }

  clearFeedback(): void {
    const gl = this.gl;
    for (const t of this.targets) {
      gl.bindFramebuffer(gl.FRAMEBUFFER, t.fbo);
      gl.clearColor(0, 0, 0, 1);
      gl.clear(gl.COLOR_BUFFER_BIT);
    }
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  }

  // ------------------------------------------------------------ frame

  /** Returns true when a scene frame was rendered this call. */
  frame(nowMs: number, signals: Signals): boolean {
    const scene = this.scene;
    const output = this.output;
    if (!scene || !output || this.paused || this.targets.length === 0) return false;

    const s = this.settings;
    if (s.fps > 0 && this.lastRender > 0 && nowMs - this.lastRender < 1000 / s.fps - 1.5) return false;

    const dt = this.lastRender > 0 ? Math.min((nowMs - this.lastRender) / 1000, 0.25) : 0;
    this.lastRender = nowMs;
    this.sceneTime += dt;
    this.driftTime += dt * this.driftSpeed;
    signals.update(dt, this.sceneTime);

    const gl = this.gl;
    this.timer.poll();
    this.timer.begin();

    // --- scene pass -> FBO at renderScale
    const src = scene.feedback ? this.targets[this.ping] : undefined;
    const dst = scene.feedback ? this.targets[1 - this.ping] : this.targets[0];
    if (!dst) return false;

    gl.bindFramebuffer(gl.FRAMEBUFFER, dst.fbo);
    gl.viewport(0, 0, dst.width, dst.height);
    gl.useProgram(scene.program);

    if (scene.uTime) gl.uniform1f(scene.uTime, this.sceneTime);
    if (scene.uResolution) gl.uniform2f(scene.uResolution, dst.width, dst.height);
    if (scene.uBands) gl.uniform4fv(scene.uBands, signals.bands);
    if (scene.uMood) gl.uniform4fv(scene.uMood, signals.mood);
    if (scene.uBeat) gl.uniform4fv(scene.uBeat, signals.beat);
    this.paletteVariation?.write(this.palette, this.driftTime);
    if (scene.uPalette) gl.uniform3fv(scene.uPalette, this.palette);
    if (scene.uImageInfo) gl.uniform4fv(scene.uImageInfo, this.imageInfo);

    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, this.image ?? this.black);
    if (scene.uImage) gl.uniform1i(scene.uImage, 0);
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_2D, src ? src.tex : this.black);
    if (scene.uPrev) gl.uniform1i(scene.uPrev, 1);

    for (const p of scene.params) {
      const v = this.values.get(p.id);
      if (v === undefined) continue;
      if (p.isColor) {
        if (Array.isArray(v)) gl.uniform3f(p.loc, v[0], v[1], v[2]);
      } else if (typeof v === 'number') {
        p.live = driftValue(p.def, v, p.drift, this.driftTime, this.driftAmount);
        gl.uniform1f(p.loc, p.live);
      }
    }
    gl.drawArrays(gl.TRIANGLES, 0, 3);

    // --- output pass -> canvas at display resolution
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    gl.viewport(0, 0, this.canvas.width, this.canvas.height);
    gl.useProgram(output.program);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, dst.tex);
    if (output.uScene) gl.uniform1i(output.uScene, 0);
    if (output.uResolution) gl.uniform2f(output.uResolution, this.canvas.width, this.canvas.height);
    if (output.uTime) gl.uniform1f(output.uTime, this.sceneTime);
    const pr = this.profile;
    if (pr) {
      if (output.uBrightness) gl.uniform1f(output.uBrightness, pr.brightness);
      if (output.uContrast) gl.uniform1f(output.uContrast, pr.contrast);
      if (output.uSaturation) gl.uniform1f(output.uSaturation, pr.saturation);
      if (output.uGamma) gl.uniform1f(output.uGamma, pr.gamma);
      if (output.uBlackFloor) gl.uniform1f(output.uBlackFloor, pr.blackFloor);
      if (output.uTemperature) gl.uniform1f(output.uTemperature, pr.temperature);
      if (output.uDither) gl.uniform1f(output.uDither, pr.dither);
    }
    gl.drawArrays(gl.TRIANGLES, 0, 3);

    this.timer.end();
    if (scene.feedback) this.ping = 1 - this.ping;

    // --- stats
    if (dt > 0) {
      const ms = dt * 1000;
      this.intervalEma = this.intervalEma === 0 ? ms : this.intervalEma + (ms - this.intervalEma) * 0.1;
      this.stats.intervalMs = this.intervalEma;
      this.stats.fps = 1000 / this.intervalEma;
    }
    this.stats.gpuMs = this.timer.lastMs;
    return true;
  }

  // ------------------------------------------------------------ internals

  private applyCanvasSize(): void {
    this.canvas.width = this.settings.width;
    this.canvas.height = this.settings.height;
  }

  private ensureTargets(force = false): void {
    const want = this.scene?.feedback ? 2 : 1;
    const w = Math.max(1, Math.round(this.settings.width * this.settings.renderScale));
    const h = Math.max(1, Math.round(this.settings.height * this.settings.renderScale));
    const first = this.targets[0];
    if (!force && first && first.width === w && first.height === h && this.targets.length === want) return;
    for (const t of this.targets) destroyTarget(this.gl, t);
    this.targets = [];
    for (let i = 0; i < want; i++) this.targets.push(createTarget(this.gl, w, h, this.info.halfFloat));
    this.ping = 0;
    this.stats.renderWidth = w;
    this.stats.renderHeight = h;
    this.stats.format = this.targets[0]?.format ?? '-';
  }
}

export { ShaderError };
