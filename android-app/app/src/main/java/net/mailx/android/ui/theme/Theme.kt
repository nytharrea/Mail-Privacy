package net.mailx.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AddyPalette.PrimaryDark,
    onPrimary = AddyPalette.OnPrimaryDark,
    primaryContainer = AddyPalette.PrimaryContainerDark,
    onPrimaryContainer = AddyPalette.OnPrimaryContainerDark,
    secondary = AddyPalette.SentCyan,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF183842),
    onSecondaryContainer = Color(0xFFB6EBFF),
    tertiary = AddyPalette.ForwardedOrange,
    onTertiary = Color(0xFF3E2200),
    tertiaryContainer = Color(0xFF4A300A),
    onTertiaryContainer = Color(0xFFFFDDB3),
    error = AddyPalette.BlockedRed,
    onError = Color.White,
    errorContainer = Color(0xFF4F1711),
    onErrorContainer = Color(0xFFFFDAD6),
    background = AddyPalette.DarkBackground,
    onBackground = Color(0xFFECEEF6),
    surface = AddyPalette.DarkSurface,
    onSurface = Color(0xFFECEEF6),
    surfaceVariant = AddyPalette.DarkSurfaceContainer,
    onSurfaceVariant = Color(0xFFB4B8C8),
    surfaceContainerLowest = Color(0xFF0A0C12),
    surfaceContainerLow = AddyPalette.DarkSurfaceLow,
    surfaceContainer = AddyPalette.DarkSurfaceContainer,
    surfaceContainerHigh = AddyPalette.DarkSurfaceHigh,
    surfaceContainerHighest = AddyPalette.DarkSurfaceHighest,
    outline = AddyPalette.DarkOutline,
    outlineVariant = AddyPalette.DarkOutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary = AddyPalette.PrimaryLight,
    onPrimary = AddyPalette.OnPrimaryLight,
    primaryContainer = AddyPalette.PrimaryContainerLight,
    onPrimaryContainer = AddyPalette.OnPrimaryContainerLight,
    secondary = Color(0xFF05677E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB6EBFF),
    onSecondaryContainer = Color(0xFF004E60),
    tertiary = Color(0xFFC77200),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF3E2200),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = AddyPalette.LightBackground,
    onBackground = Color(0xFF191B22),
    surface = AddyPalette.LightSurface,
    onSurface = Color(0xFF191B22),
    surfaceVariant = AddyPalette.LightSurfaceContainer,
    onSurfaceVariant = Color(0xFF454652),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = AddyPalette.LightSurfaceLow,
    surfaceContainer = AddyPalette.LightSurfaceContainer,
    surfaceContainerHigh = AddyPalette.LightSurfaceHigh,
    surfaceContainerHighest = AddyPalette.LightSurfaceHighest,
    outline = AddyPalette.LightOutline,
    outlineVariant = AddyPalette.LightOutlineVariant
)

@Composable
fun MailxAddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MailxTypography,
        content = content
    )
}
