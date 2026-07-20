package com.nova.app.core.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.nova.app.core.model.ScreenSpec

sealed interface AppRoute {
    data object Splash : AppRoute
    data object Onboarding : AppRoute
    data object SignIn : AppRoute
    data object Otp : AppRoute
    data object ProfileSetup : AppRoute
    data object Interests : AppRoute
    data object Home : AppRoute
    data object Discover : AppRoute
    data object ProfileDetail : AppRoute
    data object ProfileConnections : AppRoute
    data object Messages : AppRoute
    data object Community : AppRoute
    data object Profile : AppRoute
    data object Story : AppRoute
    data object Feed : AppRoute
    data object AICompatibility : AppRoute
    data object MatchSuccess : AppRoute
    data object Chat : AppRoute
    data object VoiceCall : AppRoute
    data object VideoCall : AppRoute
    data object CallSummary : AppRoute
    data object CommunityDetail : AppRoute
    data object Events : AppRoute
    data object AIMatch : AppRoute
    data object Search : AppRoute
    data object Filter : AppRoute
    data object Notifications : AppRoute
    data object Premium : AppRoute
    data object Badge : AppRoute
    data object Wallet : AppRoute
    data object Gift : AppRoute
    data object Settings : AppRoute
    data object Safety : AppRoute
    data object Admin : AppRoute
    data object DesignSystem : AppRoute
    data class Template(val spec: ScreenSpec) : AppRoute
    data class Status(val kind: ScreenStateKind) : AppRoute
}

enum class ScreenStateKind {
    Success,
    Loading,
    Empty,
    Error,
    Offline,
    Permission,
    FirstTimeUser,
    Premium,
}

enum class MainTab {
    Home,
    Discover,
    Messages,
    Community,
    Profile,
}

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_SIGN_IN = "sign_in"
private const val ROUTE_OTP = "otp"
private const val ROUTE_PROFILE_SETUP = "profile_setup"
private const val ROUTE_INTERESTS = "interests"
private const val ROUTE_HOME = "home"
private const val ROUTE_DISCOVER = "discover"
private const val ROUTE_PROFILE_DETAIL = "profile_detail"
private const val ROUTE_PROFILE_CONNECTIONS = "profile_connections"
private const val ROUTE_MESSAGES = "messages"
private const val ROUTE_COMMUNITY = "community"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_STORY = "story"
private const val ROUTE_FEED = "feed"
private const val ROUTE_AI_COMPATIBILITY = "ai_compatibility"
private const val ROUTE_MATCH_SUCCESS = "match_success"
private const val ROUTE_CHAT = "chat"
private const val ROUTE_VOICE_CALL = "voice_call"
private const val ROUTE_VIDEO_CALL = "video_call"
private const val ROUTE_CALL_SUMMARY = "call_summary"
private const val ROUTE_COMMUNITY_DETAIL = "community_detail"
private const val ROUTE_EVENTS = "events"
private const val ROUTE_AI_MATCH = "ai_match"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_FILTER = "filter"
private const val ROUTE_NOTIFICATIONS = "notifications"
private const val ROUTE_PREMIUM = "premium"
private const val ROUTE_BADGE = "badge"
private const val ROUTE_WALLET = "wallet"
private const val ROUTE_GIFT = "gift"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_SAFETY = "safety"
private const val ROUTE_ADMIN = "admin"
private const val ROUTE_DESIGN_SYSTEM = "design_system"
private const val ROUTE_TEMPLATE = "template"
private const val ROUTE_STATUS = "status"

