package net.mailx.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Mailx "Night Fjord" design system.
 * Brand accents are reserved for actions; semantic colors communicate state.
 */
object MailxPalette {
    // Brand: sea-glass mint with a deep teal light-theme counterpart.
    val BrandPrimary = Color(0xFF087F73)
    val BrandPrimaryDark = Color(0xFF63E6CB)
    val BrandPrimaryAccent = Color(0xFF9AF5E2)
    val BrandPrimaryDeep = Color(0xFF075B55)
    val BrandPrimaryContainerDark = Color(0xFF123B3A)
    val BrandOnPrimaryContainerDark = Color(0xFFD0FFF4)
    val BrandPrimaryContainerLight = Color(0xFFD3F8EF)
    val BrandOnPrimaryContainerLight = Color(0xFF064E49)

    // Cool blue is a secondary interaction color, not a second primary.
    val BrandAccentTeal = Color(0xFF176F91)
    val BrandAccentTealDark = Color(0xFF78D7F6)
    val BrandSecondaryContainerDark = Color(0xFF163543)
    val BrandOnSecondaryContainerDark = Color(0xFFD5F5FF)
    val BrandSecondaryContainerLight = Color(0xFFDDF4FC)
    val BrandOnSecondaryContainerLight = Color(0xFF104A62)

    // Restrained lilac is reserved for specialist tools and neutral emphasis.
    val BrandTertiaryNeutral = Color(0xFF67558E)
    val BrandTertiaryNeutralDark = Color(0xFFC6B4FF)
    val BrandTertiaryContainerDark = Color(0xFF302A45)
    val BrandOnTertiaryContainerDark = Color(0xFFECE5FF)
    val BrandTertiaryContainerLight = Color(0xFFEDE7FF)
    val BrandOnTertiaryContainerLight = Color(0xFF3F3267)

    // Semantic states.
    val ForwardEmerald = Color(0xFF10B981)
    val ForwardEmeraldSoft = Color(0xFF34D399)
    val ForwardedGreen = Color(0xFF42D6A0)
    val CryptoCyan = Color(0xFF78D7F6)
    val ReplySky = Color(0xFF78CFF4)
    val SentSlate = Color(0xFF9BAFC0)
    val BlockedRose = Color(0xFFFF718B)
    val PausedAmber = Color(0xFFF6C66A)

    // Dark surfaces: cool graphite with enough separation for layered cards.
    val Obsidian950 = Color(0xFF080E15)
    val Obsidian925 = Color(0xFF0D151E)
    val Obsidian900 = Color(0xFF121D28)
    val Obsidian850 = Color(0xFF172431)
    val Obsidian800 = Color(0xFF20303D)
    val Obsidian750 = Color(0xFF2A3D4B)
    val ObsidianBorder = Color(0xFF263845)

    val Pearl50 = Color(0xFFF3F7F8)
    val Pearl100 = Color(0xFFFFFFFF)
    val Pearl150 = Color(0xFFEBF2F3)
    val Pearl200 = Color(0xFFDCE7E8)
    val Pearl300 = Color(0xFFC7D7D9)

    val Slate50 = Color(0xFFF8FAFC)
    val Slate100 = Color(0xFFF1F5F9)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate400 = Color(0xFF9AAAB8)
    val Slate500 = Color(0xFF6D7E8D)
    val Slate600 = Color(0xFF536675)
    val Slate700 = Color(0xFF3B4C59)
    val Slate800 = Color(0xFF243440)
    val Slate900 = Color(0xFF101B25)
}
