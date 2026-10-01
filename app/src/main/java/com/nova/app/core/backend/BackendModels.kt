package com.nova.app.core.backend

import android.net.Uri
import com.nova.app.core.model.CallDirection
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.model.CallSummaryUiState
import com.nova.app.core.model.CallType
import com.nova.app.core.model.ChatAttachmentKind
import com.nova.app.core.model.ChatMessage
import com.nova.app.core.model.ChatThread
import com.nova.app.core.model.CommunityComment
import com.nova.app.core.model.CommunityMention
import com.nova.app.core.model.UserCard
import java.util.Locale

enum class BackendAuthProvider(
    val backendValue: String,
    val devToken: String,
) {
    Google("GOOGLE", "dev:current"),
    Facebook("FACEBOOK", "dev:current"),
}

data class BackendSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val publicId: String = "",
    val displayName: String,
    val avatarUrl: String? = null,
    val onboardingComplete: Boolean = false,
    val profileComplete: Boolean = false,
)

data class BackendProfile(
    val userId: String,
    val displayName: String,
    val username: String,
    val bio: String,
    val avatarUrl: String,
    val featuredPhotos: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val age: Int = 0,
    val city: String = "",
    val gender: String = "Not specified",
    val verified: Boolean = false,
    val online: Boolean = false,
    val premium: Boolean = false,
    val vipTierId: String? = null,
    val vipTierName: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val friendsCount: Int = 0,
    val followedByMe: Boolean = false,
    val followedByThem: Boolean = false,
    val friend: Boolean = false,
    val onboardingComplete: Boolean = false,
    val profileComplete: Boolean = false,
    val publicId: String = "",
)

data class BackendProfilePage(
    val items: List<BackendProfile>,
    val page: Int,
    val size: Int,
    val total: Long,
)

data class BackendSearchUser(
    val userId: String,
    val displayName: String,
    val bio: String = "",
    val age: Int,
    val avatarUrl: String,
    val username: String = "",
    val vipTierId: String? = null,
    val vipTierName: String? = null,
    val premium: Boolean = false,
    val verified: Boolean = false,
    val distanceKm: Int? = null,
    val online: Boolean = false,
    val city: String = "",
    val gender: String = "Not specified",
    val interests: List<String> = emptyList(),
    val publicId: String = "",
    val friend: Boolean = false,
)

data class BackendSearchPage(
    val items: List<BackendSearchUser>,
    val page: Int,
    val size: Int,
    val total: Long,
)

data class BackendDiscoveryCandidate(
    val candidateId: String,
    val user: BackendPublicUserCard,
    val bio: String,
    val compatibility: Int,
    val commonInterests: List<String> = emptyList(),
    val iceBreaker: String,
    val mutualFriends: Int,
    val musicTaste: String,
    val height: String,
    val job: String,
    val relationshipGoal: String,
    val gallery: List<String> = emptyList(),
    val voiceIntro: Boolean = true,
    val videoIntro: Boolean = true,
)

data class BackendDiscoverResponse(
    val items: List<BackendDiscoveryCandidate>,
    val filters: List<String> = emptyList(),
)

data class BackendSwipeResponse(
    val matched: Boolean,
    val message: String,
    val nextCandidateId: String? = null,
)

data class BackendPokeResponse(
    val delivered: Boolean,
    val message: String,
    val nextCandidateId: String? = null,
)

data class BackendCommerceCatalog(
    val vipTiers: List<BackendVipTier> = emptyList(),
    val diamondPackages: List<BackendDiamondPackage> = emptyList(),
    val paymentProviders: List<BackendPaymentProvider> = emptyList(),
)

data class BackendVipTier(
    val id: String,
    val name: String,
    val level: Int,
    val price: String,
    val cycle: String,
    val subtitle: String,
    val badgeLabel: String,
    val features: List<String> = emptyList(),
    val highlighted: Boolean = false,
    val accentColor: String = "",
    val durationDays: Int = 30,
)