fun AppRoute.routeName(): String = when (this) {
    AppRoute.Splash -> ROUTE_SPLASH
    AppRoute.Onboarding -> ROUTE_ONBOARDING
    AppRoute.SignIn -> ROUTE_SIGN_IN
    AppRoute.Otp -> ROUTE_OTP
    AppRoute.ProfileSetup -> ROUTE_PROFILE_SETUP
    AppRoute.Interests -> ROUTE_INTERESTS
    AppRoute.Home -> ROUTE_HOME
    AppRoute.Discover -> ROUTE_DISCOVER
    AppRoute.ProfileDetail -> ROUTE_PROFILE_DETAIL
    AppRoute.ProfileConnections -> ROUTE_PROFILE_CONNECTIONS
    AppRoute.Messages -> ROUTE_MESSAGES
    AppRoute.Community -> ROUTE_COMMUNITY
    AppRoute.Profile -> ROUTE_PROFILE
    AppRoute.Story -> ROUTE_STORY
    AppRoute.Feed -> ROUTE_FEED
    AppRoute.AICompatibility -> ROUTE_AI_COMPATIBILITY
    AppRoute.MatchSuccess -> ROUTE_MATCH_SUCCESS
    AppRoute.Chat -> ROUTE_CHAT
    AppRoute.VoiceCall -> ROUTE_VOICE_CALL
    AppRoute.VideoCall -> ROUTE_VIDEO_CALL
    AppRoute.CallSummary -> ROUTE_CALL_SUMMARY
    AppRoute.CommunityDetail -> ROUTE_COMMUNITY_DETAIL
    AppRoute.Events -> ROUTE_EVENTS
    AppRoute.AIMatch -> ROUTE_AI_MATCH
    AppRoute.Search -> ROUTE_SEARCH
    AppRoute.Filter -> ROUTE_FILTER
    AppRoute.Notifications -> ROUTE_NOTIFICATIONS
    AppRoute.Premium -> ROUTE_PREMIUM
    AppRoute.Badge -> ROUTE_BADGE
    AppRoute.Wallet -> ROUTE_WALLET
    AppRoute.Gift -> ROUTE_GIFT
    AppRoute.Settings -> ROUTE_SETTINGS
    AppRoute.Safety -> ROUTE_SAFETY
    AppRoute.Admin -> ROUTE_ADMIN
    AppRoute.DesignSystem -> ROUTE_DESIGN_SYSTEM
    is AppRoute.Template -> "$ROUTE_TEMPLATE/${Uri.encode(this.spec.id)}"
    is AppRoute.Status -> "$ROUTE_STATUS/${this.kind.name.lowercase()}"
}

fun NavController.navigateTo(route: AppRoute) {
    navigate(route.routeName()) {
        launchSingleTop = true
    }
}

fun NavController.navigateToProfileDetail(userId: String) {
    navigate(profileDetailRoute(userId)) {
        launchSingleTop = true
    }
}

fun NavController.navigateToProfileConnections(userId: String, tab: String? = null) {
    navigate(profileConnectionsRoute(userId, tab)) {
        launchSingleTop = true
    }
}

fun NavController.replaceWith(route: AppRoute, currentRoute: AppRoute) {
    navigate(route.routeName()) {
        launchSingleTop = true
        popUpTo(currentRoute.routeName()) {
            inclusive = true
        }
    }
}

fun NavController.replaceAllWith(route: AppRoute) {
    navigate(route.routeName()) {
        launchSingleTop = true
        popUpTo(graph.findStartDestination().id) {
            inclusive = true
        }
    }
}

fun AppRoute.isMainTab(): Boolean = when (this) {
    AppRoute.Home,
    AppRoute.Discover,
    AppRoute.Messages,
    AppRoute.Community,
    AppRoute.Profile -> true
    else -> false
}

fun mainTabFor(route: AppRoute): MainTab? = when (route) {
    AppRoute.Home -> MainTab.Home
    AppRoute.Discover -> MainTab.Discover
    AppRoute.Messages -> MainTab.Messages
    AppRoute.Community -> MainTab.Community
    AppRoute.Profile -> MainTab.Profile
    else -> null
}

fun profileDetailRoute(userId: String): String {
    return "$ROUTE_PROFILE_DETAIL/${Uri.encode(userId)}"
}

fun profileConnectionsRoute(userId: String, tab: String? = null): String {
    val base = "$ROUTE_PROFILE_CONNECTIONS/${Uri.encode(userId)}"
    return if (tab.isNullOrBlank()) base else "$base?tab=${Uri.encode(tab)}"
}
