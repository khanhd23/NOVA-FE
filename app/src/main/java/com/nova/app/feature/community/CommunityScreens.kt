package com.nova.app.feature.community

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nova.app.core.model.CommunityComment
import com.nova.app.core.model.CommunityPost
import com.nova.app.core.model.CommunityUiState
import com.nova.app.core.state.NovaLoadState
import com.nova.app.core.ui.ExpandableText
import com.nova.app.core.ui.NovaBadge
import com.nova.app.core.ui.NovaCard
import com.nova.app.core.ui.NovaChip
import com.nova.app.core.ui.NovaTextField
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.ui.PostMediaPreview
import com.nova.app.core.ui.NovaVideoView
import com.nova.app.core.ui.VipAvatar
import com.nova.app.core.viewmodel.CommunityViewModel
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    uiState: NovaLoadState<CommunityUiState>,
    communityViewModel: CommunityViewModel,
    notificationCount: Int,
    onNotificationClick: () -> Unit,
    onMediaClick: (List<String>, Int) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(2) }
    var selectedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var commentDraft by rememberSaveable { mutableStateOf("") }

    val tabs = remember {
        listOf(
            CommunityTab("Friends", "friends"),
            CommunityTab("Following", "following"),
            CommunityTab("For You", "for_you"),
        )
    }

    LaunchedEffect(selectedTabIndex) {
        communityViewModel.selectTab(tabs[selectedTabIndex].slug)
    }

    val data = (uiState as? NovaLoadState.Success)?.data
    val selectedPost = data?.posts?.firstOrNull { it.id == selectedPostId }
    val filteredPosts = remember(searchQuery, data?.posts) {
        val base = data?.posts.orEmpty()
        if (searchQuery.isBlank()) {
            base
        } else {
            val q = searchQuery.trim().lowercase()
            base.filter { post ->
                post.text.lowercase().contains(q) ||
                    post.author.name.lowercase().contains(q) ||
                    post.tags.any { it.lowercase().contains(q) } ||
                    post.topic.lowercase().contains(q)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            NovaTopBar(
                title = "Community",
                subtitle = data?.refreshToken?.takeIf { it.isNotBlank() } ?: "Trending conversations",
                actions = {
                    IconButton(onClick = { data?.let { communityViewModel.refresh(it.selectedTab) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Box(contentAlignment = Alignment.TopEnd) {
                        IconButton(onClick = onNotificationClick) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        NovaBadge(count = notificationCount, modifier = Modifier.padding(top = 8.dp, end = 8.dp))
                    }
                }
            )

            NovaTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search posts, tags, people...",
                leadingIcon = Icons.Default.Search,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = PurpleMain,
                divider = {},
                edgePadding = 24.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = PurpleMain
                    )
                }
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                tab.title,
                                color = if (selectedTabIndex == index) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (data != null) {
                Spacer(modifier = Modifier.height(14.dp))
                TrendingTagsRow(tags = data.trending)
                Spacer(modifier = Modifier.height(10.dp))
            }

            when (uiState) {
                is NovaLoadState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is NovaLoadState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(uiState.message, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { communityViewModel.refresh(tabs[selectedTabIndex].slug) }) {
                            Text(uiState.actionLabel)
                        }
                    }
                }
                is NovaLoadState.Success -> {
                    if (filteredPosts.isEmpty()) {
                        EmptyCommunityState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(filteredPosts, key = { it.id }) { post ->
                                CommunityPostCard(
                                    post = post,
                                    onLike = { communityViewModel.likePost(post.id, !post.likedByMe) },
                                    onComment = { selectedPostId = post.id },
                                    onShare = {
                                        communityViewModel.sharePost(post.id)
                                        shareCommunityPost(context, clipboardManager, post)
                                    },
                                    onMediaClick = onMediaClick,
                                    onOpenProfile = onOpenProfile,
                                )
                            }
                        }
                    }
                }
                else -> {
                    EmptyCommunityState()
                }
            }
        }

        selectedPost?.let { post ->
            CommentBottomSheet(
                post = post,
                commentDraft = commentDraft,
                onCommentDraftChange = { commentDraft = it },
                onDismiss = {
                    selectedPostId = null
                    commentDraft = ""
                },
                onSend = {
                    if (commentDraft.isNotBlank()) {
                        communityViewModel.commentPost(post.id, commentDraft.trim())
                        commentDraft = ""
                    }
                },
                onOpenProfile = onOpenProfile,
            )
        }
    }
}

@Composable
private fun TrendingTagsRow(tags: List<String>) {
    if (tags.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tags, key = { it }) { tag ->
            NovaChip(text = "#$tag", selected = true)
        }
    }
}