data class BackendDiamondPackage(
    val id: String,
    val name: String,
    val diamonds: Int,
    val price: String,
    val subtitle: String,
    val bonusLabel: String,
    val bestValue: Boolean = false,
    val accentColor: String = "",
)

data class BackendPaymentProvider(
    val id: String,
    val name: String,
    val subtitle: String,
    val available: Boolean = false,
    val recommended: Boolean = false,
    val capabilities: List<String> = emptyList(),
)

data class BackendCommerceMe(
    val userId: String,
    val vipActive: Boolean = false,
    val vipTierId: String? = null,
    val vipTierName: String? = null,
    val vipExpiresAt: String? = null,
    val diamondBalance: Long = 0L,
    val activeBenefits: List<String> = emptyList(),
    val recentOrders: List<BackendCommerceOrder> = emptyList(),
)

data class BackendCommerceOrder(
    val orderId: String,
    val userId: String,
    val purchaseType: String,
    val productId: String,
    val productName: String,
    val productSubtitle: String,
    val amount: Int,
    val currency: String,
    val status: String,
    val provider: String,
    val checkoutUrl: String? = null,
    val qrContent: String? = null,
    val expiresAt: String? = null,
    val grant: BackendCommerceGrant? = null,
    val transactionId: String? = null,
    val failureReason: String? = null,
)

data class BackendCommerceGrant(
    val grantType: String,
    val vipTierId: String? = null,
    val vipTierName: String? = null,
    val vipExpiresAt: String? = null,
    val diamondsAdded: Int? = null,
    val diamondBalanceAfter: Long? = null,
    val benefits: List<String> = emptyList(),
)

data class BackendPublicUserCard(
    val userId: String,
    val displayName: String,
    val username: String,
    val bio: String,
    val age: Int,
    val avatarUrl: String,
    val featuredPhotos: List<String> = emptyList(),
    val vipTierId: String? = null,
    val vipTierName: String? = null,
    val verified: Boolean = false,
    val premium: Boolean = false,
    val distanceKm: Int? = null,
    val online: Boolean = false,
    val city: String = "",
    val gender: String = "Not specified",
    val interests: List<String> = emptyList(),
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val friendsCount: Int = 0,
    val followedByThem: Boolean = false,
    val friend: Boolean = false,
    val followedByMe: Boolean = false,
    val publicId: String = "",
)

data class BackendChatThread(
    val id: String,
    val type: String,
    val participant: BackendPublicUserCard,
    val lastMessage: String,
    val unreadCount: Int,
    val online: Boolean,
    val typing: Boolean,
    val pinned: Boolean,
    val matchLabel: String,
    val updatedAt: String,
)

data class BackendChatThreadPage(
    val items: List<BackendChatThread>,
    val page: Int,
    val size: Int,
    val total: Long,
)

data class BackendChatMessage(
    val id: String,
    val threadId: String,
    val text: String,
    val sentByMe: Boolean,
    val timeLabel: String,
    val createdAt: String? = null,
    val isVoice: Boolean = false,
    val isGif: Boolean = false,
    val isSticker: Boolean = false,
    val attachmentKind: String? = null,
    val attachmentUrl: String? = null,
    val attachmentPreviewUrl: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentName: String? = null,
    val attachmentDurationSeconds: Int? = null,
    val attachmentWidth: Int? = null,
    val attachmentHeight: Int? = null,
    val translatedText: String? = null,
    val isRead: Boolean = false,
    val callSummary: CallSummaryUiState? = null,
    val status: String = "SENT",
)

data class BackendThreadDetailResponse(
    val thread: BackendChatThread,
    val messages: List<BackendChatMessage>,
    val hasMore: Boolean,
    val nextCursor: String? = null,
)

data class BackendProfileUpdateRequest(
    val displayName: String,
    val bio: String? = null,
    val city: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val photoUrl: String? = null,
    val featuredPhotos: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
)

data class BackendCallSession(
    val callId: String,
    val threadId: String,
    val summary: CallSummaryUiState? = null,
    val status: String,
    val minimized: Boolean,
)

