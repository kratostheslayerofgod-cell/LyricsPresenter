package com.lyricsapp.controller

import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyricsapp.shared.MediaFitMode
import com.lyricsapp.shared.MediaAsset
import com.lyricsapp.shared.Template
import com.lyricsapp.shared.TemplateBackgroundType
import com.lyricsapp.shared.builtInTemplates
import java.util.UUID
import coil.compose.AsyncImage

private enum class TemplateColorTarget { Text, Background, Gradient, Shadow, Outline }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TemplateManagerScreen(
    templates: List<Template>,
    mediaAssets: List<MediaAsset> = emptyList(),
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
                    TemplatePreview(template = template, mediaAssets = mediaAssets, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
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
        var gradientEndColor by remember(draft.id) { mutableStateOf(draft.backgroundGradientEndColor ?: "#FF1E3A8A") }
        var shadowColor by remember(draft.id) { mutableStateOf(draft.shadowColor) }
        var outlineColor by remember(draft.id) { mutableStateOf(draft.outlineColor) }
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
        var mediaMenuExpanded by remember(draft.id) { mutableStateOf(false) }
        val previewMedia = mediaAssets.firstOrNull { it.mediaId == backgroundMediaId }

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (templates.any { it.id == draft.id }) "Edit template" else "Create template") },
            text = {
                Column(
                    Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            .background(previewBackground(backgroundType, backgroundColor, gradientEndColor), RoundedCornerShape(16.dp)),
                        contentAlignment = templateAlignment(verticalAlignment)
                    ) {
                        if (backgroundType == TemplateBackgroundType.IMAGE && previewMedia?.sourceUri != null) {
                            AsyncImage(
                                model = Uri.parse(previewMedia.sourceUri),
                                contentDescription = null,
                                contentScale = mediaContentScale(fit),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (backgroundType == TemplateBackgroundType.VIDEO) {
                            Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.4f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(previewMedia?.name ?: "Video background", color = Color.White)
                                }
                            }
                        }
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
                            style = TextStyle(shadow = shadowColor?.let { Shadow(parseTemplateColor(it), Offset(2f, 2f), 5f) }),
                            maxLines = 3
                        )
                    }
                    Text("Text size: ${fontSize.toInt()} sp", style = MaterialTheme.typography.labelLarge)
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

                    Text("Text color (words)")
                    ColorSwatches(textColor) { color -> textColor = color }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Custom text…" to TemplateColorTarget.Text, "Shadow…" to TemplateColorTarget.Shadow, "Outline…" to TemplateColorTarget.Outline).forEach { (label, target) ->
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

                    Text("Background & media studio")
                    ColorSwatches(backgroundColor) { color -> backgroundColor = color; backgroundType = TemplateBackgroundType.SOLID }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { colorTarget = TemplateColorTarget.Background; showPicker = true }) { Text("Custom background color…") }
                        FilterChip(backgroundType == TemplateBackgroundType.GRADIENT, { backgroundType = TemplateBackgroundType.GRADIENT }, label = { Text("Gradient") })
                    }
                    if (backgroundType == TemplateBackgroundType.GRADIENT) {
                        Text("Gradient end color")
                        ColorSwatches(gradientEndColor) { gradientEndColor = it }
                        OutlinedButton(onClick = { colorTarget = TemplateColorTarget.Gradient; showPicker = true }) { Text("Custom gradient color…") }
                    }
                    Text("Background type")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TemplateBackgroundType.entries.forEach { type ->
                            FilterChip(backgroundType == type, { backgroundType = type }, label = { Text(type.name) })
                        }
                    }
                    if (backgroundType == TemplateBackgroundType.IMAGE || backgroundType == TemplateBackgroundType.VIDEO) {
                        val matchingMedia = mediaAssets.filter { asset ->
                            if (backgroundType == TemplateBackgroundType.IMAGE) asset.type.equals("IMAGE", true) || asset.type.equals("PHOTO", true)
                            else asset.type.equals("VIDEO", true)
                        }
                        ExposedDropdownMenuBox(expanded = mediaMenuExpanded, onExpandedChange = { mediaMenuExpanded = it }) {
                            OutlinedTextField(
                                value = matchingMedia.firstOrNull { it.mediaId == backgroundMediaId }?.name
                                    ?: backgroundMediaId.ifBlank { "Choose imported ${backgroundType.name.lowercase()}" },
                                onValueChange = {}, readOnly = true, label = { Text("Background media") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mediaMenuExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = mediaMenuExpanded, onDismissRequest = { mediaMenuExpanded = false }) {
                                matchingMedia.forEach { asset ->
                                    DropdownMenuItem(text = { Text(asset.name) }, onClick = {
                                        backgroundMediaId = asset.mediaId
                                        mediaMenuExpanded = false
                                    })
                                }
                                if (matchingMedia.isEmpty()) DropdownMenuItem(text = { Text("No matching media imported") }, onClick = { mediaMenuExpanded = false })
                            }
                        }
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
                        outlineColor = outlineColor,
                        horizontalAlignment = horizontalAlignment,
                        verticalAlignment = verticalAlignment,
                        lineSpacing = lineSpacing,
                        shadowColor = shadowColor,
                        backgroundType = backgroundType,
                        backgroundColor = backgroundColor,
                        backgroundGradientEndColor = gradientEndColor.takeIf { backgroundType == TemplateBackgroundType.GRADIENT },
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
                TemplateColorTarget.Gradient -> gradientEndColor
                TemplateColorTarget.Shadow -> shadowColor ?: "#FF000000"
                TemplateColorTarget.Outline -> outlineColor ?: "#FFFFFFFF"
            }
            ColorPickerDialog(
                initialColor = parseTemplateColor(selected).value.toLong().toInt(),
                onDismiss = { showPicker = false },
                onApply = { value ->
                    val hex = String.format("#%08X", value)
                    when (colorTarget) {
                        TemplateColorTarget.Text -> textColor = hex
                        TemplateColorTarget.Background -> backgroundColor = hex
                        TemplateColorTarget.Gradient -> gradientEndColor = hex
                        TemplateColorTarget.Shadow -> shadowColor = hex
                        TemplateColorTarget.Outline -> outlineColor = hex
                    }
                    showPicker = false
                }
            )
        }
    }
}

