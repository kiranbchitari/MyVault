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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colours the Material scheme has no slot for. */
@Immutable
data class VaultPalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val glow: Color,
    val card: Color,
    val cardBorder: Color,
    val accent: Color,
    val accentDeep: Color,
    val success: Color,
    val warning: Color,
)

private val DarkPalette = VaultPalette(
    backgroundTop = Color(0xFF0F1830),
    backgroundBottom = Color(0xFF080D19),
    glow = Color(0x33E2C27A),
    card = Color(0xFF131C31),
    cardBorder = Color(0xFF222E4A),
    accent = Color(0xFFE8CB8A),
    accentDeep = Color(0xFFC49A45),
    success = Color(0xFF5AD1A6),
    warning = Color(0xFFF2B866),
)

private val LightPalette = VaultPalette(
    backgroundTop = Color(0xFFFFFFFF),
    backgroundBottom = Color(0xFFEEF1F7),
    glow = Color(0x26B8892C),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFDDE2EC),
    accent = Color(0xFFB8892C),
    accentDeep = Color(0xFF8C651A),
    success = Color(0xFF1F8F68),
    warning = Color(0xFFA9690C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE2C27A),
    onPrimary = Color(0xFF1C1503),
    primaryContainer = Color(0xFF2B2513),
    onPrimaryContainer = Color(0xFFF1DDAA),
    secondary = Color(0xFF9DB0D4),
    onSecondary = Color(0xFF0B1120),
    background = Color(0xFF080D19),
    onBackground = Color(0xFFEAEEF6),
    surface = Color(0xFF0E1526),
    onSurface = Color(0xFFEAEEF6),
    surfaceVariant = Color(0xFF172038),
    onSurfaceVariant = Color(0xFF9BA7C0),
    surfaceContainerHigh = Color(0xFF172038),
    outline = Color(0xFF34415F),
    outlineVariant = Color(0xFF222E4A),
    error = Color(0xFFFF8080),
    onError = Color(0xFF2A0606),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF14213D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E9F4),
    onPrimaryContainer = Color(0xFF14213D),
    secondary = Color(0xFF47577A),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0D1526),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D1526),
    surfaceVariant = Color(0xFFECEFF6),
    onSurfaceVariant = Color(0xFF5A667F),
    surfaceContainerHigh = Color(0xFFECEFF6),
    outline = Color(0xFFBCC5D6),
    outlineVariant = Color(0xFFDDE2EC),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val VaultTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
)

private val LocalVaultPalette = staticCompositionLocalOf { DarkPalette }

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
