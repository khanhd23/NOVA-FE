package com.nova.app.core.backend

import android.util.Log
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import java.io.IOException

/** Source of the current tokens; implemented by the runtime that owns the session. */
interface SessionTokenStore {
    fun currentAccessToken(): String?
    fun currentRefreshToken(): String?
    fun onTokensRefreshed(accessToken: String, refreshToken: String)

    /** The refresh token was rejected: the user must sign in again. */
    fun onSessionExpired()
}

/**
 * OkHttp calls this when a request comes back 401. It exchanges the refresh token for a new
 * token pair (once, even if many requests fail at the same time) and retries the request.
 */
internal class TokenAuthenticator(
    private val baseUrl: String,
    private val refreshClient: OkHttpClient,
    private val tokenStore: () -> SessionTokenStore?,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val store = tokenStore() ?: return null
        val request = response.request
        // Auth endpoints answer 401 for bad credentials; retrying them makes no sense.
        if (request.url.encodedPath.startsWith(AUTH_PATH_PREFIX)) return null
        if (response.priorResponse != null) return null // already retried once
        val failedToken = request.header(AUTHORIZATION)?.removePrefix(BEARER)?.trim() ?: return null

        synchronized(lock) {
            // Another request may have refreshed while this one waited for the lock.
            val current = store.currentAccessToken()
            if (!current.isNullOrBlank() && current != failedToken) {
                return request.withToken(current)
            }
            val refreshToken = store.currentRefreshToken()
            if (refreshToken.isNullOrBlank()) {
                store.onSessionExpired()
                return null
            }
            return when (val result = refresh(refreshToken)) {
                is RefreshResult.Success -> {
                    store.onTokensRefreshed(result.accessToken, result.refreshToken)
                    request.withToken(result.accessToken)
                }
                RefreshResult.Rejected -> {
                    store.onSessionExpired()
                    null
                }
                // Network problem: keep the session, the next request will try again.
                RefreshResult.NetworkError -> null
            }
        }
    }

    private fun refresh(refreshToken: String): RefreshResult {
        val body = JSONObject().put("refreshToken", refreshToken).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val call = refreshClient.newCall(
            Request.Builder().url("$baseUrl$REFRESH_PATH").post(body).build()
        )
        return try {
            call.execute().use { response ->
                if (response.code == 400 || response.code == 401 || response.code == 403) {
                    return RefreshResult.Rejected
                }
                if (!response.isSuccessful) return RefreshResult.NetworkError
                val tokens = JSONObject(response.body?.string().orEmpty())
                    .optJSONObject("data")
                    ?.optJSONObject("tokens")
                    ?: return RefreshResult.NetworkError
                val access = tokens.optString("accessToken")
                val refresh = tokens.optString("refreshToken")
                if (access.isBlank() || refresh.isBlank()) RefreshResult.NetworkError
                else RefreshResult.Success(access, refresh)
            }
        } catch (error: IOException) {
            Log.w("NovaAuth", "Token refresh failed: ${error.message}")
            RefreshResult.NetworkError
        }
    }

    private fun Request.withToken(token: String): Request =
        newBuilder().header(AUTHORIZATION, "$BEARER$token").build()

    private sealed interface RefreshResult {
        data class Success(val accessToken: String, val refreshToken: String) : RefreshResult
        data object Rejected : RefreshResult
        data object NetworkError : RefreshResult
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer "
        const val AUTH_PATH_PREFIX = "/api/v1/auth/"
        const val REFRESH_PATH = "/api/v1/auth/refresh"
    }
}
