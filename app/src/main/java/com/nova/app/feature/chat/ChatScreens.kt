package com.nova.app.feature.chat

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.media.MediaPlayer
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.ui.VipAvatar
import com.nova.app.ui.theme.*
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow

@Composable
fun ChatListScreen(
    messagesState: MessagesUiState,
    onSearchClick: () -> Unit,
    onChatClick: (ChatThread) -> Unit,
) {
    val chats = messagesState.threads
    var searchQuery by remember { mutableStateOf("") }
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            NovaTopBar(
                title = "Messages",
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = "Search friends", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )
            
            NovaTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search by name or ID...",
                leadingIcon = Icons.Default.Search,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(chats) { thread ->
                    ChatListItem(thread) { onChatClick(thread) }
                }
            }
        }
    }
}

@Composable
fun ChatListItem(thread: ChatThread, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
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
                    .background(if (thread.online) Color(0xFF22C55E) else Color(0xFF6B7280))
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
                text = if (thread.typing) "Typing..." else thread.lastMessage.ifBlank { "No messages yet" },
                color = if (thread.typing) PurpleMain else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (thread.online) "Online" else "Offline",
                color = if (thread.online) Color(0xFF22C55E) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (thread.unreadCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = PurpleMain,
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

@Composable
fun ChatDetailScreen(
    name: String,
    uiState: ChatUiState,
    onBack: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onIncomingVoiceCall: () -> Unit = {},
    onIncomingVideoCall: () -> Unit = {},
    onCallAgain: (CallSummaryUiState) -> Unit = {},
    onSendMessage: (String, ChatAttachmentDraft?) -> Unit = { _, _ -> },
    onLoadMore: () -> Unit = {},
) {
    val context = LocalContext.current
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
                composerError = "Unable to start voice recording"
            }
        } else if (!granted) {
            awaitingVoicePermission = false
            composerError = "Microphone permission is required for voice messages"
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
                name = "Voice note",
                mimeType = "audio/mp4",
                durationSeconds = result.durationSeconds,
                previewUri = Uri.fromFile(result.file),
            )
            composerError = null
        } else {
            composerError = "Voice recording failed"
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
                composerError = "Unable to start voice recording"
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
            NovaTopBar(
                title = name,
                subtitle = when {
                    uiState.typing || uiState.thread.typing -> "Typing..."
                    uiState.thread.online -> "Online"
                    else -> "Offline"
                },
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onVoiceCall) {
                        Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onVideoCall) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )
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
                            .background(if (showAttachmentMenu) PurpleMain else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            if (showAttachmentMenu) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    NovaTextField(
                        value = message,
                        onValueChange = { message = it },
                        placeholder = if (pendingAttachment != null) "Add a caption..." else "Type...",
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
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }

                composerError?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFF6B6B),
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
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                if (uiState.typing) {
                    item(key = "typing") {
                        TypingIndicator()
                    }
                }

                items(
                    items = uiState.messages,
                    key = { it.id },
                ) { item ->
                    MessageBubble(
                        message = item,
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
                onIncomingVoiceCall = onIncomingVoiceCall,
                onIncomingVideoCall = onIncomingVideoCall,
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
        ActionIcon(Icons.Default.Image, "Photo", PurpleMain, onClick = onPhotoClick)
        ActionIcon(Icons.Default.Videocam, "Video", Color(0xFF4CAF50), onClick = onVideoClick)
        ActionIcon(
            if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
            if (isRecordingVoice) "Stop" else "Voice",
            Color(0xFFFF9800),
            onClick = onVoiceClick
        )
        ActionIcon(Icons.Default.AttachFile, "File", Color(0xFF2196F3), onClick = onFileClick)
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
    onIncomingVoiceCall: () -> Unit = {},
    onIncomingVideoCall: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Chat with $name", color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column {
                Text(
                    text = "Call demos",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ChatSettingItem(Icons.Default.Call, "Outgoing Voice Call")
                ChatSettingItem(Icons.Default.Videocam, "Outgoing Video Call")
                ChatSettingItem(Icons.AutoMirrored.Filled.CallReceived, "Simulate Incoming Voice Call", PurpleMain) {
                    onIncomingVoiceCall()
                    onDismiss()
                }
                ChatSettingItem(Icons.Default.VideoCall, "Simulate Incoming Video Call", PurplePink) {
                    onIncomingVideoCall()
                    onDismiss()
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                ChatSettingItem(Icons.Default.Edit, "Change Nickname")
                ChatSettingItem(Icons.Default.Palette, "Change Theme")
                ChatSettingItem(Icons.Default.Image, "View Media & Files")
                ChatSettingItem(Icons.Default.Block, "Block User", Color.Red)
                ChatSettingItem(Icons.Default.Report, "Report User", Color.Red)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = PurpleMain) }
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
        if (isMe && status != null) {
            Text(status, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    onCallAgain: (CallSummaryUiState) -> Unit = {},
) {
    if (message.isCallLog && message.callSummary != null) {
        CallMessageBubble(
            message = message,
            summary = message.callSummary,
            onCallAgain = onCallAgain,
        )
    } else if (message.hasAttachment || message.isVoice) {
        AttachmentMessageBubble(message = message)
    } else {
        TextMessageBubble(message = message)
    }
}

@Composable
private fun TextMessageBubble(message: ChatMessage) {
    val alignment = if (message.sentByMe) Alignment.End else Alignment.Start
    val bgColor = if (message.sentByMe) PurpleMain else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val shape = if (message.sentByMe) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(bgColor)
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
        if (message.sentByMe && message.isRead) {
            Text(
                text = "Seen",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun AttachmentMessageBubble(message: ChatMessage) {
    val context = LocalContext.current
    val alignment = if (message.sentByMe) Alignment.End else Alignment.Start
    val accent = if (message.sentByMe) PurpleMain else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    val shape = if (message.sentByMe) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = if (message.sentByMe) PurpleMain.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            ),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    message.isImageAttachment -> ImageAttachmentContent(message = message)
                    message.isVideoAttachment -> VideoAttachmentContent(message = message)
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
        if (message.sentByMe && message.isRead) {
            Text(
                text = "Seen",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ImageAttachmentContent(message: ChatMessage) {
    val source = resolveMediaUrl(message.attachmentPreviewUrl ?: message.attachmentUrl)
        ?: message.attachmentUrl
    if (source != null) {
        AsyncImage(
            model = source,
            contentDescription = message.attachmentName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
        )
    } else {
        GenericAttachmentContent(message = message)
    }
}

@Composable
private fun VideoAttachmentContent(message: ChatMessage) {
    val previewSource = resolveMediaUrl(message.attachmentPreviewUrl)
    if (previewSource != null) {
        AsyncImage(
            model = previewSource,
            contentDescription = message.attachmentName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = PurpleMain,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.attachmentName ?: "Video",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp
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
                text = message.attachmentName ?: "Voice note",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1
            )
            Text(
                text = message.attachmentDurationSeconds?.takeIf { it > 0 }?.let { formatCallDuration(it) } ?: "Voice note",
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
                .background(Color(0xFF2196F3).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = null,
                tint = Color(0xFF2196F3)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.attachmentName ?: "File",
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
                Icon(Icons.Default.Close, contentDescription = "Remove attachment")
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5A6A).copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, Color(0xFFFF5A6A).copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF5A6A).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFFFF5A6A))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Recording voice note",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Tap stop to send ${formatCallDuration(seconds.coerceAtLeast(1))}",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                    fontSize = 11.sp
                )
            }
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.onBackground)
            }
            OutlinedButton(
                onClick = onStop,
                border = BorderStroke(1.dp, Color(0xFFFF5A6A)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5A6A))
            ) {
                Text("Stop")
            }
        }
    }
}

