package com.nova.app.feature.community

import com.nova.app.core.designsystem.NovaColors

import androidx.compose.ui.res.stringResource
import com.nova.app.R
import androidx.compose.ui.platform.LocalResources
import androidx.annotation.StringRes

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nova.app.core.model.CommunityComment
import com.nova.app.core.model.CommunityPost
import com.nova.app.core.model.CommunityUiState
import com.nova.app.core.backend.BackendRuntimeRegistry
import com.nova.app.core.backend.toCommunityComment
import com.nova.app.core.state.NovaLoadState
import com.nova.app.core.ui.ExpandableText
import com.nova.app.core.ui.NovaBadge
import com.nova.app.core.ui.NovaCard
import com.nova.app.core.ui.NovaChip
import com.nova.app.core.ui.NovaTopLoadingBar
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.ui.PostMediaPreview
import com.nova.app.core.ui.NovaVideoView
import com.nova.app.core.ui.VipAvatar
import com.nova.app.core.ui.formatCount
import com.nova.app.core.ui.formatPostTimestamp
import com.nova.app.core.viewmodel.CommunityViewModel
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink
import kotlinx.coroutines.launch

private const val COMMUNITY_COMMENT_PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    uiState: NovaLoadState<CommunityUiState>,
    communityViewModel: CommunityViewModel,
    notificationCount: Int,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onMediaClick: (List<String>, Int) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(2) }
    var selectedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var commentDraft by rememberSaveable { mutableStateOf("") }

    val tabs = remember {
        listOf(
            CommunityTab(R.string.community_tab_friends, "friends"),
            CommunityTab(R.string.community_tab_following, "following"),
            CommunityTab(R.string.community_tab_for_you, "for_you"),
        )
    }

    LaunchedEffect(selectedTabIndex) {
        communityViewModel.selectTab(tabs[selectedTabIndex].slug)
    }

    val data = (uiState as? NovaLoadState.Success)?.data
    val selectedPost = data?.posts?.firstOrNull { it.id == selectedPostId }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            NovaTopBar(
                title = stringResource(R.string.community_title),
                subtitle = stringResource(R.string.community_subtitle),
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.community_search), tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Box(contentAlignment = Alignment.TopEnd) {
                        IconButton(onClick = onNotificationClick) {
                            Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.notif_title), tint = MaterialTheme.colorScheme.onBackground)
                        }
                        NovaBadge(count = notificationCount, modifier = Modifier.padding(top = 8.dp, end = 8.dp))
                    }
                }
            )
            NovaTopLoadingBar(
                visible = uiState is NovaLoadState.Loading || data?.loading == true || data?.refreshing == true
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
                                stringResource(tab.titleRes),
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
                            Text(uiState.actionLabel, color = PurpleMain)
                        }
                    }
                }
                is NovaLoadState.Success -> {
                    val stateData = uiState.data
                    if (stateData.posts.isEmpty()) {
                        EmptyCommunityState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(stateData.posts, key = { it.id }) { post ->
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
                onSend = { text ->
                    if (text.isNotBlank()) {
                        communityViewModel.commentPost(post.id, text.trim())
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
        targetValue = if (post.likedByMe) NovaColors.current.like else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        label = "communityLikeColor"
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
                            Surface(
                                color = PurpleMain.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.community_verified),
                                    color = PurpleMain,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = formatPostTimestamp(post.createdAt, post.timeLabel),
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
                            text = { Text(stringResource(R.string.community_copy_link), color = MaterialTheme.colorScheme.onBackground) },
                            onClick = { showMenu = false; onShare() },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.community_hide_post), color = MaterialTheme.colorScheme.onBackground) },
                            onClick = { showMenu = false },
                            leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.community_report), color = NovaColors.current.danger) },
                            onClick = { showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Report, contentDescription = null, tint = NovaColors.current.danger) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            ExpandableText(
                text = post.text,
                mentions = post.mentions,
                onMentionClick = onOpenProfile,
            )

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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                PostActionButton(
                    icon = if (post.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    count = post.likes,
                    tint = likeColor,
                    containerColor = if (post.likedByMe) NovaColors.current.like.copy(alpha = 0.16f) else NovaColors.current.like.copy(alpha = 0.08f),
                    onClick = onLike,
                )
                PostActionButton(
                    icon = Icons.Default.ChatBubbleOutline,
                    count = post.comments,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                    onClick = onComment,
                )
                PostActionButton(
                    icon = Icons.Default.Share,
                    count = post.shares,
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.09f),
                    onClick = onShare,
                )
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
private fun PostActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .widthIn(min = 64.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            if (count > 0) {
                Spacer(modifier = Modifier.size(5.dp))
                Text(
                    text = formatCount(count),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CommentPreview(
    comment: CommunityComment,
    onOpenProfile: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        VipAvatar(
            imageUrl = comment.author.photoUrl,
            contentDescription = comment.author.name,
            modifier = Modifier
                .size(28.dp)
                .clickable { onOpenProfile(comment.author.id) },
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
                fontSize = 12.sp,
                modifier = Modifier.clickable { onOpenProfile(comment.author.id) },
            )
            ExpandableText(
                text = comment.text,
                collapsedMaxLines = 2,
                mentions = comment.mentions,
                onMentionClick = onOpenProfile,
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
    onSend: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    var comments by remember(post.id) { mutableStateOf(post.commentsPreview) }
    var commentPage by rememberSaveable(post.id) { mutableIntStateOf(0) }
    var commentTotal by rememberSaveable(post.id) { mutableStateOf(post.commentsPreview.size.toLong()) }
    var commentsLoading by rememberSaveable(post.id) { mutableStateOf(false) }
    var commentsError by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    var replyingTo by remember(post.id) { mutableStateOf<CommunityComment?>(null) }

    suspend fun loadComments(page: Int, append: Boolean) {
        commentsLoading = true
        commentsError = null
        val result = BackendRuntimeRegistry.runtime?.fetchCommunityComments(post.id, page, COMMUNITY_COMMENT_PAGE_SIZE)
        if (result == null) {
            commentsLoading = false
            if (!append) {
                comments = post.commentsPreview
                commentTotal = post.commentsPreview.size.toLong()
            }
            commentsError = if (comments.isEmpty()) res.getString(R.string.community_comments_failed) else null
            return
        }
        val mapped = result.items.map { it.toCommunityComment() }
        comments = if (append) comments + mapped else mapped
        commentPage = result.page
        commentTotal = result.total
        commentsLoading = false
    }

    LaunchedEffect(post.id, post.comments) {
        loadComments(page = 0, append = false)
    }

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
                text = stringResource(R.string.community_comments),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                if (commentsError != null) {
                    item {
                        Text(
                            text = commentsError.orEmpty(),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                } else if (comments.isEmpty() && !commentsLoading) {
                    item {
                        Text(
                            text = stringResource(R.string.community_no_comments),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                }
                items(comments, key = { it.id }) { comment ->
                    CommentDetailItem(
                        comment = comment,
                        onOpenProfile = onOpenProfile,
                        onReply = {
                            replyingTo = comment
                            val mention = "@${comment.author.name.trim().replace(" ", ".")}"
                            if (!commentDraft.contains(mention, ignoreCase = true)) {
                                onCommentDraftChange("$mention ")
                            }
                        },
                    )
                }
                if (comments.size.toLong() < commentTotal) {
                    item {
                        TextButton(
                            onClick = { scope.launch { loadComments(commentPage + 1, append = true) } },
                            enabled = !commentsLoading,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        ) {
                            Text(if (commentsLoading) stringResource(R.string.common_loading_ellipsis) else stringResource(R.string.common_load_more), color = PurpleMain)
                        }
                    }
                }
                if (commentsLoading && comments.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            CommentComposerBar(
                value = commentDraft,
                onValueChange = onCommentDraftChange,
                replyingToName = replyingTo?.author?.name,
                onClearReply = { replyingTo = null },
                onSend = {
                    onSend(commentDraft)
                    replyingTo = null
                },
            )
        }
    }
}

@Composable
private fun CommentDetailItem(
    comment: CommunityComment,
    onOpenProfile: (String) -> Unit,
    onReply: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        VipAvatar(
            imageUrl = comment.author.photoUrl,
            contentDescription = comment.author.name,
            modifier = Modifier
                .size(32.dp)
                .clickable { onOpenProfile(comment.author.id) },
            vipTierId = comment.author.vipTierId,
            premium = comment.author.premium,
            borderWidth = 1.5.dp,
            padding = 2.dp,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            comment.author.name,
                            color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                                Color(0xFF111111)
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { onOpenProfile(comment.author.id) },
                        )
                        if (comment.mine) {
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.community_you), color = PurpleMain, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    ExpandableText(
                        text = comment.text,
                        collapsedMaxLines = 4,
                        mentions = comment.mentions,
                        onMentionClick = onOpenProfile,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatPostTimestamp(comment.createdAt, comment.timeLabel),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.community_reply),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.66f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onReply),
                )
            }
        }
    }
}

@Composable
private fun CommentComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    replyingToName: String?,
    onClearReply: () -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (!replyingToName.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.community_replying_to, replyingToName.orEmpty()),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = PurpleMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onClearReply),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(28.dp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                ),
                maxLines = 4,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f), shape)
                    .padding(horizontal = 16.dp),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) {
                            Text(
                                text = if (replyingToName.isNullOrBlank()) stringResource(R.string.community_add_comment) else stringResource(R.string.community_write_reply),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                                fontSize = 14.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Spacer(modifier = Modifier.size(10.dp))
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (value.isNotBlank()) PurpleMain else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.community_post), tint = MaterialTheme.colorScheme.onBackground)
            }
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
            text = stringResource(R.string.community_no_posts),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.community_no_posts_desc),
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
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.community_share_post)))
}

private data class CommunityTab(@StringRes val titleRes: Int, val slug: String)

private fun fallbackAvatarUrl(name: String): String {
    val safeName = if (name.isBlank()) "Nova User" else name.trim()
    val encoded = java.net.URLEncoder.encode(safeName, Charsets.UTF_8)
    return "https://ui-avatars.com/api/?name=$encoded&background=6C5CE7&color=FFFFFF&size=512"
}
