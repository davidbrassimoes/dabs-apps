package dev.santavibes.phone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The whole UI: open the TV's control page, two ways to send, stop, an optional TV address,
 * and the state. Built in
 * code (no layouts, no AndroidX). Microphone = the sound in the room; "som do telemóvel" =
 * what other apps play (Android asks for consent each session; some apps block capture).
 */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var tv: EditText
    private val ui = Handler(Looper.getMainLooper())
    private var pendingKind = 0
    /** True while "Abrir controlo" looks for the TV. */
    @Volatile private var searching = false

    private val tick = object : Runnable {
        override fun run() {
            status.text = statusText()
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply { text = label; setOnClickListener { onClick() } }
        root.addView(button("Abrir controlo") { openControl() })
        root.addView(TextView(this).apply { text = "Enviar sinais para a TV"; textSize = 22f; gravity = Gravity.START; setPadding(0, pad, 0, 0) })
        root.addView(button("Microfone (som da sala)") { begin(SignalsNative.KIND_MIC) })
        root.addView(button("Som do telemóvel") { begin(SignalsNative.KIND_PLAYBACK) })
        root.addView(button("Parar") { CaptureService.stop(this) })
        tv = EditText(this).apply {
            hint = "IP da TV (opcional; normalmente encontra-a sozinho)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            setText(getPreferences(MODE_PRIVATE).getString("tv", ""))
        }
        root.addView(tv)
        status = TextView(this).apply { typeface = Typeface.MONOSPACE; setPadding(0, pad, 0, 0) }
        root.addView(status)
        // Android 15+ draws apps edge to edge: keep the content clear of the system bars.
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            insets
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        super.onPause()
    }

/** The control page in the browser; finding the TV can take a few seconds, so off the UI thread. */
    private fun openControl() {
        Status.error = null
        searching = true
        status.text = statusText()
        val typed = tv.text.toString()
        Thread {
            val url = ControlLink.find(this, typed)
            ui.post {
                searching = false
                if (url == null) Status.error = "Não encontrei a TV. Abre a app Santa Vibes na TV e confirma que o telemóvel está no Wi-Fi de casa."
                else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }.start()
    }

    private fun begin(kind: Int) {
        getPreferences(MODE_PRIVATE).edit().putString("tv", tv.text.toString().trim()).apply()
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        pendingKind = kind
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
            return
        }
        afterPermissions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISSIONS) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Status.error = "Sem autorização para o som não há sinais."
            return
        }
        afterPermissions()
    }

    private fun afterPermissions() {
        CaptureService.stop(this)
        if (pendingKind == SignalsNative.KIND_PLAYBACK) {
            @Suppress("DEPRECATION")
            startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), REQ_PROJECTION)
        } else {
            startCapture(SignalsNative.KIND_MIC, null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PROJECTION) return
        if (resultCode != RESULT_OK || data == null) {
            Status.error = "Captura do som do telemóvel recusada."
            return
        }
        startCapture(SignalsNative.KIND_PLAYBACK, Intent().putExtra(CaptureService.EXTRA_PROJECTION_CODE, resultCode).putExtra(CaptureService.EXTRA_PROJECTION_DATA, data))
    }

    private fun startCapture(kind: Int, extras: Intent?) {
        val i = Intent(this, CaptureService::class.java).putExtra(CaptureService.EXTRA_KIND, kind).putExtra(CaptureService.EXTRA_TV, tv.text.toString())
        extras?.extras?.let { i.putExtras(it) }
        // The previous capture (if any) is stopping; start the new one right after.
        ui.postDelayed({ startForegroundService(i) }, 300)
    }

    private fun statusText(): String {
        val s = Status
        val sb = StringBuilder()
        if (searching) sb.append("À procura da TV para abrir o controlo…\n")
        if (!s.active) sb.append("Parado.\n") else {
            sb.append(if (s.kind == SignalsNative.KIND_PLAYBACK) "A enviar: som do telemóvel" else "A enviar: microfone")
            sb.append(" · ").append(s.sampleRate).append(" Hz, ").append(s.channels).append(" canal(is)\n")
            sb.append(if (s.tvCount == 0) "À procura da TV…\n" else "TV: ${s.tvs()}\n")
            sb.append("pacotes enviados: ").append(s.sent).append('\n')
            sb.append(if (s.silent) "silêncio\n" else "energia %.2f · graves %.2f · %s\n".format(s.energy, s.low, if (s.tempoValid) "%.1f bpm".format(s.bpm) else "sem tempo"))
        }
        s.error?.let { sb.append("Erro: ").append(it).append('\n') }
        return sb.toString()
    }

    companion object {
        private const val REQ_PERMISSIONS = 1
        private const val REQ_PROJECTION = 2
    }
}
