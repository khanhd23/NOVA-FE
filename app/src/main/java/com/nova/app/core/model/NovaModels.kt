package com.nova.app.core.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.nova.app.core.navigation.AppRoute
import com.nova.app.core.navigation.ScreenStateKind

@Immutable
data class AppSettings(
    val darkMode: Boolean = true,
    val language: String = "English",
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val autoTranslateEnabled: Boolean = true,
    val incognitoEnabled: Boolean = false,
    val travelModeEnabled: Boolean = false,
    val premiumEnabled: Boolean = false,
    val locationSharingEnabled: Boolean = true,
    val photoVerificationEnabled: Boolean = true,
    val videoVerificationEnabled: Boolean = false,
    val identityVerificationEnabled: Boolean = true,
)

@Immutable
data class SessionState(
    val isFirstLaunch: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val profileCompleted: Boolean = false,
    val otpVerified: Boolean = false,
)

@Immutable
data class UserCard(
    val id: String,
    val name: String,
    val age: Int,
    val photoUrl: String,
    val verified: Boolean = false,
    val distanceKm: Int? = null,
    val online: Boolean = false,
    val city: String = "",
    val vipTierId: String? = null,
    val vipTierName: String? = null,
    val premium: Boolean = false,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val friendsCount: Int = 0,
    val followedByMe: Boolean = false,
    val followedByThem: Boolean = false,
    val friend: Boolean = false,
    val gender: String = "Not specified",
    val publicId: String = "",
)

@Immutable
data class StatCard(
    val label: String,
    val value: String,
    val detail: String? = null,
)

enum class ActionKind { Primary, Secondary, Ghost }

@Immutable
data class ScreenAction(
    val label: String,
    val icon: ImageVector? = null,
    val target: AppRoute? = null,
    val kind: ActionKind = ActionKind.Primary,
)

@Immutable
data class ScreenSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val heroLabel: String,
    val heroDescription: String,
    val stats: List<StatCard> = emptyList(),
    val chips: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
    val photos: List<String> = emptyList(),
    val actions: List<ScreenAction> = emptyList(),
    val state: ScreenStateKind = ScreenStateKind.Success,
    val accentTag: String? = null,
)

@Immutable
data class DiscoveryCandidate(
    val user: UserCard,
    val bio: String,
    val compatibility: Int,
    val commonInterests: List<String>,
    val iceBreaker: String,
    val mutualFriends: Int,
    val musicTaste: String,
    val height: String,
    val job: String,
    val relationshipGoal: String,
    val gallery: List<String>,
    val voiceIntro: Boolean = true,
    val videoIntro: Boolean = true,
)

@Immutable
data class StoryItem(
    val user: UserCard,
    val mediaUrl: String,
    val caption: String,
    val music: String,
    val viewed: Boolean = false,
    val expiresInHours: Int = 8,
)

@Immutable
data class FeedPost(
    val id: String,
    val author: UserCard,
    val caption: String,
    val mediaUrls: List<String>,
    val likes: Int,
    val comments: Int,
    val saves: Int,
    val tags: List<String>,
    val timeLabel: String,
)

@Immutable
data class ChatMessage(
    val id: String,
    val text: String = "",
    val sentByMe: Boolean,
    val timeLabel: String,
    val isVoice: Boolean = false,
    val isGif: Boolean = false,
    val isSticker: Boolean = false,
    val attachmentKind: ChatAttachmentKind? = null,
    val attachmentUrl: String? = null,
    val attachmentPreviewUrl: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentName: String? = null,
    val attachmentDurationSeconds: Int? = null,
    val translatedText: String? = null,
    val isRead: Boolean = false,
    val callSummary: CallSummaryUiState? = null,
) {
    val isCallLog: Boolean
        get() = callSummary != null

    val hasAttachment: Boolean
        get() = attachmentKind != null

    val isImageAttachment: Boolean
        get() = attachmentKind == ChatAttachmentKind.Image

    val isVideoAttachment: Boolean
        get() = attachmentKind == ChatAttachmentKind.Video

    val isAudioAttachment: Boolean
        get() = attachmentKind == ChatAttachmentKind.Audio || isVoice

    val isFileAttachment: Boolean
        get() = attachmentKind == ChatAttachmentKind.File
}

