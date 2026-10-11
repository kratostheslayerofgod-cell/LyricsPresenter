package com.lyricsapp.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Sealed class for all message types in the Lyrics Presenter protocol
 */
@Serializable
sealed class Message {
    abstract val timestamp: Long
}

@Serializable
@SerialName("NEXT_SLIDE")
data class NextSlideMessage(
    override val timestamp: Long = System.currentTimeMillis()
) : Message()

@Serializable
@SerialName("PREV_SLIDE")
data class PrevSlideMessage(
    override val timestamp: Long = System.currentTimeMillis()
) : Message()

@Serializable
@SerialName("JUMP_SLIDE")
data class JumpSlideMessage(
    val slideIndex: Int,
    override val timestamp: Long = System.currentTimeMillis()
) : Message()

@Serializable
@SerialName("BLACK_SCREEN")
data class BlackScreenMessage(
    override val timestamp: Long = System.currentTimeMillis()
) : Message()

@Serializable
@SerialName("CLEAR_SCREEN")
data class ClearScreenMessage(
    override val timestamp: Long = System.currentTimeMillis()
) : Message()

@Serializable
data class Slide(
    val index: Int,
    val content: String
)

@Serializable
@SerialName("DISPLAY")
data class DisplayMessage(
    val songId: String,
    val songTitle: String,
    val currentSlide: Int,
    val totalSlides: Int,
    val slides: List<Slide>,
    override val timestamp: Long = System.currentTimeMillis()
) : Message()

@Serializable
@SerialName("ACK")
data class AckMessage(
    val receivedMessageType: String,
    val success: Boolean,
    val currentSlide: Int? = null,
    val error: String? = null,
    val protocolVersion: Int? = null,
    val mediaPort: Int? = null,
    val screenWidthDp: Int? = null,
    val screenHeightDp: Int? = null,
    val screenState: String? = null,
    val layerState: LayerState? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : Message()

@Serializable
data class LayerState(
    val background: Boolean = false,
    val lyrics: Boolean = false,
    val overlay: Boolean = false
)

@Serializable
@SerialName("HANDSHAKE")
data class HandshakeMessage(
    val protocolVersion: Int = 2,
    override val timestamp: Long = System.currentTimeMillis()
) : Message()

@Serializable
@SerialName("SET_BACKGROUND")
data class SetBackgroundMessage(
    val mediaId: String,
    val mediaType: String,
    val fit: String = "FILL",
    val loop: Boolean = true,
    val muted: Boolean = true,
    override val timestamp: Long = System.currentTimeMillis()
) : Message()

@Serializable
@SerialName("CLEAR_BACKGROUND")
data class ClearBackgroundMessage(override val timestamp: Long = System.currentTimeMillis()) : Message()
@Serializable
@SerialName("CLEAR_LYRICS")
data class ClearLyricsMessage(override val timestamp: Long = System.currentTimeMillis()) : Message()
@Serializable
@SerialName("CLEAR_OVERLAY")
data class ClearOverlayMessage(override val timestamp: Long = System.currentTimeMillis()) : Message()
@Serializable
@SerialName("CLEAR_ALL")
data class ClearAllMessage(override val timestamp: Long = System.currentTimeMillis()) : Message()
@Serializable
@SerialName("APPLY_TEMPLATE")
data class ApplyTemplateMessage(val template: Template, override val timestamp: Long = System.currentTimeMillis()) : Message()
@Serializable
@SerialName("UPDATE_STYLE")
data class UpdateStyleMessage(
    val textColor: String? = null,
    val bgColor: String? = null,
    val backgroundAlpha: Int? = null,
    val fontSize: Int? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : Message()
@Serializable
@SerialName("MEDIA_READY")
data class MediaReadyMessage(val mediaId: String, val url: String, override val timestamp: Long = System.currentTimeMillis()) : Message()
@Serializable
@SerialName("MEDIA_ERROR")
data class MediaErrorMessage(val mediaId: String?, val reason: String, override val timestamp: Long = System.currentTimeMillis()) : Message()

@Serializable
@SerialName("HEARTBEAT")
data class HeartbeatMessage(
    override val timestamp: Long = System.currentTimeMillis()
) : Message()

@Serializable
@SerialName("HEARTBEAT_ACK")
data class HeartbeatAckMessage(
    override val timestamp: Long = System.currentTimeMillis()
) : Message()
