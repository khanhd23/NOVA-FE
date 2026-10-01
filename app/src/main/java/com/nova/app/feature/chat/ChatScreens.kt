package com.nova.app.feature.chat

import kotlinx.coroutines.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

import com.nova.app.core.designsystem.NovaBrand

import com.nova.app.core.designsystem.NovaColors

import com.nova.app.core.i18n.localizedMessage

import androidx.compose.ui.res.stringResource
import com.nova.app.R
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import java.time.format.FormatStyle

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.model.CallSummaryUiState
import com.nova.app.core.model.CallType
import com.nova.app.core.model.ChatAttachmentDraft
import com.nova.app.core.model.ChatAttachmentKind
import com.nova.app.core.model.ChatMessage
import com.nova.app.core.model.ChatThread
import com.nova.app.core.model.ChatUiState
import com.nova.app.core.model.MessagesUiState
import com.nova.app.core.backend.BackendConfig
import com.nova.app.core.ui.NovaTextField
import com.nova.app.core.ui.NovaTopLoadingBar
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.ui.NovaVideoView
import com.nova.app.core.ui.VipAvatar
import com.nova.app.core.ui.VideoPosterPreview
import com.nova.app.ui.theme.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow

data class ChatMediaViewerItem(
    val url: String,
    val kind: ChatAttachmentKind,
    val title: String,
    val caption: String,
    val mimeType: String?,
)

@Composable
fun ChatListScreen(
    messagesState: MessagesUiState,
    onSearchClick: () -> Unit,
    onChatClick: (ChatThread) -> Unit,
    onDeleteThread: (ChatThread) -> Unit = {},
) {
    val chats = messagesState.threads
    // Only one row stays swiped open at a time, like Messenger.
    var revealedThreadId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ChatThread?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            NovaTopBar(
                title = stringResource(R.string.chat_messages),
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.chat_search_chats), tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(chats, key = { it.id }) { thread ->
                    SwipeToDeleteChatItem(
                        thread = thread,
                        revealed = revealedThreadId == thread.id,
                        onRevealedChange = { open -> revealedThreadId = if (open) thread.id else null },
                        onDeleteClick = { pendingDelete = thread },
                        onClick = { onChatClick(thread) },
                    )
                }
            }
        }
    }

    pendingDelete?.let { thread ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.chat_delete_title)) },
            text = { Text(stringResource(R.string.chat_delete_message, thread.user.name)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    revealedThreadId = null
                    onDeleteThread(thread)
                }) {
                    Text(stringResource(R.string.chat_delete), color = NovaColors.current.danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** Swipe left to reveal a delete button; tap the row again or swipe back to close it. */
@Composable
private fun SwipeToDeleteChatItem(
    thread: ChatThread,
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    onClick: () -> Unit,
) {
    val revealWidth = with(LocalDensity.current) { 88.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(revealed) {
        offsetX.animateTo(if (revealed) -revealWidth else 0f)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(NovaColors.current.danger.copy(alpha = 0.14f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(NovaColors.current.danger),
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.chat_delete_title), tint = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                // Opaque base so the delete button never shows through the translucent row.
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(thread.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onRevealedChange(offsetX.value < -revealWidth / 2) },
                        onDragCancel = { onRevealedChange(revealed) },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-revealWidth, 0f)) }
                    }
                },
        ) {
            ChatListItem(thread) {
                if (revealed) onRevealedChange(false) else onClick()
            }
        }
    }
}

