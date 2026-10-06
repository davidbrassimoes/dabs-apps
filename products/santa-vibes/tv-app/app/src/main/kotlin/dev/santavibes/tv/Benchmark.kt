package dev.santavibes.tv

import android.util.Log

/**
 * Benchmark (step 0.5), driven from [SceneRenderer.onDrawFrame] on the GL thread. Renders
 * without vsync and walks output-pass-only, then every scene x scale, logging one "bench"
 * line per step. Allocates only between steps.
 */
class Benchmark internal constructor(private val cfg: Config, private val lib: SceneLibrary, private val r: SceneRenderer) {
    class Config(val scales: DoubleArray, val sceneIds: Set<String>?, val warmS: Double = 2.0, val measureS: Double = 4.0)

    @Volatile var done = false; private set

    private val order = lib.scenes.indices.filter { cfg.sceneIds == null || lib.scenes[it].def.id in cfg.sceneIds }.toIntArray()
    private var sceneIdx = -1 // -1 = output pass only
    private var scaleIdx = 0
    private var startNs = 0L
    private var n = 0
    private val intervals = FloatArray(4096)
    private val cpu = FloatArray(4096)

    init {
        r.skipScenePass = true
        Log.i(SceneLibrary.TAG, "bench start: ${order.size} scenes x ${cfg.scales.size} scales, warm ${cfg.warmS}s, measure ${cfg.measureS}s, vsync off")
    }

    fun tick(now: Long, intervalMs: Float, cpuMs: Float) {
        if (startNs == 0L) startNs = now
        val elapsed = (now - startNs) / 1e9
        if (elapsed >= cfg.warmS && intervalMs > 0f && n < intervals.size) {
            intervals[n] = intervalMs
            cpu[n] = cpuMs
            n++
        }
        if (elapsed < cfg.warmS + cfg.measureS) return

        // Step finished: log it, then move on.
        val label = if (sceneIdx < 0) "output-only" else lib.scenes[order[sceneIdx]].def.id
        var sum = 0.0
        var sumCpu = 0.0
        for (i in 0 until n) { sum += intervals[i]; sumCpu += cpu[i] }
        val avg = if (n > 0) sum / n else 0.0
        java.util.Arrays.sort(intervals, 0, n)
        val p95 = if (n > 0) intervals[((n - 1) * 95) / 100] else 0f
        Log.i(
            SceneLibrary.TAG,
            "bench scene=%s scale=%.2f render=%dx%d frames=%d avg=%.2f ms p95=%.2f ms fps=%.1f cpu=%.2f ms".format(
                java.util.Locale.ROOT, label, if (sceneIdx < 0) 0.0 else r.renderScale, r.renderWidth, r.renderHeight,
                n, avg, p95, if (avg > 0) 1000.0 / avg else 0.0, if (n > 0) sumCpu / n else 0.0,
            ),
        )
        n = 0
        startNs = 0L
        if (sceneIdx < 0) {
            r.skipScenePass = false
            sceneIdx = 0
            scaleIdx = 0
        } else if (++scaleIdx >= cfg.scales.size) {
            scaleIdx = 0
            if (++sceneIdx >= order.size) {
                done = true
                r.scaleOverride = 0.0
                Log.i(SceneLibrary.TAG, "bench done")
                return
            }
        }
        val target = order[sceneIdx]
        r.scaleOverride = cfg.scales[scaleIdx]
        if (target != r.sceneIndex) r.loadScene(target, 0.0) else r.applyScaleOverride()
    }
}
