package com.jarvis.launcher

import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** The TV's address on the local network (Wi-Fi or Ethernet), or null if it has none. */
fun localIpAddress(): String? = try {
    val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
    interfaces
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
} catch (e: Exception) {
    null
}

/**
 * A tiny web server so you can send a wallpaper from your phone's browser.
 * It only runs while the "Add wallpaper from my phone" dialog is open, and the
 * address contains a random code so other people on your Wi-Fi can't guess it.
 */
class UploadServer(
    private val token: String,
    private val onUpload: (InputStream) -> Boolean
) {
    @Volatile
    private var server: ServerSocket? = null

    /** Returns the port in use, or 0 if no port was free. */
    fun start(): Int {
        for (port in 8080..8090) {
            try {
                val s = ServerSocket(port)
                server = s
                thread(name = "wallpaper-server", isDaemon = true) { acceptLoop(s) }
                return port
            } catch (e: IOException) {
                // port busy, try the next one
            }
        }
        return 0
    }

    fun stop() {
        try {
            server?.close()
        } catch (e: IOException) {
            // already closed
        }
        server = null
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            try {
                val client = s.accept()
                thread(isDaemon = true) { client.use { handle(it) } }
            } catch (e: IOException) {
                break
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 30_000
            val input = BufferedInputStream(socket.getInputStream())
            val out = socket.getOutputStream()

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].substringBefore('?')

            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: return
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
            }

            when {
                method == "GET" && path == "/$token" ->
                    respond(out, 200, "text/html; charset=utf-8", page())

                method == "POST" && path == "/$token/upload" -> {
                    val length = headers["content-length"]?.toLongOrNull() ?: -1L
                    if (length <= 0L || length > MAX_BYTES) {
                        respond(out, 413, "text/plain; charset=utf-8", "That photo is too big (30 MB max).")
                    } else if (onUpload(LimitedInputStream(input, length))) {
                        respond(out, 200, "text/plain; charset=utf-8", "ok")
                    } else {
                        respond(out, 400, "text/plain; charset=utf-8", "That file isn't a usable image.")
                    }
                }

                else -> respond(out, 404, "text/plain; charset=utf-8", "Not found")
            }
        } catch (e: IOException) {
            // client went away
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 8192) {
            val c = input.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
        return null
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            else -> "Error"
        }
        val head = "HTTP/1.1 $code $status\r\nContent-Type: $type\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun page(): String = PAGE.replace("TOKEN", token)

    /** Reads at most [remaining] bytes from the underlying request body. */
    private class LimitedInputStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = super.read()
            if (b >= 0) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val n = super.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n > 0) remaining -= n
            return n
        }
    }

    private companion object {
        const val MAX_BYTES = 30L * 1024 * 1024

        const val PAGE = """<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Jarvis TV wallpaper</title></head>
<body style="font-family:sans-serif;background:#0b0d12;color:#fff;text-align:center;padding:32px 20px">
<h2>Jarvis TV wallpaper</h2>
<p style="color:#aab">Choose a landscape photo from your phone.</p>
<p><input type="file" id="f" accept="image/*" style="font-size:18px"></p>
<p id="s" style="font-size:20px"></p>
<script>
var f=document.getElementById('f'),s=document.getElementById('s');
f.onchange=function(){
  var x=f.files[0]; if(!x) return;
  s.textContent='Uploading...';
  fetch('/TOKEN/upload',{method:'POST',body:x}).then(function(r){
    return r.text().then(function(t){ s.textContent = r.ok ? 'Done! Look at your TV.' : 'Failed: '+t; });
  }).catch(function(){ s.textContent='Failed. Is your phone on the same Wi-Fi?'; });
};
</script></body></html>"""
    }
}
