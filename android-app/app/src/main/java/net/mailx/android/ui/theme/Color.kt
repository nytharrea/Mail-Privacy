package net.mailx.android.ui.theme

import androidx.compose.ui.graphics.Color

object AddyPalette {
    // Siber Turkuaz birincil vurgu paleti (Saf AMOLED için yüksek kontrast)
    val PrimaryDark = Color(0xFF00E5FF)
    val OnPrimaryDark = Color(0xFF00262B)
    val PrimaryContainerDark = Color(0xFF002B33)
    val OnPrimaryContainerDark = Color(0xFF84F2FF)

    val SecondaryDark = Color(0xFF00B8D4)
    val OnSecondaryDark = Color(0xFF002026)
    val SecondaryContainerDark = Color(0xFF0A2128)
    val OnSecondaryContainerDark = Color(0xFFB5F4FF)

    val PrimaryLight = Color(0xFF006876)
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val PrimaryContainerLight = Color(0xFFA6EEFF)
    val OnPrimaryContainerLight = Color(0xFF001F25)

    // Semantik durum ve trafik renkleri (Saf AMOLED üzerinde canlı ve net)
    val ForwardedOrange = Color(0xFF00E5FF)   // İletilen: Siber Turkuaz
    val RepliedBlue = Color(0xFF38BDF8)       // Yanıtlanan: Gökyüzü Mavisi
    val SentCyan = Color(0xFF00E676)          // Gönderilen: Zümrüt Yeşili
    val BlockedRed = Color(0xFFFF2E43)        // Engellenen / Tehlike: Türk Kırmızısı / Kızıl
    val ActiveGreen = Color(0xFF00E676)       // Aktif durum: Zümrüt Yeşili
    val PinnedAmber = Color(0xFFFFB300)       // Sabitlenen: Kehribar Altın

    // Saf AMOLED Siyah (#000000) ve Nötr Obsidyen Karbon yüzey hiyerarşisi
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF000000)
    val AmoledSurfaceLow = Color(0xFF07080A)
    val AmoledSurfaceContainer = Color(0xFF0B0D10)
    val AmoledSurfaceHigh = Color(0xFF11151A)
    val AmoledSurfaceHighest = Color(0xFF181D24)
    val AmoledOutline = Color(0xFF2C3542)
    val AmoledOutlineVariant = Color(0xFF1A2029)

    // Koyu Karbon tema (AMOLED kapalıysa bile lacivert olmayan derin karbon)
    val DarkBackground = Color(0xFF050608)
    val DarkSurface = Color(0xFF080A0D)
    val DarkSurfaceLow = Color(0xFF0B0E12)
    val DarkSurfaceContainer = Color(0xFF0F1318)
    val DarkSurfaceHigh = Color(0xFF151A21)
    val DarkSurfaceHighest = Color(0xFF1C222B)
    val DarkOutline = Color(0xFF303947)
    val DarkOutlineVariant = Color(0xFF1E242E)

    // Metin renkleri
    val TextPrimary = Color(0xFFF4F6F8)
    val TextSecondary = Color(0xFF8A96A8)
    val TextMuted = Color(0xFF5C6778)

    // Açık tema Material 3 surfaceContainer hiyerarşisi
    val LightBackground = Color(0xFFF7F9FC)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceLow = Color(0xFFF1F4F8)
    val LightSurfaceContainer = Color(0xFFEBEEF3)
    val LightSurfaceHigh = Color(0xFFE4E8EE)
    val LightSurfaceHighest = Color(0xFFDDE2E9)
    val LightOutline = Color(0xFF727880)
    val LightOutlineVariant = Color(0xFFC2C7CF)
}