data class BackendDeviceTokenRequest(
    val token: String,
    val platform: String = "ANDROID",
    val deviceId: String,
    val appVersion: String,
)

enum class BackendRealtimeEventType {
    CONNECTION_READY,
    MESSAGE_CREATED,
    MESSAGE_UPDATED,
    MESSAGE_RECALLED,
    MESSAGE_DELETED,
    THREAD_DELETED,
    THREAD_READ,
    THREAD_TYPING,
    USER_PRESENCE,
    CALL_STARTED,
    CALL_ANSWERED,
    CALL_ENDED,
    CALL_MINIMIZED,
    CALL_SIGNAL,
    NOTIFICATION_CREATED,
    PING,
    UNKNOWN,
}

data class BackendRealtimeEvent(
    val id: String,
    val type: BackendRealtimeEventType,
    val room: String? = null,
    val actorUserId: String? = null,
    val targetUserId: String? = null,
    val threadId: String? = null,
    val callId: String? = null,
    val messageId: String? = null,
    val title: String? = null,
    val body: String? = null,
    val payload: Map<String, String> = emptyMap(),
    val timestamp: String? = null,
)

data class BackendCallSignal(
    val targetUserId: String,
    val threadId: String,
    val callId: String,
    val signalType: String,
    val sdpType: String? = null,
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val video: Boolean = false,
)

data class BackendIceServer(
    val url: String,
    val username: String? = null,
    val credential: String? = null,
)

data class BackendRealtimeConfig(
    val iceServers: List<BackendIceServer> = emptyList(),
    val maxRestartAttempts: Int = 2,
    val restartBackoffMs: Long = 1000L,
)

data class BackendMediaAsset(
    val id: String,
    val title: String,
    val url: String,
    val mimeType: String,
    val kind: String,
    val previewUrl: String? = null,
)

data class BackendNotification(
    val id: String,
    val kind: String,
    val threadId: String? = null,
    val title: String,
    val body: String,
    val timeLabel: String,
    val read: Boolean,
    val actionTarget: String? = null,
)

data class BackendCommunityComment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String,
    val authorVipTierId: String? = null,
    val authorVipTierName: String? = null,
    val authorPremium: Boolean = false,
    val text: String,
    val timeLabel: String,
    val createdAt: String = "",
    val mine: Boolean = false,
    val mentionedUserIds: List<String> = emptyList(),
    val mentions: List<BackendCommunityMention> = emptyList(),
    val authorPublicId: String = "",
)

data class BackendCommunityMention(
    val userId: String,
    val displayName: String,
    val username: String = "",
    val avatarUrl: String = "",
)

data class BackendCommunityPost(
    val id: String,
    val topicId: String,
    val postType: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String,
    val authorVipTierId: String? = null,
    val authorVipTierName: String? = null,
    val authorPremium: Boolean = false,
    val authorVerified: Boolean = false,
    val authorOnline: Boolean = false,
    val authorCity: String = "",
    val text: String,
    val mediaUrl: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val tags: List<String> = emptyList(),
    val mentionedUserIds: List<String> = emptyList(),
    val mentions: List<BackendCommunityMention> = emptyList(),
    val likes: Int = 0,
    val comments: Int = 0,
    val commentsPreview: List<BackendCommunityComment> = emptyList(),
    val shares: Int = 0,
    val likedByMe: Boolean = false,
    val sharedByMe: Boolean = false,
    val timeLabel: String = "",
    val createdAt: String = "",
    val authorPublicId: String = "",
)

data class BackendCommunityCommentPage(
    val items: List<BackendCommunityComment>,
    val page: Int,
    val size: Int,
    val total: Long,
)

