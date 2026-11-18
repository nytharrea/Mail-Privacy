package net.mailx.android.ui.theme

import androidx.compose.ui.graphics.Color

object MailxPalette {
    val BrandHeroBg = Color(0xFF0B0E11)
    val BrandHeroSurface = Color(0xFF13181D)
    val BrandAccentTeal = Color(0xFF14B8A6)

    // Desaturated, calm telemetry hues — only critical states (Blocked, Paused) stay vibrant
    val ForwardEmerald = Color(0xFF2E9E83)
    val ReplySky = Color(0xFF5689B5)
    val SentIndigo = Color(0xFF6E78A8)
    val VerifiedGreen = Color(0xFF349B76)
    val BlockedRose = Color(0xFFF43F5E)
    val PausedAmber = Color(0xFFF59E0B)

    val Slate400 = Color(0xFF94A3B8)
    val Slate500 = Color(0xFF64748B)
    val Slate600 = Color(0xFF475569)
    val Slate700 = Color(0xFF334155)

    // Refined, high-contrast Dark Tonal Palette (Sophisticated Teal #14B8A6 on clean neutral slate)
    val PrimaryDark = Color(0xFF14B8A6)
    val OnPrimaryDark = Color(0xFF002C26)
    val PrimaryContainerDark = Color(0xFF0F4C45)
    val OnPrimaryContainerDark = Color(0xFFB2F5EA)

    val SecondaryDark = Color(0xFF94A3B8)
    val OnSecondaryDark = Color(0xFF1E293B)
    val SecondaryContainerDark = Color(0xFF263241)
    val OnSecondaryContainerDark = Color(0xFFE2E8F0)

    val TertiaryDark = Color(0xFF7FA6D6)
    val OnTertiaryDark = Color(0xFF0F2942)
    val TertiaryContainerDark = Color(0xFF1E3A5F)
    val OnTertiaryContainerDark = Color(0xFFD6E6FF)

    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorDark = Color(0xFF690005)
    val ErrorContainerDark = Color(0xFF93000A)
    val OnErrorContainerDark = Color(0xFFFFDAD6)

    val DarkBackground = Color(0xFF0B0E11)
    val DarkOnBackground = Color(0xFFE6EDF3)
    val DarkSurface = Color(0xFF0B0E11)
    val DarkOnSurface = Color(0xFFE6EDF3)
    val DarkSurfaceVariant = Color(0xFF26303A)
    val DarkOnSurfaceVariant = Color(0xFF9BA7B4)
    val DarkOutline = Color(0xFF6E7B88)
    val DarkOutlineVariant = Color(0xFF28323C)

    // Distinct tonal surface hierarchy: Lowest -> Low -> Container -> High -> Highest
    val DarkSurfaceLowest = Color(0xFF07090B)
    val DarkSurfaceLow = Color(0xFF13181D)
    val DarkSurfaceContainer = Color(0xFF1A2128)
    val DarkSurfaceHigh = Color(0xFF232C35)
    val DarkSurfaceHighest = Color(0xFF2D3843)

    // AMOLED Pure Black with WCAG AA/AAA text & clear container separation
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF000000)
    val AmoledOnSurface = Color(0xFFF1F5F9)
    val AmoledOnSurfaceVariant = Color(0xFFBEC8D2)
    val AmoledSurfaceLowest = Color(0xFF000000)
    val AmoledSurfaceLow = Color(0xFF0D1115)
    val AmoledSurfaceContainer = Color(0xFF151B21)
    val AmoledSurfaceHigh = Color(0xFF1E262E)
    val AmoledSurfaceHighest = Color(0xFF27313B)
    val AmoledOutline = Color(0xFF7B8794)
    val AmoledOutlineVariant = Color(0xFF242E38)

    // Clean, trustworthy Light Tonal Palette
    val PrimaryLight = Color(0xFF0D9488)
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val PrimaryContainerLight = Color(0xFFCCFBF1)
    val OnPrimaryContainerLight = Color(0xFF042F2E)

    val SecondaryLight = Color(0xFF475569)
    val OnSecondaryLight = Color(0xFFFFFFFF)
    val SecondaryContainerLight = Color(0xFFE2E8F0)
    val OnSecondaryContainerLight = Color(0xFF0F172A)

    val TertiaryLight = Color(0xFF3B6698)
    val OnTertiaryLight = Color(0xFFFFFFFF)
    val TertiaryContainerLight = Color(0xFFDbeafe)
    val OnTertiaryContainerLight = Color(0xFF0F2942)

    val ErrorLight = Color(0xFFDC2626)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFFEE2E2)
    val OnErrorContainerLight = Color(0xFF450A0A)

    val LightBackground = Color(0xFFF8FAFC)
    val LightOnBackground = Color(0xFF0F172A)
    val LightSurface = Color(0xFFF8FAFC)
    val LightOnSurface = Color(0xFF0F172A)
    val LightSurfaceVariant = Color(0xFFE2E8F0)
    val LightOnSurfaceVariant = Color(0xFF475569)
    val LightOutline = Color(0xFF64748B)
    val LightOutlineVariant = Color(0xFFCBD5E1)

    val LightSurfaceLowest = Color(0xFFFFFFFF)
    val LightSurfaceLow = Color(0xFFF1F5F9)
    val LightSurfaceContainer = Color(0xFFE8EEF5)
    val LightSurfaceHigh = Color(0xFFDEE6EF)
    val LightSurfaceHighest = Color(0xFFD3DFEB)
}
