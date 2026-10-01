package com.nova.app.feature.chat

import com.nova.app.core.designsystem.NovaBrand

import androidx.compose.ui.res.stringResource
import com.nova.app.R
import androidx.compose.ui.platform.LocalResources

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.core.backend.BackendRuntimeRegistry
import com.nova.app.core.backend.toChatThread
import com.nova.app.core.model.ChatThread
import com.nova.app.core.model.MessagesUiState
import com.nova.app.core.model.UserCard
import com.nova.app.core.ui.NovaTopLoadingBar
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.ui.theme.PurpleMain
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val CHAT_SEARCH_PAGE_SIZE = 10
private const val CHAT_SEARCH_PREFS = "chat_search_history"
private const val CHAT_SEARCH_HISTORY_KEY = "threads"
private const val CHAT_SEARCH_HISTORY_LIMIT = 10

@Composable
fun ChatSearchScreen(
    messagesState: MessagesUiState,
    onBack: () -> Unit,
    onOpenChat: (ChatThread) -> Unit,
) {
    val context = LocalContext.current
    val res = LocalResources.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences(CHAT_SEARCH_PREFS, Context.MODE_PRIVATE)
    }

    var query by rememberSaveable { mutableStateOf("") }
    var submittedQuery by rememberSaveable { mutableStateOf("") }
    var recentThreads by remember { mutableStateOf(loadChatSearchHistory(prefs)) }
    var results by remember { mutableStateOf<List<ChatThread>>(emptyList()) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var total by rememberSaveable { mutableStateOf(0L) }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(messagesState.threads) {
        recentThreads = hydrateRecentThreads(recentThreads, messagesState.threads)
    }

    suspend fun searchThreads(pageToLoad: Int, append: Boolean) {
        val term = submittedQuery.trim()
        if (term.isBlank()) return
        loading = true
        error = null
        val result = BackendRuntimeRegistry.runtime?.searchChatThreads(term, pageToLoad, CHAT_SEARCH_PAGE_SIZE)
        if (result == null) {
            loading = false
            error = res.getString(R.string.chat_search_failed)
            return
        }
        val mapped = result.items.map { it.toChatThread() }
        results = if (append) results + mapped else mapped
        page = result.page
        total = result.total
        loading = false
    }

    fun submitSearch(term: String = query) {
        val normalized = term.trim()
        if (normalized.isBlank()) return
        if (!normalized.equals(submittedQuery, ignoreCase = true)) {
            results = emptyList()
            page = 0
            total = 0L
            error = null
        }
        query = normalized
        submittedQuery = normalized
        keyboardController?.hide()
        scope.launch {
            searchThreads(pageToLoad = 0, append = false)
        }
    }

    fun openChat(thread: ChatThread) {
        recentThreads = saveChatSearchHistory(prefs, thread, recentThreads)
        onOpenChat(thread)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            NovaTopBar(
                title = stringResource(R.string.chat_search_title),
                subtitle = chatSearchSubtitle(submittedQuery, total),
                onBack = onBack,
            )
            NovaTopLoadingBar(visible = loading)

            ChatSearchInput(
                query = query,
                onQueryChange = { value ->
                    query = value
                    if (value.isBlank()) {
                        submittedQuery = ""
                        results = emptyList()
                        page = 0
                        total = 0L
                        error = null
                    }
                },
                onSearch = { submitSearch() },
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (submittedQuery.isBlank()) {
                val visibleRecentThreads = if (recentThreads.isNotEmpty()) recentThreads else messagesState.threads.take(CHAT_SEARCH_HISTORY_LIMIT)
                ChatThreadHistory(
                    title = if (recentThreads.isNotEmpty()) stringResource(R.string.chat_recent_opened) else stringResource(R.string.chat_recent_conversations),
                    threads = visibleRecentThreads,
                    emptyText = stringResource(R.string.chat_no_recent),
                    onOpenChat = ::openChat,
                )
            } else {
                ChatThreadResults(
                    query = submittedQuery,
                    results = results,
                    total = total,
                    loading = loading,
                    error = error,
                    canLoadMore = results.size.toLong() < total,
                    onLoadMore = { scope.launch { searchThreads(page + 1, append = true) } },
                    onOpenChat = ::openChat,
                )
            }
        }
    }
}

@Composable
private fun ChatSearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val inputShape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(inputShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.36f), inputShape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isBlank()) {
                Text(
                    text = stringResource(R.string.common_search_hint),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.46f),
                    fontSize = 14.sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(PurpleMain),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        IconButton(
            onClick = onSearch,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(NovaBrand.Start),
        ) {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.chat_search_chats), tint = Color.White)
        }
    }
}

@Composable
private fun ChatThreadHistory(
    title: String,
    threads: List<ChatThread>,
    emptyText: String,
    onOpenChat: (ChatThread) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
        if (threads.isEmpty()) {
            item { ChatSearchStateText(emptyText) }
        } else {
            items(threads, key = { it.id }) { thread ->
                ChatListItem(thread = thread, onClick = { onOpenChat(thread) })
            }
        }
    }
}

