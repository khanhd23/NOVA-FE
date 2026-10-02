package com.nova.app.core.data

import kotlinx.coroutines.launch

import android.net.Uri
import com.nova.app.core.model.AppSettings
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
import com.nova.app.core.model.CommunityMention
import com.nova.app.core.model.CommunityPost
import com.nova.app.core.model.CommunityTopic
import com.nova.app.core.model.DiscoverUiState
import com.nova.app.core.model.DiscoveryCandidate
import com.nova.app.core.model.EventItem
import com.nova.app.core.model.HomeUiState
import com.nova.app.core.model.LaunchUiState
import com.nova.app.core.model.MessagesUiState
import com.nova.app.core.model.NotificationItem
import com.nova.app.core.model.ProfileUiState
import com.nova.app.core.model.SafetyItem
import com.nova.app.core.model.SessionState
import com.nova.app.core.model.UserCard
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
import com.nova.app.core.backend.payloadString
import com.nova.app.core.backend.toChatMessage
import com.nova.app.core.backend.toChatThread
import com.nova.app.core.backend.toDiscoveryCandidate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    suspend fun refreshDiscover()
    suspend fun applyDiscoverFilters(gender: String, minAge: Int, maxAge: Int)
    suspend fun likeCandidate()
    suspend fun superLikeCandidate()
    suspend fun skipCandidate()
    suspend fun saveCandidate()
    suspend fun pokeCandidate()
    suspend fun clearDiscoverMessage()
    suspend fun refreshMessages()
    suspend fun markVisibleChatThreadsSeen()
    suspend fun openChatThread(thread: ChatThread)
    suspend fun loadMoreChatMessages()
    suspend fun sendMessage(text: String, attachment: ChatAttachmentDraft? = null)
    suspend fun retryMessage(messageId: String)
    suspend fun setChatTyping(typing: Boolean)
    suspend fun deleteCurrentThreadForMe()

    /** Hides the conversation history for the current user only; the other side keeps it. */
    suspend fun deleteThreadForMe(threadId: String): Boolean
    suspend fun deleteMessageForMe(messageId: String)
    suspend fun recallMessage(messageId: String)
    suspend fun editMessage(messageId: String, text: String)
    suspend fun uploadProfileImage(uri: Uri, fileName: String, mimeType: String, title: String): String?
    suspend fun toggleTopic(topicId: String)
    suspend fun joinEvent(eventId: String)
    suspend fun refreshCommunity(tab: String = "for_you", cursor: String? = null, refresh: Boolean = false, size: Int = 10)
    suspend fun createCommunityPost(draft: CreatePostDraft): Boolean
    suspend fun likeCommunityPost(postId: String, liked: Boolean = true)
    suspend fun commentCommunityPost(postId: String, text: String)
    suspend fun shareCommunityPost(postId: String, target: String = "profile", recipientUserId: String? = null, copyLink: Boolean = true)
    suspend fun logout()
    fun applyBackendSession(session: BackendSession?)
    fun applyRealtimeEvent(event: BackendRealtimeEvent, currentUserId: String?)
}

class DefaultNovaRepository : NovaRepository {
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val sessionState = MutableStateFlow(SessionState())
    private val settingsState = MutableStateFlow(defaultSettings())
    private val discoverState = MutableStateFlow(defaultDiscoverState())
    private val homeState = MutableStateFlow(defaultHomeState())
    private val messagesState = MutableStateFlow(defaultMessagesState())
    private val chatState = MutableStateFlow(defaultChatState())
    private val communityState = MutableStateFlow(defaultCommunityState())
    private val profileState = MutableStateFlow(defaultProfileState(settingsState.value))
    private var backendCurrentUserId: String? = null
    private val seenDiscoverCandidateIds = linkedSetOf<String>()

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
        // Diamond balance and the active VIP tier live in the commerce wallet, not in /me.
        runCatching { runtime.fetchCommerceMe() }.getOrNull()?.let { wallet ->
            profileState.update { current ->
                current.copy(
                    diamonds = wallet.diamondBalance.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    user = if (wallet.vipActive) {
                        current.user.copy(
                            vipTierId = wallet.vipTierId ?: current.user.vipTierId,
                            vipTierName = wallet.vipTierName ?: current.user.vipTierName,
                        )
                    } else {
                        current.user
                    },
                )
            }
        }
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