@Composable
private fun CallMessageBubble(
    message: ChatMessage,
    summary: CallSummaryUiState,
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                            text = "Call again",
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
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
                    text = "Call availability",
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
            text = "Typing...",
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
            text = "Loading earlier messages",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
            fontSize = 12.sp
        )
    }
}

private fun callStatusText(summary: CallSummaryUiState): String {
    return when {
        summary.durationSeconds > 0 -> "Connected ${formatCallDuration(summary.durationSeconds)}"
        summary.endReason == CallEndReason.Missed -> "Missed call"
        summary.endReason == CallEndReason.Declined -> "Declined call"
        summary.endReason == CallEndReason.Rejected -> "Rejected call"
        summary.endReason == CallEndReason.Busy -> "Busy"
        summary.endReason == CallEndReason.Canceled -> "Canceled call"
        summary.endReason == CallEndReason.NoAnswer -> "No answer"
        summary.endReason == CallEndReason.Dropped -> "Call dropped"
        else -> "Call ended"
    }
}

private fun callAccentColor(summary: CallSummaryUiState): Color {
    return if (summary.durationSeconds > 0) PurpleMain else Color(0xFFFF5A6A)
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
    return ChatAttachmentDraft(
        uri = uri,
        kind = kind,
        name = name,
        mimeType = mimeType ?: defaultMimeType(kind),
        durationSeconds = null,
        previewUri = if (kind == ChatAttachmentKind.Image) uri else null,
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

private fun pendingAttachmentColor(kind: ChatAttachmentKind): Color {
    return when (kind) {
        ChatAttachmentKind.Image -> PurpleMain
        ChatAttachmentKind.Video -> Color(0xFF4CAF50)
        ChatAttachmentKind.Audio -> Color(0xFFFF9800)
        ChatAttachmentKind.File -> Color(0xFF2196F3)
    }
}

private fun pendingAttachmentLabel(attachment: ChatAttachmentDraft): String {
    return when (attachment.kind) {
        ChatAttachmentKind.Image -> "Photo ready to send"
        ChatAttachmentKind.Video -> "Video ready to send"
        ChatAttachmentKind.Audio -> "Voice note ${attachment.durationSeconds?.let { "· ${formatCallDuration(it)}" } ?: ""}".trim()
        ChatAttachmentKind.File -> friendlyMimeLabel(attachment.mimeType)
    }
}

private fun friendlyMimeLabel(mimeType: String?): String {
    if (mimeType.isNullOrBlank()) {
        return "Attachment"
    }
    return when {
        mimeType.startsWith("image/") -> "Image"
        mimeType.startsWith("video/") -> "Video"
        mimeType.startsWith("audio/") -> "Audio"
        mimeType == "application/pdf" -> "PDF"
        mimeType.contains("word", ignoreCase = true) -> "Document"
        mimeType.contains("zip", ignoreCase = true) -> "Archive"
        else -> mimeType.substringAfter('/').uppercase(Locale.ROOT)
    }
}

private fun attachmentKindLabel(message: ChatMessage): String {
    return when (message.attachmentKind) {
        ChatAttachmentKind.Image -> "Photo"
        ChatAttachmentKind.Video -> "Video"
        ChatAttachmentKind.Audio -> "Voice message"
        ChatAttachmentKind.File -> "File"
        null -> "Attachment"
    }
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

