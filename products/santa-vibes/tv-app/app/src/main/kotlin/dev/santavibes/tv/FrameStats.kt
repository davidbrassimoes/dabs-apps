package dev.santavibes.tv

/**
 * Frame timing for the overlay and for step 0.5 (measurement on the box).
 * record() runs on the GL thread every frame and allocates nothing; every [WINDOW_S]
 * seconds it publishes a summary (average and p95) that the UI thread reads and logs.
 */
class FrameStats {
    private val interval = FloatArray(CAP) // ms between frame starts
    private val cpu = FloatArray(CAP) // ms spent in onDrawFrame
    private val scratch = FloatArray(CAP)
    private var n = 0
    private var windowStartNs = 0L

    /** Smoothed live values for the overlay. */
    @Volatile var fps = 0f
    @Volatile var intervalMs = 0f
    @Volatile var cpuMs = 0f

    /** Last published window. [seq] increments on every publish. */
    @Volatile var seq = 0
    @Volatile var winFrames = 0
    @Volatile var winFps = 0f
    @Volatile var winIntervalAvg = 0f
    @Volatile var winIntervalP95 = 0f
    @Volatile var winIntervalMax = 0f
    @Volatile var winCpuAvg = 0f
    @Volatile var winCpuP95 = 0f

    fun reset(nowNs: Long) {
        n = 0
        windowStartNs = nowNs
    }

    fun record(nowNs: Long, intervalMsValue: Float, cpuMsValue: Float) {
        if (intervalMsValue > 0f) {
            intervalMs = if (intervalMs == 0f) intervalMsValue else intervalMs + (intervalMsValue - intervalMs) * 0.1f
            fps = 1000f / intervalMs
        }
        cpuMs = if (cpuMs == 0f) cpuMsValue else cpuMs + (cpuMsValue - cpuMs) * 0.1f
        if (n < CAP && intervalMsValue > 0f) {
            interval[n] = intervalMsValue
            cpu[n] = cpuMsValue
            n++
        }
        if (windowStartNs == 0L) windowStartNs = nowNs
        if (nowNs - windowStartNs >= WINDOW_S * 1_000_000_000L && n > 0) publish(nowNs)
    }

    private fun publish(nowNs: Long) {
        var sumI = 0f
        var sumC = 0f
        var maxI = 0f
        for (i in 0 until n) {
            sumI += interval[i]
            sumC += cpu[i]
            if (interval[i] > maxI) maxI = interval[i]
        }
        winFrames = n
        winIntervalAvg = sumI / n
        winFps = 1000f / winIntervalAvg
        winIntervalMax = maxI
        winCpuAvg = sumC / n
        winIntervalP95 = p95(interval)
        winCpuP95 = p95(cpu)
        seq++
        reset(nowNs)
    }

    private fun p95(src: FloatArray): Float {
        System.arraycopy(src, 0, scratch, 0, n)
        java.util.Arrays.sort(scratch, 0, n)
        return scratch[((n - 1) * 95) / 100]
    }

    companion object {
        const val WINDOW_S = 10
        private const val CAP = 1200 // 10 s at up to 120 fps
    }
}
