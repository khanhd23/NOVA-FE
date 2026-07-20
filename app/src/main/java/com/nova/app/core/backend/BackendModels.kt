package com.nova.app.core.backend

import android.net.Uri
import com.nova.app.core.model.CallDirection
import com.nova.app.core.model.CallEndReason
import com.nova.app.core.model.CallSummaryUiState
import com.nova.app.core.model.CallType
import com.nova.app.core.model.ChatAttachmentKind
import com.nova.app.core.model.ChatMessage
import com.nova.app.core.model.ChatThread
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
    val age: Int,
    val avatarUrl: String,
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
)

data class BackendSearchPage(
    val items: List<BackendSearchUser>,
    val page: Int,
    val size: Int,
    val total: Long,
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

data class BackendChatMessage(
    val id: String,
    val threadId: String,
    val text: String,
    val sentByMe: Boolean,
    val timeLabel: String,
    val isVoice: Boolean = false,
    val isGif: Boolean = false,
    val isSticker: Boolean = false,
    val attachmentKind: String? = null,
    val attachmentUrl: String? = null,
    val attachmentPreviewUrl: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentName: String? = null,
    val attachmentDurationSeconds: Int? = null,
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
    MESSAGE_RECALLED,
    MESSAGE_DELETED,
    THREAD_DELETED,
    THREAD_READ,
    THREAD_TYPING,
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
    val mine: Boolean = false,
    val mentionedUserIds: List<String> = emptyList(),
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
    val likes: Int = 0,
    val comments: Int = 0,
    val commentsPreview: List<BackendCommunityComment> = emptyList(),
    val shares: Int = 0,
    val likedByMe: Boolean = false,
    val sharedByMe: Boolean = false,
    val timeLabel: String = "",
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
    val resolvedKind = attachmentKind.toChatAttachmentKind() ?: if (isVoice) ChatAttachmentKind.Audio else null
    return ChatMessage(
        id = id,
        text = when {
            status.equals("RECALLED", ignoreCase = true) && sentByMe -> "You unsent a message"
            status.equals("RECALLED", ignoreCase = true) -> "This message was unsent"
            callSummary != null -> ""
            else -> text
        },
        sentByMe = sentByMe,
        timeLabel = timeLabel,
        isVoice = isVoice || resolvedKind == ChatAttachmentKind.Audio,
        isGif = isGif,
        isSticker = isSticker,
        attachmentKind = resolvedKind,
        attachmentUrl = attachmentUrl,
        attachmentPreviewUrl = attachmentPreviewUrl,
        attachmentMimeType = attachmentMimeType,
        attachmentName = attachmentName,
        attachmentDurationSeconds = attachmentDurationSeconds,
        translatedText = translatedText,
        isRead = isRead || status.equals("SEEN", ignoreCase = true) || status.equals("RECALLED", ignoreCase = true),
        callSummary = callSummary,
    )
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
    if (type != BackendRealtimeEventType.MESSAGE_CREATED && type != BackendRealtimeEventType.MESSAGE_RECALLED) {
        return null
    }

    val callSummary = if (payload["kind"] == "CALL_LOG") payload.toCallSummary() else null
    val status = payload["status"] ?: "SENT"
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
        else -> payload["text"].orEmpty()
    }

    return ChatMessage(
        id = messageId ?: payload["messageId"].orEmpty(),
        text = text,
        sentByMe = sentByMe,
        timeLabel = payload["timeLabel"].orEmpty(),
        isVoice = payloadBoolean("voice"),
        isGif = payloadBoolean("gif"),
        isSticker = payloadBoolean("sticker"),
        attachmentKind = attachmentKind,
        attachmentUrl = payload["attachmentUrl"]?.takeIf { it.isNotBlank() },
        attachmentPreviewUrl = payload["attachmentPreviewUrl"]?.takeIf { it.isNotBlank() },
        attachmentMimeType = payload["attachmentMimeType"]?.takeIf { it.isNotBlank() },
        attachmentName = payload["attachmentName"]?.takeIf { it.isNotBlank() },
        attachmentDurationSeconds = payload["attachmentDurationSeconds"]?.toIntOrNull(),
        translatedText = null,
        isRead = status.equals("SEEN", ignoreCase = true) || status.equals("RECALLED", ignoreCase = true),
        callSummary = callSummary,
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
            ?: this["partnerName"]
            ?: this["summaryText"]
            ?: "",
        threadId = this["threadId"].orEmpty(),
        peerUserId = this["peerUserId"]
            ?: this["partnerId"]
            ?: this["fromUserId"]
            ?: this["actorUserId"]
            ?: "",
        callId = this["callId"]?.takeIf { it.isNotBlank() },
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
