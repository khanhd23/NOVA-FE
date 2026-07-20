package com.nova.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.core.ui.NovaButton
import com.nova.app.ui.theme.*

@Composable
fun LoginScreen(
    onGoogleLogin: () -> Unit,
    onFacebookLogin: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "NOVA",
                style = MaterialTheme.typography.displayLarge.copy(
                    brush = Brush.linearGradient(listOf(PurpleMain, PurplePink))
                )
            )
            Text(
                text = "Connect by Emotion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            NovaButton(
                text = "Sign in with Google",
                onClick = onGoogleLogin,
                containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                icon = Icons.Default.AccountCircle
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            NovaButton(
                text = "Sign in with Facebook",
                onClick = onFacebookLogin,
                containerColor = Color(0xFF1877F2),
                icon = Icons.Default.Facebook
            )
            
            Spacer(modifier = Modifier.weight(1.2f))
            
            Text(
                "By joining, you agree to our Terms and Privacy Policy",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
