package dev.santavibes.tv

import android.opengl.EGL14
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import dev.santavibes.core.PaletteVariation
import dev.santavibes.core.Profile
import dev.santavibes.core.SceneFormat
import dev.santavibes.core.SignalMixer
import dev.santavibes.core.Synth
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Scene pass into an FBO at renderScale (ping-pong for feedback scenes), then the shared
 * output pass (shared/glsl/output.frag) to the surface. Mirrors authoring/src/renderer.ts:
 * parameter drift and palette variation on the host, and scene changes crossfade over
 * [transitionS] seconds (the outgoing [SceneSlot] keeps rendering until the fade ends).
 *
 * onDrawFrame allocates nothing: uniforms come from preallocated arrays, params are
 * walked by index, and scene switches (rare, user-driven) are the only place that
 * composes and compiles. Fields marked @Volatile are read by the UI thread.
 */
class SceneRenderer(private val lib: SceneLibrary, private val profile: Profile) : GLSurfaceView.Renderer {

    // ---- host settings (set from the UI thread; the control page arrives in phase 3)
    /** Crossfade between scenes, seconds (0 = cut). */
    @Volatile var transitionS = 4.0
    /**
     * During a crossfade, render the outgoing scene every N frames (1 = always; 0 = hold its
     * last frame). 2 by default (DABS, 2026-10-07): 13 of 15 transitions stay at the fps cap
     * instead of 9, and the half-rate scene is fading out anyway (tv-app/README.md).
     */
    @Volatile var fadeEvery = 2
    /** Global drift multiplier: 0 = off, 1 = as authored in scene.json. */
    @Volatile var driftAmount = 1.0
    /** Forced into every scene's `react` param: 0 = the current look, 1 = the reactive one. */
    @Volatile var react = 0f
    /** Drift time runs at this rate (1 = real time). */
    @Volatile var driftSpeed = 1.0
    /** Rotation and hue drift; set its fields before the surface exists. */
    val paletteVariation = PaletteVariation()

    // ---- shared with the UI thread
    @Volatile var sceneIndex = -1; private set
    @Volatile var lastError: String? = null
    @Volatile var fpsCap = profile.maxFps; private set
    @Volatile var renderScale = profile.renderScale; private set
    @Volatile var renderWidth = 0; private set
    @Volatile var renderHeight = 0; private set
    @Volatile var halfFloat = false; private set
    @Volatile var sceneTime = 0.0; private set
    @Volatile var driftTime = 0.0; private set
    /** 0..1 while crossfading between scenes, 1 otherwise. */
    @Volatile var transition = 1.0; private set
    val stats = FrameStats()
    private val pendingStep = AtomicInteger(0)

    /** Benchmark (step 0.5): set before the surface exists. See [Benchmark]. */
    var bench: Benchmark.Config? = null

    /** Scene to open first (intent extra "scene"); ignored when unknown. */
    var initialSceneId: String? = null

    /** Ask the GL thread to move `delta` scenes forward/back (D-pad). */
    fun step(delta: Int) {
        pendingStep.addAndGet(delta)
    }

    // ---- GL thread state
    private var surfaceW = 0
    private var surfaceH = 0
    private var halfOk = false
    private var black = 0
    private var noise = 0
    /** Every scene compiled once per GL context (0 = failed, see [programErrors]); no compile hitch on a switch. */
    private var programs = IntArray(0)
    private var programErrors = arrayOfNulls<String>(0)
    private var cur: SceneSlot? = null
    private var out: SceneSlot? = null // outgoing scene during a transition
    private var fadeS = 0.0
    private val synth = Synth()
    /** Real signals blended into the Synth (step 1.3); reactivity is set from the UI thread. */
    val mixer = SignalMixer()
    /** Set by the activity; null = Synth only. */
    @Volatile var hub: SignalHub? = null
    /** Source fed to the mixer at the last frame (SignPacket.SOURCE_*, 0 = none) and its weight. */
    @Volatile var signalSource = 0; private set
    @Volatile var signalWeight = 0.0; private set
    private val palette = FloatArray(15)
    private val imageInfo = floatArrayOf(0f, 1f, 0f, 0f)
    private var lastFrameNs = 0L
    private var benchmark: Benchmark? = null
    /** > 0: replaces the scene/profile renderScale (benchmark). */
    internal var scaleOverride = 0.0
    /** True while benchmarking the output pass alone. */
    internal var skipScenePass = false

