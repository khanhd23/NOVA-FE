package com.nova.app.core.data

import com.nova.app.core.designsystem.NovaIcons
import com.nova.app.core.model.ActionKind
import com.nova.app.core.model.AdminMetric
import com.nova.app.core.model.AppSettings
import com.nova.app.core.model.BadgeItem
import com.nova.app.core.model.CallDirection
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.model.CallSummaryUiState
import com.nova.app.core.model.CallType
import com.nova.app.core.model.ChatAttachmentDraft
import com.nova.app.core.model.ChatAttachmentKind
import com.nova.app.core.model.ChatMessage
import com.nova.app.core.model.ChatThread
import com.nova.app.core.model.ChatUiState
import com.nova.app.core.model.CommunityUiState
import com.nova.app.core.model.CommunityComment
import com.nova.app.core.model.CommunityPost
import com.nova.app.core.model.CommunityTopic
import com.nova.app.core.model.CompatibilityMetric
import com.nova.app.core.model.DiscoverUiState
import com.nova.app.core.model.DiscoveryCandidate
import com.nova.app.core.model.EventItem
import com.nova.app.core.model.FeedPost
import com.nova.app.core.model.HomeUiState
import com.nova.app.core.model.LaunchUiState
import com.nova.app.core.model.MessagesUiState
import com.nova.app.core.model.NotificationItem
import com.nova.app.core.model.PremiumPlan
import com.nova.app.core.model.ProfileUiState
import com.nova.app.core.model.SampleMedia
import com.nova.app.core.model.SafetyItem
import com.nova.app.core.model.ScreenAction
import com.nova.app.core.model.ScreenSpec
import com.nova.app.core.model.SearchFilter
import com.nova.app.core.model.SessionState
import com.nova.app.core.model.StatCard
import com.nova.app.core.model.StoryItem
import com.nova.app.core.model.UserCard
import com.nova.app.core.model.WalletEntry
import com.nova.app.core.backend.BackendRealtimeEvent
import com.nova.app.core.backend.BackendRealtimeEventType
import com.nova.app.core.backend.BackendChatMessage
import com.nova.app.core.backend.BackendChatThread
import com.nova.app.core.backend.BackendProfileUpdateRequest
import com.nova.app.core.backend.BackendSession
import com.nova.app.core.backend.BackendThreadDetailResponse
import com.nova.app.core.backend.BackendRuntimeRegistry
import com.nova.app.core.backend.BackendCommunityCommentRequest
import com.nova.app.core.backend.BackendCommunityComment
import com.nova.app.core.backend.BackendCommunityFeed
import com.nova.app.core.backend.BackendCommunityEvent
import com.nova.app.core.backend.BackendCommunityPost
import com.nova.app.core.backend.BackendCommunityTopic
import com.nova.app.core.backend.BackendCommunityPostRequest
import com.nova.app.core.backend.BackendCommunityShareRequest
import com.nova.app.core.backend.BackendCommunityShareResponse
import com.nova.app.core.backend.BackendMediaUploadRequest
import com.nova.app.core.backend.BackendMessageAttachment
import com.nova.app.core.model.CreatePostDraft
import com.nova.app.core.backend.payloadBoolean
import com.nova.app.core.backend.toChatMessage
import com.nova.app.core.backend.toChatThread
import com.nova.app.core.navigation.AppRoute
import com.nova.app.core.navigation.ScreenStateKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

interface NovaRepository {
    val session: StateFlow<SessionState>
    val settings: StateFlow<AppSettings>
    val home: StateFlow<HomeUiState>
    val discover: StateFlow<DiscoverUiState>
    val messages: StateFlow<MessagesUiState>
    val chat: StateFlow<ChatUiState>
    val community: StateFlow<CommunityUiState>
    val profile: StateFlow<ProfileUiState>

    suspend fun completeOnboarding()
    suspend fun completeAuth()
    suspend fun completeProfile()
    suspend fun toggleTheme()
    suspend fun togglePremium()
    suspend fun updateLanguage(language: String)
    suspend fun toggleIncognito()
    suspend fun toggleTravelMode()
    suspend fun refreshProfile()
    suspend fun updateProfile(request: BackendProfileUpdateRequest)
    suspend fun likeCandidate()
    suspend fun superLikeCandidate()
    suspend fun skipCandidate()
    suspend fun saveCandidate()
    suspend fun refreshMessages()
    suspend fun openChatThread(thread: ChatThread)
    suspend fun loadMoreChatMessages()
    suspend fun sendMessage(text: String, attachment: ChatAttachmentDraft? = null)
    suspend fun toggleTopic(topicId: String)
    suspend fun joinEvent(eventId: String)
    suspend fun refreshCommunity(tab: String = "for_you", cursor: String? = null, refresh: Boolean = false, size: Int = 10)
    suspend fun createCommunityPost(draft: CreatePostDraft)
    suspend fun likeCommunityPost(postId: String, liked: Boolean = true)
    suspend fun commentCommunityPost(postId: String, text: String)
    suspend fun shareCommunityPost(postId: String, target: String = "profile", recipientUserId: String? = null, copyLink: Boolean = true)
    suspend fun logout()
    fun applyBackendSession(session: BackendSession?)
    fun applyRealtimeEvent(event: BackendRealtimeEvent, currentUserId: String?)
}

class FakeNovaRepository : NovaRepository {
    private val sessionState = MutableStateFlow(SessionState())
    private val settingsState = MutableStateFlow(defaultSettings())
    private val discoverState = MutableStateFlow(defaultDiscoverState())
    private val homeState = MutableStateFlow(defaultHomeState())
    private val messagesState = MutableStateFlow(defaultMessagesState())
    private val chatState = MutableStateFlow(defaultChatState())
    private val communityState = MutableStateFlow(defaultCommunityState())
    private val profileState = MutableStateFlow(defaultProfileState(settingsState.value))
    private var backendCurrentUserId: String? = null

    override val session: StateFlow<SessionState> = sessionState.asStateFlow()
    override val settings: StateFlow<AppSettings> = settingsState.asStateFlow()
    override val home: StateFlow<HomeUiState> = homeState.asStateFlow()
    override val discover: StateFlow<DiscoverUiState> = discoverState.asStateFlow()
    override val messages: StateFlow<MessagesUiState> = messagesState.asStateFlow()
    override val chat: StateFlow<ChatUiState> = chatState.asStateFlow()
    override val community: StateFlow<CommunityUiState> = communityState.asStateFlow()
    override val profile: StateFlow<ProfileUiState> = profileState.asStateFlow()

    override suspend fun completeOnboarding() {
        sessionState.update { it.copy(isFirstLaunch = false, onboardingCompleted = true) }
        syncProfile()
    }

    override suspend fun completeAuth() {
        sessionState.update { it.copy(otpVerified = true) }
        syncProfile()
    }

    override suspend fun completeProfile() {
        sessionState.update { it.copy(profileCompleted = true) }
        syncProfile()
    }

    override suspend fun toggleTheme() {
        settingsState.update { it.copy(darkMode = !it.darkMode) }
        syncProfile()
    }

    override suspend fun togglePremium() {
        settingsState.update { it.copy(premiumEnabled = !it.premiumEnabled) }
        syncProfile()
    }

    override suspend fun updateLanguage(language: String) {
        settingsState.update { it.copy(language = language) }
        syncProfile()
    }

    override suspend fun toggleIncognito() {
        settingsState.update { it.copy(incognitoEnabled = !it.incognitoEnabled) }
        syncProfile()
    }

    override suspend fun toggleTravelMode() {
        settingsState.update { it.copy(travelModeEnabled = !it.travelModeEnabled) }
        syncProfile()
    }

    override suspend fun refreshProfile() {
        val runtime = BackendRuntimeRegistry.runtime ?: return
        val profile = runCatching { runtime.fetchMe() }.getOrNull() ?: return
        applyBackendProfile(profile)
        val posts = runCatching { runtime.fetchProfilePosts(profile.userId) }.getOrNull()
        if (posts != null) {
            profileState.update { current ->
                current.copy(posts = posts.map { it.toCommunityPost() })
            }
        }
    }

