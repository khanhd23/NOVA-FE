package com.nova.app.feature.profile

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
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
import com.nova.app.core.ui.ExpandableText
import com.nova.app.core.ui.NovaChip
import com.nova.app.core.ui.NovaCard
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.ui.PostMediaPreview
import com.nova.app.core.ui.VipAvatar
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink

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
                title = profile?.displayName ?: "Profile",
                subtitle = "Public profile",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )

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
                            Text("Retry")
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
                            text = if (profile.online) "Online" else "Offline",
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
            text = "Interests",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (interests.isEmpty()) {
            Text(
                text = "No interests shared yet.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                interests.forEach { interest ->
                    NovaChip(text = interest, selected = true)
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
            profile.friend -> "Friends"
            profile.followedByMe -> "Following"
            profile.followedByThem -> "Follow back"
            else -> "Follow"
        }
        val followColors = if (profile.followedByMe || profile.friend) {
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
        } else {
            ButtonDefaults.buttonColors(containerColor = PurpleMain, contentColor = MaterialTheme.colorScheme.onBackground)
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
            colors = ButtonDefaults.buttonColors(containerColor = PurplePink, contentColor = MaterialTheme.colorScheme.onBackground),
        ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text("Message")
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
            text = "Featured photos",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (photos.isEmpty()) {
            Text(
                text = "No featured photos yet.",
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
) {
    var commentingPost by remember { mutableStateOf<BackendCommunityPost?>(null) }
    var commentDraft by remember { mutableStateOf("") }

    Column {
        Text(
            text = "Posts",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (posts.isEmpty()) {
            Text(
                text = "No posts yet.",
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
                )
            }
        }
    }

    commentingPost?.let { post ->
        AlertDialog(
            onDismissRequest = {
                commentingPost = null
                commentDraft = ""
            },
            title = { Text("Add comment") },
            text = {
                Column {
                    Text(post.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = commentDraft,
                        onValueChange = { commentDraft = it },
                        placeholder = { Text("Write a comment...") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = commentDraft.trim()
                        if (text.isNotBlank()) {
                            onCommentPost(post, text)
                        }
                        commentingPost = null
                        commentDraft = ""
                    }
                ) {
                    Text("Post")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    commentingPost = null
                    commentDraft = ""
                }) {
                    Text("Cancel")
                }
            },
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
                            text = post.authorName.ifBlank { "Unknown" },
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

            Spacer(modifier = Modifier.height(10.dp))

            ExpandableText(text = post.text)

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

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (post.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (post.likedByMe) Color(0xFFFF4D6D) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        modifier = Modifier.size(20.dp),
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

private fun genderLabel(gender: String): String {
    val normalized = gender.trim().lowercase()
    return when {
        normalized.contains("female") || normalized.contains("woman") || normalized.contains("girl") -> "Female"
        normalized.contains("male") || normalized.contains("man") || normalized.contains("boy") -> "Male"
        else -> "Gender"
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

