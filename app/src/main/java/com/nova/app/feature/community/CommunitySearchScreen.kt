package com.nova.app.feature.community

import com.nova.app.core.designsystem.NovaBrand

import androidx.compose.ui.res.stringResource
import com.nova.app.R
import androidx.compose.ui.platform.LocalResources

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nova.app.core.backend.BackendCommunityPost
import com.nova.app.core.backend.BackendRuntimeRegistry
import com.nova.app.core.backend.BackendSearchUser
import com.nova.app.core.model.CommunityMention
import com.nova.app.core.ui.ExpandableText
import com.nova.app.core.ui.NovaCard
import com.nova.app.core.ui.NovaChip
import com.nova.app.core.ui.NovaTopLoadingBar
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.ui.PostMediaPreview
import com.nova.app.core.ui.VipAvatar
import com.nova.app.core.ui.formatPostTimestamp
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink
import kotlinx.coroutines.launch

private const val COMMUNITY_SEARCH_PAGE_SIZE = 10
private const val COMMUNITY_SEARCH_HISTORY_KEY = "terms"

@Composable
fun CommunitySearchScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMedia: (List<String>, Int) -> Unit,
) {
    val context = LocalContext.current
    val res = LocalResources.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences("community_search_history", android.content.Context.MODE_PRIVATE)
    }

    var query by rememberSaveable { mutableStateOf("") }
    var submittedQuery by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(loadCommunitySearchHistory(prefs)) }

    var posts by remember { mutableStateOf<List<BackendCommunityPost>>(emptyList()) }
    var postPage by rememberSaveable { mutableIntStateOf(0) }
    var postTotal by rememberSaveable { mutableStateOf(0L) }
    var postLoading by rememberSaveable { mutableStateOf(false) }
    var postError by rememberSaveable { mutableStateOf<String?>(null) }

    var users by remember { mutableStateOf<List<BackendSearchUser>>(emptyList()) }
    var userPage by rememberSaveable { mutableIntStateOf(0) }
    var userTotal by rememberSaveable { mutableStateOf(0L) }
    var userLoading by rememberSaveable { mutableStateOf(false) }
    var userError by rememberSaveable { mutableStateOf<String?>(null) }

    suspend fun searchPosts(page: Int, append: Boolean) {
        val term = submittedQuery.trim()
        if (term.isBlank()) return
        postLoading = true
        postError = null
        val result = BackendRuntimeRegistry.runtime?.searchCommunityPosts(term, page, COMMUNITY_SEARCH_PAGE_SIZE)
        if (result == null) {
            postLoading = false
            postError = res.getString(R.string.community_search_posts_failed)
            return
        }
        posts = if (append) posts + result.items else result.items
        postPage = result.page
        postTotal = result.total
        postLoading = false
    }

    suspend fun searchUsers(page: Int, append: Boolean) {
        val term = submittedQuery.trim()
        if (term.isBlank()) return
        userLoading = true
        userError = null
        val result = BackendRuntimeRegistry.runtime?.searchUsers(term, page, COMMUNITY_SEARCH_PAGE_SIZE)
        if (result == null) {
            userLoading = false
            userError = res.getString(R.string.community_search_users_failed)
            return
        }
        users = if (append) users + result.items else result.items
        userPage = result.page
        userTotal = result.total
        userLoading = false
    }

    fun submitSearch(term: String = query) {
        val normalized = term.trim()
        if (normalized.isBlank()) return
        if (!normalized.equals(submittedQuery, ignoreCase = true)) {
            posts = emptyList()
            postPage = 0
            postTotal = 0L
            postError = null
            users = emptyList()
            userPage = 0
            userTotal = 0L
            userError = null
        }
        query = normalized
        submittedQuery = normalized
        history = saveCommunitySearchHistory(prefs, normalized, history)
        keyboardController?.hide()
        scope.launch {
            if (selectedTab == 0) {
                searchPosts(page = 0, append = false)
            } else {
                searchUsers(page = 0, append = false)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            NovaTopBar(
                title = stringResource(R.string.community_search_title),
                subtitle = resultSubtitle(selectedTab, submittedQuery, postTotal, userTotal),
                onBack = onBack,
            )
            NovaTopLoadingBar(visible = if (selectedTab == 0) postLoading else userLoading)

            SearchInput(
                query = query,
                onQueryChange = { value ->
                    query = value
                    if (value.isBlank()) {
                        submittedQuery = ""
                        posts = emptyList()
                        postTotal = 0L
                        postError = null
                        users = emptyList()
                        userTotal = 0L
                        userError = null
                    }
                },
                onSearch = { submitSearch() },
            )

            Spacer(modifier = Modifier.height(12.dp))

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = PurpleMain,
                divider = {},
                edgePadding = 24.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = PurpleMain,
                    )
                }
            ) {
                listOf(stringResource(R.string.community_search_posts), stringResource(R.string.community_search_people)).forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (submittedQuery.isNotBlank()) {
                                scope.launch {
                                    if (index == 0 && posts.isEmpty()) searchPosts(page = 0, append = false)
                                    if (index == 1 && users.isEmpty()) searchUsers(page = 0, append = false)
                                }
                            }
                        },
                        text = {
                            Text(
                                text = title,
                                color = if (selectedTab == index) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    )
                }
            }

            when {
                submittedQuery.isBlank() -> SearchHistoryList(
                    history = history,
                    onUseTerm = { submitSearch(it) },
                    onRemoveTerm = { term ->
                        history = removeCommunitySearchHistory(prefs, term, history)
                    },
                )
                selectedTab == 0 -> PostResults(
                    query = submittedQuery,
                    posts = posts,
                    total = postTotal,
                    loading = postLoading,
                    error = postError,
                    canLoadMore = posts.size.toLong() < postTotal,
                    onLoadMore = { scope.launch { searchPosts(postPage + 1, append = true) } },
                    onOpenProfile = onOpenProfile,
                    onOpenMedia = onOpenMedia,
                )
                else -> UserResults(
                    query = submittedQuery,
                    users = users,
                    total = userTotal,
                    loading = userLoading,
                    error = userError,
                    canLoadMore = users.size.toLong() < userTotal,
                    onLoadMore = { scope.launch { searchUsers(userPage + 1, append = true) } },
                    onOpenProfile = onOpenProfile,
                )
            }
        }
    }
}

