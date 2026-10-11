package com.lyricsapp.controller

import android.content.Context
import android.content.res.Resources

object AppMessages {
    fun success(context: Context, command: String): AppMessage = when (command) {
        "HANDSHAKE" -> info(R.string.msg_connected, R.string.msg_connected_cause, MessageSeverity.Success)
        "DISPLAY" -> info(R.string.msg_lyrics_sent, R.string.msg_lyrics_sent_cause, MessageSeverity.Success)
        "NEXT_SLIDE", "PREV_SLIDE", "JUMP_SLIDE" -> info(R.string.msg_slide_sent, R.string.msg_slide_sent_cause, MessageSeverity.Success)
        "SET_BACKGROUND" -> info(R.string.msg_background_started, R.string.msg_background_started_cause, MessageSeverity.Success)
        "CLEAR_BACKGROUND" -> info(R.string.msg_background_cleared, R.string.msg_background_cleared_cause, MessageSeverity.Success)
        "CLEAR_LYRICS" -> info(R.string.msg_lyrics_cleared, R.string.msg_lyrics_cleared_cause, MessageSeverity.Success)
        "CLEAR_ALL" -> info(R.string.msg_screen_cleared, R.string.msg_screen_cleared_cause, MessageSeverity.Success)
        "BLACK_SCREEN" -> info(R.string.msg_black_screen, R.string.msg_black_screen_cause, MessageSeverity.Success)
        "CLEAR_SCREEN" -> info(R.string.msg_lyrics_restored, R.string.msg_lyrics_restored_cause, MessageSeverity.Success)
        "APPLY_TEMPLATE" -> info(R.string.msg_template_applied, R.string.msg_template_applied_cause, MessageSeverity.Success)
        "UPDATE_STYLE" -> info(R.string.msg_style_updated, R.string.msg_style_updated_cause, MessageSeverity.Success)
        "VIDEO_PLAY" -> info(R.string.msg_video_playing, R.string.msg_video_playing_cause, MessageSeverity.Success)
        "VIDEO_PAUSE" -> info(R.string.msg_video_paused, R.string.msg_video_paused_cause, MessageSeverity.Info)
        else -> info(R.string.msg_tv_updated, R.string.msg_tv_updated_cause, MessageSeverity.Success)
    }

    fun failedAck(command: String, details: String?): AppMessage = when {
        command == "JUMP_SLIDE" -> AppMessage(R.string.err_slide_missing, R.string.err_slide_missing_cause, R.string.action_review_song, AppMessageAction.ReviewSong, MessageSeverity.Error)
        details?.contains("protocol", ignoreCase = true) == true -> AppMessage(R.string.err_protocol_version, R.string.err_protocol_version_cause, R.string.action_update_receiver, AppMessageAction.OpenConnection, MessageSeverity.Warning)
        else -> AppMessage(R.string.err_command_failed, R.string.err_command_failed_cause, R.string.action_retry, AppMessageAction.RetryConnection, MessageSeverity.Error)
    }

    fun mediaFailure(reason: String): AppMessage {
        val value = reason.lowercase()
        return when {
            "unsupported" in value || "not supported" in value || "media type" in value -> AppMessage(R.string.err_video_decode, R.string.err_video_decode_cause, R.string.action_choose_another, AppMessageAction.OpenMedia, MessageSeverity.Error)
            "large" in value || "2 gib" in value || "too big" in value -> AppMessage(R.string.err_media_too_large, R.string.err_media_too_large_cause, R.string.action_choose_another, AppMessageAction.OpenMedia, MessageSeverity.Error)
            "storage" in value || "space" in value || "507" in value -> AppMessage(R.string.err_tv_storage, R.string.err_tv_storage_cause, R.string.action_choose_another, AppMessageAction.OpenMedia, MessageSeverity.Error)
            "codec" in value || "decode" in value || "playback" in value -> AppMessage(R.string.err_video_decode, R.string.err_video_decode_cause, R.string.action_choose_another, AppMessageAction.OpenMedia, MessageSeverity.Error)
            "interrupt" in value || "timeout" in value || "upload" in value -> AppMessage(R.string.err_upload_interrupted, R.string.err_upload_interrupted_cause, R.string.action_retry, AppMessageAction.OpenMedia, MessageSeverity.Error)
            else -> AppMessage(R.string.err_media_general, R.string.err_media_general_cause, R.string.action_choose_another, AppMessageAction.OpenMedia, MessageSeverity.Error)
        }
    }

    fun noReceiver(): AppMessage = AppMessage(R.string.err_no_tv, R.string.err_no_tv_cause, R.string.action_enter_ip, AppMessageAction.OpenConnection, MessageSeverity.Warning)
    fun reconnecting(attempt: Int): AppMessage = AppMessage(R.string.msg_reconnecting, R.string.msg_reconnecting_cause, R.string.action_retry, AppMessageAction.RetryConnection, MessageSeverity.Warning)
    fun backOnline(): AppMessage = info(R.string.msg_back_online, R.string.msg_back_online_cause, MessageSeverity.Success)
    fun connectionProblem(): AppMessage = AppMessage(R.string.err_connection, R.string.err_connection_cause, R.string.action_retry, AppMessageAction.RetryConnection, MessageSeverity.Error)
    fun bibleOffline(): AppMessage = AppMessage(R.string.err_bible_offline, R.string.err_bible_offline_cause, R.string.action_download, AppMessageAction.DownloadBible, MessageSeverity.Info)
    fun genericFailure(): AppMessage = AppMessage(R.string.err_generic, R.string.err_generic_cause, R.string.action_retry, AppMessageAction.RetryConnection, MessageSeverity.Error)

    fun fromThrowable(error: Throwable): AppMessage {
        val text = error.message.orEmpty().lowercase()
        return when {
            "room" in text || "database" in text -> AppMessage(R.string.err_storage, R.string.err_storage_cause, R.string.action_retry, AppMessageAction.SaveSong, MessageSeverity.Error)
            "timeout" in text || "refused" in text || "network" in text -> connectionProblem()
            "bible" in text && "download" in text -> bibleOffline()
            else -> genericFailure()
        }
    }

    private fun info(title: Int, cause: Int, severity: MessageSeverity) = AppMessage(title, cause, severity = severity)
}
