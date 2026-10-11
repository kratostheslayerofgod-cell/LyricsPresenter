package com.lyricsapp.controller

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyricsapp.shared.MediaFitMode
import com.lyricsapp.shared.Template
import com.lyricsapp.shared.TemplateBackgroundType
import com.lyricsapp.shared.builtInTemplates
import java.util.UUID

private enum class TemplateColorTarget { Text, Background, Shadow }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TemplateManagerScreen(
    templates: List<Template>,
    onSave: (Template) -> Unit,
    onDelete: (Template) -> Unit,
    onSetDefault: (Template) -> Unit,
    onApply: (Template) -> Unit
) {
    var editing by remember { mutableStateOf<Template?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Templates", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text("Built-in styles are read-only. Duplicate one to customize it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { editing = Template(name = "Custom template") }) { Text("Create template") }
        }
        items(templates, key = { it.id }) { template ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    TemplatePreview(template = template, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Text(template.name + if (template.isDefault) " · Default" else "", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onApply(template) }) { Text("Apply") }
                        TextButton(onClick = { editing = template.copy(id = UUID.randomUUID().toString(), name = "${template.name} Copy", isBuiltIn = false, isDefault = false) }) { Text("Duplicate") }
                        TextButton(onClick = { onSetDefault(template) }) { Text("Set default") }
                        if (!template.isBuiltIn) {
                            TextButton(onClick = { editing = template }) { Text("Edit") }
                            TextButton(onClick = { onDelete(template) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { draft ->
        var name by remember(draft.id) { mutableStateOf(draft.name) }
        var fontFamily by remember(draft.id) { mutableStateOf(draft.fontFamily) }
        var fontSize by remember(draft.id) { mutableFloatStateOf(draft.fontSize.toFloat()) }
        var bold by remember(draft.id) { mutableStateOf(draft.bold) }
        var italic by remember(draft.id) { mutableStateOf(draft.italic) }
        var textColor by remember(draft.id) { mutableStateOf(draft.textColor) }
        var backgroundColor by remember(draft.id) { mutableStateOf(draft.backgroundColor) }
        var shadowColor by remember(draft.id) { mutableStateOf(draft.shadowColor) }
        var horizontalAlignment by remember(draft.id) { mutableStateOf(draft.horizontalAlignment) }
        var verticalAlignment by remember(draft.id) { mutableStateOf(draft.verticalAlignment) }
        var lineSpacing by remember(draft.id) { mutableFloatStateOf(draft.lineSpacing) }
        var backgroundType by remember(draft.id) { mutableStateOf(draft.backgroundType) }
        var backgroundMediaId by remember(draft.id) { mutableStateOf(draft.backgroundMediaId.orEmpty()) }
        var fit by remember(draft.id) { mutableStateOf(draft.mediaFitMode) }
        var overlayDim by remember(draft.id) { mutableFloatStateOf(draft.overlayDimPercent.toFloat()) }
        var padding by remember(draft.id) { mutableFloatStateOf(draft.paddingDp.toFloat()) }
        var margin by remember(draft.id) { mutableFloatStateOf(draft.marginDp.toFloat()) }
        var colorTarget by remember(draft.id) { mutableStateOf(TemplateColorTarget.Text) }
        var showPicker by remember(draft.id) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit template") },
            text = {
                Column(
                    Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            .background(previewBackground(backgroundType, backgroundColor), RoundedCornerShape(16.dp)),
                        contentAlignment = templateAlignment(verticalAlignment)
                    ) {
                        if (overlayDim > 0 && backgroundType != TemplateBackgroundType.NONE) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = overlayDim / 100f)))
                        }
                        Text(
                            "Amazing grace, how sweet the sound",
                            modifier = Modifier.fillMaxWidth().padding((padding + margin).dp.coerceAtMost(64.dp)),
                            color = parseTemplateColor(textColor),
                            fontSize = (fontSize / 2).coerceAtLeast(14f).sp,
                            fontFamily = templateFontFamily(fontFamily),
                            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                            textAlign = templateTextAlign(horizontalAlignment),
                            lineHeight = (fontSize / 2 * lineSpacing).sp,
                            maxLines = 3
                        )
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("Template name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Text("Font family", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SansSerif", "Serif", "Monospace", "Telugu").forEach { family ->
                            FilterChip(selected = fontFamily == family, onClick = { fontFamily = family }, label = { Text(family) })
                        }
                    }
                    Text("Font size: ${fontSize.toInt()} sp")
                    Slider(fontSize, { fontSize = it }, valueRange = 24f..96f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(bold, { bold = it }); Text("Bold")
                        Spacer(Modifier.width(12.dp))
                        Checkbox(italic, { italic = it }); Text("Italic")
                    }

                    Text("Text color, background and shadow")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Text" to TemplateColorTarget.Text, "Background" to TemplateColorTarget.Background, "Shadow" to TemplateColorTarget.Shadow).forEach { (label, target) ->
                            OutlinedButton(onClick = { colorTarget = target; showPicker = true }) { Text(label) }
                        }
                    }

                    Text("Horizontal alignment")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("left", "center", "right").forEach { value ->
                            FilterChip(horizontalAlignment == value, { horizontalAlignment = value }, label = { Text(value.replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    Text("Vertical alignment")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("top", "center", "bottom").forEach { value ->
                            FilterChip(verticalAlignment == value, { verticalAlignment = value }, label = { Text(value.replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    Text("Line spacing: ${"%.2f".format(lineSpacing)}")
                    Slider(lineSpacing, { lineSpacing = it }, valueRange = 1f..2f)

                    Text("Background type")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TemplateBackgroundType.entries.forEach { type ->
                            FilterChip(backgroundType == type, { backgroundType = type }, label = { Text(type.name) })
                        }
                    }
                    if (backgroundType == TemplateBackgroundType.IMAGE || backgroundType == TemplateBackgroundType.VIDEO) {
                        OutlinedTextField(
                            value = backgroundMediaId,
                            onValueChange = { backgroundMediaId = it },
                            label = { Text("Background media ID") },
                            placeholder = { Text("Choose an imported media ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text("Media fit")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MediaFitMode.entries.forEach { mode ->
                            FilterChip(fit == mode, { fit = mode }, label = { Text(mode.name) })
                        }
                    }
                    Text("Overlay dim: ${overlayDim.toInt()}%")
                    Slider(overlayDim, { overlayDim = it }, valueRange = 0f..100f)
                    Text("Text safe-area padding: ${padding.toInt()} dp")
                    Slider(padding, { padding = it }, valueRange = 0f..120f)
                    Text("Outer margin: ${margin.toInt()} dp")
                    Slider(margin, { margin = it }, valueRange = 0f..80f)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSave(draft.copy(
                        name = name.ifBlank { "Custom template" },
                        fontFamily = fontFamily,
                        fontSize = fontSize.toInt(),
                        bold = bold,
                        italic = italic,
                        textColor = textColor,
                        horizontalAlignment = horizontalAlignment,
                        verticalAlignment = verticalAlignment,
                        lineSpacing = lineSpacing,
                        shadowColor = shadowColor,
                        backgroundType = backgroundType,
                        backgroundColor = backgroundColor,
                        backgroundMediaId = backgroundMediaId.ifBlank { null },
                        mediaFitMode = fit,
                        overlayDimPercent = overlayDim.toInt(),
                        paddingDp = padding.toInt(),
                        marginDp = margin.toInt(),
                        isBuiltIn = false
                    ))
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )

        if (showPicker) {
            val selected = when (colorTarget) {
                TemplateColorTarget.Text -> textColor
                TemplateColorTarget.Background -> backgroundColor
                TemplateColorTarget.Shadow -> shadowColor ?: "#FF000000"
            }
            ColorPickerDialog(
                initialColor = parseTemplateColor(selected).value.toLong().toInt(),
                onDismiss = { showPicker = false },
                onApply = { value ->
                    val hex = String.format("#%08X", value)
                    when (colorTarget) {
                        TemplateColorTarget.Text -> textColor = hex
                        TemplateColorTarget.Background -> backgroundColor = hex
                        TemplateColorTarget.Shadow -> shadowColor = hex
                    }
                    showPicker = false
                }
            )
        }
    }
}

@Composable
private fun TemplatePreview(template: Template, modifier: Modifier = Modifier) {
    Box(modifier.background(previewBackground(template.backgroundType, template.backgroundColor), RoundedCornerShape(8.dp)), contentAlignment = templateAlignment(template.verticalAlignment)) {
        Text(
            "Amazing grace, how sweet the sound",
            modifier = Modifier.fillMaxWidth().padding(template.paddingDp.dp.coerceAtMost(48.dp)),
            color = parseTemplateColor(template.textColor),
            fontSize = (template.fontSize / 2).coerceAtLeast(14).sp,
            fontFamily = templateFontFamily(template.fontFamily),
            fontWeight = if (template.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (template.italic) FontStyle.Italic else FontStyle.Normal,
            textAlign = templateTextAlign(template.horizontalAlignment),
            lineHeight = (template.fontSize / 2f * template.lineSpacing).sp,
            maxLines = 3
        )
    }
}

private fun previewBackground(type: TemplateBackgroundType, color: String): Color =
    if (type == TemplateBackgroundType.SOLID) parseTemplateColor(color) else Color(0xFF10131C)

private fun templateAlignment(vertical: String): Alignment = when (vertical.lowercase()) {
    "top" -> Alignment.TopCenter
    "bottom" -> Alignment.BottomCenter
    else -> Alignment.Center
}

private fun templateTextAlign(horizontal: String): TextAlign = when (horizontal.lowercase()) {
    "left" -> TextAlign.Start
    "right" -> TextAlign.End
    else -> TextAlign.Center
}

private fun templateFontFamily(family: String): FontFamily = when (family.lowercase()) {
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "telugu" -> FontFamily(Typeface.create("sans-serif-te", Typeface.NORMAL))
    else -> FontFamily.SansSerif
}

private fun parseTemplateColor(value: String): Color = runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color.Black)