    override suspend fun updateProfile(request: BackendProfileUpdateRequest) {
        val runtime = BackendRuntimeRegistry.runtime
        val updated = runtime?.updateProfile(request)
        if (updated != null) {
            applyBackendProfile(updated)
        } else {
            applyLocalProfileUpdate(request)
        }
    }

    override suspend fun likeCandidate() {
        discoverState.update { state ->
            val next = state.queue.drop(1).ifEmpty { sampleCandidates() }
            state.copy(queue = next, liked = state.liked + 1, activeIndex = 0)
        }
    }

    override suspend fun superLikeCandidate() {
        discoverState.update { state ->
            val next = state.queue.drop(1).ifEmpty { sampleCandidates() }
            state.copy(queue = next, superLiked = state.superLiked + 1, activeIndex = 0)
        }
    }

    override suspend fun skipCandidate() {
        discoverState.update { state ->
            val next = state.queue.drop(1).ifEmpty { sampleCandidates() }
            state.copy(queue = next, skipped = state.skipped + 1, activeIndex = 0)
        }
    }

    override suspend fun saveCandidate() {
        discoverState.update { it.copy(saved = it.saved + 1) }
    }

    override suspend fun refreshMessages() {
        val runtime = BackendRuntimeRegistry.runtime ?: return
        val threads = runCatching { runtime.fetchThreads() }.getOrNull() ?: return
        val mappedThreads = threads.map { it.toChatThread() }
        messagesState.update { state ->
            state.copy(
                threads = mappedThreads,
                onlineNow = mappedThreads.count { it.online },
            )
        }
        val currentThreadId = chatState.value.thread.id
        val refreshedThread = mappedThreads.firstOrNull { it.id == currentThreadId } ?: return
        chatState.update { state ->
            state.copy(thread = refreshedThread)
        }
    }

    override suspend fun openChatThread(thread: ChatThread) {
        val runtime = BackendRuntimeRegistry.runtime
        val session = runtime?.currentSession()
        chatState.update { current ->
            current.copy(
                thread = thread,
                messages = emptyList(),
                typing = false,
                loading = true,
                loadingMore = false,
                hasMore = false,
                nextCursor = null,
            )
        }

        if (runtime == null || session == null) {
            chatState.update { current ->
                current.copy(loading = false)
            }
            return
        }

        val detail = runCatching { runtime.fetchThread(thread.id, limit = 20) }.getOrNull()
        if (detail == null) {
            chatState.update { current ->
                current.copy(
                    thread = thread,
                    loading = false,
                )
            }
            return
        }

        val backendThread = detail.thread.toChatThread().copy(unreadCount = 0)
        val messages = detail.messages.asReversed().map { it.toChatMessage(session.userId) }
        chatState.update { current ->
            if (current.thread.id != backendThread.id) {
                current
            } else {
                current.copy(
                    thread = backendThread,
                    messages = messages,
                    typing = backendThread.typing,
                    loading = false,
                    loadingMore = false,
                    hasMore = detail.hasMore,
                    nextCursor = detail.nextCursor,
                )
            }
        }
        messagesState.update { state ->
            val updatedThreads = listOf(backendThread) + state.threads.filterNot { it.id == backendThread.id }
            state.copy(
                threads = updatedThreads,
                onlineNow = updatedThreads.count { it.online },
            )
        }
        runCatching { runtime.markThreadRead(thread.id) }
    }

    override suspend fun loadMoreChatMessages() {
        val runtime = BackendRuntimeRegistry.runtime ?: return
        val session = runtime.currentSession() ?: return
        val current = chatState.value
        val threadId = current.thread.id
        val cursor = current.nextCursor ?: return
        if (current.loadingMore || !current.hasMore || threadId.isBlank()) {
            return
        }

        chatState.update { state ->
            if (state.thread.id != threadId) state else state.copy(loadingMore = true)
        }

        val detail = runCatching { runtime.fetchThread(threadId, limit = 20, before = cursor) }.getOrNull()
        if (detail == null) {
            chatState.update { state ->
                if (state.thread.id != threadId) state else state.copy(loadingMore = false)
            }
            return
        }

        val olderMessages = detail.messages.asReversed().map { it.toChatMessage(session.userId) }
        chatState.update { state ->
            if (state.thread.id != threadId) {
                state
            } else {
                val existingIds = state.messages.mapTo(hashSetOf()) { it.id }
                val merged = state.messages + olderMessages.filterNot { existingIds.contains(it.id) }
                state.copy(
                    messages = merged,
                    loadingMore = false,
                    hasMore = detail.hasMore,
                    nextCursor = detail.nextCursor,
                )
            }
        }
    }

    override suspend fun sendMessage(text: String, attachment: ChatAttachmentDraft?) {
        val runtime = BackendRuntimeRegistry.runtime ?: return
        val session = runtime.currentSession() ?: return
        val outgoing = chatState.value
        val backendAttachment = attachment?.let { draft ->
            val uploaded = runCatching {
                runtime.uploadMedia(
                    BackendMediaUploadRequest(
                        uri = draft.uri,
                        fileName = draft.name,
                        title = draft.name,
                        mimeType = draft.mimeType,
                        kind = draft.kind,
                    )
                )
            }.getOrNull() ?: return
            BackendMessageAttachment(
                url = uploaded.url,
                previewUrl = uploaded.previewUrl,
                mimeType = uploaded.mimeType,
                name = draft.name,
                kind = draft.kind,
                durationSeconds = draft.durationSeconds,
            )
        }
        val sent = runCatching {
            runtime.sendMessage(outgoing.thread.id, text.trim(), backendAttachment)
        }.getOrNull() ?: return
        val message = sent.toChatMessage(session.userId)
        val updatedThread = outgoing.thread.copy(lastMessage = previewTextForOutgoing(text, attachment), unreadCount = 0, typing = false)
        chatState.update { state ->
            if (state.thread.id != outgoing.thread.id) {
                state
            } else {
                state.copy(
                    thread = updatedThread,
                    messages = state.messages.upsertNewest(message),
                    typing = false,
                )
            }
        }
        messagesState.update { state ->
            val existingThread = state.threads.firstOrNull { it.id == outgoing.thread.id }
            val mergedThread = (existingThread ?: updatedThread).copy(
                lastMessage = previewTextForOutgoing(text, attachment),
                unreadCount = 0,
                typing = false,
            )
            val updatedThreads = listOf(mergedThread) + state.threads.filterNot { it.id == mergedThread.id }
            state.copy(
                threads = updatedThreads,
                onlineNow = updatedThreads.count { it.online },
            )
        }
    }

    override suspend fun toggleTopic(topicId: String) {
        communityState.update { state ->
            state.copy(topics = state.topics.map { if (it.id == topicId) it.copy(isJoined = !it.isJoined) else it })
        }
    }

    override suspend fun joinEvent(eventId: String) {
        communityState.update { state ->
            state.copy(events = state.events.map { if (it.id == eventId) it.copy(joined = true) else it })
        }
    }

    override suspend fun refreshCommunity(tab: String, cursor: String?, refresh: Boolean, size: Int) {
        val runtime = BackendRuntimeRegistry.runtime
        if (runtime != null) {
            val feed = runCatching { runtime.fetchCommunityFeed(tab, cursor, refresh, size) }.getOrNull()
            if (feed != null) {
                applyBackendCommunity(feed, tab)
                return
            }
        }

        communityState.update { current ->
            current.copy(
                selectedTab = tab,
                loading = false,
                refreshing = false,
                hasMore = false,
                nextCursor = null,
                refreshToken = if (refresh) "local-refresh-${System.currentTimeMillis()}" else current.refreshToken,
            )
        }
    }

