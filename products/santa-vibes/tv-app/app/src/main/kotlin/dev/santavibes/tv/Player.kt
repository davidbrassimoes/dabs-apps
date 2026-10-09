package dev.santavibes.tv

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.widget.FrameLayout
import dev.santavibes.core.ImageSources

/**
 * Everything that puts scenes on screen, kept out of MainActivity so the control server (3.2)
 * can drive the same object the remote does: scene library, renderer, signal hub, image provider, GL view with vsync pacing
 * by Choreographer at the scene's fps cap (RENDERMODE_WHEN_DIRTY), diagnostic overlay and the
 * logcat summary. The host only builds the Options, adds [view] and calls start/stop.
 */
class Player(private val context: Context, private val o: Options) {

    /**
     * Host settings from MainActivity's intent extras. Null = not given: the saved state (3.4)
     * decides. Given ones win for this run only.
     */
    data class Options(
        val sceneId: String? = null,
        val bench: Benchmark.Config? = null,
        val transitionS: Double? = null,
        val fadeEvery: Int? = null,
        val drift: Double? = null,
        val driftSpeed: Double? = null,
        val source: String? = null,
        val reactivity: Double? = null,
        val reactive: Boolean? = null,
        val images: String? = null, // "off" = no image provider at all
        val imageServer: String = DEFAULT_IMAGE_SERVER,
        val imageHoldS: Double? = null,
        val rotate: Boolean? = null,
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

    /** The control page and the remote both go through it (3.2–3.4); null while benchmarking. */
    private val controller: Controller?
    private val server: ControlServer?

    private val glView: GLSurfaceView
    private val overlay = Overlay(context)
    /** The view to show fullscreen. */
    val view: FrameLayout

    init {
        renderer.bench = o.bench
        o.transitionS?.let { renderer.transitionS = it }
        o.fadeEvery?.let { renderer.fadeEvery = it }
        o.drift?.let { renderer.driftAmount = it }
        o.driftSpeed?.let { renderer.driftSpeed = it }
        if (o.images != "off") {
            val p = ImageProvider(java.io.File(context.filesDir, "images"), ImageSources.ALL.toSet(), ImageFetchers(o.imageServer))
            imageProvider = p
            renderer.images = ImageStage(p).apply { o.imageHoldS?.let { holdS = it } }
        }
        renderer.paletteVariation.apply {
            rotate = true // box default (DABS, 2026-10-07); the saved state decides from here on
            o.holdS?.let { holdS = it }
            o.fadeS?.let { fadeS = it }
            o.hueDeg?.let { hueDeg = it }
        }
        if (benchMode) {
            renderer.initialSceneId = o.sceneId
            controller = null
            server = null
        } else {
            renderer.hub = hub
            val c = Controller(library, renderer, hub, imageProvider, context.filesDir)
            c.override(overrides())
            c.applyAll()
            renderer.initialSceneId = c.sceneId()
            controller = c
            val routes = ControlRoutes(context.assets, library, c, ::live)
            server = ControlServer(ControlServer.PORT, routes::handle)
        }

        glView = GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 8, 0, 0)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = if (benchMode) GLSurfaceView.RENDERMODE_CONTINUOUSLY else GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
        view = FrameLayout(context).apply { addView(glView) }
        overlay.addTo(view)
        Log.i(SceneLibrary.TAG, "${library.scenes.size} scenes: ${library.scenes.joinToString { it.def.id }}")
    }

    /** Intent extras as a control patch (only the ones given). */
    private fun overrides(): Map<String, Any?> = buildMap {
        o.sceneId?.let { put("scene", it) }
        o.reactive?.let { put("look", if (it) "reactive" else "current") }
        o.source?.let { put("source", it) }
        o.reactivity?.let { put("reactivity", it) }
        o.images?.let { sel -> ImageSources.parseSelection(sel)?.let { put("images", it.toList()) } }
        o.rotate?.let { put("paletteRotate", it) }
    }

    fun start() {
        glView.onResume()
        running = true
        nextDueNs = 0L
        if (!benchMode) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
            hub.start()
            imageProvider?.start()
            server?.start()
        }
        ui.post(tick)
    }

    fun stop() {
        running = false
        ui.removeCallbacks(tick)
        server?.stop()
        hub.stop()
        imageProvider?.stop()
        glView.onPause()
    }

    // ---- remote actions (the same path as the control page)

    fun step(delta: Int) {
        controller?.step(delta) ?: renderer.step(delta)
        if (overlay.visible) overlay.update(overlayText())
    }

    fun toggleLook() {
        controller?.toggleLook() ?: run { renderer.react = 1f - renderer.react }
        Log.i(SceneLibrary.TAG, "look=${lookName()}")
        if (overlay.visible) overlay.update(overlayText())
    }

    fun toggleOverlay() = overlay.toggle(overlayText(), ControlServer.url())

    /** Live status for the control page (HTTP thread; reads volatile fields only). */
    private fun live(): Map<String, Any?> = linkedMapOf(
        "source" to SignalHub.kindName(renderer.signalSource),
        "packetsPerS" to packetRate.toDouble(),
        "bpm" to renderer.mixer.realBpm,
        "weight" to renderer.signalWeight,
        "image" to renderer.images?.shown?.credit(),
        "imagesCached" to (imageProvider?.cached ?: 0),
        "fps" to renderer.stats.fps.toDouble(),
    )

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
            if (overlay.visible) overlay.update(overlayText())
            controller?.tick(System.currentTimeMillis())
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
        /** The home server's image folder (NOTES.md, 2.1); DABS fills it. */
        const val DEFAULT_IMAGE_SERVER = "http://192.168.1.200/santa-vibes/"
    }
}
