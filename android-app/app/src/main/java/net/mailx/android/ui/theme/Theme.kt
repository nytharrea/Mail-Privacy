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
    onPrimary = Color(0xFF111113),
    primaryContainer = MailxPalette.BrandPrimaryContainerDark,
    onPrimaryContainer = MailxPalette.BrandOnPrimaryContainerDark,
    secondary = MailxPalette.BrandAccentTealDark,
    onSecondary = Color(0xFF141417),
    secondaryContainer = MailxPalette.BrandSecondaryContainerDark,
    onSecondaryContainer = MailxPalette.BrandOnSecondaryContainerDark,
    tertiary = MailxPalette.BrandTertiaryVioletDark,
    onTertiary = Color(0xFF141820),
    tertiaryContainer = MailxPalette.BrandTertiaryContainerDark,
    onTertiaryContainer = MailxPalette.BrandOnTertiaryContainerDark,
    background = MailxPalette.Obsidian950,
    onBackground = MailxPalette.Slate100,
    surface = MailxPalette.Obsidian925,
    onSurface = MailxPalette.Slate100,
    surfaceVariant = MailxPalette.Obsidian800,
    onSurfaceVariant = MailxPalette.Slate400,
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = MailxPalette.Obsidian900,
    surfaceContainer = MailxPalette.Obsidian850,
    surfaceContainerHigh = MailxPalette.Obsidian800,
    surfaceContainerHighest = MailxPalette.Obsidian750,
    outline = MailxPalette.Slate600,
    outlineVariant = MailxPalette.ObsidianBorder,
    error = MailxPalette.BlockedRose,
    onError = Color(0xFF2B0B0E),
    errorContainer = Color(0xFF3B1418),
    onErrorContainer = Color(0xFFFCE7E9)
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
    surfaceContainerLowest = Color(0xFFF2F2F5),
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF1F1F4),
    surfaceContainerHigh = Color(0xFFE7E7EC),
    surfaceContainerHighest = MailxPalette.Pearl200,
    outline = MailxPalette.Slate400,
    outlineVariant = MailxPalette.Pearl300,
    error = Color(0xFFB94A52),
    onError = Color.White,
    errorContainer = Color(0xFFFCE7E9),
    onErrorContainer = Color(0xFF691B21)
)

/**
 * Softens Android 12+ Dynamic Wallpaper colors so they never produce glaring neon/electric blue
 * switches or borders, while preserving true achromatic Zinc surfaces.
 */
private fun ColorScheme.withCalmDynamicDark(): ColorScheme {
    val calmPrimary = lerp(primary, MailxPalette.BrandPrimaryDark, 0.72f)
    val calmSecondary = lerp(secondary, MailxPalette.BrandAccentTealDark, 0.75f)
    return copy(
        primary = calmPrimary,
        onPrimary = Color(0xFF111113),
        primaryContainer = MailxPalette.BrandPrimaryContainerDark,
        onPrimaryContainer = MailxPalette.BrandOnPrimaryContainerDark,
        secondary = calmSecondary,
        onSecondary = Color(0xFF141417),
        secondaryContainer = MailxPalette.BrandSecondaryContainerDark,
        onSecondaryContainer = MailxPalette.BrandOnSecondaryContainerDark,
        background = MailxPalette.Obsidian950,
        surface = MailxPalette.Obsidian925,
        surfaceVariant = MailxPalette.Obsidian800,
        surfaceContainerLowest = Color(0xFF0E0E10),
        surfaceContainerLow = MailxPalette.Obsidian900,
        surfaceContainer = MailxPalette.Obsidian850,
        surfaceContainerHigh = MailxPalette.Obsidian800,
        surfaceContainerHighest = MailxPalette.Obsidian750,
        outline = MailxPalette.Slate600,
        outlineVariant = MailxPalette.ObsidianBorder
    )
}

private fun ColorScheme.withCalmDynamicLight(): ColorScheme {
    val calmPrimary = lerp(primary, MailxPalette.BrandPrimary, 0.68f)
    return copy(
        primary = calmPrimary,
        onPrimary = Color.White,
        background = MailxPalette.Pearl50,
        surface = MailxPalette.Pearl100,
        surfaceContainerLowest = Color(0xFFF2F2F5),
        surfaceContainerLow = Color.White,
        surfaceContainer = Color(0xFFF1F1F4),
        surfaceContainerHigh = Color(0xFFE7E7EC),
        surfaceContainerHighest = MailxPalette.Pearl200,
        outlineVariant = MailxPalette.Pearl300
    )
}

private fun ColorScheme.withAmoledPitchBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color(0xFF09090B),
    surfaceContainerLowest = Color(0xFF0C0C0E),
    surfaceContainerLow = Color(0xFF121214),
    surfaceContainer = Color(0xFF19191C),
    surfaceContainerHigh = Color(0xFF222226),
    surfaceContainerHighest = Color(0xFF2B2B30),
    outlineVariant = Color(0xFF252529)
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
            dynamicDarkColorScheme(context).withCalmDynamicDark()
        } else {
            dynamicLightColorScheme(context).withCalmDynamicLight()
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
