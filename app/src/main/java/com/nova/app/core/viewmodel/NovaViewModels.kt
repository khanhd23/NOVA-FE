package com.nova.app.core.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.app.core.domain.CompleteAuthUseCase
import com.nova.app.core.domain.CompleteOnboardingUseCase
import com.nova.app.core.domain.CompleteProfileUseCase
import com.nova.app.core.domain.JoinEventUseCase
import com.nova.app.core.domain.LikeCandidateUseCase
import com.nova.app.core.domain.LogoutUseCase
import com.nova.app.core.domain.ObserveChatUseCase
import com.nova.app.core.domain.ObserveCommunityUseCase
import com.nova.app.core.domain.ObserveDiscoverUseCase
import com.nova.app.core.domain.ObserveHomeUseCase
import com.nova.app.core.domain.ObserveMessagesUseCase
import com.nova.app.core.domain.ObserveProfileUseCase
import com.nova.app.core.domain.ObserveSessionUseCase
import com.nova.app.core.domain.ObserveSettingsUseCase
import com.nova.app.core.domain.RefreshProfileUseCase
import com.nova.app.core.domain.SaveCandidateUseCase
import com.nova.app.core.domain.UpdateProfileUseCase
import com.nova.app.core.domain.SendMessageUseCase
import com.nova.app.core.domain.SkipCandidateUseCase
import com.nova.app.core.domain.SuperLikeCandidateUseCase
import com.nova.app.core.domain.ToggleIncognitoUseCase
import com.nova.app.core.domain.TogglePremiumUseCase
import com.nova.app.core.domain.ToggleThemeUseCase
import com.nova.app.core.domain.ToggleTopicUseCase
import com.nova.app.core.domain.ToggleTravelModeUseCase
import com.nova.app.core.domain.UpdateLanguageUseCase
import com.nova.app.core.backend.BackendAuthProvider
import com.nova.app.core.backend.BackendNotification
import com.nova.app.core.backend.BackendRuntime
import com.nova.app.core.backend.BackendProfile
import com.nova.app.core.backend.BackendProfilePage
import com.nova.app.core.backend.BackendSession
import com.nova.app.core.backend.BackendProfileUpdateRequest
import com.nova.app.core.model.ChatAttachmentDraft
import com.nova.app.core.model.AppSettings
import com.nova.app.core.model.CreatePostDraft
import com.nova.app.core.model.ChatThread
import com.nova.app.core.model.ChatUiState
import com.nova.app.core.model.CommunityUiState
import com.nova.app.core.model.DiscoverUiState
import com.nova.app.core.model.HomeUiState
import com.nova.app.core.model.LaunchUiState
import com.nova.app.core.model.MessagesUiState
import com.nova.app.core.model.NotificationsUiState
import com.nova.app.core.model.ProfileUiState
import com.nova.app.core.model.ProfileConnectionItem
import com.nova.app.core.model.ProfileConnectionsUiState
import com.nova.app.core.model.SearchResultItem
import com.nova.app.core.model.SearchUiState
import com.nova.app.core.model.SessionState
import com.nova.app.core.navigation.AppRoute
import com.nova.app.core.state.NovaLoadState
import com.nova.app.core.data.NovaRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.FlowPreview

