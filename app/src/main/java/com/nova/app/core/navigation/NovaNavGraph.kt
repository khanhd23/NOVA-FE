package com.nova.app.core.navigation

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nova.app.core.backend.ACTION_ANSWER_CALL
import com.nova.app.core.backend.ACTION_OPEN_CALL
import com.nova.app.core.backend.ACTION_OPEN_CHAT
import com.nova.app.core.backend.ACTION_OPEN_NOTIFICATION_TARGET
import com.nova.app.core.backend.ACTION_OPEN_PROFILE
import com.nova.app.core.backend.BackendCommunityCommentRequest
import com.nova.app.core.backend.BackendCommunityPost
import com.nova.app.core.backend.BackendCommunityShareRequest
import com.nova.app.core.backend.BackendProfile
import com.nova.app.core.backend.CallNotificationPayload
import com.nova.app.core.backend.ChatNotificationPayload
import com.nova.app.core.backend.EXTRA_NOTIFICATION_TARGET
import com.nova.app.core.backend.EXTRA_MESSAGE_PREVIEW
import com.nova.app.core.backend.EXTRA_PARTICIPANT_NAME
import com.nova.app.core.backend.EXTRA_PEER_USER_ID
import com.nova.app.core.backend.EXTRA_PROFILE_USER_ID
import com.nova.app.core.backend.EXTRA_THREAD_ID
import com.nova.app.core.auth.GoogleIdentityClient
import com.nova.app.core.backend.toCallNotificationPayload
import com.nova.app.core.backend.toChatNotificationPayload
import com.nova.app.core.di.NovaContainer
import com.nova.app.core.model.AppSettings
import com.nova.app.core.model.ChatThread
import com.nova.app.core.model.CallSessionUiState
import com.nova.app.core.model.CallType
import com.nova.app.core.model.MessagesUiState
import com.nova.app.core.model.NotificationItem
import com.nova.app.core.model.ProfileUiState
import com.nova.app.core.model.UserCard
import com.nova.app.core.viewmodel.MessagesViewModel
import com.nova.app.core.viewmodel.CallViewModel
import com.nova.app.core.viewmodel.ChatViewModel
import com.nova.app.core.viewmodel.FlowViewModel
import com.nova.app.core.viewmodel.NotificationsViewModel
import com.nova.app.core.viewmodel.ProfileConnectionsViewModel
import com.nova.app.core.viewmodel.LaunchViewModel
import com.nova.app.core.viewmodel.ProfileViewModel
import com.nova.app.core.viewmodel.SearchViewModel
import com.nova.app.feature.auth.LoginScreen
import com.nova.app.feature.call.CallSummaryScreen
import com.nova.app.feature.call.FloatingCallWindow
import com.nova.app.feature.call.VideoCallScreen
import com.nova.app.feature.call.VoiceCallScreen
import com.nova.app.feature.chat.ChatDetailScreen
import com.nova.app.feature.notifications.NotificationsScreen
import com.nova.app.feature.search.SearchScreen
import com.nova.app.feature.onboarding.OnboardingScreen
import com.nova.app.feature.onboarding.SplashScreen
import com.nova.app.core.ui.MediaViewer
import com.nova.app.feature.profile.PublicProfileScreen
import com.nova.app.feature.profile.ProfileConnectionsScreen
import com.nova.app.feature.profile.ProfileSetupScreen
import com.nova.app.feature.settings.PremiumScreen
import com.nova.app.feature.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPLASH_DELAY_MS = 1600L

private data class PreviewMediaState(
    val urls: List<String>,
    val startIndex: Int = 0,
)

