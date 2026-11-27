package net.mailx.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemeContrastUnitTest {

    private fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun verifyAllPairs(
        mode: String,
        scheme: ColorScheme,
        semantic: MailxSemanticColors
    ) {
        val checks = listOf(
            Triple("onSurface/surface", scheme.onSurface to scheme.surface, 4.5),
            Triple("onSurfaceVariant/surface", scheme.onSurfaceVariant to scheme.surface, 4.5),
            Triple("onSurfaceVariant/containerHigh", scheme.onSurfaceVariant to scheme.surfaceContainerHigh, 4.5),
            Triple("primary/surface", scheme.primary to scheme.surface, 4.5),
            Triple("onPrimary/primary", scheme.onPrimary to scheme.primary, 4.5),
            Triple("onPrimaryContainer/primaryContainer", scheme.onPrimaryContainer to scheme.primaryContainer, 4.5),
            Triple("onSecondaryContainer/secondaryContainer", scheme.onSecondaryContainer to scheme.secondaryContainer, 4.5),
            Triple("onTertiaryContainer/tertiaryContainer", scheme.onTertiaryContainer to scheme.tertiaryContainer, 4.5),
            Triple("error/surface", scheme.error to scheme.surface, 4.5),
            Triple("onError/error", scheme.onError to scheme.error, 4.5),
            Triple("onErrorContainer/errorContainer", scheme.onErrorContainer to scheme.errorContainer, 4.5),
            Triple("paused/surface", semantic.paused to scheme.surface, 4.5),
            Triple("onPausedContainer/pausedContainer", semantic.onPausedContainer to semantic.pausedContainer, 4.5),
            Triple("relay/surface", semantic.relay to scheme.surface, 4.5),
            Triple("onRelayContainer/relayContainer", semantic.onRelayContainer to semantic.relayContainer, 4.5),
            Triple("outline/surface", scheme.outline to scheme.surface, 3.0),
            Triple("outline/containerHigh", scheme.outline to scheme.surfaceContainerHigh, 3.0)
        )
        for ((label, pair, target) in checks) {
            val ratio = contrastRatio(pair.first, pair.second)
            assertTrue(
                "$mode $label contrast %.2f is below %.1f".format(ratio, target),
                ratio >= target
            )
        }
    }

    @Test
    fun darkSchemeMeetsAllSeventeenWcagContrastTargets() {
        verifyAllPairs("Dark", graphiteMintDarkColorScheme(), graphiteMintDarkSemanticColors())
    }

    @Test
    fun lightSchemeMeetsAllSeventeenWcagContrastTargets() {
        verifyAllPairs("Light", graphiteMintLightColorScheme(), graphiteMintLightSemanticColors())
    }

    @Test
    fun amoledSchemeMeetsAllSeventeenWcagContrastTargets() {
        verifyAllPairs("AMOLED", graphiteMintAmoledColorScheme(), graphiteMintDarkSemanticColors())
    }

    @Test
    fun typographyMeetsMinimumTwelveSpFloor() {
        val styles = listOf(
            MailxTypography.bodyLarge,
            MailxTypography.bodyMedium,
            MailxTypography.bodySmall,
            MailxTypography.labelLarge,
            MailxTypography.labelMedium,
            MailxTypography.labelSmall,
            MailxAddressStyle
        )
        for (style in styles) {
            assertTrue("Font size ${style.fontSize} below 12sp", style.fontSize.value >= 12f)
        }
    }
}
