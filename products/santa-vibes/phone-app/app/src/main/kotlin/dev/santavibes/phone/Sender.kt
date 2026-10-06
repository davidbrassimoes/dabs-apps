package dev.santavibes.phone

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Where packets go: TVs heard on the beacon port (47801, docs/PROTOCOLO-SIGNALS.md) plus
 * an optional address typed by hand. Same rules as pc-bridge/src/net.rs: a beacon TV is
 * forgotten after 7 s of silence. Sending reuses one DatagramPacket per TV.
 */
class Sender(manual: String?) {
    private class Tv(val packet: DatagramPacket, var seenMs: Long, val manual: Boolean)

    private val lock = Any()
    private val tvs = ArrayList<Tv>()
    private val buf = ByteArray(PACKET_LEN)
    private val socket = DatagramSocket()
    @Volatile private var running = true
    private var beaconThread: Thread? = null

    init {
        manual?.trim()?.takeIf { it.isNotEmpty() }?.let { host ->
            Thread {
                runCatching {
                    val addr = InetSocketAddress(InetAddress.getByName(host), SIGNAL_PORT)
                    synchronized(lock) { tvs += Tv(DatagramPacket(buf, PACKET_LEN, addr), 0, true) }
                }.onFailure { Log.w(TAG, "bad TV address $host: $it") }
            }.start()
        }
    }

    /** The packet bytes to fill before [send]. */
    val packet: ByteArray get() = buf

    val tvCount: Int get() = synchronized(lock) { tvs.size }

    fun describeTvs(): String = synchronized(lock) { tvs.joinToString { it.packet.address.hostAddress ?: "?" } }

    /** Send [packet] to every live TV; drops beacon TVs not heard for 7 s. */
    fun send(nowMs: Long): Int {
        var n = 0
        synchronized(lock) {
            tvs.removeAll { !it.manual && nowMs - it.seenMs > BEACON_TTL_MS }
            for (t in tvs) {
                if (runCatching { socket.send(t.packet) }.isSuccess) n++
            }
        }
        return n
    }

    /** Listen for TV beacons (needs a Wi-Fi multicast lock held by the caller). */
    fun startBeaconListener() {
        beaconThread = Thread({
            val s = runCatching { DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(BEACON_PORT)); soTimeout = 1000 } }
                .getOrElse { Log.w(TAG, "beacon port busy: $it"); return@Thread }
            val b = ByteArray(64)
            val p = DatagramPacket(b, b.size)
            s.use {
                while (running) {
                    try {
                        it.receive(p)
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (e: Exception) {
                        if (running) Log.w(TAG, "beacon: $e")
                        break
                    }
                    if (p.length != 16 || b[0] != 'P'.code.toByte() || b[1] != 'N'.code.toByte() ||
                        b[2] != 'M'.code.toByte() || b[3] != 'B'.code.toByte() || b[4].toInt() != 1
                    ) continue
                    val port = (b[6].toInt() and 0xff) or ((b[7].toInt() and 0xff) shl 8)
                    val addr = InetSocketAddress(p.address, port)
                    val now = System.currentTimeMillis()
                    synchronized(lock) {
                        val known = tvs.firstOrNull { !it.manual && it.packet.socketAddress == addr }
                        if (known != null) known.seenMs = now
                        else {
                            tvs += Tv(DatagramPacket(buf, PACKET_LEN, addr), now, false)
                            Log.i(TAG, "TV found: $addr")
                        }
                    }
                }
            }
        }, "beacon").apply { isDaemon = true; start() }
    }

    fun close() {
        running = false
        beaconThread?.interrupt()
        socket.close()
    }

    companion object {
        const val TAG = "SantaVibesPhone"
        const val PACKET_LEN = 56
        const val SIGNAL_PORT = 47800
        const val BEACON_PORT = 47801
        const val BEACON_TTL_MS = 7000L
    }
}
