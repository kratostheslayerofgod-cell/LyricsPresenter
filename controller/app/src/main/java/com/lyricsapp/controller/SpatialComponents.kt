package com.lyricsapp.controller

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class GlassElevation { Base, Raised, Floating }

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    elevation: GlassElevation = GlassElevation.Raised,
    focused: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(if (elevation == GlassElevation.Floating) 28.dp else 24.dp)
    val fill = when (elevation) {
        GlassElevation.Base -> SpatialColors.Surface.copy(alpha = 0.96f)
        GlassElevation.Raised -> SpatialColors.Raised.copy(alpha = 0.96f)
        GlassElevation.Floating -> SpatialColors.Floating.copy(alpha = 0.97f)
    }
    Column(
        modifier = modifier
            .shadow(if (elevation == GlassElevation.Floating) 22.dp else 12.dp, shape)
            .clip(shape)
            .background(fill)
            .border(if (focused) 2.dp else 1.dp, if (focused) SpatialColors.FocusBorder else SpatialColors.InnerBorder, shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SpatialButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    secondary: Boolean = false,
    loading: Boolean = false,
    error: Boolean = false,
    focused: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when { error || danger -> Color(0xFF331C25); secondary -> SpatialColors.Floating; else -> SpatialColors.Accent }
    val fg = when { error || danger -> SpatialColors.Error; secondary -> SpatialColors.TextPrimary; else -> Color(0xFF080A12) }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(SpatialRadii.Control),
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) SpatialColors.FocusBorder else SpatialColors.InnerBorder),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg, disabledContainerColor = SpatialColors.Raised, disabledContentColor = SpatialColors.TextMuted)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
        else Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, color: Color = SpatialColors.Success) {
    Surface(modifier = modifier.heightIn(min = 32.dp), shape = CircleShape, color = color.copy(alpha = 0.13f), border = BorderStroke(1.dp, color.copy(alpha = 0.28f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

enum class MessageSeverity { Success, Info, Warning, Error }
enum class AppMessageAction { None, OpenConnection, OpenBatterySettings, TryHotspot, RetryConnection, OpenMedia, DownloadBible, ReviewSong, SaveSong }

data class AppMessage(
    @StringRes val title: Int,
    @StringRes val cause: Int,
    @StringRes val actionLabel: Int? = null,
    val action: AppMessageAction = AppMessageAction.None,
    val severity: MessageSeverity = MessageSeverity.Info,
    val id: Long = System.nanoTime(),
    val details: String? = null
)

@Composable
fun AppMessageHost(message: AppMessage?, modifier: Modifier = Modifier, onDismiss: () -> Unit, onAction: (AppMessageAction) -> Unit) {
    LaunchedEffect(message?.id) {
        if (message != null) { delay(1_000); onDismiss() }
    }
    AnimatedVisibility(
        modifier = modifier,
        visible = message != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 })
    ) {
        val current = message ?: return@AnimatedVisibility
        val accent = when (current.severity) {
            MessageSeverity.Success -> SpatialColors.Success
            MessageSeverity.Info -> SpatialColors.Info
            MessageSeverity.Warning -> SpatialColors.Warning
            MessageSeverity.Error -> SpatialColors.Error
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = SpatialColors.Floating,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
            shadowElevation = 18.dp
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(current.title), color = SpatialColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(current.cause), color = SpatialColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    current.details?.takeIf { it.isNotBlank() }?.let { Text(it, color = SpatialColors.TextSecondary, style = MaterialTheme.typography.labelSmall) }
                }
                current.actionLabel?.let { label ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(label),
                        modifier = Modifier.clip(CircleShape).clickable { onAction(current.action); onDismiss() }.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = accent,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
