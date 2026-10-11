package com.lyricsapp.controller

import android.net.Uri
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Compact local preview used by song creation and the live screen. */
@Composable
fun LyricsSlidePreview(
    text: String,
    fontFamilyName: String,
    fontSizeSp: Float,
    textColorHex: String,
    backgroundColorHex: String,
    receiverWidthDp: Int,
    horizontalAlignment: String,
    verticalAlignment: String,
    bold: Boolean,
    italic: Boolean,
    lineSpacing: Float,
    paddingDp: Int,
    backgroundType: String = "COLOR",
    backgroundUrl: String? = null,
    mediaFit: String = "FILL",
    overlayDimPercent: Int = 0,
    shadowColorHex: String? = null,
    lyricsVisible: Boolean = text.isNotBlank(),
    backgroundVisible: Boolean = backgroundType.equals("IMAGE", true) || backgroundType.equals("VIDEO", true),
    black: Boolean = false,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE") val previewWidth = receiverWidthDp
    val media = backgroundUrl
    val alignment = when (verticalAlignment.lowercase()) {
        "top" -> when (horizontalAlignment.lowercase()) { "left" -> Alignment.TopStart; "right" -> Alignment.TopEnd; else -> Alignment.TopCenter }
        "bottom" -> when (horizontalAlignment.lowercase()) { "left" -> Alignment.BottomStart; "right" -> Alignment.BottomEnd; else -> Alignment.BottomCenter }
        else -> when (horizontalAlignment.lowercase()) { "left" -> Alignment.CenterStart; "right" -> Alignment.CenterEnd; else -> Alignment.Center }
    }
    val textAlign = when (horizontalAlignment.lowercase()) { "left" -> TextAlign.Start; "right" -> TextAlign.End; else -> TextAlign.Center }

    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).background(if (black) Color.Black else parsePreviewColor(backgroundColorHex, Color.Black))) {
        if (!black && backgroundVisible && !media.isNullOrBlank()) {
            if (backgroundType.equals("IMAGE", true)) {
                AsyncImage(
                    model = Uri.parse(media), contentDescription = null,
                    contentScale = when (mediaFit.uppercase()) { "FIT" -> ContentScale.Fit; "STRETCH" -> ContentScale.FillBounds; else -> ContentScale.Crop },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (!black && backgroundVisible && overlayDimPercent > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = overlayDimPercent.coerceIn(0, 100) / 100f)))
        }
        if (!black && lyricsVisible && text.isNotBlank()) {
            Box(Modifier.fillMaxSize().padding(paddingDp.coerceAtLeast(0).dp), contentAlignment = alignment) {
                androidx.compose.material3.Text(
                    text = text,
                    color = parsePreviewColor(textColorHex, Color.White),
                    fontSize = fontSizeSp.sp,
                    fontFamily = previewFont(fontFamilyName),
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                    textAlign = textAlign,
                    lineHeight = (fontSizeSp * lineSpacing.coerceIn(0.8f, 2.5f)).sp,
                    style = TextStyle(shadow = shadowColorHex?.let { androidx.compose.ui.graphics.Shadow(parsePreviewColor(it, Color.Transparent), androidx.compose.ui.geometry.Offset(2f, 2f), 5f) })
                )
            }
        }
    }
}

private fun previewFont(name: String): FontFamily = when (name.lowercase()) {
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "telugu", "telugu (sans)", "sans-serif-te" -> FontFamily(Typeface.create("sans-serif-te", Typeface.NORMAL))
    "telugu (serif)", "serif-te" -> FontFamily(Typeface.create("serif-te", Typeface.NORMAL))
    else -> FontFamily.SansSerif
}

private fun parsePreviewColor(value: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)
