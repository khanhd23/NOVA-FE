package com.nova.app.feature.profile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FollowTheSigns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.core.model.ProfileConnectionItem
import com.nova.app.core.model.ProfileConnectionsUiState
import com.nova.app.core.ui.ExpandableText
import com.nova.app.core.ui.NovaBadge
import com.nova.app.core.ui.NovaCard
import com.nova.app.core.ui.NovaChip
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.ui.VipAvatar
import com.nova.app.core.ui.VipTierChip
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink

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
            title = if (uiState.isSelf) "Connections" else "Shared connections",
            subtitle = if (uiState.isSelf) "Followers, friends, and following" else "Mutual friends and social graph",
            onBack = onBack,
            actions = {
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    if (uiState.total > 0) {
                        NovaBadge(count = uiState.total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), modifier = Modifier.padding(top = 6.dp, end = 6.dp))
                    }
                }
            }
        )

        if (!uiState.profileLoaded && uiState.loading && uiState.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        if (uiState.error != null && uiState.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.error, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onRefresh) {
                        Text("Retry")
                    }
                }
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (uiState.userName.isNotBlank()) {
                ProfileConnectionsHeader(uiState = uiState)
            }

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            when {
                uiState.loading && uiState.items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No people in this tab yet",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            ConnectionItemCard(
                                item = item,
                                onOpenProfile = onOpenProfile,
                            )
                        }

                        if (uiState.hasMore) {
                            item {
                                TextButton(
                                    onClick = onLoadMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (uiState.loadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.size(8.dp))
                                    }
                                    Text("Load more")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileConnectionsHeader(
    uiState: ProfileConnectionsUiState,
) {
    NovaCard(modifier = Modifier.padding(horizontal = 24.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VipAvatar(
                    imageUrl = uiState.avatarUrl,
                    contentDescription = uiState.userName,
                    modifier = Modifier.size(64.dp),
                    vipTierId = uiState.vipTierId,
                    premium = uiState.premium,
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.userName,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        if (uiState.verified) {
                            Spacer(modifier = Modifier.size(6.dp))
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = PurplePink, modifier = Modifier.size(14.dp))
                        }
                    }
                    if (uiState.city.isNotBlank()) {
                        Text(
                            text = uiState.city,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                        )
                    }
                    if (uiState.vipTierName != null || uiState.vipTierId != null) {
                        Spacer(modifier = Modifier.size(8.dp))
                        VipTierChip(tierLabel = uiState.vipTierName ?: uiState.vipTierId?.uppercase(), premium = uiState.premium)
                    }
                }
            }

            if (uiState.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                ExpandableText(text = uiState.bio, collapsedMaxLines = 3)
            }

            if (uiState.interests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.interests.take(4).forEach { interest ->
                        NovaChip(text = interest, selected = true)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ConnectionCountChip(
                    label = "Followers",
                    value = uiState.followersCount,
                    modifier = Modifier.weight(1f),
                )
                ConnectionCountChip(
                    label = "Friends",
                    value = uiState.friendsCount,
                    modifier = Modifier.weight(1f),
                )
                ConnectionCountChip(
                    label = "Following",
                    value = uiState.followingCount,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ConnectionCountChip(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = PurpleMain.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, PurpleMain.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value.toString(), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Text(text = label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun ConnectionItemCard(
    item: ProfileConnectionItem,
    onOpenProfile: (String) -> Unit,
) {
    NovaCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenProfile(item.id) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VipAvatar(
                imageUrl = item.avatarUrl,
                contentDescription = item.name,
                modifier = Modifier.size(58.dp),
                vipTierId = item.vipTierId,
                premium = item.premium,
            )

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    if (item.friend) {
                        Spacer(modifier = Modifier.size(6.dp))
                        NovaChip(text = "Friend", selected = true)
                    } else if (item.followedByThem && !item.followedByMe) {
                        Spacer(modifier = Modifier.size(6.dp))
                        NovaChip(text = "Follow back", selected = true)
                    } else if (item.followedByMe) {
                        Spacer(modifier = Modifier.size(6.dp))
                        NovaChip(text = "Following", selected = true)
                    }
                }

                Text(
                    text = buildString {
                        append(item.gender.ifBlank { "Not specified" })
                        if (item.city.isNotBlank()) {
                            append(" • ")
                            append(item.city)
                        }
                    },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                )

                Spacer(modifier = Modifier.size(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.interests.take(3).forEach { interest ->
                        NovaChip(text = interest, selected = true)
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = PurplePink,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
            )
        }
    }
}

private fun relationTabLabel(tab: String, isSelf: Boolean): String {
    return when (tab) {
        "followers" -> "Followers"
        "friends" -> "Friends"
        "following" -> "Following"
        "mutual" -> if (isSelf) "Friends" else "Mutual"
        else -> tab.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
