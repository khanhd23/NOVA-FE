package com.nova.app.feature.chat

import androidx.compose.ui.text.font.FontWeight

import kotlinx.coroutines.launch
import com.nova.app.core.designsystem.NovaBrand
import com.nova.app.core.designsystem.NovaColors
import androidx.compose.ui.res.stringResource
import com.nova.app.R
import androidx.compose.ui.platform.LocalResources
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.nova.app.core.model.CallSummaryUiState
import com.nova.app.core.model.ChatAttachmentDraft
import com.nova.app.core.model.ChatAttachmentKind
import com.nova.app.core.model.ChatMessage
import com.nova.app.core.model.ChatUiState
import com.nova.app.core.ui.NovaTopLoadingBar
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.ui.theme.*
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow

data class ChatMediaViewerItem(
    val url: String,
    val kind: ChatAttachmentKind,
    val title: String,
    val caption: String,
    val mimeType: String?,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    name: String,
    uiState: ChatUiState,
    onBack: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onCallAgain: (CallSummaryUiState) -> Unit = {},
    onSendMessage: (String, ChatAttachmentDraft?) -> Unit = { _, _ -> },
    onRetryMessage: (String) -> Unit = {},
    onTypingChanged: (Boolean) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onDeleteThreadForMe: () -> Unit = {},
    onDeleteMessageForMe: (String) -> Unit = {},
    onRecallMessage: (String) -> Unit = {},
    onEditMessage: (String, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val res = LocalResources.current
    val voiceRecorder = remember(context) { VoiceNoteRecorder(context.applicationContext) }
    val listState = rememberLazyListState()
    var message by rememberSaveable { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var confirmDeleteConversation by remember { mutableStateOf(false) }
    var pendingAttachment by remember { mutableStateOf<ChatAttachmentDraft?>(null) }
    var recordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var awaitingVoicePermission by remember { mutableStateOf(false) }
    var composerError by remember { mutableStateOf<String?>(null) }
    var canTriggerLoadMore by rememberSaveable(uiState.thread.id) { mutableStateOf(true) }
    var hasScrolledUp by rememberSaveable(uiState.thread.id) { mutableStateOf(false) }
    var selectedActionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedMedia by remember { mutableStateOf<ChatMediaViewerItem?>(null) }
    val expandedTimeIds = remember(uiState.thread.id) { mutableStateListOf<String>() }
    val localReactions = remember(uiState.thread.id) { mutableStateMapOf<String, String>() }
    val newestMessageId = uiState.messages.firstOrNull()?.id

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingAttachment = buildAttachmentDraft(context, uri, ChatAttachmentKind.Image)
            showAttachmentMenu = false
            composerError = null
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingAttachment = buildAttachmentDraft(context, uri, ChatAttachmentKind.Video)
            showAttachmentMenu = false
            composerError = null
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingAttachment = buildAttachmentDraft(context, uri, null)
            showAttachmentMenu = false
            composerError = null
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && awaitingVoicePermission) {
            awaitingVoicePermission = false
            if (startVoiceRecording(voiceRecorder)) {
                recordingVoice = true
                recordingSeconds = 0
                pendingAttachment = null
                composerError = null
            } else {
                composerError = res.getString(R.string.chat_err_voice_start)
            }
        } else if (!granted) {
            awaitingVoicePermission = false
            composerError = res.getString(R.string.chat_err_mic_permission)
        }
    }

    LaunchedEffect(recordingVoice) {
        if (!recordingVoice) {
            recordingSeconds = 0
            return@LaunchedEffect
        }
        while (recordingVoice) {
            delay(1000)
            if (recordingVoice) {
                recordingSeconds += 1
            }
        }
    }

    fun sendComposerMessage() {
        val text = message.trim()
        if (text.isBlank() && pendingAttachment == null) {
            return
        }
        onTypingChanged(false)
        onSendMessage(text, pendingAttachment)
        message = ""
        pendingAttachment = null
        showAttachmentMenu = false
        composerError = null
    }

    fun stopVoiceRecording() {
        val result = voiceRecorder.stop()
        recordingVoice = false
        awaitingVoicePermission = false
        if (result != null) {
            pendingAttachment = ChatAttachmentDraft(
                uri = Uri.fromFile(result.file),
                kind = ChatAttachmentKind.Audio,
                name = res.getString(R.string.chat_voice_note),
                mimeType = "audio/mp4",
                durationSeconds = result.durationSeconds,
                previewUri = Uri.fromFile(result.file),
            )
            composerError = null
        } else {
            composerError = res.getString(R.string.chat_err_voice_failed)
        }
    }

    fun cancelVoiceRecording() {
        voiceRecorder.cancel()
        recordingVoice = false
        awaitingVoicePermission = false
        recordingSeconds = 0
    }

    fun handleVoiceAction() {
        if (recordingVoice) {
            stopVoiceRecording()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (startVoiceRecording(voiceRecorder)) {
                recordingVoice = true
                recordingSeconds = 0
                pendingAttachment = null
                composerError = null
                showAttachmentMenu = false
            } else {
                composerError = res.getString(R.string.chat_err_voice_start)
            }
        } else {
            awaitingVoicePermission = true
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState.thread.id) {
        canTriggerLoadMore = true
        hasScrolledUp = false
    }

    LaunchedEffect(uiState.thread.id, message) {
        if (message.isBlank()) {
            onTypingChanged(false)
            return@LaunchedEffect
        }
        onTypingChanged(true)
        delay(1500)
        onTypingChanged(false)
    }

    DisposableEffect(uiState.thread.id) {
        onDispose { onTypingChanged(false) }
    }

    LaunchedEffect(uiState.thread.id, uiState.loading) {
        if (!uiState.loading && uiState.messages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(uiState.thread.id, newestMessageId) {
        if (!uiState.loading && newestMessageId != null) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(listState, uiState.hasMore, uiState.loadingMore, uiState.messages.size, uiState.thread.id) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            val firstVisible = visibleItems.minOfOrNull { it.index } ?: 0
            val lastVisible = visibleItems.maxOfOrNull { it.index } ?: 0
            Triple(firstVisible, lastVisible, totalItems)
        }.collect { (firstVisible, lastVisible, totalItems) ->
            if (firstVisible > 0) {
                hasScrolledUp = true
            }
            val nearTop = totalItems > 0 && lastVisible >= totalItems - 2
            if (!nearTop) {
                canTriggerLoadMore = true
            }
            if (hasScrolledUp && nearTop && canTriggerLoadMore && uiState.hasMore && !uiState.loadingMore) {
                canTriggerLoadMore = false
                onLoadMore()
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                NovaTopBar(
                    title = name,
                    subtitle = when {
                        uiState.typing || uiState.thread.typing -> stringResource(R.string.chat_typing)
                        uiState.thread.online -> stringResource(R.string.chat_online)
                        else -> stringResource(R.string.chat_offline)
                    },
                    onBack = onBack,
                    onTitleClick = onOpenProfile,
                    actions = {
                        IconButton(onClick = onVoiceCall) {
                            Icon(Icons.Default.Call, contentDescription = stringResource(R.string.chat_voice_call), tint = MaterialTheme.colorScheme.onBackground)
                        }
                        IconButton(onClick = onVideoCall) {
                            Icon(Icons.Default.Videocam, contentDescription = stringResource(R.string.chat_video_call), tint = MaterialTheme.colorScheme.onBackground)
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.settings_title), tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                )
                NovaTopLoadingBar(visible = uiState.loading)
            }
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                if (showAttachmentMenu) {
                    ChatActionMenu(
                        isRecordingVoice = recordingVoice,
                        onPhotoClick = {
                            showAttachmentMenu = false
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onVideoClick = {
                            showAttachmentMenu = false
                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        onVoiceClick = { handleVoiceAction() },
                        onFileClick = {
                            showAttachmentMenu = false
                            filePicker.launch(arrayOf("*/*"))
                        },
                    )
                }

                if (recordingVoice) {
                    RecordingBanner(
                        seconds = recordingSeconds,
                        onStop = { stopVoiceRecording() },
                        onCancel = { cancelVoiceRecording() },
                    )
                }

                pendingAttachment?.let { attachment ->
                    SelectedAttachmentPreview(
                        attachment = attachment,
                        onRemove = {
                            pendingAttachment = null
                            composerError = null
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showAttachmentMenu = !showAttachmentMenu },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (showAttachmentMenu) NovaBrand.Start else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            if (showAttachmentMenu) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = stringResource(R.string.chat_more),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    ChatComposerField(
                        value = message,
                        onValueChange = { message = it },
                        placeholder = if (pendingAttachment != null) stringResource(R.string.chat_add_caption) else stringResource(R.string.chat_type_hint),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = { sendComposerMessage() },
                        enabled = message.isNotBlank() || pendingAttachment != null,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (message.isNotBlank() || pendingAttachment != null) PurpleMain else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send), tint = MaterialTheme.colorScheme.onBackground)
                    }
                }

                composerError?.let {
                    Text(
                        text = it,
                        color = NovaColors.current.danger,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                if (uiState.typing) {
                    item(key = "typing") {
                        TypingIndicator()
                    }
                }

                itemsIndexed(
                    items = uiState.messages,
                    key = { _, item -> item.id },
                ) { index, item ->
                    val newerMessage = uiState.messages.getOrNull(index - 1)
                    val olderMessage = uiState.messages.getOrNull(index + 1)
                    val connectedToNewer = isSameMessageCluster(item, newerMessage)
                    val connectedToOlder = isSameMessageCluster(item, olderMessage)
                    val hasPersistentTime = shouldShowTimeSeparator(item, olderMessage)
                    ChatTimelineMessage(
                        message = item,
                        peerAvatarUrl = uiState.thread.user.photoUrl,
                        showIncomingAvatar = !item.sentByMe && !connectedToNewer,
                        showTimeSeparator = hasPersistentTime,
                        showInlineTime = !hasPersistentTime && expandedTimeIds.contains(item.id),
                        showStatus = item.sentByMe && (!connectedToNewer || expandedTimeIds.contains(item.id)),
                        connectedToNewer = connectedToNewer,
                        connectedToOlder = connectedToOlder,
                        reaction = localReactions[item.id],
                        onToggleTime = {
                            if (!hasPersistentTime) {
                                if (expandedTimeIds.contains(item.id)) {
                                    expandedTimeIds.remove(item.id)
                                } else {
                                    expandedTimeIds.add(item.id)
                                }
                            }
                        },
                        onLongPress = { selectedActionMessage = item },
                        onRetryMessage = { onRetryMessage(item.id) },
                        onOpenMedia = { media -> selectedMedia = media },
                        onCallAgain = onCallAgain,
                    )
                }

                if (uiState.loadingMore) {
                    item(key = "loading_more") {
                        LoadingMoreIndicator()
                    }
                }

                if (uiState.messages.isNotEmpty() && uiState.callHint.isNotBlank()) {
                    item(key = "call_hint") {
                        CallHintCard(callHint = uiState.callHint)
                    }
                }
            }

            if (uiState.loading && uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = PurpleMain)
                }
            }
        }

        if (showSettings) {
            ChatSettingsDialog(
                name = name,
                onDismiss = { showSettings = false },
                onDeleteConversation = {
                    showSettings = false
                    confirmDeleteConversation = true
                },
            )
        }
        if (confirmDeleteConversation) {
            AlertDialog(
                onDismissRequest = { confirmDeleteConversation = false },
                title = { Text(stringResource(R.string.chat_delete_title)) },
                text = { Text(stringResource(R.string.chat_delete_message, name)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDeleteConversation = false
                        onDeleteThreadForMe()
                    }) {
                        Text(stringResource(R.string.chat_delete), color = NovaColors.current.danger, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteConversation = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
        selectedActionMessage?.let { actionMessage ->
            MessageActionsDialog(
                message = actionMessage,
                onDismiss = { selectedActionMessage = null },
                onReact = { emoji ->
                    localReactions[actionMessage.id] = emoji
                    selectedActionMessage = null
                },
                onDeleteForMe = {
                    onDeleteMessageForMe(actionMessage.id)
                    selectedActionMessage = null
                },
                onRecall = {
                    onRecallMessage(actionMessage.id)
                    selectedActionMessage = null
                },
                onEdit = {
                    editingMessage = actionMessage
                    selectedActionMessage = null
                },
                onRetry = {
                    onRetryMessage(actionMessage.id)
                    selectedActionMessage = null
                },
                onDeleteThreadForMe = {
                    onDeleteThreadForMe()
                    selectedActionMessage = null
                },
            )
        }
        editingMessage?.let { editTarget ->
            EditMessageDialog(
                message = editTarget,
                onDismiss = { editingMessage = null },
                onSave = { nextText ->
                    onEditMessage(editTarget.id, nextText)
                    editingMessage = null
                },
            )
        }
        selectedMedia?.let { media ->
            ChatMediaViewerDialog(
                item = media,
                onDismiss = { selectedMedia = null },
                onDownload = { downloadChatMedia(context, media) },
            )
        }
    }
}

@Composable
fun ChatActionMenu(
    isRecordingVoice: Boolean,
    onPhotoClick: () -> Unit,
    onVideoClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onFileClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ActionIcon(Icons.Default.Image, stringResource(R.string.chat_photo), PurpleMain, onClick = onPhotoClick)
        ActionIcon(Icons.Default.Videocam, stringResource(R.string.chat_video), NovaColors.current.success, onClick = onVideoClick)
        ActionIcon(
            if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
            if (isRecordingVoice) stringResource(R.string.chat_stop) else stringResource(R.string.chat_voice),
            NovaColors.current.warning,
            onClick = onVoiceClick
        )
        ActionIcon(Icons.Default.AttachFile, stringResource(R.string.chat_file), NovaColors.current.info, onClick = onFileClick)
    }
}

@Composable
fun ActionIcon(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit = {},
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
    }
}

@Composable
fun ChatSettingsDialog(
    name: String,
    onDismiss: () -> Unit,
    onDeleteConversation: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text(stringResource(R.string.chat_with, name), color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column {
                ChatSettingItem(Icons.Default.Edit, stringResource(R.string.chat_change_nickname))
                ChatSettingItem(Icons.Default.Palette, stringResource(R.string.chat_change_theme))
                ChatSettingItem(Icons.Default.Image, stringResource(R.string.chat_view_media))
                ChatSettingItem(Icons.Default.Block, stringResource(R.string.chat_block_user), NovaColors.current.danger)
                ChatSettingItem(Icons.Default.Report, stringResource(R.string.chat_report_user), NovaColors.current.danger)
                ChatSettingItem(
                    Icons.Default.Delete,
                    stringResource(R.string.chat_delete_conversation),
                    NovaColors.current.danger,
                    onClick = onDeleteConversation,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = PurpleMain) }
        }
    )
}

@Composable
fun ChatSettingItem(
    icon: ImageVector,
    label: String,
    color: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, color = color, fontSize = 14.sp)
    }
}

@Composable
fun MessageBubble(text: String, isMe: Boolean, status: String? = null) {
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val bgColor = if (isMe) PurpleMain else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val shape = if (isMe) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }
    
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(bgColor)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(text, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
        }
        MessageStatusLabel(sentByMe = isMe, isRead = false, status = status)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatTimelineMessage(
    message: ChatMessage,
    peerAvatarUrl: String,
    showIncomingAvatar: Boolean,
    showTimeSeparator: Boolean,
    showInlineTime: Boolean,
    showStatus: Boolean,
    connectedToNewer: Boolean,
    connectedToOlder: Boolean,
    reaction: String?,
    onToggleTime: () -> Unit,
    onLongPress: () -> Unit,
    onRetryMessage: () -> Unit,
    onOpenMedia: (ChatMediaViewerItem) -> Unit,
    onCallAgain: (CallSummaryUiState) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if ((showTimeSeparator || showInlineTime) && message.timeLabel.isNotBlank()) {
            TimeSeparator(
                timeLabel = displayMessageTimeLabel(message),
                onClick = onToggleTime,
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (message.sentByMe) Arrangement.End else Arrangement.Start,
        ) {
            if (!message.sentByMe) {
                Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.BottomStart) {
                    if (showIncomingAvatar) {
                        AsyncImage(
                            model = peerAvatarUrl,
                            contentDescription = stringResource(R.string.chat_sender_avatar),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .combinedClickable(
                        onClick = onToggleTime,
                        onLongClick = onLongPress,
                    )
            ) {
                Column(horizontalAlignment = if (message.sentByMe) Alignment.End else Alignment.Start) {
                    MessageBubble(
                        message = message,
                        showStatus = showStatus,
                        connectedToNewer = connectedToNewer,
                        connectedToOlder = connectedToOlder,
                        onRetryMessage = onRetryMessage,
                        onOpenMedia = onOpenMedia,
                        onCallAgain = onCallAgain,
                    )
                    reaction?.let {
                        Text(
                            text = it,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .padding(top = 2.dp, end = if (message.sentByMe) 8.dp else 0.dp, start = if (message.sentByMe) 0.dp else 8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(if (connectedToNewer || connectedToOlder) 2.dp else 8.dp))
    }
}

@Composable
internal fun ChatComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(28.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
        ),
        cursorBrush = SolidColor(PurpleMain),
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.64f))
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) PurpleMain.copy(alpha = 0.62f) else Color.Transparent,
                shape = shape,
            ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
                        fontSize = 14.sp,
                    )
                }
                innerTextField()
            }
        },
    )
}
