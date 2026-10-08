package dev.santavibes.tv

import android.opengl.GLES30
import android.util.Log
import dev.santavibes.core.ImageIndex
import dev.santavibes.core.PaletteVariation
import dev.santavibes.core.SceneDef

/**
 * The current image on the GL thread (ROADMAP 2.1): one texture, swapped every [holdS] seconds.
 * `uImageInfo.z` fades it out over [fadeS], the next one is uploaded, and it fades back in, so a
 * scene never shows a hard cut (FORMATO-CENA.md: `mix de transição`).
 *
 * The decoded pixels come from [ImageProvider] (background thread); the only GL work here is one
 * glTexImage2D + mipmaps per swap, timed in the log.
 */
class ImageStage(private val provider: ImageProvider) {
    @Volatile var holdS = 60.0
    @Volatile var fadeS = 2.0

    /** hasImage, aspect, mix, reserved: as the scenes read uImageInfo. */
    val info = floatArrayOf(0f, 1f, 0f, 0f)
    var texture = 0; private set
    /** Palette of the image on screen (15 floats) and a counter bumped when it changes. */
    var palette: FloatArray? = null; private set
    var paletteVersion = 0; private set
    @Volatile var shown: ImageIndex.Entry? = null; private set
    /** Last upload cost, for the log and the overlay. */
    @Volatile var uploadMs = 0f; private set

    private enum class State { WAIT, IN, HOLD, OUT }
    private var state = State.WAIT
    private var t = 0.0
    private var requested = false

    /** A new EGL context: the old texture is gone, start again with the next image. */
    fun onSurfaceCreated() {
        texture = 0
        shown = null
        info[0] = 0f
        info[2] = 0f
        state = State.WAIT
        requested = false
    }

    fun frame(dt: Double) {
        t += dt
        when (state) {
            State.WAIT -> if (takeReady()) go(State.IN) else ask()
            State.IN -> if (t >= fadeS) go(State.HOLD)
            State.HOLD -> {
                if (t >= holdS - PREFETCH_S) ask()
                val r = provider.ready
                if (r != null && r.entry.key == shown?.key) { // only one image: keep it, no fade to black
                    provider.ready = null
                    requested = false
                    t = 0.0
                }
                if (t >= holdS && provider.ready != null) go(State.OUT)
            }
            State.OUT -> if (t >= fadeS && takeReady()) go(State.IN)
        }
        val f = (t / maxOf(1e-3, fadeS)).coerceIn(0.0, 1.0)
        val m = when (state) {
            State.WAIT -> 0.0
            State.IN -> f
            State.HOLD -> 1.0
            State.OUT -> 1.0 - f
        }
        info[2] = (m * m * (3 - 2 * m)).toFloat()
    }

    private fun go(s: State) {
        state = s
        t = 0.0
    }

    private fun ask() {
        if (!requested && provider.ready == null) {
            provider.requestNext()
            requested = true
        }
    }

    private fun takeReady(): Boolean {
        val d = provider.ready ?: return false
        provider.ready = null
        requested = false
        val start = System.nanoTime()
        if (texture == 0) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            texture = ids[0]
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, d.width, d.height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, d.pixels)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D) // scenes sample it at 1/3 resolution
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        uploadMs = ((System.nanoTime() - start) / 1e6).toFloat()
        info[0] = 1f
        info[1] = d.width.toFloat() / d.height
        palette = d.entry.palette
        paletteVersion++
        shown = d.entry
        Log.i(SceneLibrary.TAG, "image ${d.entry.key} ${d.width}x${d.height} upload %.1f ms · %s".format(uploadMs, d.entry.credit()))
        return true
    }

    /** Whether `def` gets the image (its `image` field is not `none`). */
    fun usedBy(def: SceneDef) = def.image != "none"

    fun infoFor(def: SceneDef): FloatArray = if (usedBy(def)) info else NO_IMAGE

    fun textureFor(def: SceneDef, black: Int) = if (usedBy(def) && texture != 0) texture else black

    /** The scene's rotation set, with the image's palette first when the scene uses images. */
    fun palettesFor(def: SceneDef): List<List<FloatArray>> {
        val base = PaletteVariation.scenePalettes(def)
        val p = palette ?: return base
        if (!usedBy(def)) return base
        return PaletteVariation.rotationPalettes(List(5) { k -> floatArrayOf(p[k * 3], p[k * 3 + 1], p[k * 3 + 2]) }, base)
    }

    companion object {
        /** Decode the next image this long before it is due. */
        const val PREFETCH_S = 5.0
        private val NO_IMAGE = floatArrayOf(0f, 1f, 0f, 0f)
    }
}
