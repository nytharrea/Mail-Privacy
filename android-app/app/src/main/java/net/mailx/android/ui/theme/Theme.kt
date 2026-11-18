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
    onPrimary = Color(0xFF0F122E),
    primaryContainer = MailxPalette.BrandPrimaryContainerDark,
    onPrimaryContainer = MailxPalette.BrandOnPrimaryContainerDark,
    secondary = MailxPalette.BrandAccentTealDark,
    onSecondary = Color(0xFF042630),
    secondaryContainer = MailxPalette.BrandSecondaryContainerDark,
    onSecondaryContainer = MailxPalette.BrandOnSecondaryContainerDark,
    tertiary = MailxPalette.BrandTertiaryVioletDark,
    onTertiary = Color(0xFF280B45),
    tertiaryContainer = MailxPalette.BrandTertiaryContainerDark,
    onTertiaryContainer = MailxPalette.BrandOnTertiaryContainerDark,
    background = MailxPalette.Obsidian950,
    onBackground = MailxPalette.Slate100,
    surface = MailxPalette.Obsidian925,
    onSurface = MailxPalette.Slate100,
    surfaceVariant = MailxPalette.Obsidian800,
    onSurfaceVariant = MailxPalette.Slate400,
    surfaceContainerLowest = Color(0xFF04060B),
    surfaceContainerLow = MailxPalette.Obsidian900,
    surfaceContainer = MailxPalette.Obsidian850,
    surfaceContainerHigh = MailxPalette.Obsidian800,
    surfaceContainerHighest = MailxPalette.Obsidian750,
    outline = MailxPalette.Slate600,
    outlineVariant = MailxPalette.ObsidianBorder,
    error = MailxPalette.BlockedRose,
    onError = Color.White,
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
    surfaceContainerLow = Color(0xFFF4F7FB),
    surfaceContainer = Color(0xFFEEF2F7),
    surfaceContainerHigh = Color(0xFFE6ECF4),
    surfaceContainerHighest = MailxPalette.Pearl200,
    outline = MailxPalette.Slate400,
    outlineVariant = MailxPalette.Pearl300,
    error = MailxPalette.BlockedRose,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337)
)

private fun ColorScheme.withAmoledPitchBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF090C14),
    surfaceContainer = Color(0xFF101420),
    surfaceContainerHigh = Color(0xFF161C2C),
    surfaceContainerHighest = Color(0xFF1E263B)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MailxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledDark: Boolean = false,
    expressiveMotion: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
    }

    val finalScheme = if (darkTheme && amoledDark) {
        baseScheme.withAmoledPitchBlack()
    } else {
        baseScheme
    }

    val motionScheme = if (expressiveMotion && !reducedMotion) {
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
