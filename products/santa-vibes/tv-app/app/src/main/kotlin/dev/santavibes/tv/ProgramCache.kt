package dev.santavibes.tv

import android.opengl.GLES30
import android.util.Log

/** Every scene's program for the current GL context, compiled up front (0 = failed, see [error]). */
internal class ProgramCache(private val lib: SceneLibrary) {
    private var programs = IntArray(0)
    private var errors = arrayOfNulls<String>(0)

    fun program(index: Int): Int = programs.getOrElse(index) { 0 }

    fun error(index: Int): String? = errors.getOrNull(index)

    /**
     * Compile every scene and draw each once into a 1x1 target, so drivers that finish
     * compiling lazily (on first draw) do it here and not in the middle of a crossfade.
     */
    fun compileAll(halfOk: Boolean) {
        val t0 = System.nanoTime()
        val n = lib.scenes.size
        programs = IntArray(n)
        errors = arrayOfNulls(n)
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
                errors[i] = e.message
                Log.e(SceneLibrary.TAG, "compile failed in ${def.id}:\n${e.message}")
            }
        }
        GLES30.glFinish()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        Gl.destroy(warm)
        Log.i(SceneLibrary.TAG, "compiled $n scenes in %.0f ms".format(java.util.Locale.ROOT, (System.nanoTime() - t0) / 1e6))
    }
}
