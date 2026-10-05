// Scene renderer: FBO at renderScale (ping-pong when the scene uses feedback),
// then the shared output pass to the canvas at the simulated display resolution.
// Mirrors what tv-app/SceneRenderer + OutputPass will do on the box.
//
// Scene transitions: each scene lives in a slot (program, targets, ping-pong, time).
// When a new scene arrives with a pending transition, the current slot becomes the
// outgoing one and keeps rendering — with its uniforms frozen at their last values,
// which GL keeps as program state — while the output pass crossfades the two.
// Outside transitions only one scene is rendered.

import type { Composed } from './compose';
import { driftSpec, driftValue, type DriftSpec } from './drift';
import {
  FULLSCREEN_VS,
  GpuTimer,
  ShaderError,
  compileProgram,
  createBlackTexture,
  createImageTexture,
  createNoiseTexture,
  createTarget,
  destroyTarget,
  type GLInfo,
  type Target,
} from './gl';
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
  uNoise: WebGLUniformLocation | null;
  params: ParamUniform[];
}

/** One running scene: its program, render targets and clock. */
interface Slot {
  scene: SceneProgram;
  targets: Target[];
  ping: number;
  time: number;
}

interface OutputProgram {
  program: WebGLProgram;
  uScene: WebGLUniformLocation | null;
  uScenePrev: WebGLUniformLocation | null;
  uMix: WebGLUniformLocation | null;
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

  private cur: Slot | null = null;
  private out: Slot | null = null; // outgoing scene during a transition
  private fade = 1; // 0..1 progress of the transition
  private fadeS = 0;
  private pendingFadeS = 0;
  private output: OutputProgram | null = null;
  private settings: RenderSettings = { width: 1920, height: 1080, renderScale: 0.5, fps: 30 };
  private values: ParamValues = new Map();
  private readonly palette = new Float32Array(15);
  private readonly imageInfo = new Float32Array([0, 1, 0, 0]);
  private readonly black: WebGLTexture;
  private readonly noise: WebGLTexture;
  private image: WebGLTexture | null = null;
  private profile: Profile | null = null;

  private driftTime = 0;
  private lastRender = 0;
  private intervalEma = 0;

  constructor(
    private readonly gl: WebGL2RenderingContext,
    private readonly info: GLInfo,
    private readonly canvas: HTMLCanvasElement,
  ) {
    this.black = createBlackTexture(gl);
    this.noise = createNoiseTexture(gl);
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
      uScenePrev: u('uScenePrev'),
      uMix: u('uMix'),
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

  /** The next setScene() crossfades from the current scene over `seconds` (0 = cut). */
  transitionNext(seconds: number): void {
    this.pendingFadeS = seconds;
  }

  /**
   * Compile and swap the scene program. On failure throws and keeps the previous one.
   * With a pending transition the current scene becomes the outgoing one and the new
   * scene starts in a fresh slot (time 0, cleared targets); otherwise the program is
   * replaced in place (hot reload keeps time and feedback).
   */
  setScene(scene: SceneJson, composed: Composed): void {
    const gl = this.gl;
    const pending = this.pendingFadeS;
    this.pendingFadeS = 0;
    const program = compileProgram(gl, FULLSCREEN_VS, composed.source); // throws ShaderError
    const u = (n: string) => gl.getUniformLocation(program, n);
    const params: ParamUniform[] = [];
    for (const p of scene.params) {
      const loc = u(`p_${p.id}`);
      if (loc) params.push({ id: p.id, loc, isColor: p.type === 'color', def: p, drift: driftSpec(scene.id, p.id), live: 0 });
    }
    const prog: SceneProgram = {
      program,
      feedback: scene.feedback === true,
      uTime: u('uTime'),
      uResolution: u('uResolution'),
      uBands: u('uBands'),
      uMood: u('uMood'),
      uBeat: u('uBeat'),
      uPalette: u('uPalette') ?? u('uPalette[0]'),
      uImage: u('uImage'),
      uImageInfo: u('uImageInfo'),
      uPrev: u('uPrev'),
      uNoise: u('uNoise'),
      params,
    };
    const fresh = pending > 0 || !this.cur;
    if (pending > 0 && this.cur) {
      if (this.out) this.destroySlot(this.out);
      this.out = this.cur;
      this.cur = { scene: prog, targets: [], ping: 0, time: 0 };
      this.fade = 0;
      this.fadeS = pending;
    } else if (this.cur) {
      gl.deleteProgram(this.cur.scene.program);
      this.cur.scene = prog;
    } else {
      this.cur = { scene: prog, targets: [], ping: 0, time: 0 };
    }
    this.ensureTargets();
    if (fresh) this.clearSlot(this.cur);
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
    if (this.cur) this.cur.time = 0;
    this.lastRender = 0;
  }

  /** Host drift time (seconds × drift speed since start); drives drift and palette variation. */
  get driftClock(): number {
    return this.driftTime;
  }

  get time(): number {
    return this.cur?.time ?? 0;
  }

  /** 0..1 while crossfading between scenes, 1 otherwise. */
  get transition(): number {
    return this.out ? this.fade : 1;
  }

  /** Value last sent to the shader for a float/int param (base + drift), or undefined. */
  liveValue(id: string): number | undefined {
    const p = this.cur?.scene.params.find((x) => x.id === id);
    return p && !p.isColor ? p.live : undefined;
  }

  clearFeedback(): void {
    if (this.cur) this.clearSlot(this.cur);
  }

  // ------------------------------------------------------------ frame

  /** Returns true when a scene frame was rendered this call. */
  frame(nowMs: number, signals: Signals): boolean {
    const cur = this.cur;
    const output = this.output;
    if (!cur || !output || this.paused || cur.targets.length === 0) return false;

    const s = this.settings;
    if (s.fps > 0 && this.lastRender > 0 && nowMs - this.lastRender < 1000 / s.fps - 1.5) return false;

    const dt = this.lastRender > 0 ? Math.min((nowMs - this.lastRender) / 1000, 0.25) : 0;
    this.lastRender = nowMs;
    cur.time += dt;
    this.driftTime += dt * this.driftSpeed;
    signals.update(dt, cur.time);

    const gl = this.gl;
    this.timer.poll();
    this.timer.begin();

    // --- scene passes -> FBOs at renderScale
    this.paletteVariation?.write(this.palette, this.driftTime);
    const dst = this.renderSlot(cur, signals, true);
    let prevDst: Target | null = null;
    const out = this.out;
    if (out) {
      this.fade = this.fadeS > 0 ? Math.min(1, this.fade + dt / this.fadeS) : 1;
      if (this.fade >= 1) {
        this.destroySlot(out);
        this.out = null;
      } else {
        out.time += dt;
        prevDst = this.renderSlot(out, signals, false);
      }
    }
    if (!dst) return false;

    // --- output pass -> canvas at display resolution
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    gl.viewport(0, 0, this.canvas.width, this.canvas.height);
    gl.useProgram(output.program);
    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, dst.tex);
    if (output.uScene) gl.uniform1i(output.uScene, 0);
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_2D, prevDst ? prevDst.tex : this.black);
    if (output.uScenePrev) gl.uniform1i(output.uScenePrev, 1);
    const f = prevDst ? this.fade : 1;
    if (output.uMix) gl.uniform1f(output.uMix, f * f * (3 - 2 * f));
    if (output.uResolution) gl.uniform2f(output.uResolution, this.canvas.width, this.canvas.height);
    if (output.uTime) gl.uniform1f(output.uTime, cur.time);
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

