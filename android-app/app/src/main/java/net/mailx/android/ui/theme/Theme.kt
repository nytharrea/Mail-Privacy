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

enum class MailxColorPreset(val id: String, val label: String) {
    GRAPHITE_LIME("graphite_lime", "Graphite Lime"),
    NIGHT_FJORD("night_fjord", "Night Fjord");

    companion object {
        fun fromId(id: String): MailxColorPreset =
            entries.firstOrNull { it.id == id } ?: GRAPHITE_LIME
    }
}

private val GraphiteLimeDarkColorScheme = darkColorScheme(
    primary = MailxPalette.GraphiteLimePrimaryDark,
    onPrimary = MailxPalette.GraphiteLimeOnPrimaryDark,
    primaryContainer = MailxPalette.GraphiteLimePrimaryContainerDark,
    onPrimaryContainer = MailxPalette.GraphiteLimeOnPrimaryContainerDark,
    secondary = MailxPalette.GraphiteLimeSecondaryDark,
    onSecondary = MailxPalette.GraphiteLimeOnSecondaryDark,
    secondaryContainer = MailxPalette.GraphiteLimeSecondaryContainerDark,
    onSecondaryContainer = MailxPalette.GraphiteLimeOnSecondaryContainerDark,
    tertiary = MailxPalette.BrandTertiaryNeutralDark,
    onTertiary = Color(0xFF14121F),
    tertiaryContainer = MailxPalette.BrandTertiaryContainerDark,
    onTertiaryContainer = MailxPalette.BrandOnTertiaryContainerDark,
    background = MailxPalette.Graphite950,
    onBackground = MailxPalette.GraphiteText,
    surface = MailxPalette.Graphite925,
    onSurface = MailxPalette.GraphiteText,
    surfaceVariant = MailxPalette.Graphite800,
    onSurfaceVariant = MailxPalette.GraphiteSubtext,
    surfaceContainerLowest = Color(0xFF090A0C),
    surfaceContainerLow = MailxPalette.Graphite900,
    surfaceContainer = MailxPalette.Graphite850,
    surfaceContainerHigh = MailxPalette.Graphite800,
    surfaceContainerHighest = MailxPalette.Graphite750,
    outline = MailxPalette.GraphiteOutline,
    outlineVariant = MailxPalette.GraphiteBorder,
    error = MailxPalette.BlockedRose,
    onError = Color.White,
    errorContainer = Color(0xFF3B1520),
    onErrorContainer = Color(0xFFFFE4E6)
)

private val GraphiteLimeLightColorScheme = lightColorScheme(
    primary = MailxPalette.GraphiteLimePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = MailxPalette.GraphiteLimePrimaryContainerLight,
    onPrimaryContainer = MailxPalette.GraphiteLimeOnPrimaryContainerLight,
    secondary = MailxPalette.GraphiteLimeSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = MailxPalette.GraphiteLimeSecondaryContainerLight,
    onSecondaryContainer = MailxPalette.GraphiteLimeOnSecondaryContainerLight,
    tertiary = MailxPalette.BrandTertiaryNeutral,
    onTertiary = Color.White,
    tertiaryContainer = MailxPalette.BrandTertiaryContainerLight,
    onTertiaryContainer = MailxPalette.BrandOnTertiaryContainerLight,
    background = MailxPalette.Paper50,
    onBackground = Color(0xFF141618),
    surface = MailxPalette.Paper100,
    onSurface = Color(0xFF141618),
    surfaceVariant = MailxPalette.Paper150,
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceContainerLowest = MailxPalette.Paper150,
    surfaceContainerLow = Color.White,
    surfaceContainer = MailxPalette.Paper150,
    surfaceContainerHigh = MailxPalette.Paper200,
    surfaceContainerHighest = MailxPalette.Paper300,
    outline = Color(0xFF8C929B),
    outlineVariant = MailxPalette.Paper200,
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337)
)

