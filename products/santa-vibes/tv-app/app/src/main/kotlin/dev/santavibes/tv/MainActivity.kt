package dev.santavibes.tv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Fullscreen renderer. Remote: ◀ ▶ change scene, OK toggles the diagnostic overlay.
 * Frames are paced by Choreographer (vsync) at the scene's fps cap, so the GL thread
 * only renders when asked (RENDERMODE_WHEN_DIRTY).
 *
 * Open a given scene: am start -n dev.santavibes.tv/.MainActivity --es scene nebula
 *
 * Benchmark (step 0.5), from adb:
 *   am start -n dev.santavibes.tv/.MainActivity --ez bench true [--es scales 0.25,0.33,0.4,0.5] [--es scenes aurora,nebula]
 * renders continuously without vsync and logs "bench" lines (see Benchmark).
 *
 * Host settings (step 0.6) until the control page exists (phase 3), all optional:
 *   --ef transition 4     crossfade between scenes in seconds (0 = cut)
 *   --ef drift 1          drift amount (0 = off, 1 = as authored)
 *   --ef driftSpeed 1     drift time rate (1 = real time)
 *   --ez rotate true      rotate through the scene's palettes
 *   --ef hold 180         seconds on each palette; --ef fade 20 palette crossfade seconds
 *   --ef hue 0            hue drift amplitude in degrees (0 = off)
 */
class MainActivity : Activity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: SceneRenderer
    private lateinit var overlay: TextView
    private val ui = Handler(Looper.getMainLooper())
    private var running = false

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
    private val tick = object : Runnable {
        override fun run() {
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
                    "stats scene=$id frames=${s.winFrames} fps=%.1f interval avg=%.2f p95=%.2f max=%.2f ms cpu avg=%.2f p95=%.2f ms render=%dx%d %s cap=%d alloc=%d B/s".format(
                        s.winFps, s.winIntervalAvg, s.winIntervalP95, s.winIntervalMax, s.winCpuAvg, s.winCpuP95,
                        renderer.renderWidth, renderer.renderHeight, if (renderer.halfFloat) "RGBA16F" else "RGBA8",
                        renderer.fpsCap, allocRate,
                    ),
                )
            }
            if (overlay.visibility == View.VISIBLE) overlay.text = overlayText()
            ui.postDelayed(this, 500)
        }
    }

    private lateinit var library: SceneLibrary
    private var benchMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        library = SceneLibrary(assets)
        renderer = SceneRenderer(library, library.profile("tv"))
        renderer.initialSceneId = intent.getStringExtra("scene")
        benchMode = intent.getBooleanExtra("bench", false)
        if (benchMode) {
            val scales = (intent.getStringExtra("scales") ?: "0.25,0.33,0.4,0.5").split(",").map { it.trim().toDouble() }.toDoubleArray()
            val ids = intent.getStringExtra("scenes")?.split(",")?.map { it.trim() }?.toSet()
            renderer.bench = Benchmark.Config(scales, ids)
        }
        renderer.transitionS = intent.getFloatExtra("transition", renderer.transitionS.toFloat()).toDouble()
        renderer.fadeEvery = intent.getIntExtra("fadeEvery", renderer.fadeEvery)
        renderer.driftAmount = intent.getFloatExtra("drift", renderer.driftAmount.toFloat()).toDouble()
        renderer.driftSpeed = intent.getFloatExtra("driftSpeed", renderer.driftSpeed.toFloat()).toDouble()
        renderer.paletteVariation.apply {
            rotate = intent.getBooleanExtra("rotate", rotate)
            holdS = intent.getFloatExtra("hold", holdS.toFloat()).toDouble()
            fadeS = intent.getFloatExtra("fade", fadeS.toFloat()).toDouble()
            hueDeg = intent.getFloatExtra("hue", hueDeg.toFloat()).toDouble()
        }

        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 8, 0, 0)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = if (benchMode) GLSurfaceView.RENDERMODE_CONTINUOUSLY else GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
        overlay = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            setPadding(24, 16, 24, 16)
            visibility = View.GONE
        }
        val root = FrameLayout(this)
        root.addView(glView)
        root.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            setMargins(32, 32, 32, 32)
        })
        setContentView(root)
        Log.i(SceneLibrary.TAG, "${library.scenes.size} scenes: ${library.scenes.joinToString { it.def.id }}")
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        glView.onResume()
        running = true
        nextDueNs = 0L
        if (!benchMode) Choreographer.getInstance().postFrameCallback(frameCallback)
        ui.post(tick)
    }

    override fun onPause() {
        running = false
        ui.removeCallbacks(tick)
        glView.onPause()
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> renderer.step(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> renderer.step(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                overlay.visibility = if (overlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                overlay.text = overlayText()
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
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
        r.lastError?.let { sb.append("ERRO: ").append(it).append('\n') }
        sb.append("◀ ▶ cena · OK overlay")
        return sb.toString()
    }

    private fun allocatedBytes(): Long = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0L

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }
}
