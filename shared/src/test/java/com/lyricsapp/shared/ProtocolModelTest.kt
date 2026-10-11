package com.lyricsapp.shared

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class ProtocolModelTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    @Test fun templatesRoundTripAndProvideFiveBuiltIns() {
        val template = builtInTemplates().first { it.id == "builtin-warm-gold" }
        assertEquals(template, json.decodeFromString<Template>(json.encodeToString(template)))
        assertEquals(5, builtInTemplates().size)
    }

    @Test fun applyTemplateMessageContainsFullPayload() {
        val encoded = json.encodeToString(ApplyTemplateMessage(builtInTemplates()[1]))
        assertTrue(encoded.contains("Worship Blue"))
        assertTrue(encoded.contains("#FF1E3A8A"))
    }

    @Test fun commandUsesStableTypeDiscriminator() {
        val encoded = json.encodeToString<Message>(ClearAllMessage())
        assertTrue(encoded.contains("\"type\":\"CLEAR_ALL\""))
        assertTrue(json.decodeFromString<Message>(encoded) is ClearAllMessage)
    }

    @Test fun handshakeAckRoundTripsVersionAndIndependentBlankLayers() {
        val ack = AckMessage(
            receivedMessageType = "HANDSHAKE",
            success = true,
            protocolVersion = 2,
            mediaPort = 8766,
            screenWidthDp = 1920,
            screenHeightDp = 1080,
            screenState = "BLANK",
            layerState = LayerState(background = false, lyrics = false, overlay = false)
        )
        val encoded = json.encodeToString(ack)
        assertEquals(ack, json.decodeFromString<AckMessage>(encoded))
        assertTrue(encoded.contains("\"screenState\":\"BLANK\""))
        assertTrue(encoded.contains("\"background\":false"))
    }
}
