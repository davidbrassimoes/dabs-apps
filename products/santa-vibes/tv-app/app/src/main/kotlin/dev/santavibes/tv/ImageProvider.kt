package dev.santavibes.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import dev.santavibes.core.ImageIndex
import dev.santavibes.core.ImageSources
import dev.santavibes.core.PaletteExtract
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real images for the scenes (ROADMAP 2.1, docs/FONTES-IMAGEM.md), all on one background thread:
 * - every [REFRESH_MS] (and at start) asks the chosen sources for candidates and downloads up to
 *   [PREFETCH] new ones into [dir], with their credit and extracted palette in the [ImageIndex];
 * - on [requestNext], decodes the next cached image (least recently shown) into [ready], an RGBA
 *   buffer already flipped for GL (origin bottom-left) and at most [MAX_SIDE] px on its long side.
 * Offline it keeps rotating through what is cached. The GL thread only uploads [ready].
 */
class ImageProvider(private val dir: File, @Volatile var sources: Set<String>, private val fetcher: CandidateFetcher) {
    class Decoded(val entry: ImageIndex.Entry, val width: Int, val height: Int, val pixels: ByteBuffer)

    /** Where candidates come from; one implementation per source (see [CandidateFetcher]). */
    fun interface CandidateFetcher {
        fun candidates(source: String): List<ImageSources.Candidate>
    }

    @Volatile var ready: Decoded? = null
    /** For the overlay: images cached, and what the background thread is doing. */
    @Volatile var status = "a começar"; private set
    @Volatile var cached = 0; private set

