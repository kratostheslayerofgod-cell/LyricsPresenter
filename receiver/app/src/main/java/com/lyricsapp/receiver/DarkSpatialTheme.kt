package com.lyricsapp.receiver

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ReceiverDarkScheme = darkColorScheme(
    primary = Color(0xFF7C8CFF),
    secondary = Color(0xFFF2C879),
    background = Color(0xFF07080C),
    surface = Color(0xFF10131C),
    onBackground = Color(0xFFF5F6FF),
    onSurface = Color(0xFFF5F6FF),
    onSurfaceVariant = Color(0xFFA4AAC0),
    error = Color(0xFFF17C7C)
)

@Composable
fun LyricsReceiverTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ReceiverDarkScheme, content = content)
}