@Composable
private fun TemplatePreview(template: Template, mediaAssets: List<MediaAsset>, modifier: Modifier = Modifier) {
    val media = mediaAssets.firstOrNull { it.mediaId == template.backgroundMediaId }
    Box(modifier.background(previewBackground(template.backgroundType, template.backgroundColor, template.backgroundGradientEndColor), RoundedCornerShape(8.dp)), contentAlignment = templateAlignment(template.verticalAlignment)) {
        if (template.backgroundType == TemplateBackgroundType.IMAGE && media?.sourceUri != null) {
            AsyncImage(Uri.parse(media.sourceUri), contentDescription = null, contentScale = mediaContentScale(template.mediaFitMode), modifier = Modifier.fillMaxSize())
        }
        if (template.backgroundType == TemplateBackgroundType.VIDEO) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                Text(media?.name ?: "Video background", color = Color.White)
            }
        }
        if (template.overlayDimPercent > 0 && template.backgroundType != TemplateBackgroundType.NONE) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = template.overlayDimPercent.coerceIn(0, 100) / 100f)))
        }
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
            style = TextStyle(shadow = template.shadowColor?.let { Shadow(parseTemplateColor(it), Offset(2f, 2f), 5f) }),
            maxLines = 3
        )
    }
}

private fun previewBackground(type: TemplateBackgroundType, color: String, endColor: String? = null): Brush = when (type) {
    TemplateBackgroundType.SOLID -> SolidColor(parseTemplateColor(color))
    TemplateBackgroundType.GRADIENT -> Brush.linearGradient(listOf(parseTemplateColor(color), parseTemplateColor(endColor ?: "#FF1E3A8A")))
    TemplateBackgroundType.NONE -> SolidColor(Color.Black)
    else -> SolidColor(Color(0xFF10131C))
}

@Composable
private fun ColorSwatches(selected: String, onSelect: (String) -> Unit) {
    val colors = listOf("#FF00BCD4", "#FF29B6F6", "#FFFFFFFF", "#FFFFFF00", "#FF000000", "#FF1E3A8A", "#FF2563EB", "#FF111827")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { hex ->
            Box(
                Modifier.size(38.dp).background(parseTemplateColor(hex), RoundedCornerShape(50))
                    .border(if (hex.equals(selected, true)) 3.dp else 1.dp, if (hex.equals(selected, true)) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .clickable { onSelect(hex) }
            )
        }
    }
}

private fun mediaContentScale(mode: MediaFitMode): ContentScale = when (mode) {
    MediaFitMode.FIT -> ContentScale.Fit
    MediaFitMode.STRETCH -> ContentScale.FillBounds
    MediaFitMode.FILL -> ContentScale.Crop
}

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
