package com.lyricsapp.controller

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.atan2
import kotlin.math.min

/** Reusable wheel, value, RGB, alpha and HEX color editor with a live preview. */
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit
) {
    var color by remember(initialColor) { mutableIntStateOf(initialColor) }
    var hex by remember(color) { mutableStateOf(String.format("#%06X", color and 0xFFFFFF)) }
    val presets = listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFFF00.toInt(), 0xFFFF0000.toInt(), 0xFF0000FF.toInt(), 0xFF00FF00.toInt(), 0xFFFF9800.toInt(), 0xFF9C27B0.toInt())
    fun updateRgb(red: Int = AndroidColor.red(color), green: Int = AndroidColor.green(color), blue: Int = AndroidColor.blue(color)) {
        color = AndroidColor.argb(AndroidColor.alpha(color), red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
    }
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose color", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { value ->
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(value)).pointerInput(Unit) { detectTapGestures { color = value } })
                    }
                }
                Canvas(Modifier.fillMaxWidth().height(190.dp).pointerInput(Unit) {
                    detectTapGestures { point ->
                        val cx = size.width / 2f; val cy = size.height / 2f
                        val hue = ((Math.toDegrees(atan2((point.y - cy).toDouble(), (point.x - cx).toDouble())) + 360.0) % 360.0).toFloat()
                        val hsv = FloatArray(3); AndroidColor.colorToHSV(color, hsv); hsv[0] = hue; hsv[1] = (min(1f, kotlin.math.hypot(point.x - cx, point.y - cy) / min(cx, cy)))
                        color = AndroidColor.HSVToColor(AndroidColor.alpha(color), hsv)
                    }
                }) { drawCircle(Brush.sweepGradient(List(13) { Color(AndroidColor.HSVToColor(floatArrayOf(it * 30f, 1f, 1f))) })) }
                Text("Brightness")
                val hsv = FloatArray(3).also { AndroidColor.colorToHSV(color, it) }
                Slider(value = hsv[2], onValueChange = { hsv[2] = it; color = AndroidColor.HSVToColor(AndroidColor.alpha(color), hsv) }, valueRange = 0f..1f)
                listOf("R" to AndroidColor.red(color), "G" to AndroidColor.green(color), "B" to AndroidColor.blue(color)).forEach { (label, channel) ->
                    var numeric by remember(color, label) { mutableStateOf(channel.toString()) }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(label, Modifier.width(20.dp))
                        Slider(value = channel.toFloat(), onValueChange = { updateRgb(when(label) { "R" -> it.toInt(); else -> AndroidColor.red(color) }, when(label) { "G" -> it.toInt(); else -> AndroidColor.green(color) }, when(label) { "B" -> it.toInt(); else -> AndroidColor.blue(color) }) }, valueRange = 0f..255f, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = numeric, onValueChange = { numeric = it; it.toIntOrNull()?.let { n -> updateRgb(when(label) { "R" -> n; else -> AndroidColor.red(color) }, when(label) { "G" -> n; else -> AndroidColor.green(color) }, when(label) { "B" -> n; else -> AndroidColor.blue(color) }) } }, modifier = Modifier.width(76.dp), singleLine = true)
                    }
                }
                OutlinedTextField(value = hex, onValueChange = { value -> hex = value; runCatching { AndroidColor.parseColor(value) }.getOrNull()?.let { color = it } }, label = { Text("HEX (#RRGGBB)") }, singleLine = true)
                Text("Background alpha")
                Slider(value = AndroidColor.alpha(color).toFloat(), onValueChange = { color = AndroidColor.argb(it.toInt(), AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color)) }, valueRange = 0f..255f)
                Box(Modifier.fillMaxWidth().height(56.dp).background(Color(color)), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("Live preview", color = Color(AndroidColor.rgb(255 - AndroidColor.red(color), 255 - AndroidColor.green(color), 255 - AndroidColor.blue(color)))) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onApply(color) }) { Text("Apply") }
                }
            }
        }
    }
}
