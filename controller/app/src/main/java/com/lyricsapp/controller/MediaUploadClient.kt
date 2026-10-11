package com.lyricsapp.controller

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UploadedMedia(val mediaId: String, val url: String, val type: String)

object MediaUploadClient {
    /** Streams a Photo Picker URI to the receiver using HTTP chunked transfer encoding. */
    fun upload(context: Context, uri: Uri, host: String, port: Int, onProgress: (Float) -> Unit): UploadedMedia {
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        require(mime.startsWith("image/") || mime.startsWith("video/")) { "Choose an image or video" }
        val urlHost = host.trim().removePrefix("[").removeSuffix("]").let { address ->
            if (address.contains(':')) "[${address.replace("%", "%25")}]" else address
        }
        val connection = (URL("http://$urlHost:$port/media").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = 60_000
            setRequestProperty("Content-Type", mime)
            setChunkedStreamingMode(64 * 1024)
        }
        try {
            val expected = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            context.contentResolver.openInputStream(uri)?.use { input ->
                connection.outputStream.use { output ->
                    val buffer = ByteArray(64 * 1024); var copied = 0L
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        output.write(buffer, 0, n); copied += n
                        if (expected > 0) onProgress((copied.toFloat() / expected).coerceIn(0f, 1f))
                    }
                }
            } ?: error("Unable to read selected media")
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error(body.ifBlank { "Media upload failed ($status)" })
            val response = JSONObject(body)
            onProgress(1f)
            val mediaId = response.getString("mediaId")
            return UploadedMedia(mediaId, "http://$urlHost:$port/media/$mediaId", response.getString("type"))
        } finally { connection.disconnect() }
    }

}
