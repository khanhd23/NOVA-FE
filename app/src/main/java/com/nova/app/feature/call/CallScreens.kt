package com.nova.app.feature.call

import com.nova.app.core.designsystem.NovaColors

import androidx.compose.ui.res.stringResource
import com.nova.app.R
import com.nova.app.core.i18n.localizedCallEvent
import com.nova.app.core.i18n.localizedLabel

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.nova.app.core.model.CallDirection
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.model.CallSessionUiState
import com.nova.app.core.model.CallStatus
import com.nova.app.core.model.CallSummaryUiState
import com.nova.app.core.model.CallType
import com.nova.app.core.model.displayLabel
import com.nova.app.core.webrtc.NovaWebRtcEngineRegistry
import com.nova.app.core.ui.NovaButton
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.ui.theme.NOVATheme
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink
import com.nova.app.core.webrtc.TextureViewRenderer
import com.nova.app.core.call.CallAudioRoute
import com.nova.app.core.call.CallAudioState
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import kotlin.math.roundToInt

@Composable
fun VoiceCallScreen(
    uiState: CallSessionUiState,
    audioState: CallAudioState = CallAudioState(),
    onToggleAudioRoute: () -> Unit = {},
    onBack: () -> Unit,
    onAnswerCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    rememberCallPermissionsGranted(isVideoCall = false)

    CallScreen(
        uiState = uiState,
        selfAvatarUrl = "",
        accentColor = PurpleMain,
        subtitle = stringResource(R.string.call_voice),
        onBack = onBack,
        onAnswerCall = onAnswerCall,
        onEndCall = onEndCall,
        onToggleMic = onToggleMic,
        onToggleVideo = null,
        onSwitchCamera = {},
        audioState = audioState,
        onToggleAudioRoute = onToggleAudioRoute,
        modifier = modifier,
    )
}

@Composable
fun VideoCallScreen(
    uiState: CallSessionUiState,
    audioState: CallAudioState = CallAudioState(),
    isPictureInPicture: Boolean = false,
    onToggleAudioRoute: () -> Unit = {},
    selfAvatarUrl: String = "",
    onBack: () -> Unit,
    onAnswerCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleVideo: () -> Unit,
    onEnsureVideoPreview: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val hasCallPermissions = rememberCallPermissionsGranted(isVideoCall = true)

    LaunchedEffect(hasCallPermissions, uiState.isActive, uiState.isVideoCall, uiState.isVideoOn, uiState.callId) {
        if (hasCallPermissions && uiState.isActive && uiState.isVideoCall && uiState.isVideoOn) {
            onEnsureVideoPreview()
        }
    }

    CallScreen(
        uiState = uiState,
        selfAvatarUrl = selfAvatarUrl,
        accentColor = PurplePink,
        subtitle = stringResource(R.string.call_video),
        onBack = onBack,
        onAnswerCall = onAnswerCall,
        onEndCall = onEndCall,
        onToggleMic = onToggleMic,
        onToggleVideo = onToggleVideo,
        onSwitchCamera = onSwitchCamera,
        audioState = audioState,
        onToggleAudioRoute = onToggleAudioRoute,
        isPictureInPicture = isPictureInPicture,
        modifier = modifier,
    )
}

@Composable
fun CallSummaryScreen(
    summary: CallSummaryUiState,
    onBack: () -> Unit,
    onCallAgain: () -> Unit,
    onMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val brush = Brush.verticalGradient(
        colors = listOf(
            if (summary.callType == CallType.Video) PurplePink.copy(alpha = 0.38f) else PurpleMain.copy(alpha = 0.38f),
            Color(0xFF0A0B10),
            Color(0xFF090A0E),
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            NovaTopBar(
                title = stringResource(R.string.call_ended),
                subtitle = summary.participantName,
                onBack = onBack,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(
                            if (summary.durationSeconds > 0) PurpleMain.copy(alpha = 0.18f) else NovaColors.current.danger.copy(
                                alpha = 0.18f
                            )
                        )
                        .border(
                            1.dp,
                            if (summary.durationSeconds > 0) PurpleMain.copy(alpha = 0.28f) else NovaColors.current.danger.copy(
                                alpha = 0.28f
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (summary.callType == CallType.Video) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = null,
                        tint = if (summary.durationSeconds > 0) PurpleMain else NovaColors.current.danger,
                        modifier = Modifier.size(54.dp),
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = summary.participantName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = summaryStatusText(summary),
                    color = summaryStatusColor(summary),
                    fontSize = 14.sp,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NovaButton(
                    text = stringResource(R.string.call_again),
                    onClick = onCallAgain,
                )
                OutlinedButton(
                    onClick = onMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.call_message))
                }
            }
        }
    }
}

