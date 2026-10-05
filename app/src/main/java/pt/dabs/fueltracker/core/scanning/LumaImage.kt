package pt.dabs.fueltracker.core.scanning

/**
 * Imagem em tons de cinzento (luminância 0..255), guardada linha a linha. Sem dependências de
 * Android para poder ser testada na JVM; a conversão a partir de Bitmap/ImageProxy fica na UI.
 */
class LumaImage(val width: Int, val height: Int, val pixels: ByteArray) {

    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "dimensões inválidas" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF

    /** Recorte limitado aos limites da imagem. */
    fun crop(left: Int, top: Int, right: Int, bottom: Int): LumaImage {
        val l = left.coerceIn(0, width - 1)
        val t = top.coerceIn(0, height - 1)
        val r = right.coerceIn(l + 1, width)
        val b = bottom.coerceIn(t + 1, height)
        val w = r - l
        val out = ByteArray(w * (b - t))
        for (y in t until b) System.arraycopy(pixels, y * width + l, out, (y - t) * w, w)
        return LumaImage(w, b - t, out)
    }

    /** Região central com [widthFraction] × [heightFraction] do tamanho (frações limitadas a 1). */
    fun centerRegion(widthFraction: Float, heightFraction: Float): LumaImage {
        val w = (width * widthFraction.coerceIn(0.01f, 1f)).toInt().coerceAtLeast(1)
        val h = (height * heightFraction.coerceIn(0.01f, 1f)).toInt().coerceAtLeast(1)
        val left = (width - w) / 2
        val top = (height - h) / 2
        return crop(left, top, left + w, top + h)
    }

    /** Roda no sentido dos ponteiros do relógio (0, 90, 180 ou 270 graus), como o rotationDegrees da câmara. */
    fun rotate(degrees: Int): LumaImage {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return this
        require(normalized % 90 == 0) { "rotação não suportada: $degrees" }
        val outWidth = if (normalized == 180) width else height
        val outHeight = if (normalized == 180) height else width
        val out = ByteArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val (nx, ny) = when (normalized) {
                    90 -> (height - 1 - y) to x
                    180 -> (width - 1 - x) to (height - 1 - y)
                    else -> y to (width - 1 - x)
                }
                out[ny * outWidth + nx] = pixels[y * width + x]
            }
        }
        return LumaImage(outWidth, outHeight, out)
    }

    /** Reduz por média de blocos até o lado maior ficar ≤ [maxSide]. */
    fun downscaleTo(maxSide: Int): LumaImage {
        val factor = (maxOf(width, height) + maxSide - 1) / maxSide
        if (factor <= 1) return this
        val outWidth = width / factor
        val outHeight = height / factor
        val out = ByteArray(outWidth * outHeight)
        val area = factor * factor
        for (oy in 0 until outHeight) {
            for (ox in 0 until outWidth) {
                var sum = 0
                for (dy in 0 until factor) {
                    val row = (oy * factor + dy) * width + ox * factor
                    for (dx in 0 until factor) sum += pixels[row + dx].toInt() and 0xFF
                }
                out[oy * outWidth + ox] = (sum / area).toByte()
            }
        }
        return LumaImage(outWidth, outHeight, out)
    }

    companion object {
        /** A partir de píxeis ARGB (Bitmap.getPixels), com a luminância BT.601. */
        fun fromArgb(width: Int, height: Int, argb: IntArray): LumaImage {
            val out = ByteArray(width * height)
            for (i in out.indices) {
                val c = argb[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                out[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
            }
            return LumaImage(width, height, out)
        }

        /**
         * A partir do plano Y de uma imagem YUV da câmara, limitado a [cropLeft]..[cropRight) ×
         * [cropTop]..[cropBottom) (o cropRect do ImageProxy). [plane] é lido a partir da posição 0.
         */
        fun fromYPlane(
            plane: ByteArray,
            rowStride: Int,
            pixelStride: Int,
            cropLeft: Int,
            cropTop: Int,
            cropRight: Int,
            cropBottom: Int,
        ): LumaImage {
            val w = cropRight - cropLeft
            val h = cropBottom - cropTop
            val out = ByteArray(w * h)
            for (y in 0 until h) {
                val rowStart = (cropTop + y) * rowStride + cropLeft * pixelStride
                if (pixelStride == 1) {
                    System.arraycopy(plane, rowStart, out, y * w, w)
                } else {
                    for (x in 0 until w) out[y * w + x] = plane[rowStart + x * pixelStride]
                }
            }
            return LumaImage(w, h, out)
        }
    }
}
