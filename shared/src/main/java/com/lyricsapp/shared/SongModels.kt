package com.lyricsapp.shared

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Song(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String? = null,
    val sections: List<SongSection> = emptyList(),
    val displaySettings: DisplaySettings = DisplaySettings(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val folderId: String? = null
) {
    val slides: List<SongSlide>
        get() = sections.flatMap { section -> section.slides }
}

@Serializable
data class DisplaySettings(
    val fontFamily: String = "SansSerif",
    val fontSize: Int = 48,
    val textColor: String = "#00E5FF", // Bright Glowing Cyan
    val backgroundType: String = "COLOR", // COLOR, IMAGE, VIDEO
    val backgroundValue: String = "#1E3A8A", // Royal Blue
    val textBold: Boolean = false,
    val textItalic: Boolean = false,
    val horizontalAlignment: String = "center",
    val verticalAlignment: String = "center",
    val lineSpacing: Float = 1.25f,
    val shadowColor: String? = null,
    val backgroundAlpha: Int = 255,
    val backgroundMediaId: String? = null,
    val mediaFitMode: MediaFitMode = MediaFitMode.FILL,
    val templateId: String? = null,
    val paddingDp: Int = 40,
    val backgroundGradientEndColor: String? = null
)

@Serializable
enum class TemplateBackgroundType { SOLID, GRADIENT, IMAGE, VIDEO, NONE }

@Serializable
enum class MediaFitMode { FILL, FIT, STRETCH }

/** A self-contained style payload. Built-in templates are immutable in the UI but duplicable. */
@Serializable
data class Template(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isDefault: Boolean = false,
    val isBuiltIn: Boolean = false,
    val fontFamily: String = "SansSerif",
    val fontSize: Int = 48,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val textColor: String = "#FFFFFFFF",
    val horizontalAlignment: String = "center",
    val verticalAlignment: String = "center",
    val lineSpacing: Float = 1.25f,
    val shadowColor: String? = null,
    val outlineColor: String? = null,
    val backgroundType: TemplateBackgroundType = TemplateBackgroundType.SOLID,
    val backgroundColor: String = "#FF000000",
    val backgroundMediaId: String? = null,
    val mediaFitMode: MediaFitMode = MediaFitMode.FILL,
    val overlayDimPercent: Int = 0,
    val paddingDp: Int = 40,
    val marginDp: Int = 0,
    val backgroundGradientEndColor: String? = null
)

/** Applies a template to existing song display settings without changing the song/Room schema. */
fun Template.toDisplaySettings(base: DisplaySettings = DisplaySettings()): DisplaySettings = base.copy(
    fontFamily = fontFamily,
    fontSize = fontSize,
    textColor = textColor,
    backgroundType = when (backgroundType) {
        TemplateBackgroundType.SOLID -> "COLOR"
        TemplateBackgroundType.GRADIENT -> "GRADIENT"
        TemplateBackgroundType.IMAGE -> "IMAGE"
        TemplateBackgroundType.VIDEO -> "VIDEO"
        TemplateBackgroundType.NONE -> "NONE"
    },
    backgroundValue = if (backgroundType == TemplateBackgroundType.SOLID || backgroundType == TemplateBackgroundType.GRADIENT) backgroundColor else base.backgroundValue,
    backgroundGradientEndColor = backgroundGradientEndColor.takeIf { backgroundType == TemplateBackgroundType.GRADIENT },
    textBold = bold,
    textItalic = italic,
    horizontalAlignment = horizontalAlignment,
    verticalAlignment = verticalAlignment,
    lineSpacing = lineSpacing,
    shadowColor = shadowColor,
    backgroundAlpha = (255 * (100 - overlayDimPercent.coerceIn(0, 100)) / 100),
    backgroundMediaId = backgroundMediaId,
    mediaFitMode = mediaFitMode,
    templateId = id,
    paddingDp = (paddingDp + marginDp).coerceAtLeast(0)
)

@Serializable
data class MediaAsset(
    val mediaId: String,
    val name: String,
    val type: String,
    val sourceUri: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

fun builtInTemplates(): List<Template> = listOf(
    Template(id = "builtin-classic", name = "Classic White on Black", isDefault = true, isBuiltIn = true),
    Template(id = "builtin-worship-blue", name = "Worship Blue", isBuiltIn = true, textColor = "#FF00E5FF", backgroundColor = "#FF1E3A8A"),
    Template(id = "builtin-warm-gold", name = "Warm Gold", isBuiltIn = true, textColor = "#FFFFD166", backgroundColor = "#FF20150A"),
    Template(id = "builtin-large-print", name = "Large Print", isBuiltIn = true, fontSize = 64, textColor = "#FFFFFFFF", backgroundColor = "#FF000000"),
    Template(id = "builtin-high-contrast", name = "High Contrast", isBuiltIn = true, fontSize = 56, bold = true, textColor = "#FFFFFF00", backgroundColor = "#FF000000")
)

@Serializable
data class SongSection(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val type: SectionType = SectionType.OTHER,
    val slides: List<SongSlide> = emptyList()
)

@Serializable
data class SongSlide(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,
    val content: String
)

@Serializable
enum class SectionType {
    VERSE,
    CHORUS,
    BRIDGE,
    INTRO,
    OUTRO,
    OTHER
}

@Serializable
data class Setlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun splitIntoSlides(text: String): List<String> =
    text.trim()
        .split(Regex("""\r?\n\s*\r?\n"""))
        .map(String::trim)
        .filter(String::isNotEmpty)

/**
 * FreeShow / ProPresenter style lyrics parser.
 * Converts raw text with section headers (e.g. [Verse 1], [Chorus])
 * and double line breaks into structured SongSection and SongSlide list.
 */
fun parseLyricsToSections(lyricsText: String): List<SongSection> {
    if (lyricsText.isBlank()) return emptyList()

    val lines = lyricsText.lines()
    val sections = mutableListOf<SongSection>()
    var currentSectionLabel = "Verse 1"
    var currentSectionType = SectionType.VERSE
    val currentParagraphLines = mutableListOf<String>()
    val currentSectionSlides = mutableListOf<SongSlide>()
    var slideGlobalIndex = 0

    fun commitSlide() {
        if (currentParagraphLines.isNotEmpty()) {
            val content = currentParagraphLines.joinToString("\n").trim()
            if (content.isNotEmpty()) {
                currentSectionSlides.add(
                    SongSlide(
                        id = UUID.randomUUID().toString(),
                        index = slideGlobalIndex++,
                        content = content
                    )
                )
            }
            currentParagraphLines.clear()
        }
    }

    fun commitSection() {
        commitSlide()
        if (currentSectionSlides.isNotEmpty()) {
            sections.add(
                SongSection(
                    id = UUID.randomUUID().toString(),
                    label = currentSectionLabel,
                    type = currentSectionType,
                    slides = currentSectionSlides.toList()
                )
            )
            currentSectionSlides.clear()
        }
    }

    for (rawLine in lines) {
        val line = rawLine.trim()
        val isHeader = (line.startsWith("[") && line.endsWith("]")) ||
                line.equals("Chorus", ignoreCase = true) ||
                line.equals("Bridge", ignoreCase = true) ||
                line.startsWith("Verse", ignoreCase = true) ||
                line.startsWith("Intro", ignoreCase = true) ||
                line.startsWith("Outro", ignoreCase = true)

        if (isHeader) {
            commitSection()
            currentSectionLabel = line.removePrefix("[").removeSuffix("]").removeSuffix(":").trim()
            currentSectionType = when {
                currentSectionLabel.contains("Chorus", ignoreCase = true) -> SectionType.CHORUS
                currentSectionLabel.contains("Verse", ignoreCase = true) -> SectionType.VERSE
                currentSectionLabel.contains("Bridge", ignoreCase = true) -> SectionType.BRIDGE
                currentSectionLabel.contains("Intro", ignoreCase = true) -> SectionType.INTRO
                currentSectionLabel.contains("Outro", ignoreCase = true) -> SectionType.OUTRO
                else -> SectionType.OTHER
            }
        } else if (line.isEmpty()) {
            commitSlide()
        } else {
            currentParagraphLines.add(line)
        }
    }
    commitSection()

    if (sections.isEmpty()) {
        val rawSlides = splitIntoSlides(lyricsText).mapIndexed { idx, content ->
            SongSlide(id = UUID.randomUUID().toString(), index = idx, content = content)
        }
        if (rawSlides.isNotEmpty()) {
            sections.add(
                SongSection(
                    id = UUID.randomUUID().toString(),
                    label = "Verse 1",
                    type = SectionType.VERSE,
                    slides = rawSlides
                )
            )
        }
    }

    return sections
}
