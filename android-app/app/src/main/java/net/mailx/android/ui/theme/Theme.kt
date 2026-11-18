package net.mailx.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

val LocalReducedMotion = compositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = MailxPalette.BrandPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = MailxPalette.BrandPrimaryContainerDark,
    onPrimaryContainer = MailxPalette.BrandOnPrimaryContainerDark,
    secondary = MailxPalette.BrandAccentTealDark,
    onSecondary = Color(0xFF062033),
    secondaryContainer = MailxPalette.BrandSecondaryContainerDark,
    onSecondaryContainer = MailxPalette.BrandOnSecondaryContainerDark,
    tertiary = MailxPalette.BrandTertiaryVioletDark,
    onTertiary = Color(0xFF1B1038),
    tertiaryContainer = MailxPalette.BrandTertiaryContainerDark,
    onTertiaryContainer = MailxPalette.BrandOnTertiaryContainerDark,
    background = MailxPalette.Obsidian950,
    onBackground = MailxPalette.Slate100,
    surface = MailxPalette.Obsidian925,
    onSurface = MailxPalette.Slate100,
    surfaceVariant = MailxPalette.Obsidian800,
    onSurfaceVariant = MailxPalette.Slate400,
    surfaceContainerLowest = Color(0xFF070A10),
    surfaceContainerLow = MailxPalette.Obsidian900,
    surfaceContainer = MailxPalette.Obsidian850,
    surfaceContainerHigh = MailxPalette.Obsidian800,
    surfaceContainerHighest = MailxPalette.Obsidian750,
    outline = MailxPalette.Slate600,
    outlineVariant = MailxPalette.ObsidianBorder,
    error = MailxPalette.BlockedRose,
    onError = Color.White,
    errorContainer = Color(0xFF3F1521),
    onErrorContainer = Color(0xFFFFE4E6)
)

private val LightColorScheme = lightColorScheme(
    primary = MailxPalette.BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = MailxPalette.BrandPrimaryContainerLight,
    onPrimaryContainer = MailxPalette.BrandOnPrimaryContainerLight,
    secondary = MailxPalette.BrandAccentTeal,
    onSecondary = Color.White,
    secondaryContainer = MailxPalette.BrandSecondaryContainerLight,
    onSecondaryContainer = MailxPalette.BrandOnSecondaryContainerLight,
    tertiary = MailxPalette.BrandTertiaryViolet,
    onTertiary = Color.White,
    tertiaryContainer = MailxPalette.BrandTertiaryContainerLight,
    onTertiaryContainer = MailxPalette.BrandOnTertiaryContainerLight,
    background = MailxPalette.Pearl50,
    onBackground = MailxPalette.Slate900,
    surface = MailxPalette.Pearl100,
    onSurface = MailxPalette.Slate900,
    surfaceVariant = MailxPalette.Pearl150,
    onSurfaceVariant = MailxPalette.Slate600,
    surfaceContainerLowest = Color(0xFFF1F5F9),
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = MailxPalette.Pearl300,
    outline = MailxPalette.Slate400,
    outlineVariant = MailxPalette.Pearl200,
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337)
)

private fun ColorScheme.withHarmonizedDynamicDark(): ColorScheme {
    val balancedPrimary = lerp(primary, MailxPalette.BrandPrimaryDark, 0.45f)
    val balancedSecondary = lerp(secondary, MailxPalette.BrandAccentTealDark, 0.45f)
    return copy(
        primary = balancedPrimary,
        onPrimary = Color.White,
        primaryContainer = lerp(primaryContainer, MailxPalette.BrandPrimaryContainerDark, 0.55f),
        onPrimaryContainer = MailxPalette.BrandOnPrimaryContainerDark,
        secondary = balancedSecondary,
        secondaryContainer = lerp(secondaryContainer, MailxPalette.BrandSecondaryContainerDark, 0.55f),
        onSecondaryContainer = MailxPalette.BrandOnSecondaryContainerDark,
        background = MailxPalette.Obsidian950,
        surface = MailxPalette.Obsidian925,
        surfaceVariant = MailxPalette.Obsidian800,
        surfaceContainerLowest = Color(0xFF070A10),
        surfaceContainerLow = MailxPalette.Obsidian900,
        surfaceContainer = MailxPalette.Obsidian850,
        surfaceContainerHigh = MailxPalette.Obsidian800,
        surfaceContainerHighest = MailxPalette.Obsidian750,
        outline = MailxPalette.Slate600,
        outlineVariant = MailxPalette.ObsidianBorder
    )
}

private fun ColorScheme.withHarmonizedDynamicLight(): ColorScheme {
    val balancedPrimary = lerp(primary, MailxPalette.BrandPrimary, 0.45f)
    return copy(
        primary = balancedPrimary,
        onPrimary = Color.White,
        background = MailxPalette.Pearl50,
        surface = MailxPalette.Pearl100,
        surfaceContainerLowest = Color(0xFFF1F5F9),
        surfaceContainerLow = Color.White,
        surfaceContainer = Color(0xFFF1F5F9),
        surfaceContainerHigh = Color(0xFFE2E8F0),
        surfaceContainerHighest = MailxPalette.Pearl300,
        outlineVariant = MailxPalette.Pearl200
    )
}

private fun ColorScheme.withAmoledPitchBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color(0xFF06080E),
    surfaceContainerLowest = Color(0xFF04060A),
    surfaceContainerLow = Color(0xFF0B0E17),
    surfaceContainer = Color(0xFF111624),
    surfaceContainerHigh = Color(0xFF181F32),
    surfaceContainerHighest = Color(0xFF202942),
    outlineVariant = Color(0xFF1E263D)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MailxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoledDark: Boolean = false,
    expressiveMotion: Boolean = true,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) {
            dynamicDarkColorScheme(context).withHarmonizedDynamicDark()
        } else {
            dynamicLightColorScheme(context).withHarmonizedDynamicLight()
        }
    } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
    }

    val finalScheme = if (darkTheme && amoledDark) {
        baseScheme.withAmoledPitchBlack()
    } else {
        baseScheme
    }

    val motionScheme = if (reducedMotion) {
        MotionScheme.standard()
    } else if (expressiveMotion) {
        MotionScheme.expressive()
    } else {
        MotionScheme.standard()
    }

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialExpressiveTheme(
            colorScheme = finalScheme,
            typography = MailxTypography,
            shapes = MailxShapes,
            motionScheme = motionScheme,
            content = content
        )
    }
}
