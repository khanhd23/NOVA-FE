package com.nova.app.feature.auth

import com.nova.app.core.designsystem.NovaBrand

import androidx.compose.ui.res.stringResource
import com.nova.app.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.ui.theme.*

enum class SignInProvider { Google, Facebook }

@Composable
fun LoginScreen(
    onGoogleLogin: () -> Unit,
    onFacebookLogin: () -> Unit,
    loadingProvider: SignInProvider? = null,
    errorMessage: String? = null,
) {
    val busy = loadingProvider != null
    val glow = rememberInfiniteTransition(label = "login-glow")
    val glowScale by glow.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow-scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Soft brand glows behind the hero.
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-90).dp, y = (-40).dp)
                .scale(glowScale)
                .blur(90.dp)
                .alpha(0.45f)
                .background(PurpleMain, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = 180.dp)
                .scale(2f - glowScale)
                .blur(90.dp)
                .alpha(0.35f)
                .background(PurplePink, CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(NovaBrand.gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "NOVA",
                style = MaterialTheme.typography.displayLarge.copy(
                    brush = Brush.linearGradient(NovaBrand.gradient),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                )
            )
            Text(
                text = stringResource(R.string.login_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            )

            Spacer(modifier = Modifier.height(36.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureBadge(Icons.Default.Favorite, stringResource(R.string.login_feature_match))
                FeatureBadge(Icons.Default.Videocam, stringResource(R.string.login_feature_video))
                FeatureBadge(Icons.Default.Groups, stringResource(R.string.login_feature_community))
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.login_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.login_card_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
                )

                SocialButton(
                    text = stringResource(R.string.login_google),
                    loading = loadingProvider == SignInProvider.Google,
                    enabled = !busy,
                    onClick = onGoogleLogin,
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F1F1F),
                    leading = { GoogleMark() },
                )
                Spacer(modifier = Modifier.height(12.dp))
                SocialButton(
                    text = stringResource(R.string.login_facebook),
                    loading = loadingProvider == SignInProvider.Facebook,
                    enabled = !busy,
                    onClick = onFacebookLogin,
                    containerColor = Color(0xFF1877F2),
                    contentColor = Color.White,
                    leading = { Icon(Icons.Default.Facebook, contentDescription = null, tint = Color.White) },
                )

                AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.login_terms),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        }
    }
}

@Composable
private fun FeatureBadge(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = PurpleMedium, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun SocialButton(
    text: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(CircleShape)
            .background(containerColor.copy(alpha = if (enabled || loading) 1f else 0.55f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(color = contentColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                leading()
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (loading) stringResource(R.string.login_signing_in) else text,
            color = contentColor,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun GoogleMark() {
    Text(
        text = "G",
        style = MaterialTheme.typography.titleLarge.copy(
            brush = Brush.linearGradient(
                listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFFFBBC05), Color(0xFF34A853))
            ),
            fontWeight = FontWeight.Black,
        )
    )
}

@Preview
@Composable
private fun LoginScreenPreview() {
    NOVATheme {
        LoginScreen(onGoogleLogin = {}, onFacebookLogin = {}, errorMessage = "Could not sign in. Please try again.")
    }
}
