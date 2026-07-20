package com.nova.app.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nova.app.core.ui.NovaBadge
import com.nova.app.core.ui.MediaViewer
import com.nova.app.feature.chat.ChatListScreen
import com.nova.app.feature.community.CommunityScreen
import com.nova.app.feature.discover.DiscoverScreen
import com.nova.app.feature.post.CreatePostScreen
import com.nova.app.feature.profile.AccountScreen
import com.nova.app.core.model.MessagesUiState
import com.nova.app.core.model.CommunityUiState
import com.nova.app.core.model.ProfileUiState
import com.nova.app.core.state.NovaLoadState
import com.nova.app.core.viewmodel.CommunityViewModel
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink

@Composable
fun HomeShell(
    messagesState: MessagesUiState,
    communityState: NovaLoadState<CommunityUiState>,
    communityViewModel: CommunityViewModel,
    profileState: ProfileUiState,
    notificationCount: Int,
    onChatClick: (com.nova.app.core.model.ChatThread) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    onOpenConnections: (String) -> Unit = {},
    onProfilePostLike: (String, Boolean) -> Unit = { _, _ -> },
    onProfilePostComment: (String, String) -> Unit = { _, _ -> },
    onProfilePostShare: (String) -> Unit = {},
    initialTab: Int = 0,
    modifier: Modifier = Modifier,
) {
    data class ViewerMediaState(
        val urls: List<String>,
        val startIndex: Int = 0,
    )

    var viewerMedia by remember { mutableStateOf<ViewerMediaState?>(null) }
    var selectedTab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    val messageBadgeCount = messagesState.threads.sumOf { it.unreadCount }

    BackHandler(enabled = viewerMedia != null) {
        viewerMedia = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (selectedTab) {
            0 -> DiscoverScreen()
            1 -> CommunityScreen(
                uiState = communityState,
                communityViewModel = communityViewModel,
                notificationCount = notificationCount,
                onNotificationClick = onNotificationClick,
                onMediaClick = { urls, startIndex -> viewerMedia = ViewerMediaState(urls, startIndex) },
                onOpenProfile = onOpenProfile,
            )
            2 -> CreatePostScreen(
                communityViewModel = communityViewModel,
                onBack = { selectedTab = 1 },
                onPublished = { selectedTab = 1 }
            )
            3 -> ChatListScreen(messagesState = messagesState, onSearchClick = onSearchClick, onChatClick = onChatClick)
            4 -> AccountScreen(
                profileState = profileState,
                onSettingsClick = onSettingsClick,
                onEditProfile = onEditProfile,
                onNewPostClick = { selectedTab = 2 },
                onOpenConnections = onOpenConnections,
                onLikePost = onProfilePostLike,
                onCommentPost = onProfilePostComment,
                onSharePost = onProfilePostShare,
                onOpenMedia = { urls, startIndex -> viewerMedia = ViewerMediaState(urls, startIndex) },
            )
        }

        if (selectedTab != 2 && viewerMedia == null) {
            NovaBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                messageBadgeCount = messageBadgeCount,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (viewerMedia != null) {
            MediaViewer(
                mediaUrls = viewerMedia!!.urls,
                startIndex = viewerMedia!!.startIndex,
                onDismiss = { viewerMedia = null }
            )
        }
    }
}

@Composable
fun NovaBottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    messageBadgeCount: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(24.dp)
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(40.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavIcon(Icons.Default.Explore, selectedTab == 0) { onTabSelected(0) }
            NavIcon(Icons.Default.Public, selectedTab == 1) { onTabSelected(1) }
            NavIcon(Icons.Default.Add, selectedTab == 2, isMain = true) { onTabSelected(2) }
            NavIcon(Icons.Default.ChatBubble, selectedTab == 3, badgeCount = messageBadgeCount) { onTabSelected(3) }
            NavIcon(Icons.Default.Person, selectedTab == 4) { onTabSelected(4) }
        }
    }
}

@Composable
fun NavIcon(
    icon: ImageVector,
    isSelected: Boolean,
    isMain: Boolean = false,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val tint = if (isSelected) PurpleMain else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val containerSize = if (isMain) 56.dp else 48.dp
    val iconSize = if (isMain) 32.dp else 24.dp

    val modifier = if (isMain) {
        Modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(PurpleMain, PurplePink)))
    } else {
        Modifier.size(containerSize)
    }

    IconButton(onClick = onClick, modifier = modifier) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isMain) MaterialTheme.colorScheme.onBackground else tint,
                modifier = Modifier.size(iconSize)
            )
            if (badgeCount > 0) {
                NovaBadge(
                    count = badgeCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                )
            }
        }
    }
}
