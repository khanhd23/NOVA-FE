package com.nova.app.core.backend

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TokenAuthenticatorTest {

    private class FakeStore(
        var access: String? = "old-access",
        var refresh: String? = "old-refresh",
    ) : SessionTokenStore {
        var expired = false

        override fun currentAccessToken() = access
        override fun currentRefreshToken() = refresh
        override fun onTokensRefreshed(accessToken: String, refreshToken: String) {
            access = accessToken
            refresh = refreshToken
        }

        override fun onSessionExpired() {
            expired = true
        }
    }

    /** Refresh client whose network layer is replaced by [respond]; counts refresh calls. */
    private class FakeRefreshServer(private val respond: (Request) -> Response) {
        var calls = 0
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                calls++
                respond(chain.request())
            })
            .build()
    }

    private fun jsonResponse(request: Request, code: Int, body: String) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    private fun unauthorized(path: String = "/api/v1/me", token: String = "old-access"): Response {
        val request = Request.Builder()
            .url("https://api.test$path")
            .header("Authorization", "Bearer $token")
            .build()
        return jsonResponse(request, 401, "{}")
    }

    private fun tokensBody(access: String, refresh: String) =
        """{"success":true,"data":{"tokens":{"accessToken":"$access","refreshToken":"$refresh"}}}"""

    @Test
    fun `refreshes once and retries with the new access token`() {
        val store = FakeStore()
        val server = FakeRefreshServer { jsonResponse(it, 200, tokensBody("new-access", "new-refresh")) }
        val authenticator = TokenAuthenticator("https://api.test", server.client) { store }

        val retry = authenticator.authenticate(null, unauthorized())

        assertEquals("Bearer new-access", retry?.header("Authorization"))
        assertEquals("new-access", store.access)
        assertEquals("new-refresh", store.refresh)
        assertEquals(1, server.calls)
    }

    @Test
    fun `reuses a token refreshed by another request instead of refreshing again`() {
        val store = FakeStore(access = "already-new")
        val server = FakeRefreshServer { error("must not refresh") }
        val authenticator = TokenAuthenticator("https://api.test", server.client) { store }

        val retry = authenticator.authenticate(null, unauthorized(token = "old-access"))

        assertEquals("Bearer already-new", retry?.header("Authorization"))
        assertEquals(0, server.calls)
    }

    @Test
    fun `rejected refresh token ends the session`() {
        val store = FakeStore()
        val server = FakeRefreshServer { jsonResponse(it, 401, "{}") }
        val authenticator = TokenAuthenticator("https://api.test", server.client) { store }

        val retry = authenticator.authenticate(null, unauthorized())

        assertNull(retry)
        assertTrue(store.expired)
    }

    @Test
    fun `network error keeps the session for a later retry`() {
        val store = FakeStore()
        val server = FakeRefreshServer { throw IOException("offline") }
        val authenticator = TokenAuthenticator("https://api.test", server.client) { store }

        val retry = authenticator.authenticate(null, unauthorized())

        assertNull(retry)
        assertFalse(store.expired)
        assertEquals("old-access", store.access)
    }

    @Test
    fun `does not retry auth endpoints`() {
        val store = FakeStore()
        val server = FakeRefreshServer { error("must not refresh") }
        val authenticator = TokenAuthenticator("https://api.test", server.client) { store }

        assertNull(authenticator.authenticate(null, unauthorized(path = "/api/v1/auth/social/login")))
        assertEquals(0, server.calls)
    }

    @Test
    fun `gives up after one retry`() {
        val store = FakeStore()
        val server = FakeRefreshServer { error("must not refresh") }
        val authenticator = TokenAuthenticator("https://api.test", server.client) { store }
        val first = unauthorized()
        val second = first.newBuilder().priorResponse(first.newBuilder().body(null).build()).build()

        assertNull(authenticator.authenticate(null, second))
        assertEquals(0, server.calls)
    }
}
