package dev.santavibes.tv

import android.util.Log

/** Frame pacing during a scene crossfade (step 0.6 measurements), logged when the fade ends. */
class FadePacing {
    var frames = 0; private set
    private var intervalSum = 0.0
    private var intervalMax = 0f
    private var cpuMax = 0f

    fun reset() {
        frames = 0
        intervalSum = 0.0
        intervalMax = 0f
        cpuMax = 0f
    }

    fun record(intervalMs: Float, cpuMs: Float) {
        frames++
        intervalSum += intervalMs
        if (intervalMs > intervalMax) intervalMax = intervalMs
        if (cpuMs > cpuMax) cpuMax = cpuMs
    }

    fun log(from: String, to: String?) {
        if (frames == 0) return
        Log.i(
            SceneLibrary.TAG,
            "transition $from -> $to: frames=%d interval avg=%.2f max=%.2f ms cpu max=%.2f ms".format(
                java.util.Locale.ROOT, frames, intervalSum / frames, intervalMax, cpuMax,
            ),
        )
    }
}
