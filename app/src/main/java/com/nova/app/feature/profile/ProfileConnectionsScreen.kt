package com.nova.app.feature.profile

import com.nova.app.core.i18n.localizedMessage

import androidx.compose.ui.res.stringResource
import com.nova.app.R

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.core.model.ProfileConnectionItem
import com.nova.app.core.model.ProfileConnectionsUiState
import com.nova.app.core.ui.NovaBadge
import com.nova.app.core.ui.NovaTopLoadingBar
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.ui.VipAvatar
import com.nova.app.ui.theme.PurpleMain

@Composable
fun ProfileConnectionsScreen(
    uiState: ProfileConnectionsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val relationTabs = uiState.tabs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NovaTopBar(
            title = if (uiState.isSelf) stringResource(R.string.profile_connections) else stringResource(R.string.profile_shared_connections),
            subtitle = if (uiState.isSelf) stringResource(R.string.profile_connections_desc) else stringResource(R.string.profile_shared_connections_desc),
            onBack = onBack,
            actions = {
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = MaterialTheme.colorScheme.onBackground)
                    }
                    if (uiState.total > 0) {
                        NovaBadge(count = uiState.total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), modifier = Modifier.padding(top = 6.dp, end = 6.dp))
                    }
                }
            }
        )
        NovaTopLoadingBar(visible = uiState.loading || uiState.loadingMore)

        if (!uiState.profileLoaded && uiState.loading && uiState.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (uiState.error != null && uiState.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(localizedMessage(uiState.error), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onRefresh) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScrollableTabRow(
                    selectedTabIndex = relationTabs.indexOf(uiState.selectedTab).coerceAtLeast(0),
                    containerColor = Color.Transparent,
                    contentColor = PurpleMain,
                    divider = {},
                    edgePadding = 24.dp,
                    indicator = { tabPositions ->
                        val index = relationTabs.indexOf(uiState.selectedTab).coerceAtLeast(0)
                        if (tabPositions.isNotEmpty()) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[index]),
                                color = PurpleMain
                            )
                        }
                    }
                ) {
                    relationTabs.forEach { tab ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { onSelectTab(tab) },
                            text = {
                                Text(
                                    text = relationTabLabel(tab, uiState.isSelf),
                                    color = if (uiState.selectedTab == tab) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                                    fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        )
                    }
                }
            }

            when {
                uiState.loading && uiState.items.isEmpty() -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                uiState.items.isEmpty() -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.profile_no_people),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
                else -> {
                    items(uiState.items, key = { it.id }) { item ->
                        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                            ConnectionItemRow(
                                item = item,
                                onOpenProfile = onOpenProfile,
                            )
                        }
                    }

                    if (uiState.hasMore) {
                        item {
                            TextButton(
                                onClick = onLoadMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                            ) {
                                if (uiState.loadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.size(8.dp))
                                }
                                Text(stringResource(R.string.common_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionItemRow(
    item: ProfileConnectionItem,
    onOpenProfile: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenProfile(item.id) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VipAvatar(
            imageUrl = item.avatarUrl,
            contentDescription = item.name,
            modifier = Modifier.size(54.dp),
            vipTierId = item.vipTierId,
            premium = item.premium,
        )

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.bio.ifBlank { stringResource(R.string.setup_default_bio) },
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
@Composable
private fun relationTabLabel(tab: String, isSelf: Boolean): String {
    return when (tab) {
        "followers" -> stringResource(R.string.profile_followers)
        "friends" -> stringResource(R.string.community_tab_friends)
        "following" -> stringResource(R.string.community_tab_following)
        "mutual" -> if (isSelf) stringResource(R.string.community_tab_friends) else stringResource(R.string.profile_mutual)
        else -> tab.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

