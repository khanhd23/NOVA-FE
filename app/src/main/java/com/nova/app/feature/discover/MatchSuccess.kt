package com.nova.app.feature.discover

import com.nova.app.core.designsystem.NovaBrand

import androidx.compose.ui.res.stringResource
import com.nova.app.R

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.core.ui.NovaButton
import com.nova.app.ui.theme.PurpleMain
import com.nova.app.ui.theme.PurplePink

@Composable
fun MatchSuccessScreen(onSendMessage: () -> Unit, onKeepSwiping: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.match_title),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    brush = Brush.linearGradient(NovaBrand.gradient)
                ),
                modifier = Modifier.scale(scale)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy((-20).dp)) {
                MatchAvatar(MaterialTheme.colorScheme.surfaceVariant)
                MatchAvatar(MaterialTheme.colorScheme.surface)
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = stringResource(R.string.match_subtitle),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            NovaButton(
                text = stringResource(R.string.match_send_message),
                onClick = onSendMessage,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.match_keep_swiping),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                modifier = Modifier.clickable { onKeepSwiping() },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun MatchAvatar(color: Color) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(color)
            .background(
                Brush.radialGradient(
                    listOf(PurpleMain.copy(alpha = 0.3f), Color.Transparent)
                )
            )
    )
}