@Composable
private fun CommunityPostCard(
    post: CommunityPost,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onMediaClick: (List<String>, Int) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val likeColor by animateColorAsState(
        targetValue = if (post.likedByMe) Color(0xFFFF4D6D) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        label = "communityLikeColor"
    )
    val likeScale by animateFloatAsState(
        targetValue = if (post.likedByMe) 1.15f else 1f,
        label = "communityLikeScale"
    )

    NovaCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenProfile(post.author.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                VipAvatar(
                    imageUrl = post.author.photoUrl,
                    contentDescription = post.author.name,
                    modifier = Modifier.size(42.dp),
                    vipTierId = post.author.vipTierId,
                    premium = post.author.premium,
                )

                Spacer(modifier = Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.author.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (post.author.verified) {
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Verified", color = PurpleMain, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        text = buildString {
                            append(post.topic)
                            if (post.author.city.isNotBlank()) {
                                append(" · ")
                                append(post.author.city)
                            }
                            append(" · ")
                            append(post.timeLabel)
                        },
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        fontSize = 11.sp
                    )
                }

                }
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy link", color = MaterialTheme.colorScheme.onBackground) },
                            onClick = { showMenu = false; onShare() },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Hide Post", color = MaterialTheme.colorScheme.onBackground) },
                            onClick = { showMenu = false },
                            leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Report", color = Color.Red) },
                            onClick = { showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Report, contentDescription = null, tint = Color.Red) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ExpandableText(text = post.text)

            if (post.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(post.tags, key = { it }) { tag ->
                        NovaChip(text = "#$tag", selected = true)
                    }
                }
            }

            if (post.allMediaUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                CommunityMediaPreview(
                    mediaUrls = post.allMediaUrls,
                    thumbnailUrl = post.thumbnailUrl,
                    onOpen = { index -> onMediaClick(post.allMediaUrls, index) }
                )
            }

            if (post.commentsPreview.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    post.commentsPreview.take(2).forEach { comment ->
                        CommentPreview(comment = comment, onOpenProfile = onOpenProfile)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike) {
                    Icon(
                        imageVector = if (post.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = likeColor,
                        modifier = Modifier.graphicsLayer(scaleX = likeScale, scaleY = likeScale).size(20.dp)
                    )
                }
                Text(post.likes.toString(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), fontSize = 12.sp)

                Spacer(modifier = Modifier.size(8.dp))

                IconButton(onClick = onComment) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), modifier = Modifier.size(20.dp))
                }
                Text(post.comments.toString(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), fontSize = 12.sp)

                Spacer(modifier = Modifier.size(8.dp))

                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), modifier = Modifier.size(20.dp))
                }
                Text(post.shares.toString(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), fontSize = 12.sp)

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    color = PurpleMain.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = post.postType.uppercase(),
                        color = PurplePink,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommunityMediaPreview(
    mediaUrls: List<String>,
    thumbnailUrl: String?,
    onOpen: (Int) -> Unit,
) {
    PostMediaPreview(
        mediaUrls = mediaUrls,
        thumbnailUrl = thumbnailUrl,
        onOpen = onOpen
    )
}

@Composable
private fun CommentPreview(
    comment: CommunityComment,
    onOpenProfile: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onOpenProfile(comment.author.id) }
    ) {
        VipAvatar(
            imageUrl = comment.author.photoUrl,
            contentDescription = comment.author.name,
            modifier = Modifier.size(28.dp),
            vipTierId = comment.author.vipTierId,
            premium = comment.author.premium,
            borderWidth = 1.2.dp,
            padding = 2.dp,
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column {
            Text(
                text = comment.author.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = comment.text,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentBottomSheet(
    post: CommunityPost,
    commentDraft: String,
    onCommentDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(540.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Comments",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            Text(
                text = post.text,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(post.commentsPreview, key = { it.id }) { comment ->
                    CommentDetailItem(comment = comment, onOpenProfile = onOpenProfile)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NovaTextField(
                    value = commentDraft,
                    onValueChange = onCommentDraftChange,
                    placeholder = "Add a comment...",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.size(12.dp))
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PurpleMain)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

@Composable
private fun CommentDetailItem(
    comment: CommunityComment,
    onOpenProfile: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .clickable { onOpenProfile(comment.author.id) }
    ) {
        VipAvatar(
            imageUrl = comment.author.photoUrl,
            contentDescription = comment.author.name,
            modifier = Modifier.size(32.dp),
            vipTierId = comment.author.vipTierId,
            premium = comment.author.premium,
            borderWidth = 1.5.dp,
            padding = 2.dp,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.author.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                if (comment.mine) {
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("You", color = PurpleMain, fontSize = 10.sp)
                }
            }
            Text(comment.text, color = Color.LightGray, fontSize = 12.sp)
            Text(comment.timeLabel, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun EmptyCommunityState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No posts yet",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "Refresh or create a new post to start the feed.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}

private fun shareCommunityPost(
    context: android.content.Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    post: CommunityPost,
) {
    val link = "https://nova.app/community/post/${post.id}"
    clipboardManager.setText(AnnotatedString(link))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${post.author.name}: ${post.text.take(120)}\n$link")
    }
    context.startActivity(Intent.createChooser(intent, "Share post"))
}

private data class CommunityTab(val title: String, val slug: String)

private fun fallbackAvatarUrl(name: String): String {
    val safeName = if (name.isBlank()) "Nova User" else name.trim()
    val encoded = java.net.URLEncoder.encode(safeName, Charsets.UTF_8)
    return "https://ui-avatars.com/api/?name=$encoded&background=6C5CE7&color=FFFFFF&size=512"
}
