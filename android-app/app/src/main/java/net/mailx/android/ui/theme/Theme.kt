package net.mailx.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AddyDarkColorScheme = darkColorScheme(
    primary = AddyPalette.PrimaryDark,
    onPrimary = AddyPalette.OnPrimaryDark,
    primaryContainer = AddyPalette.PrimaryContainerDark,
    onPrimaryContainer = AddyPalette.OnPrimaryContainerDark,
    secondary = AddyPalette.SecondaryDark,
    onSecondary = AddyPalette.OnSecondaryDark,
    secondaryContainer = AddyPalette.SecondaryContainerDark,
    onSecondaryContainer = AddyPalette.OnSecondaryContainerDark,
    tertiary = AddyPalette.TertiaryDark,
    onTertiary = AddyPalette.OnTertiaryDark,
    tertiaryContainer = AddyPalette.TertiaryContainerDark,
    onTertiaryContainer = AddyPalette.OnTertiaryContainerDark,
    error = AddyPalette.ErrorDark,
    onError = AddyPalette.OnErrorDark,
    errorContainer = AddyPalette.ErrorContainerDark,
    onErrorContainer = AddyPalette.OnErrorContainerDark,
    background = AddyPalette.DarkBackground,
    onBackground = AddyPalette.DarkOnBackground,
    surface = AddyPalette.DarkSurface,
    onSurface = AddyPalette.DarkOnSurface,
    surfaceVariant = AddyPalette.DarkSurfaceVariant,
    onSurfaceVariant = AddyPalette.DarkOnSurfaceVariant,
    surfaceContainerLowest = AddyPalette.DarkSurfaceLowest,
    surfaceContainerLow = AddyPalette.DarkSurfaceLow,
    surfaceContainer = AddyPalette.DarkSurfaceContainer,
    surfaceContainerHigh = AddyPalette.DarkSurfaceHigh,
    surfaceContainerHighest = AddyPalette.DarkSurfaceHighest,
    outline = AddyPalette.DarkOutline,
    outlineVariant = AddyPalette.DarkOutlineVariant,
    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF303036),
    inversePrimary = AddyPalette.PrimaryLight
)

private val AddyLightColorScheme = lightColorScheme(
    primary = AddyPalette.PrimaryLight,
    onPrimary = AddyPalette.OnPrimaryLight,
    primaryContainer = AddyPalette.PrimaryContainerLight,
    onPrimaryContainer = AddyPalette.OnPrimaryContainerLight,
    secondary = AddyPalette.SecondaryLight,
    onSecondary = AddyPalette.OnSecondaryLight,
    secondaryContainer = AddyPalette.SecondaryContainerLight,
    onSecondaryContainer = AddyPalette.OnSecondaryContainerLight,
    tertiary = AddyPalette.TertiaryLight,
    onTertiary = AddyPalette.OnTertiaryLight,
    tertiaryContainer = AddyPalette.TertiaryContainerLight,
    onTertiaryContainer = AddyPalette.OnTertiaryContainerLight,
    error = AddyPalette.ErrorLight,
    onError = AddyPalette.OnErrorLight,
    errorContainer = AddyPalette.ErrorContainerLight,
    onErrorContainer = AddyPalette.OnErrorContainerLight,
    background = AddyPalette.LightBackground,
    onBackground = AddyPalette.LightOnBackground,
    surface = AddyPalette.LightSurface,
    onSurface = AddyPalette.LightOnSurface,
    surfaceVariant = AddyPalette.LightSurfaceVariant,
    onSurfaceVariant = AddyPalette.LightOnSurfaceVariant,
    surfaceContainerLowest = AddyPalette.LightSurfaceLowest,
    surfaceContainerLow = AddyPalette.LightSurfaceLow,
    surfaceContainer = AddyPalette.LightSurfaceContainer,
    surfaceContainerHigh = AddyPalette.LightSurfaceHigh,
    surfaceContainerHighest = AddyPalette.LightSurfaceHighest,
    outline = AddyPalette.LightOutline,
    outlineVariant = AddyPalette.LightOutlineVariant,
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF2EFF7),
    inversePrimary = AddyPalette.PrimaryDark
)

private fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = AddyPalette.AmoledBackground,
    surface = AddyPalette.AmoledSurface,
    surfaceContainerLowest = AddyPalette.AmoledSurfaceLowest,
    surfaceContainerLow = AddyPalette.AmoledSurfaceLow,
    surfaceContainer = AddyPalette.AmoledSurfaceContainer,
    surfaceContainerHigh = AddyPalette.AmoledSurfaceHigh,
    surfaceContainerHighest = AddyPalette.AmoledSurfaceHighest,
    outline = AddyPalette.AmoledOutline,
    outlineVariant = AddyPalette.AmoledOutlineVariant
)

@Composable
fun MailxAddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoledDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AddyDarkColorScheme
        else -> AddyLightColorScheme
    }

    val colorScheme = if (darkTheme && amoledDark) {
        baseScheme.withAmoledSurfaces()
    } else {
        baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
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