@Composable
fun FloatingCallWindow(
    uiState: CallSessionUiState,
    onExpand: () -> Unit,
    onAnswerCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardWidth = 168.dp
    val stageHeight = 200.dp
    val controlsHeight = 56.dp
    val cardHeight = stageHeight + controlsHeight

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val marginPx = with(density) { 16.dp.toPx() }
        val maxOffsetX = (with(density) { maxWidth.toPx() } - cardWidthPx - marginPx).coerceAtLeast(marginPx)
        val maxOffsetY = (with(density) { maxHeight.toPx() } - cardHeightPx - marginPx).coerceAtLeast(marginPx)

        var offsetX by remember(maxWidth, maxHeight) { mutableFloatStateOf(maxOffsetX) }
        val topBarBottomPx = with(density) {
            WindowInsets.statusBars.getTop(this).toFloat() + TOP_BAR_HEIGHT.toPx()
        }
        var offsetY by remember(maxWidth, maxHeight) {
            mutableFloatStateOf(topBarBottomPx.coerceIn(marginPx, maxOffsetY))
        }

        Card(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(width = cardWidth, height = cardHeight)
                .pointerInput(uiState.callId) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(marginPx, maxOffsetX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(marginPx, maxOffsetY)
                    }
                },
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF12131A)),
            elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Stage: video or avatar. Tap to return to the full call screen.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stageHeight)
                        .clickable(onClick = onExpand),
                ) {
                    if (uiState.isVideoCall && uiState.isAnswered && uiState.isRemoteVideoOn) {
                        RemoteVideoSurface(modifier = Modifier.fillMaxSize())
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1A1B24)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CallParticipantAvatar(
                                name = uiState.participantName,
                                accentColor = if (uiState.isVideoCall) PurplePink else PurpleMain,
                                modifier = Modifier.size(72.dp),
                            )
                        }
                    }

                    // Top bar: status/timer on the left, expand on the right.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    uiState.isRinging && uiState.direction == CallDirection.Incoming -> stringResource(R.string.call_incoming)
                                    uiState.isRinging -> stringResource(R.string.call_calling_ellipsis)
                                    uiState.isAnswered -> callProgressText(uiState)
                                    else -> uiState.status.localizedLabel()
                                },
                                color = if (uiState.isReconnecting) Color(0xFFFFB020) else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(
                                text = uiState.participantName,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.OpenInFull,
                                contentDescription = stringResource(R.string.call_expand),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // Control bar below the stage, so buttons never cover the video or the timer.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(controlsHeight)
                        .background(Color(0xFF0B0C11)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        uiState.isRinging && uiState.direction == CallDirection.Incoming -> {
                            CallControlButton(icon = Icons.Default.Call, backgroundColor = CallAnswerGreen, onClick = onAnswerCall, size = 38)
                            CallControlButton(icon = Icons.Default.CallEnd, backgroundColor = CallHangUpRed, onClick = onEndCall, size = 38)
                        }
                        uiState.isRinging -> {
                            CallControlButton(icon = Icons.Default.CallEnd, backgroundColor = CallHangUpRed, onClick = onEndCall, size = 38)
                        }
                        else -> {
                            CallControlButton(
                                icon = if (uiState.isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                                backgroundColor = if (uiState.isMicOn) Color.White.copy(alpha = 0.12f) else Color.White,
                                iconTint = if (uiState.isMicOn) Color.White else Color.Black,
                                onClick = onToggleMic,
                                size = 36,
                            )
                            if (uiState.isVideoCall) {
                                CallControlButton(
                                    icon = if (uiState.isVideoOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    backgroundColor = if (uiState.isVideoOn) Color.White.copy(alpha = 0.12f) else Color.White,
                                    iconTint = if (uiState.isVideoOn) Color.White else Color.Black,
                                    onClick = onToggleVideo,
                                    size = 36,
                                )
                            }
                            CallControlButton(icon = Icons.Default.CallEnd, backgroundColor = CallHangUpRed, onClick = onEndCall, size = 36)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallScreen(
    uiState: CallSessionUiState,
    selfAvatarUrl: String,
    accentColor: Color,
    subtitle: String,
    onBack: () -> Unit,
    onAnswerCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleVideo: (() -> Unit)?,
    onSwitchCamera: () -> Unit,
    audioState: CallAudioState,
    onToggleAudioRoute: () -> Unit,
    isPictureInPicture: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isVideo = uiState.isVideoCall

    if (isPictureInPicture && isVideo) {
        // Floating window outside the app: only the other person's video.
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            if (uiState.isRemoteVideoOn) {
                RemoteVideoSurface(modifier = Modifier.fillMaxSize())
            } else {
                RemoteVideoOffStage(uiState.participantName, PurplePink)
            }
        }
        return
    }
    val isAnswered = uiState.isAnswered

    val brush = Brush.verticalGradient(
        colors = listOf(
            accentColor.copy(alpha = if (isVideo) 0.55f else 0.42f),
            Color(0xFF0C0D12),
            Color(0xFF090A0E),
        )
    )

    if (isVideo && isAnswered) {
        ConnectedVideoCallStage(
            uiState = uiState,
            selfAvatarUrl = selfAvatarUrl,
            onBack = onBack,
            onAnswerCall = onAnswerCall,
            onEndCall = onEndCall,
            onToggleMic = onToggleMic,
            onToggleVideo = onToggleVideo,
            onSwitchCamera = onSwitchCamera,
            audioState = audioState,
            onToggleAudioRoute = onToggleAudioRoute,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(brush)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                NovaTopBar(
                    title = uiState.participantName.ifBlank { stringResource(R.string.call_default_name) },
                    subtitle = buildSubtitle(uiState, subtitle),
                    onBack = onBack,
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isVideo) {
                    VideoCallBackdrop(
                        uiState = uiState,
                        accentColor = accentColor,
                    )
                } else {
                    CallCenterStage(
                        uiState = uiState,
                        accentColor = accentColor,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                CallActions(
                    uiState = uiState,
                    onAnswerCall = onAnswerCall,
                    onEndCall = onEndCall,
                    onToggleMic = onToggleMic,
                    onToggleVideo = onToggleVideo,
                    onSwitchCamera = onSwitchCamera,
                    audioState = audioState,
                    onToggleAudioRoute = onToggleAudioRoute,
                )
            }

            if (isVideo) {
                DraggableLocalPreview(
                    uiState = uiState,
                    selfAvatarUrl = selfAvatarUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ConnectedVideoCallStage(
    uiState: CallSessionUiState,
    selfAvatarUrl: String,
    onBack: () -> Unit,
    onAnswerCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleVideo: (() -> Unit)?,
    onSwitchCamera: () -> Unit,
    audioState: CallAudioState,
    onToggleAudioRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Without this the last received frame stays frozen after the other person turns
        // their camera off.
        if (uiState.isRemoteVideoOn) {
            RemoteVideoSurface(modifier = Modifier.fillMaxSize())
        } else {
            RemoteVideoOffStage(uiState.participantName, PurplePink)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = callProgressText(uiState),
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        DraggableLocalPreview(
            uiState = uiState,
            selfAvatarUrl = selfAvatarUrl,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f),
                        )
                    )
                )
                .padding(top = 54.dp),
        ) {
            CallActions(
                uiState = uiState,
                onAnswerCall = onAnswerCall,
                onEndCall = onEndCall,
                onToggleMic = onToggleMic,
                onToggleVideo = onToggleVideo,
                onSwitchCamera = onSwitchCamera,
                audioState = audioState,
                onToggleAudioRoute = onToggleAudioRoute,
            )
        }
    }
}

@Composable
private fun DraggableLocalPreview(
    uiState: CallSessionUiState,
    selfAvatarUrl: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val cardWidth = 118.dp
        val cardHeight = 164.dp
        val margin = 18.dp
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val marginPx = with(density) { margin.toPx() }
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val maxOffsetX = (maxWidthPx - cardWidthPx - marginPx).coerceAtLeast(marginPx)
        val maxOffsetY = (maxHeightPx - cardHeightPx - marginPx).coerceAtLeast(marginPx)

        val topOffsetY = marginPx + 78f
        val bottomOffsetY = maxOffsetY
        var offsetX by remember(maxWidth, maxHeight) { mutableFloatStateOf(maxOffsetX) }
        var offsetY by remember(maxWidth, maxHeight) { mutableFloatStateOf(topOffsetY.coerceAtMost(maxOffsetY)) }

        fun snapToNearestCorner() {
            val left = marginPx
            val right = maxOffsetX
            val top = topOffsetY.coerceAtMost(maxOffsetY)
            val bottom = bottomOffsetY
            offsetX = if (offsetX < (left + right) / 2f) left else right
            offsetY = if (offsetY < (top + bottom) / 2f) top else bottom
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(cardWidth, cardHeight)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                .pointerInput(uiState.callId, uiState.isVideoOn) {
                    detectDragGestures(
                        onDragEnd = { snapToNearestCorner() },
                        onDragCancel = { snapToNearestCorner() },
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(marginPx, maxOffsetX)
                        offsetY = (offsetY + dragAmount.y).coerceIn(topOffsetY.coerceAtMost(maxOffsetY), maxOffsetY)
                    }
                },
        ) {
            if (uiState.isVideoOn) {
                LocalVideoSurface(modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (selfAvatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = selfAvatarUrl,
                            contentDescription = stringResource(R.string.call_your_avatar),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            PurpleMain.copy(alpha = 0.55f),
                                            Color(0xFF171923),
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.86f),
                                modifier = Modifier.size(42.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.62f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = stringResource(R.string.call_camera_off),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(58.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CallCenterStage(
    uiState: CallSessionUiState,
    accentColor: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(if (uiState.isVideoCall) 148.dp else 132.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (uiState.isVideoCall) Icons.Default.Videocam else Icons.Default.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(if (uiState.isVideoCall) 72.dp else 64.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = uiState.participantName,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when {
                uiState.isRinging && uiState.direction == CallDirection.Incoming -> stringResource(R.string.call_incoming_call)
                uiState.isRinging -> stringResource(R.string.call_calling_ellipsis)
                uiState.isAnswered -> callProgressText(uiState)
                else -> uiState.status.localizedLabel()
            },
            color = if (uiState.isReconnecting) NovaColors.current.warning else accentColor,
            fontSize = 16.sp,
        )

        if (!uiState.isAnswered) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.direction.localizedLabel(),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun VideoCallBackdrop(
    uiState: CallSessionUiState,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(420.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1A1B24),
                        Color(0xFF0E1017),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp)),
    ) {
        if (uiState.isVideoCall && uiState.isAnswered) {
            if (uiState.isRemoteVideoOn) {
                RemoteVideoSurface(modifier = Modifier.fillMaxSize())
            } else {
                RemoteVideoOffStage(uiState.participantName, accentColor)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.34f),
                            Color.Transparent,
                        ),
                        center = Offset(220f, 160f),
                        radius = 620f,
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(18.dp)
        ) {
            Text(
                text = if (uiState.isAnswered) stringResource(R.string.call_live_video) else if (uiState.direction == CallDirection.Incoming) stringResource(R.string.call_incoming_video) else stringResource(R.string.call_calling_ellipsis),
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            if (uiState.isRinging) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = uiState.participantName,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (uiState.direction == CallDirection.Incoming) stringResource(R.string.call_swipe_to_answer) else stringResource(R.string.call_waiting_answer),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                )
            }
        }

        if (!uiState.isAnswered) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(156.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CallParticipantAvatar(
                    name = uiState.participantName,
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

    }
}

@Composable
private fun CallParticipantAvatar(
    name: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accentColor.copy(alpha = 0.78f),
                        PurplePink.copy(alpha = 0.58f),
                        Color(0xFF171923),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RemoteVideoOffStage(
    participantName: String,
    accentColor: Color,
    avatarSize: Dp = 156.dp,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = 0.42f), Color(0xFF090A0E)),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CallParticipantAvatar(
                name = participantName,
                accentColor = accentColor,
                modifier = Modifier.size(avatarSize),
            )
            Spacer(modifier = Modifier.height(avatarSize / 8))
            Icon(
                imageVector = Icons.Default.VideocamOff,
                contentDescription = stringResource(R.string.call_camera_off),
                tint = Color.White.copy(alpha = 0.84f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun CallActions(
    uiState: CallSessionUiState,
    onAnswerCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleVideo: (() -> Unit)?,
    onSwitchCamera: () -> Unit,
    audioState: CallAudioState,
    onToggleAudioRoute: () -> Unit,
) {
    val toggleVideo = onToggleVideo ?: {}

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            uiState.isRinging && uiState.direction == CallDirection.Incoming -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CallControlButton(
                        icon = Icons.Default.Call,
                        backgroundColor = CallAnswerGreen,
                        onClick = onAnswerCall,
                        size = 64,
                    )
                    CallControlButton(
                        icon = Icons.Default.CallEnd,
                        backgroundColor = CallHangUpRed,
                        onClick = onEndCall,
                        size = 64,
                    )
                }
            }

            uiState.isVideoCall -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AudioRouteButton(audioState = audioState, onClick = onToggleAudioRoute)
                    CallControlButton(
                        icon = if (uiState.isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                        backgroundColor = if (uiState.isMicOn) Color.White.copy(alpha = 0.12f) else Color.White,
                        iconTint = if (uiState.isMicOn) Color.White else Color.Black,
                        onClick = onToggleMic,
                    )
                    CallControlButton(
                        icon = if (uiState.isVideoOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        backgroundColor = Color.White.copy(alpha = 0.12f),
                        onClick = toggleVideo,
                    )
                    CallControlButton(
                        icon = Icons.Default.Cameraswitch,
                        backgroundColor = Color.White.copy(alpha = 0.12f),
                        onClick = onSwitchCamera,
                    )
                    CallControlButton(
                        icon = Icons.Default.CallEnd,
                        backgroundColor = CallHangUpRed,
                        onClick = onEndCall,
                    )
                }
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AudioRouteButton(audioState = audioState, onClick = onToggleAudioRoute)
                    CallControlButton(
                        icon = if (uiState.isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                        backgroundColor = if (uiState.isMicOn) Color.White.copy(alpha = 0.12f) else Color.White,
                        iconTint = if (uiState.isMicOn) Color.White else Color.Black,
                        onClick = onToggleMic,
                    )
                    CallControlButton(
                        icon = Icons.Default.CallEnd,
                        backgroundColor = CallHangUpRed,
                        onClick = onEndCall,
                    )
                }
            }
        }
    }
}

@Composable
fun CallControlButton(
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 56,
    iconTint: Color = Color.White,
    contentDescription: String? = null,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(backgroundColor),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = iconTint)
    }
}

/** Messenger-style audio button: highlighted when audio leaves the earpiece; tap to cycle outputs. */
@Composable
private fun AudioRouteButton(audioState: CallAudioState, onClick: () -> Unit) {
    val highlighted = audioState.route != CallAudioRoute.Earpiece
    val (icon, label) = when (audioState.route) {
        CallAudioRoute.Earpiece -> Icons.AutoMirrored.Filled.VolumeUp to stringResource(R.string.call_audio_earpiece)
        CallAudioRoute.Speaker -> Icons.AutoMirrored.Filled.VolumeUp to stringResource(R.string.call_audio_speaker)
        CallAudioRoute.Headset -> Icons.Default.Headset to stringResource(R.string.call_audio_headset)
        CallAudioRoute.Bluetooth -> Icons.Default.BluetoothAudio to stringResource(R.string.call_audio_bluetooth)
    }
    CallControlButton(
        icon = icon,
        backgroundColor = if (highlighted) Color.White else Color.White.copy(alpha = 0.12f),
        iconTint = if (highlighted) Color.Black else Color.White,
        contentDescription = label,
        onClick = onClick,
    )
}

/** Timer once media flows; otherwise "Connecting…" / "Reconnecting…". */
@Composable
private fun callProgressText(uiState: CallSessionUiState): String = when {
    uiState.isReconnecting -> stringResource(R.string.call_reconnecting)
    !uiState.isMediaConnected -> stringResource(R.string.call_connecting)
    else -> formatDuration(uiState.durationSeconds)
}

@Composable
private fun rememberCallPermissionsGranted(isVideoCall: Boolean): Boolean {
    val context = LocalContext.current
    val requiredPermissions = remember(isVideoCall) {
        if (isVideoCall) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }
    var hasAllPermissions by remember(requiredPermissions.contentToString()) {
        mutableStateOf(
            requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasAllPermissions = requiredPermissions.all { permission -> result[permission] == true }
    }

    LaunchedEffect(isVideoCall, hasAllPermissions) {
        val hasAll = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        hasAllPermissions = hasAll
        if (!hasAll) {
            launcher.launch(requiredPermissions)
        }
    }
    return hasAllPermissions
}

@Composable
private fun WebRtcSurface(
    modifier: Modifier = Modifier,
    mirror: Boolean,
) {
    // TextureView-based so Modifier.clip (rounded preview card) actually crops the video.
    val rendererRef = remember { arrayOfNulls<TextureViewRenderer>(1) }

    AndroidView(
        factory = { context ->
            TextureViewRenderer(context).also { renderer ->
                rendererRef[0] = renderer
                if (mirror) {
                    NovaWebRtcEngineRegistry.engine?.attachLocalRenderer(renderer)
                } else {
                    NovaWebRtcEngineRegistry.engine?.attachRemoteRenderer(renderer)
                }
            }
        },
        modifier = modifier,
    )

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            rendererRef[0]?.let { renderer ->
                if (mirror) {
                    NovaWebRtcEngineRegistry.engine?.detachLocalRenderer(renderer)
                } else {
                    NovaWebRtcEngineRegistry.engine?.detachRemoteRenderer(renderer)
                }
                renderer.release()
            }
            rendererRef[0] = null
        }
    }
}

@Composable
private fun RemoteVideoSurface(modifier: Modifier = Modifier) {
    WebRtcSurface(modifier = modifier, mirror = false)
}

@Composable
private fun LocalVideoSurface(modifier: Modifier = Modifier) {
    WebRtcSurface(modifier = modifier, mirror = true)
}

@Composable
private fun summaryStatusText(summary: CallSummaryUiState): String {
    return when {
        summary.durationSeconds > 0 -> "${stringResource(R.string.call_connected)} • ${formatDuration(summary.durationSeconds)}"
        summary.endReason == CallEndReason.Missed -> stringResource(R.string.call_missed)
        summary.endReason == CallEndReason.NoAnswer -> stringResource(R.string.call_no_answer)
        summary.endReason == CallEndReason.Declined -> stringResource(R.string.call_declined)
        summary.endReason == CallEndReason.Rejected -> stringResource(R.string.call_rejected)
        summary.endReason == CallEndReason.Busy -> stringResource(R.string.call_busy)
        summary.endReason == CallEndReason.Canceled -> stringResource(R.string.call_canceled)
        summary.endReason == CallEndReason.Dropped -> stringResource(R.string.call_dropped)
        else -> stringResource(R.string.call_ended)
    }
}

@Composable
private fun summaryStatusColor(summary: CallSummaryUiState): Color {
    return if (summary.durationSeconds > 0) {
        if (summary.callType == CallType.Video) PurplePink else PurpleMain
    } else {
        NovaColors.current.danger
    }
}

@Composable
private fun buildSubtitle(
    uiState: CallSessionUiState,
    fallback: String,
): String {
    return when {
        uiState.isRinging && uiState.direction == CallDirection.Incoming -> stringResource(R.string.call_incoming_call)
        uiState.isRinging -> stringResource(R.string.call_calling)
        uiState.isAnswered -> callProgressText(uiState)
        uiState.isEnded -> uiState.endReason?.localizedLabel() ?: stringResource(R.string.call_status_ended)
        else -> fallback
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Preview
@Composable
fun VoiceCallScreenPreview() {
    NOVATheme {
        VoiceCallScreen(
            uiState = CallSessionUiState(
                participantName = "Alex Johnson",
                callType = CallType.Voice,
                direction = CallDirection.Outgoing,
                status = CallStatus.InCall,
                isActive = true,
                isMicOn = true,
                isVideoOn = false,
                durationSeconds = 42,
                startedAtLabel = "14:30",
                lastEventLabel = "Connected",
            ),
            onBack = {},
            onAnswerCall = {},
            onEndCall = {},
            onToggleMic = {},
        )
    }
}

@Preview
@Composable
fun VideoCallScreenPreview() {
    NOVATheme {
        VideoCallScreen(
            uiState = CallSessionUiState(
                participantName = "Alex Johnson",
                callType = CallType.Video,
                direction = CallDirection.Outgoing,
                status = CallStatus.InCall,
                isActive = true,
                isMicOn = true,
                isVideoOn = true,
                durationSeconds = 86,
                startedAtLabel = "14:30",
                lastEventLabel = "Connected",
            ),
            onBack = {},
            onAnswerCall = {},
            onEndCall = {},
            onToggleMic = {},
            onToggleVideo = {},
        )
    }
}

@Preview
@Composable
fun CallSummaryScreenPreview() {
    NOVATheme {
        CallSummaryScreen(
            summary = CallSummaryUiState(
                participantName = "Alex Johnson",
                callType = CallType.Video,
                direction = CallDirection.Incoming,
                durationSeconds = 172,
                endReason = CallEndReason.Missed,
                startedAtLabel = "14:30",
                endedAtLabel = "14:33",
                isMicOn = true,
                isVideoOn = true,
            ),
            onBack = {},
            onCallAgain = {},
            onMessage = {},
        )
    }
}

@Preview
@Composable
fun FloatingCallWindowPreview() {
    NOVATheme {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            FloatingCallWindow(
                uiState = CallSessionUiState(
                    participantName = "Alex Johnson",
                callType = CallType.Video,
                direction = CallDirection.Outgoing,
                status = CallStatus.InCall,
                isActive = true,
                isMinimized = true,
                isMicOn = true,
                isVideoOn = true,
                durationSeconds = 58,
                    startedAtLabel = "14:30",
                    lastEventLabel = "Connected",
                ),
                onExpand = {},
                onAnswerCall = {},
                onEndCall = {},
                onToggleMic = {},
                onToggleVideo = {},
            )
        }
    }
}



// Call screens are always dark: fixed colors that keep white icons readable.
private val CallAnswerGreen = Color(0xFF16A34A)
private val CallHangUpRed = Color(0xFFDC2626)

/** Height of the screen top bars (NovaTopBar / call header) below the status bar. */
private val TOP_BAR_HEIGHT = 76.dp