class LaunchViewModel(
    observeSession: ObserveSessionUseCase,
) : ViewModel() {
    val uiState: StateFlow<LaunchUiState> = observeSession()
        .map { session -> LaunchUiState(target = resolveTarget(session)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LaunchUiState(AppRoute.Onboarding))

    private fun resolveTarget(session: SessionState): AppRoute {
        return when {
            session.isFirstLaunch || !session.onboardingCompleted -> AppRoute.Onboarding
            !session.otpVerified -> AppRoute.SignIn
            !session.profileCompleted -> AppRoute.ProfileSetup
            else -> AppRoute.Home
        }
    }
}

class FlowViewModel(
    observeSession: ObserveSessionUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    private val completeAuthUseCase: CompleteAuthUseCase,
    private val completeProfileUseCase: CompleteProfileUseCase,
    private val toggleThemeUseCase: ToggleThemeUseCase,
    private val togglePremiumUseCase: TogglePremiumUseCase,
    private val updateLanguageUseCase: UpdateLanguageUseCase,
    private val toggleIncognitoUseCase: ToggleIncognitoUseCase,
    private val toggleTravelModeUseCase: ToggleTravelModeUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val backendRuntime: BackendRuntime,
) : ViewModel() {
    val session: StateFlow<SessionState> = observeSession()
    val settings: StateFlow<AppSettings> = observeSettings()

    fun completeOnboarding() = viewModelScope.launch { completeOnboardingUseCase() }
    fun completeAuth() = viewModelScope.launch { completeAuthUseCase() }
    fun signInWithGoogle(
        idToken: String,
        onSuccess: (BackendSession) -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) = signIn(BackendAuthProvider.Google, idToken, onSuccess, onError)

    fun signInWithFacebook(
        onSuccess: (BackendSession) -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) = signIn(BackendAuthProvider.Facebook, null, onSuccess, onError)
    fun completeProfile() = viewModelScope.launch { completeProfileUseCase() }
    fun toggleTheme() = viewModelScope.launch { toggleThemeUseCase() }
    fun togglePremium() = viewModelScope.launch { togglePremiumUseCase() }
    fun updateLanguage(language: String) = viewModelScope.launch { updateLanguageUseCase(language) }
    fun toggleIncognito() = viewModelScope.launch { toggleIncognitoUseCase() }
    fun toggleTravelMode() = viewModelScope.launch { toggleTravelModeUseCase() }
    suspend fun logout() = logoutUseCase()

    private fun signIn(
        provider: BackendAuthProvider,
        providerToken: String?,
        onSuccess: (BackendSession) -> Unit,
        onError: (Throwable) -> Unit,
    ) = viewModelScope.launch {
        val session = runCatching {
            backendRuntime.signIn(provider, providerToken)
        }.getOrElse { throwable ->
            onError(throwable)
            return@launch
        }
        if (session.onboardingComplete) {
            completeOnboardingUseCase()
        }
        if (session.profileComplete) {
            completeProfileUseCase()
        }
        completeAuthUseCase()
        onSuccess(session)
    }
}

class HomeViewModel(
    observeHome: ObserveHomeUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<NovaLoadState<HomeUiState>>(NovaLoadState.Loading)
    val uiState: StateFlow<NovaLoadState<HomeUiState>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            delay(260)
            observeHome().collect { state ->
                _uiState.value = NovaLoadState.Success(state)
            }
        }
    }
}

class DiscoverViewModel(
    observeDiscover: ObserveDiscoverUseCase,
    private val likeCandidateUseCase: LikeCandidateUseCase,
    private val superLikeCandidateUseCase: SuperLikeCandidateUseCase,
    private val skipCandidateUseCase: SkipCandidateUseCase,
    private val saveCandidateUseCase: SaveCandidateUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<NovaLoadState<DiscoverUiState>>(NovaLoadState.Loading)
    val uiState: StateFlow<NovaLoadState<DiscoverUiState>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            delay(260)
            observeDiscover().collect { state ->
                _uiState.value = NovaLoadState.Success(state)
            }
        }
    }

    fun like() = viewModelScope.launch { likeCandidateUseCase() }
    fun superLike() = viewModelScope.launch { superLikeCandidateUseCase() }
    fun skip() = viewModelScope.launch { skipCandidateUseCase() }
    fun save() = viewModelScope.launch { saveCandidateUseCase() }
}

class MessagesViewModel(
    observeMessages: ObserveMessagesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<NovaLoadState<MessagesUiState>>(NovaLoadState.Loading)
    val uiState: StateFlow<NovaLoadState<MessagesUiState>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            delay(180)
            observeMessages().collect { state ->
                _uiState.value = NovaLoadState.Success(state)
            }
        }
    }
}

class ChatViewModel(
    observeChat: ObserveChatUseCase,
    private val sendMessage: SendMessageUseCase,
    private val repository: NovaRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<NovaLoadState<ChatUiState>>(NovaLoadState.Loading)
    val uiState: StateFlow<NovaLoadState<ChatUiState>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            delay(220)
            observeChat().collect { state ->
                _uiState.value = NovaLoadState.Success(state)
            }
        }
    }

    fun send(text: String, attachment: ChatAttachmentDraft? = null) = viewModelScope.launch {
        if (text.isBlank() && attachment == null) return@launch
        sendMessage(text.trim(), attachment)
    }

    fun openThread(thread: ChatThread) = viewModelScope.launch {
        repository.openChatThread(thread)
    }

    fun loadMore() = viewModelScope.launch {
        repository.loadMoreChatMessages()
    }
}

