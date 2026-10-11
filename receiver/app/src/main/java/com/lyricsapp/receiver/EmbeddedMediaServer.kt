package com.lyricsapp.receiver

import android.util.Base64
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Local-only streaming upload/download endpoint. Upload bodies are streamed to cache, never held in RAM. */
class EmbeddedMediaServer(
    private val cacheDir: File,
    private val port: Int = 8766,
    private val onReady: (id: String, type: String) -> Unit,
    private val onError: (reason: String) -> Unit = {}
) {
    companion object { private const val TAG = "MediaHttp"; private const val MAX_BYTES = 2L * 1024 * 1024 * 1024 }
    private val mimeById = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile private var running = false
    private var socket: ServerSocket? = null
    private val workers = Executors.newFixedThreadPool(4)
    fun start() {
        if (running) return
        running = true
        Thread({
            try {
                val listener = ServerSocket(port)
                socket = listener
                while (running) {
                    val client = listener.accept()
                    workers.execute {
                        runCatching { serve(client) }.onFailure { Log.w(TAG, "Request failed", it); runCatching { client.close() } }
                    }
                }
            } catch (e: Exception) { if (running) Log.e(TAG, "Media server stopped", e) }
        }, "lyrics-media-http").apply { isDaemon = true; start() }
    }
    fun stop() { running = false; runCatching { socket?.close() }; workers.shutdownNow() }

    private fun serve(client: Socket) {
        client.use { s ->
            s.soTimeout = 30_000
            val input = BufferedInputStream(s.getInputStream())
            val output = BufferedOutputStream(s.getOutputStream())
            val first = readLine(input) ?: return
            val parts = first.split(' ')
            val method = parts.getOrNull(0) ?: return
            val path = parts.getOrNull(1)?.substringBefore('?') ?: return
            val headers = mutableMapOf<String, String>()
            while (true) { val line = readLine(input) ?: return; if (line.isEmpty()) break; val i = line.indexOf(':'); if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim() }
            if (method == "POST" && path == "/media") { upload(input, output, headers); return }
            if (method == "GET" && path.startsWith("/media/")) { stream(path.substringAfterLast('/'), headers["range"], output); return }
            respond(output, 404, "Not Found", "text/plain", "Not found".toByteArray())
        }
    }

    private fun upload(input: BufferedInputStream, out: BufferedOutputStream, headers: Map<String, String>) {
        val length = headers["content-length"]?.toLongOrNull() ?: -1L
        if (length > MAX_BYTES) { respond(out, 413, "Payload Too Large", "text/plain", "File too large".toByteArray()); return }
        val mime = headers["content-type"]?.substringBefore(';') ?: "application/octet-stream"
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) { respond(out, 415, "Unsupported Media Type", "text/plain", "Only image/video uploads are supported".toByteArray()); return }
        val id = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest((System.nanoTime().toString() + length).toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).take(22)
        val file = File(cacheDir, "media_$id").apply { parentFile?.mkdirs() }
        try {
            file.outputStream().buffered().use { sink ->
                if (headers["transfer-encoding"]?.contains("chunked", true) == true) readChunks(input, sink)
                else {
                    if (length < 0) throw IllegalArgumentException("Missing content length")
                    val buffer = ByteArray(64 * 1024); var left = length
                    while (left > 0) { val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt()); if (n < 0) throw IllegalArgumentException("Upload ended early"); sink.write(buffer, 0, n); left -= n }
                }
            }
            if (!file.exists() || file.length() == 0L || file.length() > MAX_BYTES) throw IllegalArgumentException("Invalid or oversized upload")
            val type = if (mime.startsWith("video/")) "VIDEO" else "IMAGE"
            mimeById[id] = mime
            File(cacheDir, "media_$id.mime").writeText(mime)
            onReady(id, type)
            val body = """{"mediaId":"$id","url":"/media/$id","type":"$type"}""".toByteArray()
            respond(out, 201, "Created", "application/json", body)
        } catch (e: Exception) { file.delete(); onError(e.message ?: "Unable to store media"); respond(out, 507, "Upload Failed", "text/plain", (e.message ?: "Unable to store media").toByteArray()) }
    }

    private fun readChunks(input: BufferedInputStream, out: java.io.OutputStream) {
        var total = 0L
        while (true) {
            val sizeLine = readLine(input) ?: error("Invalid chunk")
            val size = sizeLine.substringBefore(';').trim().toLong(16)
            if (size == 0L) { while (!readLine(input).isNullOrEmpty()) { }; break }
            total += size; require(total <= MAX_BYTES) { "File too large" }
            var left = size; val buf = ByteArray(64 * 1024)
            while (left > 0) { val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (n < 0) error("Incomplete upload"); out.write(buf, 0, n); left -= n }
            readLine(input)
        }
    }

    private fun stream(id: String, range: String?, out: BufferedOutputStream) {
        val file = File(cacheDir, "media_${URLDecoder.decode(id, "UTF-8")}")
        if (!file.isFile) { respond(out, 404, "Not Found", "text/plain", byteArrayOf()); return }
        val total = file.length()
        var start = 0L
        var end = total - 1
        if (range != null) {
            val match = Regex("^bytes=(\\d*)-(\\d*)$").matchEntire(range.trim())
            if (match == null || total == 0L) {
                rejectRange(out, total)
                return
            }
            val first = match.groupValues[1].toLongOrNull()
            val last = match.groupValues[2].toLongOrNull()
            if (first == null && last == null) {
                rejectRange(out, total)
                return
            }
            if (first == null) {
                val suffixLength = last ?: 0L
                if (suffixLength <= 0L) {
                    rejectRange(out, total)
                    return
                }
                start = (total - suffixLength).coerceAtLeast(0L)
            } else {
                if (first >= total || (last != null && last < first)) {
                    rejectRange(out, total)
                    return
                }
                start = first
                end = (last ?: (total - 1)).coerceAtMost(total - 1)
            }
        }
        val partial = range != null
        val mime = mimeById[id] ?: File(cacheDir, "media_$id.mime").takeIf { it.isFile }?.readText()?.takeIf { it.startsWith("image/") || it.startsWith("video/") } ?: "application/octet-stream"
        val count = end - start + 1
        out.write("HTTP/1.1 ${if (partial) "206 Partial Content" else "200 OK"}\r\nContent-Type: $mime\r\nAccept-Ranges: bytes\r\nContent-Length: $count\r\n${if (partial) "Content-Range: bytes $start-$end/$total\r\n" else ""}Connection: close\r\n\r\n".toByteArray())
        file.inputStream().use { stream -> stream.skip(start); val buf = ByteArray(64 * 1024); var left = count; while (left > 0) { val n = stream.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (n < 0) break; out.write(buf, 0, n); left -= n } }
        out.flush()
    }

    private fun rejectRange(out: BufferedOutputStream, total: Long) {
        out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
    }

    private fun respond(out: BufferedOutputStream, status: Int, message: String, mime: String, body: ByteArray) {
        out.write("HTTP/1.1 $status $message\r\nContent-Type: $mime\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); out.write(body); out.flush()
    }
    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>(); var previous = -1
        while (true) { val b = input.read(); if (b < 0) return if (bytes.isEmpty()) null else String(bytes.toByteArray()); if (previous == 13 && b == 10) { bytes.removeAt(bytes.lastIndex); return String(bytes.toByteArray(), Charsets.ISO_8859_1) }; bytes.add(b.toByte()); previous = b }
    }
}
