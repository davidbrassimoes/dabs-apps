package dev.santavibes.tv

import android.opengl.GLES30
import dev.santavibes.core.Compose
import dev.santavibes.core.Composed

/** GLES 3.0 helpers, mirroring authoring/src/gl.ts. Load-time only; nothing here runs per frame. */
object Gl {
    /** One oversized triangle covering the viewport; no vertex buffers. Same as the authoring. */
    const val FULLSCREEN_VS = """#version 300 es
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
"""

    class ShaderError(message: String, val log: String) : RuntimeException(message)

    class Target(val fbo: Int, val tex: Int, val width: Int, val height: Int, val halfFloat: Boolean)

    private fun compileShader(type: Int, src: String): Int {
        val sh = GLES30.glCreateShader(type)
        GLES30.glShaderSource(sh, src)
        GLES30.glCompileShader(sh)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(sh, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(sh)
            GLES30.glDeleteShader(sh)
            throw ShaderError("shader compile failed", log)
        }
        return sh
    }

    /** Compile + link; throws [ShaderError] with the raw info log. */
    fun program(fragSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, FULLSCREEN_VS)
        val fs = try {
            compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        } catch (e: ShaderError) {
            GLES30.glDeleteShader(vs)
            throw e
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(p)
            GLES30.glDeleteProgram(p)
            throw ShaderError("program link failed", log)
        }
        return p
    }

    /** Compile a composed scene; on failure the message lists errors mapped to author files. */
    fun sceneProgram(composed: Composed): Int = try {
        program(composed.source)
    } catch (e: ShaderError) {
        val mapped = Compose.mapErrorLog(e.log, composed).joinToString("\n") {
            if (it.file.isEmpty()) it.message else "${it.file}:${it.line}: ${it.message}"
        }
        throw ShaderError(mapped.ifEmpty { e.log }, e.log)
    }

    fun hasExtension(name: String): Boolean {
        val all = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: return false
        return all.split(' ').contains(name)
    }

    /** Colour target for a scene pass; RGBA16F when renderable, else RGBA8. Cleared to black. */
    fun target(width: Int, height: Int, wantHalfFloat: Boolean): Target {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val tex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glGenFramebuffers(1, ids, 0)
        val fbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)

        var half = wantHalfFloat
        fun alloc() {
            if (half) GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, width, height, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
            else GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0)
        }
        alloc()
        if (half && GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            half = false
            alloc()
        }
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return Target(fbo, tex, width, height, half)
    }

    fun destroy(t: Target) {
        GLES30.glDeleteFramebuffers(1, intArrayOf(t.fbo), 0)
        GLES30.glDeleteTextures(1, intArrayOf(t.tex), 0)
    }

    /** 1x1 opaque black texture, bound where a sampler has nothing to show. */
    fun blackTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        val px = java.nio.ByteBuffer.allocateDirect(4).put(byteArrayOf(0, 0, 0, -1)).also { it.position(0) }
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, px)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return ids[0]
    }
}
