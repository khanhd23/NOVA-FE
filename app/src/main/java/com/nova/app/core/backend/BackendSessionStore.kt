package com.nova.app.core.backend

import android.content.Context
import java.util.UUID

private const val PREFS_NAME = "nova_backend_runtime"
private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val KEY_USER_ID = "user_id"
private const val KEY_PUBLIC_ID = "public_id"
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_AVATAR_URL = "avatar_url"
private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
private const val KEY_PROFILE_COMPLETE = "profile_complete"
private const val KEY_PUSH_TOKEN = "push_token"
private const val KEY_DEVICE_ID = "device_id"

object BackendSessionStore {

    fun loadSession(context: Context): BackendSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedAccess = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val storedRefresh = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        // Tokens that can't be decrypted (e.g. restored onto a new device) mean: sign in again.
        val accessToken = TokenCipher.decrypt(storedAccess) ?: return null
        val refreshToken = TokenCipher.decrypt(storedRefresh) ?: return null
        val userId = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val publicId = prefs.getString(KEY_PUBLIC_ID, null).orEmpty()
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val avatarUrl = prefs.getString(KEY_AVATAR_URL, null)?.takeIf { it.isNotBlank() }
        val onboardingComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        val profileComplete = prefs.getBoolean(KEY_PROFILE_COMPLETE, false)
        if (!TokenCipher.isEncrypted(storedAccess) || !TokenCipher.isEncrypted(storedRefresh)) {
            // Migrate sessions saved in plain text by older versions.
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, TokenCipher.encrypt(accessToken))
                .putString(KEY_REFRESH_TOKEN, TokenCipher.encrypt(refreshToken))
                .apply()
        }
        return BackendSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            publicId = publicId,
            displayName = displayName,
            avatarUrl = avatarUrl,
            onboardingComplete = onboardingComplete,
            profileComplete = profileComplete,
        )
    }

    /** Signed-in user id without decrypting tokens (used by notification code). */
    fun loadUserId(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_ID, null)
            ?.takeIf { it.isNotBlank() }

    fun saveSession(context: Context, session: BackendSession) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCESS_TOKEN, TokenCipher.encrypt(session.accessToken))
            .putString(KEY_REFRESH_TOKEN, TokenCipher.encrypt(session.refreshToken))
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_PUBLIC_ID, session.publicId)
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
            .remove(KEY_PUBLIC_ID)
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
