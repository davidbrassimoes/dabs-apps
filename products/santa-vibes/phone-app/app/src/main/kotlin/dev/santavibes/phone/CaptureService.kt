package dev.santavibes.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log

/**
 * Foreground service that captures audio (microphone, or what other apps play) and sends
 * SIGN packets: an audio thread reads float PCM into signals-core, a sender thread takes a
 * packet at 60 Hz (10 Hz in silence, as the protocol allows) and sends it to every TV.
 * The capture loop allocates nothing after start.
 */
class CaptureService : Service() {
    private var record: AudioRecord? = null
    private var projection: MediaProjection? = null
    private var sender: Sender? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var handle = 0L
    @Volatile private var running = false
    private val threads = ArrayList<Thread>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        val kind = intent?.getIntExtra(EXTRA_KIND, SignalsNative.KIND_MIC) ?: SignalsNative.KIND_MIC
        val type = if (kind == SignalsNative.KIND_PLAYBACK) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        startForeground(NOTIFICATION_ID, notification(kind), type)
        try {
            record = buildRecord(kind, intent)
            start(kind, intent?.getStringExtra(EXTRA_TV))
        } catch (e: Exception) {
            Log.e(Sender.TAG, "capture failed", e)
            Status.error = e.message ?: e.toString()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun buildRecord(kind: Int, intent: Intent?): AudioRecord {
        val channels = if (kind == SignalsNative.KIND_PLAYBACK) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(SAMPLE_RATE).setChannelMask(channels).build()
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, channels, AudioFormat.ENCODING_PCM_FLOAT)
        val b = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(maxOf(minBuf * 2, 16384))
        if (kind == SignalsNative.KIND_PLAYBACK) {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            @Suppress("DEPRECATION")
            val data = intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA) ?: error("no screen-capture consent")
            val p = mpm.getMediaProjection(intent.getIntExtra(EXTRA_PROJECTION_CODE, 0), data) ?: error("consent refused")
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() = stopSelf()
            }, null)
            projection = p
            val cfg = AudioPlaybackCaptureConfiguration.Builder(p)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            b.setAudioPlaybackCaptureConfig(cfg)
        } else {
            val am = getSystemService(AudioManager::class.java)
            val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            // UNPROCESSED skips voice processing (noise suppression, AGC) that flattens music.
            b.setAudioSource(if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC)
        }
        val r = b.build()
        check(r.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord did not initialise" }
        return r
    }

    private fun start(kind: Int, tv: String?) {
        val r = record!!
        val ch = r.channelCount
        handle = SignalsNative.create(r.sampleRate, LOOKAHEAD_MS)
        multicast = getSystemService(WifiManager::class.java)?.createMulticastLock("santavibes-beacon")?.apply {
            setReferenceCounted(false); acquire()
        }
        val s = Sender(tv).also { it.startBeaconListener() }
        sender = s
        running = true
        Status.reset(kind, r.sampleRate, ch)
        r.startRecording()

        threads += Thread({
            val buf = FloatArray(r.sampleRate / 100 * ch) // 10 ms blocks
            while (running) {
                val n = r.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                if (n > 0) SignalsNative.push(handle, buf, n, ch)
                else if (n < 0) { Status.error = "AudioRecord.read: $n"; break }
            }
        }, "capture").apply { priority = Thread.MAX_PRIORITY; start() }

        threads += Thread({
            var seq = 0
            val t0 = SystemClock.elapsedRealtime()
            var next = SystemClock.uptimeMillis()
            while (running) {
                val flags = SignalsNative.packet(handle, kind, seq++, (SystemClock.elapsedRealtime() - t0).toInt(), s.packet)
                val silent = flags and SignalsNative.FLAG_SILENCE != 0
                Status.update(s.packet, flags, s.send(System.currentTimeMillis()), s.tvCount)
                next += if (silent) 100 else 16 // ~60 Hz (62.5) / 10 Hz
                val wait = next - SystemClock.uptimeMillis()
                if (wait > 0) Thread.sleep(wait) else next = SystemClock.uptimeMillis()
            }
        }, "sender").apply { start() }
        Status.tvs = { s.describeTvs() }
        Log.i(Sender.TAG, "capturing ${if (kind == SignalsNative.KIND_PLAYBACK) "playback" else "microphone"} at ${r.sampleRate} Hz, $ch ch")
    }

    override fun onDestroy() {
        running = false
        runCatching { record?.stop() }
        threads.forEach { runCatching { it.join(500) } }
        threads.clear()
        record?.release(); record = null
        projection?.stop(); projection = null
        sender?.close(); sender = null
        multicast?.release(); multicast = null
        if (handle != 0L) SignalsNative.destroy(handle)
        handle = 0
        Status.active = false
        super.onDestroy()
    }

    private fun notification(kind: Int): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Captura de som", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle(if (kind == SignalsNative.KIND_PLAYBACK) "A enviar o som do telemóvel" else "A enviar o som do microfone")
            .setContentText("Só sinais (bandas, ritmo) para a TV; nada é gravado.")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Parar", stop).build())
            .setOngoing(true)
            .build()
    }

    companion object {
        const val SAMPLE_RATE = 48000
        const val LOOKAHEAD_MS = 60f
        const val EXTRA_KIND = "kind"
        const val EXTRA_TV = "tv"
        const val EXTRA_PROJECTION_CODE = "projectionCode"
        const val EXTRA_PROJECTION_DATA = "projectionData"
        const val ACTION_STOP = "dev.santavibes.phone.STOP"
        private const val CHANNEL = "capture"
        private const val NOTIFICATION_ID = 1

        fun stop(c: Context) {
            c.startService(Intent(c, CaptureService::class.java).setAction(ACTION_STOP))
        }
    }
}
