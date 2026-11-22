package com.myvault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colours the Material scheme has no slot for. */
@Immutable
data class VaultPalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    /** Soft tint behind the top of onboarding screens. */
    val glow: Color,
    val card: Color,
    /** Hairline separators and card outlines. */
    val cardBorder: Color,
    val accent: Color,
    val accentDeep: Color,
    val success: Color,
    val warning: Color,
    /** Neutral fill for search fields, segmented controls and unselected chips. */
    val fill: Color,
    val favorite: Color,
    val cardShadow: Color,
    val isDark: Boolean,
) {
    /** The blue used for primary actions. */
    val accentBrush: Brush get() = Brush.verticalGradient(listOf(accent, accentDeep))
}

private val LightPalette = VaultPalette(
    backgroundTop = Color(0xFFF7F9FE),
    backgroundBottom = Color(0xFFF2F3F7),
    glow = Color(0x143D7BFF),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE7E8EE),
    accent = Color(0xFF1F7AFF),
    accentDeep = Color(0xFF0A5CEB),
    success = Color(0xFF28B052),
    warning = Color(0xFFE08600),
    fill = Color(0xFFEBECF1),
    favorite = Color(0xFFFFB300),
    cardShadow = Color(0x1A1B2A4A),
    isDark = false,
)

private val DarkPalette = VaultPalette(
    backgroundTop = Color(0xFF000000),
    backgroundBottom = Color(0xFF000000),
    glow = Color(0x1F3D7BFF),
    card = Color(0xFF1C1C1E),
    cardBorder = Color(0xFF2E2E31),
    accent = Color(0xFF2B8CFF),
    accentDeep = Color(0xFF0A6FF0),
    success = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    fill = Color(0xFF2C2C2E),
    favorite = Color(0xFFFFD60A),
    cardShadow = Color(0x00000000),
    isDark = true,
)

private val LightColors = lightColorScheme(
    primary = LightPalette.accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EEFF),
    onPrimaryContainer = Color(0xFF0A4FCC),
    secondary = Color(0xFF6E6E73),
    onSecondary = Color.White,
    background = Color(0xFFF2F3F7),
    onBackground = Color(0xFF0B0B0F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B0B0F),
    surfaceVariant = LightPalette.fill,
    onSurfaceVariant = Color(0xFF6C6C73),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = LightPalette.fill,
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color.White,
    outline = Color(0xFFC9C9CF),
    outlineVariant = LightPalette.cardBorder,
    error = Color(0xFFE5342A),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = DarkPalette.accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0E2A52),
    onPrimaryContainer = Color(0xFFBFD9FF),
    secondary = Color(0xFF98989F),
    onSecondary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = DarkPalette.fill,
    onSurfaceVariant = Color(0xFF9A9AA1),
    surfaceContainerLowest = Color(0xFF1C1C1E),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = DarkPalette.fill,
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color.Black,
    outline = Color(0xFF48484A),
    outlineVariant = DarkPalette.cardBorder,
    error = Color(0xFFFF453A),
    onError = Color.White,
)

// The platform font with iOS-like weights and tight tracking on titles.
private val VaultTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.1).sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

private val LocalVaultPalette = staticCompositionLocalOf { LightPalette }

object VaultTheme {
    val palette: VaultPalette
        @Composable get() = LocalVaultPalette.current
}

@Composable
fun MyVaultTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalVaultPalette provides if (darkTheme) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = VaultTypography,
            content = content,
        )
    }
}
