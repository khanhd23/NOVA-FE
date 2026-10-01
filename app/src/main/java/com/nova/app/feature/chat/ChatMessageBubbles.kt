package com.nova.app.feature.chat

import com.nova.app.core.designsystem.NovaColors
import androidx.compose.ui.res.stringResource
import com.nova.app.R
import androidx.compose.ui.platform.LocalResources
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nova.app.core.model.CallSummaryUiState
import com.nova.app.core.model.ChatAttachmentKind
import com.nova.app.core.model.ChatMessage
import com.nova.app.core.ui.NovaVideoView
import com.nova.app.core.ui.VideoPosterPreview
import com.nova.app.ui.theme.*

@Composable
fun MessageBubble(
    message: ChatMessage,
    showStatus: Boolean = true,
    connectedToNewer: Boolean = false,
    connectedToOlder: Boolean = false,
    onRetryMessage: () -> Unit = {},
    onOpenMedia: (ChatMediaViewerItem) -> Unit = {},
    onCallAgain: (CallSummaryUiState) -> Unit = {},
) {
    if (message.isCallLog && message.callSummary != null) {
        CallMessageBubble(
            message = message,
            summary = message.callSummary,
            showStatus = showStatus,
            onCallAgain = onCallAgain,
        )
    } else if (message.hasAttachment || message.isVoice) {
        AttachmentMessageBubble(
            message = message,
            showStatus = showStatus,
            connectedToNewer = connectedToNewer,
            connectedToOlder = connectedToOlder,
            onRetryMessage = onRetryMessage,
            onOpenMedia = onOpenMedia,
        )
    } else {
        TextMessageBubble(
            message = message,
            showStatus = showStatus,
            connectedToNewer = connectedToNewer,
            connectedToOlder = connectedToOlder,
            onRetryMessage = onRetryMessage,
        )
    }
}

@Composable
internal fun TextMessageBubble(
    message: ChatMessage,
    showStatus: Boolean,
    connectedToNewer: Boolean,
    connectedToOlder: Boolean,
    onRetryMessage: () -> Unit,
) {
    val alignment = if (message.sentByMe) Alignment.End else Alignment.Start
    val bgColor = if (message.sentByMe) PurpleMain else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val shape = messageClusterShape(message.sentByMe, connectedToNewer, connectedToOlder)
    val failed = isMessageFailed(message)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(bgColor)
                .border(
                    width = if (failed) 1.dp else 0.dp,
                    color = if (failed) NovaColors.current.danger.copy(alpha = 0.68f) else Color.Transparent,
                    shape = shape,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp
                )
                if (message.translatedText != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message.translatedText,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                        fontSize = 12.sp
                    )
                }
            }
        }
        if (showStatus) {
            MessageStatusLabel(message = message, onRetry = onRetryMessage)
        }
    }
}

@Composable
internal fun AttachmentMessageBubble(
    message: ChatMessage,
    showStatus: Boolean,
    connectedToNewer: Boolean,
    connectedToOlder: Boolean,
    onRetryMessage: () -> Unit,
    onOpenMedia: (ChatMediaViewerItem) -> Unit,
) {
    val context = LocalContext.current
    val res = LocalResources.current
    val alignment = if (message.sentByMe) Alignment.End else Alignment.Start
    val accent = if (message.sentByMe) PurpleMain else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    val shape = messageClusterShape(message.sentByMe, connectedToNewer, connectedToOlder)
    val isImage = message.isImageAttachment

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            modifier = if (isImage) {
                Modifier.wrapContentWidth()
            } else {
                Modifier.fillMaxWidth(0.82f)
            },
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isImage -> Color.Transparent
                    message.sentByMe -> PurpleMain.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                }
            ),
            border = BorderStroke(1.dp, accent.copy(alpha = if (isImage) 0.1f else 0.24f)),
        ) {
            Column(
                modifier = Modifier.padding(if (isImage) 2.dp else 10.dp),
                verticalArrangement = Arrangement.spacedBy(if (isImage) 6.dp else 10.dp),
            ) {
                when {
                    message.isImageAttachment -> ImageAttachmentContent(
                        message = message,
                        onRetry = onRetryMessage,
                        onOpenMedia = onOpenMedia,
                    )
                    message.isVideoAttachment -> VideoAttachmentContent(
                        message = message,
                        onRetry = onRetryMessage,
                        onOpenMedia = onOpenMedia,
                    )
                    message.isAudioAttachment -> AudioAttachmentContent(message = message, context = context)
                    message.isFileAttachment -> FileAttachmentContent(message = message)
                    else -> GenericAttachmentContent(message = message)
                }

                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp
                    )
                }
            }
        }
        if (showStatus) {
            MessageStatusLabel(message = message, onRetry = onRetryMessage)
        }
    }
}

@Composable
internal fun MessageStatusLabel(
    message: ChatMessage,
    onRetry: () -> Unit = {},
) {
    MessageStatusLabel(
        sentByMe = message.sentByMe,
        isRead = message.isRead,
        status = message.status,
        onRetry = onRetry,
    )
}

