package com.nova.app.feature.profile

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.core.ui.formatCount
import com.nova.app.ui.theme.PurpleMain

@Composable
fun ProfileIdentityRow(
    id: String,
    centered: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val displayId = id.ifBlank { "--" }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
    ) {
        Text(
            text = "ID: $displayId",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        IconButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(displayId))
                Toast.makeText(context, "\u0110\u00e3 copy", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.height(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy ID",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                modifier = Modifier.padding(1.dp).size(15.dp),
            )
        }
    }
}

@Composable
fun ProfileGenderIcon(
    gender: String,
    modifier: Modifier = Modifier,
) {
    val normalized = gender.trim().lowercase()
    val isFemale = normalized.contains("female") || normalized.contains("woman") || normalized.contains("girl")
    val isMale = normalized.contains("male") || normalized.contains("man") || normalized.contains("boy")
    val icon = when {
        isFemale -> Icons.Default.Female
        isMale -> Icons.Default.Male
        else -> Icons.Default.Person
    }
    val tint = when {
        isFemale -> Color(0xFFFF6FA8)
        isMale -> Color(0xFF6EC1FF)
        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    }

    Icon(
        imageVector = icon,
        contentDescription = gender.ifBlank { "Gender" },
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun ProfileBioText(
    bio: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = bio.ifBlank { "Tell people what makes your profile interesting." },
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Start,
    )
}

@Composable
fun ProfileConnectionStatsRow(
    followingCount: Int,
    followersCount: Int,
    friendsCount: Int,
    onOpenConnections: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProfileStatColumn(
            label = "Following",
            value = formatCount(followingCount),
            modifier = Modifier.weight(1f),
            onClick = { onOpenConnections("following") },
        )
        ProfileStatColumn(
            label = "Followers",
            value = formatCount(followersCount),
            modifier = Modifier.weight(1f),
            onClick = { onOpenConnections("followers") },
        )
        ProfileStatColumn(
            label = "Friends",
            value = formatCount(friendsCount),
            modifier = Modifier.weight(1f),
            onClick = { onOpenConnections("friends") },
        )
        ProfileStatColumn(
            label = "Hotness",
            value = formatCount(deriveHotness(
                followersCount = followersCount,
                followingCount = followingCount,
                friendsCount = friendsCount,
            )),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProfileStatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val contentModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Column(
        modifier = contentModifier.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private fun deriveHotness(
    followersCount: Int,
    followingCount: Int,
    friendsCount: Int,
): Int {
    return followersCount + followingCount + (friendsCount * 2)
}