@Composable
fun NovaNavHost(
    container: NovaContainer,
    flowViewModel: FlowViewModel,
    callViewModel: CallViewModel,
    settings: AppSettings,
    launchIntent: Intent? = null,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val launchViewModel: LaunchViewModel = viewModel(factory = container.viewModelFactory)
    val messagesViewModel: MessagesViewModel = viewModel(factory = container.viewModelFactory)
    val chatViewModel: ChatViewModel = viewModel(factory = container.viewModelFactory)
    val communityViewModel: com.nova.app.core.viewmodel.CommunityViewModel = viewModel(factory = container.viewModelFactory)
    val profileViewModel: ProfileViewModel = viewModel(factory = container.viewModelFactory)
    val profileConnectionsViewModel: ProfileConnectionsViewModel = viewModel(factory = container.viewModelFactory)
    val searchViewModel: SearchViewModel = viewModel(factory = container.viewModelFactory)
    val notificationsViewModel: NotificationsViewModel = viewModel(factory = container.viewModelFactory)
    val launchState by launchViewModel.uiState.collectAsStateWithLifecycle()
    val callState by callViewModel.uiState.collectAsStateWithLifecycle()
    val callSummary by callViewModel.lastSummary.collectAsStateWithLifecycle()
    val messagesState by messagesViewModel.uiState.collectAsStateWithLifecycle()
    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val communityState by communityViewModel.uiState.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val profileConnectionsState by profileConnectionsViewModel.uiState.collectAsStateWithLifecycle()
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val notificationsState by notificationsViewModel.uiState.collectAsStateWithLifecycle()
    val messagesUiState = messagesScreenState(messagesState)
    val profileUiState = profileScreenState(profileState, settings)
    var selectedChatThread by remember { mutableStateOf<ChatThread?>(null) }
    var previewMedia by remember { mutableStateOf<PreviewMediaState?>(null) }
    var profileFlowMode by rememberSaveable { mutableStateOf(ProfileFlowMode.Setup) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleIdentityClient = remember { GoogleIdentityClient() }

    fun openChatThread(
        threadId: String,
        peerUserId: String? = null,
        participantName: String? = null,
        photoUrl: String = "",
        verified: Boolean = false,
        online: Boolean = false,
        city: String = "",
        vipTierId: String? = null,
        vipTierName: String? = null,
        premium: Boolean = false,
    ) {
        val existingThread = messagesUiState.threads.firstOrNull { it.id == threadId }
        selectedChatThread = existingThread?.copy(
            user = existingThread.user.copy(
                id = peerUserId ?: existingThread.user.id,
                name = participantName ?: existingThread.user.name,
                photoUrl = photoUrl.ifBlank { existingThread.user.photoUrl },
                verified = verified || existingThread.user.verified,
                online = online || existingThread.user.online,
                city = city.ifBlank { existingThread.user.city },
                vipTierId = vipTierId ?: existingThread.user.vipTierId,
                vipTierName = vipTierName ?: existingThread.user.vipTierName,
                premium = premium || existingThread.user.premium,
            )
        ) ?: ChatThread(
            id = threadId,
            user = UserCard(
                id = peerUserId ?: threadId,
                name = participantName ?: "Chat",
                age = 0,
                photoUrl = photoUrl,
                verified = verified,
                online = online,
                city = city,
                vipTierId = vipTierId,
                vipTierName = vipTierName,
                premium = premium,
            ),
            lastMessage = "",
            unreadCount = 0,
            online = online,
        )
        navController.navigateTo(AppRoute.Chat)
    }

    fun handleNotificationNavigation(notification: NotificationItem) {
        val target = notification.actionTarget.orEmpty()
        when {
            target.startsWith("profile/") -> {
                val userId = target.removePrefix("profile/")
                if (userId.isNotBlank()) {
                    navController.navigateToProfileDetail(userId)
                } else {
                    navController.navigateTo(AppRoute.Home)
                }
            }
            target.startsWith("thread/") -> {
                val threadId = target.removePrefix("thread/")
                val thread = messagesUiState.threads.firstOrNull { it.id == threadId }
                if (thread != null) {
                    selectedChatThread = thread
                    navController.navigateTo(AppRoute.Chat)
                } else {
                    navController.navigateTo(AppRoute.Messages)
                }
            }
            target.startsWith("community/") -> navController.navigateTo(AppRoute.Community)
            target.startsWith("call/") -> {
                val threadId = notification.threadId.orEmpty()
                val thread = messagesUiState.threads.firstOrNull { it.id == threadId }
                if (thread != null) {
                    selectedChatThread = thread
                    navController.navigateTo(AppRoute.Chat)
                } else {
                    navController.navigateTo(AppRoute.Messages)
                }
            }
            else -> navController.navigateTo(AppRoute.Home)
        }
    }

    fun navigateAfterSignIn(session: com.nova.app.core.backend.BackendSession) {
        val target = when {
            !session.onboardingComplete -> AppRoute.Onboarding
            !session.profileComplete -> AppRoute.ProfileSetup
            else -> AppRoute.Home
        }
        if (target == AppRoute.ProfileSetup) {
            profileFlowMode = ProfileFlowMode.Setup
        }
        navController.replaceWith(target, AppRoute.SignIn)
    }

    LaunchedEffect(callViewModel) {
        callViewModel.endEvents.collect { event -> 
            if (event.replaceCurrentCallRoute) {
                navController.replaceWith(AppRoute.CallSummary, callRoute(event.summary.callType))
            } else {
                navController.navigateTo(AppRoute.CallSummary)
            }
        }
    }

    LaunchedEffect(launchIntent) {
        val intent = launchIntent ?: return@LaunchedEffect
        when (intent.action) {
            ACTION_OPEN_CHAT -> {
                intent.toChatNotificationPayload()?.let { payload ->
                    selectedChatThread = payload.toChatThread()
                    navController.navigateTo(AppRoute.Chat)
                }
            }
            ACTION_OPEN_CALL, ACTION_ANSWER_CALL -> {
                intent.toCallNotificationPayload()?.let { payload ->
                    selectedChatThread = payload.toChatThread(selectedChatThread)
                    startCallFromNotification(callViewModel, navController, payload, intent.action == ACTION_ANSWER_CALL)
                }
            }
            ACTION_OPEN_PROFILE -> {
                val userId = intent.getStringExtra(EXTRA_PROFILE_USER_ID).orEmpty()
                if (userId.isNotBlank()) {
                    navController.navigateToProfileDetail(userId)
                }
            }
            ACTION_OPEN_NOTIFICATION_TARGET -> {
                val target = intent.getStringExtra(EXTRA_NOTIFICATION_TARGET).orEmpty()
                when {
                    target.startsWith("profile/") -> {
                        val userId = intent.getStringExtra(EXTRA_PROFILE_USER_ID).orEmpty().ifBlank {
                            target.removePrefix("profile/")
                        }
                        if (userId.isNotBlank()) {
                            navController.navigateToProfileDetail(userId)
                        }
                    }
                    target.startsWith("community/") -> navController.navigateTo(AppRoute.Community)
                    target.startsWith("thread/") -> {
                        val threadId = intent.getStringExtra(EXTRA_THREAD_ID).orEmpty().ifBlank {
                            target.removePrefix("thread/")
                        }
                        if (threadId.isNotBlank()) {
                            selectedChatThread = ChatThread(
                                id = threadId,
                                user = UserCard(
                                    id = intent.getStringExtra(EXTRA_PEER_USER_ID).orEmpty().ifBlank { threadId },
                                    name = intent.getStringExtra(EXTRA_PARTICIPANT_NAME).orEmpty().ifBlank { "Chat" },
                                    age = 0,
                                    photoUrl = "",
                                    online = false,
                                ),
                                lastMessage = intent.getStringExtra(EXTRA_MESSAGE_PREVIEW).orEmpty(),
                                unreadCount = 0,
                                online = false,
                            )
                            navController.navigateTo(AppRoute.Chat)
                        }
                    }
                    else -> navController.navigateTo(AppRoute.Notifications)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = AppRoute.Splash.routeName(),
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(AppRoute.Splash.routeName()) {
                SplashScreen()
                LaunchedEffect(launchState.target) {
                    delay(SPLASH_DELAY_MS)
                    navController.replaceWith(launchState.target, AppRoute.Splash)
                }
            }

            composable(AppRoute.Onboarding.routeName()) {
                OnboardingScreen(
                    onFinish = {
                        flowViewModel.completeOnboarding()
                        navController.replaceWith(AppRoute.SignIn, AppRoute.Onboarding)
                    }
                )
            }

            composable(AppRoute.SignIn.routeName()) {
                LoginScreen(
                    onGoogleLogin = {
                        scope.launch {
                            runCatching { googleIdentityClient.getIdToken(context) }
                                .onSuccess { idToken ->
                                    flowViewModel.signInWithGoogle(
                                        idToken = idToken,
                                        onSuccess = ::navigateAfterSignIn,
                                        onError = { throwable ->
                                            Log.w("NovaNav", "Google backend sign-in failed", throwable)
                                        }
                                    )
                                }
                                .onFailure { throwable ->
                                    Log.w("NovaNav", "Google credential retrieval failed", throwable)
                                }
                        }
                    },
                    onFacebookLogin = {
                        flowViewModel.signInWithFacebook(
                            onSuccess = ::navigateAfterSignIn
                        )
                    },
                )
            }

            composable(AppRoute.ProfileSetup.routeName()) {
                ProfileSetupScreen(
                    profileUiState = profileUiState,
                    isEditing = profileFlowMode == ProfileFlowMode.Edit,
                    profileViewModel = profileViewModel,
                    onBack = { navController.popBackStack() },
                    onComplete = {
                        if (profileFlowMode == ProfileFlowMode.Edit) {
                            navController.popBackStack()
                        } else {
                            flowViewModel.completeProfile()
                            navController.replaceWith(AppRoute.Home, AppRoute.ProfileSetup)
                        }
                    }
                )
            }

            composable(AppRoute.Home.routeName()) {
                HomeShell(
                    messagesState = messagesUiState,
                    communityState = communityState,
                    communityViewModel = communityViewModel,
                    profileState = profileUiState,
                    notificationCount = notificationsState.unreadCount,
                    onChatClick = {
                        selectedChatThread = it
                        navController.navigateTo(AppRoute.Chat)
                    },
                    onSearchClick = {
                        navController.navigateTo(AppRoute.Search)
                    },
                    onNotificationClick = {
                        navController.navigateTo(AppRoute.Notifications)
                    },
                    onSettingsClick = { navController.navigateTo(AppRoute.Settings) },
                    onEditProfile = {
                        profileFlowMode = ProfileFlowMode.Edit
                        navController.navigateTo(AppRoute.ProfileSetup)
                    },
                    onOpenProfile = { userId -> navController.navigateToProfileDetail(userId) },
                    onOpenConnections = { tab -> navController.navigateToProfileConnections(profileUiState.user.id, tab) },
                    onProfilePostLike = profileViewModel::likePost,
                    onProfilePostComment = profileViewModel::commentPost,
                    onProfilePostShare = profileViewModel::sharePost,
                )
            }

            composable(AppRoute.Messages.routeName()) {
                HomeShell(
                    messagesState = messagesUiState,
                    communityState = communityState,
                    communityViewModel = communityViewModel,
                    profileState = profileUiState,
                    notificationCount = notificationsState.unreadCount,
                    onChatClick = {
                        selectedChatThread = it
                        navController.navigateTo(AppRoute.Chat)
                    },
                    onSearchClick = {
                        navController.navigateTo(AppRoute.Search)
                    },
                    onNotificationClick = {
                        navController.navigateTo(AppRoute.Notifications)
                    },
                    onSettingsClick = { navController.navigateTo(AppRoute.Settings) },
                    onEditProfile = {
                        profileFlowMode = ProfileFlowMode.Edit
                        navController.navigateTo(AppRoute.ProfileSetup)
                    },
                    onOpenProfile = { userId -> navController.navigateToProfileDetail(userId) },
                    onOpenConnections = { tab -> navController.navigateToProfileConnections(profileUiState.user.id, tab) },
                    onProfilePostLike = profileViewModel::likePost,
                    onProfilePostComment = profileViewModel::commentPost,
                    onProfilePostShare = profileViewModel::sharePost,
                    initialTab = 3,
                )
            }

            composable(AppRoute.Community.routeName()) {
                HomeShell(
                    messagesState = messagesUiState,
                    communityState = communityState,
                    communityViewModel = communityViewModel,
                    profileState = profileUiState,
                    notificationCount = notificationsState.unreadCount,
                    onChatClick = {
                        selectedChatThread = it
                        navController.navigateTo(AppRoute.Chat)
                    },
                    onSearchClick = {
                        navController.navigateTo(AppRoute.Search)
                    },
                    onNotificationClick = {
                        navController.navigateTo(AppRoute.Notifications)
                    },
                    onSettingsClick = { navController.navigateTo(AppRoute.Settings) },
                    onEditProfile = {
                        profileFlowMode = ProfileFlowMode.Edit
                        navController.navigateTo(AppRoute.ProfileSetup)
                    },
                    onOpenProfile = { userId -> navController.navigateToProfileDetail(userId) },
                    onOpenConnections = { tab -> navController.navigateToProfileConnections(profileUiState.user.id, tab) },
                    onProfilePostLike = profileViewModel::likePost,
                    onProfilePostComment = profileViewModel::commentPost,
                    onProfilePostShare = profileViewModel::sharePost,
                    initialTab = 1,
                )
            }

            composable(AppRoute.Search.routeName()) {
                SearchScreen(
                    uiState = searchState,
                    onBack = { navController.popBackStack() },
                    onQueryChange = searchViewModel::updateQuery,
                    onGenderChange = searchViewModel::setGender,
                    onLoadMore = searchViewModel::loadMore,
                    onRetry = searchViewModel::retry,
                    onOpenProfile = { result ->
                        navController.navigateToProfileDetail(result.id)
                    },
                )
            }

            composable(
                route = "${AppRoute.ProfileDetail.routeName()}/{userId}",
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId").orEmpty()
                var profile by remember { mutableStateOf<BackendProfile?>(null) }
                var posts by remember { mutableStateOf<List<BackendCommunityPost>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }

                suspend fun loadProfileDetail() {
                    loading = true
                    error = null

                    val fetchedProfile = runCatching { container.backendRuntime.fetchPublicProfile(userId) }.getOrNull()
                    val fetchedPosts = runCatching {
                        container.backendRuntime.fetchProfilePosts(userId, size = 100)
                    }.getOrNull()

                    profile = fetchedProfile
                    posts = fetchedPosts.orEmpty()
                    error = if (fetchedProfile == null) "Unable to load profile" else null
                    loading = false
                }

                LaunchedEffect(userId) {
                    if (userId.isBlank()) {
                        profile = null
                        posts = emptyList()
                        loading = false
                        error = "Invalid profile"
                        return@LaunchedEffect
                    }
                    loadProfileDetail()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    PublicProfileScreen(
                        profile = profile,
                        loading = loading,
                        error = error,
                        posts = posts,
                        onBack = { navController.popBackStack() },
                        onRefresh = {
                            scope.launch {
                                loadProfileDetail()
                            }
                        },
                        onToggleFollow = { followed ->
                            profile?.let { current ->
                                scope.launch {
                                    val updated = runCatching { container.backendRuntime.toggleFollow(current.userId, followed) }.getOrNull()
                                    if (updated != null) {
                                        profile = updated
                                    }
                                }
                            }
                        },
                        onOpenConnections = { tab ->
                            navController.navigateToProfileConnections(userId, tab)
                        },
                        onMessage = {
                            profile?.let { current ->
                                openChatThread(
                                    threadId = "dm-${current.userId}",
                                    peerUserId = current.userId,
                                    participantName = current.displayName,
                                    photoUrl = current.avatarUrl,
                                    verified = current.verified,
                                    online = current.online,
                                    city = current.city,
                                    vipTierId = current.vipTierId,
                                    vipTierName = current.vipTierName,
                                    premium = current.premium,
                                )
                            }
                        },
                        onOpenPhoto = { mediaUrl ->
                            if (mediaUrl.isNotBlank()) {
                                previewMedia = PreviewMediaState(urls = listOf(mediaUrl))
                            }
                        },
                        onOpenMedia = { mediaUrls, startIndex ->
                            if (mediaUrls.isNotEmpty()) {
                                previewMedia = PreviewMediaState(urls = mediaUrls, startIndex = startIndex)
                            }
                        },
                        onLikePost = { post ->
                            scope.launch {
                                val updated = container.backendRuntime.likeCommunityPost(post.id, !post.likedByMe)
                                if (updated != null) {
                                    posts = posts.map { if (it.id == updated.id) updated else it }
                                }
                            }
                        },
                        onCommentPost = { post, text ->
                            scope.launch {
                                val updated = container.backendRuntime.commentCommunityPost(
                                    post.id,
                                    BackendCommunityCommentRequest(text = text),
                                )
                                if (updated != null) {
                                    posts = posts.map { if (it.id == updated.id) updated else it }
                                }
                            }
                        },
                        onSharePost = { post ->
                            scope.launch {
                                val updated = container.backendRuntime.shareCommunityPost(
                                    post.id,
                                    BackendCommunityShareRequest(),
                                )
                                if (updated != null) {
                                    posts = posts.map { if (it.id == updated.post.id) updated.post else it }
                                }
                                shareProfilePost(context, post)
                            }
                        },
                    )

                    previewMedia?.let { mediaState ->
                        MediaViewer(
                            mediaUrls = mediaState.urls,
                            startIndex = mediaState.startIndex,
                            onDismiss = { previewMedia = null },
                        )
                    }
                }
            }

            composable(
                route = "${AppRoute.ProfileConnections.routeName()}/{userId}?tab={tab}",
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType },
                    navArgument("tab") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId").orEmpty()
                val initialTab = backStackEntry.arguments?.getString("tab").orEmpty().ifBlank { null }
                LaunchedEffect(userId) {
                    if (userId.isNotBlank()) {
                        profileConnectionsViewModel.load(userId, initialTab = initialTab)
                    }
                }
                ProfileConnectionsScreen(
                    uiState = profileConnectionsState,
                    onBack = { navController.popBackStack() },
                    onRefresh = profileConnectionsViewModel::refresh,
                    onSelectTab = profileConnectionsViewModel::selectTab,
                    onLoadMore = profileConnectionsViewModel::loadMore,
                    onOpenProfile = { targetUserId -> navController.navigateToProfileDetail(targetUserId) },
                )
            }

            composable(AppRoute.Notifications.routeName()) {
                NotificationsScreen(
                    uiState = notificationsState,
                    onBack = { navController.popBackStack() },
                    onRefresh = notificationsViewModel::refresh,
                    onOpenNotification = { notification ->
                        notificationsViewModel.markRead(notification.id)
                        handleNotificationNavigation(notification)
                    },
                )
            }

            composable(AppRoute.Chat.routeName()) {
                val chatUiState = chatScreenState(chatState, selectedChatThread)
                val activeThread = selectedChatThread ?: chatUiState.thread
                LaunchedEffect(activeThread.id) {
                    if (activeThread.id.isNotBlank()) {
                        chatViewModel.openThread(activeThread)
                    }
                }
                val threadId = activeThread.id
                val peerUserId = activeThread.user.id
                val participantName = activeThread.user.name
                ChatDetailScreen(
                    name = participantName,
                    uiState = chatUiState,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = {
                        navController.navigateToProfileDetail(peerUserId)
                    },
                    onVoiceCall = {
                        callViewModel.openVoiceCall(participantName, threadId, peerUserId)
                        navController.navigateTo(AppRoute.VoiceCall)
                    },
                    onVideoCall = {
                        callViewModel.openVideoCall(participantName, threadId, peerUserId)
                        navController.navigateTo(AppRoute.VideoCall)
                    },
                    onIncomingVoiceCall = {
                        callViewModel.startIncomingVoiceCall(participantName, threadId, peerUserId = peerUserId)
                        navController.navigateTo(AppRoute.VoiceCall)
                    },
                    onIncomingVideoCall = {
                        callViewModel.startIncomingVideoCall(participantName, threadId, peerUserId = peerUserId)
                        navController.navigateTo(AppRoute.VideoCall)
                    },
                    onCallAgain = { summary ->
                        val againThreadId = summary.threadId.ifBlank { threadId }
                        val againPeerUserId = summary.peerUserId.ifBlank { peerUserId }
                        when (summary.callType) {
                            CallType.Voice -> callViewModel.openVoiceCall(summary.participantName, againThreadId, againPeerUserId)
                            CallType.Video -> callViewModel.openVideoCall(summary.participantName, againThreadId, againPeerUserId)
                        }
                        navController.navigateTo(callRoute(summary.callType))
                    },
                    onSendMessage = { text, attachment -> chatViewModel.send(text, attachment) },
                    onLoadMore = { chatViewModel.loadMore() },
                )
            }

            composable(AppRoute.VoiceCall.routeName()) {
                val participantName = selectedChatThread?.user?.name ?: callState.participantName.ifBlank { "User" }
                LaunchedEffect(participantName) {
                    if (callState.isActive && callState.participantName == participantName && callState.callType == CallType.Voice) {
                        callViewModel.expand()
                    } else {
                        callViewModel.openVoiceCall(
                            participantName = participantName,
                            threadId = callState.threadId.ifBlank { selectedChatThread?.id.orEmpty() },
                            peerUserId = callState.peerUserId.ifBlank { selectedChatThread?.user?.id.orEmpty() },
                        )
                    }
                }
                VoiceCallScreen(
                    uiState = callState,
                    onBack = {
                        callViewModel.minimize()
                        navController.popBackStack()
                    },
                    onAnswerCall = callViewModel::answerCall,
                    onEndCall = callViewModel::hangUp,
                    onToggleMic = callViewModel::toggleMic,
                )
            }

            composable(AppRoute.VideoCall.routeName()) {
                val participantName = selectedChatThread?.user?.name ?: callState.participantName.ifBlank { "User" }
                LaunchedEffect(participantName) {
                    if (callState.isActive && callState.participantName == participantName && callState.callType == CallType.Video) {
                        callViewModel.expand()
                    } else {
                        callViewModel.openVideoCall(
                            participantName = participantName,
                            threadId = callState.threadId.ifBlank { selectedChatThread?.id.orEmpty() },
                            peerUserId = callState.peerUserId.ifBlank { selectedChatThread?.user?.id.orEmpty() },
                        )
                    }
                }
                VideoCallScreen(
                    uiState = callState,
                    selfAvatarUrl = profileUiState.user.photoUrl,
                    onBack = {
                        callViewModel.minimize()
                        navController.popBackStack()
                    },
                    onAnswerCall = callViewModel::answerCall,
                    onEndCall = callViewModel::hangUp,
                    onToggleMic = callViewModel::toggleMic,
                    onToggleVideo = callViewModel::toggleVideo,
                    onEnsureVideoPreview = callViewModel::ensureVideoPreview,
                    onSwitchCamera = callViewModel::switchCamera,
                )
            }

            composable(AppRoute.CallSummary.routeName()) {
                val summary = callSummary
                if (summary == null) {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                } else {
                    CallSummaryScreen(
                        summary = summary,
                        onBack = {
                            navController.popBackStack()
                        },
                        onCallAgain = {
                            val peerUserId = summary.peerUserId.ifBlank { selectedChatThread?.user?.id.orEmpty() }
                            val threadId = summary.threadId.ifBlank { selectedChatThread?.id.orEmpty() }
                            if (threadId.isNotBlank() || peerUserId.isNotBlank()) {
                                when (summary.callType) {
                                    CallType.Voice -> callViewModel.openVoiceCall(summary.participantName, threadId, peerUserId)
                                    CallType.Video -> callViewModel.openVideoCall(summary.participantName, threadId, peerUserId)
                                }
                                navController.replaceWith(callRoute(summary.callType), AppRoute.CallSummary)
                            }
                        },
                        onMessage = {
                            navController.replaceWith(AppRoute.Chat, AppRoute.CallSummary)
                        },
                    )
                }
            }

            composable(AppRoute.Settings.routeName()) {
                SettingsScreen(
                    isDarkMode = settings.darkMode,
                    onDarkModeToggle = { desired ->
                        if (desired != settings.darkMode) {
                            flowViewModel.toggleTheme()
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onLogout = {
                        scope.launch {
                            callViewModel.resetForLogout()
                            selectedChatThread = null
                            flowViewModel.logout()
                            navController.replaceAllWith(AppRoute.SignIn)
                        }
                    },
                )
            }

            composable(AppRoute.Premium.routeName()) {
                PremiumScreen()
            }
        }

        if (callState.isActive && callState.isMinimized) {
            FloatingCallWindow(
                uiState = callState,
                onExpand = {
                    callViewModel.expand()
                    navController.navigateTo(callRoute(callState.callType))
                },
                onAnswerCall = callViewModel::answerCall,
                onEndCall = callViewModel::hangUp,
                onToggleMic = callViewModel::toggleMic,
                onToggleVideo = callViewModel::toggleVideo,
            )
        }
    }
}

private fun callRoute(callType: CallType): AppRoute {
    return when (callType) {
        CallType.Voice -> AppRoute.VoiceCall
        CallType.Video -> AppRoute.VideoCall
    }
}

private fun chatScreenState(
    state: com.nova.app.core.state.NovaLoadState<com.nova.app.core.model.ChatUiState>,
    selectedChatThread: ChatThread?,
): com.nova.app.core.model.ChatUiState {
    val fallbackUser = com.nova.app.core.model.UserCard(
        id = selectedChatThread?.user?.id ?: "placeholder",
        name = selectedChatThread?.user?.name ?: "Chat",
        age = 0,
        photoUrl = "",
        online = state is com.nova.app.core.state.NovaLoadState.Success,
    )

    val fallback = com.nova.app.core.model.ChatUiState(
        thread = com.nova.app.core.model.ChatThread(
            id = "placeholder",
            user = fallbackUser,
            lastMessage = "",
            unreadCount = 0,
            online = fallbackUser.online,
        ),
        messages = emptyList(),
        typing = false,
        suggestions = emptyList(),
        translationEnabled = false,
        callHint = "Best time to call: 8:30 PM",
        loading = state is com.nova.app.core.state.NovaLoadState.Loading,
        loadingMore = false,
        hasMore = false,
        nextCursor = null,
    )

    return when (state) {
        is com.nova.app.core.state.NovaLoadState.Success -> {
            val loaded = state.data
            val selected = selectedChatThread
            when {
                selected == null -> loaded
                loaded.thread.id.isBlank() || loaded.thread.id == "placeholder" -> loaded.copy(thread = selected)
                loaded.thread.id != selected.id -> loaded.copy(thread = selected)
                else -> loaded
            }
        }
        is com.nova.app.core.state.NovaLoadState.Loading -> fallback
        is com.nova.app.core.state.NovaLoadState.Empty -> fallback
        is com.nova.app.core.state.NovaLoadState.Error -> fallback
        is com.nova.app.core.state.NovaLoadState.Offline -> fallback
        is com.nova.app.core.state.NovaLoadState.PermissionRequired -> fallback
        is com.nova.app.core.state.NovaLoadState.FirstTimeUser -> fallback
        is com.nova.app.core.state.NovaLoadState.Premium -> fallback
    }
}

private fun messagesScreenState(
    state: com.nova.app.core.state.NovaLoadState<MessagesUiState>,
): MessagesUiState {
    val fallback = MessagesUiState(
        threads = emptyList(),
        onlineNow = 0,
        filters = emptyList(),
        searchHint = "Search people",
    )

    return when (state) {
        is com.nova.app.core.state.NovaLoadState.Success -> state.data
        is com.nova.app.core.state.NovaLoadState.Loading -> fallback
        is com.nova.app.core.state.NovaLoadState.Empty -> fallback
        is com.nova.app.core.state.NovaLoadState.Error -> fallback
        is com.nova.app.core.state.NovaLoadState.Offline -> fallback
        is com.nova.app.core.state.NovaLoadState.PermissionRequired -> fallback
        is com.nova.app.core.state.NovaLoadState.FirstTimeUser -> fallback
        is com.nova.app.core.state.NovaLoadState.Premium -> fallback
    }
}

private fun profileScreenState(
    state: com.nova.app.core.state.NovaLoadState<ProfileUiState>,
    settings: AppSettings,
): ProfileUiState {
    val fallback = ProfileUiState(
        user = UserCard(
            id = "me",
            name = "Your profile",
            age = 0,
            photoUrl = "",
            verified = false,
            online = false,
            city = "",
            vipTierId = "vip_0",
            vipTierName = "VIP 0",
            premium = false,
        ),
        bio = "Add a bio, interests, avatar, and featured photos.",
        featuredPhotos = emptyList(),
        interests = emptyList(),
        diamonds = 100,
        prompts = listOf("What makes you smile?", "A weekend I love looks like...", "My vibe in three words..."),
        badges = emptyList(),
        stats = emptyList(),
        settings = settings,
        wallet = emptyList(),
        safety = emptyList(),
        plans = emptyList(),
        notifications = emptyList(),
        genericScreens = emptyList(),
        adminMetrics = emptyList(),
        compatibility = emptyList(),
        filters = emptyList(),
    )

    return when (state) {
        is com.nova.app.core.state.NovaLoadState.Success -> state.data
        else -> fallback
    }
}

private enum class ProfileFlowMode {
    Setup,
    Edit,
}

private fun startCallFromNotification(
    callViewModel: CallViewModel,
    navController: NavController,
    payload: CallNotificationPayload,
    autoAnswer: Boolean,
) {
    val participantName = payload.participantName.ifBlank { "User" }
    when (payload.direction.uppercase()) {
        "INCOMING" -> when (payload.callType) {
            CallType.Voice -> callViewModel.startIncomingVoiceCall(
                participantName = participantName,
                threadId = payload.threadId,
                callId = payload.callId,
                peerUserId = payload.peerUserId,
            )
            CallType.Video -> callViewModel.startIncomingVideoCall(
                participantName = participantName,
                threadId = payload.threadId,
                callId = payload.callId,
                peerUserId = payload.peerUserId,
            )
        }
        else -> when (payload.callType) {
            CallType.Voice -> callViewModel.openVoiceCall(participantName, payload.threadId, payload.peerUserId)
            CallType.Video -> callViewModel.openVideoCall(participantName, payload.threadId, payload.peerUserId)
        }
    }
    navController.navigateTo(callRoute(payload.callType))
    if (autoAnswer && payload.direction.equals("INCOMING", ignoreCase = true)) {
        callViewModel.answerCall()
    }
}

private fun ChatNotificationPayload.toChatThread(): ChatThread {
    val displayName = participantName.ifBlank { "Chat" }
    val peerId = peerUserId.ifBlank { threadId.ifBlank { "thread" } }
    return ChatThread(
        id = threadId.ifBlank { "thread" },
        user = UserCard(
            id = peerId,
            name = displayName,
            age = 0,
            photoUrl = "",
            online = false,
        ),
        lastMessage = messagePreview,
        unreadCount = 0,
        online = false,
    )
}

private fun CallNotificationPayload.toChatThread(existing: ChatThread? = null): ChatThread {
    val displayName = participantName.ifBlank { existing?.user?.name ?: "Call" }
    val peerId = peerUserId.ifBlank { existing?.user?.id ?: threadId.ifBlank { "call" } }
    return ChatThread(
        id = threadId.ifBlank { existing?.id ?: "call" },
        user = UserCard(
            id = peerId,
            name = displayName,
            age = 0,
            photoUrl = "",
            online = existing?.online ?: false,
        ),
        lastMessage = body.ifBlank { existing?.lastMessage ?: "" },
        unreadCount = existing?.unreadCount ?: 0,
        online = existing?.online ?: false,
    )
}

private fun shareProfilePost(context: android.content.Context, post: BackendCommunityPost) {
    val link = "https://nova.app/community/post/${post.id}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${post.authorName}: ${post.text.take(120)}\n$link")
    }
    context.startActivity(Intent.createChooser(intent, "Share post"))
}