    override suspend fun refreshDiscover() {
        val state = discoverState.value
        loadDiscoverFromBackend(
            gender = state.selectedGender,
            minAge = state.minAge,
            maxAge = state.maxAge,
            excludeSeen = false,
        )
    }

    override suspend fun applyDiscoverFilters(gender: String, minAge: Int, maxAge: Int) {
        seenDiscoverCandidateIds.clear()
        discoverState.update {
            it.copy(
                selectedGender = gender,
                minAge = minAge,
                maxAge = maxAge,
                queue = emptyList(),
                activeIndex = 0,
                loading = true,
                error = null,
                pokeMessage = null,
            )
        }
        loadDiscoverFromBackend(gender, minAge, maxAge, excludeSeen = false)
    }

    override suspend fun likeCandidate() {
        val current = discoverState.value.activeCandidate() ?: return
        runCatching { BackendRuntimeRegistry.runtime?.swipeDiscoverCandidate(current.candidateKey(), "right") }
        discoverState.update { state ->
            seenDiscoverCandidateIds += current.candidateKey()
            val next = nextLocalDiscoverQueue(state)
            state.copy(queue = next, liked = state.liked + 1, activeIndex = 0)
        }
        refillDiscoverIfNeeded()
    }

    override suspend fun superLikeCandidate() {
        val current = discoverState.value.activeCandidate() ?: return
        runCatching { BackendRuntimeRegistry.runtime?.swipeDiscoverCandidate(current.candidateKey(), "super") }
        discoverState.update { state ->
            seenDiscoverCandidateIds += current.candidateKey()
            val next = nextLocalDiscoverQueue(state)
            state.copy(queue = next, superLiked = state.superLiked + 1, activeIndex = 0)
        }
        refillDiscoverIfNeeded()
    }

    override suspend fun skipCandidate() {
        val current = discoverState.value.activeCandidate() ?: return
        runCatching { BackendRuntimeRegistry.runtime?.swipeDiscoverCandidate(current.candidateKey(), "left") }
        discoverState.update { state ->
            seenDiscoverCandidateIds += current.candidateKey()
            val next = nextLocalDiscoverQueue(state)
            state.copy(queue = next, skipped = state.skipped + 1, activeIndex = 0)
        }
        refillDiscoverIfNeeded()
    }

    override suspend fun saveCandidate() {
        discoverState.update { it.copy(saved = it.saved + 1) }
    }

    override suspend fun pokeCandidate() {
        val current = discoverState.value.activeCandidate() ?: return
        val response = runCatching {
            BackendRuntimeRegistry.runtime?.pokeDiscoverCandidate(current.candidateKey())
        }.getOrNull()
        discoverState.update { state ->
            state.copy(
                pokeMessage = response?.message ?: "Poke sent",
                error = if (response == null && BackendRuntimeRegistry.runtime?.currentSession() != null) "Unable to send poke" else null,
            )
        }
    }

