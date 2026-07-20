package com.nova.app.core.backend

import android.content.Context
import java.util.UUID

private const val PREFS_NAME = "nova_backend_runtime"
private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val KEY_USER_ID = "user_id"
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_AVATAR_URL = "avatar_url"
private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
private const val KEY_PROFILE_COMPLETE = "profile_complete"
private const val KEY_PUSH_TOKEN = "push_token"
private const val KEY_DEVICE_ID = "device_id"

object BackendSessionStore {

    fun loadSession(context: Context): BackendSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val userId = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val avatarUrl = prefs.getString(KEY_AVATAR_URL, null)?.takeIf { it.isNotBlank() }
        val onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        val profileComplete = prefs.getBoolean(KEY_PROFILE_COMPLETE, false)
        return BackendSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            displayName = displayName,
            avatarUrl = avatarUrl,
            onboardingComplete = onboardingComplete,
            profileComplete = profileComplete,
        )
    }

    fun saveSession(context: Context, session: BackendSession) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .putString(KEY_AVATAR_URL, session.avatarUrl)
            .putBoolean(KEY_ONBOARDING_COMPLETE, session.onboardingComplete)
            .putBoolean(KEY_PROFILE_COMPLETE, session.profileComplete)
            .apply()
    }

    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_AVATAR_URL)
            .remove(KEY_ONBOARDING_COMPLETE)
            .remove(KEY_PROFILE_COMPLETE)
            .apply()
    }

    fun savePushToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PUSH_TOKEN, token)
            .apply()
    }

    fun loadPushToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PUSH_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun loadDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
        if (existing != null) {
            return existing
        }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }
}
