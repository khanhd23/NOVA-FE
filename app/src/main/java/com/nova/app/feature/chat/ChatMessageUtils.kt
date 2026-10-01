package com.nova.app.feature.chat

import com.nova.app.core.designsystem.NovaColors
import androidx.compose.ui.res.stringResource
import com.nova.app.R
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.FormatStyle
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.model.CallSummaryUiState
import com.nova.app.core.model.ChatAttachmentDraft
import com.nova.app.core.model.ChatAttachmentKind
import com.nova.app.core.model.ChatMessage
import com.nova.app.core.backend.BackendConfig
import com.nova.app.ui.theme.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun callStatusText(summary: CallSummaryUiState): String {
    return when {
        summary.durationSeconds > 0 -> stringResource(R.string.call_connected_duration, formatCallDuration(summary.durationSeconds))
        summary.endReason == CallEndReason.Missed -> stringResource(R.string.call_missed)
        summary.endReason == CallEndReason.Declined -> stringResource(R.string.call_declined)
        summary.endReason == CallEndReason.Rejected -> stringResource(R.string.call_rejected)
        summary.endReason == CallEndReason.Busy -> stringResource(R.string.call_busy)
        summary.endReason == CallEndReason.Canceled -> stringResource(R.string.call_canceled)
        summary.endReason == CallEndReason.NoAnswer -> stringResource(R.string.call_no_answer)
        summary.endReason == CallEndReason.Dropped -> stringResource(R.string.call_dropped)
        else -> stringResource(R.string.call_ended)
    }
}

@Composable
internal fun messageDeliveryLabel(status: String?, isRead: Boolean): String? {
    val normalized = status
        ?.trim()
        ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        ?.uppercase(Locale.ROOT)

    return when {
        isRead || normalized == "SEEN" || normalized == "READ" -> stringResource(R.string.chat_status_seen)
        normalized == "DELIVERED" || normalized == "RECEIVED" -> stringResource(R.string.chat_status_delivered)
        normalized == "SENDING" -> stringResource(R.string.chat_sending)
        normalized == "FAILED" -> stringResource(R.string.chat_status_failed)
        normalized == "SENT" || normalized == null -> stringResource(R.string.chat_status_sent)
        normalized == "RECALLED" -> null
        else -> stringResource(R.string.chat_status_sent)
    }
}

internal fun isMessageSending(message: ChatMessage): Boolean {
    return isSendingStatus(message.status)
}

internal fun isMessageFailed(message: ChatMessage): Boolean {
    return isFailedStatus(message.status)
}

internal fun isMessagePending(message: ChatMessage): Boolean {
    return isMessageSending(message) || isMessageFailed(message)
}

internal fun isSendingStatus(status: String?): Boolean {
    return status.equals("SENDING", ignoreCase = true)
}

internal fun isFailedStatus(status: String?): Boolean {
    return status.equals("FAILED", ignoreCase = true)
}

internal fun imageAspectRatio(width: Int?, height: Int?): Float? {
    return safeImageAspectRatio(width?.toFloat(), height?.toFloat())
}

internal fun safeImageAspectRatio(width: Float?, height: Float?): Float? {
    if (width == null || height == null || !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
        return null
    }
    return (width / height).coerceIn(0.58f, 1.9f)
}

internal fun imageBubbleSize(ratio: Float, maxWidth: Dp): Pair<Dp, Dp> {
    val safeRatio = ratio.coerceIn(0.58f, 1.9f)
    val maxBubbleWidth = if (maxWidth == Dp.Infinity) 292.dp else maxWidth.coerceAtMost(292.dp)
    val minBubbleWidth = if (maxBubbleWidth < 128.dp) maxBubbleWidth else 128.dp
    val preferredWidth = when {
        safeRatio < 0.72f -> 220.dp
        safeRatio > 1.35f -> 292.dp
        else -> 260.dp
    }.coerceAtMost(maxBubbleWidth)
    val rawHeight = preferredWidth / safeRatio
    val height = rawHeight.coerceIn(128.dp, 360.dp)
    val width = (height * safeRatio).coerceIn(minBubbleWidth, maxBubbleWidth)
    return width to height
}

