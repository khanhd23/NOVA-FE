package com.nova.app.feature.search

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nova.app.core.model.SearchResultItem
import com.nova.app.core.model.SearchUiState
import com.nova.app.core.ui.NovaCard
import com.nova.app.core.ui.NovaChip
import com.nova.app.core.ui.NovaTextField
import com.nova.app.core.ui.NovaTopBar
import com.nova.app.core.ui.ExpandableText
import com.nova.app.feature.profile.ProfileIdentityRow
import com.nova.app.core.ui.VipAvatar
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onGenderChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenProfile: (SearchResultItem) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NovaTopBar(
                title = "Search",
                subtitle = if (uiState.total > 0) "${uiState.total} people" else "Search people by name or ID",
                onBack = onBack,
                actions = {
                    if (uiState.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            )

            NovaTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                placeholder = "Search by name, ID, username",
                leadingIcon = Icons.Default.Search,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Male", "Female").forEach { gender ->
                    NovaChip(
                        text = gender,
                        selected = uiState.selectedGender.equals(gender, ignoreCase = true),
                        onClick = { onGenderChange(gender) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                uiState.error != null -> ErrorState(message = uiState.error, onRetry = onRetry)
                uiState.results.isEmpty() && !uiState.loading -> EmptyState(query = uiState.query)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.results, key = { it.id }) { result ->
                        SearchResultCard(
                            result = result,
                            onClick = { onOpenProfile(result) },
                        )
                    }

                    if (uiState.hasMore) {
                        item {
                            Button(
                                onClick = onLoadMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PurpleMain),
                            ) {
                                Text("Load more")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResultItem,
    onClick: () -> Unit,
) {
    NovaCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VipAvatar(
                imageUrl = result.avatarUrl,
                contentDescription = result.name,
                modifier = Modifier.size(60.dp),
                vipTierId = result.vipTierId,
                premium = result.premium,
            )

            Spacer(modifier = Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = result.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                    if (result.verified) {
                        Spacer(modifier = Modifier.size(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = PurpleMain,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = buildString {
                        append(result.gender.ifBlank { "Not specified" })
                        if (result.city.isNotBlank()) {
                            append(" · ")
                            append(result.city)
                        }
                        result.distanceKm?.let {
                            append(" · ")
                            append(it)
                            append(" km")
                        }
                    },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                )
                if (result.publicId.isNotBlank()) {
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = "ID: ${result.publicId}",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                    )
                }

                Spacer(modifier = Modifier.size(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.interests.take(3).forEach { interest ->
                        NovaChip(text = interest, selected = true)
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = PurpleMain.copy(alpha = 0.12f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = PurplePink,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserDetailSheet(
    user: SearchResultItem,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (user.publicId.isNotBlank()) {
                ProfileIdentityRow(
                    id = user.publicId,
                    centered = false,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            ExpandableText(
                text = "${user.gender.ifBlank { "Not specified" }}${if (user.city.isNotBlank()) " · ${user.city}" else ""}",
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                user.interests.take(4).forEach { interest ->
                    NovaChip(text = interest, selected = true)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onMessage,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleMain),
            ) {
                Text("Message")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyState(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (query.isBlank()) "Type to search people" else "No results for \"$query\"",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

private fun fallbackAvatarUrl(name: String): String {
    val safeName = if (name.isBlank()) "Nova User" else name.trim()
    val encoded = java.net.URLEncoder.encode(safeName, Charsets.UTF_8)
    return "https://ui-avatars.com/api/?name=$encoded&background=6C5CE7&color=FFFFFF&size=512"
}

