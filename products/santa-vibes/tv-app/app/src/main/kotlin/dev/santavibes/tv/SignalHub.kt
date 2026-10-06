package dev.santavibes.tv

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructTimeval
import android.util.Log
import dev.santavibes.core.SequenceGate
import dev.santavibes.core.SignPacket
import dev.santavibes.core.SignalMixer
import java.io.FileDescriptor
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Signal reception (ROADMAP 1.3, docs/PROTOCOLO-SIGNALS.md): a thread receives `SIGN`
 * packets on UDP 47800 and keeps the latest one per source kind; another broadcasts the
 * `PNMB` beacon every 2 s so senders find the TV. The GL thread calls [drain] each frame
 * to feed the chosen source into the [SignalMixer].
 *
 * Receiving allocates nothing per packet: `Os.recvfrom` into a fixed buffer without asking
 * for the sender address (a DatagramSocket would create an InetAddress every packet).
 */
class SignalHub(private val deviceId: Int, private val controlPort: Int = 0) {
    /** Source choice: [AUTO], or one SignPacket.SOURCE_* kind, or [NONE]. */
    @Volatile var mode = AUTO

    // ---- receiver thread -> GL thread, guarded by [lock]
    private val lock = Any()
    private val mail = Array(KINDS) { SignPacket() }
    private val mailCount = LongArray(KINDS)
    private val lastNs = LongArray(KINDS)
    private val lastSoundNs = LongArray(KINDS)

    /** Packets accepted per kind (UI thread reads, for rates). */
    val received = LongArray(KINDS)
    @Volatile var dropped = 0L; private set

    // ---- GL thread
    private val glCount = LongArray(KINDS)
    private val glPacket = SignPacket()

    @Volatile private var running = false
    private var fd: FileDescriptor? = null
    private var rx: Thread? = null
    private var beacon: Thread? = null

    fun start() {
        if (running) return
        running = true
        val s = try {
            Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_UDP).also {
                Os.setsockoptInt(it, OsConstants.SOL_SOCKET, OsConstants.SO_REUSEADDR, 1)
                Os.setsockoptTimeval(it, OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(500))
                Os.bind(it, InetAddress.getByName("0.0.0.0"), SignPacket.SIGNAL_PORT)
            }
        } catch (e: Exception) {
            Log.e(SceneLibrary.TAG, "signals: cannot listen on UDP ${SignPacket.SIGNAL_PORT}: $e")
            null
        }
        fd = s
        if (s != null) rx = Thread({ receiveLoop(s) }, "signals-rx").apply { isDaemon = true; start() }
        beacon = Thread({ beaconLoop() }, "signals-beacon").apply { isDaemon = true; start() }
        Log.i(SceneLibrary.TAG, "signals: listening on UDP ${SignPacket.SIGNAL_PORT}, beacon id %08x".format(deviceId))
    }

    fun stop() {
        running = false
        fd?.let { runCatching { Os.close(it) } }
        fd = null
        rx?.interrupt(); beacon?.interrupt()
        rx = null; beacon = null
    }

    private fun receiveLoop(s: FileDescriptor) {
        val buf = ByteArray(128)
        val p = SignPacket()
        val gates = Array(KINDS) { SequenceGate() }
        while (running) {
            val n = try {
                Os.recvfrom(s, buf, 0, buf.size, 0, null)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) continue
                if (running) Log.w(SceneLibrary.TAG, "signals: receive failed: $e")
                break
            }
            if (!p.decode(buf, n) || p.kind !in 1 until KINDS) {
                dropped++
                continue
            }
            val now = System.nanoTime()
            val k = p.kind
            synchronized(lock) {
                if (!gates[k].accept(p.sequence, (now - lastNs[k]) / 1e9)) {
                    dropped++
                } else {
                    mail[k].copyFrom(p)
                    mailCount[k]++
                    lastNs[k] = now
                    if (!p.silent) lastSoundNs[k] = now
                    received[k]++
                }
            }
        }
    }

    private fun beaconLoop() {
        val bytes = ByteArray(SignPacket.BEACON_LEN)
        SignPacket.encodeBeacon(bytes, SignPacket.SIGNAL_PORT, controlPort, deviceId)
        try {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                val pkt = DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), SignPacket.BEACON_PORT)
                while (running) {
                    runCatching { sock.send(pkt) }.onFailure { Log.w(SceneLibrary.TAG, "signals: beacon failed: $it") }
                    Thread.sleep(2000)
                }
            }
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            Log.e(SceneLibrary.TAG, "signals: beacon stopped: $e")
        }
    }

    /**
     * GL thread, once per frame: pick the source and hand its newest packet (if any) to
     * the mixer. Returns the chosen kind, or 0 for none. No allocation.
     */
    fun drain(mixer: SignalMixer, nowNs: Long): Int {
        val k = choose(nowNs)
        if (k == 0) return 0
        var fresh = false
        synchronized(lock) {
            if (mailCount[k] != glCount[k]) {
                glCount[k] = mailCount[k]
                glPacket.copyFrom(mail[k])
                fresh = true
            }
        }
        if (fresh) mixer.onPacket(glPacket)
        return k
    }

    private fun choose(nowNs: Long): Int {
        val m = mode
        if (m == NONE) return 0
        if (m != AUTO) return m
        // Auto: the first kind in PRIORITY that is sending sound; else the first sending at all.
        var fallback = 0
        synchronized(lock) {
            for (k in PRIORITY) {
                if (lastNs[k] == 0L || nowNs - lastNs[k] > PRESENT_NS) continue
                if (nowNs - lastSoundNs[k] < SOUND_NS) return k
                if (fallback == 0) fallback = k
            }
        }
        return fallback
    }

    companion object {
        const val AUTO = -1
        const val NONE = 0
        private const val KINDS = 5
        private val PRIORITY = intArrayOf(SignPacket.SOURCE_PC, SignPacket.SOURCE_PHONE_PLAYBACK, SignPacket.SOURCE_PHONE_MIC, SignPacket.SOURCE_MIDI)
        private const val PRESENT_NS = (SignalMixer.ABSENT_S * 1e9).toLong()
        private const val SOUND_NS = (SignalMixer.SILENT_S * 1e9).toLong()

        fun kindName(k: Int) = when (k) {
            SignPacket.SOURCE_PC -> "pc"
            SignPacket.SOURCE_PHONE_MIC -> "mic"
            SignPacket.SOURCE_PHONE_PLAYBACK -> "phone"
            SignPacket.SOURCE_MIDI -> "midi"
            else -> "none"
        }

        fun parseMode(s: String?): Int = when (s) {
            null, "auto" -> AUTO
            "pc" -> SignPacket.SOURCE_PC
            "mic" -> SignPacket.SOURCE_PHONE_MIC
            "phone" -> SignPacket.SOURCE_PHONE_PLAYBACK
            "midi" -> SignPacket.SOURCE_MIDI
            else -> NONE
        }
    }
}
