package com.nova.app.core.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.nova.app.core.model.PostMediaKind
import com.nova.app.core.model.detectPostMediaKind
import com.nova.app.core.model.normalizedPostMediaUrls

@Composable
fun NovaVideoView(
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    showControls: Boolean = true,
    onClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = autoPlay
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black)
            .clickable { onClick() }
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = showControls
                    controllerShowTimeoutMs = 2500
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.useController = showControls
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!showControls) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

@Composable
fun PostMediaPreview(
    mediaUrls: List<String>,
    thumbnailUrl: String? = null,
    modifier: Modifier = Modifier,
    onOpen: (Int) -> Unit,
) {
    val safeMediaUrls = remember(mediaUrls) { mediaUrls.normalizedPostMediaUrls() }
    if (safeMediaUrls.isEmpty()) return

    val mediaKinds = remember(safeMediaUrls) { safeMediaUrls.map { it.detectPostMediaKind() } }
    val shape = RoundedCornerShape(22.dp)

    if (safeMediaUrls.size == 1) {
        PostMediaTile(
            mediaUrl = safeMediaUrls.first(),
            kind = mediaKinds.firstOrNull() ?: PostMediaKind.UNKNOWN,
            thumbnailUrl = thumbnailUrl,
            modifier = modifier
                .fillMaxWidth()
                .height(244.dp)
                .clip(shape)
                .clickable { onOpen(0) }
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { safeMediaUrls.size })

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(shape)
        ) {
            HorizontalPager(state = pagerState) { page ->
                PostMediaTile(
                    mediaUrl = safeMediaUrls[page],
                    kind = mediaKinds.getOrNull(page) ?: PostMediaKind.UNKNOWN,
                    thumbnailUrl = thumbnailUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onOpen(page) }
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${safeMediaUrls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(safeMediaUrls.size) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (pagerState.currentPage == index) Color.White else Color.White.copy(alpha = 0.3f))
                )
            }
        }
    }
}

@Composable
private fun PostMediaTile(
    mediaUrl: String,
    kind: PostMediaKind,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black)
    ) {
        when (kind) {
            PostMediaKind.VIDEO -> {
                val poster = thumbnailUrl?.takeIf { it.isNotBlank() } ?: mediaUrl
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }
            else -> {
                AsyncImage(
                    model = mediaUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun MediaViewer(
    mediaUrls: List<String>,
    startIndex: Int = 0,
    onDismiss: () -> Unit,
) {
    val safeMediaUrls = remember(mediaUrls) { mediaUrls.normalizedPostMediaUrls() }
    val initialIndex = startIndex.coerceIn(0, (safeMediaUrls.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { safeMediaUrls.size.coerceAtLeast(1) }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (safeMediaUrls.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No media", color = Color.White.copy(alpha = 0.7f))
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (safeMediaUrls[page].detectPostMediaKind()) {
                    PostMediaKind.VIDEO -> NovaVideoView(
                        url = safeMediaUrls[page],
                        modifier = Modifier.fillMaxSize(),
                        autoPlay = pagerState.currentPage == page,
                        showControls = true
                    )
                    else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = safeMediaUrls[page],
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            if (safeMediaUrls.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1}/${safeMediaUrls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (safeMediaUrls.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(safeMediaUrls.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (selected) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
@Deprecated("Use the version without isVideo. Mixed media pages are now detected per item.")
fun MediaViewer(
    mediaUrls: List<String>,
    isVideo: Boolean,
    startIndex: Int = 0,
    onDismiss: () -> Unit,
) {
    MediaViewer(
        mediaUrls = mediaUrls,
        startIndex = startIndex,
        onDismiss = onDismiss
    )
}
