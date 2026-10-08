package dev.santavibes.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.opengl.GLSurfaceView
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import dev.santavibes.core.ImageSources

/**
 * Everything that puts scenes on screen, shared by the app (MainActivity) and the screensaver
 * (SantaDream): scene library, renderer, signal hub, image provider, GL view with vsync pacing
 * by Choreographer at the scene's fps cap (RENDERMODE_WHEN_DIRTY), diagnostic overlay and the
 * logcat summary. The host only builds the Options, adds [view] and calls start/stop.
 */
class Player(private val context: Context, private val o: Options) {

    /** Host settings; MainActivity reads them from intent extras, the screensaver uses the defaults. */
    data class Options(
        val sceneId: String? = null,
        val bench: Benchmark.Config? = null,
        val transitionS: Double? = null,
        val fadeEvery: Int? = null,
        val drift: Double? = null,
        val driftSpeed: Double? = null,
        val source: String? = null,
        val reactivity: Double = DEFAULT_REACTIVITY,
        val reactive: Boolean = true, // reactive look by default (DABS, 2026-10-07)
        val images: String? = null, // null = all sources
        val imageServer: String = DEFAULT_IMAGE_SERVER,
        val imageHoldS: Double? = null,
        val rotate: Boolean = true,
        val holdS: Double? = null,
        val fadeS: Double? = null,
        val hueDeg: Double? = null,
    )

    val library = SceneLibrary(context.assets)
    val renderer = SceneRenderer(library, library.profile("tv"))
    private val hub = SignalHub(deviceId())
    private var imageProvider: ImageProvider? = null
    private val benchMode = o.bench != null
    private val ui = Handler(Looper.getMainLooper())
    private var running = false

    private val glView: GLSurfaceView
    private val overlay: TextView
    /** The view to show fullscreen. */
    val view: FrameLayout

