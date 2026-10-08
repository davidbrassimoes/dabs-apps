package dev.santavibes.tv

import android.opengl.GLES30
import dev.santavibes.core.Drift
import dev.santavibes.core.ParamType
import dev.santavibes.core.SceneDef

/**
 * One scene on the GL thread: program, uniform locations, render targets at its own
 * renderScale (two for feedback ping-pong), its clock and its param values. Mirrors the
 * slots in authoring/src/renderer.ts: during a transition the outgoing slot keeps
 * rendering with its other uniforms frozen (GL keeps them as program state).
 *
 * Created and destroyed only on scene switches; [render] allocates nothing.
 */
internal class SceneSlot private constructor(
    val index: Int,
    val def: SceneDef,
    private val program: Int,
    private val uTime: Int,
    private val uDeltaTime: Int,
    private val uResolution: Int,
    private val uBands: Int,
    private val uMood: Int,
    private val uBeat: Int,
    private val uPalette: Int,
    private val uImage: Int,
    private val uImageInfo: Int,
    private val uPrev: Int,
    private val uNoise: Int,
    private val paramLoc: IntArray,
    private val paramColor: BooleanArray,
    private val paramOffset: IntArray,
    /** Drift spectrum per param; null where the param never drifts. */
    private val paramDrift: Array<Drift.Spec?>,
    /** Location of `p_react` (the scene param the host forces, FORMATO-CENA.md); -1 if absent. */
    private val reactLoc: Int,
    /** Base values (user/preset), floats and colours packed at [paramOffset]. */
    val values: FloatArray,
    var renderScale: Double,
) {
    private val targets = arrayOfNulls<Gl.Target>(2)
    private var targetCount = 0
    private var ping = 0
    var time = 0.0
    /** [time] at the last [render] (-1 = never): uDeltaTime is per slot, so a scene rendered
     *  every other frame during a crossfade still advances per second. */
    private var rendered = -1.0
    val width: Int get() = targets[0]?.width ?: 0
    val height: Int get() = targets[0]?.height ?: 0
    val halfFloat: Boolean get() = targets[0]?.halfFloat == true

    /** (Re)create the targets for the surface size and [renderScale]; new targets are black. */
    fun ensureTargets(surfaceW: Int, surfaceH: Int, wantHalf: Boolean, force: Boolean) {
        if (surfaceW == 0) return
        val w = maxOf(1, Math.round(surfaceW * renderScale).toInt())
        val h = maxOf(1, Math.round(surfaceH * renderScale).toInt())
        val want = if (def.feedback) 2 else 1
        val t0 = targets[0]
        if (!force && t0 != null && t0.width == w && t0.height == h && targetCount == want) return
        destroyTargets()
        for (i in 0 until want) targets[i] = Gl.target(w, h, wantHalf)
        targetCount = want
        ping = 0
    }

    /**
     * Render into the next target and return it (null before the surface exists). `full`
     * uploads palette and params (with drift); the outgoing scene passes false and only
     * gets time, resolution and signals.
     */
    fun render(
        bands: FloatArray, mood: FloatArray, beat: FloatArray, palette: FloatArray, imageInfo: FloatArray, image: Int, black: Int, noise: Int,
        full: Boolean, driftTime: Double, driftAmount: Double, react: Float,
    ): Gl.Target? {
        if (targetCount == 0) return null
        val dst = (if (def.feedback) targets[1 - ping] else targets[0]) ?: return null
        val src = if (def.feedback) targets[ping] else null
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, dst.fbo)
        GLES30.glViewport(0, 0, dst.width, dst.height)
        GLES30.glUseProgram(program)
        GLES30.glUniform1f(uTime, time.toFloat())
        GLES30.glUniform1f(uDeltaTime, if (rendered < 0) 0f else (time - rendered).toFloat())
        rendered = time
        GLES30.glUniform2f(uResolution, dst.width.toFloat(), dst.height.toFloat())
        GLES30.glUniform4fv(uBands, 1, bands, 0)
        GLES30.glUniform4fv(uMood, 1, mood, 0)
        GLES30.glUniform4fv(uBeat, 1, beat, 0)
        GLES30.glUniform4fv(uImageInfo, 1, imageInfo, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, image)
        GLES30.glUniform1i(uImage, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, src?.tex ?: black)
        GLES30.glUniform1i(uPrev, 1)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, noise)
        GLES30.glUniform1i(uNoise, 2)
        if (full) {
            GLES30.glUniform3fv(uPalette, 5, palette, 0)
            val params = def.params
            for (i in paramLoc.indices) {
                val off = paramOffset[i]
                if (paramColor[i]) {
                    GLES30.glUniform3fv(paramLoc[i], 1, values, off)
                } else {
                    val spec = paramDrift[i]
                    val base = values[off].toDouble()
                    val v = if (spec == null) base else Drift.value(params[i], base, spec, driftTime, driftAmount)
                    GLES30.glUniform1f(paramLoc[i], v.toFloat())
                }
            }
        }
        // After the params, so the host value wins; also for the outgoing scene of a crossfade.
        if (reactLoc >= 0) GLES30.glUniform1f(reactLoc, react)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        if (def.feedback) ping = 1 - ping
        return dst
    }

    /** The target the last [render] wrote (or would read next), without rendering. */
    fun latest(): Gl.Target? = if (def.feedback) targets[ping] else targets[0]

    /** Frees the targets; the program belongs to the renderer's cache and stays. */
    fun destroy() {
        destroyTargets()
    }

    private fun destroyTargets() {
        for (i in 0 until targetCount) targets[i]?.let(Gl::destroy)
        targets[0] = null
        targets[1] = null
        targetCount = 0
    }

    companion object {
        /** The scene param the host forces to [SceneRenderer.react] (0 = current look, 1 = reactive). */
        const val REACT_PARAM = "react"

        /** A slot for an already compiled scene `program` (see [SceneRenderer]'s program cache). */
        fun create(index: Int, def: SceneDef, program: Int, renderScale: Double): SceneSlot {
            fun u(n: String) = GLES30.glGetUniformLocation(program, n)
            val n = def.params.size
            val loc = IntArray(n)
            val color = BooleanArray(n)
            val offset = IntArray(n)
            val drift = arrayOfNulls<Drift.Spec>(n)
            var size = 0
            for (i in 0 until n) {
                val p = def.params[i]
                loc[i] = u("p_${p.id}")
                color[i] = p.type == ParamType.COLOR
                offset[i] = size
                size += if (color[i]) 3 else 1
                if (p.drift > 0 && (p.type == ParamType.FLOAT || p.type == ParamType.INT)) drift[i] = Drift.spec(def.id, p.id)
            }
            val v = FloatArray(size)
            for (i in 0 until n) {
                val p = def.params[i]
                val c = p.defaultColor
                if (c != null) System.arraycopy(c, 0, v, offset[i], 3) else v[offset[i]] = p.default.toFloat()
            }
            return SceneSlot(
                index, def, program,
                u("uTime"), u("uDeltaTime"), u("uResolution"), u("uBands"), u("uMood"), u("uBeat"),
                u("uPalette"), u("uImage"), u("uImageInfo"), u("uPrev"), u("uNoise"),
                loc, color, offset, drift, u("p_$REACT_PARAM"), v, renderScale,
            )
        }
    }
}
