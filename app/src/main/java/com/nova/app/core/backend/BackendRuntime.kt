package com.nova.app.core.backend

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nova.app.core.model.CallDirection
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.model.CallType
import com.nova.app.core.model.ChatAttachmentKind
import com.nova.app.core.backend.BackendCommunityCommentRequest
import com.nova.app.core.backend.BackendCommunityFeed
import com.nova.app.core.backend.BackendCommunityLikeRequest
import com.nova.app.core.backend.BackendCommunityPost
import com.nova.app.core.backend.BackendCommunityPostRequest
import com.nova.app.core.backend.BackendCommunityShareRequest
import com.nova.app.core.backend.BackendCommunityShareResponse
import com.nova.app.core.backend.BackendCommunityTagSuggestion
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

interface BackendRuntime {
    val session: StateFlow<BackendSession?>
    val events: SharedFlow<BackendRealtimeEvent>

    fun initialize(context: Context)
    suspend fun signIn(provider: BackendAuthProvider, providerToken: String? = null): BackendSession
    fun signOut()
    suspend fun fetchMe(): BackendProfile?
    suspend fun fetchPublicProfile(userId: String): BackendProfile?
    suspend fun fetchProfileRelations(userId: String, relation: String, page: Int = 0, size: Int = 50): BackendProfilePage?
    suspend fun searchUsers(query: String, page: Int = 0, size: Int = 20, gender: String? = null, interest: String? = null): BackendSearchPage?
    suspend fun fetchNotifications(): List<BackendNotification>?
    suspend fun markNotificationRead(notificationId: String): List<BackendNotification>?
    suspend fun fetchCommunityFeed(tab: String = "for_you", cursor: String? = null, refresh: Boolean = false, size: Int = 10): BackendCommunityFeed?
    suspend fun fetchProfilePosts(userId: String, size: Int = 30): List<BackendCommunityPost>?
    suspend fun createCommunityPost(request: BackendCommunityPostRequest): BackendCommunityPost?
    suspend fun likeCommunityPost(postId: String, liked: Boolean = true): BackendCommunityPost?
    suspend fun commentCommunityPost(postId: String, request: BackendCommunityCommentRequest): BackendCommunityPost?
    suspend fun shareCommunityPost(postId: String, request: BackendCommunityShareRequest): BackendCommunityShareResponse?
    suspend fun fetchCommunityTags(query: String = "", limit: Int = 8): List<BackendCommunityTagSuggestion>?
    suspend fun fetchRealtimeConfig(): BackendRealtimeConfig?
    suspend fun uploadMedia(request: BackendMediaUploadRequest): BackendMediaAsset?
    suspend fun updateProfile(request: BackendProfileUpdateRequest): BackendProfile?
    suspend fun toggleFollow(userId: String, followed: Boolean): BackendProfile?
    suspend fun fetchThreads(): List<BackendChatThread>?
    suspend fun fetchThread(threadId: String, limit: Int = 20, before: String? = null): BackendThreadDetailResponse?
    suspend fun startCall(threadId: String, callType: CallType, direction: CallDirection = CallDirection.Outgoing, peerUserId: String = ""): BackendCallSession?
    suspend fun answerCall(callId: String): BackendCallSession?
    suspend fun endCall(callId: String, reason: CallEndReason): BackendCallSession?
    suspend fun minimizeCall(callId: String, minimized: Boolean): BackendCallSession?
    suspend fun sendCallSignal(signal: BackendCallSignal): Boolean
    suspend fun sendMessage(threadId: String, text: String, attachment: BackendMessageAttachment? = null): BackendChatMessage?
    suspend fun setTyping(threadId: String, typing: Boolean)
    suspend fun markThreadRead(threadId: String)
    fun onPushTokenRefreshed(token: String)
    fun currentSession(): BackendSession?
}

