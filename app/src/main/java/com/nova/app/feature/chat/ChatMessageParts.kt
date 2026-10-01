package com.nova.app.feature.chat

import com.nova.app.core.designsystem.NovaColors
import androidx.compose.ui.res.stringResource
import com.nova.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nova.app.core.model.CallSummaryUiState
import com.nova.app.core.model.CallType
import com.nova.app.core.model.ChatAttachmentDraft
import com.nova.app.core.model.ChatAttachmentKind
import com.nova.app.core.model.ChatMessage
import com.nova.app.core.ui.NovaTextField
import com.nova.app.core.ui.VideoPosterPreview
import com.nova.app.ui.theme.*

@Composable
internal fun TimeSeparator(
    timeLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = timeLabel.ifBlank { stringResource(R.string.chat_earlier) },
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
internal fun MessageActionsDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onDeleteForMe: () -> Unit,
    onRecall: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onDeleteThreadForMe: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Text(
                text = stringResource(R.string.chat_message_actions),
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        text = {
            Column {
                Text(
                    text = message.text.ifBlank { attachmentKindLabel(message) },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDC4D").forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { onReact(emoji) },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 18.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isMessageFailed(message)) {
                    MessageActionRow(
                        icon = Icons.Default.Refresh,
                        label = stringResource(R.string.chat_retry_sending),
                        detail = stringResource(R.string.chat_retry_sending_desc),
                        onClick = onRetry,
                    )
                }
                MessageActionRow(
                    icon = Icons.Default.DeleteOutline,
                    label = stringResource(R.string.chat_delete_for_me),
                    detail = stringResource(R.string.chat_delete_for_me_desc),
                    onClick = onDeleteForMe,
                )
                MessageActionRow(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    label = stringResource(R.string.chat_recall),
                    detail = stringResource(R.string.chat_recall_desc),
                    enabled = message.sentByMe && !isMessagePending(message),
                    onClick = onRecall,
                )
                MessageActionRow(
                    icon = Icons.Default.Edit,
                    label = stringResource(R.string.chat_edit_message),
                    detail = stringResource(R.string.chat_edit_message_desc),
                    enabled = message.sentByMe && message.text.isNotBlank() && !isMessagePending(message) && !message.status.equals("RECALLED", ignoreCase = true),
                    onClick = onEdit,
                )
                MessageActionRow(
                    icon = Icons.Default.DeleteSweep,
                    label = stringResource(R.string.chat_delete_chat),
                    detail = stringResource(R.string.chat_delete_chat_desc),
                    onClick = onDeleteThreadForMe,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close), color = PurpleMain)
            }
        }
    )
}

@Composable
internal fun EditMessageDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(message.id) { mutableStateOf(message.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = {
            Text(stringResource(R.string.chat_edit_message), color = MaterialTheme.colorScheme.onBackground)
        },
        text = {
            NovaTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = stringResource(R.string.chat_update_message_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = draft.trim().isNotBlank() && draft.trim() != message.text.trim(),
                onClick = { onSave(draft.trim()) },
            ) {
                Text(stringResource(R.string.common_save), color = PurpleMain)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
            }
        },
    )
}

@Composable
internal fun MessageActionRow(
    icon: ImageVector,
    label: String,
    detail: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.36f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 0.58f else 0.3f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
internal fun SelectedAttachmentPreview(
    attachment: ChatAttachmentDraft,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (attachment.kind) {
                ChatAttachmentKind.Image -> AsyncImage(
                    model = attachment.previewUri ?: attachment.uri,
                    contentDescription = attachment.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                ChatAttachmentKind.Video -> VideoPosterPreview(
                    videoUrl = attachment.uri.toString(),
                    thumbnailUrl = attachment.previewUri?.toString(),
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    label = stringResource(R.string.chat_video),
                    showPlayBadge = true,
                    playBadgeSize = 28.dp,
                    playIconSize = 18.dp,
                )
                else -> Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(pendingAttachmentColor(attachment.kind).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = pendingAttachmentIcon(attachment.kind),
                        contentDescription = null,
                        tint = pendingAttachmentColor(attachment.kind)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Text(
                    text = pendingAttachmentLabel(attachment),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_remove_attachment))
            }
        }
    }
}

@Composable
internal fun RecordingBanner(
    seconds: Int,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NovaColors.current.danger.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, NovaColors.current.danger.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NovaColors.current.danger.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = NovaColors.current.danger)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_recording),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = stringResource(R.string.chat_tap_stop_to_send, formatCallDuration(seconds.coerceAtLeast(1))),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                    fontSize = 11.sp
                )
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.common_cancel), color = MaterialTheme.colorScheme.onBackground)
            }
            OutlinedButton(
                onClick = onStop,
                border = BorderStroke(1.dp, NovaColors.current.danger),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NovaColors.current.danger)
            ) {
                Text(stringResource(R.string.chat_stop))
            }
        }
    }
}

@Composable
internal fun CallMessageBubble(
    message: ChatMessage,
    summary: CallSummaryUiState,
    showStatus: Boolean,
    onCallAgain: (CallSummaryUiState) -> Unit,
) {
    val accent = callAccentColor(summary)
    val alignment = if (message.sentByMe) Alignment.End else Alignment.Start
    val bubbleShape = if (message.sentByMe) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.78f),
            shape = bubbleShape,
            colors = CardDefaults.cardColors(
                containerColor = accent.copy(alpha = if (message.sentByMe) 0.12f else 0.08f)
            ),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (summary.callType == CallType.Video) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.participantName,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = callStatusText(summary),
                            color = accent,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedButton(
                        onClick = { onCallAgain(summary) },
                        modifier = Modifier.fillMaxWidth(0.82f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = accent.copy(alpha = 0.06f),
                            contentColor = accent,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.call_again),
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        if (showStatus) {
            MessageStatusLabel(message = message)
        }
    }
}

@Composable
internal fun CallHintCard(callHint: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PurpleMain.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = PurpleMain,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.chat_call_availability),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = callHint,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
internal fun TypingIndicator() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.chat_typing),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}

@Composable
internal fun LoadingMoreIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = PurpleMain
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.chat_loading_earlier),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
            fontSize = 12.sp
        )
    }
}
