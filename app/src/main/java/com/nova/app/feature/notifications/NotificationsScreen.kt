package com.nova.app.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.core.model.NotificationItem
import com.nova.app.core.model.NotificationsUiState
import com.nova.app.core.ui.ExpandableText
import com.nova.app.core.ui.NovaBadge
import com.nova.app.core.ui.NovaCard
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink

@Composable
fun NotificationsScreen(
    uiState: NotificationsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenNotification: (NotificationItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NovaTopBar(
            title = "Notifications",
            subtitle = if (uiState.unreadCount > 0) "${uiState.unreadCount} unread" else "All caught up",
            onBack = onBack,
            actions = {
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    if (uiState.unreadCount > 0) {
                        NovaBadge(count = uiState.unreadCount, modifier = Modifier.padding(top = 6.dp, end = 6.dp))
                    }
                }
            }
        )

        if (uiState.loading && uiState.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Loading notifications...",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        } else if (uiState.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No notifications yet",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.items, key = { it.id }) { notification ->
                    NotificationCard(
                        item = notification,
                        onClick = { onOpenNotification(notification) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    onClick: () -> Unit,
) {
    val accent = notificationColor(item.type)
    NovaCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = notificationIcon(item.type),
                    contentDescription = null,
                    tint = accent,
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    if (item.unread) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(PurplePink)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                ExpandableText(
                    text = item.description,
                    collapsedMaxLines = 2,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.timeLabel,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
                        fontSize = 11.sp,
                    )
                    if (!item.actionTarget.isNullOrBlank()) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accent.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = actionLabel(item),
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun notificationIcon(type: String) = when (type.uppercase()) {
    "MESSAGE" -> Icons.Default.ChatBubble
    "CALL" -> Icons.Default.Call
    "FOLLOW" -> Icons.Default.Person
    "FRIEND" -> Icons.Default.People
    "COMMUNITY" -> Icons.Default.Public
    "EVENT" -> Icons.Default.Notifications
    else -> Icons.Default.Info
}

private fun notificationColor(type: String): Color = when (type.uppercase()) {
    "MESSAGE" -> PurpleMain
    "CALL" -> Color(0xFFEF5350)
    "FOLLOW" -> Color(0xFF7C4DFF)
    "FRIEND" -> Color(0xFF26C6DA)
    "COMMUNITY" -> Color(0xFF26C6DA)
    "EVENT" -> Color(0xFF66BB6A)
    else -> Color(0xFF9E9E9E)
}

private fun actionLabel(item: NotificationItem): String {
    val target = item.actionTarget.orEmpty()
    return when {
        target.startsWith("profile/") -> "Open profile"
        target.startsWith("thread/") -> "Open chat"
        target.startsWith("community/") -> "Open community"
        target.startsWith("call/") -> "Open chat"
        else -> "Open"
    }
}