data class BackendCommunityFeed(
    val topics: List<BackendCommunityTopic>,
    val posts: List<BackendCommunityPost>,
    val events: List<BackendCommunityEvent>,
    val trendingTags: List<String>,
    val postTypes: List<String>,
    val refreshToken: String,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

data class BackendCommunityPostPage(
    val items: List<BackendCommunityPost>,
    val page: Int,
    val size: Int,
    val total: Long,
)

data class BackendCommunityTopic(
    val id: String,
    val title: String,
    val description: String,
    val bannerUrl: String,
    val members: String,
    val moderator: String,
    val eventCount: Int,
    val joined: Boolean = false,
)

data class BackendCommunityEvent(
    val id: String,
    val title: String,
    val kind: String,
    val dateLabel: String,
    val location: String,
    val price: String,
    val bannerUrl: String,
    val attendees: String,
    val joined: Boolean = false,
)

data class BackendCommunityTagSuggestion(
    val tag: String,
    val hotness: Int,
    val postCount: Int,
    val exactMatch: Boolean = false,
    val canCreate: Boolean = true,
)

data class BackendCommunityPostRequest(
    val topicId: String,
    val text: String,
    val postType: String = "TEXT",
    val mediaUrl: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val tags: List<String> = emptyList(),
    val mentionedUserIds: List<String> = emptyList(),
)

data class BackendCommunityCommentRequest(
    val text: String,
)

data class BackendCommunityLikeRequest(
    val liked: Boolean = true,
)

data class BackendCommunityShareRequest(
    val target: String = "profile",
    val recipientUserId: String? = null,
    val copyLink: Boolean = true,
)

data class BackendCommunityShareResponse(
    val shareUrl: String,
    val post: BackendCommunityPost,
)

data class BackendMediaUploadRequest(
    val uri: Uri,
    val fileName: String,
    val title: String,
    val mimeType: String,
    val kind: ChatAttachmentKind,
    val previewUrl: String? = null,
)

data class BackendMessageAttachment(
    val url: String,
    val previewUrl: String? = null,
    val mimeType: String? = null,
    val name: String? = null,
    val kind: ChatAttachmentKind,
    val durationSeconds: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
)

fun BackendPublicUserCard.toUserCard(): UserCard {
    return UserCard(
        id = userId.ifBlank { displayName.ifBlank { "user" } },
        publicId = publicId,
        name = displayName.ifBlank { "Chat" },
        age = age,
        photoUrl = avatarUrl,
        verified = verified,
        online = online,
        city = city,
        vipTierId = vipTierId,
        vipTierName = vipTierName,
        premium = premium,
        gender = gender,
    )
}

fun BackendCommunityComment.toCommunityComment(): CommunityComment {
    return CommunityComment(
        id = id,
        postId = postId,
        author = UserCard(
            id = authorId,
            publicId = authorPublicId,
            name = authorName.ifBlank { "Nova User" },
            age = 0,
            photoUrl = authorAvatarUrl,
            vipTierId = authorVipTierId,
            vipTierName = authorVipTierName,
            premium = authorPremium,
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

fun BackendDiscoveryCandidate.toDiscoveryCandidate(): com.nova.app.core.model.DiscoveryCandidate {
    return com.nova.app.core.model.DiscoveryCandidate(
        candidateId = candidateId,
        user = user.toUserCard(),
        bio = bio,
        compatibility = compatibility,
        commonInterests = commonInterests,
        iceBreaker = iceBreaker,
        mutualFriends = mutualFriends,
        musicTaste = musicTaste,
        height = height,
        job = job,
        relationshipGoal = relationshipGoal,
        gallery = gallery,
        voiceIntro = voiceIntro,
        videoIntro = videoIntro,
    )
}

fun BackendChatThread.toChatThread(): ChatThread {
    return ChatThread(
        id = id,
        user = participant.toUserCard(),
        lastMessage = lastMessage,
        unreadCount = unreadCount,
        online = online,
        typing = typing,
        pinned = pinned,
        matchLabel = matchLabel,
    )
}

fun BackendChatMessage.toChatMessage(currentUserId: String? = null): ChatMessage {
    val recalled = status.equals("RECALLED", ignoreCase = true)
    val resolvedKind = attachmentKind.toChatAttachmentKind() ?: if (isVoice) ChatAttachmentKind.Audio else null
    return ChatMessage(
        id = id,
        text = when {
            recalled && sentByMe -> "You unsent a message"
            recalled -> "This message was unsent"
            callSummary != null -> ""
            else -> text
        },
        sentByMe = sentByMe,
        timeLabel = timeLabel,
        createdAt = createdAt,
        isVoice = !recalled && (isVoice || resolvedKind == ChatAttachmentKind.Audio),
        isGif = !recalled && isGif,
        isSticker = !recalled && isSticker,
        attachmentKind = if (recalled) null else resolvedKind,
        attachmentUrl = if (recalled) null else attachmentUrl,
        attachmentPreviewUrl = if (recalled) null else attachmentPreviewUrl,
        attachmentMimeType = if (recalled) null else attachmentMimeType,
        attachmentName = if (recalled) null else attachmentName,
        attachmentDurationSeconds = if (recalled) null else attachmentDurationSeconds,
        attachmentWidth = if (recalled) null else attachmentWidth,
        attachmentHeight = if (recalled) null else attachmentHeight,
        translatedText = translatedText.cleanNullableText(),
        isRead = isRead || status.equals("SEEN", ignoreCase = true) || status.equals("RECALLED", ignoreCase = true),
        callSummary = callSummary,
        status = status.cleanMessageStatus(),
    )
}

private fun String?.cleanNullableText(): String? {
    return this?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
}

private fun String.cleanMessageStatus(): String {
    return takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) } ?: "SENT"
}

fun String?.toChatAttachmentKind(): ChatAttachmentKind? {
    return when (this?.uppercase(Locale.ROOT)) {
        "IMAGE" -> ChatAttachmentKind.Image
        "VIDEO" -> ChatAttachmentKind.Video
        "AUDIO", "VOICE" -> ChatAttachmentKind.Audio
        "FILE" -> ChatAttachmentKind.File
        else -> null
    }
}

fun BackendRealtimeEvent.payloadString(key: String, default: String = ""): String {
    return payload[key] ?: default
}

fun BackendRealtimeEvent.payloadBoolean(key: String): Boolean {
    return payload[key]?.equals("true", ignoreCase = true) == true
}

fun BackendRealtimeEvent.toChatMessage(currentUserId: String?): ChatMessage? {
    if (type != BackendRealtimeEventType.MESSAGE_CREATED &&
        type != BackendRealtimeEventType.MESSAGE_UPDATED &&
        type != BackendRealtimeEventType.MESSAGE_RECALLED
    ) {
        return null
    }

    val callSummary = if (payload["kind"] == "CALL_LOG") payload.toCallSummary() else null
    val status = (payload["status"] ?: "SENT").cleanMessageStatus()
    val recalled = status.equals("RECALLED", ignoreCase = true)
    val sentByMe = currentUserId != null && actorUserId == currentUserId
    val attachmentKind = when (payload["attachmentKind"]?.uppercase(Locale.ROOT)) {
        "IMAGE" -> ChatAttachmentKind.Image
        "VIDEO" -> ChatAttachmentKind.Video
        "AUDIO", "VOICE" -> ChatAttachmentKind.Audio
        "FILE" -> ChatAttachmentKind.File
        else -> null
    }
    val text = when {
        status.equals("RECALLED", ignoreCase = true) && sentByMe -> "You unsent a message"
        status.equals("RECALLED", ignoreCase = true) -> "This message was unsent"
        callSummary != null -> ""
        else -> payload["text"].cleanNullableText().orEmpty()
    }

    return ChatMessage(
        id = messageId ?: payload["messageId"].orEmpty(),
        text = text,
        sentByMe = sentByMe,
        timeLabel = payload["timeLabel"].orEmpty(),
        createdAt = payload["createdAt"].cleanNullableText() ?: timestamp.cleanNullableText(),
        isVoice = !recalled && payloadBoolean("voice"),
        isGif = !recalled && payloadBoolean("gif"),
        isSticker = !recalled && payloadBoolean("sticker"),
        attachmentKind = if (recalled) null else attachmentKind,
        attachmentUrl = if (recalled) null else payload["attachmentUrl"].cleanNullableText(),
        attachmentPreviewUrl = if (recalled) null else payload["attachmentPreviewUrl"].cleanNullableText(),
        attachmentMimeType = if (recalled) null else payload["attachmentMimeType"].cleanNullableText(),
        attachmentName = if (recalled) null else payload["attachmentName"].cleanNullableText(),
        attachmentDurationSeconds = if (recalled) null else payload["attachmentDurationSeconds"]?.toIntOrNull(),
        attachmentWidth = if (recalled) null else payload["attachmentWidth"]?.toIntOrNull(),
        attachmentHeight = if (recalled) null else payload["attachmentHeight"]?.toIntOrNull(),
        translatedText = null,
        isRead = status.equals("SEEN", ignoreCase = true) || status.equals("RECALLED", ignoreCase = true),
        callSummary = callSummary,
        status = status,
    )
}

fun BackendRealtimeEvent.toCallSummary(): CallSummaryUiState? {
    if (type != BackendRealtimeEventType.CALL_STARTED &&
        type != BackendRealtimeEventType.CALL_ANSWERED &&
        type != BackendRealtimeEventType.CALL_ENDED &&
        type != BackendRealtimeEventType.CALL_MINIMIZED
    ) {
        return payload.toCallSummaryOrNull()
    }
    return payload.toCallSummaryOrNull()
}

fun Map<String, String>.toCallSummary(): CallSummaryUiState? {
    return toCallSummaryOrNull()
}

private fun Map<String, String>.toCallSummaryOrNull(): CallSummaryUiState? {
    val callType = when (this["callType"]?.uppercase(Locale.ROOT)) {
        "VIDEO" -> CallType.Video
        "VOICE" -> CallType.Voice
        else -> null
    } ?: return null

    val direction = when (this["direction"]?.uppercase(Locale.ROOT)) {
        "INCOMING" -> CallDirection.Incoming
        "OUTGOING" -> CallDirection.Outgoing
        else -> CallDirection.Outgoing
    }

    val endReason = when (this["endReason"]?.uppercase(Locale.ROOT)) {
        "COMPLETED" -> CallEndReason.Completed
        "MISSED" -> CallEndReason.Missed
        "NO_ANSWER" -> CallEndReason.NoAnswer
        "DECLINED" -> CallEndReason.Declined
        "REJECTED" -> CallEndReason.Rejected
        "BUSY" -> CallEndReason.Busy
        "CANCELED" -> CallEndReason.Canceled
        "DROPPED" -> CallEndReason.Dropped
        else -> CallEndReason.HungUp
    }

    return CallSummaryUiState(
        participantName = this["participantName"]
            ?: this["peerName"]
            ?: this["callerName"]
            ?: this["partnerName"]
            ?: this["summaryText"]
            ?: "",
        threadId = this["threadId"].orEmpty(),
        peerUserId = this["peerUserId"]
            ?: this["callerId"]
            ?: this["partnerId"]
            ?: this["fromUserId"]
            ?: this["actorUserId"]
            ?: "",
        callId = this["callId"]?.takeIf { it.isNotBlank() }
            ?: this["id"]?.takeIf { it.isNotBlank() },
        callType = callType,
        direction = direction,
        durationSeconds = this["durationSeconds"]?.toIntOrNull() ?: 0,
        endReason = endReason,
        startedAtLabel = this["startedAtLabel"].orEmpty(),
        endedAtLabel = this["endedAtLabel"].orEmpty(),
        isMicOn = this["micOn"]?.toBooleanStrictOrNull() ?: true,
        isVideoOn = this["videoOn"]?.toBooleanStrictOrNull() ?: callType == CallType.Video,
    )
}

fun String?.toBooleanStrictOrNull(): Boolean? {
    return when (this?.lowercase(Locale.ROOT)) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