internal fun shouldShowIncomingAvatar(message: ChatMessage, newerMessage: ChatMessage?): Boolean {
    if (message.sentByMe) {
        return false
    }
    return !isSameMessageCluster(message, newerMessage)
}

internal fun shouldShowTimeSeparator(message: ChatMessage, olderMessage: ChatMessage?): Boolean {
    if (olderMessage == null) {
        return true
    }
    return minuteGap(message.timeLabel, olderMessage.timeLabel)?.let { it >= 60 } ?: false
}

internal fun isSameMessageCluster(message: ChatMessage, other: ChatMessage?): Boolean {
    if (other == null || message.sentByMe != other.sentByMe) {
        return false
    }
    return minuteGap(message.timeLabel, other.timeLabel)?.let { it <= 5 } ?: false
}

internal fun messageClusterShape(
    sentByMe: Boolean,
    connectedToNewer: Boolean,
    connectedToOlder: Boolean,
): RoundedCornerShape {
    val full = 18.dp
    val tight = 6.dp
    val singleMessageAsTop = !connectedToNewer && !connectedToOlder
    return if (sentByMe) {
        RoundedCornerShape(
            topStart = full,
            topEnd = if (connectedToOlder) tight else full,
            bottomEnd = if (connectedToNewer || singleMessageAsTop) tight else full,
            bottomStart = full,
        )
    } else {
        RoundedCornerShape(
            topStart = if (connectedToOlder) tight else full,
            topEnd = full,
            bottomEnd = full,
            bottomStart = if (connectedToNewer || singleMessageAsTop) tight else full,
        )
    }
}

internal fun minuteGap(first: String, second: String): Int? {
    val firstMinute = minuteOfDay(first) ?: return null
    val secondMinute = minuteOfDay(second) ?: return null
    val raw = kotlin.math.abs(firstMinute - secondMinute)
    return minOf(raw, (24 * 60) - raw)
}

internal fun minuteOfDay(label: String): Int? {
    val parts = label.trim().split(":")
    if (parts.size < 2) {
        return null
    }
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].take(2).toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) {
        return null
    }
    return hour * 60 + minute
}

@Composable
internal fun displayMessageTimeLabel(message: ChatMessage): String {
    val locale = LocalConfiguration.current.locales[0]
    val instant = message.createdAt?.let {
        runCatching { Instant.parse(it) }.getOrNull()
    } ?: return message.timeLabel
    val dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
    val time = dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    return if (dateTime.toLocalDate() == LocalDate.now()) {
        time
    } else {
        "${dateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))} $time"
    }
}

@Composable
internal fun callAccentColor(summary: CallSummaryUiState): Color {
    return if (summary.durationSeconds > 0) PurpleMain else NovaColors.current.danger
}

internal fun formatCallDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

internal fun buildAttachmentDraft(
    context: Context,
    uri: Uri,
    forcedKind: ChatAttachmentKind?,
): ChatAttachmentDraft {
    val mimeType = context.contentResolver.getType(uri)
    val name = resolveDisplayName(context, uri) ?: defaultAttachmentName(uri, forcedKind)
    val kind = forcedKind ?: attachmentKindFromMimeType(mimeType, name)
    val dimensions = if (kind == ChatAttachmentKind.Image) {
        resolveImageDimensions(context, uri)
    } else {
        null
    }
    return ChatAttachmentDraft(
        uri = uri,
        kind = kind,
        name = name,
        mimeType = mimeType ?: defaultMimeType(kind),
        durationSeconds = null,
        previewUri = if (kind == ChatAttachmentKind.Image) uri else null,
        width = dimensions?.first,
        height = dimensions?.second,
    )
}

internal fun startVoiceRecording(recorder: VoiceNoteRecorder): Boolean {
    return runCatching { recorder.start() }.getOrDefault(false)
}

