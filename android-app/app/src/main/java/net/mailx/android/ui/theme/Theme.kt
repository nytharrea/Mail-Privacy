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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val LocalReducedMotion = compositionLocalOf { false }

@Immutable
data class MailxSemanticColors(
    val paused: Color,
    val onPaused: Color,
    val pausedContainer: Color,
    val onPausedContainer: Color,
    val relay: Color,
    val onRelay: Color,
    val relayContainer: Color,
    val onRelayContainer: Color
)

internal fun graphiteMintDarkSemanticColors(): MailxSemanticColors = MailxSemanticColors(
    paused = MailxPalette.MintPausedDark,
    onPaused = MailxPalette.MintOnPausedDark,
    pausedContainer = MailxPalette.MintPausedContainerDark,
    onPausedContainer = MailxPalette.MintOnPausedContainerDark,
    relay = MailxPalette.MintRelayDark,
    onRelay = MailxPalette.MintOnRelayDark,
    relayContainer = MailxPalette.MintRelayContainerDark,
    onRelayContainer = MailxPalette.MintOnRelayContainerDark
)

internal fun graphiteMintLightSemanticColors(): MailxSemanticColors = MailxSemanticColors(
    paused = MailxPalette.MintPausedLight,
    onPaused = MailxPalette.MintOnPausedLight,
    pausedContainer = MailxPalette.MintPausedContainerLight,
    onPausedContainer = MailxPalette.MintOnPausedContainerLight,
    relay = MailxPalette.MintRelayLight,
    onRelay = MailxPalette.MintOnRelayLight,
    relayContainer = MailxPalette.MintRelayContainerLight,
    onRelayContainer = MailxPalette.MintOnRelayContainerLight
)

val LocalMailxSemanticColors = staticCompositionLocalOf { graphiteMintDarkSemanticColors() }

internal fun graphiteMintDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = MailxPalette.MintPrimaryDark,
    onPrimary = MailxPalette.MintOnPrimaryDark,
    primaryContainer = MailxPalette.MintPrimaryContainerDark,
    onPrimaryContainer = MailxPalette.MintOnPrimaryContainerDark,
    secondary = MailxPalette.MintSecondaryDark,
    onSecondary = MailxPalette.MintOnSecondaryDark,
    secondaryContainer = MailxPalette.MintSecondaryContainerDark,
    onSecondaryContainer = MailxPalette.MintOnSecondaryContainerDark,
    tertiary = MailxPalette.MintTertiaryDark,
    onTertiary = MailxPalette.MintOnTertiaryDark,
    tertiaryContainer = MailxPalette.MintTertiaryContainerDark,
    onTertiaryContainer = MailxPalette.MintOnTertiaryContainerDark,
    background = MailxPalette.MintSurfaceDark,
    onBackground = MailxPalette.MintOnSurfaceDark,
    surface = MailxPalette.MintSurfaceDark,
    onSurface = MailxPalette.MintOnSurfaceDark,
    surfaceVariant = MailxPalette.MintContainerHighDark,
    onSurfaceVariant = MailxPalette.MintOnSurfaceVariantDark,
    surfaceContainerLowest = MailxPalette.MintContainerLowestDark,
    surfaceContainerLow = MailxPalette.MintContainerLowDark,
    surfaceContainer = MailxPalette.MintContainerDark,
    surfaceContainerHigh = MailxPalette.MintContainerHighDark,
    surfaceContainerHighest = MailxPalette.MintContainerHighestDark,
    inverseSurface = MailxPalette.MintInverseSurfaceDark,
    inverseOnSurface = MailxPalette.MintInverseOnSurfaceDark,
    inversePrimary = MailxPalette.MintPrimaryLight,
    outline = MailxPalette.MintOutlineDark,
    outlineVariant = MailxPalette.MintOutlineVariantDark,
    error = MailxPalette.MintErrorDark,
    onError = MailxPalette.MintOnErrorDark,
    errorContainer = MailxPalette.MintErrorContainerDark,
    onErrorContainer = MailxPalette.MintOnErrorContainerDark
)

