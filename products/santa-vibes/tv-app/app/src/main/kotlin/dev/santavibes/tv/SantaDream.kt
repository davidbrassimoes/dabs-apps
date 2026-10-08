package dev.santavibes.tv

import android.service.dreams.DreamService

/**
 * The system screensaver (step 3.1): the same [Player] as the app, with the box defaults and a
 * random scene. Not interactive: any key ends the dream, as screensavers do.
 *
 * Pick it on the box: Settings › System › Ambient mode / Screensaver, or by adb:
 *   settings put secure screensaver_components dev.santavibes.tv/.SantaDream
 */
class SantaDream : DreamService() {
    private var player: Player? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val p = Player(this, Player.Options())
        p.renderer.initialSceneId = p.library.scenes.randomOrNull()?.def?.id
        player = p
        setContentView(p.view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        player?.start()
    }

    override fun onDreamingStopped() {
        player?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        player = null
        super.onDetachedFromWindow()
    }
}
