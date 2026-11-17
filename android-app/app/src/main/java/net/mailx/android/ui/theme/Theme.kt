package net.mailx.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AmoledColorScheme = darkColorScheme(
    primary = AddyPalette.PrimaryDark,
    onPrimary = AddyPalette.OnPrimaryDark,
    primaryContainer = AddyPalette.PrimaryContainerDark,
    onPrimaryContainer = AddyPalette.OnPrimaryContainerDark,
    secondary = AddyPalette.SecondaryDark,
    onSecondary = AddyPalette.OnSecondaryDark,
    secondaryContainer = AddyPalette.SecondaryContainerDark,
    onSecondaryContainer = AddyPalette.OnSecondaryContainerDark,
    tertiary = AddyPalette.PinnedAmber,
    onTertiary = Color(0xFF332200),
    tertiaryContainer = Color(0xFF2B1F06),
    onTertiaryContainer = Color(0xFFFFDF9E),
    error = AddyPalette.BlockedRed,
    onError = Color.White,
    errorContainer = Color(0xFF3B0A11),
    onErrorContainer = Color(0xFFFFD9DC),
    background = AddyPalette.AmoledBackground,
    onBackground = AddyPalette.TextPrimary,
    surface = AddyPalette.AmoledSurface,
    onSurface = AddyPalette.TextPrimary,
    surfaceVariant = AddyPalette.AmoledSurfaceContainer,
    onSurfaceVariant = AddyPalette.TextSecondary,
    surfaceContainerLowest = AddyPalette.AmoledBackground,
    surfaceContainerLow = AddyPalette.AmoledSurfaceLow,
    surfaceContainer = AddyPalette.AmoledSurfaceContainer,
    surfaceContainerHigh = AddyPalette.AmoledSurfaceHigh,
    surfaceContainerHighest = AddyPalette.AmoledSurfaceHighest,
    outline = AddyPalette.AmoledOutline,
    outlineVariant = AddyPalette.AmoledOutlineVariant,
    inverseSurface = Color(0xFFF4F6F8),
    inverseOnSurface = Color(0xFF0B0D10),
    inversePrimary = Color(0xFF006876)
)

private val CarbonDarkColorScheme = AmoledColorScheme.copy(
    background = AddyPalette.DarkBackground,
    surface = AddyPalette.DarkSurface,
    surfaceVariant = AddyPalette.DarkSurfaceContainer,
    surfaceContainerLowest = AddyPalette.DarkBackground,
    surfaceContainerLow = AddyPalette.DarkSurfaceLow,
    surfaceContainer = AddyPalette.DarkSurfaceContainer,
    surfaceContainerHigh = AddyPalette.DarkSurfaceHigh,
    surfaceContainerHighest = AddyPalette.DarkSurfaceHighest,
    outline = AddyPalette.DarkOutline,
    outlineVariant = AddyPalette.DarkOutlineVariant
)

private fun ColorScheme.forceAmoledBlack(): ColorScheme = copy(
    background = AddyPalette.AmoledBackground,
    surface = AddyPalette.AmoledSurface,
    surfaceVariant = AddyPalette.AmoledSurfaceContainer,
    surfaceContainerLowest = AddyPalette.AmoledBackground,
    surfaceContainerLow = AddyPalette.AmoledSurfaceLow,
    surfaceContainer = AddyPalette.AmoledSurfaceContainer,
    surfaceContainerHigh = AddyPalette.AmoledSurfaceHigh,
    surfaceContainerHighest = AddyPalette.AmoledSurfaceHighest,
    outline = AddyPalette.AmoledOutline,
    outlineVariant = AddyPalette.AmoledOutlineVariant
)

@Composable
fun MailxAddyTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    amoledDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicDarkColorScheme(context)
        }
        amoledDark -> AmoledColorScheme
        else -> CarbonDarkColorScheme
    }

    val colorScheme = if (amoledDark) {
        baseScheme.forceAmoledBlack()
    } else {
        baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Black.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Black.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MailxShapes,
        typography = MailxTypography,
        content = content
    )
}