internal fun graphiteMintLightColorScheme(): ColorScheme = lightColorScheme(
    primary = MailxPalette.MintPrimaryLight,
    onPrimary = MailxPalette.MintOnPrimaryLight,
    primaryContainer = MailxPalette.MintPrimaryContainerLight,
    onPrimaryContainer = MailxPalette.MintOnPrimaryContainerLight,
    secondary = MailxPalette.MintSecondaryLight,
    onSecondary = MailxPalette.MintOnSecondaryLight,
    secondaryContainer = MailxPalette.MintSecondaryContainerLight,
    onSecondaryContainer = MailxPalette.MintOnSecondaryContainerLight,
    tertiary = MailxPalette.MintTertiaryLight,
    onTertiary = MailxPalette.MintOnTertiaryLight,
    tertiaryContainer = MailxPalette.MintTertiaryContainerLight,
    onTertiaryContainer = MailxPalette.MintOnTertiaryContainerLight,
    background = MailxPalette.MintSurfaceLight,
    onBackground = MailxPalette.MintOnSurfaceLight,
    surface = MailxPalette.MintSurfaceLight,
    onSurface = MailxPalette.MintOnSurfaceLight,
    surfaceVariant = MailxPalette.MintContainerHighLight,
    onSurfaceVariant = MailxPalette.MintOnSurfaceVariantLight,
    surfaceContainerLowest = MailxPalette.MintContainerLowestLight,
    surfaceContainerLow = MailxPalette.MintContainerLowLight,
    surfaceContainer = MailxPalette.MintContainerLight,
    surfaceContainerHigh = MailxPalette.MintContainerHighLight,
    surfaceContainerHighest = MailxPalette.MintContainerHighestLight,
    inverseSurface = MailxPalette.MintInverseSurfaceLight,
    inverseOnSurface = MailxPalette.MintInverseOnSurfaceLight,
    inversePrimary = MailxPalette.MintPrimaryDark,
    outline = MailxPalette.MintOutlineLight,
    outlineVariant = MailxPalette.MintOutlineVariantLight,
    error = MailxPalette.MintErrorLight,
    onError = MailxPalette.MintOnErrorLight,
    errorContainer = MailxPalette.MintErrorContainerLight,
    onErrorContainer = MailxPalette.MintOnErrorContainerLight
)

internal fun graphiteMintAmoledColorScheme(
    base: ColorScheme = graphiteMintDarkColorScheme()
): ColorScheme = base.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0E0E),
    surfaceContainer = Color(0xFF121616),
    surfaceContainerHigh = Color(0xFF1B2020),
    surfaceContainerHighest = Color(0xFF242A2A),
    surfaceVariant = Color(0xFF1B2020),
    outlineVariant = Color(0xFF2B3332)
)

private fun ColorScheme.withFixedSemanticError(darkTheme: Boolean): ColorScheme = if (darkTheme) {
    copy(
        error = MailxPalette.MintErrorDark,
        onError = MailxPalette.MintOnErrorDark,
        errorContainer = MailxPalette.MintErrorContainerDark,
        onErrorContainer = MailxPalette.MintOnErrorContainerDark
    )
} else {
    copy(
        error = MailxPalette.MintErrorLight,
        onError = MailxPalette.MintOnErrorLight,
        errorContainer = MailxPalette.MintErrorContainerLight,
        onErrorContainer = MailxPalette.MintOnErrorContainerLight
    )
}

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
    val baseScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        dynamic.withFixedSemanticError(darkTheme)
    } else {
        if (darkTheme) graphiteMintDarkColorScheme() else graphiteMintLightColorScheme()
    }

    val finalScheme = if (darkTheme && amoledDark) {
        graphiteMintAmoledColorScheme(baseScheme)
    } else {
        baseScheme
    }

    val semanticColors = if (darkTheme) {
        graphiteMintDarkSemanticColors()
    } else {
        graphiteMintLightSemanticColors()
    }

    val motionScheme = if (reducedMotion || !expressiveMotion) {
        MotionScheme.standard()
    } else {
        MotionScheme.expressive()
    }

    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalMailxSemanticColors provides semanticColors
    ) {
        MaterialExpressiveTheme(
            colorScheme = finalScheme,
            typography = MailxTypography,
            shapes = MailxShapes,
            motionScheme = motionScheme,
            content = content
        )
    }
}
