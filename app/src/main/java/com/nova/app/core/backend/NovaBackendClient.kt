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
    baseHttpClient: OkHttpClient = defaultClient(),
) {
    /** Set by the runtime so expired access tokens can be refreshed transparently. */
    @Volatile
    var tokenStore: SessionTokenStore? = null

    private val okHttpClient: OkHttpClient = baseHttpClient.newBuilder()
        .authenticator(TokenAuthenticator(baseUrl, baseHttpClient) { tokenStore })
        .build()

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
                accessToken = tokens.optText("accessToken"),
                refreshToken = tokens.optText("refreshToken"),
                userId = me.optText("userId"),
                publicId = me.optText("publicId"),
                displayName = me.optText("displayName"),
                avatarUrl = me.optText("avatarUrl").takeIf { it.isNotBlank() },
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

    suspend fun fetchDiscover(
        accessToken: String,
        gender: String? = null,
        minAge: Int? = null,
        maxAge: Int? = null,
        excludeIds: List<String> = emptyList(),
    ): BackendDiscoverResponse? {
        return withContext(Dispatchers.IO) {
            val queryParams = buildList {
                if (!gender.isNullOrBlank()) add("gender=${encode(gender)}")
                if (minAge != null) add("minAge=$minAge")
                if (maxAge != null) add("maxAge=$maxAge")
                excludeIds.filter { it.isNotBlank() }.forEach { add("excludeIds=${encode(it)}") }
            }.joinToString("&")
            val path = if (queryParams.isBlank()) "/api/v1/discover" else "/api/v1/discover?$queryParams"
            val json = requestJson(
                method = "GET",
                path = path,
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseDiscover(data)
        }
    }

    suspend fun swipeDiscoverCandidate(accessToken: String, candidateId: String, direction: String): BackendSwipeResponse? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "POST",
                path = "/api/v1/discover/swipe",
                body = JSONObject()
                    .put("candidateId", candidateId)
                    .put("direction", direction),
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            BackendSwipeResponse(
                matched = data.optBoolean("matched"),
                message = data.optText("message"),
                nextCandidateId = data.optText("nextCandidateId").takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun pokeDiscoverCandidate(accessToken: String, candidateId: String): BackendPokeResponse? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "POST",
                path = "/api/v1/discover/poke",
                body = JSONObject().put("candidateId", candidateId),
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            BackendPokeResponse(
                delivered = data.optBoolean("delivered"),
                message = data.optText("message"),
                nextCandidateId = data.optText("nextCandidateId").takeIf { it.isNotBlank() },
            )
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

    suspend fun fetchCommerceCatalog(): BackendCommerceCatalog? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "GET",
                path = "/api/v1/commerce/catalog",
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseCommerceCatalog(data)
        }
    }

    suspend fun fetchCommerceMe(accessToken: String): BackendCommerceMe? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "GET",
                path = "/api/v1/commerce/me",
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseCommerceMe(data)
        }
    }

    suspend fun createCommerceOrder(
        accessToken: String,
        productId: String,
        purchaseType: String,
        provider: String = "DEMO",
    ): BackendCommerceOrder? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "POST",
                path = "/api/v1/commerce/orders",
                body = JSONObject()
                    .put("productId", productId)
                    .put("purchaseType", purchaseType)
                    .put("provider", provider)
                    .put("note", "Android in-app checkout"),
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseCommerceOrder(data)
        }
    }

    suspend fun confirmCommerceOrder(
        accessToken: String,
        orderId: String,
        success: Boolean,
        transactionId: String,
        message: String,
    ): BackendCommerceOrder? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "POST",
                path = "/api/v1/commerce/orders/${encode(orderId)}/confirm",
                body = JSONObject()
                    .put("success", success)
                    .put("transactionId", transactionId)
                    .put("message", message),
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseCommerceOrder(data)
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

    suspend fun searchChatThreads(
        accessToken: String,
        query: String,
        page: Int = 0,
        size: Int = 10,
    ): BackendChatThreadPage? {
        return withContext(Dispatchers.IO) {
            val queryParams = buildList {
                add("q=${encode(query)}")
                add("page=$page")
                add("size=$size")
            }.joinToString("&")
            val json = requestJson(
                method = "GET",
                path = "/api/v1/threads/search?$queryParams",
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseChatThreadPage(data)
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

    suspend fun markAllNotificationsRead(accessToken: String): List<BackendNotification>? {
        return withContext(Dispatchers.IO) {
            requestJson(
                method = "POST",
                path = "/api/v1/notifications/read-all",
                body = JSONObject(),
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

    suspend fun searchCommunityPosts(
        accessToken: String,
        query: String,
        page: Int = 0,
        size: Int = 10,
    ): BackendCommunityPostPage {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "GET",
                path = "/api/v1/community-posts/search?q=${encode(query)}&page=$page&size=$size",
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: JSONObject()
            BackendCommunityPostPage(
                items = parseCommunityPosts(data.optJSONArray("items")),
                page = data.optInt("page", page),
                size = data.optInt("size", size),
                total = data.optLong("total", 0L),
            )
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

    suspend fun fetchCommunityComments(
        accessToken: String,
        postId: String,
        page: Int = 0,
        size: Int = 20,
    ): BackendCommunityCommentPage {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "GET",
                path = "/api/v1/community-posts/${encode(postId)}/comments?page=$page&size=$size",
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: JSONObject()
            BackendCommunityCommentPage(
                items = data.optJSONArray("items").toCommunityComments(),
                page = data.optInt("page", page),
                size = data.optInt("size", size),
                total = data.optLong("total", 0L),
            )
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
                shareUrl = data.optText("shareUrl"),
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
                    tag = item.optText("tag"),
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
                .put("gender", requestModel.gender ?: JSONObject.NULL)
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

    suspend fun logout(accessToken: String) {
        withContext(Dispatchers.IO) {
            requestJson(method = "POST", path = "/api/v1/auth/logout", accessToken = accessToken)
        }
    }

    suspend fun unregisterPushToken(accessToken: String, token: String) {
        withContext(Dispatchers.IO) {
            requestJson(
                method = "DELETE",
                path = "/api/v1/push/tokens/${URLEncoder.encode(token, StandardCharsets.UTF_8.name())}",
                accessToken = accessToken,
            )
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
                    id = data.optText("id"),
                    title = data.optText("title"),
                    url = data.optText("url"),
                    mimeType = data.optText("mimeType"),
                    kind = data.optText("kind"),
                    previewUrl = data.optText("previewUrl").takeIf { it.isNotBlank() },
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

    suspend fun deleteThreadForMe(accessToken: String, threadId: String): Boolean {
        return withContext(Dispatchers.IO) {
            requestJson(
                method = "DELETE",
                path = "/api/v1/threads/$threadId",
                accessToken = accessToken,
            )
            true
        }
    }

    suspend fun deleteMessageForMe(accessToken: String, threadId: String, messageId: String): Boolean {
        return withContext(Dispatchers.IO) {
            requestJson(
                method = "DELETE",
                path = "/api/v1/threads/$threadId/messages/$messageId",
                accessToken = accessToken,
            )
            true
        }
    }

    suspend fun recallMessage(accessToken: String, threadId: String, messageId: String): BackendChatMessage? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "POST",
                path = "/api/v1/threads/$threadId/messages/$messageId/recall",
                body = JSONObject(),
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseChatMessage(data)
        }
    }

    suspend fun editMessage(accessToken: String, threadId: String, messageId: String, text: String): BackendChatMessage? {
        return withContext(Dispatchers.IO) {
            val json = requestJson(
                method = "PATCH",
                path = "/api/v1/threads/$threadId/messages/$messageId",
                body = JSONObject().put("text", text),
                accessToken = accessToken,
            )
            val data = json.optJSONObject("data") ?: return@withContext null
            parseChatMessage(data)
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
                body = JSONObject().put("reason", reason.toBackendValue()),
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
        val type = BackendRealtimeEventType.entries.firstOrNull { it.name == json.optText("type") }
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
            id = json.optText("id"),
            type = type,
            room = json.optText("room").takeIf { it.isNotBlank() },
            actorUserId = json.optText("actorUserId").takeIf { it.isNotBlank() },
            targetUserId = json.optText("targetUserId").takeIf { it.isNotBlank() },
            threadId = json.optText("threadId").takeIf { it.isNotBlank() },
            callId = json.optText("callId").takeIf { it.isNotBlank() },
            messageId = json.optText("messageId").takeIf { it.isNotBlank() },
            title = json.optText("title").takeIf { it.isNotBlank() },
            body = json.optText("body").takeIf { it.isNotBlank() },
            payload = payload,
            timestamp = json.optText("timestamp").takeIf { it.isNotBlank() },
        )
    }

    private fun parseProfile(me: JSONObject): BackendProfile {
        return BackendProfile(
            userId = me.optText("userId"),
            publicId = me.optText("publicId"),
            displayName = me.optText("displayName"),
            username = me.optText("username"),
            bio = me.optText("bio"),
            avatarUrl = resolvedBackendMediaUrl(me.optText("avatarUrl")),
            featuredPhotos = me.optJSONArray("featuredPhotos").toResolvedMediaUrlList(),
            interests = me.optJSONArray("interests").toStringList(),
            age = me.optInt("age"),
            city = me.optText("city"),
            gender = me.optText("gender").ifBlank { "Not specified" },
            verified = me.optBoolean("verified"),
            online = me.optBoolean("online"),
            premium = me.optBoolean("premium"),
            vipTierId = me.optText("vipTierId").takeIf { it.isNotBlank() },
            vipTierName = me.optText("vipTierName").takeIf { it.isNotBlank() },
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
                    userId = item.optText("userId"),
                    publicId = item.optText("publicId"),
                    displayName = item.optText("displayName"),
                    bio = item.optText("bio"),
                    age = item.optInt("age"),
                    avatarUrl = resolvedBackendMediaUrl(item.optText("avatarUrl")),
                    username = item.optText("username"),
                    vipTierId = item.optText("vipTierId").takeIf { it.isNotBlank() },
                    vipTierName = item.optText("vipTierName").takeIf { it.isNotBlank() },
                    premium = item.optBoolean("premium"),
                    verified = item.optBoolean("verified"),
                    distanceKm = if (item.isNull("distanceKm")) null else item.optInt("distanceKm"),
                    online = item.optBoolean("online"),
                    city = item.optText("city"),
                    gender = item.optText("gender").ifBlank { "Not specified" },
                    interests = item.optJSONArray("interests").toStringList(),
                    friend = item.optBoolean("friend", false),
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

    private fun parseDiscover(data: JSONObject): BackendDiscoverResponse {
        val items = mutableListOf<BackendDiscoveryCandidate>()
        val array = data.optJSONArray("items")
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val user = item.optJSONObject("user") ?: continue
                items += BackendDiscoveryCandidate(
                    candidateId = item.optText("candidateId").ifBlank { user.optText("userId") },
                    user = parseChatParticipant(user),
                    bio = item.optText("bio"),
                    compatibility = item.optInt("compatibility"),
                    commonInterests = item.optJSONArray("commonInterests").toStringList(),
                    iceBreaker = item.optText("iceBreaker"),
                    mutualFriends = item.optInt("mutualFriends"),
                    musicTaste = item.optText("musicTaste"),
                    height = item.optText("height"),
                    job = item.optText("job"),
                    relationshipGoal = item.optText("relationshipGoal"),
                    gallery = item.optJSONArray("gallery").toResolvedMediaUrlList(),
                    voiceIntro = item.optBoolean("voiceIntro", true),
                    videoIntro = item.optBoolean("videoIntro", true),
                )
            }
        }
        return BackendDiscoverResponse(
            items = items,
            filters = data.optJSONArray("filters").toStringList(),
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

    private fun parseChatThreadPage(data: JSONObject): BackendChatThreadPage {
        val items = parseChatThreads(data.optJSONArray("items"))
        return BackendChatThreadPage(
            items = items,
            page = data.optInt("page", 0),
            size = data.optInt("size", items.size.coerceAtLeast(10)),
            total = data.optLong("total", items.size.toLong()),
        )
    }

    private fun parseChatThread(item: JSONObject): BackendChatThread? {
        val participant = item.optJSONObject("participant") ?: return null
        return BackendChatThread(
            id = item.optText("id"),
            type = item.optText("type"),
            participant = parseChatParticipant(participant),
            lastMessage = item.optText("lastMessage"),
            unreadCount = item.optInt("unreadCount"),
            online = item.optBoolean("online"),
            typing = item.optBoolean("typing"),
            pinned = item.optBoolean("pinned"),
            matchLabel = item.optText("matchLabel"),
            updatedAt = item.optText("updatedAt"),
        )
    }

    private fun parseChatParticipant(item: JSONObject): BackendPublicUserCard {
        return BackendPublicUserCard(
            userId = item.optText("userId"),
            publicId = item.optText("publicId"),
            displayName = item.optText("displayName"),
            username = item.optText("username"),
            bio = item.optText("bio"),
            age = item.optInt("age"),
            avatarUrl = resolvedBackendMediaUrl(item.optText("avatarUrl")),
            featuredPhotos = item.optJSONArray("featuredPhotos").toResolvedMediaUrlList(),
            vipTierId = item.optText("vipTierId").takeIf { it.isNotBlank() },
            vipTierName = item.optText("vipTierName").takeIf { it.isNotBlank() },
            verified = item.optBoolean("verified"),
            premium = item.optBoolean("premium"),
            distanceKm = if (item.isNull("distanceKm")) null else item.optInt("distanceKm"),
            online = item.optBoolean("online"),
            city = item.optText("city"),
            gender = item.optText("gender").ifBlank { "Not specified" },
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
            nextCursor = data.optText("nextCursor").takeIf { it.isNotBlank() },
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
        val id = item.optText("id")
        if (id.isBlank()) {
            return null
        }
        val attachmentUrl = item.optCleanString("attachmentUrl")
        val attachmentPreviewUrl = item.optCleanString("attachmentPreviewUrl")
        return BackendChatMessage(
            id = id,
            threadId = item.optText("threadId"),
            text = item.optCleanString("text").orEmpty(),
            sentByMe = item.optBoolean("sentByMe"),
            timeLabel = item.optCleanString("timeLabel").orEmpty(),
            createdAt = item.optCleanString("createdAt"),
            isVoice = item.optBoolean("isVoice"),
            isGif = item.optBoolean("isGif"),
            isSticker = item.optBoolean("isSticker"),
            attachmentKind = item.optCleanString("attachmentKind"),
            attachmentUrl = resolvedBackendMediaUrl(attachmentUrl),
            attachmentPreviewUrl = resolvedBackendMediaUrl(attachmentPreviewUrl),
            attachmentMimeType = item.optCleanString("attachmentMimeType"),
            attachmentName = item.optCleanString("attachmentName"),
            attachmentDurationSeconds = if (item.isNull("attachmentDurationSeconds")) null else item.optInt("attachmentDurationSeconds"),
            attachmentWidth = if (item.isNull("attachmentWidth")) null else item.optInt("attachmentWidth"),
            attachmentHeight = if (item.isNull("attachmentHeight")) null else item.optInt("attachmentHeight"),
            translatedText = item.optCleanString("translatedText"),
            isRead = item.optBoolean("isRead"),
            callSummary = item.optJSONObject("callSummary")?.toCallSummary(),
            status = item.optCleanString("status") ?: "SENT",
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
                id = item.optText("id"),
                kind = item.optText("kind"),
                threadId = item.optText("threadId").takeIf { it.isNotBlank() },
                title = item.optText("title"),
                body = item.optText("body"),
                timeLabel = item.optText("timeLabel"),
                read = item.optBoolean("read"),
                actionTarget = item.optText("actionTarget").takeIf { it.isNotBlank() },
            )
        }
        return items
    }

    private fun parseCommerceCatalog(data: JSONObject): BackendCommerceCatalog {
        return BackendCommerceCatalog(
            vipTiers = data.optJSONArray("vipTiers").toVipTiers(),
            diamondPackages = data.optJSONArray("diamondPackages").toDiamondPackages(),
            paymentProviders = data.optJSONArray("paymentProviders").toPaymentProviders(),
        )
    }

    private fun parseCommerceMe(data: JSONObject): BackendCommerceMe {
        return BackendCommerceMe(
            userId = data.optText("userId"),
            vipActive = data.optBoolean("vipActive"),
            vipTierId = data.optText("vipTierId").takeIf { it.isNotBlank() },
            vipTierName = data.optText("vipTierName").takeIf { it.isNotBlank() },
            vipExpiresAt = data.optText("vipExpiresAt").takeIf { it.isNotBlank() },
            diamondBalance = data.optLong("diamondBalance", 0L),
            activeBenefits = data.optJSONArray("activeBenefits").toStringList(),
            recentOrders = data.optJSONArray("recentOrders").toCommerceOrders(),
        )
    }

    private fun parseCommerceOrder(data: JSONObject): BackendCommerceOrder {
        return BackendCommerceOrder(
            orderId = data.optText("orderId"),
            userId = data.optText("userId"),
            purchaseType = data.optText("purchaseType"),
            productId = data.optText("productId"),
            productName = data.optText("productName"),
            productSubtitle = data.optText("productSubtitle"),
            amount = data.optInt("amount"),
            currency = data.optText("currency"),
            status = data.optText("status"),
            provider = data.optText("provider"),
            checkoutUrl = data.optText("checkoutUrl").takeIf { it.isNotBlank() },
            qrContent = data.optText("qrContent").takeIf { it.isNotBlank() },
            expiresAt = data.optText("expiresAt").takeIf { it.isNotBlank() },
            grant = data.optJSONObject("grant")?.let(::parseCommerceGrant),
            transactionId = data.optText("transactionId").takeIf { it.isNotBlank() },
            failureReason = data.optText("failureReason").takeIf { it.isNotBlank() },
        )
    }

    private fun parseCommerceGrant(data: JSONObject): BackendCommerceGrant {
        return BackendCommerceGrant(
            grantType = data.optText("grantType"),
            vipTierId = data.optText("vipTierId").takeIf { it.isNotBlank() },
            vipTierName = data.optText("vipTierName").takeIf { it.isNotBlank() },
            vipExpiresAt = data.optText("vipExpiresAt").takeIf { it.isNotBlank() },
            diamondsAdded = if (data.isNull("diamondsAdded")) null else data.optInt("diamondsAdded"),
            diamondBalanceAfter = if (data.isNull("diamondBalanceAfter")) null else data.optLong("diamondBalanceAfter"),
            benefits = data.optJSONArray("benefits").toStringList(),
        )
    }

    private fun JSONArray?.toVipTiers(): List<BackendVipTier> {
        if (this == null || length() == 0) return emptyList()
        val items = mutableListOf<BackendVipTier>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            items += BackendVipTier(
                id = item.optText("id"),
                name = item.optText("name"),
                level = item.optInt("level"),
                price = item.optText("price"),
                cycle = item.optText("cycle"),
                subtitle = item.optText("subtitle"),
                badgeLabel = item.optText("badgeLabel"),
                features = item.optJSONArray("features").toStringList(),
                highlighted = item.optBoolean("highlighted"),
                accentColor = item.optText("accentColor"),
                durationDays = item.optInt("durationDays", 30),
            )
        }
        return items
    }

    private fun JSONArray?.toDiamondPackages(): List<BackendDiamondPackage> {
        if (this == null || length() == 0) return emptyList()
        val items = mutableListOf<BackendDiamondPackage>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            items += BackendDiamondPackage(
                id = item.optText("id"),
                name = item.optText("name"),
                diamonds = item.optInt("diamonds"),
                price = item.optText("price"),
                subtitle = item.optText("subtitle"),
                bonusLabel = item.optText("bonusLabel"),
                bestValue = item.optBoolean("bestValue"),
                accentColor = item.optText("accentColor"),
            )
        }
        return items
    }

    private fun JSONArray?.toPaymentProviders(): List<BackendPaymentProvider> {
        if (this == null || length() == 0) return emptyList()
        val items = mutableListOf<BackendPaymentProvider>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            items += BackendPaymentProvider(
                id = item.optText("id"),
                name = item.optText("name"),
                subtitle = item.optText("subtitle"),
                available = item.optBoolean("available"),
                recommended = item.optBoolean("recommended"),
                capabilities = item.optJSONArray("capabilities").toStringList(),
            )
        }
        return items
    }

    private fun JSONArray?.toCommerceOrders(): List<BackendCommerceOrder> {
        if (this == null || length() == 0) return emptyList()
        val items = mutableListOf<BackendCommerceOrder>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            items += parseCommerceOrder(item)
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
            refreshToken = data.optText("refreshToken"),
            nextCursor = data.optText("nextCursor").takeIf { it.isNotBlank() },
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
                id = item.optText("id"),
                title = item.optText("title"),
                description = item.optText("description"),
                bannerUrl = item.optText("bannerUrl"),
                members = item.optText("members"),
                moderator = item.optText("moderator"),
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
                id = item.optText("id"),
                title = item.optText("title"),
                kind = item.optText("kind"),
                dateLabel = item.optText("dateLabel"),
                location = item.optText("location"),
                price = item.optText("price"),
                bannerUrl = item.optText("bannerUrl"),
                attendees = item.optText("attendees"),
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
        val rawMediaUrl = item.optText("mediaUrl").takeIf { it.isNotBlank() }
        val rawMediaUrls = item.optJSONArray("mediaUrls").toStringList().ifEmpty {
            rawMediaUrl?.let { listOf(it) } ?: emptyList()
        }
        val mediaUrls = rawMediaUrls.mapNotNull { resolveBackendMediaUrl(it) }.distinct()
        return BackendCommunityPost(
            id = item.optText("id"),
            topicId = item.optText("topicId"),
            postType = item.optText("postType").ifBlank { "TEXT" },
            authorId = author?.optText("userId").orEmpty(),
            authorPublicId = author?.optText("publicId").orEmpty(),
            authorName = author?.optText("displayName").orEmpty(),
            authorAvatarUrl = resolvedBackendMediaUrl(author?.optText("avatarUrl")),
            authorVipTierId = author?.optText("vipTierId")?.takeIf { it.isNotBlank() },
            authorVipTierName = author?.optText("vipTierName")?.takeIf { it.isNotBlank() },
            authorPremium = author?.optBoolean("premium") == true,
            authorVerified = author?.optBoolean("verified") == true,
            authorOnline = author?.optBoolean("online") == true,
            authorCity = author?.optText("city").orEmpty(),
            text = item.optText("text"),
            mediaUrl = resolveBackendMediaUrl(rawMediaUrl) ?: mediaUrls.firstOrNull(),
            mediaUrls = mediaUrls,
            thumbnailUrl = resolveBackendMediaUrl(item.optText("thumbnailUrl").takeIf { it.isNotBlank() }),
            tags = item.optJSONArray("tags").toStringList(),
            mentionedUserIds = item.optJSONArray("mentionedUserIds").toStringList(),
            mentions = item.optJSONArray("mentions").toCommunityMentions(),
            likes = item.optInt("likes"),
            comments = item.optInt("comments"),
            commentsPreview = comments,
            shares = item.optInt("shares"),
            likedByMe = item.optBoolean("likedByMe"),
            sharedByMe = item.optBoolean("sharedByMe"),
            timeLabel = item.optText("timeLabel"),
            createdAt = item.optText("createdAt"),
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
                id = item.optText("id"),
                postId = item.optText("postId"),
                authorId = author?.optText("userId").orEmpty(),
                authorPublicId = author?.optText("publicId").orEmpty(),
                authorName = author?.optText("displayName").orEmpty(),
                authorAvatarUrl = resolvedBackendMediaUrl(author?.optText("avatarUrl")),
                authorVipTierId = author?.optText("vipTierId")?.takeIf { it.isNotBlank() },
                authorVipTierName = author?.optText("vipTierName")?.takeIf { it.isNotBlank() },
                authorPremium = author?.optBoolean("premium") == true,
                text = item.optText("text"),
                timeLabel = item.optText("timeLabel"),
                createdAt = item.optText("createdAt"),
                mine = item.optBoolean("mine"),
                mentionedUserIds = item.optJSONArray("mentionedUserIds").toStringList(),
                mentions = item.optJSONArray("mentions").toCommunityMentions(),
            )
        }
        return items
    }

    private fun JSONArray?.toCommunityMentions(): List<BackendCommunityMention> {
        if (this == null || length() == 0) {
            return emptyList()
        }
        val items = mutableListOf<BackendCommunityMention>()
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val userId = item.optText("userId")
            if (userId.isBlank()) continue
            items += BackendCommunityMention(
                userId = userId,
                displayName = item.optText("displayName"),
                username = item.optText("username"),
                avatarUrl = resolvedBackendMediaUrl(item.optText("avatarUrl")),
            )
        }
        return items.distinctBy { it.userId }
    }

    private fun JSONArray?.toResolvedMediaUrlList(): List<String> {
        return toStringList().map(::resolvedBackendMediaUrl).filter { it.isNotBlank() }.distinct()
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

    private fun resolvedBackendMediaUrl(value: String?): String {
        return resolveBackendMediaUrl(value) ?: value.orEmpty()
    }

    private fun resolveBackendMediaUrl(value: String?): String? {
        val url = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when {
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> url
            url.startsWith("content://", ignoreCase = true) || url.startsWith("file://", ignoreCase = true) -> url
            url.startsWith("/") -> baseUrl.trimEnd('/') + url
            else -> baseUrl.trimEnd('/') + "/" + url.trimStart('/')
        }
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private fun parseCallSession(json: JSONObject): BackendCallSession {
        val data = json.optJSONObject("data") ?: json
        val summaryJson = data.optJSONObject("summary")
        val callId = data.optText("id")
        val threadId = data.optText("threadId")
        return BackendCallSession(
            callId = callId,
            threadId = threadId,
            summary = summaryJson?.toCallSummary(fallbackThreadId = threadId, fallbackCallId = callId),
            status = data.optText("status"),
            minimized = data.optBoolean("minimized"),
        )
    }

    private fun JSONObject.toCallSummary(
        fallbackThreadId: String = "",
        fallbackCallId: String? = null,
    ): CallSummaryUiState? {
        val participantName = optText("participantName").takeIf { it.isNotBlank() } ?: return null
        return CallSummaryUiState(
            participantName = participantName,
            threadId = optText("threadId").ifBlank { fallbackThreadId },
            peerUserId = optText("peerUserId")
                .ifBlank { optText("partnerId") }
                .ifBlank { optText("callerId") },
            callId = optText("callId")
                .ifBlank { optText("id") }
                .takeIf { it.isNotBlank() } ?: fallbackCallId,
            callType = when (optText("callType").uppercase()) {
                "VIDEO" -> CallType.Video
                else -> CallType.Voice
            },
            direction = when (optText("direction").uppercase()) {
                "INCOMING" -> CallDirection.Incoming
                else -> CallDirection.Outgoing
            },
            durationSeconds = optInt("durationSeconds"),
            endReason = when (optText("endReason").uppercase()) {
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
            startedAtLabel = optText("startedAtLabel"),
            endedAtLabel = optText("endedAtLabel"),
            isMicOn = optBoolean("isMicOn", true),
            isVideoOn = optBoolean("isVideoOn", optText("callType").equals("VIDEO", ignoreCase = true)),
        )
    }

    private fun parseRealtimeConfig(data: JSONObject): BackendRealtimeConfig {
        val iceServers = mutableListOf<BackendIceServer>()
        val array = data.optJSONArray("iceServers")
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optText("url").takeIf { it.isNotBlank() } ?: continue
                iceServers += BackendIceServer(
                    url = url,
                    username = item.optText("username").takeIf { it.isNotBlank() },
                    credential = item.optText("credential").takeIf { it.isNotBlank() },
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

private fun JSONObject.optCleanString(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return optString(name).takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
}

/** Like optString, but JSON null (which org.json turns into the text "null") becomes "". */
private fun JSONObject.optText(name: String): String = optCleanString(name).orEmpty()

/** Backend enum: COMPLETED, MISSED, NO_ANSWER, DECLINED, REJECTED, BUSY, CANCELED, DROPPED. */
private fun CallEndReason.toBackendValue(): String = when (this) {
    CallEndReason.HungUp, CallEndReason.Completed -> "COMPLETED"
    CallEndReason.NoAnswer -> "NO_ANSWER"
    CallEndReason.Canceled -> "CANCELED"
    CallEndReason.Declined -> "DECLINED"
    CallEndReason.Rejected -> "REJECTED"
    CallEndReason.Missed -> "MISSED"
    CallEndReason.Busy -> "BUSY"
    CallEndReason.Dropped -> "DROPPED"
}
