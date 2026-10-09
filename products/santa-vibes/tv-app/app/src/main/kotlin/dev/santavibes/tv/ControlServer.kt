package dev.santavibes.tv

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * Minimal HTTP/1.1 server for the control page (3.2), no dependency (ADR 0002): one thread,
 * one request per connection (`Connection: close`), bodies up to 64 KB. Low traffic by design:
 * one phone, a few requests a second at most. No login (decision 18: the home network).
 */
class ControlServer(private val port: Int, private val handle: (Request) -> Response) {
    class Request(val method: String, val path: String, val body: String)

    class Response(val status: Int, val type: String, val body: ByteArray) {
        companion object {
            fun json(text: String, status: Int = 200) = Response(status, "application/json; charset=utf-8", text.toByteArray())
            fun text(text: String, status: Int) = Response(status, "text/plain; charset=utf-8", text.toByteArray())
        }
    }

    @Volatile private var socket: ServerSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (thread != null) return
        thread = Thread({ loop() }, "control-http").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        thread = null
    }

    private fun loop() {
        val s = try {
            ServerSocket(port).also { socket = it }
        } catch (e: Exception) {
            Log.w(SceneLibrary.TAG, "control: cannot listen on $port: $e")
            return
        }
        Log.i(SceneLibrary.TAG, "control: listening on ${url(port) ?: ":$port"}")
        while (!s.isClosed) {
            val c = try { s.accept() } catch (e: Exception) { break }
            try {
                c.soTimeout = 5000
                serve(c)
            } catch (e: Exception) {
                Log.w(SceneLibrary.TAG, "control: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                runCatching { c.close() }
            }
        }
    }

    private fun serve(c: Socket) {
        val input = BufferedInputStream(c.getInputStream())
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return respond(c, Response.text("bad request", 400))
        var length = 0
        for (i in 0 until MAX_HEADERS) {
            val h = readLine(input) ?: return
            if (h.isEmpty()) break
            val colon = h.indexOf(':')
            if (colon > 0 && h.substring(0, colon).trim().equals("content-length", ignoreCase = true)) {
                length = h.substring(colon + 1).trim().toIntOrNull() ?: 0
            }
        }
        if (length > MAX_BODY) return respond(c, Response.text("body too large", 413))
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        val path = parts[1].substringBefore('?')
        val res = try {
            handle(Request(parts[0].uppercase(), path, String(body, 0, read, Charsets.UTF_8)))
        } catch (e: Exception) {
            Log.w(SceneLibrary.TAG, "control: ${parts[0]} $path failed: $e")
            Response.text("erro: ${e.message}", 500)
        }
        respond(c, res)
    }

    private fun respond(c: Socket, r: Response) {
        val head = "HTTP/1.1 ${r.status} ${reason(r.status)}\r\nContent-Type: ${r.type}\r\nContent-Length: ${r.body.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        val out = c.getOutputStream()
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(r.body)
        out.flush()
    }

    /** One CRLF- or LF-terminated line in ASCII/UTF-8, at most 8 KB; null at end of stream. */
    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 8192) return null
        }
        return buf.toString("UTF-8")
    }

    private fun reason(s: Int) = when (s) {
        200 -> "OK"; 201 -> "Created"; 400 -> "Bad Request"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
        413 -> "Payload Too Large"; else -> "Error"
    }

    companion object {
        const val PORT = 8080
        private const val MAX_HEADERS = 100
        private const val MAX_BODY = 64 * 1024

        /** The page address on the home network (first private IPv4 address), or null without network. */
        fun url(port: Int = PORT): String? {
            val addr = runCatching {
                NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { a: InetAddress -> a is java.net.Inet4Address && a.isSiteLocalAddress }
            }.getOrNull() ?: return null
            return "http://${addr.hostAddress}:$port/"
        }
    }
}