    override suspend fun clearDiscoverMessage() {
        discoverState.update { it.copy(pokeMessage = null) }
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

    override suspend fun markVisibleChatThreadsSeen() {
        val unreadThreadIds = messagesState.value.threads
            .filter { it.unreadCount > 0 }
            .map { it.id }
        if (unreadThreadIds.isEmpty()) {
            return
        }
        messagesState.update { state ->
            state.copy(
                threads = state.threads.map { thread ->
                    if (thread.unreadCount > 0) thread.copy(unreadCount = 0) else thread
                }
            )
        }
        val runtime = BackendRuntimeRegistry.runtime ?: return
        unreadThreadIds.forEach { threadId ->
            runCatching { runtime.markThreadRead(threadId) }
        }
    }

    override suspend fun openChatThread(thread: ChatThread) {
        val runtime = BackendRuntimeRegistry.runtime
        val session = runtime?.currentSession()
        val requestedThreadId = thread.id
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
            if (current.thread.id != requestedThreadId && current.thread.id != backendThread.id) {
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
        runCatching { runtime.markThreadRead(backendThread.id) }
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
        val outgoing = chatState.value
        if (outgoing.thread.id.isBlank()) {
            return
        }
        val now = Instant.now()
        val pendingId = "pending-${now.toEpochMilli()}"
        val pendingMessage = ChatMessage(
            id = pendingId,
            text = text.trim(),
            sentByMe = true,
            timeLabel = timeLabel(now),
            createdAt = now.toString(),
            isVoice = attachment?.kind == ChatAttachmentKind.Audio,
            attachmentKind = attachment?.kind,
            attachmentUrl = attachment?.uri?.toString(),
            attachmentPreviewUrl = attachment?.previewUri?.toString(),
            attachmentMimeType = attachment?.mimeType,
            attachmentName = attachment?.name,
            attachmentDurationSeconds = attachment?.durationSeconds,
            attachmentWidth = attachment?.width,
            attachmentHeight = attachment?.height,
            status = "SENDING",
        )
        enqueueOutgoingMessage(outgoing.thread, pendingMessage, previewTextForOutgoing(text, attachment))
        deliverPendingMessage(outgoing.thread, pendingMessage, attachment)
    }

    override suspend fun retryMessage(messageId: String) {
        val outgoing = chatState.value
        val failedMessage = outgoing.messages.firstOrNull {
            it.id == messageId && it.status.equals("FAILED", ignoreCase = true)
        } ?: return
        val now = Instant.now()
        val retryingMessage = failedMessage.copy(
            status = "SENDING",
            timeLabel = timeLabel(now),
            createdAt = now.toString(),
            isRead = false,
        )
        val attachment = retryingMessage.toRetryAttachmentDraft()
        enqueueOutgoingMessage(outgoing.thread, retryingMessage, previewTextForMessage(retryingMessage, attachment))
        deliverPendingMessage(outgoing.thread, retryingMessage, attachment)
    }

    private fun enqueueOutgoingMessage(thread: ChatThread, message: ChatMessage, previewText: String) {
        chatState.update { state ->
            if (state.thread.id == thread.id) {
                state.copy(
                    thread = state.thread.copy(lastMessage = previewText, unreadCount = 0, typing = false),
                    messages = state.messages.upsertNewest(message),
                    typing = false,
                )
            } else {
                state
            }
        }
        messagesState.update { state ->
            val existingThread = state.threads.firstOrNull { it.id == thread.id }
            val mergedThread = (existingThread ?: thread).copy(
                lastMessage = previewText,
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

    private suspend fun deliverPendingMessage(
        thread: ChatThread,
        pendingMessage: ChatMessage,
        attachment: ChatAttachmentDraft?,
    ) {
        val runtime = BackendRuntimeRegistry.runtime ?: run {
            markMessageFailed(thread.id, pendingMessage.id)
            return
        }
        val session = runtime.currentSession() ?: run {
            markMessageFailed(thread.id, pendingMessage.id)
            return
        }

        runCatching { runtime.setTyping(thread.id, false) }
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
            }.getOrNull() ?: run {
                markMessageFailed(thread.id, pendingMessage.id)
                return
            }
            BackendMessageAttachment(
                url = uploaded.url,
                previewUrl = uploaded.previewUrl,
                mimeType = uploaded.mimeType,
                name = draft.name,
                kind = draft.kind,
                durationSeconds = draft.durationSeconds,
                width = draft.width,
                height = draft.height,
            )
        }
        val sent = runCatching {
            runtime.sendMessage(thread.id, pendingMessage.text.trim(), backendAttachment)
        }.getOrNull() ?: run {
            markMessageFailed(thread.id, pendingMessage.id)
            return
        }
        val sentMessage = sent.toChatMessage(session.userId)
        val message = sentMessage.copy(
            attachmentWidth = sentMessage.attachmentWidth ?: pendingMessage.attachmentWidth,
            attachmentHeight = sentMessage.attachmentHeight ?: pendingMessage.attachmentHeight,
        )
        val previewText = previewTextForMessage(pendingMessage, attachment)
        val updatedThread = thread.copy(lastMessage = previewText, unreadCount = 0, typing = false)
        chatState.update { state ->
            if (state.thread.id != thread.id) {
                state
            } else {
                state.copy(
                    thread = updatedThread,
                    messages = state.messages.filterNot { it.id == pendingMessage.id }.upsertNewest(message),
                    typing = false,
                )
            }
        }
        messagesState.update { state ->
            val existingThread = state.threads.firstOrNull { it.id == thread.id }
            val mergedThread = (existingThread ?: updatedThread).copy(
                lastMessage = previewText,
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

    override suspend fun setChatTyping(typing: Boolean) {
        val runtime = BackendRuntimeRegistry.runtime ?: return
        val threadId = chatState.value.thread.id
        if (threadId.isBlank()) {
            return
        }
        runCatching { runtime.setTyping(threadId, typing) }
    }

    override suspend fun uploadProfileImage(
        uri: Uri,
        fileName: String,
        mimeType: String,
        title: String,
    ): String? {
        val runtime = BackendRuntimeRegistry.runtime ?: return null
        return runCatching {
            runtime.uploadMedia(
                BackendMediaUploadRequest(
                    uri = uri,
                    fileName = fileName,
                    title = title,
                    mimeType = mimeType,
                    kind = ChatAttachmentKind.Image,
                )
            )?.url
        }.getOrNull()
    }

    override suspend fun deleteCurrentThreadForMe() {
        deleteThreadForMe(chatState.value.thread.id)
    }

    override suspend fun deleteThreadForMe(threadId: String): Boolean {
        val runtime = BackendRuntimeRegistry.runtime ?: return false
        val deleted = runCatching { runtime.deleteThreadForMe(threadId) }.getOrDefault(false)
        if (deleted) {
            messagesState.update { state ->
                state.copy(threads = state.threads.filterNot { it.id == threadId })
            }
            chatState.update { state ->
                if (state.thread.id == threadId) {
                    state.copy(messages = emptyList(), typing = false, hasMore = false, nextCursor = null)
                } else {
                    state
                }
            }
        }
        return deleted
    }

    override suspend fun deleteMessageForMe(messageId: String) {
        val runtime = BackendRuntimeRegistry.runtime ?: return
        val threadId = chatState.value.thread.id
        val deleted = runCatching { runtime.deleteMessageForMe(threadId, messageId) }.getOrDefault(false)
        if (deleted) {
            chatState.update { state ->
                if (state.thread.id == threadId) {
                    state.copy(messages = state.messages.filterNot { it.id == messageId })
                } else {
                    state
                }
            }
        }
    }

    override suspend fun recallMessage(messageId: String) {
        val runtime = BackendRuntimeRegistry.runtime ?: return
        val session = runtime.currentSession() ?: return
        val threadId = chatState.value.thread.id
        val recalled = runCatching { runtime.recallMessage(threadId, messageId) }.getOrNull() ?: return
        val message = recalled.toChatMessage(session.userId)
        chatState.update { state ->
            if (state.thread.id == threadId) {
                state.copy(messages = state.messages.upsertNewest(message))
            } else {
                state
            }
        }
    }

    override suspend fun editMessage(messageId: String, text: String) {
        val runtime = BackendRuntimeRegistry.runtime ?: return
        val session = runtime.currentSession() ?: return
        val threadId = chatState.value.thread.id
        val edited = runCatching { runtime.editMessage(threadId, messageId, text.trim()) }.getOrNull() ?: return
        val message = edited.toChatMessage(session.userId)
        chatState.update { state ->
            if (state.thread.id == threadId) {
                state.copy(messages = state.messages.upsertNewest(message))
            } else {
                state
            }
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

    override suspend fun createCommunityPost(draft: CreatePostDraft): Boolean {
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
                return true
            }
            return false
        }

        return false
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
        runCatching { BackendRuntimeRegistry.runtime?.logout() }
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

    private suspend fun refillDiscoverIfNeeded() {
        val state = discoverState.value
        if (state.queue.size > 1) {
            return
        }
        loadDiscoverFromBackend(state.selectedGender, state.minAge, state.maxAge, excludeSeen = true)
    }

    private suspend fun loadDiscoverFromBackend(
        gender: String,
        minAge: Int,
        maxAge: Int,
        excludeSeen: Boolean,
    ): Boolean {
        val runtime = BackendRuntimeRegistry.runtime ?: run {
            discoverState.update { it.copy(loading = false, error = "Backend is not connected") }
            return false
        }
        discoverState.update { it.copy(loading = true, error = null) }
        val backendGender = gender.takeUnless { it.equals("Both", ignoreCase = true) || it.equals("All", ignoreCase = true) }
        var response = runCatching {
            runtime.fetchDiscover(
                gender = backendGender,
                minAge = minAge,
                maxAge = maxAge,
                excludeIds = if (excludeSeen) seenDiscoverCandidateIds.toList() else emptyList(),
            )
        }.getOrNull() ?: run {
            discoverState.update { it.copy(loading = false, error = "Unable to load discover") }
            return false
        }
        if (excludeSeen && response.items.isEmpty() && seenDiscoverCandidateIds.isNotEmpty()) {
            seenDiscoverCandidateIds.clear()
            response = runCatching {
                runtime.fetchDiscover(
                    gender = backendGender,
                    minAge = minAge,
                    maxAge = maxAge,
                    excludeIds = emptyList(),
                )
            }.getOrNull() ?: response
        }
        val mapped = response.items.map { it.toDiscoveryCandidate() }.shuffled()
        discoverState.update { state ->
            val existingIds = state.queue.mapTo(hashSetOf()) { it.candidateKey() }
            val merged = if (excludeSeen) {
                state.queue + mapped.filterNot { candidate ->
                    existingIds.contains(candidate.candidateKey()) || seenDiscoverCandidateIds.contains(candidate.candidateKey())
                }
            } else {
                mapped
            }
            state.copy(
                queue = merged,
                activeIndex = 0,
                loading = false,
                error = null,
            )
        }
        return true
    }

    private fun nextLocalDiscoverQueue(state: DiscoverUiState): List<DiscoveryCandidate> {
        return state.queue.drop(1)
    }

    private fun DiscoverUiState.activeCandidate(): DiscoveryCandidate? {
        return queue.getOrNull(activeIndex.coerceIn(0, queue.lastIndex.coerceAtLeast(0)))
    }

    private fun DiscoveryCandidate.candidateKey(): String {
        return candidateId.ifBlank { user.id }
    }

    private fun List<DiscoveryCandidate>.filterByDiscoverFilters(gender: String, minAge: Int, maxAge: Int): List<DiscoveryCandidate> {
        val normalizedGender = gender.trim().lowercase(Locale.ROOT)
        val lowerAge = minAge.coerceAtLeast(0)
        val upperAge = maxAge.coerceAtLeast(lowerAge)
        return filter { candidate ->
            val genderMatches = normalizedGender.isBlank() ||
                    normalizedGender == "both" ||
                    normalizedGender == "all" ||
                    candidate.user.gender.lowercase(Locale.ROOT).contains(normalizedGender)
            genderMatches && candidate.user.age in lowerAge..upperAge
        }
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
                    publicId = session.publicId.ifBlank { current.user.publicId },
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
            BackendRealtimeEventType.MESSAGE_UPDATED,
            BackendRealtimeEventType.MESSAGE_RECALLED -> applyRemoteMessage(event, viewerUserId)

            BackendRealtimeEventType.THREAD_READ -> applyThreadRead(event, viewerUserId)
            BackendRealtimeEventType.THREAD_TYPING -> applyThreadTyping(event)
            BackendRealtimeEventType.USER_PRESENCE -> applyUserPresence(event)
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
                safety = defaultSafety(settings),
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
                publicId = authorPublicId,
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
            createdAt = createdAt,
            mine = mine,
            mentionedUserIds = mentionedUserIds,
            mentions = mentions.map { mention ->
                CommunityMention(
                    userId = mention.userId,
                    displayName = mention.displayName,
                    username = mention.username,
                    avatarUrl = mention.avatarUrl,
                )
            },
        )
    }

    private fun BackendCommunityPost.toCommunityPost(): CommunityPost {
        return CommunityPost(
            id = id,
            topic = topicId,
            author = toUserCard(
                id = authorId,
                publicId = authorPublicId,
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
            mentions = mentions.map { mention ->
                CommunityMention(
                    userId = mention.userId,
                    displayName = mention.displayName,
                    username = mention.username,
                    avatarUrl = mention.avatarUrl,
                )
            },
            likes = likes,
            comments = comments,
            commentsPreview = commentsPreview.map { it.toCommunityComment() },
            shares = shares,
            likedByMe = likedByMe,
            sharedByMe = sharedByMe,
            timeLabel = timeLabel,
            createdAt = createdAt,
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
            // Only the conversation that is on screen right now counts as read; the last
            // opened chat stays in chatState after leaving it, so it can't be used here.
            val viewing = threadId == com.nova.app.core.backend.ActiveChat.threadId
            val nextUnreadCount = when {
                message.sentByMe -> 0
                viewing -> 0
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
                // First message of a conversation we don't have yet: reload the list.
                repositoryScope.launch { runCatching { refreshMessages() } }
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
                        if (message.sentByMe) message.copy(isRead = true, status = "SEEN") else message
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

    private fun applyUserPresence(event: BackendRealtimeEvent) {
        val userId = event.payloadString("userId", event.actorUserId.orEmpty())
        if (userId.isBlank()) {
            return
        }
        val online = event.payloadBoolean("online")
        messagesState.update { state ->
            val threads = state.threads.map { thread ->
                if (thread.user.id == userId) {
                    thread.copy(online = online, user = thread.user.copy(online = online))
                } else {
                    thread
                }
            }
            state.copy(
                threads = threads,
                onlineNow = threads.count { it.online },
            )
        }
        chatState.update { state ->
            if (state.thread.user.id == userId) {
                state.copy(thread = state.thread.copy(online = online, user = state.thread.user.copy(online = online)))
            } else {
                state
            }
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
        val kind = event.payload["kind"] ?: "System"
        if (kind.equals("MESSAGE", ignoreCase = true) || kind.equals("CALL", ignoreCase = true)) {
            return
        }
        val notification = NotificationItem(
            id = event.payload["notificationId"] ?: event.id,
            title = title,
            description = body,
            timeLabel = event.payload["timeLabel"] ?: "Now",
            type = kind,
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

    private fun previewTextForMessage(message: ChatMessage, attachment: ChatAttachmentDraft?): String {
        return previewTextForOutgoing(message.text, attachment)
    }

    private fun ChatMessage.toRetryAttachmentDraft(): ChatAttachmentDraft? {
        val kind = attachmentKind ?: if (isVoice) ChatAttachmentKind.Audio else return null
        val source = attachmentUrl ?: attachmentPreviewUrl ?: return null
        return ChatAttachmentDraft(
            uri = Uri.parse(source),
            kind = kind,
            name = attachmentName ?: when (kind) {
                ChatAttachmentKind.Image -> "image.jpg"
                ChatAttachmentKind.Video -> "video.mp4"
                ChatAttachmentKind.Audio -> "voice.m4a"
                ChatAttachmentKind.File -> "file"
            },
            mimeType = attachmentMimeType ?: when (kind) {
                ChatAttachmentKind.Image -> "image/jpeg"
                ChatAttachmentKind.Video -> "video/mp4"
                ChatAttachmentKind.Audio -> "audio/mp4"
                ChatAttachmentKind.File -> "application/octet-stream"
            },
            durationSeconds = attachmentDurationSeconds,
            previewUri = attachmentPreviewUrl?.let(Uri::parse),
            width = attachmentWidth,
            height = attachmentHeight,
        )
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

    private fun markMessageFailed(threadId: String, messageId: String) {
        chatState.update { state ->
            if (state.thread.id != threadId) {
                state
            } else {
                state.copy(
                    messages = state.messages.map { message ->
                        if (message.id == messageId) message.copy(status = "FAILED") else message
                    },
                    typing = false,
                )
            }
        }
    }

    private fun timeLabel(instant: Instant): String {
        return DateTimeFormatter.ofPattern("HH:mm")
            .format(instant.atZone(ZoneId.systemDefault()))
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
        stories = emptyList(),
        feed = emptyList(),
        featured = emptyDiscoveryCandidate(),
        events = emptyList(),
        communities = emptyList(),
        suggestions = emptyList(),
    )

    private fun defaultDiscoverState() = DiscoverUiState(
        queue = emptyList(),
        activeIndex = 0,
    )

    private fun defaultMessagesState() = MessagesUiState(
        threads = emptyList(),
        onlineNow = 0,
        filters = listOf("All", "Matches", "Voice", "Groups"),
        searchHint = "Search people",
    )

    private fun defaultChatState() = ChatUiState(
        thread = ChatThread(
            id = "",
            user = emptyUserCard(),
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
        topics = emptyList(),
        posts = emptyList(),
        events = emptyList(),
        trending = emptyList(),
    )

    private fun defaultProfileState(settings: AppSettings) = ProfileUiState(
        user = emptyUserCard(),
        bio = "",
        featuredPhotos = emptyList(),
        interests = emptyList(),
        posts = emptyList(),
        diamonds = 0,
        prompts = emptyList(),
        badges = emptyList(),
        stats = emptyList(),
        settings = settings,
        wallet = emptyList(),
        safety = defaultSafety(settings),
        plans = emptyList(),
        notifications = emptyList(),
        genericScreens = emptyList(),
        adminMetrics = emptyList(),
        compatibility = emptyList(),
        filters = emptyList(),
    )

    private fun defaultSafety(settings: AppSettings) = listOf(
        SafetyItem("Report", "Report suspicious or abusive behavior", "Open", false),
        SafetyItem("Block", "Block a user instantly", "Manage", false),
        SafetyItem("Emergency", "Quick access to emergency contacts", "Setup", false),
        SafetyItem("Location Share", "Share live location during dates", "On", settings.locationSharingEnabled),
        SafetyItem("Photo Verification", "Verify your photos for trust", "On", settings.photoVerificationEnabled),
        SafetyItem("Video Verification", "Record a short verification clip", "Off", settings.videoVerificationEnabled),
    )

    private fun emptyUserCard(name: String = "") = UserCard(
        id = "",
        name = name,
        age = 0,
        photoUrl = "",
        verified = false,
        distanceKm = null,
        online = false,
        city = "",
        vipTierId = null,
        vipTierName = null,
        premium = false,
        gender = "Not specified",
        publicId = "",
    )

    private fun emptyDiscoveryCandidate() = DiscoveryCandidate(
        candidateId = "",
        user = emptyUserCard(),
        bio = "",
        compatibility = 0,
        commonInterests = emptyList(),
        iceBreaker = "",
        mutualFriends = 0,
        musicTaste = "",
        height = "",
        job = "",
        relationshipGoal = "",
        gallery = emptyList(),
        voiceIntro = false,
        videoIntro = false,
    )

    private fun syncCommunity() {
        communityState.update { state ->
            state.copy(events = state.events.map { it.copy(joined = it.joined) })
        }
    }
}