    override suspend fun createCommunityPost(draft: CreatePostDraft) {
        val runtime = BackendRuntimeRegistry.runtime
        if (runtime != null) {
            val created = runCatching {
                runtime.createCommunityPost(
                    BackendCommunityPostRequest(
                        topicId = draft.topicId,
                        text = draft.text,
                        postType = draft.postType,
                        mediaUrl = draft.mediaUrl,
                        mediaUrls = draft.mediaUrls,
                        thumbnailUrl = draft.thumbnailUrl,
                        tags = draft.tags,
                        mentionedUserIds = draft.mentionedUserIds,
                    )
                )
            }.getOrNull()
            if (created != null) {
                refreshCommunity(communityState.value.selectedTab, refresh = true)
                return
            }
        }

        communityState.update { current ->
            val nextPosts = listOf(
                CommunityPost(
                    id = "draft-${current.posts.size + 1}",
                    topic = current.topics.firstOrNull()?.title ?: "For You",
                    author = current.posts.firstOrNull()?.author ?: sampleUsers()[0],
                    postType = draft.postType,
                    text = draft.text,
                    mediaUrl = draft.mediaUrl,
                    mediaUrls = draft.mediaUrls,
                    thumbnailUrl = draft.thumbnailUrl,
                    tags = draft.tags,
                    mentionedUserIds = draft.mentionedUserIds,
                    likes = 0,
                    comments = 0,
                    commentsPreview = emptyList(),
                    shares = 0,
                    likedByMe = true,
                    sharedByMe = false,
                    timeLabel = "Now",
                )
            ) + current.posts
            current.copy(posts = nextPosts)
        }
    }

    override suspend fun likeCommunityPost(postId: String, liked: Boolean) {
        val runtime = BackendRuntimeRegistry.runtime
        if (runtime != null) {
            val updated = runCatching { runtime.likeCommunityPost(postId, liked) }.getOrNull()
            if (updated != null) {
                applyBackendPostUpdate(updated)
                refreshCommunity(communityState.value.selectedTab, refresh = true)
                return
            }
        }
        applyLocalPostUpdate(postId) { post ->
            post.copy(
                likes = if (liked) post.likes + 1 else (post.likes - 1).coerceAtLeast(0),
                likedByMe = liked,
            )
        }
    }

    override suspend fun commentCommunityPost(postId: String, text: String) {
        val runtime = BackendRuntimeRegistry.runtime
        if (runtime != null) {
            val updated = runCatching { runtime.commentCommunityPost(postId, BackendCommunityCommentRequest(text = text)) }.getOrNull()
            if (updated != null) {
                applyBackendPostUpdate(updated)
                refreshCommunity(communityState.value.selectedTab, refresh = true)
                return
            }
        }
        applyLocalPostUpdate(postId) { post ->
            post.copy(comments = post.comments + 1)
        }
    }

    override suspend fun shareCommunityPost(postId: String, target: String, recipientUserId: String?, copyLink: Boolean) {
        val runtime = BackendRuntimeRegistry.runtime
        if (runtime != null) {
            val updated = runCatching {
                runtime.shareCommunityPost(
                    postId,
                    BackendCommunityShareRequest(
                        target = target,
                        recipientUserId = recipientUserId,
                        copyLink = copyLink,
                    )
                )
            }.getOrNull()
            if (updated != null) {
                applyBackendPostUpdate(updated.post)
                refreshCommunity(communityState.value.selectedTab, refresh = true)
                return
            }
        }
        applyLocalPostUpdate(postId) { post ->
            post.copy(shares = post.shares + 1, sharedByMe = true)
        }
    }

    override suspend fun logout() {
        runCatching { BackendRuntimeRegistry.runtime?.signOut() }
        sessionState.update { current ->
            current.copy(
                isFirstLaunch = false,
                onboardingCompleted = true,
                otpVerified = false,
            )
        }
        backendCurrentUserId = null
        homeState.value = defaultHomeState()
        discoverState.value = defaultDiscoverState()
        messagesState.value = defaultMessagesState()
        chatState.value = defaultChatState()
        communityState.value = defaultCommunityState()
        profileState.value = defaultProfileState(settingsState.value)
    }

    override fun applyBackendSession(session: BackendSession?) {
        if (session == null) {
            return
        }
        sessionState.update { current ->
            current.copy(
                isFirstLaunch = false,
                onboardingCompleted = session.onboardingComplete || current.onboardingCompleted,
                profileCompleted = session.profileComplete || current.profileCompleted,
                otpVerified = true,
            )
        }
        profileState.update { current ->
            current.copy(
                user = current.user.copy(
                    id = session.userId,
                    name = session.displayName,
                    photoUrl = session.avatarUrl?.takeIf { it.isNotBlank() } ?: current.user.photoUrl,
                ),
            )
        }
    }

    override fun applyRealtimeEvent(event: BackendRealtimeEvent, currentUserId: String?) {
        backendCurrentUserId = currentUserId ?: backendCurrentUserId
        val viewerUserId = currentUserId ?: backendCurrentUserId
        when (event.type) {
            BackendRealtimeEventType.MESSAGE_CREATED,
            BackendRealtimeEventType.MESSAGE_RECALLED -> applyRemoteMessage(event, viewerUserId)

            BackendRealtimeEventType.THREAD_READ -> applyThreadRead(event, viewerUserId)
            BackendRealtimeEventType.THREAD_TYPING -> applyThreadTyping(event)
            BackendRealtimeEventType.THREAD_DELETED -> applyThreadDeletion(event, viewerUserId)
            BackendRealtimeEventType.MESSAGE_DELETED -> applyMessageDeletion(event, viewerUserId)
            BackendRealtimeEventType.NOTIFICATION_CREATED -> applyNotification(event)
            else -> Unit
        }
    }

    private fun syncProfile() {
        val settings = settingsState.value
        val session = sessionState.value
        profileState.update { current ->
            current.copy(
                settings = settings,
                stats = listOf(
                    StatCard("Matches", "128", "This month"),
                    StatCard("Soulmates", "4", "High compatibility"),
                    StatCard("Events", "17", "Joined"),
                    StatCard("Streak", "26", "Days active"),
                ),
                badges = defaultBadges(),
                wallet = defaultWallet(),
                safety = defaultSafety(settings),
                plans = defaultPlans(settings.premiumEnabled),
                notifications = defaultNotifications(),
                compatibility = defaultCompatibility(),
                filters = defaultFilters(),
                genericScreens = genericScreens(),
                adminMetrics = defaultAdminMetrics(),
                bio = current.bio,
            )
        }
    }

    private fun applyBackendProfile(profile: com.nova.app.core.backend.BackendProfile) {
        sessionState.update { current ->
            current.copy(
                isFirstLaunch = false,
                onboardingCompleted = profile.onboardingComplete || current.onboardingCompleted,
                profileCompleted = profile.profileComplete || current.profileCompleted,
                otpVerified = true,
            )
        }
        profileState.update { current ->
            current.copy(
                user = current.user.copy(
                    id = profile.userId,
                    publicId = profile.publicId,
                    name = profile.displayName,
                    age = profile.age,
                    photoUrl = profile.avatarUrl,
                    gender = profile.gender,
                    verified = profile.verified,
                    online = profile.online,
                    city = profile.city,
                    vipTierId = profile.vipTierId,
                    vipTierName = profile.vipTierName,
                    premium = profile.premium,
                    followersCount = profile.followersCount,
                    followingCount = profile.followingCount,
                    friendsCount = profile.friendsCount,
                    followedByMe = profile.followedByMe,
                    followedByThem = profile.followedByThem,
                    friend = profile.friend,
                ),
                bio = profile.bio,
                featuredPhotos = profile.featuredPhotos,
                interests = profile.interests,
            )
        }
    }

    private fun applyLocalProfileUpdate(request: BackendProfileUpdateRequest) {
        profileState.update { current ->
            current.copy(
                user = current.user.copy(
                    name = request.displayName,
                    photoUrl = request.photoUrl?.takeIf { it.isNotBlank() } ?: current.user.photoUrl,
                ),
                bio = request.bio ?: current.bio,
                featuredPhotos = if (request.featuredPhotos.isEmpty()) current.featuredPhotos else request.featuredPhotos,
                interests = if (request.interests.isEmpty()) current.interests else request.interests,
            )
        }
        sessionState.update { current ->
            current.copy(profileCompleted = true, otpVerified = true, isFirstLaunch = false)
        }
    }