class CommunityViewModel(
    observeCommunity: ObserveCommunityUseCase,
    private val repository: NovaRepository,
    private val toggleTopicUseCase: ToggleTopicUseCase,
    private val joinEventUseCase: JoinEventUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<NovaLoadState<CommunityUiState>>(NovaLoadState.Loading)
    val uiState: StateFlow<NovaLoadState<CommunityUiState>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            delay(220)
            try {
                repository.refreshCommunity()
            } catch (_: Throwable) {
                // fall back to local seed state
            }
            observeCommunity().collect { state ->
                _uiState.value = NovaLoadState.Success(state)
            }
        }
    }

    fun toggleTopic(topicId: String) = viewModelScope.launch { toggleTopicUseCase(topicId) }
    fun joinEvent(eventId: String) = viewModelScope.launch { joinEventUseCase(eventId) }
    fun refresh(tab: String = "for_you") = viewModelScope.launch { repository.refreshCommunity(tab, refresh = true) }
    fun selectTab(tab: String) = viewModelScope.launch { repository.refreshCommunity(tab, refresh = false) }
    fun publishPost(draft: CreatePostDraft) = viewModelScope.launch { repository.createCommunityPost(draft) }
    fun likePost(postId: String, liked: Boolean) = viewModelScope.launch { repository.likeCommunityPost(postId, liked) }
    fun commentPost(postId: String, text: String) = viewModelScope.launch { repository.commentCommunityPost(postId, text) }
    fun sharePost(postId: String, target: String = "profile", recipientUserId: String? = null, copyLink: Boolean = true) =
        viewModelScope.launch { repository.shareCommunityPost(postId, target, recipientUserId, copyLink) }
}

class ProfileViewModel(
    observeProfile: ObserveProfileUseCase,
    private val repository: NovaRepository,
    private val refreshProfileUseCase: RefreshProfileUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val toggleThemeUseCase: ToggleThemeUseCase,
    private val togglePremiumUseCase: TogglePremiumUseCase,
    private val toggleIncognitoUseCase: ToggleIncognitoUseCase,
    private val toggleTravelModeUseCase: ToggleTravelModeUseCase,
    private val updateLanguageUseCase: UpdateLanguageUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<NovaLoadState<ProfileUiState>>(NovaLoadState.Loading)
    val uiState: StateFlow<NovaLoadState<ProfileUiState>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { refreshProfileUseCase() }
            delay(220)
            observeProfile().collect { state ->
                _uiState.value = NovaLoadState.Success(state)
            }
        }
    }

    fun toggleTheme() = viewModelScope.launch { toggleThemeUseCase() }
    fun togglePremium() = viewModelScope.launch { togglePremiumUseCase() }
    fun toggleIncognito() = viewModelScope.launch { toggleIncognitoUseCase() }
    fun toggleTravelMode() = viewModelScope.launch { toggleTravelModeUseCase() }
    fun setLanguage(language: String) = viewModelScope.launch { updateLanguageUseCase(language) }
    fun likePost(postId: String, liked: Boolean) = viewModelScope.launch { repository.likeCommunityPost(postId, liked) }
    fun commentPost(postId: String, text: String) = viewModelScope.launch { repository.commentCommunityPost(postId, text) }
    fun sharePost(postId: String) = viewModelScope.launch { repository.shareCommunityPost(postId) }

    suspend fun saveProfile(
        displayName: String,
        bio: String,
        avatarUrl: String?,
        featuredPhotos: List<String>,
        interests: List<String>,
    ) {
        updateProfileUseCase(
            BackendProfileUpdateRequest(
                displayName = displayName,
                bio = bio,
                photoUrl = avatarUrl,
                featuredPhotos = featuredPhotos,
                interests = interests,
            )
        )
    }
}

