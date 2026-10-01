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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.core.model.ChatThread
import com.nova.app.core.model.MessagesUiState
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.ui.VipAvatar
import com.nova.app.ui.theme.*

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
internal fun SwipeToDeleteChatItem(
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