    private fun applyBackendCommunity(feed: BackendCommunityFeed, tab: String) {
        communityState.update { current ->
            current.copy(
                topics = feed.topics.map { it.toCommunityTopic() },
                posts = feed.posts.map { it.toCommunityPost() },
                events = feed.events.map { it.toEventItem() },
                trending = feed.trendingTags.ifEmpty { current.trending },
                selectedTab = tab,
                loading = false,
                refreshing = false,
                hasMore = feed.hasMore,
                nextCursor = feed.nextCursor,
                refreshToken = feed.refreshToken,
                postTypes = feed.postTypes.ifEmpty { current.postTypes },
            )
        }
    }

    private fun BackendCommunityTopic.toCommunityTopic(): CommunityTopic {
        return CommunityTopic(
            id = id,
            title = title,
            description = description,
            bannerUrl = bannerUrl,
            members = members,
            moderator = moderator,
            eventCount = eventCount,
            isJoined = joined,
        )
    }

    private fun BackendCommunityEvent.toEventItem(): EventItem {
        return EventItem(
            id = id,
            title = title,
            kind = kind,
            dateLabel = dateLabel,
            location = location,
            price = price,
            bannerUrl = bannerUrl,
            attendees = attendees,
            joined = joined,
        )
    }

    private fun BackendCommunityComment.toCommunityComment(): CommunityComment {
        return CommunityComment(
            id = id,
            postId = postId,
            author = toUserCard(
                id = authorId,
                name = authorName,
                photoUrl = authorAvatarUrl,
                vipTierId = authorVipTierId,
                vipTierName = authorVipTierName,
                premium = authorPremium,
                verified = false,
                online = false,
                city = "",
            ),
            text = text,
            timeLabel = timeLabel,
            mine = mine,
            mentionedUserIds = mentionedUserIds,
        )
    }

    private fun BackendCommunityPost.toCommunityPost(): CommunityPost {
        return CommunityPost(
            id = id,
            topic = topicId,
            author = toUserCard(
                id = authorId,
                name = authorName,
                photoUrl = authorAvatarUrl,
                vipTierId = authorVipTierId,
                vipTierName = authorVipTierName,
                premium = authorPremium,
                verified = authorVerified,
                online = authorOnline,
                city = authorCity,
            ),
            postType = postType,
            text = text,
            mediaUrl = mediaUrl,
            mediaUrls = mediaUrls,
            thumbnailUrl = thumbnailUrl,
            tags = tags,
            mentionedUserIds = mentionedUserIds,
            likes = likes,
            comments = comments,
            commentsPreview = commentsPreview.map { it.toCommunityComment() },
            shares = shares,
            likedByMe = likedByMe,
            sharedByMe = sharedByMe,
            timeLabel = timeLabel,
        )
    }

    private fun applyBackendPostUpdate(post: BackendCommunityPost) {
        val mapped = post.toCommunityPost()
        applyLocalPostUpdate(mapped.id) { mapped }
    }

    private fun applyLocalPostUpdate(postId: String, transform: (CommunityPost) -> CommunityPost) {
        communityState.update { state ->
            state.copy(posts = state.posts.map { post -> if (post.id == postId) transform(post) else post })
        }
        profileState.update { state ->
            state.copy(posts = state.posts.map { post -> if (post.id == postId) transform(post) else post })
        }
    }

    private fun toUserCard(
        id: String,
        publicId: String = "",
        name: String,
        photoUrl: String,
        vipTierId: String? = null,
        vipTierName: String? = null,
        premium: Boolean = false,
        verified: Boolean,
        online: Boolean,
        city: String,
        gender: String = "Not specified",
    ): UserCard {
        return UserCard(
            id = id,
            publicId = publicId,
            name = name.ifBlank { "Nova User" },
            age = 0,
            photoUrl = photoUrl.ifBlank { fallbackAvatarUrl(name) },
            verified = verified,
            online = online,
            city = city,
            vipTierId = vipTierId,
            vipTierName = vipTierName,
            premium = premium,
            gender = gender,
        )
    }

    private fun fallbackAvatarUrl(name: String): String {
        val safeName = if (name.isBlank()) "Nova User" else name.trim()
        val encoded = java.net.URLEncoder.encode(safeName, java.nio.charset.StandardCharsets.UTF_8.toString())
        return "https://ui-avatars.com/api/?name=$encoded&background=6C5CE7&color=FFFFFF&size=512"
    }

    private fun applyRemoteMessage(event: BackendRealtimeEvent, viewerUserId: String?) {
        val message = event.toChatMessage(viewerUserId) ?: return
        val threadId = event.threadId ?: return

        messagesState.update { state ->
            val existingThread = state.threads.firstOrNull { it.id == threadId }
                ?: if (chatState.value.thread.id == threadId) chatState.value.thread else null
            val nextUnreadCount = when {
                message.sentByMe -> 0
                threadId == chatState.value.thread.id -> 0
                else -> (existingThread?.unreadCount ?: 0) + 1
            }
            val updatedThread = existingThread?.copy(
                lastMessage = previewTextForEvent(event, viewerUserId, message),
                unreadCount = nextUnreadCount,
                typing = false,
            )
            val threads = if (updatedThread != null) {
                listOf(updatedThread) + state.threads.filterNot { it.id == threadId }
            } else {
                state.threads
            }
            state.copy(
                threads = threads,
                onlineNow = threads.count { it.online },
            )
        }

        chatState.update { state ->
            if (state.thread.id != threadId) {
                state
            } else {
                state.copy(
                    messages = state.messages.upsertNewest(message),
                    typing = state.typing,
                )
            }
        }
    }

    private fun applyThreadRead(event: BackendRealtimeEvent, viewerUserId: String?) {
        val threadId = event.threadId ?: return
        messagesState.update { state ->
            state.copy(
                threads = state.threads.map { thread ->
                    if (thread.id == threadId) thread.copy(unreadCount = 0, typing = false) else thread
                }
            )
        }
        chatState.update { state ->
            if (state.thread.id != threadId) {
                state
            } else {
                state.copy(
                    messages = state.messages.map { message ->
                        if (message.sentByMe) message else message.copy(isRead = true)
                    },
                )
            }
        }
    }

    private fun applyThreadTyping(event: BackendRealtimeEvent) {
        val threadId = event.threadId ?: return
        val typing = event.payloadBoolean("typing")
        messagesState.update { state ->
            state.copy(
                threads = state.threads.map { thread ->
                    if (thread.id == threadId) thread.copy(typing = typing) else thread
                }
            )
        }
        chatState.update { state ->
            if (state.thread.id == threadId) state.copy(typing = typing) else state
        }
    }

    private fun applyThreadDeletion(event: BackendRealtimeEvent, viewerUserId: String?) {
        val threadId = event.threadId ?: return
        if (viewerUserId != null && event.targetUserId != null && event.targetUserId != viewerUserId) {
            return
        }
        messagesState.update { state ->
            state.copy(threads = state.threads.filterNot { it.id == threadId })
        }
        chatState.update { state ->
            if (state.thread.id == threadId) {
                state.copy(
                    messages = emptyList(),
                    typing = false,
                    loading = false,
                    loadingMore = false,
                    hasMore = false,
                    nextCursor = null,
                )
            } else {
                state
            }
        }
    }

    private fun applyMessageDeletion(event: BackendRealtimeEvent, viewerUserId: String?) {
        val threadId = event.threadId ?: return
        val messageId = event.messageId ?: return
        chatState.update { state ->
            if (state.thread.id != threadId) {
                state
            } else {
                state.copy(messages = state.messages.filterNot { it.id == messageId })
            }
        }
    }

