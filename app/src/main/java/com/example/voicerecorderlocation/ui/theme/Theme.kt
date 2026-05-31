package com.example.voicerecorderlocation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SoundTrailColors = darkColorScheme(
    primary             = Mint,
    onPrimary           = OnMint,
    primaryContainer    = MintSoft,
    onPrimaryContainer  = Mint,
    secondary           = Gold,
    onSecondary         = OnMint,
    error               = Coral,
    onError             = OnMint,
    background          = Bg,
    onBackground        = TextHi,
    surface             = Panel,
    onSurface           = TextHi,
    surfaceVariant      = Panel2,
    onSurfaceVariant    = TextMut,
    outline             = Hair2,
    outlineVariant      = Hair,
)

// Numeric / timer readouts use monospace for the "instrument" feel.
// Drop Space Grotesk / Noto Sans SC into res/font and swap here for pixel-perfect match.
val NumFamily = FontFamily.Monospace
val UiFamily  = FontFamily.Default

private val SoundTrailType = Typography(
    headlineMedium = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Bold,      fontSize = 26.sp, letterSpacing = (-0.5).sp),
    titleLarge     = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Bold,      fontSize = 20.sp),
    titleMedium    = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.SemiBold,  fontSize = 16.sp),
    bodyMedium     = TextStyle(fontFamily = UiFamily,                                    fontSize = 14.sp),
    bodySmall      = TextStyle(fontFamily = UiFamily,                                    fontSize = 12.sp),
    labelSmall     = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Medium,    fontSize = 11.sp),
)

@Composable
fun SoundTrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoundTrailColors,
        typography  = SoundTrailType,
        content     = content
    )
}
