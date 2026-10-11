package com.lyricsapp.controller

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Dark Spatial token values. Keep in sync with the Figma Dark Spatial variables. */
object SpatialColors {
    val Background = Color(0xFF07080C)
    val Surface = Color(0xFF10131C)
    val Raised = Color(0xFF171B27)
    val Floating = Color(0xFF202637)
    val TextPrimary = Color(0xFFF5F6FF)
    val TextSecondary = Color(0xFFA4AAC0)
    val TextMuted = Color(0xFF737B94)
    val Accent = Color(0xFF7C8CFF)
    val Gold = Color(0xFFF2C879)
    val Success = Color(0xFF66D2A5)
    val Warning = Color(0xFFF2B76B)
    val Error = Color(0xFFF17C7C)
    val Info = Color(0xFF7FB9FF)
    val InnerBorder = Color.White.copy(alpha = 0.12f)
    val FocusBorder = Color(0xFF9AA6FF)
}

object SpatialSpacing {
    val One = 4.dp
    val Two = 8.dp
    val Three = 12.dp
    val Four = 16.dp
    val Six = 24.dp
    val Eight = 32.dp
}

object SpatialRadii {
    val Control = 16.dp
    val Panel = 24.dp
    val Feature = 32.dp
}

private val ControllerDarkScheme = darkColorScheme(
    primary = SpatialColors.Accent,
    onPrimary = Color(0xFF080A12),
    primaryContainer = SpatialColors.Floating,
    onPrimaryContainer = SpatialColors.TextPrimary,
    secondary = SpatialColors.Gold,
    onSecondary = Color(0xFF211A0D),
    secondaryContainer = Color(0xFF302B20),
    onSecondaryContainer = SpatialColors.Gold,
    tertiary = SpatialColors.Info,
    background = SpatialColors.Background,
    onBackground = SpatialColors.TextPrimary,
    surface = SpatialColors.Surface,
    onSurface = SpatialColors.TextPrimary,
    surfaceVariant = SpatialColors.Raised,
    onSurfaceVariant = SpatialColors.TextSecondary,
    outline = SpatialColors.InnerBorder,
    error = SpatialColors.Error,
    onError = Color(0xFF220A0D)
)

@Composable
fun LyricsControllerTheme(content: @Composable () -> Unit) {
    val uiSans = FontFamily.SansSerif
    MaterialTheme(
        colorScheme = ControllerDarkScheme,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(fontFamily = uiSans, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 42.sp),
            headlineSmall = TextStyle(fontFamily = uiSans, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 34.sp),
            titleLarge = TextStyle(fontFamily = uiSans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontFamily = uiSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontFamily = uiSans, fontSize = 16.sp, lineHeight = 25.sp, platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true), lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)),
            bodyMedium = TextStyle(fontFamily = uiSans, fontSize = 14.sp, lineHeight = 22.sp, platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true), lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)),
            labelLarge = TextStyle(fontFamily = uiSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
        ),
        content = content
    )
}

/** A quiet ambient glow, blurred only on Android 12+ devices with enough memory. */
@Composable
fun SpatialBackdrop(content: @Composable BoxScope.() -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lowRam = rememberLowRamDevice(context)
    Box(Modifier.fillMaxSize().background(SpatialColors.Background)) {
        val blur = if (android.os.Build.VERSION.SDK_INT >= 31 && !lowRam) Modifier.blur(54.dp) else Modifier
        Box(
            Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                .size(270.dp)
                .background(Brush.radialGradient(listOf(SpatialColors.Accent.copy(alpha = 0.15f), Color.Transparent)), CircleShape)
                .then(blur)
        )
        content()
    }
}

private fun rememberLowRamDevice(context: android.content.Context): Boolean =
    (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice ?: true