    // ---- per-transition frame pacing, logged when the fade ends
    private var fadeFrames = 0
    private var fadeIntervalSum = 0.0
    private var fadeIntervalMax = 0f
    private var fadeCpuMax = 0f

    private var output: OutputPass? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new EGL context: every GL object from before is gone.
        cur = null
        out = null
        transition = 1.0
        halfOk = Gl.hasExtension("GL_EXT_color_buffer_half_float") || Gl.hasExtension("GL_EXT_color_buffer_float")
        black = Gl.blackTexture()
        noise = Gl.noiseTexture()
        output = OutputPass(lib.outputFrag, profile)
        Log.i(SceneLibrary.TAG, "GL: ${GLES30.glGetString(GLES30.GL_RENDERER)} · ${GLES30.glGetString(GLES30.GL_VERSION)} · RGBA16F targets: $halfOk")
        compileAll()
        bench?.let { b ->
            EGL14.eglSwapInterval(EGL14.eglGetCurrentDisplay(), 0) // measure GPU cost, not vsync
            benchmark = Benchmark(b, lib, this)
        }
        val wanted = lib.scenes.indexOfFirst { it.def.id == initialSceneId }
        val first = if (sceneIndex in lib.scenes.indices) sceneIndex else if (wanted >= 0) wanted else 0
        sceneIndex = -1
        if (lib.scenes.isNotEmpty()) loadScene(first, 0.0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceW = width
        surfaceH = height
        cur?.ensureTargets(width, height, halfOk, force = true)
        out?.ensureTargets(width, height, halfOk, force = true)
        publishTargetInfo()
        Log.i(SceneLibrary.TAG, "surface ${width}x$height")
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val step = pendingStep.getAndSet(0)
        if (step != 0 && lib.scenes.isNotEmpty()) {
            val n = lib.scenes.size
            loadScene(((sceneIndex + step) % n + n) % n, if (bench == null) transitionS else 0.0)
        }
        val dt = if (lastFrameNs == 0L) 0.0 else minOf((now - lastFrameNs) / 1e9, 0.25)
        val intervalMs = if (lastFrameNs == 0L) 0f else ((now - lastFrameNs) / 1e6).toFloat()
        lastFrameNs = now

        val slot = cur
        if (slot == null || slot.width == 0) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
        }
        slot.time += dt
        sceneTime = slot.time
        driftTime += dt * driftSpeed
        // The reactive look reacts to music only: no synthetic metronome (beat, punch) without it.
        synth.beatEnabled = react == 0f
        synth.punch = if (react == 0f) 0.5 else 0.0 // 0.5: the Synth default
        synth.update(dt, slot.time)
        signalSource = hub?.drain(mixer, now) ?: 0
        mixer.update(dt, synth)
        signalWeight = mixer.realWeight
        paletteVariation.write(palette, driftTime)

        // --- scene passes -> FBOs at renderScale (skipped while benchmarking the output pass alone)
        var dst: Gl.Target? = null
        var prev: Gl.Target? = null
        var mix = 1f
        if (skipScenePass) {
            dst = slot.latest() // same texture fetch cost as a real frame
        } else {
            dst = slot.render(mixer.bands, mixer.mood, mixer.beat, palette, imageInfo, black, noise, true, driftTime, driftAmount, react)
            val o = out
            if (o != null) {
                val f = if (fadeS > 0) minOf(1.0, transition + dt / fadeS) else 1.0
                transition = f
                if (f >= 1.0) {
                    endTransition(o)
                } else {
                    o.time += dt
                    val every = fadeEvery
                    prev = if (every > 0 && fadeFrames % every == 0) {
                        o.render(mixer.bands, mixer.mood, mixer.beat, palette, imageInfo, black, noise, false, driftTime, driftAmount, react)
                    } else {
                        o.latest()
                    }
                    mix = (f * f * (3 - 2 * f)).toFloat()
                }
            }
        }

        // --- output pass -> surface at native resolution
        output?.draw(dst?.tex ?: black, prev?.tex ?: black, if (prev != null) mix else 1f, noise, surfaceW, surfaceH)