    init {
        renderer.initialSceneId = o.sceneId
        renderer.bench = o.bench
        o.transitionS?.let { renderer.transitionS = it }
        o.fadeEvery?.let { renderer.fadeEvery = it }
        o.drift?.let { renderer.driftAmount = it }
        o.driftSpeed?.let { renderer.driftSpeed = it }
        hub.mode = SignalHub.parseMode(o.source)
        renderer.mixer.reactivity = o.reactivity
        renderer.react = if (o.reactive) 1f else 0f
        ImageSources.parseSelection(o.images)?.let { sel ->
            val p = ImageProvider(java.io.File(context.filesDir, "images"), sel, ImageFetchers(o.imageServer))
            imageProvider = p
            renderer.images = ImageStage(p).apply { o.imageHoldS?.let { holdS = it } }
        }
        if (!benchMode) renderer.hub = hub
        renderer.paletteVariation.apply {
            rotate = o.rotate
            o.holdS?.let { holdS = it }
            o.fadeS?.let { fadeS = it }
            o.hueDeg?.let { hueDeg = it }
        }

        glView = GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 8, 0, 0)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = if (benchMode) GLSurfaceView.RENDERMODE_CONTINUOUSLY else GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
        overlay = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setPadding(24, 16, 24, 16)
            visibility = View.GONE
        }
        view = FrameLayout(context).apply {
            addView(glView)
            addView(this@Player.overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                setMargins(32, 32, 32, 32)
            })
        }
        Log.i(SceneLibrary.TAG, "${library.scenes.size} scenes: ${library.scenes.joinToString { it.def.id }}")
    }

    fun start() {
        glView.onResume()
        running = true
        nextDueNs = 0L
        if (!benchMode) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
            hub.start()
            imageProvider?.start()
        }
        ui.post(tick)
    }

    fun stop() {
        running = false
        ui.removeCallbacks(tick)
        hub.stop()
        imageProvider?.stop()
        glView.onPause()
    }

    // ---- remote actions

    fun step(delta: Int) = renderer.step(delta)

    fun toggleLook() {
        renderer.react = 1f - renderer.react
        Log.i(SceneLibrary.TAG, "look=${lookName()}")
        if (overlay.visibility == View.VISIBLE) overlay.text = overlayText()
    }

    fun toggleOverlay() {
        overlay.visibility = if (overlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        overlay.text = overlayText()
    }

    // ---- frame pacing
    private var nextDueNs = 0L
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val interval = 1_000_000_000L / maxOf(1, renderer.fpsCap)
            // Request when the next frame is due (within half a 60 Hz vsync); keeps e.g.
            // 24 fps on a 60 Hz display as a 2-3-2-3 vsync cadence instead of 20 fps.
            if (frameTimeNanos >= nextDueNs - 8_000_000L) {
                glView.requestRender()
                nextDueNs += interval
                if (frameTimeNanos - nextDueNs > interval) nextDueNs = frameTimeNanos + interval
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // ---- overlay + logcat summary (UI thread, twice a second; never on the GL thread)
    private var loggedSeq = 0
    private var allocAtLastLog = 0L
    private var allocTimeNs = 0L
    private var allocRate = 0L // bytes/s allocated by the whole process, last window
    private var loggedSource = -1
    private val lastReceived = LongArray(5)
    private var lastReceivedNs = 0L
    private var packetRate = 0f // packets/s of the chosen source, last tick
    private val tick = object : Runnable {
        override fun run() {
            val src = renderer.signalSource
            val nowRx = System.nanoTime()
            if (lastReceivedNs != 0L && src > 0) {
                packetRate = (hub.received[src] - lastReceived[src]) * 1e9f / (nowRx - lastReceivedNs)
            }
            System.arraycopy(hub.received, 0, lastReceived, 0, 5)
            lastReceivedNs = nowRx
            if (src != loggedSource) {
                loggedSource = src
                Log.i(SceneLibrary.TAG, "signals: source " + SignalHub.kindName(src))
            }
            val s = renderer.stats
            if (s.seq != loggedSeq) {
                loggedSeq = s.seq
                val now = System.nanoTime()
                val alloc = allocatedBytes()
                if (allocTimeNs != 0L) allocRate = ((alloc - allocAtLastLog) * 1_000_000_000L) / maxOf(1L, now - allocTimeNs)
                allocAtLastLog = alloc
                allocTimeNs = now
                val id = renderer.sceneIndex.let { if (it >= 0) library.scenes[it].def.id else "-" }
                Log.i(
                    SceneLibrary.TAG,
                    "stats scene=$id frames=${s.winFrames} fps=%.1f interval avg=%.2f p95=%.2f max=%.2f ms cpu avg=%.2f p95=%.2f ms render=%dx%d %s cap=%d alloc=%d B/s src=%s w=%.2f bpm=%.1f pkt/s=%.0f look=%s".format(
                        s.winFps, s.winIntervalAvg, s.winIntervalP95, s.winIntervalMax, s.winCpuAvg, s.winCpuP95,
                        renderer.renderWidth, renderer.renderHeight, if (renderer.halfFloat) "RGBA16F" else "RGBA8",
                        renderer.fpsCap, allocRate, SignalHub.kindName(src), renderer.signalWeight, renderer.mixer.realBpm, packetRate, lookName(),
                    ),
                )
            }
            if (overlay.visibility == View.VISIBLE) overlay.text = overlayText()
            ui.postDelayed(this, 500)
        }
    }

    private fun overlayText(): String {
        val r = renderer
        val s = r.stats
        val i = r.sceneIndex
        val def = if (i >= 0) library.scenes[i].def else null
        val sb = StringBuilder()
        sb.append(def?.name ?: "-").append(" (").append(def?.id ?: "-").append(") · ").append(i + 1).append('/').append(library.scenes.size).append('\n')
        sb.append("%.1f fps · %.1f ms/frame · cpu %.2f ms · cap %d".format(s.fps, s.intervalMs, s.cpuMs, r.fpsCap)).append('\n')
        sb.append("%dx%d %s · renderScale %.2f · t=%.0fs".format(r.renderWidth, r.renderHeight, if (r.halfFloat) "RGBA16F" else "RGBA8", r.renderScale, r.sceneTime)).append('\n')
        if (s.seq > 0) sb.append("10 s: avg %.2f · p95 %.2f · max %.2f ms · alloc %d B/s".format(s.winIntervalAvg, s.winIntervalP95, s.winIntervalMax, allocRate)).append('\n')
        val pv = r.paletteVariation
        sb.append("drift %.1f · t=%.0fs · paleta %d/%d%s%s · crossfade %.1fs%s".format(
            r.driftAmount, r.driftTime, pv.current + 1, pv.size,
            if (pv.rotate) " (rodar ${pv.holdS.toInt()}s)" else "",
            if (pv.hueDeg != 0.0) " · matiz ±${pv.hueDeg.toInt()}°" else "",
            r.transitionS, if (r.transition < 1.0) " · a misturar %.0f%%".format(r.transition * 100) else "",
        )).append('\n')
        val src = r.signalSource
        sb.append(if (src == 0) "sinal: sintético" else "sinal: %s · %.0f pkt/s · peso %.2f · %s".format(
            SignalHub.kindName(src), packetRate, r.signalWeight,
            if (r.mixer.realBpm > 0) "%.1f bpm".format(r.mixer.realBpm) else "sem tempo",
        )).append(" · reatividade %.1f".format(r.mixer.reactivity)).append('\n')
        sb.append("versão: ").append(if (r.react > 0f) "reativa" else "atual").append('\n')
        renderer.images?.let { st ->
            val p = imageProvider
            sb.append("imagem: ").append(st.shown?.credit()?.take(60) ?: "-").append(" · cache ").append(p?.cached ?: 0)
                .append(" · ").append(p?.status ?: "").append('\n')
        }
        r.lastError?.let { sb.append("ERRO: ").append(it).append('\n') }
        sb.append("◀ ▶ cena · ▲ versão · OK overlay")
        return sb.toString()
    }

    private fun lookName() = if (renderer.react > 0f) "reactive" else "current"

    /** Random, kept across restarts: the beacon's deviceId (PROTOCOLO-SIGNALS.md). */
    private fun deviceId(): Int {
        val prefs = context.getSharedPreferences("santavibes", Context.MODE_PRIVATE)
        if (!prefs.contains("deviceId")) prefs.edit().putInt("deviceId", java.util.Random().nextInt()).apply()
        return prefs.getInt("deviceId", 0)
    }

    private fun allocatedBytes(): Long = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0L

    companion object {
        const val DEFAULT_REACTIVITY = 0.7
        /** The home server's image folder (NOTES.md, 2.1); DABS fills it. */
        const val DEFAULT_IMAGE_SERVER = "http://192.168.1.200/santa-vibes/"
    }
}