@Composable
private fun SearchInput(
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
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_search), tint = Color.White)
        }
    }
}

@Composable
private fun SearchHistoryList(
    history: List<String>,
    onUseTerm: (String) -> Unit,
    onRemoveTerm: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.community_recent_searches),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
        if (history.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.community_no_history),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                )
            }
        } else {
            items(history, key = { it }) { term ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onUseTerm(term) }.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = PurpleMain, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(term, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemoveTerm(term) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.community_remove_history), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostResults(
    query: String,
    posts: List<BackendCommunityPost>,
    total: Long,
    loading: Boolean,
    error: String?,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMedia: (List<String>, Int) -> Unit,
) {
    ResultList(
        loading = loading,
        error = error,
        empty = posts.isEmpty(),
        emptyText = stringResource(R.string.community_no_posts_for, query),
        total = total,
        canLoadMore = canLoadMore,
        onLoadMore = onLoadMore,
    ) {
        items(posts, key = { it.id }) { post ->
            CommunitySearchPostCard(
                post = post,
                onOpenProfile = onOpenProfile,
                onOpenMedia = onOpenMedia,
            )
        }
    }
}

@Composable
private fun UserResults(
    query: String,
    users: List<BackendSearchUser>,
    total: Long,
    loading: Boolean,
    error: String?,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    ResultList(
        loading = loading,
        error = error,
        empty = users.isEmpty(),
        emptyText = stringResource(R.string.community_no_users_for, query),
        total = total,
        canLoadMore = canLoadMore,
        onLoadMore = onLoadMore,
    ) {
        items(users, key = { it.userId }) { user ->
            CommunitySearchUserCard(user = user, onClick = { onOpenProfile(user.userId) })
        }
    }
}

@Composable
private fun ResultList(
    loading: Boolean,
    error: String?,
    empty: Boolean,
    emptyText: String,
    total: Long,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 14.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = if (total > 0) stringResource(R.string.common_results_count, total.toInt()) else stringResource(R.string.common_results),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (error != null) {
            item { StateMessage(error) }
        } else if (empty && !loading) {
            item { StateMessage(emptyText) }
        } else {
            content()
            if (canLoadMore) {
                item {
                    Button(
                        onClick = onLoadMore,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NovaBrand.Start),
                    ) {
                        Text(if (loading) stringResource(R.string.common_loading_ellipsis) else stringResource(R.string.common_load_more))
                    }
                }
            }
        }
        if (loading && empty) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun StateMessage(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}

@Composable
private fun CommunitySearchUserCard(user: BackendSearchUser, onClick: () -> Unit) {
    NovaCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VipAvatar(
                imageUrl = user.avatarUrl,
                contentDescription = user.displayName,
                modifier = Modifier.size(58.dp),
                vipTierId = user.vipTierId,
                premium = user.premium,
            )
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.displayName.ifBlank { stringResource(R.string.common_unknown) },
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (user.verified) {
                        Spacer(modifier = Modifier.size(6.dp))
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PurpleMain, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    text = stringResource(R.string.community_id_label, user.publicId.ifBlank { "--" }),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = user.bio.ifBlank { stringResource(R.string.setup_default_bio) },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CommunitySearchPostCard(
    post: BackendCommunityPost,
    onOpenProfile: (String) -> Unit,
    onOpenMedia: (List<String>, Int) -> Unit,
) {
    val mediaUrls = post.mediaUrls.ifEmpty { post.mediaUrl?.let { listOf(it) } ?: emptyList() }
    NovaCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onOpenProfile(post.authorId) },
            ) {
                VipAvatar(
                    imageUrl = post.authorAvatarUrl,
                    contentDescription = post.authorName,
                    modifier = Modifier.size(42.dp),
                    vipTierId = post.authorVipTierId,
                    premium = post.authorPremium,
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName.ifBlank { stringResource(R.string.common_unknown) },
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatPostTimestamp(post.createdAt, post.timeLabel),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            ExpandableText(
                text = post.text,
                mentions = post.mentions.map {
                    CommunityMention(
                        userId = it.userId,
                        displayName = it.displayName,
                        username = it.username,
                        avatarUrl = it.avatarUrl,
                    )
                },
                onMentionClick = onOpenProfile,
            )
            if (post.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.tags.take(3).forEach { tag -> NovaChip(text = "#$tag", selected = true) }
                }
            }
            if (mediaUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                PostMediaPreview(
                    mediaUrls = mediaUrls,
                    thumbnailUrl = post.thumbnailUrl,
                    onOpen = { index -> onOpenMedia(mediaUrls, index) },
                )
            }
        }
    }
}

@Composable
private fun resultSubtitle(tab: Int, query: String, postTotal: Long, userTotal: Long): String? {
    if (query.isBlank()) {
        return null
    }
    val total = if (tab == 0) postTotal else userTotal
    return if (total > 0) stringResource(R.string.common_results_for, total.toInt(), query) else stringResource(R.string.common_search_results)
}

private fun loadCommunitySearchHistory(prefs: SharedPreferences): List<String> {
    return prefs.getString(COMMUNITY_SEARCH_HISTORY_KEY, "").orEmpty()
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(10)
}

private fun saveCommunitySearchHistory(
    prefs: SharedPreferences,
    term: String,
    current: List<String>,
): List<String> {
    val next = (listOf(term) + current.filterNot { it.equals(term, ignoreCase = true) }).take(10)
    prefs.edit().putString(COMMUNITY_SEARCH_HISTORY_KEY, next.joinToString("\n")).apply()
    return next
}

private fun removeCommunitySearchHistory(
    prefs: SharedPreferences,
    term: String,
    current: List<String>,
): List<String> {
    val next = current.filterNot { it == term }
    prefs.edit().putString(COMMUNITY_SEARCH_HISTORY_KEY, next.joinToString("\n")).apply()
    return next
}