  /**
   * Render one slot into its next target. `full` uploads palette and params (the
   * current scene); the outgoing scene only gets time, resolution and signals and
   * keeps the rest of its uniforms as they were.
   */
  private renderSlot(slot: Slot, signals: Signals, full: boolean): Target | null {
    const gl = this.gl;
    const scene = slot.scene;
    const src = scene.feedback ? slot.targets[slot.ping] : undefined;
    const dst = scene.feedback ? slot.targets[1 - slot.ping] : slot.targets[0];
    if (!dst) return null;

    gl.bindFramebuffer(gl.FRAMEBUFFER, dst.fbo);
    gl.viewport(0, 0, dst.width, dst.height);
    gl.useProgram(scene.program);

    if (scene.uTime) gl.uniform1f(scene.uTime, slot.time);
    if (scene.uResolution) gl.uniform2f(scene.uResolution, dst.width, dst.height);
    if (scene.uBands) gl.uniform4fv(scene.uBands, signals.bands);
    if (scene.uMood) gl.uniform4fv(scene.uMood, signals.mood);
    if (scene.uBeat) gl.uniform4fv(scene.uBeat, signals.beat);
    if (scene.uImageInfo) gl.uniform4fv(scene.uImageInfo, this.imageInfo);

    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, this.image ?? this.black);
    if (scene.uImage) gl.uniform1i(scene.uImage, 0);
    gl.activeTexture(gl.TEXTURE1);
    gl.bindTexture(gl.TEXTURE_2D, src ? src.tex : this.black);
    if (scene.uPrev) gl.uniform1i(scene.uPrev, 1);
    gl.activeTexture(gl.TEXTURE2);
    gl.bindTexture(gl.TEXTURE_2D, this.noise);
    if (scene.uNoise) gl.uniform1i(scene.uNoise, 2);

    if (full) {
      if (scene.uPalette) gl.uniform3fv(scene.uPalette, this.palette);
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
    }
    gl.drawArrays(gl.TRIANGLES, 0, 3);
    if (scene.feedback) slot.ping = 1 - slot.ping;
    return dst;
  }

  private applyCanvasSize(): void {
    this.canvas.width = this.settings.width;
    this.canvas.height = this.settings.height;
  }

  /** (Re)create the current slot's targets for its feedback mode and the render size. */
  private ensureTargets(force = false): void {
    const cur = this.cur;
    if (!cur) return;
    const want = cur.scene.feedback ? 2 : 1;
    const w = Math.max(1, Math.round(this.settings.width * this.settings.renderScale));
    const h = Math.max(1, Math.round(this.settings.height * this.settings.renderScale));
    const first = cur.targets[0];
    if (!force && first && first.width === w && first.height === h && cur.targets.length === want) return;
    for (const t of cur.targets) destroyTarget(this.gl, t);
    cur.targets = [];
    for (let i = 0; i < want; i++) cur.targets.push(createTarget(this.gl, w, h, this.info.halfFloat));
    cur.ping = 0;
    this.stats.renderWidth = w;
    this.stats.renderHeight = h;
    this.stats.format = cur.targets[0]?.format ?? '-';
  }

  private clearSlot(slot: Slot): void {
    const gl = this.gl;
    for (const t of slot.targets) {
      gl.bindFramebuffer(gl.FRAMEBUFFER, t.fbo);
      gl.clearColor(0, 0, 0, 1);
      gl.clear(gl.COLOR_BUFFER_BIT);
    }
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  }

  private destroySlot(slot: Slot): void {
    for (const t of slot.targets) destroyTarget(this.gl, t);
    slot.targets = [];
    this.gl.deleteProgram(slot.scene.program);
  }
}

export { ShaderError };