class DefaultBackendRuntime(
    private val client: NovaBackendClient = NovaBackendClient(),
) : BackendRuntime {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _session = MutableStateFlow<BackendSession?>(null)
    private val _events = MutableSharedFlow<BackendRealtimeEvent>(extraBufferCapacity = 64)
    private val initialized = AtomicBoolean(false)
    private val pendingCallSignals = mutableListOf<BackendCallSignal>()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var realtimeSocket: WebSocket? = null

    override val session: StateFlow<BackendSession?> = _session.asStateFlow()
    override val events: SharedFlow<BackendRealtimeEvent> = _events.asSharedFlow()

    override fun initialize(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            appContext = context.applicationContext
            val persisted = BackendSessionStore.loadSession(appContext!!)
            _session.value = persisted
            if (persisted != null) {
                reconnect(persisted)
                syncPushToken(persisted)
            }
            BackendRuntimeRegistry.runtime = this
        }
    }

    override suspend fun signIn(provider: BackendAuthProvider, providerToken: String?): BackendSession {
        val context = requireContext()
        val deviceId = BackendSessionStore.loadDeviceId(context)
        val session = client.login(provider, deviceId, BackendConfig.appVersion, providerToken)
        BackendSessionStore.saveSession(context, session)
        _session.value = session
        reconnect(session)
        syncPushToken(session)
        return session
    }

    override fun signOut() {
        realtimeSocket?.cancel()
        realtimeSocket = null
        synchronized(pendingCallSignals) {
            pendingCallSignals.clear()
        }
        _session.value = null
        appContext?.let { BackendSessionStore.clearSession(it) }
    }

    override suspend fun fetchMe(): BackendProfile? {
        val session = _session.value ?: return null
        val profile = runCatching {
            client.fetchMe(session.accessToken)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to fetch profile: ${throwable.message}")
            null
        }
        if (profile != null) {
            persistSessionFromProfile(profile)
        }
        return profile
    }

    override suspend fun fetchPublicProfile(userId: String): BackendProfile? {
        val session = _session.value ?: return null
        return runCatching {
            client.fetchPublicProfile(session.accessToken, userId)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to fetch public profile: ${throwable.message}")
            null
        }
    }

    override suspend fun fetchProfileRelations(userId: String, relation: String, page: Int, size: Int): BackendProfilePage? {
        val session = _session.value ?: return null
        return runCatching {
            client.fetchProfileRelations(session.accessToken, userId, relation, page, size)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to fetch profile relations: ${throwable.message}")
            null
        }
    }

    override suspend fun searchUsers(
        query: String,
        page: Int,
        size: Int,
        gender: String?,
        interest: String?,
    ): BackendSearchPage? {
        val session = _session.value ?: return null
        return runCatching {
            client.searchUsers(session.accessToken, query, page, size, gender, interest)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to search users: ${throwable.message}")
            null
        }
    }

    override suspend fun fetchNotifications(): List<BackendNotification>? {
        val session = _session.value ?: return null
        return runCatching {
            client.fetchNotifications(session.accessToken)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to fetch notifications: ${throwable.message}")
            null
        }
    }

    override suspend fun markNotificationRead(notificationId: String): List<BackendNotification>? {
        val session = _session.value ?: return null
        return runCatching {
            client.markNotificationRead(session.accessToken, notificationId)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to mark notification read: ${throwable.message}")
            null
        }
    }

    override suspend fun fetchCommunityFeed(tab: String, cursor: String?, refresh: Boolean, size: Int): BackendCommunityFeed? {
        val session = _session.value ?: return null
        return runCatching {
            client.fetchCommunityFeed(session.accessToken, tab, cursor, refresh, size)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to fetch community feed: ${throwable.message}")
            null
        }
    }

    override suspend fun fetchProfilePosts(userId: String, size: Int): List<BackendCommunityPost>? {
        val session = _session.value ?: return null
        return runCatching {
            client.fetchProfilePosts(session.accessToken, userId, size)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to fetch profile posts: ${throwable.message}")
            null
        }
    }

    override suspend fun createCommunityPost(request: BackendCommunityPostRequest): BackendCommunityPost? {
        val session = _session.value ?: return null
        return runCatching {
            client.createCommunityPost(session.accessToken, request)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to create community post: ${throwable.message}")
            null
        }
    }

    override suspend fun likeCommunityPost(postId: String, liked: Boolean): BackendCommunityPost? {
        val session = _session.value ?: return null
        return runCatching {
            client.likeCommunityPost(session.accessToken, postId, liked)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to like community post: ${throwable.message}")
            null
        }
    }

    override suspend fun commentCommunityPost(postId: String, request: BackendCommunityCommentRequest): BackendCommunityPost? {
        val session = _session.value ?: return null
        return runCatching {
            client.commentCommunityPost(session.accessToken, postId, request)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to comment community post: ${throwable.message}")
            null
        }
    }

    override suspend fun shareCommunityPost(postId: String, request: BackendCommunityShareRequest): BackendCommunityShareResponse? {
        val session = _session.value ?: return null
        return runCatching {
            client.shareCommunityPost(session.accessToken, postId, request)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to share community post: ${throwable.message}")
            null
        }
    }

    override suspend fun fetchCommunityTags(query: String, limit: Int): List<BackendCommunityTagSuggestion>? {
        val session = _session.value ?: return null
        return runCatching {
            client.fetchCommunityTags(session.accessToken, query, limit)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to fetch community tags: ${throwable.message}")
            null
        }
    }

    override suspend fun fetchRealtimeConfig(): BackendRealtimeConfig? {
        val session = _session.value ?: return null
        return runCatching {
            client.fetchRealtimeConfig(session.accessToken)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to fetch realtime config: ${throwable.message}")
            null
        }
    }

    override suspend fun uploadMedia(request: BackendMediaUploadRequest): BackendMediaAsset? {
        val session = _session.value ?: return null
        val context = requireContext()
        return runCatching {
            val bytes = readBytes(context, request.uri) ?: return@runCatching null
            client.uploadMedia(
                accessToken = session.accessToken,
                fileName = request.fileName,
                mimeType = request.mimeType,
                kind = request.kind.name.lowercase(Locale.ROOT),
                title = request.title,
                fileBytes = bytes,
                previewUrl = request.previewUrl,
            )
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to upload media: ${throwable.message}")
            null
        }
    }

    override suspend fun updateProfile(request: BackendProfileUpdateRequest): BackendProfile? {
        val session = _session.value ?: return null
        val profile = runCatching {
            client.updateProfile(session.accessToken, request)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to update profile: ${throwable.message}")
            null
        }
        if (profile != null) {
            persistSessionFromProfile(profile, forceProfileComplete = true)
        } else {
            persistSessionFromRequest(session, request)
        }
        return profile
    }

    override suspend fun toggleFollow(userId: String, followed: Boolean): BackendProfile? {
        val session = _session.value ?: return null
        return runCatching {
            client.toggleFollow(session.accessToken, userId, followed)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to toggle follow: ${throwable.message}")
            null
        }
    }

    override suspend fun fetchThreads(): List<BackendChatThread>? {
        val session = _session.value ?: return null
        return runCatching {
            client.fetchThreads(session.accessToken)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to fetch threads: ${throwable.message}")
            null
        }
    }

    override suspend fun fetchThread(threadId: String, limit: Int, before: String?): BackendThreadDetailResponse? {
        val session = _session.value ?: return null
        return runCatching {
            client.fetchThread(session.accessToken, threadId, limit, before)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to fetch thread: ${throwable.message}")
            null
        }
    }

    override suspend fun startCall(
        threadId: String,
        callType: CallType,
        direction: CallDirection,
        peerUserId: String,
    ): BackendCallSession? {
        val session = _session.value ?: return null
        return runCatching {
            client.startCall(session.accessToken, threadId, callType, direction, peerUserId)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to start call: ${throwable.message}")
            null
        }
    }

    override suspend fun answerCall(callId: String): BackendCallSession? {
        val session = _session.value ?: return null
        return runCatching {
            client.answerCall(session.accessToken, callId)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to answer call: ${throwable.message}")
            null
        }
    }

    override suspend fun endCall(callId: String, reason: CallEndReason): BackendCallSession? {
        val session = _session.value ?: return null
        return runCatching {
            client.endCall(session.accessToken, callId, reason)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to end call: ${throwable.message}")
            null
        }
    }

    override suspend fun minimizeCall(callId: String, minimized: Boolean): BackendCallSession? {
        val session = _session.value ?: return null
        return runCatching {
            client.minimizeCall(session.accessToken, callId, minimized)
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to update call minimize state: ${throwable.message}")
            null
        }
    }

    override suspend fun sendCallSignal(signal: BackendCallSignal): Boolean {
        val session = _session.value ?: return false
        val socket = realtimeSocket ?: run {
            queueCallSignal(signal)
            return false
        }
        return runCatching {
            val sent = socket.send(
                JSONObject()
                    .put("type", "call_signal")
                    .put("targetUserId", signal.targetUserId)
                    .put("threadId", signal.threadId)
                    .put("callId", signal.callId)
                    .put("signalType", signal.signalType)
                    .put("sdpType", signal.sdpType ?: JSONObject.NULL)
                    .put("sdp", signal.sdp ?: JSONObject.NULL)
                    .put("candidate", signal.candidate ?: JSONObject.NULL)
                    .put("sdpMid", signal.sdpMid ?: JSONObject.NULL)
                    .put("sdpMLineIndex", signal.sdpMLineIndex ?: JSONObject.NULL)
                    .put("video", signal.video)
                    .put("fromUserId", session.userId)
                    .toString()
            )
            if (!sent) {
                queueCallSignal(signal)
            }
            sent
        }.getOrElse { throwable ->
            Log.w("NovaBackend", "Failed to send call signal: ${throwable.message}")
            queueCallSignal(signal)
            false
        }
    }

    override suspend fun sendMessage(threadId: String, text: String, attachment: BackendMessageAttachment?): BackendChatMessage? {
        val session = _session.value ?: return null
        return client.sendMessage(session.accessToken, threadId, text, attachment)
    }

    override suspend fun setTyping(threadId: String, typing: Boolean) {
        val session = _session.value ?: return
        client.setTyping(session.accessToken, threadId, typing)
    }

    override suspend fun markThreadRead(threadId: String) {
        val session = _session.value ?: return
        client.markThreadRead(session.accessToken, threadId)
    }

    override fun onPushTokenRefreshed(token: String) {
        val context = appContext ?: return
        BackendSessionStore.savePushToken(context, token)
        val current = _session.value ?: return
        scope.launch {
            runCatching {
                client.registerPushToken(
                    accessToken = current.accessToken,
                    requestModel = BackendDeviceTokenRequest(
                        token = token,
                        deviceId = BackendSessionStore.loadDeviceId(context),
                        appVersion = BackendConfig.appVersion,
                    )
                )
            }.onFailure { throwable ->
                Log.w("NovaBackend", "Failed to register push token: ${throwable.message}")
            }
        }
    }

    override fun currentSession(): BackendSession? = _session.value

    private fun reconnect(session: BackendSession) {
        realtimeSocket?.cancel()
        realtimeSocket = client.openRealtime(
            accessToken = session.accessToken,
            onEvent = { event -> scope.launch { _events.emit(event) } },
            onClosed = { throwable ->
                realtimeSocket = null
                if (throwable != null) {
                    Log.w("NovaBackend", "Realtime closed: ${throwable.message}")
                    scope.launch {
                        delay(2_000)
                        if (_session.value?.accessToken == session.accessToken && realtimeSocket == null) {
                            reconnect(session)
                        }
                    }
                }
            },
        )
        flushPendingCallSignals()
    }

    private fun syncPushToken(session: BackendSession) {
        val context = appContext ?: return
        val pushToken = BackendSessionStore.loadPushToken(context) ?: return
        scope.launch {
            runCatching {
                client.registerPushToken(
                    accessToken = session.accessToken,
                    requestModel = BackendDeviceTokenRequest(
                        token = pushToken,
                        deviceId = BackendSessionStore.loadDeviceId(context),
                        appVersion = BackendConfig.appVersion,
                    )
                )
            }.onFailure { throwable ->
                Log.w("NovaBackend", "Failed to sync push token: ${throwable.message}")
            }
        }
    }

    private fun persistSessionFromProfile(
        profile: BackendProfile,
        forceProfileComplete: Boolean = false,
    ) {
        val current = _session.value ?: return
        val updated = current.copy(
            displayName = profile.displayName.ifBlank { current.displayName },
            avatarUrl = profile.avatarUrl.takeIf { it.isNotBlank() } ?: current.avatarUrl,
            onboardingComplete = profile.onboardingComplete || current.onboardingComplete,
            profileComplete = forceProfileComplete || profile.profileComplete || current.profileComplete,
        )
        if (updated != current) {
            _session.value = updated
            appContext?.let { BackendSessionStore.saveSession(it, updated) }
        }
    }

    private fun persistSessionFromRequest(current: BackendSession, request: BackendProfileUpdateRequest) {
        val updated = current.copy(
            displayName = request.displayName.ifBlank { current.displayName },
            avatarUrl = request.photoUrl?.takeIf { it.isNotBlank() } ?: current.avatarUrl,
            profileComplete = true,
        )
        if (updated != current) {
            _session.value = updated
            appContext?.let { BackendSessionStore.saveSession(it, updated) }
        }
    }

    private fun queueCallSignal(signal: BackendCallSignal) {
        synchronized(pendingCallSignals) {
            pendingCallSignals.add(signal)
            if (pendingCallSignals.size > 64) {
                pendingCallSignals.removeAt(0)
            }
        }
    }

    private fun flushPendingCallSignals() {
        val socket = realtimeSocket ?: return
        val session = _session.value ?: return
        val snapshot = synchronized(pendingCallSignals) {
            if (pendingCallSignals.isEmpty()) {
                return
            }
            pendingCallSignals.toList().also { pendingCallSignals.clear() }
        }
        snapshot.forEach { signal ->
            val sent = runCatching {
                socket.send(
                    JSONObject()
                        .put("type", "call_signal")
                        .put("targetUserId", signal.targetUserId)
                        .put("threadId", signal.threadId)
                        .put("callId", signal.callId)
                        .put("signalType", signal.signalType)
                        .put("sdpType", signal.sdpType ?: JSONObject.NULL)
                        .put("sdp", signal.sdp ?: JSONObject.NULL)
                        .put("candidate", signal.candidate ?: JSONObject.NULL)
                        .put("sdpMid", signal.sdpMid ?: JSONObject.NULL)
                        .put("sdpMLineIndex", signal.sdpMLineIndex ?: JSONObject.NULL)
                        .put("video", signal.video)
                        .put("fromUserId", session.userId)
                        .toString()
                )
            }.getOrDefault(false)
            if (!sent) {
                queueCallSignal(signal)
            }
        }
    }

    private fun requireContext(): Context {
        return appContext ?: throw IllegalStateException("Backend runtime was not initialized")
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            }
        }.getOrNull()
    }
}
