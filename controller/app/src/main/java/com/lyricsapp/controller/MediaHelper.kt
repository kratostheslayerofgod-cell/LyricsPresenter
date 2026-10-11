package com.lyricsapp.controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

object MediaHelper {
    private const val TAG = "MediaHelper"

    /**
     * Converts a local image Uri selected from device storage into a compressed 1080p JPEG Base64 Data URI.
     * This allows transmitting local photos directly over WebSocket to the Receiver TV without external URLs!
     */
    fun processLocalImageUri(context: Context, uri: Uri): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return uri.toString()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap == null) return uri.toString()

            // Resize bitmap to max 1920x1080 resolution for optimal WebSocket transmission
            val maxDim = 1920
            val scale = Math.min(
                maxDim.toFloat() / bitmap.width,
                maxDim.toFloat() / bitmap.height
            )
            
            val scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val bytes = baos.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            Log.e(TAG, "Error processing local image uri: $uri", e)
            uri.toString()
        }
    }
}
