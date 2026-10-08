package dev.santavibes.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager

/**
 * The app: a [Player] fullscreen. Remote: ◀ ▶ change scene, ▲ switches the look, OK toggles the overlay.
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
 * Box defaults (DABS, 2026-10-07): palette rotation on (180 s each, 20 s fade), no hue drift,
 * reactivity 0.7, outgoing scene at half rate during a crossfade.
 *   --ez rotate false     rotate through the scene's palettes (default on)
 *   --ef hold 180         seconds on each palette; --ef fade 20 palette crossfade seconds
 *   --ef hue 0            hue drift amplitude in degrees (0 = off)
 *
 * Signals (step 1.3): the app listens on UDP 47800 and broadcasts its beacon on 47801.
 *   --ef reactivity 0.7   0 = Synth only, 1 = only the real source while it is present
 *   --es source auto      auto | pc | phone | mic | none
 */
class MainActivity : Activity() {
    private lateinit var player: Player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        player = Player(this, options(intent))
        setContentView(player.view)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        player.start()
    }

    override fun onPause() {
        player.stop()
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> player.step(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> player.step(-1)
            KeyEvent.KEYCODE_DPAD_UP -> player.toggleLook()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> player.toggleOverlay()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun options(i: Intent): Player.Options {
        fun f(key: String) = if (i.hasExtra(key)) i.getFloatExtra(key, 0f).toDouble() else null
        val bench = if (i.getBooleanExtra("bench", false)) {
            val scales = (i.getStringExtra("scales") ?: "0.25,0.33,0.4,0.5").split(",").map { it.trim().toDouble() }.toDoubleArray()
            Benchmark.Config(scales, i.getStringExtra("scenes")?.split(",")?.map { it.trim() }?.toSet())
        } else null
        return Player.Options(
            sceneId = i.getStringExtra("scene"),
            bench = bench,
            transitionS = f("transition"),
            fadeEvery = if (i.hasExtra("fadeEvery")) i.getIntExtra("fadeEvery", 2) else null,
            drift = f("drift"),
            driftSpeed = f("driftSpeed"),
            source = i.getStringExtra("source"),
            reactivity = f("reactivity") ?: Player.DEFAULT_REACTIVITY,
            reactive = i.getStringExtra("look") != "current",
            images = i.getStringExtra("images"),
            imageServer = i.getStringExtra("imageServer") ?: Player.DEFAULT_IMAGE_SERVER,
            imageHoldS = f("imageHold"),
            rotate = i.getBooleanExtra("rotate", true),
            holdS = f("hold"),
            fadeS = f("fade"),
            hueDeg = f("hue"),
        )
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }
}