@Immutable
data class ChatThread(
    val id: String,
    val user: UserCard,
    val lastMessage: String,
    val unreadCount: Int,
    val online: Boolean,
    val typing: Boolean = false,
    val pinned: Boolean = false,
    val matchLabel: String = "New match",
)

@Immutable
data class CommunityTopic(
    val id: String,
    val title: String,
    val description: String,
    val bannerUrl: String,
    val members: String,
    val moderator: String,
    val eventCount: Int,
    val isJoined: Boolean = false,
)

@Immutable
data class CommunityPost(
    val id: String,
    val topic: String,
    val author: UserCard,
    val postType: String = "TEXT",
    val text: String,
    val mediaUrl: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val tags: List<String> = emptyList(),
    val mentionedUserIds: List<String> = emptyList(),
    val likes: Int,
    val comments: Int,
    val commentsPreview: List<CommunityComment> = emptyList(),
    val shares: Int = 0,
    val likedByMe: Boolean = false,
    val sharedByMe: Boolean = false,
    val timeLabel: String,
) {
    val allMediaUrls: List<String>
        get() = (if (mediaUrls.isNotEmpty()) mediaUrls else mediaUrl?.let { listOf(it) } ?: emptyList()).normalizedPostMediaUrls()

    val hasVideoMedia: Boolean
        get() = postType.equals("VIDEO", ignoreCase = true) || allMediaUrls.hasVideoMedia()

    val hasImageMedia: Boolean
        get() = allMediaUrls.hasImageMedia()

    val hasMixedMedia: Boolean
        get() = allMediaUrls.hasMixedMedia()

    val hasMultipleMedia: Boolean
        get() = allMediaUrls.size > 1
}

@Immutable
data class CommunityComment(
    val id: String,
    val postId: String,
    val author: UserCard,
    val text: String,
    val timeLabel: String,
    val mine: Boolean = false,
    val mentionedUserIds: List<String> = emptyList(),
)

@Immutable
data class EventItem(
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

@Immutable
data class CompatibilityMetric(
    val label: String,
    val value: Int,
    val note: String,
)

@Immutable
data class BadgeItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val progress: Int,
    val iconLabel: String,
    val unlocked: Boolean,
)

@Immutable
data class WalletEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: String,
    val timeLabel: String,
    val positive: Boolean,
)

@Immutable
data class PremiumPlan(
    val name: String,
    val price: String,
    val cycle: String,
    val subtitle: String,
    val features: List<String>,
    val highlighted: Boolean = false,
)

@Immutable
data class SafetyItem(
    val title: String,
    val description: String,
    val actionLabel: String,
    val enabled: Boolean = false,
)

@Immutable
data class AdminMetric(
    val label: String,
    val value: String,
    val delta: String,
    val positive: Boolean,
)

@Immutable
data class NotificationItem(
    val id: String,
    val title: String,
    val description: String,
    val timeLabel: String,
    val type: String,
    val unread: Boolean,
    val actionTarget: String? = null,
    val threadId: String? = null,
)

@Immutable
data class SearchResultItem(
    val id: String,
    val name: String,
    val gender: String,
    val interests: List<String>,
    val avatarUrl: String,
    val vipTierId: String? = null,
    val vipTierName: String? = null,
    val premium: Boolean = false,
    val verified: Boolean = false,
    val city: String = "",
    val distanceKm: Int? = null,
    val online: Boolean = false,
    val publicId: String = "",
)

@Immutable
data class SearchUiState(
    val query: String = "",
    val selectedGender: String = "All",
    val loading: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val total: Long = 0,
    val hasMore: Boolean = false,
    val error: String? = null,
)

@Immutable
data class NotificationsUiState(
    val items: List<NotificationItem> = emptyList(),
    val unreadCount: Int = 0,
    val loading: Boolean = false,
)

@Immutable
data class ProfileConnectionItem(
    val id: String,
    val name: String,
    val username: String,
    val avatarUrl: String,
    val gender: String,
    val interests: List<String>,
    val vipTierId: String? = null,
    val vipTierName: String? = null,
    val premium: Boolean = false,
    val verified: Boolean = false,
    val city: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val friendsCount: Int = 0,
    val followedByMe: Boolean = false,
    val followedByThem: Boolean = false,
    val friend: Boolean = false,
)

