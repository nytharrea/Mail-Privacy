package net.mailx.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * `anonaddy/addy-android` (`anonaddy_shared/src/main/res/values/colors.xml` ve
 * `values-night/colors.xml`) resmi Material 3 renk paleti.
 */
object AddyPalette {
    // Addy.io Kurumsal Sabit İndigo (Setup ekranı ve marka vurguları)
    val PrimaryColorStatic = Color(0xFF19216C)
    val PrimaryInnerStatic = Color(0xFF2D38A1)
    val LauncherForeground = Color(0xFFDFE0FF)

    // Semantik İstatistik & Durum Renkleri (addy-android colors.xml birebir değerleri)
    val PortalOrange = Color(0xFFFF9800)      // Forwarded (İletilen)
    val PortalBlue = Color(0xFF4289FD)        // Replied (Yanıtlanan)
    val EasternBlue = Color(0xFF16A8B8)       // Sent (Gönderilen)
    val SoftRed = Color(0xFFFD5842)           // Blocked (Engellenen) & Tehlike Aksiyonu
    val HardRed = Color(0xFFFF0000)
    val Green = Color(0xFF8BC34A)             // Aktif / Bilgi

    // Geriye dönük uyumluluk takma adları
    val ForwardedOrange = PortalOrange
    val RepliedBlue = PortalBlue
    val SentCyan = EasternBlue
    val BlockedRed = SoftRed
    val ActiveGreen = Green
    val PinnedAmber = PortalOrange

    // Pasif Donut Halka Grileri (addy-android md_grey_500..800)
    val Grey500 = Color(0xFF9E9E9E)
    val Grey600 = Color(0xFF757575)
    val Grey700 = Color(0xFF616161)
    val Grey800 = Color(0xFF424242)

    // Material 3 Karanlık Tema (values-night/colors.xml)
    val PrimaryDark = Color(0xFFBEC2FF)
    val OnPrimaryDark = Color(0xFF272B60)
    val PrimaryContainerDark = Color(0xFF3D4278)
    val OnPrimaryContainerDark = Color(0xFFE0E0FF)

    val SecondaryDark = Color(0xFFC2C1FF)
    val OnSecondaryDark = Color(0xFF2A2A60)
    val SecondaryContainerDark = Color(0xFF414178)
    val OnSecondaryContainerDark = Color(0xFFE2DFFF)

    val TertiaryDark = Color(0xFF87D1EB)
    val OnTertiaryDark = Color(0xFF003543)
    val TertiaryContainerDark = Color(0xFF004E60)
    val OnTertiaryContainerDark = Color(0xFFB6EBFF)

    val ErrorDark = Color(0xFFFFB4AB)
    val OnErrorDark = Color(0xFF690005)
    val ErrorContainerDark = Color(0xFF93000A)
    val OnErrorContainerDark = Color(0xFFFFDAD6)

    val DarkBackground = Color(0xFF131318)
    val DarkOnBackground = Color(0xFFE4E1E9)
    val DarkSurface = Color(0xFF131318)
    val DarkOnSurface = Color(0xFFE4E1E9)
    val DarkSurfaceVariant = Color(0xFF46464F)
    val DarkOnSurfaceVariant = Color(0xFFC7C5D0)
    val DarkOutline = Color(0xFF91909A)
    val DarkOutlineVariant = Color(0xFF46464F)

    val DarkSurfaceLowest = Color(0xFF0E0E13)
    val DarkSurfaceLow = Color(0xFF1B1B21)
    val DarkSurfaceContainer = Color(0xFF1F1F25)
    val DarkSurfaceHigh = Color(0xFF2A292F)
    val DarkSurfaceHighest = Color(0xFF34343A)

    // Saf Siyah (AMOLED) Yüzey Hiyerarşisi (keskin çerçeve olmadan yumuşak ton ayrımı)
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF000000)
    val AmoledSurfaceLowest = Color(0xFF000000)
    val AmoledSurfaceLow = Color(0xFF121218)
    val AmoledSurfaceContainer = Color(0xFF181820)
    val AmoledSurfaceHigh = Color(0xFF22222C)
    val AmoledSurfaceHighest = Color(0xFF2C2C38)
    val AmoledOutline = Color(0xFF8A8994)
    val AmoledOutlineVariant = Color(0xFF383844)

    // Material 3 Açık Tema (values/colors.xml)
    val PrimaryLight = Color(0xFF555A92)
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val PrimaryContainerLight = Color(0xFFE0E0FF)
    val OnPrimaryContainerLight = Color(0xFF3D4278)

    val SecondaryLight = Color(0xFF595992)
    val OnSecondaryLight = Color(0xFFFFFFFF)
    val SecondaryContainerLight = Color(0xFFE2DFFF)
    val OnSecondaryContainerLight = Color(0xFF414178)

    val TertiaryLight = Color(0xFF05677E)
    val OnTertiaryLight = Color(0xFFFFFFFF)
    val TertiaryContainerLight = Color(0xFFB6EBFF)
    val OnTertiaryContainerLight = Color(0xFF004E60)

    val ErrorLight = Color(0xFFBA1A1A)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFFFDAD6)
    val OnErrorContainerLight = Color(0xFF93000A)

    val LightBackground = Color(0xFFFBF8FF)
    val LightOnBackground = Color(0xFF1B1B21)
    val LightSurface = Color(0xFFFBF8FF)
    val LightOnSurface = Color(0xFF1B1B21)
    val LightSurfaceVariant = Color(0xFFE3E1EC)
    val LightOnSurfaceVariant = Color(0xFF46464F)
    val LightOutline = Color(0xFF777680)
    val LightOutlineVariant = Color(0xFFC7C5D0)

    val LightSurfaceLowest = Color(0xFFFFFFFF)
    val LightSurfaceLow = Color(0xFFF5F2FA)
    val LightSurfaceContainer = Color(0xFFF0ECF4)
    val LightSurfaceHigh = Color(0xFFEAE7EF)
    val LightSurfaceHighest = Color(0xFFE4E1E9)
}
