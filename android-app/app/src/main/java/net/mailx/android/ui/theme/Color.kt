package net.mailx.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Mailx brand and semantic palette.
 * Keep brand teal for primary actions; reserve status colors for status meaning.
 */
object MailxPalette {
    // Brand: deep teal in light mode, luminous teal in dark mode.
    val BrandPrimary = Color(0xFF0F766E)
    val BrandPrimaryDark = Color(0xFF5EEAD4)
    val BrandPrimaryAccent = Color(0xFF99F6E4)
    val BrandPrimaryDeep = Color(0xFF115E59)
    val BrandPrimaryContainerDark = Color(0xFF123B3A)
    val BrandOnPrimaryContainerDark = Color(0xFFCCFBF1)
    val BrandPrimaryContainerLight = Color(0xFFCCFBF1)
    val BrandOnPrimaryContainerLight = Color(0xFF134E4A)

    // Secondary accent stays in the teal/cyan family, not a competing blue.
    val BrandAccentTeal = Color(0xFF0E7490)
    val BrandAccentTealDark = Color(0xFF67E8F9)
    val BrandSecondaryContainerDark = Color(0xFF12313A)
    val BrandOnSecondaryContainerDark = Color(0xFFCFFAFE)
    val BrandSecondaryContainerLight = Color(0xFFCFFAFE)
    val BrandOnSecondaryContainerLight = Color(0xFF164E63)

    // Neutral tertiary keeps the interface quiet; violet is no longer a brand color.
    val BrandTertiaryNeutral = Color(0xFF475569)
    val BrandTertiaryNeutralDark = Color(0xFFCBD5E1)
    val BrandTertiaryContainerDark = Color(0xFF263340)
    val BrandOnTertiaryContainerDark = Color(0xFFE2E8F0)
    val BrandTertiaryContainerLight = Color(0xFFE2E8F0)
    val BrandOnTertiaryContainerLight = Color(0xFF1E293B)

    // Semantic states: do not reuse the brand accent for success/error/warning.
    val ForwardEmerald = Color(0xFF10B981)
    val ForwardEmeraldSoft = Color(0xFF34D399)
    val ForwardedGreen = Color(0xFF34D399)
    val CryptoCyan = Color(0xFF67E8F9)
    val ReplySky = Color(0xFF7DD3FC)
    val SentSlate = Color(0xFF94A3B8)
    val BlockedRose = Color(0xFFFB7185)
    val PausedAmber = Color(0xFFFBBF24)

    // Obsidian / graphite surfaces.
    val Obsidian950 = Color(0xFF0B1118)
    val Obsidian925 = Color(0xFF101820)
    val Obsidian900 = Color(0xFF151F28)
    val Obsidian850 = Color(0xFF1B2731)
    val Obsidian800 = Color(0xFF23313C)
    val Obsidian750 = Color(0xFF2D3C47)
    val ObsidianBorder = Color(0xFF293842)

    val Pearl50 = Color(0xFFF5F8F8)
    val Pearl100 = Color(0xFFFFFFFF)
    val Pearl150 = Color(0xFFEDF3F2)
    val Pearl200 = Color(0xFFDCE6E4)
    val Pearl300 = Color(0xFFC6D4D1)

    val Slate50 = Color(0xFFF8FAFC)
    val Slate100 = Color(0xFFF1F5F9)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate400 = Color(0xFF94A3B8)
    val Slate500 = Color(0xFF64748B)
    val Slate600 = Color(0xFF475569)
    val Slate700 = Color(0xFF334155)
    val Slate800 = Color(0xFF1E293B)
    val Slate900 = Color(0xFF0F172A)
}