@Composable
internal fun MessageStatusLabel(
    sentByMe: Boolean,
    isRead: Boolean,
    status: String?,
    onRetry: () -> Unit = {},
) {
    if (!sentByMe) {
        return
    }
    val label = messageDeliveryLabel(status = status, isRead = isRead) ?: return
    val failed = isFailedStatus(status)
    val color = if (failed) NovaColors.current.danger else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f)
    Row(
        modifier = Modifier
            .padding(top = 1.dp, end = 4.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = failed, onClick = onRetry)
            .padding(horizontal = if (failed) 6.dp else 0.dp, vertical = if (failed) 2.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (failed) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp),
            )
        }
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = if (failed) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun ImageAttachmentContent(
    message: ChatMessage,
    onRetry: () -> Unit,
    onOpenMedia: (ChatMediaViewerItem) -> Unit,
) {
    val source = resolveMediaUrl(message.attachmentPreviewUrl ?: message.attachmentUrl)
        ?: message.attachmentUrl
    if (source != null) {
        val mediaItem = message.toChatMediaViewerItem()
        val failed = isMessageFailed(message)
        var aspectRatio by remember(source, message.attachmentWidth, message.attachmentHeight) {
            mutableFloatStateOf(imageAspectRatio(message.attachmentWidth, message.attachmentHeight) ?: 1f)
        }
        BoxWithConstraints {
            val (imageWidth, imageHeight) = imageBubbleSize(aspectRatio, maxWidth)
            Box(
                modifier = Modifier
                    .width(imageWidth)
                    .height(imageHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
                    .clickable(enabled = failed || mediaItem != null) {
                        if (failed) {
                            onRetry()
                        } else {
                            mediaItem?.let(onOpenMedia)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = source,
                    contentDescription = message.attachmentName,
                    contentScale = ContentScale.Crop,
                    onSuccess = { state ->
                        safeImageAspectRatio(
                            width = state.painter.intrinsicSize.width,
                            height = state.painter.intrinsicSize.height,
                        )?.let { aspectRatio = it }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                ImageSendStateOverlay(
                    message = message,
                    onRetry = onRetry,
                )
            }
        }
    } else {
        GenericAttachmentContent(message = message)
    }
}

@Composable
internal fun ImageSendStateOverlay(
    message: ChatMessage,
    onRetry: () -> Unit,
) {
    when {
        isMessageSending(message) -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        strokeWidth = 3.dp,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.chat_sending),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        isMessageFailed(message) -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.46f))
                    .clickable(onClick = onRetry),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(NovaColors.current.danger.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.chat_tap_retry),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun VideoAttachmentContent(
    message: ChatMessage,
    onRetry: () -> Unit,
    onOpenMedia: (ChatMediaViewerItem) -> Unit,
) {
    val videoSource = resolveMediaUrl(message.attachmentUrl ?: message.attachmentPreviewUrl)
    val thumbnailSource = resolveMediaUrl(message.attachmentPreviewUrl)
    val mediaItem = message.toChatMediaViewerItem()
    val failed = isMessageFailed(message)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = 320.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = failed || mediaItem != null) {
                if (failed) {
                    onRetry()
                } else {
                    mediaItem?.let(onOpenMedia)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        VideoPosterPreview(
            videoUrl = videoSource,
            thumbnailUrl = thumbnailSource,
            modifier = Modifier.fillMaxSize(),
            label = message.attachmentName ?: stringResource(R.string.chat_video),
            showPlayBadge = true,
            playBadgeSize = 56.dp,
            playIconSize = 34.dp,
        )

        ImageSendStateOverlay(
            message = message,
            onRetry = onRetry,
        )
    }
}

@Composable
internal fun ChatMediaViewerDialog(
    item: ChatMediaViewerItem,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when (item.kind) {
                ChatAttachmentKind.Video -> NovaVideoView(
                    url = item.url,
                    modifier = Modifier.fillMaxSize(),
                    autoPlay = true,
                    showControls = true,
                )
                else -> AsyncImage(
                    model = item.url,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.48f)),
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.48f)),
                    ) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.common_download), tint = Color.White)
                    }
                }
            }

            if (item.caption.isNotBlank()) {
                Text(
                    text = item.caption,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.54f))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
internal fun AudioAttachmentContent(message: ChatMessage, context: Context) {
    val resolvedSource = resolveMediaUrl(message.attachmentUrl) ?: message.attachmentUrl
    var isPlaying by remember(message.id) { mutableStateOf(false) }
    var player by remember(message.id) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(message.id) {
        onDispose {
            player?.runCatching {
                stop()
                release()
            }
        }
    }

    fun releasePlayer() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
        isPlaying = false
    }

    fun startPlayback() {
        val source = resolvedSource ?: return
        releasePlayer()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setOnPreparedListener {
            it.start()
            isPlaying = true
        }
        mediaPlayer.setOnCompletionListener {
            releasePlayer()
        }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            releasePlayer()
            true
        }
        runCatching {
            if (source.startsWith("http://") || source.startsWith("https://")) {
                mediaPlayer.setDataSource(source)
            } else {
                mediaPlayer.setDataSource(context, Uri.parse(source))
            }
            mediaPlayer.prepareAsync()
        }.onFailure {
            releasePlayer()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable {
                if (isPlaying) {
                    releasePlayer()
                } else {
                    startPlayback()
                }
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(PurpleMain.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = PurpleMain
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.attachmentName ?: stringResource(R.string.chat_voice_note),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1
            )
            Text(
                text = message.attachmentDurationSeconds?.takeIf { it > 0 }?.let { formatCallDuration(it) } ?: stringResource(R.string.chat_voice_note),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
internal fun FileAttachmentContent(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(NovaColors.current.info.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = null,
                tint = NovaColors.current.info
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.attachmentName ?: stringResource(R.string.chat_file),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1
            )
            Text(
                text = friendlyMimeLabel(message.attachmentMimeType),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
internal fun GenericAttachmentContent(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(PurpleMain.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = PurpleMain
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.attachmentName ?: attachmentKindLabel(message),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1
            )
            Text(
                text = friendlyMimeLabel(message.attachmentMimeType),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                fontSize = 11.sp
            )
        }
    }
}
