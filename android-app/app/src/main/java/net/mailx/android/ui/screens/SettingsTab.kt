package net.mailx.android.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
        "wildcard" to "Wildcard (*)"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "server_defaults_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
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
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
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
                        Surface(
                            onClick = onSyncDefaults,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Senkronize Et",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                            val isSelected = defaultsState.defaultDomain == dom
                            FilterChip(
                                selected = isSelected,
                                onClick = { onUpdateDefaultDomain(dom) },
                                label = {
                                    Text(
                                        text = "@$dom",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                )
                            )
                        }
                    }

                    Text(
                        text = "Varsayılan Alias Formatı",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        formatOptions.forEach { (fmt, lbl) ->
                            val selected = defaultsState.defaultFormat == fmt
                            Surface(
                                onClick = { onUpdateDefaultFormat(fmt) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lbl,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "verified_recipients_card") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
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
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "appearance_m3_card") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Görünüm & Tema",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                SectionSwitchRow(
                    icon = Icons.Default.Contrast,
                    title = "Saf AMOLED Siyah (#000000)",
                    subtitle = "Tam siyah arka plan ve obsidyen yüzey kontrastı",
                    checked = themeState.amoledDarkEnabled,
                    onCheckedChange = onToggleAmoledDark
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SectionSwitchRow(
                        icon = Icons.Default.Palette,
                        title = "Duvar Kağıdı Renkleri (Material You)",
                        subtitle = "Kapalıyken Siber Turkuaz & Obsidyen paleti kullanılır",
                        checked = themeState.dynamicColorEnabled,
                        onCheckedChange = onToggleDynamicColor
                    )
                }
            }
        }

        item(key = "tools_and_security_actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Araçlar & Güvenlik",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
