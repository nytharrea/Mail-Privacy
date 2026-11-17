package net.mailx.android.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mailx.android.ui.components.SectionActionRow
import net.mailx.android.ui.components.SectionSwitchRow
import net.mailx.android.ui.model.DefaultsUiState
import net.mailx.android.ui.model.ThemeUiState
import net.mailx.android.ui.theme.AddyPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    baseUrl: String,
    defaultsState: DefaultsUiState,
    themeState: ThemeUiState,
    onUpdateDefaultDomain: (String) -> Unit,
    onUpdateDefaultFormat: (String) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleAmoledDark: (Boolean) -> Unit,
    onSyncDefaults: () -> Unit,
    onOpenReverseAliasTool: () -> Unit,
    onRequestLogout: () -> Unit
) {
    val formatOptions = listOf(
        "words" to "Kelime",
        "random" to "Rastgele",
        "uuid" to "UUID",
        "wildcard" to "Wildcard"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "server_defaults_card") {
            OutlinedCard(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sunucu & Varsayılanlar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        FilledTonalIconButton(onClick = onSyncDefaults) {
                            Icon(Icons.Default.Sync, contentDescription = "Senkronize Et")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "Varsayılan Alan Adı",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        defaultsState.availableDomains.forEach { dom ->
                            FilterChip(
                                selected = defaultsState.defaultDomain == dom,
                                onClick = { onUpdateDefaultDomain(dom) },
                                label = { Text("@$dom") },
                                shape = MaterialTheme.shapes.small
                            )
                        }
                    }

                    Text(
                        text = "Varsayılan Alias Formatı",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        formatOptions.forEachIndexed { index, (fmt, lbl) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = formatOptions.size
                                ),
                                onClick = { onUpdateDefaultFormat(fmt) },
                                selected = defaultsState.defaultFormat == fmt,
                                label = {
                                    Text(
                                        text = lbl,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        item(key = "appearance_m3_card") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Görünüm & Material 3",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SectionSwitchRow(
                    icon = Icons.Default.Contrast,
                    title = "Saf AMOLED Siyah Karanlık Mod",
                    subtitle = "Koyu temada #000000 arka plan ve derin yüzey kontrastı kullanır",
                    checked = themeState.amoledDarkEnabled,
                    onCheckedChange = onToggleAmoledDark
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SectionSwitchRow(
                        icon = Icons.Default.Palette,
                        title = "Material You Dinamik Renkler",
                        subtitle = "Duvar kağıdınızdan üretilen Android 12+ renk paletini uygular",
                        checked = themeState.dynamicColorEnabled,
                        onCheckedChange = onToggleDynamicColor
                    )
                }
            }
        }

        item(key = "verified_recipients_card") {
            OutlinedCard(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Doğrulanmış Alıcılar",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (defaultsState.availableRecipients.isEmpty()) {
                        Text(
                            text = "Kayıtlı alıcı bulunamadı.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        defaultsState.availableRecipients.forEach { rec ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MarkEmailRead,
                                    contentDescription = null,
                                    tint = AddyPalette.ActiveGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = rec,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "tools_and_security_actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionActionRow(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = "Ters-Alias (Send from Alias)",
                    subtitle = "Takma adınız üzerinden gerçek e-postanızı gizleyerek ileti gönderin",
                    trailingIcon = Icons.Default.ChevronRight,
                    onClick = onOpenReverseAliasTool
                )
                SectionActionRow(
                    icon = Icons.Default.Shield,
                    title = "Takipçi Engelleme (Block Trackers)",
                    subtitle = if (defaultsState.blockTrackers) "Sunucuda aktif" else "Sunucu varsayılanı: Kapalı"
                )
                SectionActionRow(
                    icon = Icons.Default.Security,
                    title = "Başlık Temizleme (Remove Header)",
                    subtitle = if (defaultsState.removeHeader) "Sunucuda aktif" else "Sunucu varsayılanı: Kapalı"
                )
                SectionActionRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "Güvenli Çıkış Yap",
                    subtitle = "Cihazdaki şifreli oturum anahtarını ve önbelleği temizler",
                    accentColor = AddyPalette.BlockedRed,
                    onClick = onRequestLogout
                )
            }
        }
    }
}
