package net.mailx.android.ui.screens

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
import androidx.compose.material.icons.filled.MarkEmailRead
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
import net.mailx.android.ui.model.DefaultsUiState
import net.mailx.android.ui.theme.AddyPalette

@Composable
fun SettingsTab(
    baseUrl: String,
    defaultsState: DefaultsUiState,
    onUpdateDefaultDomain: (String) -> Unit,
    onUpdateDefaultFormat: (String) -> Unit,
    onSyncDefaults: () -> Unit,
    onOpenReverseAliasTool: () -> Unit,
    onRequestLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
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
                                label = { Text("@$dom") }
                            )
                        }
                    }

                    Text(
                        text = "Varsayılan Alias Formatı",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "words" to "Kelime",
                            "random" to "Rastgele",
                            "uuid" to "UUID",
                            "wildcard" to "Wildcard"
                        ).forEach { (fmt, lbl) ->
                            FilterChip(
                                selected = defaultsState.defaultFormat == fmt,
                                onClick = { onUpdateDefaultFormat(fmt) },
                                label = { Text(lbl) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
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

        item {
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
