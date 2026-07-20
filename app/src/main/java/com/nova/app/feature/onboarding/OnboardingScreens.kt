package com.nova.app.feature.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.app.ui.theme.*

@Composable
fun SplashScreen() {
    val scale = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "NOVA",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    brush = Brush.linearGradient(
                        colors = listOf(PurpleMain, PurplePink)
                    )
                ),
                modifier = Modifier.scale(scale.value)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect by Emotion",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    letterSpacing = 2.sp
                )
            )
        }
    }
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    // Basic onboarding for now, can be expanded to 4-6 pages
    var currentPage by remember { mutableStateOf(0) }
    val pages = listOf(
        OnboardingData("AI Matching", "Discover your soulmate with advanced AI analysis.", PurpleMain),
        OnboardingData("Video Date", "Connect deeper with high-quality video calls.", PurpleMedium),
        OnboardingData("Community", "Join groups that share your passions.", PurplePink)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .background(pages[currentPage].color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Placeholder for Lottie/Animation
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(pages[currentPage].color, CircleShape)
                )
            }
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = pages[currentPage].title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = pages[currentPage].description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        com.nova.app.core.ui.NovaButton(
            text = if (currentPage < pages.size - 1) "Next" else "Get Started",
            onClick = {
                if (currentPage < pages.size - 1) {
                    currentPage++
                } else {
                    onFinish()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

data class OnboardingData(val title: String, val description: String, val color: Color)

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun SplashScreenPreview() {
    NOVATheme {
        SplashScreen()
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun OnboardingScreenPreview() {
    NOVATheme {
        OnboardingScreen {}
    }
}