@Composable
private fun ChatThreadResults(
    query: String,
    results: List<ChatThread>,
    total: Long,
    loading: Boolean,
    error: String?,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenChat: (ChatThread) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = if (total > 0) stringResource(R.string.chat_conversations_count, total) else stringResource(R.string.common_results),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
        }
        when {
            error != null -> item { ChatSearchStateText(error) }
            results.isEmpty() && !loading -> item { ChatSearchStateText(stringResource(R.string.chat_no_conversations_for, query)) }
            else -> {
                items(results, key = { it.id }) { thread ->
                    ChatListItem(thread = thread, onClick = { onOpenChat(thread) })
                }
                if (canLoadMore) {
                    item {
                        Button(
                            onClick = onLoadMore,
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NovaBrand.Start),
                        ) {
                            Text(if (loading) stringResource(R.string.common_loading_ellipsis) else stringResource(R.string.common_load_more))
                        }
                    }
                }
            }
        }
        if (loading && results.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun ChatSearchStateText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}

@Composable
private fun chatSearchSubtitle(query: String, total: Long): String? {
    return if (query.isBlank()) {
        null
    } else if (total > 0) {
        stringResource(R.string.common_results_for, total.toInt(), query)
    } else {
        stringResource(R.string.common_search_results)
    }
}

private fun loadChatSearchHistory(prefs: SharedPreferences): List<ChatThread> {
    val raw = prefs.getString(CHAT_SEARCH_HISTORY_KEY, "").orEmpty()
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toChatThreadSnapshot()?.let { add(it) }
            }
        }.take(CHAT_SEARCH_HISTORY_LIMIT)
    }.getOrDefault(emptyList())
}

private fun saveChatSearchHistory(
    prefs: SharedPreferences,
    thread: ChatThread,
    current: List<ChatThread>,
): List<ChatThread> {
    val next = (listOf(thread) + current.filterNot { it.id == thread.id }).take(CHAT_SEARCH_HISTORY_LIMIT)
    val array = JSONArray()
    next.forEach { array.put(it.toJsonSnapshot()) }
    prefs.edit().putString(CHAT_SEARCH_HISTORY_KEY, array.toString()).apply()
    return next
}

private fun hydrateRecentThreads(
    recent: List<ChatThread>,
    liveThreads: List<ChatThread>,
): List<ChatThread> {
    if (recent.isEmpty() || liveThreads.isEmpty()) return recent
    val liveById = liveThreads.associateBy { it.id }
    return recent.map { liveById[it.id] ?: it }
}

private fun ChatThread.toJsonSnapshot(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("lastMessage", lastMessage)
        .put("unreadCount", unreadCount)
        .put("online", online)
        .put("typing", typing)
        .put("pinned", pinned)
        .put("matchLabel", matchLabel)
        .put(
            "user",
            JSONObject()
                .put("id", user.id)
                .put("publicId", user.publicId)
                .put("name", user.name)
                .put("age", user.age)
                .put("photoUrl", user.photoUrl)
                .put("verified", user.verified)
                .put("distanceKm", user.distanceKm)
                .put("online", user.online)
                .put("city", user.city)
                .put("vipTierId", user.vipTierId)
                .put("vipTierName", user.vipTierName)
                .put("premium", user.premium)
                .put("followersCount", user.followersCount)
                .put("followingCount", user.followingCount)
                .put("friendsCount", user.friendsCount)
                .put("followedByMe", user.followedByMe)
                .put("followedByThem", user.followedByThem)
                .put("friend", user.friend)
                .put("gender", user.gender),
        )
}

private fun JSONObject.toChatThreadSnapshot(): ChatThread? {
    val userJson = optJSONObject("user") ?: return null
    val user = UserCard(
        id = userJson.optString("id"),
        publicId = userJson.optString("publicId"),
        name = userJson.optString("name").ifBlank { "Chat" },
        age = userJson.optInt("age", 0),
        photoUrl = userJson.optString("photoUrl"),
        verified = userJson.optBoolean("verified", false),
        distanceKm = if (userJson.isNull("distanceKm")) null else userJson.optInt("distanceKm"),
        online = userJson.optBoolean("online", false),
        city = userJson.optString("city"),
        vipTierId = userJson.optString("vipTierId").takeIf { it.isNotBlank() && it != "null" },
        vipTierName = userJson.optString("vipTierName").takeIf { it.isNotBlank() && it != "null" },
        premium = userJson.optBoolean("premium", false),
        followersCount = userJson.optInt("followersCount", 0),
        followingCount = userJson.optInt("followingCount", 0),
        friendsCount = userJson.optInt("friendsCount", 0),
        followedByMe = userJson.optBoolean("followedByMe", false),
        followedByThem = userJson.optBoolean("followedByThem", false),
        friend = userJson.optBoolean("friend", false),
        gender = userJson.optString("gender").ifBlank { "Not specified" },
    )
    return ChatThread(
        id = optString("id"),
        user = user,
        lastMessage = optString("lastMessage"),
        unreadCount = optInt("unreadCount", 0),
        online = optBoolean("online", false),
        typing = optBoolean("typing", false),
        pinned = optBoolean("pinned", false),
        matchLabel = optString("matchLabel").ifBlank { "Direct" },
    )
}