    private val lock = Object()
    private var wantNext = false
    private var currentKey: String? = null
    @Volatile private var running = false
    private var thread: Thread? = null
    private val indexFile = File(dir, "index.json")
    private lateinit var index: ImageIndex

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "images").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        synchronized(lock) { lock.notifyAll() }
    }

    /** Ask for the next image; it appears in [ready] when decoded. */
    fun requestNext() = synchronized(lock) {
        wantNext = true
        lock.notifyAll()
    }

    private fun loop() {
        dir.mkdirs()
        index = runCatching { ImageIndex.fromJson(indexFile.readText(), MAX_BYTES) }.getOrElse { ImageIndex(MAX_BYTES) }
        index.entries.removeAll { !File(dir, it.file).exists() }
        cached = index.entries.size
        var nextRefresh = 0L
        while (running) {
            if (synchronized(lock) { wantNext } && ready == null) {
                decodeNext()
                continue
            }
            if (System.currentTimeMillis() >= nextRefresh) {
                refresh()
                nextRefresh = System.currentTimeMillis() + REFRESH_MS
                continue
            }
            synchronized(lock) { if (!wantNext) lock.wait(1000) }
        }
    }

    private fun decodeNext() {
        val e = index.next(sources, currentKey, System.currentTimeMillis())
        synchronized(lock) { wantNext = false }
        if (e == null) {
            status = "sem imagens em cache"
            return
        }
        try {
            ready = decode(File(dir, e.file), e)
            currentKey = e.key
            save()
        } catch (t: Throwable) {
            Log.w(SceneLibrary.TAG, "images: cannot decode ${e.file}: $t")
            index.entries.remove(e)
            File(dir, e.file).delete()
            cached = index.entries.size
            requestNext()
        }
    }

    private fun refresh() {
        for (source in sources) {
            if (!running) return
            status = "a procurar em $source"
            val list = runCatching { fetcher.candidates(source) }.getOrElse {
                Log.i(SceneLibrary.TAG, "images: $source unavailable ($it)")
                null
            } ?: continue
            if (source == ImageSources.SERVER) prune(list) // a photo DABS removed must not stay on the TV
            var added = 0
            for (c in list) {
                if (!running || added >= PREFETCH) break
                if (index.contains(c.key)) continue
                if (download(c)) added++
                Thread.sleep(PAUSE_MS) // be gentle with public APIs
            }
            if (added > 0) Log.i(SceneLibrary.TAG, "images: +$added from $source (${index.entries.size} cached, ${index.totalBytes / 1024 / 1024} MB)")
        }
        status = "pronto"
        if (ready == null && synchronized(lock) { !wantNext }) requestNext()
    }

    /** Drop cached server images that are no longer in the folder (the listing just succeeded). */
    private fun prune(listing: List<ImageSources.Candidate>) {
        val keep = listing.mapTo(HashSet()) { it.key }
        val gone = index.entries.filter { it.source == ImageSources.SERVER && it.key !in keep }
        if (gone.isEmpty()) return
        for (e in gone) {
            index.entries.remove(e)
            File(dir, e.file).delete()
        }
        cached = index.entries.size
        save()
        Log.i(SceneLibrary.TAG, "images: -${gone.size} removed from the server folder")
    }

    private fun download(c: ImageSources.Candidate): Boolean {
        val name = "${c.source}-${Integer.toHexString(c.key.hashCode())}.img"
        val file = File(dir, name)
        try {
            status = "a descarregar ${c.title.take(30)}"
            val bytes = httpGet(c.url, MAX_DOWNLOAD)
            file.writeBytes(bytes)
            val palette = PaletteExtract.extract(thumbnail(file))
            val e = ImageIndex.Entry(c.key, c.source, name, bytes.size.toLong(), c.title, c.artist, c.institution, c.license, palette, System.currentTimeMillis())
            for (old in index.add(e)) File(dir, old.file).delete()
            cached = index.entries.size
            save()
            return true
        } catch (t: Throwable) {
            Log.i(SceneLibrary.TAG, "images: skip ${c.key}: $t")
            file.delete()
            return false
        }
    }

    private fun save() {
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(index.toJson())
        tmp.renameTo(indexFile)
    }

    private fun thumbnail(file: File): IntArray {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        require(o.outWidth > 0) { "not an image" }
        o.inJustDecodeBounds = false
        o.inSampleSize = Integer.highestOneBit(maxOf(1, minOf(o.outWidth, o.outHeight) / 128))
        val big = BitmapFactory.decodeFile(file.path, o) ?: error("decode failed")
        val small = Bitmap.createScaledBitmap(big, 64, 64, true)
        val px = IntArray(64 * 64)
        small.getPixels(px, 0, 64, 0, 0, 64, 64)
        if (small !== big) small.recycle()
        big.recycle()
        return px
    }

    private fun decode(file: File, e: ImageIndex.Entry): Decoded {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        require(o.outWidth > 0) { "not an image" }
        o.inJustDecodeBounds = false
        o.inSampleSize = Integer.highestOneBit(maxOf(1, maxOf(o.outWidth, o.outHeight) / MAX_SIDE))
        o.inPreferredConfig = Bitmap.Config.ARGB_8888
        val src = BitmapFactory.decodeFile(file.path, o) ?: error("decode failed")
        val s = minOf(1f, MAX_SIDE.toFloat() / maxOf(src.width, src.height))
        val m = Matrix().apply { setScale(s, -s) } // scale and flip: GL rows start at the bottom
        val bmp = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (bmp !== src) src.recycle()
        val buf = ByteBuffer.allocateDirect(bmp.width * bmp.height * 4).order(ByteOrder.nativeOrder())
        bmp.copyPixelsToBuffer(buf) // ARGB_8888 is RGBA in memory
        buf.position(0)
        val d = Decoded(e, bmp.width, bmp.height, buf)
        bmp.recycle()
        return d
    }

    companion object {
        const val MAX_SIDE = 1024
        const val MAX_BYTES = 300L * 1024 * 1024
        const val MAX_DOWNLOAD = 15 * 1024 * 1024
        const val PREFETCH = 20
        const val PAUSE_MS = 1000L
        const val REFRESH_MS = 30 * 60 * 1000L

        /** GET with timeouts and a size cap; follows redirects within the same protocol. */
        fun httpGet(url: String, maxBytes: Int): ByteArray {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("AIC-User-Agent", USER_AGENT)
            try {
                if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                c.inputStream.use { ins ->
                    val out = java.io.ByteArrayOutputStream()
                    val b = ByteArray(16 * 1024)
                    while (true) {
                        val n = ins.read(b)
                        if (n < 0) break
                        out.write(b, 0, n)
                        if (out.size() > maxBytes) error("larger than $maxBytes bytes")
                    }
                    return out.toByteArray()
                }
            } finally {
                c.disconnect()
            }
        }

        /** Sent to the public APIs, as AIC asks; names the app, no personal data. */
        const val USER_AGENT = "SantaVibes/0.1 (personal, non-commercial TV ambient visuals)"
    }
}