class ProfileConnectionsViewModel(
    private val backendRuntime: BackendRuntime,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileConnectionsUiState(loading = true))
    val uiState: StateFlow<ProfileConnectionsUiState> = _uiState.asStateFlow()

    fun load(userId: String, initialTab: String? = null, refreshProfile: Boolean = true) {
        viewModelScope.launch {
            val current = _uiState.value
            val resolvedTab = normalizeTab(initialTab ?: current.selectedTab, current.isSelf)
            _uiState.value = current.copy(
                userId = userId,
                selectedTab = resolvedTab,
                loading = true,
                loadingMore = false,
                error = null,
                items = if (refreshProfile || current.userId != userId) emptyList() else current.items,
                page = if (refreshProfile || current.userId != userId) 0 else current.page,
                total = if (refreshProfile || current.userId != userId) 0 else current.total,
                hasMore = false,
                profileLoaded = false,
            )
            val profile = runCatching { backendRuntime.fetchPublicProfile(userId) }.getOrNull()
            val isSelf = backendRuntime.currentSession()?.userId == userId
            val tabs = relationTabs(isSelf)
            val tab = normalizeTab(resolvedTab, isSelf, tabs)
            if (profile == null) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "Unable to load profile",
                    tabs = tabs,
                    isSelf = isSelf,
                )
                return@launch
            }
            val relations = loadRelations(userId, tab, page = 0)
            _uiState.value = _uiState.value.copy(
                userName = profile.displayName,
                avatarUrl = profile.avatarUrl,
                bio = profile.bio,
                city = profile.city,
                verified = profile.verified,
                online = profile.online,
                premium = profile.premium,
                vipTierId = profile.vipTierId,
                vipTierName = profile.vipTierName,
                followersCount = profile.followersCount,
                followingCount = profile.followingCount,
                friendsCount = profile.friendsCount,
                interests = profile.interests,
                isSelf = isSelf,
                tabs = tabs,
                selectedTab = tab,
                items = relations.items,
                page = relations.page,
                size = relations.size,
                total = relations.total,
                hasMore = relations.hasMore,
                loading = false,
                loadingMore = false,
                error = null,
                profileLoaded = true,
            )
        }
    }

    fun refresh() {
        val current = _uiState.value
        if (current.userId.isBlank()) {
            return
        }
        load(current.userId, current.selectedTab, refreshProfile = true)
    }

    fun selectTab(tab: String) {
        val current = _uiState.value
        if (current.userId.isBlank()) {
            return
        }
        viewModelScope.launch {
            val resolvedTab = normalizeTab(tab, current.isSelf, current.tabs)
            _uiState.value = current.copy(selectedTab = resolvedTab, loading = true, loadingMore = false, error = null)
            val relations = loadRelations(current.userId, resolvedTab, page = 0)
            _uiState.value = _uiState.value.copy(
                items = relations.items,
                page = relations.page,
                size = relations.size,
                total = relations.total,
                hasMore = relations.hasMore,
                loading = false,
                loadingMore = false,
                error = null,
            )
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.userId.isBlank() || current.loadingMore || !current.hasMore) {
            return
        }
        viewModelScope.launch {
            _uiState.value = current.copy(loadingMore = true, error = null)
            val nextPage = current.page + 1
            val relations = loadRelations(current.userId, current.selectedTab, page = nextPage)
            _uiState.value = _uiState.value.copy(
                items = current.items + relations.items,
                page = relations.page,
                size = relations.size,
                total = relations.total,
                hasMore = relations.hasMore,
                loading = false,
                loadingMore = false,
                error = null,
            )
        }
    }

    private suspend fun loadRelations(userId: String, relation: String, page: Int): ProfileConnectionsUiState {
        val pageData = runCatching {
            backendRuntime.fetchProfileRelations(userId, relation, page = page, size = 50)
        }.getOrNull()
        if (pageData == null) {
            return _uiState.value.copy(
                items = emptyList(),
                page = page,
                size = 50,
                total = 0,
                hasMore = false,
            )
        }
        val items = pageData.items.map { item ->
            ProfileConnectionItem(
                id = item.userId,
                name = item.displayName,
                username = item.username,
                avatarUrl = item.avatarUrl,
                gender = item.gender,
                interests = item.interests,
                vipTierId = item.vipTierId,
                vipTierName = item.vipTierName,
                premium = item.premium,
                verified = item.verified,
                city = item.city,
                followersCount = item.followersCount,
                followingCount = item.followingCount,
                friendsCount = item.friendsCount,
                followedByMe = item.followedByMe,
                followedByThem = item.followedByThem,
                friend = item.friend,
            )
        }
        val hasMore = (pageData.page + 1L) * pageData.size < pageData.total
        return _uiState.value.copy(
            items = items,
            page = pageData.page,
            size = pageData.size,
            total = pageData.total,
            hasMore = hasMore,
        )
    }

    private fun normalizeTab(tab: String, isSelf: Boolean, tabs: List<String> = relationTabs(isSelf)): String {
        val normalized = tab.trim().lowercase()
        return when {
            tabs.contains(normalized) -> normalized
            tabs.isNotEmpty() -> tabs.first()
            else -> "followers"
        }
    }

    private fun relationTabs(isSelf: Boolean): List<String> {
        return if (isSelf) {
            listOf("followers", "friends", "following")
        } else {
            listOf("followers", "friends", "following", "mutual")
        }
    }
}

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val backendRuntime: BackendRuntime,
) : ViewModel() {
    private val queryState = MutableStateFlow("")
    private val genderState = MutableStateFlow("All")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                queryState.debounce(300L),
                genderState,
            ) { query, gender -> query to gender }
                .collectLatest { (query, gender) ->
                    performSearch(query, gender, page = 0, append = false)
                }
        }
    }

    fun updateQuery(query: String) {
        queryState.value = query
        if (query.isBlank()) {
            viewModelScope.launch { performSearch(query, genderState.value, page = 0, append = false) }
        }
    }

    fun setGender(gender: String) {
        genderState.value = gender
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || !state.hasMore) return
        viewModelScope.launch {
            performSearch(
                query = state.query,
                gender = state.selectedGender,
                page = state.page + 1,
                append = true,
            )
        }
    }

    fun retry() {
        viewModelScope.launch {
            performSearch(
                query = queryState.value,
                gender = genderState.value,
                page = 0,
                append = false,
            )
        }
    }

    private suspend fun performSearch(query: String, gender: String, page: Int, append: Boolean) {
        val backendGender = gender.takeUnless { it.equals("All", ignoreCase = true) || it.equals("Both", ignoreCase = true) }
        _uiState.value = _uiState.value.copy(
            query = query,
            selectedGender = gender,
            loading = true,
            error = null,
        )

        val pageData = backendRuntime.searchUsers(
            query = query,
            page = page,
            size = _uiState.value.size,
            gender = backendGender,
        )

        if (pageData == null) {
            _uiState.value = _uiState.value.copy(loading = false, error = "Unable to load users")
            return
        }

        val mapped = pageData.items.map { item ->
            SearchResultItem(
                id = item.userId,
                publicId = item.publicId,
                name = item.displayName,
                gender = item.gender,
                interests = item.interests,
                avatarUrl = item.avatarUrl,
                vipTierId = item.vipTierId,
                vipTierName = item.vipTierName,
                premium = item.premium,
                verified = item.verified,
                city = item.city,
                distanceKm = item.distanceKm,
                online = item.online,
            )
        }

        val combined = if (append) _uiState.value.results + mapped else mapped
        _uiState.value = _uiState.value.copy(
            loading = false,
            results = combined,
            page = pageData.page,
            total = pageData.total,
            hasMore = combined.size.toLong() < pageData.total,
        )
    }
}

