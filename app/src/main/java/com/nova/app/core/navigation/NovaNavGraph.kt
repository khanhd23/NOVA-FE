package com.nova.app.core.navigation

import androidx.compose.ui.res.stringResource
import com.nova.app.R
import androidx.compose.ui.platform.LocalResources

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import com.nova.app.core.backend.ACTION_CALL_BACK
import com.nova.app.core.call.CallSystemRegistry
import com.nova.app.core.call.CallAudioState
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
import com.nova.app.core.i18n.findActivity
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
import com.nova.app.core.viewmodel.DiscoverViewModel
import com.nova.app.core.viewmodel.NotificationsViewModel
import com.nova.app.core.viewmodel.ProfileConnectionsViewModel
import com.nova.app.core.viewmodel.LaunchViewModel
import com.nova.app.core.viewmodel.ProfileViewModel
import com.nova.app.core.viewmodel.SearchViewModel
import com.nova.app.feature.auth.LoginScreen
import com.nova.app.feature.auth.SignInProvider
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.nova.app.feature.call.CallSummaryScreen
import com.nova.app.feature.call.FloatingCallWindow
import com.nova.app.feature.call.VideoCallScreen
import com.nova.app.feature.call.VoiceCallScreen
import com.nova.app.feature.chat.ChatDetailScreen
import com.nova.app.feature.chat.ChatSearchScreen
import com.nova.app.feature.community.CommunitySearchScreen
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
    val discoverViewModel: DiscoverViewModel = viewModel(factory = container.viewModelFactory)
    val profileViewModel: ProfileViewModel = viewModel(factory = container.viewModelFactory)
    val profileConnectionsViewModel: ProfileConnectionsViewModel = viewModel(factory = container.viewModelFactory)
    val searchViewModel: SearchViewModel = viewModel(factory = container.viewModelFactory)
    val notificationsViewModel: NotificationsViewModel = viewModel(factory = container.viewModelFactory)
    val launchState by launchViewModel.uiState.collectAsStateWithLifecycle()
    val callState by callViewModel.uiState.collectAsStateWithLifecycle()
    val callSummary by callViewModel.lastSummary.collectAsStateWithLifecycle()
    val callSystem = CallSystemRegistry.system
    val callAudioState by (callSystem?.audio?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(CallAudioState()) })
        .collectAsStateWithLifecycle()
    val isPictureInPicture by (callSystem?.pictureInPicture ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) })
        .collectAsStateWithLifecycle()
    val messagesState by messagesViewModel.uiState.collectAsStateWithLifecycle()
    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val discoverState by discoverViewModel.uiState.collectAsStateWithLifecycle()
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
        if (!navController.popBackStack(AppRoute.Chat.routeName(), false)) {
            navController.navigateTo(AppRoute.Chat)
        }
    }

    fun deleteThreadForMe(thread: ChatThread) {
        messagesViewModel.deleteThreadForMe(thread.id) { deleted ->
            if (!deleted) {
                android.widget.Toast.makeText(context, context.getString(R.string.chat_delete_failed), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openDiscoverChat(candidate: com.nova.app.core.model.DiscoveryCandidate) {
        val user = candidate.user
        openChatThread(
            threadId = "dm-${user.id}",
            peerUserId = user.id,
            participantName = user.name,
            photoUrl = user.photoUrl,
            verified = user.verified,
            online = user.online,
            city = user.city,
            vipTierId = user.vipTierId,
            vipTierName = user.vipTierName,
            premium = user.premium,
        )
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
        val target = if (!session.profileComplete) AppRoute.ProfileSetup else AppRoute.Home
        if (target == AppRoute.ProfileSetup) {
            profileFlowMode = ProfileFlowMode.Setup
        }
        navController.replaceWith(target, AppRoute.SignIn)
    }

    LaunchedEffect(callState.callId, callState.status, callState.isActive) {
        val incomingRinging = callState.isActive && callState.isRinging &&
            callState.direction == com.nova.app.core.model.CallDirection.Incoming && !callState.isMinimized
        if (incomingRinging) {
            val route = callRoute(callState.callType)
            if (navController.currentDestination?.route != route.routeName()) {
                navController.navigateTo(route)
            }
        }
    }

    LaunchedEffect(callViewModel) {
        com.nova.app.core.call.CallActions.expandRequests.collect {
            val current = callViewModel.uiState.value
            if (current.isActive) {
                callViewModel.expand()
                val route = callRoute(current.callType)
                if (navController.currentDestination?.route != route.routeName()) {
                    navController.navigateTo(route)
                }
            }
        }
    }

    // Refresh token rejected (expired or revoked): clean up like a logout and ask to sign in again.
    LaunchedEffect(container.backendRuntime) {
        container.backendRuntime.sessionExpired.collect {
            callViewModel.resetForLogout()
            selectedChatThread = null
            flowViewModel.logout()
            android.widget.Toast.makeText(context, context.getString(R.string.session_expired), android.widget.Toast.LENGTH_LONG).show()
            navController.replaceAllWith(AppRoute.SignIn)
        }
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
            ACTION_CALL_BACK -> {
                intent.toCallNotificationPayload()?.let { payload ->
                    selectedChatThread = payload.toChatThread(selectedChatThread)
                    if (!callViewModel.uiState.value.isActive) {
                        // The call route starts an outgoing call to the selected thread.
                        navController.navigateTo(callRoute(payload.callType))
                    }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                var signInProvider by remember { mutableStateOf<SignInProvider?>(null) }
                var signInError by remember { mutableStateOf<String?>(null) }
                val res = LocalResources.current

                fun onSignInFailed(throwable: Throwable) {
                    Log.w("NovaNav", "Sign-in failed", throwable)
                    signInProvider = null
                    signInError = res.getString(R.string.login_error_generic)
                }

                LoginScreen(
                    loadingProvider = signInProvider,
                    errorMessage = signInError,
                    onGoogleLogin = {
                        if (signInProvider != null) return@LoginScreen
                        signInProvider = SignInProvider.Google
                        signInError = null
                        scope.launch {
                            runCatching { googleIdentityClient.getIdToken(context.findActivity() ?: context) }
                                .onSuccess { idToken ->
                                    flowViewModel.signInWithGoogle(
                                        idToken = idToken,
                                        onSuccess = ::navigateAfterSignIn,
                                        onError = ::onSignInFailed,
                                    )
                                }
                                .onFailure { throwable ->
                                    Log.w("NovaNav", "Google credential retrieval failed", throwable)
                                    signInProvider = null
                                    signInError = when (throwable) {
                                        is GetCredentialCancellationException -> null
                                        is NoCredentialException -> res.getString(R.string.login_error_no_google)
                                        else -> res.getString(R.string.login_error_google_unavailable)
                                    }
                                }
                        }
                    },
                    onFacebookLogin = {
                        if (signInProvider != null) return@LoginScreen
                        signInProvider = SignInProvider.Facebook
                        signInError = null
                        flowViewModel.signInWithFacebook(
                            onSuccess = ::navigateAfterSignIn,
                            onError = ::onSignInFailed,
                        )
                    },
                )
            }

            composable(AppRoute.ProfileSetup.routeName()) {
                ProfileSetupScreen(
                    profileUiState = profileUiState,
                    isEditing = profileFlowMode == ProfileFlowMode.Edit,
                    profileViewModel = profileViewModel,
                    onBack = {
                        if (profileFlowMode == ProfileFlowMode.Edit) {
                            navController.popBackStack()
                        } else {
                            scope.launch {
                                flowViewModel.logout()
                                navController.replaceWith(AppRoute.SignIn, AppRoute.ProfileSetup)
                            }
                        }
                    },
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
                    discoverState = discoverState,
                    discoverViewModel = discoverViewModel,
                    communityState = communityState,
                    communityViewModel = communityViewModel,
                    profileState = profileUiState,
                    notificationCount = notificationsState.unreadCount,
                    onChatClick = {
                        selectedChatThread = it
                        navController.navigateTo(AppRoute.Chat)
                    },
                    onSearchClick = {
                        navController.navigateTo(AppRoute.ChatSearch)
                    },
                    onChatTabSeen = messagesViewModel::markVisibleThreadsSeen,
                    onDeleteThread = ::deleteThreadForMe,
                    onCommunitySearchClick = {
                        navController.navigateTo(AppRoute.CommunitySearch)
                    },
                    onNotificationClick = {
                        notificationsViewModel.markAllSeenLocal()
                        navController.navigateTo(AppRoute.Notifications)
                    },
                    onSettingsClick = { navController.navigateTo(AppRoute.Settings) },
                    onEditProfile = {
                        profileFlowMode = ProfileFlowMode.Edit
                        navController.navigateTo(AppRoute.ProfileSetup)
                    },
                    onOpenProfile = { userId -> navController.navigateToProfileDetail(userId) },
                    onDiscoverLike = ::openDiscoverChat,
                    onOpenConnections = { tab -> navController.navigateToProfileConnections(profileUiState.user.id, tab) },
                    onProfilePostLike = profileViewModel::likePost,
                    onProfilePostComment = profileViewModel::commentPost,
                    onProfilePostShare = profileViewModel::sharePost,
                    onPostPublished = profileViewModel::refreshProfile,
                )
            }

            composable(AppRoute.Messages.routeName()) {
                HomeShell(
                    messagesState = messagesUiState,
                    discoverState = discoverState,
                    discoverViewModel = discoverViewModel,
                    communityState = communityState,
                    communityViewModel = communityViewModel,
                    profileState = profileUiState,
                    notificationCount = notificationsState.unreadCount,
                    onChatClick = {
                        selectedChatThread = it
                        navController.navigateTo(AppRoute.Chat)
                    },
                    onSearchClick = {
                        navController.navigateTo(AppRoute.ChatSearch)
                    },
                    onChatTabSeen = messagesViewModel::markVisibleThreadsSeen,
                    onDeleteThread = ::deleteThreadForMe,
                    onCommunitySearchClick = {
                        navController.navigateTo(AppRoute.CommunitySearch)
                    },
                    onNotificationClick = {
                        notificationsViewModel.markAllSeenLocal()
                        navController.navigateTo(AppRoute.Notifications)
                    },
                    onSettingsClick = { navController.navigateTo(AppRoute.Settings) },
                    onEditProfile = {
                        profileFlowMode = ProfileFlowMode.Edit
                        navController.navigateTo(AppRoute.ProfileSetup)
                    },
                    onOpenProfile = { userId -> navController.navigateToProfileDetail(userId) },
                    onDiscoverLike = ::openDiscoverChat,
                    onOpenConnections = { tab -> navController.navigateToProfileConnections(profileUiState.user.id, tab) },
                    onProfilePostLike = profileViewModel::likePost,
                    onProfilePostComment = profileViewModel::commentPost,
                    onProfilePostShare = profileViewModel::sharePost,
                    onPostPublished = profileViewModel::refreshProfile,
                    initialTab = 3,
                )
            }

            composable(AppRoute.Community.routeName()) {
                HomeShell(
                    messagesState = messagesUiState,
                    discoverState = discoverState,
                    discoverViewModel = discoverViewModel,
                    communityState = communityState,
                    communityViewModel = communityViewModel,
                    profileState = profileUiState,
                    notificationCount = notificationsState.unreadCount,
                    onChatClick = {
                        selectedChatThread = it
                        navController.navigateTo(AppRoute.Chat)
                    },
                    onSearchClick = {
                        navController.navigateTo(AppRoute.ChatSearch)
                    },
                    onChatTabSeen = messagesViewModel::markVisibleThreadsSeen,
                    onDeleteThread = ::deleteThreadForMe,
                    onCommunitySearchClick = {
                        navController.navigateTo(AppRoute.CommunitySearch)
                    },
                    onNotificationClick = {
                        notificationsViewModel.markAllSeenLocal()
                        navController.navigateTo(AppRoute.Notifications)
                    },
                    onSettingsClick = { navController.navigateTo(AppRoute.Settings) },
                    onEditProfile = {
                        profileFlowMode = ProfileFlowMode.Edit
                        navController.navigateTo(AppRoute.ProfileSetup)
                    },
                    onOpenProfile = { userId -> navController.navigateToProfileDetail(userId) },
                    onDiscoverLike = ::openDiscoverChat,
                    onOpenConnections = { tab -> navController.navigateToProfileConnections(profileUiState.user.id, tab) },
                    onProfilePostLike = profileViewModel::likePost,
                    onProfilePostComment = profileViewModel::commentPost,
                    onProfilePostShare = profileViewModel::sharePost,
                    onPostPublished = profileViewModel::refreshProfile,
                    initialTab = 1,
                )
            }

            composable(AppRoute.CommunitySearch.routeName()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    CommunitySearchScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { userId -> navController.navigateToProfileDetail(userId) },
                        onOpenMedia = { mediaUrls, startIndex ->
                            if (mediaUrls.isNotEmpty()) {
                                previewMedia = PreviewMediaState(urls = mediaUrls, startIndex = startIndex)
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

            composable(AppRoute.ChatSearch.routeName()) {
                ChatSearchScreen(
                    messagesState = messagesUiState,
                    onBack = { navController.popBackStack() },
                    onOpenChat = { thread ->
                        selectedChatThread = thread
                        navController.navigateTo(AppRoute.Chat)
                    },
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
                    error = if (fetchedProfile == null) context.getString(R.string.profile_load_failed) else null
                    loading = false
                }

                LaunchedEffect(userId) {
                    if (userId.isBlank()) {
                        profile = null
                        posts = emptyList()
                        loading = false
                        error = context.getString(R.string.profile_invalid)
                        return@LaunchedEffect
                    }
                    loadProfileDetail()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
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
                        onOpenProfile = { mentionedUserId ->
                            navController.navigateToProfileDetail(mentionedUserId)
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
                val activeThread = chatUiState.thread
                LaunchedEffect(activeThread.id) {
                    if (activeThread.id.isNotBlank()) {
                        chatViewModel.openThread(activeThread)
                    }
                }
                // While this conversation is on screen its messages are read and not notified.
                androidx.lifecycle.compose.LifecycleResumeEffect(activeThread.id) {
                    com.nova.app.core.backend.ActiveChat.threadId = activeThread.id.takeIf { it.isNotBlank() }
                    com.nova.app.core.backend.MessageNotifier.cancel(context, activeThread.id)
                    onPauseOrDispose {
                        if (com.nova.app.core.backend.ActiveChat.threadId == activeThread.id) {
                            com.nova.app.core.backend.ActiveChat.threadId = null
                        }
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
                    onRetryMessage = { messageId -> chatViewModel.retryMessage(messageId) },
                    onTypingChanged = { typing -> chatViewModel.setTyping(typing) },
                    onLoadMore = { chatViewModel.loadMore() },
                    onDeleteThreadForMe = {
                        chatViewModel.deleteCurrentThreadForMe()
                        navController.popBackStack()
                    },
                    onDeleteMessageForMe = { messageId -> chatViewModel.deleteMessageForMe(messageId) },
                    onRecallMessage = { messageId -> chatViewModel.recallMessage(messageId) },
                    onEditMessage = { messageId, text -> chatViewModel.editMessage(messageId, text) },
                )
            }

            composable(AppRoute.VoiceCall.routeName()) {
                val participantName = selectedChatThread?.user?.name ?: callState.participantName.ifBlank { "User" }
                LaunchedEffect(participantName) {
                    if (callState.isActive) {
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
                    audioState = callAudioState,
                    onToggleAudioRoute = { callSystem?.audio?.cycleRoute() },
                    onBack = {
                        // Back never ends a call: shrink it to the floating window and return to the app.
                        callViewModel.minimize()
                        if (!navController.popBackStack()) {
                            navController.navigateTo(AppRoute.Home)
                        }
                    },
                    onAnswerCall = callViewModel::answerCall,
                    onEndCall = callViewModel::hangUp,
                    onToggleMic = callViewModel::toggleMic,
                )
            }

            composable(AppRoute.VideoCall.routeName()) {
                val participantName = selectedChatThread?.user?.name ?: callState.participantName.ifBlank { "User" }
                LaunchedEffect(participantName) {
                    if (callState.isActive) {
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
                    audioState = callAudioState,
                    isPictureInPicture = isPictureInPicture,
                    onToggleAudioRoute = { callSystem?.audio?.cycleRoute() },
                    selfAvatarUrl = profileUiState.user.photoUrl,
                    onBack = {
                        // Back never ends a call: shrink it to the floating window and return to the app.
                        callViewModel.minimize()
                        if (!navController.popBackStack()) {
                            navController.navigateTo(AppRoute.Home)
                        }
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
                selected.id.startsWith("dm-") && loaded.thread.user.id == selected.user.id -> loaded
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
        bio = "",
        featuredPhotos = emptyList(),
        interests = emptyList(),
        diamonds = 0,
        prompts = emptyList(),
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
    if (payload.direction.equals("INCOMING", ignoreCase = true)) {
        val accepted = callViewModel.receiveIncomingCall(
            participantName = payload.participantName.ifBlank { "User" },
            threadId = payload.threadId,
            callId = payload.callId,
            peerUserId = payload.peerUserId,
            callType = payload.callType,
        )
        if (!accepted) return
        navController.navigateTo(callRoute(payload.callType))
        if (autoAnswer) {
            callViewModel.answerCall()
        }
        return
    }
    // Ongoing-call notification: return to the call if it is still alive, otherwise open the chat.
    // Never start a new call from here.
    val current = callViewModel.uiState.value
    if (current.isActive && (current.callId == payload.callId || payload.callId.isBlank())) {
        callViewModel.expand()
        navController.navigateTo(callRoute(current.callType))
    } else {
        navController.navigateTo(AppRoute.Chat)
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



