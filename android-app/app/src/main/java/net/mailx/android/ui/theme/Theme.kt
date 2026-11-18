@file:Suppress("OPT_IN_IS_NOT_ENABLED", "EXPERIMENTAL_IS_NOT_ENABLED", "UNNECESSARY_OPT_IN")

package net.mailx.android.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.LocalAutofillHighlightColor
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalReducedMotion = compositionLocalOf { false }

fun isSystemReducedMotionEnabled(context: Context): Boolean {
    return runCatching {
        val animScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        val transitionScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f
        )
        animScale == 0f || transitionScale == 0f
    }.getOrDefault(false)
}

private val MailxDarkColorScheme = darkColorScheme(
    primary = MailxPalette.PrimaryDark,
    onPrimary = MailxPalette.OnPrimaryDark,
    primaryContainer = MailxPalette.PrimaryContainerDark,
    onPrimaryContainer = MailxPalette.OnPrimaryContainerDark,
    secondary = MailxPalette.SecondaryDark,
    onSecondary = MailxPalette.OnSecondaryDark,
    secondaryContainer = MailxPalette.SecondaryContainerDark,
    onSecondaryContainer = MailxPalette.OnSecondaryContainerDark,
    tertiary = MailxPalette.TertiaryDark,
    onTertiary = MailxPalette.OnTertiaryDark,
    tertiaryContainer = MailxPalette.TertiaryContainerDark,
    onTertiaryContainer = MailxPalette.OnTertiaryContainerDark,
    error = MailxPalette.ErrorDark,
    onError = MailxPalette.OnErrorDark,
    errorContainer = MailxPalette.ErrorContainerDark,
    onErrorContainer = MailxPalette.OnErrorContainerDark,
    background = MailxPalette.DarkBackground,
    onBackground = MailxPalette.DarkOnBackground,
    surface = MailxPalette.DarkSurface,
    onSurface = MailxPalette.DarkOnSurface,
    surfaceVariant = MailxPalette.DarkSurfaceVariant,
    onSurfaceVariant = MailxPalette.DarkOnSurfaceVariant,
    surfaceContainerLowest = MailxPalette.DarkSurfaceLowest,
    surfaceContainerLow = MailxPalette.DarkSurfaceLow,
    surfaceContainer = MailxPalette.DarkSurfaceContainer,
    surfaceContainerHigh = MailxPalette.DarkSurfaceHigh,
    surfaceContainerHighest = MailxPalette.DarkSurfaceHighest,
    outline = MailxPalette.DarkOutline,
    outlineVariant = MailxPalette.DarkOutlineVariant,
    inverseSurface = Color(0xFFE6EDF3),
    inverseOnSurface = Color(0xFF1A2128),
    inversePrimary = MailxPalette.PrimaryLight
)

private val MailxLightColorScheme = lightColorScheme(
    primary = MailxPalette.PrimaryLight,
    onPrimary = MailxPalette.OnPrimaryLight,
    primaryContainer = MailxPalette.PrimaryContainerLight,
    onPrimaryContainer = MailxPalette.OnPrimaryContainerLight,
    secondary = MailxPalette.SecondaryLight,
    onSecondary = MailxPalette.OnSecondaryLight,
    secondaryContainer = MailxPalette.SecondaryContainerLight,
    onSecondaryContainer = MailxPalette.OnSecondaryContainerLight,
    tertiary = MailxPalette.TertiaryLight,
    onTertiary = MailxPalette.OnTertiaryLight,
    tertiaryContainer = MailxPalette.TertiaryContainerLight,
    onTertiaryContainer = MailxPalette.OnTertiaryContainerLight,
    error = MailxPalette.ErrorLight,
    onError = MailxPalette.OnErrorLight,
    errorContainer = MailxPalette.ErrorContainerLight,
    onErrorContainer = MailxPalette.OnErrorContainerLight,
    background = MailxPalette.LightBackground,
    onBackground = MailxPalette.LightOnBackground,
    surface = MailxPalette.LightSurface,
    onSurface = MailxPalette.LightOnSurface,
    surfaceVariant = MailxPalette.LightSurfaceVariant,
    onSurfaceVariant = MailxPalette.LightOnSurfaceVariant,
    surfaceContainerLowest = MailxPalette.LightSurfaceLowest,
    surfaceContainerLow = MailxPalette.LightSurfaceLow,
    surfaceContainer = MailxPalette.LightSurfaceContainer,
    surfaceContainerHigh = MailxPalette.LightSurfaceHigh,
    surfaceContainerHighest = MailxPalette.LightSurfaceHighest,
    outline = MailxPalette.LightOutline,
    outlineVariant = MailxPalette.LightOutlineVariant,
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9),
    inversePrimary = MailxPalette.PrimaryDark
)

/**
 * Harmonizes Android 12+ Material You dynamic wallpaper colors with Mailx Brand Teal.
 * Primary interactive roles (switches, active pills, CTAs, brand accents) remain locked
 * to Mailx Teal so brand identity is never lost to generic system blue, while secondary,
 * tertiary, and surface containers subtly adapt to the user's wallpaper.
 */
private fun ColorScheme.withBrandTealIdentity(darkTheme: Boolean): ColorScheme {
    val brandScheme = if (darkTheme) MailxDarkColorScheme else MailxLightColorScheme
    return copy(
        primary = brandScheme.primary,
        onPrimary = brandScheme.onPrimary,
        primaryContainer = brandScheme.primaryContainer,
        onPrimaryContainer = brandScheme.onPrimaryContainer,
        inversePrimary = brandScheme.inversePrimary,
        error = brandScheme.error,
        onError = brandScheme.onError,
        errorContainer = brandScheme.errorContainer,
        onErrorContainer = brandScheme.onErrorContainer
    )
}

private fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = MailxPalette.AmoledBackground,
    onBackground = MailxPalette.AmoledOnSurface,
    surface = MailxPalette.AmoledSurface,
    onSurface = MailxPalette.AmoledOnSurface,
    onSurfaceVariant = MailxPalette.AmoledOnSurfaceVariant,
    surfaceContainerLowest = MailxPalette.AmoledSurfaceLowest,
    surfaceContainerLow = MailxPalette.AmoledSurfaceLow,
    surfaceContainer = MailxPalette.AmoledSurfaceContainer,
    surfaceContainerHigh = MailxPalette.AmoledSurfaceHigh,
    surfaceContainerHighest = MailxPalette.AmoledSurfaceHighest,
    outline = MailxPalette.AmoledOutline,
    outlineVariant = MailxPalette.AmoledOutlineVariant
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MailxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoledDark: Boolean = false,
    expressiveMotion: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemReducedMotion = remember(context) { isSystemReducedMotionEnabled(context) }
    val effectiveReducedMotion = reducedMotion || systemReducedMotion

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val rawDynamic = if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            rawDynamic.withBrandTealIdentity(darkTheme = darkTheme)
        }
        darkTheme -> MailxDarkColorScheme
        else -> MailxLightColorScheme
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
            val lightBars = !darkTheme && colorScheme.background.luminance() > 0.5f
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    CompositionLocalProvider(
        LocalAutofillHighlightColor provides Color.Transparent,
        LocalReducedMotion provides effectiveReducedMotion
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = if (expressiveMotion && !effectiveReducedMotion) {
                MotionScheme.expressive()
            } else {
                MotionScheme.standard()
            },
            shapes = MailxShapes,
            typography = MailxTypography,
            content = content
        )
    }
}
