package dev.santavibes.tv

import android.opengl.GLES30
import dev.santavibes.core.OutputGrade
import dev.santavibes.core.Profile

/**
 * The shared output pass (shared/glsl/output.frag): profile grade, scene crossfade and
 * dither, from the scene targets to the surface. Create on the GL thread once per
 * context; [draw] allocates nothing.
 */
internal class OutputPass(fragSrc: String, private val profile: Profile) {
    private val program = Gl.program(fragSrc)
    private fun u(n: String) = GLES30.glGetUniformLocation(program, n)
    private val uScene = u("uScene")
    private val uScenePrev = u("uScenePrev")
    private val uMix = u("uMix")
    private val uResolution = u("uResolution")
    private val uNoise = u("uNoise")
    private val uDitherOffset = u("uDitherOffset")
    private val uGrade = u("uGrade")
    private val uGradeOffset = u("uGradeOffset")
    private val uInvGamma = u("uInvGamma")
    private val uLevels = u("uLevels")
    private val uDither = u("uDither")
    private val grade = OutputGrade().apply { compute(profile) }
    private var frameCount = 0

    /** `scene` over `prev` by `mix` (1 = only `scene`; pass `black` as `prev` outside transitions). */
    fun draw(scene: Int, prev: Int, mix: Float, noise: Int, width: Int, height: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, scene)
        GLES30.glUniform1i(uScene, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, prev)
        GLES30.glUniform1i(uScenePrev, 1)
        GLES30.glUniform1f(uMix, mix)
        GLES30.glUniform2f(uResolution, width.toFloat(), height.toFloat())
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, noise)
        GLES30.glUniform1i(uNoise, 2)
        frameCount++
        GLES30.glUniform2f(uDitherOffset, ((frameCount * 73) and 255).toFloat(), ((frameCount * 151) and 255).toFloat())
        GLES30.glUniformMatrix3fv(uGrade, 1, false, grade.mat, 0)
        GLES30.glUniform3fv(uGradeOffset, 1, grade.offset, 0)
        GLES30.glUniform1f(uInvGamma, grade.invGamma)
        GLES30.glUniform2fv(uLevels, 1, grade.levels, 0)
        GLES30.glUniform1f(uDither, profile.dither)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }
}