@Immutable
data class ProfileConnectionsUiState(
    val userId: String = "",
    val userName: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val city: String = "",
    val verified: Boolean = false,
    val online: Boolean = false,
    val premium: Boolean = false,
    val vipTierId: String? = null,
    val vipTierName: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val friendsCount: Int = 0,
    val interests: List<String> = emptyList(),
    val isSelf: Boolean = false,
    val selectedTab: String = "followers",
    val tabs: List<String> = listOf("followers", "friends", "following"),
    val items: List<ProfileConnectionItem> = emptyList(),
    val page: Int = 0,
    val size: Int = 50,
    val total: Long = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val profileLoaded: Boolean = false,
)

@Immutable
data class SearchFilter(
    val name: String,
    val value: String,
)

@Immutable
data class LaunchUiState(
    val target: AppRoute,
)

@Immutable
data class HomeUiState(
    val stories: List<StoryItem>,
    val feed: List<FeedPost>,
    val featured: DiscoveryCandidate,
    val events: List<EventItem>,
    val communities: List<CommunityTopic>,
    val suggestions: List<String>,
)

@Immutable
data class DiscoverUiState(
    val queue: List<DiscoveryCandidate>,
    val activeIndex: Int,
    val liked: Int = 0,
    val superLiked: Int = 0,
    val saved: Int = 0,
    val skipped: Int = 0,
    val iceBreakerHint: String = "Ask about their latest trip or favorite playlist.",
)

@Immutable
data class MessagesUiState(
    val threads: List<ChatThread>,
    val onlineNow: Int,
    val filters: List<String>,
    val searchHint: String,
)

@Immutable
data class ChatUiState(
    val thread: ChatThread,
    val messages: List<ChatMessage>,
    val typing: Boolean,
    val suggestions: List<String>,
    val translationEnabled: Boolean,
    val callHint: String,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
)

@Immutable
data class CommunityUiState(
    val topics: List<CommunityTopic>,
    val posts: List<CommunityPost>,
    val events: List<EventItem>,
    val trending: List<String>,
    val selectedTab: String = "for_you",
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
    val refreshToken: String = "",
    val postTypes: List<String> = listOf("TEXT", "IMAGE", "VIDEO", "MIXED", "VOICE", "LINK", "POLL"),
)

@Immutable
data class CreatePostDraft(
    val topicId: String,
    val text: String,
    val postType: String = "TEXT",
    val mediaUrl: String? = null,
    val mediaUrls: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val tags: List<String> = emptyList(),
    val mentionedUserIds: List<String> = emptyList(),
)

@Immutable
data class ProfileUiState(
    val user: UserCard,
    val bio: String,
    val featuredPhotos: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val posts: List<CommunityPost> = emptyList(),
    val diamonds: Int = 100,
    val prompts: List<String>,
    val badges: List<BadgeItem>,
    val stats: List<StatCard>,
    val settings: AppSettings,
    val wallet: List<WalletEntry>,
    val safety: List<SafetyItem>,
    val plans: List<PremiumPlan>,
    val notifications: List<NotificationItem>,
    val genericScreens: List<ScreenSpec>,
    val adminMetrics: List<AdminMetric>,
    val compatibility: List<CompatibilityMetric>,
    val filters: List<SearchFilter>,
)

object SampleMedia {
    val portrait1 = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=900&q=80"
    val portrait2 = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=900&q=80"
    val portrait3 = "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=900&q=80"
    val portrait4 = "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?auto=format&fit=crop&w=900&q=80"
    val portrait5 = "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=900&q=80"
    val portrait6 = "https://images.unsplash.com/photo-1500917293891-ef795e70e1f6?auto=format&fit=crop&w=900&q=80"
    val portrait7 = "https://images.unsplash.com/photo-1488426862026-3ee34a7d66df?auto=format&fit=crop&w=900&q=80"
    val portrait8 = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=900&q=80"
    val portrait9 = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=900&q=80"
    val portrait10 = "https://images.unsplash.com/photo-1521119989659-a83eee488004?auto=format&fit=crop&w=900&q=80"

    val landscape1 = "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1400&q=80"
    val landscape2 = "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?auto=format&fit=crop&w=1400&q=80"
    val landscape3 = "https://images.unsplash.com/photo-1493246507139-91e8fad9978e?auto=format&fit=crop&w=1400&q=80"
    val landscape4 = "https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop&w=1400&q=80"
}