    private fun applyNotification(event: BackendRealtimeEvent) {
        val title = event.title ?: return
        val body = event.body ?: return
        val notification = NotificationItem(
            id = event.payload["notificationId"] ?: event.id,
            title = title,
            description = body,
            timeLabel = event.payload["timeLabel"] ?: "Now",
            type = event.payload["kind"] ?: "System",
            unread = !event.payloadBoolean("read"),
            actionTarget = event.payload["actionTarget"],
            threadId = event.threadId ?: event.payload["threadId"],
        )
        profileState.update { state ->
            state.copy(notifications = listOf(notification) + state.notifications.filterNot { it.id == notification.id })
        }
    }

    private fun previewTextForEvent(event: BackendRealtimeEvent, viewerUserId: String?, message: ChatMessage): String {
        if (message.callSummary != null) {
            return callSummaryPreview(message.callSummary)
        }
        if (event.type == BackendRealtimeEventType.MESSAGE_RECALLED) {
            return if (message.sentByMe) "You unsent a message" else "This message was unsent"
        }
        return when {
            message.attachmentKind != null -> when (message.attachmentKind) {
                com.nova.app.core.model.ChatAttachmentKind.Image -> "Photo"
                com.nova.app.core.model.ChatAttachmentKind.Video -> "Video"
                com.nova.app.core.model.ChatAttachmentKind.Audio -> "Voice message"
                com.nova.app.core.model.ChatAttachmentKind.File -> message.attachmentName ?: "File"
            }
            message.isVoice -> "Voice message"
            message.isGif -> "GIF"
            message.isSticker -> "Sticker"
            message.text.isNotBlank() -> message.text
            else -> event.body ?: ""
        }
    }

    private fun previewTextForOutgoing(text: String, attachment: ChatAttachmentDraft?): String {
        if (attachment != null) {
            return when (attachment.kind) {
                ChatAttachmentKind.Image -> "Photo"
                ChatAttachmentKind.Video -> "Video"
                ChatAttachmentKind.Audio -> "Voice message"
                ChatAttachmentKind.File -> attachment.name.ifBlank { "File" }
            }
        }
        return text.trim()
    }

    private fun List<ChatMessage>.upsertNewest(message: ChatMessage): List<ChatMessage> {
        val existingIndex = indexOfFirst { it.id == message.id }
        return if (existingIndex >= 0) {
            toMutableList().apply {
                this[existingIndex] = message
            }.toList()
        } else {
            listOf(message) + filterNot { it.id == message.id }
        }
    }