class NotificationsViewModel(
    private val backendRuntime: BackendRuntime,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState(loading = true))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val items = backendRuntime.fetchNotifications()
            if (items == null) {
                _uiState.value = _uiState.value.copy(loading = false)
                return@launch
            }
            _uiState.value = NotificationsUiState(
                items = items.map { it.toNotificationItem() },
                unreadCount = items.count { !it.read },
                loading = false,
            )
        }
    }

    fun markRead(notificationId: String) {
        viewModelScope.launch {
            val items = backendRuntime.markNotificationRead(notificationId)
            if (items != null) {
                _uiState.value = NotificationsUiState(
                    items = items.map { it.toNotificationItem() },
                    unreadCount = items.count { !it.read },
                    loading = false,
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    items = _uiState.value.items.map { if (it.id == notificationId) it.copy(unread = false) else it },
                    unreadCount = _uiState.value.items.count { it.unread && it.id != notificationId },
                    loading = false,
                )
            }
        }
    }

    private fun BackendNotification.toNotificationItem(): com.nova.app.core.model.NotificationItem {
        return com.nova.app.core.model.NotificationItem(
            id = id,
            title = title,
            description = body,
            timeLabel = timeLabel,
            type = kind,
            unread = !read,
            actionTarget = actionTarget,
            threadId = threadId,
        )
    }
}