@Composable
fun ChatListItem(thread: ChatThread, onClick: () -> Unit) {
    val hasUnread = thread.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (hasUnread) PurpleMain.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            VipAvatar(
                imageUrl = thread.user.photoUrl,
                contentDescription = thread.user.name,
                modifier = Modifier.size(56.dp),
                vipTierId = thread.user.vipTierId,
                premium = thread.user.premium,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (thread.online) NovaColors.current.success else NovaColors.current.neutral)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                thread.user.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (thread.typing) stringResource(R.string.chat_typing) else thread.lastMessage.ifBlank { stringResource(R.string.chat_no_messages) }.let { localizedMessage(it) },
                color = when {
                    thread.typing -> PurpleMain
                    hasUnread -> MaterialTheme.colorScheme.onBackground
                    else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                },
                fontSize = 12.sp,
                fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (thread.online) stringResource(R.string.chat_online) else stringResource(R.string.chat_offline),
                color = if (thread.online) NovaColors.current.success else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (thread.unreadCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = NovaBrand.Start,
                    shape = CircleShape,
                ) {
                    Text(
                        text = thread.unreadCount.coerceAtMost(99).toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

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
private fun ChatTimelineMessage(
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
private fun ChatComposerField(
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
private fun TextMessageBubble(
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
private fun AttachmentMessageBubble(
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
private fun MessageStatusLabel(
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
private fun MessageStatusLabel(
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
private fun ImageAttachmentContent(
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
private fun ImageSendStateOverlay(
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
private fun VideoAttachmentContent(
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
private fun ChatMediaViewerDialog(
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
private fun AudioAttachmentContent(message: ChatMessage, context: Context) {
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
private fun FileAttachmentContent(message: ChatMessage) {
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
private fun GenericAttachmentContent(message: ChatMessage) {
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

@Composable
private fun TimeSeparator(
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
private fun MessageActionsDialog(
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
private fun EditMessageDialog(
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
private fun MessageActionRow(
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
private fun SelectedAttachmentPreview(
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
private fun RecordingBanner(
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
private fun CallMessageBubble(
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
private fun CallHintCard(callHint: String) {
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
private fun TypingIndicator() {
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
private fun LoadingMoreIndicator() {
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

@Composable
private fun callStatusText(summary: CallSummaryUiState): String {
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
private fun messageDeliveryLabel(status: String?, isRead: Boolean): String? {
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

private fun isMessageSending(message: ChatMessage): Boolean {
    return isSendingStatus(message.status)
}

private fun isMessageFailed(message: ChatMessage): Boolean {
    return isFailedStatus(message.status)
}

private fun isMessagePending(message: ChatMessage): Boolean {
    return isMessageSending(message) || isMessageFailed(message)
}

private fun isSendingStatus(status: String?): Boolean {
    return status.equals("SENDING", ignoreCase = true)
}

private fun isFailedStatus(status: String?): Boolean {
    return status.equals("FAILED", ignoreCase = true)
}

private fun imageAspectRatio(width: Int?, height: Int?): Float? {
    return safeImageAspectRatio(width?.toFloat(), height?.toFloat())
}

private fun safeImageAspectRatio(width: Float?, height: Float?): Float? {
    if (width == null || height == null || !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
        return null
    }
    return (width / height).coerceIn(0.58f, 1.9f)
}

private fun imageBubbleSize(ratio: Float, maxWidth: Dp): Pair<Dp, Dp> {
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

private fun shouldShowIncomingAvatar(message: ChatMessage, newerMessage: ChatMessage?): Boolean {
    if (message.sentByMe) {
        return false
    }
    return !isSameMessageCluster(message, newerMessage)
}

private fun shouldShowTimeSeparator(message: ChatMessage, olderMessage: ChatMessage?): Boolean {
    if (olderMessage == null) {
        return true
    }
    return minuteGap(message.timeLabel, olderMessage.timeLabel)?.let { it >= 60 } ?: false
}

private fun isSameMessageCluster(message: ChatMessage, other: ChatMessage?): Boolean {
    if (other == null || message.sentByMe != other.sentByMe) {
        return false
    }
    return minuteGap(message.timeLabel, other.timeLabel)?.let { it <= 5 } ?: false
}

private fun messageClusterShape(
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

private fun minuteGap(first: String, second: String): Int? {
    val firstMinute = minuteOfDay(first) ?: return null
    val secondMinute = minuteOfDay(second) ?: return null
    val raw = kotlin.math.abs(firstMinute - secondMinute)
    return minOf(raw, (24 * 60) - raw)
}

private fun minuteOfDay(label: String): Int? {
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
private fun displayMessageTimeLabel(message: ChatMessage): String {
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
private fun callAccentColor(summary: CallSummaryUiState): Color {
    return if (summary.durationSeconds > 0) PurpleMain else NovaColors.current.danger
}

private fun formatCallDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun buildAttachmentDraft(
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

private fun startVoiceRecording(recorder: VoiceNoteRecorder): Boolean {
    return runCatching { recorder.start() }.getOrDefault(false)
}

private fun resolveDisplayName(context: Context, uri: Uri): String? {
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

private fun resolveImageDimensions(context: Context, uri: Uri): Pair<Int, Int>? {
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

private fun defaultAttachmentName(uri: Uri, kind: ChatAttachmentKind?): String {
    val lastSegment = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    return lastSegment ?: when (kind) {
        ChatAttachmentKind.Image -> "image.jpg"
        ChatAttachmentKind.Video -> "video.mp4"
        ChatAttachmentKind.Audio -> "voice.m4a"
        ChatAttachmentKind.File, null -> "file"
    }
}

private fun attachmentKindFromMimeType(mimeType: String?, name: String): ChatAttachmentKind {
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

private fun defaultMimeType(kind: ChatAttachmentKind): String {
    return when (kind) {
        ChatAttachmentKind.Image -> "image/jpeg"
        ChatAttachmentKind.Video -> "video/mp4"
        ChatAttachmentKind.Audio -> "audio/mp4"
        ChatAttachmentKind.File -> "application/octet-stream"
    }
}

private fun pendingAttachmentIcon(kind: ChatAttachmentKind): ImageVector {
    return when (kind) {
        ChatAttachmentKind.Image -> Icons.Default.Image
        ChatAttachmentKind.Video -> Icons.Default.Videocam
        ChatAttachmentKind.Audio -> Icons.Default.Mic
        ChatAttachmentKind.File -> Icons.Default.AttachFile
    }
}

@Composable
private fun pendingAttachmentColor(kind: ChatAttachmentKind): Color {
    return when (kind) {
        ChatAttachmentKind.Image -> PurpleMain
        ChatAttachmentKind.Video -> NovaColors.current.success
        ChatAttachmentKind.Audio -> NovaColors.current.warning
        ChatAttachmentKind.File -> NovaColors.current.info
    }
}

@Composable
private fun pendingAttachmentLabel(attachment: ChatAttachmentDraft): String {
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
private fun friendlyMimeLabel(mimeType: String?): String {
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
private fun attachmentKindLabel(message: ChatMessage): String {
    return when (message.attachmentKind) {
        ChatAttachmentKind.Image -> stringResource(R.string.chat_photo)
        ChatAttachmentKind.Video -> stringResource(R.string.chat_video)
        ChatAttachmentKind.Audio -> stringResource(R.string.chat_voice_message)
        ChatAttachmentKind.File -> stringResource(R.string.chat_file)
        null -> stringResource(R.string.chat_attachment)
    }
}

@Composable
private fun ChatMessage.toChatMediaViewerItem(): ChatMediaViewerItem? {
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

private fun downloadChatMedia(context: Context, item: ChatMediaViewerItem) {
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

private fun openChatMediaExternally(context: Context, item: ChatMediaViewerItem) {
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

private fun chatDownloadFileName(item: ChatMediaViewerItem): String {
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

private fun resolveMediaUrl(url: String?): String? {
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
