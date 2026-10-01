package com.nova.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.nova.app.core.designsystem.NovaBrand
import com.nova.app.core.designsystem.NovaColors
import com.nova.app.core.designsystem.NovaPalette

// Accent for text, icons and tints: adapts to light/dark so it stays readable.
// For backgrounds that carry white content use NovaBrand instead.
val PurpleMain: Color
    @Composable
    @ReadOnlyComposable
    get() = NovaColors.current.accent

val PurplePink: Color
    @Composable
    @ReadOnlyComposable
    get() = NovaColors.current.accentPink

// Middle stop of the brand gradient.
val PurpleMedium = NovaBrand.Mid

// Accent & Functional
val AccentPink = NovaPalette.AccentPink
val SuccessGreen = NovaPalette.Success
val WarningOrange = NovaPalette.Warning

// --- DARK THEME RAW COLORS ---
val BgDark = NovaPalette.Background
val BgCardDark = NovaPalette.Card
val SurfaceVariantDark = NovaPalette.BackgroundAlt2
val BorderDark = NovaPalette.Border
val GlassDark = Color(0x0AFFFFFF)
val TextPrimaryDark = NovaPalette.TextPrimary
val TextSecondaryDark = NovaPalette.TextSecondary

// --- LIGHT THEME RAW COLORS ---
val BgLight = NovaPalette.LightBackground
val BgCardLight = NovaPalette.LightCardAlt
val SurfaceVariantLight = NovaPalette.LightCard
val BorderLight = NovaPalette.LightBorder
val GlassLight = Color(0x14000000)
val TextPrimaryLight = NovaPalette.LightTextPrimary
val TextSecondaryLight = NovaPalette.LightTextSecondary
