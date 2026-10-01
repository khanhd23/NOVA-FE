package com.nova.app.feature.profile

import com.nova.app.core.designsystem.NovaBrand

import com.nova.app.core.designsystem.NovaColors

import com.nova.app.core.i18n.interestLabel

import androidx.compose.ui.res.stringResource
import com.nova.app.R
import androidx.compose.ui.platform.LocalResources

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import coil3.compose.AsyncImage
import com.nova.app.core.backend.BackendCommunityPost
import com.nova.app.core.backend.BackendProfile
import com.nova.app.core.backend.BackendRuntimeRegistry
import com.nova.app.core.backend.toCommunityComment
import com.nova.app.core.model.CommunityComment
import com.nova.app.core.model.CommunityMention
import com.nova.app.core.ui.ExpandableText
import com.nova.app.core.ui.NovaChip
import com.nova.app.core.ui.NovaCard
import com.nova.app.core.ui.NovaTopLoadingBar
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.ui.PostMediaPreview
import com.nova.app.core.ui.VipAvatar
import com.nova.app.core.ui.formatCount
import com.nova.app.core.ui.formatPostTimestamp
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink
import kotlinx.coroutines.launch

private const val PROFILE_COMMENT_PAGE_SIZE = 20

@Composable
fun PublicProfileScreen(
    profile: BackendProfile?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFollow: (Boolean) -> Unit,
    onOpenConnections: (String) -> Unit = {},
    onMessage: () -> Unit,
    onOpenPhoto: (String) -> Unit,
    onOpenMedia: (List<String>, Int) -> Unit = { _, _ -> },
    posts: List<BackendCommunityPost> = emptyList(),
    onLikePost: (BackendCommunityPost) -> Unit = {},
    onCommentPost: (BackendCommunityPost, String) -> Unit = { _, _ -> },
    onSharePost: (BackendCommunityPost) -> Unit = {},
    onOpenProfile: (String) -> Unit = {},
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            NovaTopBar(
                title = profile?.displayName ?: stringResource(R.string.profile_title),
                subtitle = stringResource(R.string.profile_public),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )
            NovaTopLoadingBar(visible = loading)

            when {
                loading && profile == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null && profile == null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        TextButton(onClick = onRefresh) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }
                profile != null -> {
                    ProfileHero(profile = profile)
                    Spacer(modifier = Modifier.height(18.dp))

                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        ActionRow(
                            profile = profile,
                            onToggleFollow = onToggleFollow,
                            onMessage = onMessage,
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        ProfileConnectionStatsRow(
                            followingCount = profile.followingCount,
                            followersCount = profile.followersCount,
                            friendsCount = profile.friendsCount,
                            onOpenConnections = onOpenConnections,
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        PublicProfileInterestsSection(
                            interests = profile.interests,
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        FeaturedPhotosSection(
                            photos = profile.featuredPhotos,
                            onOpenPhoto = onOpenPhoto,
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        PublicProfilePostsSection(
                            posts = posts,
                            onOpenPhoto = onOpenPhoto,
                            onOpenMedia = onOpenMedia,
                            onLikePost = onLikePost,
                            onCommentPost = onCommentPost,
                            onSharePost = onSharePost,
                            onOpenProfile = onOpenProfile,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHero(profile: BackendProfile) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        PurpleMain.copy(alpha = 0.92f),
                        PurplePink.copy(alpha = 0.88f),
                        Color(0xFF161623),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(34.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                VipAvatar(
                    imageUrl = profile.avatarUrl,
                    contentDescription = profile.displayName,
                    modifier = Modifier.size(102.dp),
                    vipTierId = profile.vipTierId,
                    premium = profile.premium,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profile.displayName,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (profile.age > 0) {
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = buildAnnotatedString {
                                    append(profile.age.toString())
                                    withStyle(
                                        style = SpanStyle(
                                            baselineShift = BaselineShift.Superscript,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFD166),
                                        )
                                    ) {
                                        append("+")
                                    }
                                },
                                color = Color(0xFFFFD166),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    ProfileIdentityRow(
                        id = profile.publicId,
                        centered = false,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NovaChip(
                            text = if (profile.online) stringResource(R.string.chat_online) else stringResource(R.string.chat_offline),
                            selected = profile.online,
                        )
                        NovaChip(text = genderLabel(profile.gender), selected = false)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ProfileBioText(
                bio = profile.bio,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicProfileInterestsSection(
    interests: List<String>,
) {
    Column {
        Text(
            text = stringResource(R.string.setup_interests),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (interests.isEmpty()) {
            Text(
                text = stringResource(R.string.profile_no_interests),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                interests.forEach { interest ->
                    NovaChip(text = interestLabel(interest), selected = true)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    profile: BackendProfile,
    onToggleFollow: (Boolean) -> Unit,
    onMessage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val followLabel = when {
            profile.friend -> stringResource(R.string.community_tab_friends)
            profile.followedByMe -> stringResource(R.string.community_tab_following)
            profile.followedByThem -> stringResource(R.string.profile_follow_back)
            else -> stringResource(R.string.profile_follow)
        }
        val followColors = if (profile.followedByMe || profile.friend) {
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
        } else {
            ButtonDefaults.buttonColors(containerColor = NovaBrand.Start, contentColor = Color.White)
        }
        if (profile.friend) {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(followLabel)
            }
        } else if (profile.followedByMe) {
            OutlinedButton(
                onClick = { onToggleFollow(false) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(followLabel)
            }
        } else {
            Button(
                onClick = { onToggleFollow(true) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = followColors,
            ) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(followLabel)
            }
        }

        Button(
            onClick = onMessage,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PurplePink, contentColor = MaterialTheme.colorScheme.onPrimary),
        ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.call_message))
        }
    }
}

@Composable
private fun FeaturedPhotosSection(
    photos: List<String>,
    onOpenPhoto: (String) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.setup_featured_photos),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (photos.isEmpty()) {
            Text(
                text = stringResource(R.string.profile_no_featured),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 24.dp),
            ) {
                items(photos, key = { it }) { photo ->
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 145.dp, height = 180.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { onOpenPhoto(photo) },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicProfilePostsSection(
    posts: List<BackendCommunityPost>,
    onOpenPhoto: (String) -> Unit,
    onOpenMedia: (List<String>, Int) -> Unit,
    onLikePost: (BackendCommunityPost) -> Unit,
    onCommentPost: (BackendCommunityPost, String) -> Unit,
    onSharePost: (BackendCommunityPost) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    var commentingPost by remember { mutableStateOf<BackendCommunityPost?>(null) }
    var commentDraft by remember { mutableStateOf("") }

    Column {
        Text(
            text = stringResource(R.string.profile_posts),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (posts.isEmpty()) {
            Text(
                text = stringResource(R.string.profile_no_posts),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
            return
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            posts.forEach { post ->
                PublicProfilePostCard(
                    post = post,
                    onOpenPhoto = onOpenPhoto,
                    onOpenMedia = onOpenMedia,
                    onLike = { onLikePost(post) },
                    onComment = {
                        commentingPost = post
                        commentDraft = ""
                    },
                    onShare = { onSharePost(post) },
                    onOpenProfile = onOpenProfile,
                )
            }
        }
    }

    commentingPost?.let { post ->
        ProfileCommentBottomSheet(
            post = post,
            commentDraft = commentDraft,
            onCommentDraftChange = { commentDraft = it },
            onDismiss = {
                commentingPost = null
                commentDraft = ""
            },
            onSend = { text ->
                if (text.isNotBlank()) {
                    onCommentPost(post, text.trim())
                    commentDraft = ""
                }
            },
            onOpenProfile = onOpenProfile,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicProfilePostCard(
    post: BackendCommunityPost,
    onOpenPhoto: (String) -> Unit,
    onOpenMedia: (List<String>, Int) -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    NovaCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VipAvatar(
                        imageUrl = post.authorAvatarUrl,
                        contentDescription = post.authorName,
                        modifier = Modifier.size(42.dp),
                        vipTierId = post.authorVipTierId,
                        premium = post.authorPremium,
                    )

                    Spacer(modifier = Modifier.size(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = post.authorName.ifBlank { stringResource(R.string.common_unknown) },
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = buildString {
                                if (post.authorCity.isNotBlank()) {
                                    append(post.authorCity)
                                    if (post.timeLabel.isNotBlank()) append(" · ")
                                }
                                append(post.timeLabel)
                            }.ifBlank { post.topicId },
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                        )
                    }
                }

            }

            Spacer(modifier = Modifier.height(10.dp))

            ExpandableText(
                text = post.text,
                mentions = post.mentions.map {
                    CommunityMention(
                        userId = it.userId,
                        displayName = it.displayName,
                        username = it.username,
                        avatarUrl = it.avatarUrl,
                    )
                },
                onMentionClick = onOpenProfile,
            )

            if (post.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    post.tags.forEach { tag ->
                        NovaChip(text = "#$tag", selected = true)
                    }
                }
            }

            if (post.allMediaUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                PublicProfileMediaPreview(
                    mediaUrls = post.allMediaUrls,
                    isVideo = post.hasVideoMedia,
                    thumbnailUrl = post.thumbnailUrl,
                    onOpen = { index -> onOpenMedia(post.allMediaUrls, index) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                PublicPostActionButton(
                    icon = if (post.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    count = post.likes,
                    tint = if (post.likedByMe) NovaColors.current.like else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                    containerColor = if (post.likedByMe) NovaColors.current.like.copy(alpha = 0.16f) else NovaColors.current.like.copy(alpha = 0.08f),
                    onClick = onLike,
                )
                PublicPostActionButton(
                    icon = Icons.Default.ChatBubbleOutline,
                    count = post.comments,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                    onClick = onComment,
                )
                PublicPostActionButton(
                    icon = Icons.Default.Share,
                    count = post.shares,
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.09f),
                    onClick = onShare,
                )
            }
        }
    }
}

@Composable
private fun PublicPostActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileCommentBottomSheet(
    post: BackendCommunityPost,
    commentDraft: String,
    onCommentDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    var comments by remember(post.id) { mutableStateOf(post.commentsPreview.map { it.toCommunityComment() }) }
    var commentPage by rememberSaveable(post.id) { mutableIntStateOf(0) }
    var commentTotal by rememberSaveable(post.id) { mutableStateOf(post.commentsPreview.size.toLong()) }
    var commentsLoading by rememberSaveable(post.id) { mutableStateOf(false) }
    var commentsError by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    var replyingTo by remember(post.id) { mutableStateOf<CommunityComment?>(null) }

    suspend fun loadComments(page: Int, append: Boolean) {
        commentsLoading = true
        commentsError = null
        val result = BackendRuntimeRegistry.runtime?.fetchCommunityComments(post.id, page, PROFILE_COMMENT_PAGE_SIZE)
        if (result == null) {
            commentsLoading = false
            if (!append) {
                comments = post.commentsPreview.map { it.toCommunityComment() }
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
                    ProfileCommentDetailItem(
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

            ProfileCommentComposerBar(
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
private fun ProfileCommentDetailItem(
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
                            text = comment.author.name,
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
                    text = formatPostTimestamp(comment.createdAt, comment.timeLabel),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.community_reply),
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
private fun ProfileCommentComposerBar(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PublicProfileMediaPreview(
    mediaUrls: List<String>,
    isVideo: Boolean,
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
private fun genderLabel(gender: String): String {
    val normalized = gender.trim().lowercase()
    return when {
        normalized.contains("female") || normalized.contains("woman") || normalized.contains("girl") -> stringResource(R.string.gender_female)
        normalized.contains("male") || normalized.contains("man") || normalized.contains("boy") -> stringResource(R.string.gender_male)
        else -> stringResource(R.string.setup_gender)
    }
}

private val BackendCommunityPost.allMediaUrls: List<String>
    get() = if (mediaUrls.isNotEmpty()) mediaUrls else mediaUrl?.let { listOf(it) } ?: emptyList()

private val BackendCommunityPost.hasVideoMedia: Boolean
    get() = postType.equals("VIDEO", ignoreCase = true) || allMediaUrls.any {
        it.endsWith(".mp4", ignoreCase = true) ||
            it.endsWith(".mov", ignoreCase = true) ||
            it.endsWith(".mkv", ignoreCase = true) ||
            it.endsWith(".webm", ignoreCase = true)
    }