        val cpuMs = ((System.nanoTime() - now) / 1e6).toFloat()
        stats.record(now, intervalMs, cpuMs)
        if (prev != null && intervalMs > 0f) {
            fadeFrames++
            fadeIntervalSum += intervalMs
            if (intervalMs > fadeIntervalMax) fadeIntervalMax = intervalMs
            if (cpuMs > fadeCpuMax) fadeCpuMax = cpuMs
        }
        benchmark?.let { if (!it.done) it.tick(now, intervalMs, cpuMs) }
    }

    // ------------------------------------------------------------ scene switching (not per frame)

    /** Switch to scene `index`; with `fade` > 0 the current scene crossfades out over `fade` seconds. */
    internal fun loadScene(index: Int, fade: Double) {
        val entry = lib.scenes[index]
        val def = entry.def
        val scale = if (scaleOverride > 0) scaleOverride else def.renderScale ?: profile.renderScale
        val program = programs.getOrElse(index) { 0 }
        if (program == 0) {
            lastError = "${def.id}: ${programErrors.getOrNull(index)}"
            if (cur == null) sceneIndex = index // nothing else to show; keep the index so D-pad moves on
            return
        }
        val slot = SceneSlot.create(index, def, program, scale)
        val prev = cur
        out?.let { endTransition(it) }
        if (prev != null && fade > 0) {
            out = prev
            fadeS = fade
            transition = 0.0
            fadeFrames = 0; fadeIntervalSum = 0.0; fadeIntervalMax = 0f; fadeCpuMax = 0f
        } else {
            prev?.destroy()
        }
        cur = slot
        slot.ensureTargets(surfaceW, surfaceH, halfOk, force = true)
        System.arraycopy(SceneFormat.palette(def), 0, palette, 0, 15)
        paletteVariation.setPalettes(def.id, PaletteVariation.scenePalettes(def), driftTime)
        sceneIndex = index
        lastError = null
        fpsCap = def.fps ?: profile.maxFps
        renderScale = scale
        sceneTime = 0.0
        publishTargetInfo()
        stats.reset(System.nanoTime())
        Log.i(
            SceneLibrary.TAG,
            "scene ${def.id} (${index + 1}/${lib.scenes.size}) · ${renderWidth}x$renderHeight · $fpsCap fps cap · feedback=${def.feedback}" +
                (if (out != null) " · crossfade ${fade}s from ${prev?.def?.id}" else "") +
                " · palettes ${paletteVariation.size}",
        )
    }

    /**
     * Compile every scene and draw each once into a 1x1 target, so drivers that finish
     * compiling lazily (on first draw) do it here and not in the middle of a crossfade.
     */
    private fun compileAll() {
        val t0 = System.nanoTime()
        val n = lib.scenes.size
        programs = IntArray(n)
        programErrors = arrayOfNulls(n)
        val warm = Gl.target(1, 1, halfOk)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, warm.fbo)
        GLES30.glViewport(0, 0, 1, 1)
        for (i in 0 until n) {
            val def = lib.scenes[i].def
            try {
                programs[i] = Gl.sceneProgram(lib.compose(lib.scenes[i]))
                GLES30.glUseProgram(programs[i])
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
            } catch (e: Gl.ShaderError) {
                programErrors[i] = e.message
                Log.e(SceneLibrary.TAG, "compile failed in ${def.id}:\n${e.message}")
            }
        }
        GLES30.glFinish()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        Gl.destroy(warm)
        Log.i(SceneLibrary.TAG, "compiled $n scenes in %.0f ms".format(java.util.Locale.ROOT, (System.nanoTime() - t0) / 1e6))
    }

    /** Benchmark: re-create the current targets at [scaleOverride]. */
    internal fun applyScaleOverride() {
        val slot = cur ?: return
        slot.renderScale = scaleOverride
        renderScale = scaleOverride
        slot.ensureTargets(surfaceW, surfaceH, halfOk, force = true)
        publishTargetInfo()
    }

    private fun endTransition(o: SceneSlot) {
        if (fadeFrames > 0) {
            Log.i(
                SceneLibrary.TAG,
                "transition ${o.def.id} -> ${cur?.def?.id}: frames=%d interval avg=%.2f max=%.2f ms cpu max=%.2f ms".format(
                    java.util.Locale.ROOT, fadeFrames, fadeIntervalSum / fadeFrames, fadeIntervalMax, fadeCpuMax,
                ),
            )
        }
        o.destroy()
        out = null
        transition = 1.0
        fadeFrames = 0
    }

    private fun publishTargetInfo() {
        val slot = cur ?: return
        renderWidth = slot.width
        renderHeight = slot.height
        halfFloat = slot.halfFloat
    }
}
