package dev.santavibes.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.santavibes.core.QrCode

/**
 * What OK shows over the scene: the diagnostic text (top left) and the control page's QR code
 * with its address (bottom right, 3.2). Built on the UI thread when shown, never per frame.
 */
class Overlay(context: Context) {
    private val text = TextView(context).apply {
        typeface = Typeface.MONOSPACE
        textSize = 14f
        setTextColor(Color.WHITE)
        setBackgroundColor(0x99000000.toInt())
        setPadding(24, 16, 24, 16)
        visibility = View.GONE
    }
    private val qr = ImageView(context).apply { setBackgroundColor(Color.WHITE); setPadding(16, 16, 16, 16) }
    private val qrLabel = TextView(context).apply {
        typeface = Typeface.MONOSPACE
        textSize = 13f
        setTextColor(Color.BLACK)
        gravity = Gravity.CENTER
    }
    private val qrBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(8, 8, 8, 12)
        addView(qr)
        addView(TextView(context).apply {
            text = "Controlar no telemóvel"
            textSize = 15f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        })
        addView(qrLabel)
        visibility = View.GONE
    }
    private var qrUrl: String? = null

    val visible: Boolean get() = text.visibility == View.VISIBLE

    fun addTo(root: FrameLayout) {
        root.addView(text, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            setMargins(32, 32, 32, 32)
        })
        root.addView(qrBox, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(48, 48, 48, 48)
        })
    }

    /** Show or hide; `url` is the control page address (null = no network, no QR code). */
    fun toggle(content: String, url: String?) {
        val show = !visible
        text.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            text.text = content
            if (url != qrUrl) {
                qrUrl = url
                if (url != null) qr.setImageBitmap(bitmap(QrCode.encode(url)))
                qrLabel.text = url ?: ""
            }
        }
        qrBox.visibility = if (show && url != null) View.VISIBLE else View.GONE
    }

    fun update(content: String) {
        if (visible) text.text = content
    }

    private fun bitmap(code: QrCode): Bitmap {
        val scale = 8
        val quiet = 4 // light border required around the code, in modules
        val n = code.size + quiet * 2
        val px = IntArray(n * n) { i ->
            val x = i % n - quiet
            val y = i / n - quiet
            if (x in 0 until code.size && y in 0 until code.size && code.get(x, y)) Color.BLACK else Color.WHITE
        }
        val small = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(small, n * scale, n * scale, false)
    }
}