private val NightFjordDarkColorScheme = darkColorScheme(
    primary = MailxPalette.BrandPrimaryDark,
    onPrimary = Color(0xFF062A27),
    primaryContainer = MailxPalette.BrandPrimaryContainerDark,
    onPrimaryContainer = MailxPalette.BrandOnPrimaryContainerDark,
    secondary = MailxPalette.BrandAccentTealDark,
    onSecondary = Color(0xFF062033),
    secondaryContainer = MailxPalette.BrandSecondaryContainerDark,
    onSecondaryContainer = MailxPalette.BrandOnSecondaryContainerDark,
    tertiary = MailxPalette.BrandTertiaryNeutralDark,
    onTertiary = Color(0xFF0F172A),
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

private val NightFjordLightColorScheme = lightColorScheme(
    primary = MailxPalette.BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = MailxPalette.BrandPrimaryContainerLight,
    onPrimaryContainer = MailxPalette.BrandOnPrimaryContainerLight,
    secondary = MailxPalette.BrandAccentTeal,
    onSecondary = Color.White,
    secondaryContainer = MailxPalette.BrandSecondaryContainerLight,
    onSecondaryContainer = MailxPalette.BrandOnSecondaryContainerLight,
    tertiary = MailxPalette.BrandTertiaryNeutral,
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

private fun ColorScheme.withHarmonizedDynamicDark(preset: MailxColorPreset): ColorScheme {
    return when (preset) {
        MailxColorPreset.GRAPHITE_LIME -> copy(
            primary = lerp(primary, MailxPalette.GraphiteLimePrimaryDark, 0.45f),
            onPrimary = MailxPalette.GraphiteLimeOnPrimaryDark,
            primaryContainer = lerp(
                primaryContainer,
                MailxPalette.GraphiteLimePrimaryContainerDark,
                0.55f
            ),
            onPrimaryContainer = MailxPalette.GraphiteLimeOnPrimaryContainerDark,
            background = MailxPalette.Graphite950,
            surface = MailxPalette.Graphite925,
            surfaceVariant = MailxPalette.Graphite800,
            surfaceContainerLowest = Color(0xFF090A0C),
            surfaceContainerLow = MailxPalette.Graphite900,
            surfaceContainer = MailxPalette.Graphite850,
            surfaceContainerHigh = MailxPalette.Graphite800,
            surfaceContainerHighest = MailxPalette.Graphite750,
            outline = MailxPalette.GraphiteOutline,
            outlineVariant = MailxPalette.GraphiteBorder
        )
        MailxColorPreset.NIGHT_FJORD -> copy(
            primary = lerp(primary, MailxPalette.BrandPrimaryDark, 0.45f),
            onPrimary = Color(0xFF062A27),
            primaryContainer = lerp(
                primaryContainer,
                MailxPalette.BrandPrimaryContainerDark,
                0.55f
            ),
            onPrimaryContainer = MailxPalette.BrandOnPrimaryContainerDark,
            secondary = lerp(secondary, MailxPalette.BrandAccentTealDark, 0.45f),
            secondaryContainer = lerp(
                secondaryContainer,
                MailxPalette.BrandSecondaryContainerDark,
                0.55f
            ),
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
}

private fun ColorScheme.withHarmonizedDynamicLight(preset: MailxColorPreset): ColorScheme {
    val targetPrimary = when (preset) {
        MailxColorPreset.GRAPHITE_LIME -> MailxPalette.GraphiteLimePrimaryLight
        MailxColorPreset.NIGHT_FJORD -> MailxPalette.BrandPrimary
    }
    return copy(
        primary = lerp(primary, targetPrimary, 0.45f),
        onPrimary = Color.White,
        background = MailxPalette.Paper50,
        surface = MailxPalette.Paper100,
        surfaceContainerLowest = MailxPalette.Paper150,
        surfaceContainerLow = Color.White,
        surfaceContainer = MailxPalette.Paper150,
        surfaceContainerHigh = MailxPalette.Paper200,
        surfaceContainerHighest = MailxPalette.Paper300,
        outlineVariant = MailxPalette.Paper200
    )
}

private fun ColorScheme.withAmoledPitchBlack(preset: MailxColorPreset): ColorScheme {
    return when (preset) {
        MailxColorPreset.GRAPHITE_LIME -> copy(
            background = Color.Black,
            surface = Color(0xFF08090B),
            surfaceContainerLowest = Color(0xFF050607),
            surfaceContainerLow = Color(0xFF0F1114),
            surfaceContainer = Color(0xFF15181C),
            surfaceContainerHigh = Color(0xFF1C2026),
            surfaceContainerHighest = Color(0xFF242930),
            outlineVariant = Color(0xFF22262E)
        )
        MailxColorPreset.NIGHT_FJORD -> copy(
            background = Color.Black,
            surface = Color(0xFF06080E),
            surfaceContainerLowest = Color(0xFF04060A),
            surfaceContainerLow = Color(0xFF0B0E17),
            surfaceContainer = Color(0xFF111624),
            surfaceContainerHigh = Color(0xFF181F32),
            surfaceContainerHighest = Color(0xFF202942),
            outlineVariant = Color(0xFF1E263D)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MailxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoledDark: Boolean = false,
    expressiveMotion: Boolean = false,
    reducedMotion: Boolean = false,
    colorPreset: String = MailxColorPreset.GRAPHITE_LIME.id,
    content: @Composable () -> Unit
) {
    val preset = MailxColorPreset.fromId(colorPreset)
    val context = LocalContext.current
    val baseScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) {
            dynamicDarkColorScheme(context).withHarmonizedDynamicDark(preset)
        } else {
            dynamicLightColorScheme(context).withHarmonizedDynamicLight(preset)
        }
    } else {
        when (preset) {
            MailxColorPreset.GRAPHITE_LIME ->
                if (darkTheme) GraphiteLimeDarkColorScheme else GraphiteLimeLightColorScheme
            MailxColorPreset.NIGHT_FJORD ->
                if (darkTheme) NightFjordDarkColorScheme else NightFjordLightColorScheme
        }
    }

    val finalScheme = if (darkTheme && amoledDark) {
        baseScheme.withAmoledPitchBlack(preset)
    } else {
        baseScheme
    }

    val motionScheme = if (reducedMotion || !expressiveMotion) {
        MotionScheme.standard()
    } else {
        MotionScheme.expressive()
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
