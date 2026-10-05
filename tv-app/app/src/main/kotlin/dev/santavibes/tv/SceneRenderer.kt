package dev.santavibes.tv

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import dev.santavibes.core.ParamType
import dev.santavibes.core.Profile
import dev.santavibes.core.SceneFormat
import dev.santavibes.core.Synth
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Scene pass into an FBO at renderScale (ping-pong for feedback scenes), then the shared
 * output pass (shared/glsl/output.frag) to the surface. Mirrors authoring/src/renderer.ts.
 *
 * onDrawFrame allocates nothing: uniforms come from preallocated arrays, params are
 * walked by index, and scene switches (rare, user-driven) are the only place that
 * composes and compiles. Fields marked @Volatile are read by the UI thread.
 */
class SceneRenderer(private val lib: SceneLibrary, private val profile: Profile) : GLSurfaceView.Renderer {

    private class ScenePass(
        val program: Int,
        val feedback: Boolean,
        val uTime: Int,
        val uResolution: Int,
        val uBands: Int,
        val uMood: Int,
        val uBeat: Int,
        val uPalette: Int,
        val uImage: Int,
        val uImageInfo: Int,
        val uPrev: Int,
        val paramLoc: IntArray,
        val paramColor: BooleanArray,
        val paramOffset: IntArray,
    )

    // ---- shared with the UI thread
    @Volatile var sceneIndex = -1; private set
    @Volatile var lastError: String? = null
    @Volatile var fpsCap = profile.maxFps; private set
    @Volatile var renderScale = profile.renderScale; private set
    @Volatile var renderWidth = 0; private set
    @Volatile var renderHeight = 0; private set
    @Volatile var halfFloat = false; private set
    @Volatile var sceneTime = 0.0; private set
    val stats = FrameStats()
    private val pendingStep = AtomicInteger(0)

    /** Ask the GL thread to move `delta` scenes forward/back (D-pad). */
    fun step(delta: Int) {
        pendingStep.addAndGet(delta)
    }

    // ---- GL thread state
    private var surfaceW = 0
    private var surfaceH = 0
    private var halfOk = false
    private var black = 0
    private var scene: ScenePass? = null
    private val targets = arrayOfNulls<Gl.Target>(2)
    private var targetCount = 0
    private var ping = 0
    private val synth = Synth()
    private var palette = FloatArray(15)
    private var values = FloatArray(0)
    private val imageInfo = floatArrayOf(0f, 1f, 0f, 0f)
    private var lastFrameNs = 0L

    private var output = 0
    private var oScene = -1
    private var oScenePrev = -1
    private var oMix = -1
    private var oResolution = -1
    private var oTime = -1
    private var oBrightness = -1
    private var oContrast = -1
    private var oSaturation = -1
    private var oGamma = -1
    private var oBlackFloor = -1
    private var oTemperature = -1
    private var oDither = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new EGL context: every GL object from before is gone.
        scene = null
        targetCount = 0
        halfOk = Gl.hasExtension("GL_EXT_color_buffer_half_float") || Gl.hasExtension("GL_EXT_color_buffer_float")
        black = Gl.blackTexture()
        output = Gl.program(lib.outputFrag)
        fun u(n: String) = GLES30.glGetUniformLocation(output, n)
        oScene = u("uScene"); oScenePrev = u("uScenePrev"); oMix = u("uMix")
        oResolution = u("uResolution"); oTime = u("uTime")
        oBrightness = u("uBrightness"); oContrast = u("uContrast"); oSaturation = u("uSaturation")
        oGamma = u("uGamma"); oBlackFloor = u("uBlackFloor"); oTemperature = u("uTemperature"); oDither = u("uDither")
        Log.i(SceneLibrary.TAG, "GL: ${GLES30.glGetString(GLES30.GL_RENDERER)} · ${GLES30.glGetString(GLES30.GL_VERSION)} · RGBA16F targets: $halfOk")
        val first = if (sceneIndex in lib.scenes.indices) sceneIndex else 0
        sceneIndex = -1
        if (lib.scenes.isNotEmpty()) loadScene(first)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceW = width
        surfaceH = height
        ensureTargets(force = true)
        Log.i(SceneLibrary.TAG, "surface ${width}x$height")
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val step = pendingStep.getAndSet(0)
        if (step != 0 && lib.scenes.isNotEmpty()) {
            val n = lib.scenes.size
            loadScene(((sceneIndex + step) % n + n) % n)
        }
        val dt = if (lastFrameNs == 0L) 0.0 else minOf((now - lastFrameNs) / 1e9, 0.25)
        val intervalMs = if (lastFrameNs == 0L) 0f else ((now - lastFrameNs) / 1e6).toFloat()
        lastFrameNs = now

