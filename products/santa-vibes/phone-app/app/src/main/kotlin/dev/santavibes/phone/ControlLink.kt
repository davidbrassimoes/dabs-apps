package dev.santavibes.phone

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * "Abrir controlo": where the TV's control page is. The address typed in the app wins;
 * otherwise wait up to [WAIT_MS] for the TV's beacon (PROTOCOLO-SIGNALS.md), which it sends
 * about once a second while the Santa Vibes app is open on the TV. Blocking: call off the UI thread.
 */
object ControlLink {
    /** The control server's port on the TV (tv-app ControlServer.PORT). */
    const val PORT = 8080
    private const val WAIT_MS = 3000

    /** The page address, or null when the TV did not answer (app closed on the TV, or another network). */
    fun find(context: Context, typed: String): String? {
        typed.trim().takeIf { it.isNotEmpty() }?.let { return url(it) }
        val lock = context.getSystemService(WifiManager::class.java)?.createMulticastLock("santavibes-control")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(Sender.BEACON_PORT)); soTimeout = 500 }.use { s ->
                val b = ByteArray(64)
                val p = DatagramPacket(b, b.size)
                val end = System.currentTimeMillis() + WAIT_MS
                while (System.currentTimeMillis() < end) {
                    try {
                        s.receive(p)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    if (p.length == 16 && b[0] == 'P'.code.toByte() && b[1] == 'N'.code.toByte() && b[2] == 'M'.code.toByte() && b[3] == 'B'.code.toByte()) {
                        return url(p.address.hostAddress ?: continue)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(Sender.TAG, "control: no beacon: $e")
        } finally {
            lock?.release()
        }
        return null
    }

    private fun url(host: String) = "http://$host:$PORT/"
}
