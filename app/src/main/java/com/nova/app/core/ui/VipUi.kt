package com.nova.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink

@Composable
fun VipAvatar(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    vipTierId: String? = null,
    premium: Boolean = false,
    fallbackModel: String = imageUrl,
    borderWidth: Dp = 2.dp,
    padding: Dp = 3.dp,
) {
    val palette = vipPalette(vipTierId, premium)
    val brush = Brush.linearGradient(palette.colors)
    val ringModifier = Modifier
        .clip(CircleShape)
        .border(border = androidx.compose.foundation.BorderStroke(borderWidth, brush), shape = CircleShape)
        .background(palette.background)
        .padding(padding)

    Box(modifier = modifier.then(ringModifier), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = imageUrl.ifBlank { fallbackModel.ifBlank { defaultAvatarModel(contentDescription ?: "Nova User") } },
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun VipTierChip(
    tierLabel: String?,
    premium: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = vipPalette(tierLabel, premium)
    val text = tierLabel?.takeIf { it.isNotBlank() } ?: if (premium) "VIP" else "Free"
    Surface(
        modifier = modifier,
        color = palette.background.copy(alpha = 0.9f),
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Brush.linearGradient(palette.colors)),
    ) {
        Text(
            text = text,
            color = palette.text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private data class VipPalette(
    val colors: List<Color>,
    val background: Color,
    val text: Color,
)

@Composable
private fun vipPalette(tierId: String?, premium: Boolean): VipPalette {
    val normalized = tierId?.lowercase().orEmpty()
    return when {
        normalized.contains("vip_1") || normalized.contains("spark") -> VipPalette(
            colors = listOf(Color(0xFF58C7FF), Color(0xFF7A5CFF)),
            background = Color(0xFF58C7FF).copy(alpha = 0.14f),
            text = Color(0xFF8AD9FF),
        )
        normalized.contains("vip_2") || normalized.contains("glow") -> VipPalette(
            colors = listOf(Color(0xFFFF69B4), Color(0xFF8B5CF6)),
            background = Color(0xFFFF69B4).copy(alpha = 0.14f),
            text = Color(0xFFFF92D0),
        )
        normalized.contains("vip_3") || normalized.contains("pulse") -> VipPalette(
            colors = listOf(Color(0xFFFF8A65), Color(0xFFFF4D6D)),
            background = Color(0xFFFF8A65).copy(alpha = 0.14f),
            text = Color(0xFFFFB199),
        )
        normalized.contains("vip_4") || normalized.contains("elite") -> VipPalette(
            colors = listOf(Color(0xFF26C6DA), Color(0xFF3F51B5)),
            background = Color(0xFF26C6DA).copy(alpha = 0.14f),
            text = Color(0xFF7DE8F3),
        )
        normalized.contains("vip_5") || normalized.contains("prime") -> VipPalette(
            colors = listOf(Color(0xFFF9D423), Color(0xFFFF8A00)),
            background = Color(0xFFF9D423).copy(alpha = 0.14f),
            text = Color(0xFFFFE082),
        )
        normalized.contains("vip_6") || normalized.contains("aura") -> VipPalette(
            colors = listOf(Color(0xFF9C27B0), Color(0xFF00E5FF)),
            background = Color(0xFF9C27B0).copy(alpha = 0.14f),
            text = Color(0xFFCE93D8),
        )
        normalized.contains("vip_7") || normalized.contains("infinity") -> VipPalette(
            colors = listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF), Color(0xFFFF4081)),
            background = Color(0xFF7C4DFF).copy(alpha = 0.16f),
            text = Color(0xFFE1BEE7),
        )
        premium -> VipPalette(
            colors = listOf(PurpleMain, PurplePink),
            background = PurpleMain.copy(alpha = 0.14f),
            text = Color(0xFFE1D8FF),
        )
        else -> VipPalette(
            colors = listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outline),
            background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            text = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

private fun defaultAvatarModel(name: String): String {
    val encoded = java.net.URLEncoder.encode(name.ifBlank { "Nova User" }, Charsets.UTF_8)
    return "https://ui-avatars.com/api/?name=$encoded&background=6C5CE7&color=FFFFFF&size=512"
}