        val pass = scene
        val dst = if (pass == null || targetCount == 0) null else if (pass.feedback) targets[1 - ping] else targets[0]
        if (pass == null || dst == null) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
        }
        sceneTime += dt
        synth.update(dt, sceneTime)

        // --- scene pass -> FBO at renderScale
        val src = if (pass.feedback) targets[ping] else null
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, dst.fbo)
        GLES30.glViewport(0, 0, dst.width, dst.height)
        GLES30.glUseProgram(pass.program)
        GLES30.glUniform1f(pass.uTime, sceneTime.toFloat())
        GLES30.glUniform2f(pass.uResolution, dst.width.toFloat(), dst.height.toFloat())
        GLES30.glUniform4fv(pass.uBands, 1, synth.bands, 0)
        GLES30.glUniform4fv(pass.uMood, 1, synth.mood, 0)
        GLES30.glUniform4fv(pass.uBeat, 1, synth.beat, 0)
        GLES30.glUniform3fv(pass.uPalette, 5, palette, 0)
        GLES30.glUniform4fv(pass.uImageInfo, 1, imageInfo, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, black)
        GLES30.glUniform1i(pass.uImage, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, src?.tex ?: black)
        GLES30.glUniform1i(pass.uPrev, 1)
        for (i in pass.paramLoc.indices) {
            val off = pass.paramOffset[i]
            if (pass.paramColor[i]) GLES30.glUniform3fv(pass.paramLoc[i], 1, values, off)
            else GLES30.glUniform1f(pass.paramLoc[i], values[off])
        }
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        if (pass.feedback) ping = 1 - ping

        // --- output pass -> surface at native resolution
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceW, surfaceH)
        GLES30.glUseProgram(output)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dst.tex)
        GLES30.glUniform1i(oScene, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, black)
        GLES30.glUniform1i(oScenePrev, 1)
        GLES30.glUniform1f(oMix, 1f) // crossfade arrives with step 0.6
        GLES30.glUniform2f(oResolution, surfaceW.toFloat(), surfaceH.toFloat())
        GLES30.glUniform1f(oTime, sceneTime.toFloat())
        GLES30.glUniform1f(oBrightness, profile.brightness)
        GLES30.glUniform1f(oContrast, profile.contrast)
        GLES30.glUniform1f(oSaturation, profile.saturation)
        GLES30.glUniform1f(oGamma, profile.gamma)
        GLES30.glUniform1f(oBlackFloor, profile.blackFloor)
        GLES30.glUniform1f(oTemperature, profile.temperature)
        GLES30.glUniform1f(oDither, profile.dither)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)

        stats.record(now, intervalMs, ((System.nanoTime() - now) / 1e6).toFloat())
    }

    // ------------------------------------------------------------ scene switching (not per frame)

    private fun loadScene(index: Int) {
        val entry = lib.scenes[index]
        val def = entry.def
        val composed = lib.compose(entry)
        val program = try {
            Gl.sceneProgram(composed)
        } catch (e: Gl.ShaderError) {
            lastError = "${def.id}: ${e.message}"
            Log.e(SceneLibrary.TAG, "compile failed in ${def.id}:\n${e.message}")
            if (scene == null) sceneIndex = index // nothing else to show; keep the index so D-pad moves on
            return
        }
        scene?.let { GLES30.glDeleteProgram(it.program) }
        fun u(n: String) = GLES30.glGetUniformLocation(program, n)
        val n = def.params.size
        val loc = IntArray(n)
        val color = BooleanArray(n)
        val offset = IntArray(n)
        var size = 0
        for (i in 0 until n) {
            val p = def.params[i]
            loc[i] = u("p_${p.id}")
            color[i] = p.type == ParamType.COLOR
            offset[i] = size
            size += if (color[i]) 3 else 1
        }
        val v = FloatArray(size)
        for (i in 0 until n) {
            val p = def.params[i]
            val c = p.defaultColor
            if (c != null) System.arraycopy(c, 0, v, offset[i], 3) else v[offset[i]] = p.default.toFloat()
        }
        values = v
        palette = SceneFormat.palette(def)
        scene = ScenePass(
            program, def.feedback,
            u("uTime"), u("uResolution"), u("uBands"), u("uMood"), u("uBeat"),
            u("uPalette"), u("uImage"), u("uImageInfo"), u("uPrev"),
            loc, color, offset,
        )
        sceneIndex = index
        lastError = null
        fpsCap = def.fps ?: profile.maxFps
        renderScale = def.renderScale ?: profile.renderScale
        sceneTime = 0.0
        ensureTargets(force = true)
        stats.reset(System.nanoTime())
        Log.i(SceneLibrary.TAG, "scene ${def.id} (${index + 1}/${lib.scenes.size}) · ${renderWidth}x$renderHeight · ${fpsCap} fps cap · feedback=${def.feedback}")
    }

    private fun ensureTargets(force: Boolean) {
        val pass = scene ?: return
        if (surfaceW == 0) return
        val w = maxOf(1, Math.round(surfaceW * renderScale).toInt())
        val h = maxOf(1, Math.round(surfaceH * renderScale).toInt())
        val want = if (pass.feedback) 2 else 1
        val t0 = targets[0]
        if (!force && t0 != null && t0.width == w && t0.height == h && targetCount == want) return
        for (i in 0 until targetCount) targets[i]?.let(Gl::destroy)
        targets[0] = null
        targets[1] = null
        for (i in 0 until want) targets[i] = Gl.target(w, h, halfOk)
        targetCount = want
        ping = 0
        renderWidth = w
        renderHeight = h
        halfFloat = targets[0]?.halfFloat == true
    }
}