internal fun resolveDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)
            } else {
                null
            }
        }
    }.getOrNull()
}

internal fun resolveImageDimensions(context: Context, uri: Uri): Pair<Int, Int>? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth to options.outHeight
            } else {
                null
            }
        }
    }.getOrNull()
}

internal fun defaultAttachmentName(uri: Uri, kind: ChatAttachmentKind?): String {
    val lastSegment = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    return lastSegment ?: when (kind) {
        ChatAttachmentKind.Image -> "image.jpg"
        ChatAttachmentKind.Video -> "video.mp4"
        ChatAttachmentKind.Audio -> "voice.m4a"
        ChatAttachmentKind.File, null -> "file"
    }
}

internal fun attachmentKindFromMimeType(mimeType: String?, name: String): ChatAttachmentKind {
    val resolvedMime = mimeType?.lowercase(Locale.ROOT).orEmpty()
    return when {
        resolvedMime.startsWith("image/") -> ChatAttachmentKind.Image
        resolvedMime.startsWith("video/") -> ChatAttachmentKind.Video
        resolvedMime.startsWith("audio/") -> ChatAttachmentKind.Audio
        name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) || name.endsWith(".png", ignoreCase = true) -> ChatAttachmentKind.Image
        name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".mkv", ignoreCase = true) || name.endsWith(".webm", ignoreCase = true) -> ChatAttachmentKind.Video
        name.endsWith(".m4a", ignoreCase = true) || name.endsWith(".aac", ignoreCase = true) || name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".wav", ignoreCase = true) -> ChatAttachmentKind.Audio
        else -> ChatAttachmentKind.File
    }
}

internal fun defaultMimeType(kind: ChatAttachmentKind): String {
    return when (kind) {
        ChatAttachmentKind.Image -> "image/jpeg"
        ChatAttachmentKind.Video -> "video/mp4"
        ChatAttachmentKind.Audio -> "audio/mp4"
        ChatAttachmentKind.File -> "application/octet-stream"
    }
}

internal fun pendingAttachmentIcon(kind: ChatAttachmentKind): ImageVector {
    return when (kind) {
        ChatAttachmentKind.Image -> Icons.Default.Image
        ChatAttachmentKind.Video -> Icons.Default.Videocam
        ChatAttachmentKind.Audio -> Icons.Default.Mic
        ChatAttachmentKind.File -> Icons.Default.AttachFile
    }
}

@Composable
internal fun pendingAttachmentColor(kind: ChatAttachmentKind): Color {
    return when (kind) {
        ChatAttachmentKind.Image -> PurpleMain
        ChatAttachmentKind.Video -> NovaColors.current.success
        ChatAttachmentKind.Audio -> NovaColors.current.warning
        ChatAttachmentKind.File -> NovaColors.current.info
    }
}

@Composable
internal fun pendingAttachmentLabel(attachment: ChatAttachmentDraft): String {
    return when (attachment.kind) {
        ChatAttachmentKind.Image -> stringResource(R.string.chat_photo_ready)
        ChatAttachmentKind.Video -> stringResource(R.string.chat_video_ready)
        ChatAttachmentKind.Audio -> attachment.durationSeconds
            ?.let { stringResource(R.string.chat_voice_note_duration, formatCallDuration(it)) }
            ?: stringResource(R.string.chat_voice_note)
        ChatAttachmentKind.File -> friendlyMimeLabel(attachment.mimeType)
    }
}

@Composable
internal fun friendlyMimeLabel(mimeType: String?): String {
    if (mimeType.isNullOrBlank()) {
        return stringResource(R.string.chat_attachment)
    }
    return when {
        mimeType.startsWith("image/") -> stringResource(R.string.chat_image)
        mimeType.startsWith("video/") -> stringResource(R.string.chat_video)
        mimeType.startsWith("audio/") -> stringResource(R.string.chat_audio)
        mimeType == "application/pdf" -> "PDF"
        mimeType.contains("word", ignoreCase = true) -> stringResource(R.string.chat_document)
        mimeType.contains("zip", ignoreCase = true) -> stringResource(R.string.chat_archive)
        else -> mimeType.substringAfter('/').uppercase(Locale.ROOT)
    }
}