    private fun callSummaryPreview(summary: com.nova.app.core.model.CallSummaryUiState): String {
        return if (summary.durationSeconds > 0) {
            "Connected ${formatDuration(summary.durationSeconds)}"
        } else {
            when (summary.endReason) {
                com.nova.app.core.model.CallEndReason.Missed -> "Missed call"
                com.nova.app.core.model.CallEndReason.NoAnswer -> "No answer"
                com.nova.app.core.model.CallEndReason.Declined -> "Declined call"
                com.nova.app.core.model.CallEndReason.Rejected -> "Rejected call"
                com.nova.app.core.model.CallEndReason.Busy -> "Busy"
                com.nova.app.core.model.CallEndReason.Canceled -> "Canceled call"
                com.nova.app.core.model.CallEndReason.Dropped -> "Call dropped"
                else -> "Call ended"
            }
        }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun defaultSettings() = AppSettings()

    private fun defaultHomeState() = HomeUiState(
        stories = listOf(
            StoryItem(user = sampleUsers()[0], mediaUrl = SampleMedia.landscape1, caption = "Rooftop sunset and a latte.", music = "SZA - Snooze"),
            StoryItem(user = sampleUsers()[1], mediaUrl = SampleMedia.landscape2, caption = "Hiking before breakfast.", music = "Odesza - A Moment Apart"),
            StoryItem(user = sampleUsers()[2], mediaUrl = SampleMedia.landscape3, caption = "Vinyl night with friends.", music = "Khruangbin - Time"),
            StoryItem(user = sampleUsers()[3], mediaUrl = SampleMedia.landscape4, caption = "Museum, then cocktail bar.", music = "Glass Animals - Gooey"),
        ),
        feed = listOf(
            FeedPost(
                id = "feed-1",
                author = sampleUsers()[0],
                caption = "A soft launch into my new city. Coffee, design, and long walks only.",
                mediaUrls = listOf(SampleMedia.portrait2),
                likes = 384,
                comments = 48,
                saves = 94,
                tags = listOf("City life", "Coffee", "Fashion"),
                timeLabel = "12m ago",
            ),
            FeedPost(
                id = "feed-2",
                author = sampleUsers()[1],
                caption = "Looking for a hiking buddy who can also recommend a good playlist.",
                mediaUrls = listOf(SampleMedia.landscape3),
                likes = 221,
                comments = 31,
                saves = 62,
                tags = listOf("Hiking", "Music", "Weekend"),
                timeLabel = "1h ago",
            ),
        ),
        featured = sampleCandidates().first(),
        events = sampleEvents(),
        communities = sampleTopics(),
        suggestions = listOf("Video date tonight", "Coffee nearby", "Soulmate picks", "Join a community"),
    )

    private fun defaultDiscoverState() = DiscoverUiState(
        queue = sampleCandidates(),
        activeIndex = 0,
        liked = 18,
        superLiked = 4,
        saved = 9,
        skipped = 27,
    )

    private fun defaultMessagesState() = MessagesUiState(
        threads = emptyList(),
        onlineNow = 0,
        filters = listOf("All", "Matches", "Voice", "Groups"),
        searchHint = "Search by name, interest, or city",
    )

    private fun defaultChatState() = ChatUiState(
        thread = ChatThread(
            id = "",
            user = UserCard(
                id = "",
                name = "Chat",
                age = 0,
                photoUrl = "",
                verified = false,
                online = false,
                city = "",
            ),
            lastMessage = "",
            unreadCount = 0,
            online = false,
        ),
        messages = emptyList(),
        typing = false,
        suggestions = emptyList(),
        translationEnabled = false,
        callHint = "",
        loading = false,
        loadingMore = false,
        hasMore = false,
        nextCursor = null,
    )

    private fun defaultCommunityState() = CommunityUiState(
        topics = sampleTopics(),
        posts = listOf(
            CommunityPost(
                id = "cp-1",
                topic = "Travel",
                author = sampleUsers()[0],
                postType = "TEXT",
                text = "Best date spots in Bangkok that don't feel touristy?",
                mediaUrl = null,
                mediaUrls = emptyList(),
                likes = 84,
                comments = 14,
                timeLabel = "2h ago",
            ),
            CommunityPost(
                id = "cp-2",
                topic = "Fitness",
                author = sampleUsers()[1],
                postType = "IMAGE",
                text = "Sunset walk, one frame, no filter.",
                mediaUrl = SampleMedia.landscape1,
                mediaUrls = listOf(SampleMedia.landscape1),
                likes = 58,
                comments = 9,
                timeLabel = "5h ago",
            ),
            CommunityPost(
                id = "cp-3",
                topic = "Photography",
                author = sampleUsers()[2],
                postType = "IMAGE",
                text = "Three frames from the same golden hour walk.",
                mediaUrl = SampleMedia.landscape2,
                mediaUrls = listOf(SampleMedia.landscape2, SampleMedia.landscape3, SampleMedia.landscape4),
                likes = 102,
                comments = 21,
                timeLabel = "8h ago",
            ),
            CommunityPost(
                id = "cp-4",
                topic = "Travel",
                author = sampleUsers()[3],
                postType = "MIXED",
                text = "Weekend recap: stills, then the reel.",
                mediaUrl = SampleMedia.landscape3,
                mediaUrls = listOf(
                    SampleMedia.landscape3,
                    "https://www.w3schools.com/html/mov_bbb.mp4",
                    SampleMedia.landscape1,
                ),
                thumbnailUrl = SampleMedia.landscape4,
                likes = 77,
                comments = 18,
                timeLabel = "11h ago",
            ),
            CommunityPost(
                id = "cp-5",
                topic = "Cafe",
                author = sampleUsers()[4],
                postType = "VIDEO",
                text = "Short clip from last night's set.",
                mediaUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
                mediaUrls = listOf("https://www.w3schools.com/html/mov_bbb.mp4"),
                thumbnailUrl = SampleMedia.landscape2,
                likes = 41,
                comments = 6,
                timeLabel = "1d ago",
            ),
        ),
        events = sampleEvents(),
        trending = listOf("Travel friends", "Coffee dates", "Startup founders", "Anime night"),
    )

    private fun defaultProfileState(settings: AppSettings) = ProfileUiState(
        user = sampleUsers()[4].copy(
            vipTierId = "vip_0",
            vipTierName = "VIP 0",
            premium = false,
        ),
        bio = "Builder by day, city explorer by night. Looking for something emotionally honest and curious.",
        featuredPhotos = listOf(
            SampleMedia.portrait6,
            SampleMedia.landscape1,
            SampleMedia.landscape2,
        ),
        interests = listOf("Travel", "Coffee", "Music", "Photography"),
        diamonds = 100,
        prompts = listOf(
            "A green flag I notice immediately is...",
            "My ideal first date is...",
            "The song that feels like home is...",
        ),
        badges = defaultBadges(),
        stats = listOf(
            StatCard("Matches", "128", "Lifetime"),
            StatCard("Soulmates", "4", "High compatibility"),
            StatCard("Events", "17", "Joined"),
            StatCard("Streak", "26", "Days"),
        ),
        settings = settings,
        wallet = defaultWallet(),
        safety = defaultSafety(settings),
        plans = defaultPlans(settings.premiumEnabled),
        notifications = defaultNotifications(),
        genericScreens = genericScreens(),
        adminMetrics = defaultAdminMetrics(),
        compatibility = defaultCompatibility(),
        filters = defaultFilters(),
    )

    private fun defaultBadges() = listOf(
        BadgeItem("b1", "Daily Login", "Opened the app 26 days in a row", 100, "26", true),
        BadgeItem("b2", "Verified", "Photo and identity verified", 100, "V", true),
        BadgeItem("b3", "Community Leader", "Helpful and active in communities", 72, "C", false),
        BadgeItem("b4", "Video Date", "Completed a successful video date", 48, "VD", false),
    )

    private fun defaultWallet() = listOf(
        WalletEntry("w1", "Coins added", "Gift credit from a match", "+120", "Today", true),
        WalletEntry("w2", "Premium", "Monthly subscription", "-$19.99", "Yesterday", false),
        WalletEntry("w3", "Boost", "Profile boost used", "-25", "2d ago", false),
    )

    private fun defaultSafety(settings: AppSettings) = listOf(
        SafetyItem("Report", "Report suspicious or abusive behavior", "Open", false),
        SafetyItem("Block", "Block a user instantly", "Manage", false),
        SafetyItem("Emergency", "Quick access to emergency contacts", "Setup", false),
        SafetyItem("Location Share", "Share live location during dates", "On", settings.locationSharingEnabled),
        SafetyItem("Photo Verification", "Verify your photos for trust", "On", settings.photoVerificationEnabled),
        SafetyItem("Video Verification", "Record a short verification clip", "Off", settings.videoVerificationEnabled),
    )

    private fun defaultPlans(premiumEnabled: Boolean) = listOf(
        PremiumPlan(
            name = "Free",
            price = "$0",
            cycle = "Forever",
            subtitle = "Core matching, stories, and community access.",
            features = listOf("Daily likes", "Basic filters", "Messaging", "Community access"),
            highlighted = !premiumEnabled,
        ),
        PremiumPlan(
            name = "Premium",
            price = "$19.99",
            cycle = "per month",
            subtitle = "Unlimited matches, advanced filters, and AI coach.",
            features = listOf("Unlimited likes", "Incognito", "Boost", "See who liked you", "Undo swipe", "Travel mode"),
            highlighted = premiumEnabled,
        ),
    )

    private fun defaultNotifications() = listOf(
        NotificationItem("n1", "New match", "Mia liked your profile and sent a message.", "5m", "Match", true),
        NotificationItem("n2", "Event reminder", "Coffee date meetup starts in 2 hours.", "1h", "Event", true),
        NotificationItem("n3", "Community reply", "Your post got 12 replies in Travel.", "3h", "Community", false),
        NotificationItem("n4", "Premium offer", "Unlock advanced filters and travel mode.", "Yesterday", "Promotion", false),
    )

    private fun defaultCompatibility() = listOf(
        CompatibilityMetric("Personality", 94, "Shared energy"),
        CompatibilityMetric("Lifestyle", 88, "Aligned pace"),
        CompatibilityMetric("Emotion", 91, "Deep trust"),
        CompatibilityMetric("Communication", 97, "Clear and direct"),
        CompatibilityMetric("Love Language", 89, "Strong overlap"),
        CompatibilityMetric("Conflict", 84, "Healthy repair"),
    )

    private fun defaultFilters() = listOf(
        SearchFilter("Distance", "Nearby"),
        SearchFilter("Age", "24-34"),
        SearchFilter("MBTI", "INFJ / ENFP"),
        SearchFilter("Language", "English, Vietnamese"),
        SearchFilter("Goal", "Long-term"),
    )

    private fun defaultAdminMetrics() = listOf(
        AdminMetric("Revenue", "$48.2K", "+12.4%", true),
        AdminMetric("Subscriptions", "7.8K", "+8.6%", true),
        AdminMetric("Reports", "42", "-18%", true),
        AdminMetric("Active users", "129K", "+9.1%", true),
    )

    private fun genericScreens() = listOf(
        ScreenSpec(
            id = "saved",
            title = "Saved",
            subtitle = "Curate the people and posts you want to revisit.",
            heroLabel = "Saved for later",
            heroDescription = "Bookmarks, favorite profiles, and conversations you want to bring back into the flow.",
            stats = listOf(StatCard("Profiles", "24"), StatCard("Posts", "18"), StatCard("Events", "7")),
            chips = listOf("Profiles", "Posts", "Events", "Music"),
            bullets = listOf("Organize by mood", "Keep the best icebreakers", "Jump back in anytime"),
            photos = listOf(SampleMedia.portrait1, SampleMedia.portrait2, SampleMedia.landscape2, SampleMedia.landscape3),
            actions = listOf(ScreenAction("Open Premium", NovaIcons.Premium, AppRoute.Premium), ScreenAction("Back to Profile", NovaIcons.Profile, AppRoute.Profile, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "likes",
            title = "Likes",
            subtitle = "See who reacted, liked, or saved your vibe.",
            heroLabel = "Who liked you",
            heroDescription = "A mix of matches, communities, and story reactions that can turn into conversations.",
            stats = listOf(StatCard("Likes", "128"), StatCard("Super likes", "9"), StatCard("Views", "4.1K")),
            chips = listOf("Today", "This week", "This month"),
            bullets = listOf("Fast response cards", "One-tap match actions", "Voice intro preview"),
            photos = listOf(SampleMedia.portrait3, SampleMedia.portrait4, SampleMedia.portrait5, SampleMedia.portrait6),
            actions = listOf(ScreenAction("Upgrade", NovaIcons.Premium, AppRoute.Premium), ScreenAction("See matches", NovaIcons.Messages, AppRoute.Messages, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "blocked",
            title = "Blocked users",
            subtitle = "Trust and safety list with instant control.",
            heroLabel = "Safe by default",
            heroDescription = "Manage blocked users, report history, and quick unblock flows from a single privacy surface.",
            stats = listOf(StatCard("Blocked", "6"), StatCard("Reports", "3"), StatCard("Safe mode", "On")),
            chips = listOf("Block", "Report", "Mute"),
            bullets = listOf("Emergency access", "Scam alerts", "Identity verification"),
            photos = listOf(SampleMedia.portrait7, SampleMedia.portrait8),
            actions = listOf(ScreenAction("Open Safety", NovaIcons.Safety, AppRoute.Safety), ScreenAction("Back", NovaIcons.ChevronRight, AppRoute.Profile, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "privacy",
            title = "Privacy center",
            subtitle = "Control visibility, read receipts, and location sharing.",
            heroLabel = "Privacy first",
            heroDescription = "Every control is tuned for trust, not friction. Hide where needed, show only when it matters.",
            stats = listOf(StatCard("Incognito", if (settingsState.value.incognitoEnabled) "On" else "Off"), StatCard("Location", if (settingsState.value.locationSharingEnabled) "Shared" else "Hidden")),
            chips = listOf("Incognito", "Location", "Read receipts", "Hide profile"),
            bullets = listOf("Fine-grained visibility", "Travel mode ready", "Safety controls in one place"),
            photos = listOf(SampleMedia.landscape1, SampleMedia.landscape4),
            actions = listOf(ScreenAction("Open Settings", NovaIcons.Settings, AppRoute.Settings), ScreenAction("Open Safety", NovaIcons.Safety, AppRoute.Safety, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "help",
            title = "Help center",
            subtitle = "Support, troubleshooting, and product guidance.",
            heroLabel = "Need a hand?",
            heroDescription = "Search articles, chat with support, and learn how to keep your profile strong.",
            stats = listOf(StatCard("Articles", "120"), StatCard("Chat", "24/7"), StatCard("Response", "< 2h")),
            chips = listOf("Account", "Payments", "Matches", "Safety"),
            bullets = listOf("AI search answers", "Self-service flows", "Escalate when needed"),
            photos = listOf(SampleMedia.portrait9, SampleMedia.portrait10),
            actions = listOf(ScreenAction("Feedback", NovaIcons.Feedback, AppRoute.Settings), ScreenAction("About", NovaIcons.Info, AppRoute.Profile, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "feedback",
            title = "Feedback",
            subtitle = "Capture product feedback and UX pain points.",
            heroLabel = "Say what you need",
            heroDescription = "A premium feedback surface with screenshots, context, and an easy submit flow.",
            stats = listOf(StatCard("Open", "14"), StatCard("Resolved", "126")),
            chips = listOf("UX", "Bug", "Feature", "Billing"),
            bullets = listOf("Screenshot attachment", "Priority tagging", "Direct follow up"),
            photos = listOf(SampleMedia.landscape2, SampleMedia.landscape3),
            actions = listOf(ScreenAction("Submit", NovaIcons.Send, AppRoute.Status(ScreenStateKind.Success)), ScreenAction("Back", NovaIcons.ChevronRight, AppRoute.Profile, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "about",
            title = "About NOVA",
            subtitle = "The product story, roadmap, and release notes.",
            heroLabel = "Built for real connection",
            heroDescription = "NOVA blends dating, social discovery, and community in one premium mobile experience.",
            stats = listOf(StatCard("Version", "1.0"), StatCard("Users", "129K")),
            chips = listOf("Dating", "Community", "AI", "Safety"),
            bullets = listOf("Modern stack", "Design-first product", "Scalable architecture"),
            photos = listOf(SampleMedia.landscape1, SampleMedia.landscape2, SampleMedia.landscape4),
            actions = listOf(ScreenAction("Security", NovaIcons.Security, AppRoute.Safety), ScreenAction("Back", NovaIcons.ChevronRight, AppRoute.Profile, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "security",
            title = "Security",
            subtitle = "Passwords, devices, verification, and session health.",
            heroLabel = "Trust layer",
            heroDescription = "All critical security settings live in one place with clear progress and warnings.",
            stats = listOf(StatCard("Verified", "Yes"), StatCard("Devices", "2")),
            chips = listOf("2FA", "Device lock", "Identity", "Photo"),
            bullets = listOf("Secure sign-in", "Session alerts", "Verification history"),
            photos = listOf(SampleMedia.portrait5, SampleMedia.portrait6),
            actions = listOf(ScreenAction("Identity", NovaIcons.Verified, AppRoute.Safety), ScreenAction("Back", NovaIcons.ChevronRight, AppRoute.Profile, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "verification",
            title = "Verification",
            subtitle = "Identity, video, and photo verification flows.",
            heroLabel = "Verified humans only",
            heroDescription = "A trust layer that keeps fake profiles out and gives real people confidence.",
            stats = listOf(StatCard("Photo", "On"), StatCard("Video", "Ready"), StatCard("ID", "In review")),
            chips = listOf("Photo", "Video", "ID", "Face match"),
            bullets = listOf("Reduce spam", "Increase match trust", "Better conversion"),
            photos = listOf(SampleMedia.portrait1, SampleMedia.portrait8, SampleMedia.portrait9),
            actions = listOf(ScreenAction("Start", NovaIcons.Camera, AppRoute.Status(ScreenStateKind.Permission)), ScreenAction("Back", NovaIcons.ChevronRight, AppRoute.Profile, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "travel",
            title = "Travel mode",
            subtitle = "Reach people in the city you're visiting before you land.",
            heroLabel = "Match anywhere",
            heroDescription = "Discover locals, events, and coffee spots for your next trip.",
            stats = listOf(StatCard("Cities", "18"), StatCard("Nearby", "214")),
            chips = listOf("Cities", "Nearby", "Events", "Coffee"),
            bullets = listOf("Smooth city switching", "Local recommendations", "Time-zone aware chat"),
            photos = listOf(SampleMedia.landscape3, SampleMedia.landscape4),
            actions = listOf(ScreenAction("Enable", NovaIcons.Travel, AppRoute.Events), ScreenAction("Back", NovaIcons.ChevronRight, AppRoute.Premium, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "incognito",
            title = "Incognito mode",
            subtitle = "Browse and match privately when you want a low-profile session.",
            heroLabel = "Private browsing",
            heroDescription = "Only the people you like can discover you while this mode is active.",
            stats = listOf(StatCard("Visible", "Selected"), StatCard("Private", "On")),
            chips = listOf("Invisible", "Selected likes", "Private", "Safe"),
            bullets = listOf("Hide from discover", "Selective visibility", "Premium gated"),
            photos = listOf(SampleMedia.portrait2, SampleMedia.portrait7),
            actions = listOf(ScreenAction("Upgrade", NovaIcons.Premium, AppRoute.Premium), ScreenAction("Back", NovaIcons.ChevronRight, AppRoute.Profile, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "boost",
            title = "Boost",
            subtitle = "Move your profile to the top and get instant reach.",
            heroLabel = "Visibility spike",
            heroDescription = "A premium growth surface with timing, analytics, and a fast activation button.",
            stats = listOf(StatCard("Boosts", "3"), StatCard("Reach", "+240%")),
            chips = listOf("Top slot", "Peak hours", "Analytics"),
            bullets = listOf("Immediate ranking lift", "Best time suggestions", "Boost history"),
            photos = listOf(SampleMedia.landscape2, SampleMedia.landscape3),
            actions = listOf(ScreenAction("Activate", NovaIcons.Boost, AppRoute.Status(ScreenStateKind.Success)), ScreenAction("Back", NovaIcons.ChevronRight, AppRoute.Premium, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "who_liked",
            title = "Who liked you",
            subtitle = "Premium reveal for the people most likely to match.",
            heroLabel = "Reveal queue",
            heroDescription = "A confidence-building way to see the people already interested in you.",
            stats = listOf(StatCard("Hidden likes", "24"), StatCard("Mutual", "9")),
            chips = listOf("Recent", "Top match", "Local"),
            bullets = listOf("Fast matching", "Priority sorting", "Confidence boost"),
            photos = listOf(SampleMedia.portrait3, SampleMedia.portrait4, SampleMedia.portrait5),
            actions = listOf(ScreenAction("Open Premium", NovaIcons.Premium, AppRoute.Premium), ScreenAction("Back", NovaIcons.ChevronRight, AppRoute.Profile, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "daily_reward",
            title = "Daily reward",
            subtitle = "Claim coins, boosts, and bonus gifts.",
            heroLabel = "Keep the streak",
            heroDescription = "A light daily loop that feels rewarding without breaking the premium tone.",
            stats = listOf(StatCard("Streak", "26"), StatCard("Coins", "420")),
            chips = listOf("Coins", "Boost", "Gift", "Streak"),
            bullets = listOf("Daily incentives", "Progressive rewards", "Soft habit loop"),
            photos = listOf(SampleMedia.landscape1, SampleMedia.landscape2),
            actions = listOf(ScreenAction("Claim", NovaIcons.Gift, AppRoute.Status(ScreenStateKind.Success)), ScreenAction("Back", NovaIcons.ChevronRight, AppRoute.Profile, ActionKind.Secondary)),
        ),
        ScreenSpec(
            id = "ux",
            title = "UX audit",
            subtitle = "Design system checks, accessibility, and performance states.",
            heroLabel = "Ship quality",
            heroDescription = "Review component reuse, empty/error handling, and touch target compliance.",
            stats = listOf(StatCard("Components", "24"), StatCard("States", "7")),
            chips = listOf("A11y", "Spacing", "States", "Motion"),
            bullets = listOf("Touch target >= 44px", "Dark mode contrast", "Animation restraint"),
            photos = listOf(SampleMedia.landscape4, SampleMedia.portrait10),
            actions = listOf(ScreenAction("Open Design System", NovaIcons.Sparkle, AppRoute.DesignSystem), ScreenAction("Back", NovaIcons.ChevronRight, AppRoute.Admin, ActionKind.Secondary)),
        ),
    )

    private fun sampleUsers() = listOf(
        UserCard("u1", "Mia", 27, SampleMedia.portrait2, verified = true, distanceKm = 2, online = true, city = "Bangkok", vipTierId = "vip_1", vipTierName = "VIP 1 Spark", premium = true, gender = "Female"),
        UserCard("u2", "Noah", 29, SampleMedia.portrait3, verified = true, distanceKm = 4, online = false, city = "Bangkok", vipTierId = "vip_2", vipTierName = "VIP 2 Glow", premium = true, gender = "Male"),
        UserCard("u3", "Ava", 24, SampleMedia.portrait4, verified = true, distanceKm = 1, online = true, city = "Bangkok", vipTierId = "vip_3", vipTierName = "VIP 3 Pulse", premium = true, gender = "Female"),
        UserCard("u4", "Ken", 31, SampleMedia.portrait5, verified = false, distanceKm = 6, online = false, city = "Bangkok", vipTierId = "vip_4", vipTierName = "VIP 4 Elite", premium = true, gender = "Male"),
        UserCard("me", "You", 28, SampleMedia.portrait6, verified = true, distanceKm = null, online = true, city = "Bangkok", vipTierId = "vip_0", vipTierName = "VIP 0", premium = false, gender = "Male"),
        UserCard("u5", "Olivia", 30, SampleMedia.portrait7, verified = true, distanceKm = 5, online = false, city = "Bangkok", vipTierId = "vip_5", vipTierName = "VIP 5 Prime", premium = true, gender = "Female"),
        UserCard("u6", "Daniel", 32, SampleMedia.portrait8, verified = true, distanceKm = 8, online = false, city = "Bangkok", vipTierId = "vip_6", vipTierName = "VIP 6 Aura", premium = true, gender = "Male"),
    )

    private fun sampleCandidates() = listOf(
        DiscoveryCandidate(
            user = sampleUsers()[0],
            bio = "Brand strategist, coffee ritualist, and a believer in long conversations over loud rooms.",
            compatibility = 96,
            commonInterests = listOf("Coffee", "Travel", "Architecture", "Vinyl"),
            iceBreaker = "Which city changed your taste in people?",
            mutualFriends = 8,
            musicTaste = "Indie pop",
            height = "168 cm",
            job = "Brand Strategist",
            relationshipGoal = "Long-term",
            gallery = listOf(SampleMedia.portrait2, SampleMedia.landscape1, SampleMedia.landscape2),
        ),
        DiscoveryCandidate(
            user = sampleUsers()[1],
            bio = "Product designer who likes early runs, calm playlists, and people with good eye contact.",
            compatibility = 91,
            commonInterests = listOf("Design", "Running", "Music"),
            iceBreaker = "What's your ideal first date pace?",
            mutualFriends = 5,
            musicTaste = "Electronic",
            height = "180 cm",
            job = "Product Designer",
            relationshipGoal = "Relationship",
            gallery = listOf(SampleMedia.portrait3, SampleMedia.landscape3, SampleMedia.landscape4),
        ),
        DiscoveryCandidate(
            user = sampleUsers()[2],
            bio = "Hospitality founder exploring new neighborhoods and good people.",
            compatibility = 88,
            commonInterests = listOf("Food", "Travel", "Startup", "Coffee"),
            iceBreaker = "Where would you take someone for a first coffee in your city?",
            mutualFriends = 3,
            musicTaste = "Jazz",
            height = "165 cm",
            job = "Founder",
            relationshipGoal = "Dating first",
            gallery = listOf(SampleMedia.portrait4, SampleMedia.landscape1),
        ),
        DiscoveryCandidate(
            user = sampleUsers()[3],
            bio = "Fitness coach, anime watcher, and weekend language-exchange host.",
            compatibility = 84,
            commonInterests = listOf("Fitness", "Anime", "Language", "Community"),
            iceBreaker = "If we only had 90 minutes, what would we do?",
            mutualFriends = 2,
            musicTaste = "Afrobeat",
            height = "176 cm",
            job = "Coach",
            relationshipGoal = "Open to connection",
            gallery = listOf(SampleMedia.portrait5, SampleMedia.portrait6, SampleMedia.landscape2),
        ),
    )

    private fun sampleThreads() = listOf(
        ChatThread("thread-seraphina", sampleUsers()[0], "Let's keep the coffee date idea alive.", 2, online = true, typing = true, pinned = true, matchLabel = "96% match"),
        ChatThread("thread-elena", sampleUsers()[1], "Shared a playlist and a Saturday plan.", 0, online = false, pinned = false, matchLabel = "91% match"),
        ChatThread("thread-chloe", sampleUsers()[2], "You were right about the rooftop bar.", 1, online = true, pinned = false, matchLabel = "88% match"),
        ChatThread("thread-marcus", sampleUsers()[3], "Festival tickets? I am in.", 0, online = false, pinned = false, matchLabel = "84% match"),
    )

    private fun sampleTopics() = listOf(
        CommunityTopic("travel", "Travel", "Weekend trips, city guides, and hidden gems.", SampleMedia.landscape1, "18.2K", "Mia", 12, true),
        CommunityTopic("cafe", "Cafe", "Coffee spots, roaster reviews, and date-friendly menus.", SampleMedia.landscape2, "7.4K", "Ava", 8, true),
        CommunityTopic("game", "Game", "Co-op nights, tournaments, and low-pressure icebreakers.", SampleMedia.landscape3, "5.1K", "Ken", 4, false),
        CommunityTopic("anime", "Anime", "Watch parties, recommendations, and fan meetups.", SampleMedia.landscape4, "9.8K", "Noah", 6, false),
        CommunityTopic("startup", "Startup", "Founders, operators, and creative builders.", SampleMedia.portrait8, "11.2K", "Mia", 7, false),
    )

    private fun sampleEvents() = listOf(
        EventItem("e1", "Sunset Coffee Crawl", "Offline", "Fri, 7:00 PM", "Thao Dien, Ho Chi Minh City", "$12", SampleMedia.landscape1, "42 joined", true),
        EventItem("e2", "Language Exchange Night", "Online", "Sat, 8:30 PM", "Zoom", "Free", SampleMedia.landscape2, "126 joined", false),
        EventItem("e3", "Speed Dating Social", "Offline", "Sun, 6:00 PM", "District 1", "$19", SampleMedia.landscape3, "61 joined", false),
        EventItem("e4", "Hiking + Brunch", "Offline", "Next Tue", "Dalat", "$24", SampleMedia.landscape4, "18 joined", false),
    )

    private fun syncCommunity() {
        communityState.update { state ->
            state.copy(events = state.events.map { it.copy(joined = it.joined) })
        }
    }
}
