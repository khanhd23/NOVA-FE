package com.nova.app.core.backend

import com.nova.app.core.model.CallDirection
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.model.CallSummaryUiState
import com.nova.app.core.model.CallType
import com.nova.app.core.model.ChatAttachmentKind
import org.json.JSONArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NovaBackendClient(
    private val baseUrl: String = BackendConfig.baseUrl,
    private val okHttpClient: OkHttpClient = defaultClient(),
) {

    suspend fun login(
        provider: BackendAuthProvider,
        deviceId: String,
        appVersion: String,
        providerToken: String? = null,
    ): BackendSession {
        return withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("provider", provider.backendValue)
                .put("providerToken", providerToken?.takeIf { it.isNotBlank() } ?: provider.devToken)
                .put("deviceId", deviceId)
                .put("appVersion", appVersion)
            val json = requestJson(
                method = "POST",
                path = "/api/v1/auth/social/login",
                body = payload,
            )
            val data = json.optJSONObject("data") ?: throw IOException("Login response missing data")
            val tokens = data.optJSONObject("tokens") ?: throw IOException("Login response missing tokens")
            val me = data.optJSONObject("me") ?: throw IOException("Login response missing profile")
            BackendSession(
                accessToken = tokens.optString("accessToken"),
                refreshToken = tokens.optString("refreshToken"),
                userId = me.optString("userId"),
                displayName = me.optString("displayName"),
                avatarUrl = me.optString("avatarUrl").takeIf { it.isNotBlank() },
                onboardingComplete = me.optBoolean("onboardingComplete", false),
                profileComplete = me.optBoolean("profileComplete", false),
            )
        }
    }

    suspend fun fetchMe(accessToken: String): BackendProfile? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "GET",
                path = "/api/v1/me",
                accessToken = accessToken,
            )
            val me = json.optJSONObject("data") ?: return@withContext null
            parseProfile(me)
        }
    }

    suspend fun fetchPublicProfile(accessToken: String, userId: String): BackendProfile? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "GET",
                path = "/api/v1/users/${encode(userId)}",
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseProfile(data)
        }
    }

    suspend fun fetchProfileRelations(accessToken: String, userId: String, relation: String, page: Int = 0, size: Int = 50): BackendProfilePage? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "GET",
                path = "/api/v1/users/${encode(userId)}/relations?type=${encode(relation)}&page=$page&size=$size",
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseProfilePage(data)
        }
    }

    suspend fun searchUsers(
        accessToken: String,
        query: String,
        page: Int = 0,
        size: Int = 20,
        gender: String? = null,
        interest: String? = null,
    ): BackendSearchPage? {
        return withContext(Dispatchers.IO) {
            val queryParams = buildList {
                add("q=${encode(query)}")
                add("page=$page")
                add("size=$size")
                if (!gender.isNullOrBlank()) add("gender=${encode(gender)}")
                if (!interest.isNullOrBlank()) add("interest=${encode(interest)}")
            }.joinToString("&")
            val json = requestJson(
                method = "GET",
                path = "/api/v1/users/search?$queryParams",
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseSearchPage(data)
        }
    }

    suspend fun fetchNotifications(accessToken: String): List<BackendNotification>? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "GET",
                path = "/api/v1/notifications",
                accessToken = accessToken,
            )
            val data = json.optJSONArray("data") ?: json.optJSONObject("data")?.optJSONArray("items") ?: return@withContext emptyList()
            parseNotifications(data)
        }
    }

    suspend fun fetchThreads(accessToken: String): List<BackendChatThread>? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "GET",
                path = "/api/v1/threads",
                accessToken = accessToken,
            )
            val data = json.optJSONArray("data") ?: json.optJSONObject("data")?.optJSONArray("items") ?: return@withContext emptyList()
            parseChatThreads(data)
        }
    }

    suspend fun fetchThread(
        accessToken: String,
        threadId: String,
        limit: Int = 20,
        before: String? = null,
    ): BackendThreadDetailResponse? {
        return withContext(Dispatchers.IO) {
            val queryParams = buildList {
                add("limit=$limit")
                if (!before.isNullOrBlank()) add("before=${encode(before)}")
            }.joinToString("&")
            val json = requestJson(
                method = "GET",
                path = "/api/v1/threads/$threadId?$queryParams",
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseThreadDetail(data)
        }
    }

    suspend fun markNotificationRead(accessToken: String, notificationId: String): List<BackendNotification>? {
        return withContext(Dispatchers.IO) {
            requestJson(
                method = "POST",
                path = "/api/v1/notifications/read",
                body = JSONObject().put("notificationId", notificationId),
                accessToken = accessToken,
            )
            fetchNotifications(accessToken)
        }
    }

    suspend fun fetchCommunityFeed(
        accessToken: String,
        tab: String,
        cursor: String? = null,
        refresh: Boolean = false,
        size: Int = 10,
    ): BackendCommunityFeed? {
        return withContext(Dispatchers.IO) {
            val queryParams = buildList {
                add("tab=${encode(tab)}")
                add("refresh=$refresh")
                add("size=$size")
                if (!cursor.isNullOrBlank()) add("cursor=${encode(cursor)}")
            }.joinToString("&")
            val json = requestJson(
                method = "GET",
                path = "/api/v1/communities?$queryParams",
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseCommunityFeed(data)
        }
    }

    suspend fun fetchProfilePosts(
        accessToken: String,
        userId: String,
        size: Int = 30,
    ): List<BackendCommunityPost> {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "GET",
                path = "/api/v1/users/${encode(userId)}/posts?size=$size",
                accessToken = accessToken,
            )
            val data = json.optJSONArray("data") ?: json.optJSONObject("data")?.optJSONArray("items")
            parseCommunityPosts(data)
        }
    }

    suspend fun createCommunityPost(
        accessToken: String,
        requestModel: BackendCommunityPostRequest,
    ): BackendCommunityPost? {
        return withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("topicId", requestModel.topicId)
                .put("text", requestModel.text)
                .put("postType", requestModel.postType)
                .put("mediaUrl", requestModel.mediaUrl ?: JSONObject.NULL)
                .put("mediaUrls", JSONArray(requestModel.mediaUrls))
                .put("thumbnailUrl", requestModel.thumbnailUrl ?: JSONObject.NULL)
                .put("tags", JSONArray(requestModel.tags))
                .put("mentionedUserIds", JSONArray(requestModel.mentionedUserIds))
            val json = requestJson(
                method = "POST",
                path = "/api/v1/community-posts",
                body = payload,
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseCommunityPost(data)
        }
    }

    suspend fun likeCommunityPost(
        accessToken: String,
        postId: String,
        liked: Boolean,
    ): BackendCommunityPost? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "POST",
                path = "/api/v1/community-posts/$postId/like",
                body = JSONObject().put("liked", liked),
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseCommunityPost(data)
        }
    }

    suspend fun commentCommunityPost(
        accessToken: String,
        postId: String,
        requestModel: BackendCommunityCommentRequest,
    ): BackendCommunityPost? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "POST",
                path = "/api/v1/community-posts/$postId/comments",
                body = JSONObject().put("text", requestModel.text),
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseCommunityPost(data)
        }
    }

    suspend fun shareCommunityPost(
        accessToken: String,
        postId: String,
        requestModel: BackendCommunityShareRequest,
    ): BackendCommunityShareResponse? {
        return withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("target", requestModel.target)
                .put("recipientUserId", requestModel.recipientUserId ?: JSONObject.NULL)
                .put("copyLink", requestModel.copyLink)
            val json = requestJson(
                method = "POST",
                path = "/api/v1/community-posts/$postId/share",
                body = payload,
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            BackendCommunityShareResponse(
                shareUrl = data.optString("shareUrl"),
                post = data.optJSONObject("post")?.let { parseCommunityPost(it) } ?: return@withContext null,
            )
        }
    }

    suspend fun fetchCommunityTags(
        accessToken: String,
        query: String,
        limit: Int,
    ): List<BackendCommunityTagSuggestion>? {
        return withContext(Dispatchers.IO) {
            val queryParams = buildList {
                add("q=${encode(query)}")
                add("limit=$limit")
            }.joinToString("&")
            val json = requestJson(
                method = "GET",
                path = "/api/v1/community-tags?$queryParams",
                accessToken = accessToken,
            )
            val data = json.optJSONArray("data") ?: json.optJSONObject("data")?.optJSONArray("items") ?: return@withContext emptyList()
            val items = mutableListOf<BackendCommunityTagSuggestion>()
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                items += BackendCommunityTagSuggestion(
                    tag = item.optString("tag"),
                    hotness = item.optInt("hotness"),
                    postCount = item.optInt("postCount"),
                    exactMatch = item.optBoolean("exactMatch"),
                    canCreate = item.optBoolean("canCreate", true),
                )
            }
            items
        }
    }

    suspend fun updateProfile(accessToken: String, requestModel: BackendProfileUpdateRequest): BackendProfile? {
        return withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("displayName", requestModel.displayName)
                .put("bio", requestModel.bio ?: JSONObject.NULL)
                .put("city", requestModel.city ?: JSONObject.NULL)
                .put("age", requestModel.age ?: JSONObject.NULL)
                .put("photoUrl", requestModel.photoUrl ?: JSONObject.NULL)
                .put("featuredPhotos", JSONArray(requestModel.featuredPhotos))
                .put("interests", JSONArray(requestModel.interests))
            val json = requestJson(
                method = "PATCH",
                path = "/api/v1/me/profile",
                body = payload,
                accessToken = accessToken,
            )
            val me = json.optJSONObject("data") ?: return@withContext null
            parseProfile(me)
        }
    }

    suspend fun toggleFollow(accessToken: String, userId: String, followed: Boolean): BackendProfile? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "POST",
                path = "/api/v1/users/${encode(userId)}/follow",
                body = JSONObject().put("followed", followed),
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseProfile(data)
        }
    }

    suspend fun registerPushToken(accessToken: String, requestModel: BackendDeviceTokenRequest) {
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("token", requestModel.token)
                .put("platform", requestModel.platform)
                .put("deviceId", requestModel.deviceId)
                .put("appVersion", requestModel.appVersion)
            requestJson(
                method = "POST",
                path = "/api/v1/push/tokens",
                body = payload,
                accessToken = accessToken,
            )
        }
    }

    suspend fun sendMessage(accessToken: String, threadId: String, text: String): BackendChatMessage? {
        return sendMessage(accessToken, threadId, text, null)
    }

    suspend fun sendMessage(
        accessToken: String,
        threadId: String,
        text: String,
        attachment: BackendMessageAttachment?,
    ): BackendChatMessage? {
        return withContext(Dispatchers.IO) {
            val payload = JSONObject().put("text", text)
            if (attachment != null) {
                payload.put("attachmentUrl", attachment.url)
                payload.put("attachmentPreviewUrl", attachment.previewUrl ?: JSONObject.NULL)
                payload.put("attachmentMimeType", attachment.mimeType ?: JSONObject.NULL)
                payload.put("attachmentName", attachment.name ?: JSONObject.NULL)
                payload.put("attachmentKind", attachment.kind.name.uppercase())
                payload.put("attachmentDurationSeconds", attachment.durationSeconds ?: JSONObject.NULL)
            }
            val json = requestJson(
                method = "POST",
                path = "/api/v1/threads/$threadId/messages",
                body = payload,
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            return@withContext parseChatMessage(data)
        }
    }

    suspend fun uploadMedia(
        accessToken: String,
        fileName: String,
        mimeType: String,
        kind: String,
        title: String,
        fileBytes: ByteArray,
        previewUrl: String? = null,
    ): BackendMediaAsset? {
        return withContext(Dispatchers.IO) {
            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", title)
                .addFormDataPart("kind", kind)
                .addFormDataPart("file", fileName, fileBytes.toRequestBody(mimeType.toMediaType()))
            if (!previewUrl.isNullOrBlank()) {
                bodyBuilder.addFormDataPart("previewUrl", previewUrl)
            }

            val request = Request.Builder()
                .url("$baseUrl/api/v1/media/upload")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(bodyBuilder.build())
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Request failed ${response.code}: $raw")
                }
                if (raw.isBlank()) {
                    return@withContext null
                }
                val json = JSONObject(raw)
                val data = json.optJSONObject("data") ?: return@withContext null
                BackendMediaAsset(
                    id = data.optString("id"),
                    title = data.optString("title"),
                    url = data.optString("url"),
                    mimeType = data.optString("mimeType"),
                    kind = data.optString("kind"),
                    previewUrl = data.optString("previewUrl").takeIf { it.isNotBlank() },
                )
            }
        }
    }

    suspend fun setTyping(accessToken: String, threadId: String, typing: Boolean) {
        withContext(Dispatchers.IO) {
            requestJson(
                method = "POST",
                path = "/api/v1/threads/$threadId/typing",
                body = JSONObject().put("typing", typing),
                accessToken = accessToken,
            )
        }
    }

    suspend fun markThreadRead(accessToken: String, threadId: String) {
        withContext(Dispatchers.IO) {
            requestJson(
                method = "POST",
                path = "/api/v1/threads/$threadId/read",
                body = JSONObject(),
                accessToken = accessToken,
            )
        }
    }

    suspend fun fetchRealtimeConfig(accessToken: String): BackendRealtimeConfig? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "GET",
                path = "/api/v1/realtime/config",
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseRealtimeConfig(data)
        }
    }

    suspend fun startCall(
        accessToken: String,
        threadId: String,
        callType: CallType,
        direction: CallDirection = CallDirection.Outgoing,
        peerUserId: String = "",
    ): BackendCallSession {
        return withContext(Dispatchers.IO) {
            val resolvedThreadId = threadId.ifBlank { "direct" }
            val json = requestJson(
                method = "POST",
                path = "/api/v1/threads/${encode(resolvedThreadId)}/calls",
                body = JSONObject()
                    .put("callType", callType.name.uppercase())
                    .put("direction", direction.name.uppercase())
                    .put("peerUserId", peerUserId),
                accessToken = accessToken,
            )
            parseCallSession(json)
        }
    }

    suspend fun answerCall(accessToken: String, callId: String): BackendCallSession {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "POST",
                path = "/api/v1/calls/$callId/answer",
                body = JSONObject(),
                accessToken = accessToken,
            )
            parseCallSession(json)
        }
    }

    suspend fun endCall(accessToken: String, callId: String, reason: CallEndReason): BackendCallSession {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "POST",
                path = "/api/v1/calls/$callId/end",
                body = JSONObject().put("reason", reason.name.uppercase()),
                accessToken = accessToken,
            )
            parseCallSession(json)
        }
    }

    suspend fun minimizeCall(accessToken: String, callId: String, minimized: Boolean): BackendCallSession {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "POST",
                path = "/api/v1/calls/$callId/minimize?minimized=$minimized",
                body = JSONObject(),
                accessToken = accessToken,
            )
            parseCallSession(json)
        }
    }

    fun openRealtime(
        accessToken: String,
        onEvent: (BackendRealtimeEvent) -> Unit,
        onClosed: (Throwable?) -> Unit = {},
    ): WebSocket {
        val request = Request.Builder()
            .url("$baseUrl/ws/realtime?token=${URLEncoder.encode(accessToken, StandardCharsets.UTF_8.name())}")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                onEvent(
                    BackendRealtimeEvent(
                        id = "connection-ready",
                        type = BackendRealtimeEventType.CONNECTION_READY,
                        title = "Realtime connected",
                        body = "WebSocket connection established",
                    )
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseRealtimeEvent(text)?.let(onEvent)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                onClosed(null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                onClosed(t)
            }
        })
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        accessToken: String? = null,
    ): JSONObject {
        val response = okHttpClient.newCall(
            Request.Builder()
                .url("$baseUrl$path")
                .apply {
                    if (accessToken != null) {
                addHeader("Authorization", "Bearer $accessToken")
                }
                    addHeader("Content-Type", "application/json")
                    when (method.uppercase()) {
                        "POST" -> post((body ?: JSONObject()).toString().toRequestBody(JSON_MEDIA_TYPE))
                        "PATCH" -> patch((body ?: JSONObject()).toString().toRequestBody(JSON_MEDIA_TYPE))
                        "DELETE" -> delete()
                        else -> get()
                    }
                }
                .build()
        ).execute()

        response.use { result ->
            val raw = result.body?.string().orEmpty()
            if (!result.isSuccessful) {
                throw IOException("Request failed ${result.code}: $raw")
            }
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        }
    }

    private fun parseRealtimeEvent(text: String): BackendRealtimeEvent? {
        if (text.isBlank()) {
            return null
        }
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val type = BackendRealtimeEventType.entries.firstOrNull { it.name == json.optString("type") }
            ?: BackendRealtimeEventType.UNKNOWN
        val payloadObject = json.optJSONObject("payload")
        val payload = mutableMapOf<String, String>()
        if (payloadObject != null) {
            val keys = payloadObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                payload[key] = payloadObject.opt(key)?.let { value ->
                    when (value) {
                        JSONObject.NULL -> ""
                        else -> value.toString()
                    }
                }.orEmpty()
            }
        }
        return BackendRealtimeEvent(
            id = json.optString("id"),
            type = type,
            room = json.optString("room").takeIf { it.isNotBlank() },
            actorUserId = json.optString("actorUserId").takeIf { it.isNotBlank() },
            targetUserId = json.optString("targetUserId").takeIf { it.isNotBlank() },
            threadId = json.optString("threadId").takeIf { it.isNotBlank() },
            callId = json.optString("callId").takeIf { it.isNotBlank() },
            messageId = json.optString("messageId").takeIf { it.isNotBlank() },
            title = json.optString("title").takeIf { it.isNotBlank() },
            body = json.optString("body").takeIf { it.isNotBlank() },
            payload = payload,
            timestamp = json.optString("timestamp").takeIf { it.isNotBlank() },
        )
    }

    private fun parseProfile(me: JSONObject): BackendProfile {
        return BackendProfile(
            userId = me.optString("userId"),
            publicId = me.optString("publicId"),
            displayName = me.optString("displayName"),
            username = me.optString("username"),
            bio = me.optString("bio"),
            avatarUrl = me.optString("avatarUrl"),
            featuredPhotos = me.optJSONArray("featuredPhotos").toStringList(),
            interests = me.optJSONArray("interests").toStringList(),
            age = me.optInt("age"),
            city = me.optString("city"),
            gender = me.optString("gender").ifBlank { "Not specified" },
            verified = me.optBoolean("verified"),
            online = me.optBoolean("online"),
            premium = me.optBoolean("premium"),
            vipTierId = me.optString("vipTierId").takeIf { it.isNotBlank() },
            vipTierName = me.optString("vipTierName").takeIf { it.isNotBlank() },
            followersCount = me.optInt("followersCount", 0),
            followingCount = me.optInt("followingCount", 0),
            friendsCount = me.optInt("friendsCount", 0),
            followedByMe = me.optBoolean("followedByMe", false),
            followedByThem = me.optBoolean("followedByThem", false),
            friend = me.optBoolean("friend", false),
            onboardingComplete = me.optBoolean("onboardingComplete"),
            profileComplete = me.optBoolean("profileComplete"),
        )
    }

    private fun parseSearchPage(data: JSONObject): BackendSearchPage {
        val items = mutableListOf<BackendSearchUser>()
        val array = data.optJSONArray("items")
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                items += BackendSearchUser(
                    userId = item.optString("userId"),
                    publicId = item.optString("publicId"),
                    displayName = item.optString("displayName"),
                    age = item.optInt("age"),
                    avatarUrl = item.optString("avatarUrl"),
                    vipTierId = item.optString("vipTierId").takeIf { it.isNotBlank() },
                    vipTierName = item.optString("vipTierName").takeIf { it.isNotBlank() },
                    premium = item.optBoolean("premium"),
                    verified = item.optBoolean("verified"),
                    distanceKm = if (item.isNull("distanceKm")) null else item.optInt("distanceKm"),
                    online = item.optBoolean("online"),
                    city = item.optString("city"),
                    gender = item.optString("gender").ifBlank { "Not specified" },
                    interests = item.optJSONArray("interests").toStringList(),
                )
            }
        }
        return BackendSearchPage(
            items = items,
            page = data.optInt("page", 0),
            size = data.optInt("size", items.size.coerceAtLeast(20)),
            total = data.optLong("total", items.size.toLong()),
        )
    }

    private fun parseProfilePage(data: JSONObject): BackendProfilePage {
        val items = mutableListOf<BackendProfile>()
        val array = data.optJSONArray("items")
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                parseProfile(item)?.let { items += it }
            }
        }
        return BackendProfilePage(
            items = items,
            page = data.optInt("page", 0),
            size = data.optInt("size", items.size.coerceAtLeast(50)),
            total = data.optLong("total", items.size.toLong()),
        )
    }

    private fun parseChatThreads(array: JSONArray?): List<BackendChatThread> {
        if (array == null || array.length() == 0) {
            return emptyList()
        }
        val items = mutableListOf<BackendChatThread>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parseChatThread(item)?.let { items += it }
        }
        return items
    }

    private fun parseChatThread(item: JSONObject): BackendChatThread? {
        val participant = item.optJSONObject("participant") ?: return null
        return BackendChatThread(
            id = item.optString("id"),
            type = item.optString("type"),
            participant = parseChatParticipant(participant),
            lastMessage = item.optString("lastMessage"),
            unreadCount = item.optInt("unreadCount"),
            online = item.optBoolean("online"),
            typing = item.optBoolean("typing"),
            pinned = item.optBoolean("pinned"),
            matchLabel = item.optString("matchLabel"),
            updatedAt = item.optString("updatedAt"),
        )
    }

    private fun parseChatParticipant(item: JSONObject): BackendPublicUserCard {
        return BackendPublicUserCard(
            userId = item.optString("userId"),
            publicId = item.optString("publicId"),
            displayName = item.optString("displayName"),
            username = item.optString("username"),
            bio = item.optString("bio"),
            age = item.optInt("age"),
            avatarUrl = item.optString("avatarUrl"),
            featuredPhotos = item.optJSONArray("featuredPhotos").toStringList(),
            vipTierId = item.optString("vipTierId").takeIf { it.isNotBlank() },
            vipTierName = item.optString("vipTierName").takeIf { it.isNotBlank() },
            verified = item.optBoolean("verified"),
            premium = item.optBoolean("premium"),
            distanceKm = if (item.isNull("distanceKm")) null else item.optInt("distanceKm"),
            online = item.optBoolean("online"),
            city = item.optString("city"),
            gender = item.optString("gender").ifBlank { "Not specified" },
            interests = item.optJSONArray("interests").toStringList(),
            followersCount = item.optInt("followersCount"),
            followingCount = item.optInt("followingCount"),
            friendsCount = item.optInt("friendsCount"),
            followedByThem = item.optBoolean("followedByThem"),
            friend = item.optBoolean("friend"),
            followedByMe = item.optBoolean("followedByMe"),
        )
    }

    private fun parseThreadDetail(data: JSONObject): BackendThreadDetailResponse? {
        val thread = data.optJSONObject("thread")?.let { parseChatThread(it) } ?: return null
        return BackendThreadDetailResponse(
            thread = thread,
            messages = parseChatMessages(data.optJSONArray("messages")),
            hasMore = data.optBoolean("hasMore"),
            nextCursor = data.optString("nextCursor").takeIf { it.isNotBlank() },
        )
    }

    private fun parseChatMessages(array: JSONArray?): List<BackendChatMessage> {
        if (array == null || array.length() == 0) {
            return emptyList()
        }
        val items = mutableListOf<BackendChatMessage>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parseChatMessage(item)?.let { items += it }
        }
        return items
    }

    private fun parseChatMessage(item: JSONObject): BackendChatMessage? {
        val id = item.optString("id")
        if (id.isBlank()) {
            return null
        }
        return BackendChatMessage(
            id = id,
            threadId = item.optString("threadId"),
            text = item.optString("text"),
            sentByMe = item.optBoolean("sentByMe"),
            timeLabel = item.optString("timeLabel"),
            isVoice = item.optBoolean("isVoice"),
            isGif = item.optBoolean("isGif"),
            isSticker = item.optBoolean("isSticker"),
            attachmentKind = item.optString("attachmentKind").takeIf { it.isNotBlank() },
            attachmentUrl = item.optString("attachmentUrl").takeIf { it.isNotBlank() },
            attachmentPreviewUrl = item.optString("attachmentPreviewUrl").takeIf { it.isNotBlank() },
            attachmentMimeType = item.optString("attachmentMimeType").takeIf { it.isNotBlank() },
            attachmentName = item.optString("attachmentName").takeIf { it.isNotBlank() },
            attachmentDurationSeconds = if (item.isNull("attachmentDurationSeconds")) null else item.optInt("attachmentDurationSeconds"),
            translatedText = item.optString("translatedText").takeIf { it.isNotBlank() },
            isRead = item.optBoolean("isRead"),
            callSummary = item.optJSONObject("callSummary")?.toCallSummary(),
            status = item.optString("status").ifBlank { "SENT" },
        )
    }

    private fun parseNotifications(array: JSONArray): List<BackendNotification> {
        if (array.length() == 0) {
            return emptyList()
        }
        val items = mutableListOf<BackendNotification>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            items += BackendNotification(
                id = item.optString("id"),
                kind = item.optString("kind"),
                threadId = item.optString("threadId").takeIf { it.isNotBlank() },
                title = item.optString("title"),
                body = item.optString("body"),
                timeLabel = item.optString("timeLabel"),
                read = item.optBoolean("read"),
                actionTarget = item.optString("actionTarget").takeIf { it.isNotBlank() },
            )
        }
        return items
    }

    private fun parseCommunityFeed(data: JSONObject): BackendCommunityFeed {
        return BackendCommunityFeed(
            topics = parseCommunityTopics(data.optJSONArray("topics")),
            posts = parseCommunityPosts(data.optJSONArray("posts")),
            events = parseCommunityEvents(data.optJSONArray("events")),
            trendingTags = data.optJSONArray("trendingTags").toStringList(),
            postTypes = data.optJSONArray("postTypes").toStringList(),
            refreshToken = data.optString("refreshToken"),
            nextCursor = data.optString("nextCursor").takeIf { it.isNotBlank() },
            hasMore = data.optBoolean("hasMore", false),
        )
    }

    private fun parseCommunityTopics(array: JSONArray?): List<BackendCommunityTopic> {
        if (array == null || array.length() == 0) {
            return emptyList()
        }
        val items = mutableListOf<BackendCommunityTopic>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            items += BackendCommunityTopic(
                id = item.optString("id"),
                title = item.optString("title"),
                description = item.optString("description"),
                bannerUrl = item.optString("bannerUrl"),
                members = item.optString("members"),
                moderator = item.optString("moderator"),
                eventCount = item.optInt("eventCount"),
                joined = item.optBoolean("joined"),
            )
        }
        return items
    }

    private fun parseCommunityEvents(array: JSONArray?): List<BackendCommunityEvent> {
        if (array == null || array.length() == 0) {
            return emptyList()
        }
        val items = mutableListOf<BackendCommunityEvent>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            items += BackendCommunityEvent(
                id = item.optString("id"),
                title = item.optString("title"),
                kind = item.optString("kind"),
                dateLabel = item.optString("dateLabel"),
                location = item.optString("location"),
                price = item.optString("price"),
                bannerUrl = item.optString("bannerUrl"),
                attendees = item.optString("attendees"),
                joined = item.optBoolean("joined"),
            )
        }
        return items
    }

    private fun parseCommunityPosts(array: JSONArray?): List<BackendCommunityPost> {
        if (array == null || array.length() == 0) {
            return emptyList()
        }
        val items = mutableListOf<BackendCommunityPost>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            items += parseCommunityPost(item)
        }
        return items
    }

    private fun parseCommunityPost(item: JSONObject): BackendCommunityPost {
        val author = item.optJSONObject("author")
        val comments = item.optJSONArray("commentsPreview").toCommunityComments()
        return BackendCommunityPost(
            id = item.optString("id"),
            topicId = item.optString("topicId"),
            postType = item.optString("postType").ifBlank { "TEXT" },
            authorId = author?.optString("userId").orEmpty(),
            authorName = author?.optString("displayName").orEmpty(),
            authorAvatarUrl = author?.optString("avatarUrl").orEmpty(),
            authorVipTierId = author?.optString("vipTierId")?.takeIf { it.isNotBlank() },
            authorVipTierName = author?.optString("vipTierName")?.takeIf { it.isNotBlank() },
            authorPremium = author?.optBoolean("premium") == true,
            authorVerified = author?.optBoolean("verified") == true,
            authorOnline = author?.optBoolean("online") == true,
            authorCity = author?.optString("city").orEmpty(),
            text = item.optString("text"),
            mediaUrl = item.optString("mediaUrl").takeIf { it.isNotBlank() },
            mediaUrls = item.optJSONArray("mediaUrls").toStringList().ifEmpty {
                item.optString("mediaUrl").takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
            },
            thumbnailUrl = item.optString("thumbnailUrl").takeIf { it.isNotBlank() },
            tags = item.optJSONArray("tags").toStringList(),
            mentionedUserIds = item.optJSONArray("mentionedUserIds").toStringList(),
            likes = item.optInt("likes"),
            comments = item.optInt("comments"),
            commentsPreview = comments,
            shares = item.optInt("shares"),
            likedByMe = item.optBoolean("likedByMe"),
            sharedByMe = item.optBoolean("sharedByMe"),
            timeLabel = item.optString("timeLabel"),
        )
    }

    private fun JSONArray?.toCommunityComments(): List<BackendCommunityComment> {
        if (this == null || length() == 0) {
            return emptyList()
        }
        val items = mutableListOf<BackendCommunityComment>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val author = item.optJSONObject("author")
            items += BackendCommunityComment(
                id = item.optString("id"),
                postId = item.optString("postId"),
                authorId = author?.optString("userId").orEmpty(),
                authorName = author?.optString("displayName").orEmpty(),
                authorAvatarUrl = author?.optString("avatarUrl").orEmpty(),
                authorVipTierId = author?.optString("vipTierId")?.takeIf { it.isNotBlank() },
                authorVipTierName = author?.optString("vipTierName")?.takeIf { it.isNotBlank() },
                authorPremium = author?.optBoolean("premium") == true,
                text = item.optString("text"),
                timeLabel = item.optString("timeLabel"),
                mine = item.optBoolean("mine"),
                mentionedUserIds = item.optJSONArray("mentionedUserIds").toStringList(),
            )
        }
        return items
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null || length() == 0) {
            return emptyList()
        }
        val items = mutableListOf<String>()
        for (index in 0 until length()) {
            val value = opt(index)?.toString().orEmpty().trim()
            if (value.isNotBlank()) {
                items += value
            }
        }
        return items.distinct()
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private fun parseCallSession(json: JSONObject): BackendCallSession {
        val data = json.optJSONObject("data") ?: json
        val summaryJson = data.optJSONObject("summary")
        val callId = data.optString("id")
        val threadId = data.optString("threadId")
        return BackendCallSession(
            callId = callId,
            threadId = threadId,
            summary = summaryJson?.toCallSummary(fallbackThreadId = threadId, fallbackCallId = callId),
            status = data.optString("status"),
            minimized = data.optBoolean("minimized"),
        )
    }

    private fun JSONObject.toCallSummary(
        fallbackThreadId: String = "",
        fallbackCallId: String? = null,
    ): CallSummaryUiState? {
        val participantName = optString("participantName").takeIf { it.isNotBlank() } ?: return null
        return CallSummaryUiState(
            participantName = participantName,
            threadId = optString("threadId").ifBlank { fallbackThreadId },
            callId = optString("callId").takeIf { it.isNotBlank() } ?: fallbackCallId,
            callType = when (optString("callType").uppercase()) {
                "VIDEO" -> CallType.Video
                else -> CallType.Voice
            },
            direction = when (optString("direction").uppercase()) {
                "INCOMING" -> CallDirection.Incoming
                else -> CallDirection.Outgoing
            },
            durationSeconds = optInt("durationSeconds"),
            endReason = when (optString("endReason").uppercase()) {
                "MISSED" -> CallEndReason.Missed
                "NO_ANSWER" -> CallEndReason.NoAnswer
                "DECLINED" -> CallEndReason.Declined
                "REJECTED" -> CallEndReason.Rejected
                "BUSY" -> CallEndReason.Busy
                "CANCELED" -> CallEndReason.Canceled
                "DROPPED" -> CallEndReason.Dropped
                "COMPLETED" -> CallEndReason.Completed
                else -> CallEndReason.HungUp
            },
            startedAtLabel = optString("startedAtLabel"),
            endedAtLabel = optString("endedAtLabel"),
            isMicOn = optBoolean("isMicOn", true),
            isVideoOn = optBoolean("isVideoOn", optString("callType").equals("VIDEO", ignoreCase = true)),
        )
    }

    private fun parseRealtimeConfig(data: JSONObject): BackendRealtimeConfig {
        val iceServers = mutableListOf<BackendIceServer>()
        val array = data.optJSONArray("iceServers")
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url").takeIf { it.isNotBlank() } ?: continue
                iceServers += BackendIceServer(
                    url = url,
                    username = item.optString("username").takeIf { it.isNotBlank() },
                    credential = item.optString("credential").takeIf { it.isNotBlank() },
                )
            }
        }
        return BackendRealtimeConfig(
            iceServers = iceServers,
            maxRestartAttempts = data.optInt("maxRestartAttempts", 2),
            restartBackoffMs = data.optLong("restartBackoffMs", 1_000L),
        )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