@Composable
internal fun attachmentKindLabel(message: ChatMessage): String {
    return when (message.attachmentKind) {
        ChatAttachmentKind.Image -> stringResource(R.string.chat_photo)
        ChatAttachmentKind.Video -> stringResource(R.string.chat_video)
        ChatAttachmentKind.Audio -> stringResource(R.string.chat_voice_message)
        ChatAttachmentKind.File -> stringResource(R.string.chat_file)
        null -> stringResource(R.string.chat_attachment)
    }
}

@Composable
internal fun ChatMessage.toChatMediaViewerItem(): ChatMediaViewerItem? {
    val kind = attachmentKind?.takeIf { it == ChatAttachmentKind.Image || it == ChatAttachmentKind.Video } ?: return null
    val url = resolveMediaUrl(attachmentUrl ?: attachmentPreviewUrl)
        ?: resolveMediaUrl(attachmentPreviewUrl)
        ?: return null
    return ChatMediaViewerItem(
        url = url,
        kind = kind,
        title = attachmentName?.takeIf { it.isNotBlank() } ?: attachmentKindLabel(this),
        caption = text,
        mimeType = attachmentMimeType ?: defaultMimeType(kind),
    )
}

internal fun downloadChatMedia(context: Context, item: ChatMediaViewerItem) {
    val uri = Uri.parse(item.url)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme == "http" || scheme == "https") {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager == null) {
            Toast.makeText(context, context.getString(R.string.chat_download_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = chatDownloadFileName(item)
        runCatching {
            val request = DownloadManager.Request(uri)
                .setTitle(fileName)
                .setDescription(context.getString(R.string.chat_downloading_item, item.title))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setMimeType(item.mimeType ?: defaultMimeType(item.kind))
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            manager.enqueue(request)
        }.onSuccess {
            Toast.makeText(context, context.getString(R.string.chat_downloading_to), Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.chat_download_failed), Toast.LENGTH_SHORT).show()
        }
    } else {
        openChatMediaExternally(context, item)
    }
}

internal fun openChatMediaExternally(context: Context, item: ChatMediaViewerItem) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(item.url), item.mimeType ?: defaultMimeType(item.kind))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.chat_open_media)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.chat_open_media_failed), Toast.LENGTH_SHORT).show()
    }
}

internal fun chatDownloadFileName(item: ChatMediaViewerItem): String {
    val fallback = if (item.kind == ChatAttachmentKind.Video) "nova-video" else "nova-photo"
    val sanitized = (item.title.ifBlank { fallback })
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .trim()
        .ifBlank { fallback }
    if (sanitized.substringAfterLast('.', missingDelimiterValue = "").isNotBlank()) {
        return sanitized
    }
    val extension = when {
        item.mimeType.equals("image/png", ignoreCase = true) -> "png"
        item.mimeType.equals("image/webp", ignoreCase = true) -> "webp"
        item.mimeType.equals("video/quicktime", ignoreCase = true) -> "mov"
        item.mimeType.equals("video/webm", ignoreCase = true) -> "webm"
        item.kind == ChatAttachmentKind.Video -> "mp4"
        else -> "jpg"
    }
    return "$sanitized.$extension"
}

internal fun resolveMediaUrl(url: String?): String? {
    if (url.isNullOrBlank()) {
        return null
    }
    return when {
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> url
        url.startsWith("content://", ignoreCase = true) || url.startsWith("file://", ignoreCase = true) -> url
        url.startsWith("/") -> BackendConfig.baseUrl.trimEnd('/') + url
        else -> BackendConfig.baseUrl.trimEnd('/') + "/" + url.trimStart('/')
    }
}
