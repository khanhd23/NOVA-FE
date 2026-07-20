package com.nova.app.feature.profile

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import coil3.compose.AsyncImage
import com.nova.app.core.model.CommunityPost
import com.nova.app.core.model.ProfileUiState
import com.nova.app.core.ui.ExpandableText
import com.nova.app.core.ui.NovaCard
import com.nova.app.core.ui.NovaChip
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.ui.PostMediaPreview
import com.nova.app.core.ui.VipAvatar
import com.nova.app.core.ui.VipTierChip
import com.nova.app.core.ui.formatCount
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink

@Composable
fun AccountScreen(
    profileState: ProfileUiState,
    onSettingsClick: () -> Unit,
    onEditProfile: () -> Unit,
    onNewPostClick: () -> Unit,
    onOpenConnections: (String) -> Unit = {},
    onLikePost: (String, Boolean) -> Unit = { _, _ -> },
    onCommentPost: (String, String) -> Unit = { _, _ -> },
    onSharePost: (String) -> Unit = {},
    onOpenMedia: (List<String>, Int) -> Unit = { _, _ -> },
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            NovaTopBar(
                title = "Profile",
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        VipTierChip(
                            tierLabel = profileState.user.vipTierName ?: profileState.user.vipTierId?.uppercase(),
                            premium = profileState.user.premium,
                        )

                        Spacer(Modifier.width(8.dp))

                        Surface(
                            color = Color(0xFF00E5FF).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Diamond, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(2.dp))
                                Text(formatCount(profileState.diamonds), color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )

            ProfileHero(
                profileState = profileState,
                onEdit = onEditProfile,
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onEditProfile,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Edit Profile")
                    }
                    Button(
                        onClick = onNewPostClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePink, contentColor = MaterialTheme.colorScheme.onBackground),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("New Post")
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                ProfileConnectionStatsRow(
                    followingCount = profileState.user.followingCount,
                    followersCount = profileState.user.followersCount,
                    friendsCount = profileState.user.friendsCount,
                    onOpenConnections = onOpenConnections,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(18.dp))

                InterestsSection(
                    interests = profileState.interests,
                    onEditProfile = onEditProfile,
                )

                Spacer(modifier = Modifier.height(18.dp))

                FeaturedPhotosSection(
                    photos = profileState.featuredPhotos,
                    onAddPhoto = onEditProfile,
                )

                Spacer(modifier = Modifier.height(24.dp))

                UserPostsSection(
                    posts = profileState.posts,
                    onNewPostClick = onNewPostClick,
                    onLikePost = onLikePost,
                    onCommentPost = onCommentPost,
                    onSharePost = onSharePost,
                    onOpenMedia = onOpenMedia,
                )

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun ProfileHero(
    profileState: ProfileUiState,
    onEdit: () -> Unit,
) {
    val avatarUrl = profileState.user.photoUrl.ifBlank { fallbackAvatarUrl(profileState.user.name) }

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
                    Box(contentAlignment = Alignment.BottomEnd) {
                    VipAvatar(
                        imageUrl = avatarUrl,
                        contentDescription = profileState.user.name,
                        modifier = Modifier.size(102.dp),
                        vipTierId = profileState.user.vipTierId,
                        premium = profileState.user.premium,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profileState.user.name,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (profileState.user.age > 0) {
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = buildAnnotatedString {
                                    append(profileState.user.age.toString())
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
                        id = profileState.user.publicId,
                        centered = false,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NovaChip(
                            text = if (profileState.user.online) "Online" else "Offline",
                            selected = profileState.user.online,
                        )
                        NovaChip(text = genderLabel(profileState.user.gender), selected = false)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ProfileBioText(
                bio = profileState.bio,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestsSection(
    interests: List<String>,
    onEditProfile: () -> Unit,
) {
    Column {
        Text(
            text = "Interests",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (interests.isEmpty()) {
                NovaChip(text = "Add interests", selected = false, onClick = onEditProfile)
            } else {
                interests.forEach {
                    NovaChip(text = it, selected = true)
                }
            }
        }
    }
}

@Composable
private fun FeaturedPhotosSection(
    photos: List<String>,
    onAddPhoto: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Featured photos",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onAddPhoto) {
                Text("Edit", color = PurpleMain)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(3) { index ->
                val photoUrl = photos.getOrNull(index).orEmpty()
                FeaturedPhotoSlot(
                    photoUrl = photoUrl,
                    onClick = onAddPhoto,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FeaturedPhotoSlot(
    photoUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .aspectRatio(0.78f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isBlank()) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add photo",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp)
            )
        } else {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun UserPostsSection(
    posts: List<CommunityPost>,
    onNewPostClick: () -> Unit,
    onLikePost: (String, Boolean) -> Unit,
    onCommentPost: (String, String) -> Unit,
    onSharePost: (String) -> Unit,
    onOpenMedia: (List<String>, Int) -> Unit,
) {
    val context = LocalContext.current
    var commentingPost by remember { mutableStateOf<CommunityPost?>(null) }
    var commentDraft by remember { mutableStateOf("") }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Posts",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(onClick = onNewPostClick) {
                Text("+ New Post", color = PurpleMain)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (posts.isEmpty()) {
            NovaCard(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No posts yet.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onNewPostClick) {
                        Text("Create your first post", color = PurpleMain)
                    }
                }
            }
        } else {
            posts.forEach { post ->
                FeedPostItem(
                    post = post,
                    onLike = { onLikePost(post.id, !post.likedByMe) },
                    onComment = {
                        commentingPost = post
                        commentDraft = ""
                    },
                    onShare = {
                        onSharePost(post.id)
                        shareProfilePost(context, post)
                    },
                    onOpenMedia = onOpenMedia,
                )
                Spacer(modifier = Modifier.height(16.dp))
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
                            onCommentPost(post.id, text)
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

@Composable
private fun FeedPostItem(
    post: CommunityPost,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onOpenMedia: (List<String>, Int) -> Unit,
) {
    NovaCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VipAvatar(
                        imageUrl = post.author.photoUrl.ifBlank { fallbackAvatarUrl(post.author.name) },
                        contentDescription = post.author.name,
                        modifier = Modifier.size(40.dp),
                        vipTierId = post.author.vipTierId,
                        premium = post.author.premium,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = post.author.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = post.timeLabel.ifBlank { post.topic },
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                        )
                    }
                }

                IconButton(onClick = {}) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = post.text,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
            )

            if (post.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.tags.forEach { tag -> NovaChip(text = "#$tag", selected = true) }
                }
            }

            if (post.allMediaUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                PostMediaPreview(
                    mediaUrls = post.allMediaUrls,
                    thumbnailUrl = post.thumbnailUrl,
                    onOpen = { index -> onOpenMedia(post.allMediaUrls, index) },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLike) {
                    Icon(
                        if (post.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (post.likedByMe) Color(0xFFFF4D6D) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(post.likes.toString(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(onClick = onComment) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(post.comments.toString(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(post.shares.toString(), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ImageGrid(count: Int) {
    val shape = RoundedCornerShape(16.dp)
    when (count) {
        1 -> Box(modifier = Modifier.fillMaxWidth().height(240.dp).clip(shape).background(Color.DarkGray))
        2 -> Row(modifier = Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(shape).background(Color.DarkGray))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(shape).background(Color.DarkGray))
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(shape).background(Color.DarkGray))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(shape).background(Color.DarkGray))
                }
                Row(modifier = Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(shape).background(Color.DarkGray))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(shape).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                        if (count > 4) {
                            Text(
                                text = "+${count - 4}",
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun genderLabel(gender: String): String {
    val normalized = gender.trim().lowercase()
    return when {
        normalized.contains("female") || normalized.contains("woman") || normalized.contains("girl") -> "Female"
        normalized.contains("male") || normalized.contains("man") || normalized.contains("boy") -> "Male"
        else -> "Gender"
    }
}

private fun fallbackAvatarUrl(name: String): String {
    val safeName = if (name.isBlank()) "Nova User" else name.trim()
    val encoded = java.net.URLEncoder.encode(safeName, Charsets.UTF_8)
    return "https://ui-avatars.com/api/?name=$encoded&background=6C5CE7&color=FFFFFF&size=512"
}

private fun shareProfilePost(context: android.content.Context, post: CommunityPost) {
    val link = "https://nova.app/community/post/${post.id}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${post.author.name}: ${post.text.take(120)}\n$link")
    }
    context.startActivity(Intent.createChooser(intent, "Share post"))
}
