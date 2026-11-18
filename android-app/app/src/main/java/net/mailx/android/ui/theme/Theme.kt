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
import androidx.compose.ui.platform.LocalContext

val LocalReducedMotion = compositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = MailxPalette.BrandPrimaryDark,
    onPrimary = Color(0xFF042F20),
    primaryContainer = MailxPalette.BrandPrimaryContainerDark,
    onPrimaryContainer = MailxPalette.BrandOnPrimaryContainerDark,
    secondary = MailxPalette.BrandAccentTealDark,
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = MailxPalette.BrandSecondaryContainerDark,
    onSecondaryContainer = MailxPalette.BrandOnSecondaryContainerDark,
    tertiary = MailxPalette.BrandTertiaryVioletDark,
    onTertiary = Color(0xFF1E1B4B),
    tertiaryContainer = MailxPalette.BrandTertiaryContainerDark,
    onTertiaryContainer = MailxPalette.BrandOnTertiaryContainerDark,
    background = MailxPalette.Obsidian950,
    onBackground = MailxPalette.Slate100,
    surface = MailxPalette.Obsidian925,
    onSurface = MailxPalette.Slate100,
    surfaceVariant = MailxPalette.Obsidian800,
    onSurfaceVariant = MailxPalette.Slate400,
    surfaceContainerLowest = Color(0xFF050608),
    surfaceContainerLow = MailxPalette.Obsidian900,
    surfaceContainer = MailxPalette.Obsidian850,
    surfaceContainerHigh = MailxPalette.Obsidian800,
    surfaceContainerHighest = MailxPalette.Obsidian750,
    outline = MailxPalette.Slate600,
    outlineVariant = MailxPalette.ObsidianBorder,
    error = MailxPalette.BlockedRose,
    onError = Color(0xFF3F0614),
    errorContainer = Color(0xFF4C0519),
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
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F6F8),
    surfaceContainer = Color(0xFFEDEFF3),
    surfaceContainerHigh = Color(0xFFE5E8EE),
    surfaceContainerHighest = MailxPalette.Pearl200,
    outline = MailxPalette.Slate400,
    outlineVariant = MailxPalette.Pearl300,
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337)
)

private fun ColorScheme.withCarbonDarkSurfaces(): ColorScheme = copy(
    background = MailxPalette.Obsidian950,
    surface = MailxPalette.Obsidian925,
    surfaceVariant = MailxPalette.Obsidian800,
    surfaceContainerLowest = Color(0xFF050608),
    surfaceContainerLow = MailxPalette.Obsidian900,
    surfaceContainer = MailxPalette.Obsidian850,
    surfaceContainerHigh = MailxPalette.Obsidian800,
    surfaceContainerHighest = MailxPalette.Obsidian750,
    outlineVariant = MailxPalette.ObsidianBorder
)

private fun ColorScheme.withAmoledPitchBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color(0xFF07080B),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0D0F14),
    surfaceContainer = Color(0xFF13161D),
    surfaceContainerHigh = Color(0xFF1A1E27),
    surfaceContainerHighest = Color(0xFF222733),
    outlineVariant = Color(0xFF242A35)
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
            // Preserve crisp Carbon-Graphite surface hierarchy even when Dynamic Color is active
            dynamicDarkColorScheme(context).withCarbonDarkSurfaces()
        } else {
            dynamicLightColorScheme(context)
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
