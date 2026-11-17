package net.mailx.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.formatIsoDateShort
import net.mailx.android.ui.theme.AddyPalette

/**
 * `anonaddy/addy-android` içindeki `aliases_recyclerview_list_item.xml` ve `AliasAdapter.kt`
 * bileşeninin birebir Compose karşılığı:
 * - Sol: `DonutProgressView` (`AddyDonutChart`)
 * - Orta: E-posta adresi (16sp, sans-serif-medium) + 2 satır açıklama/tarih (DescTextStyle, alpha 0.7) + etiket rozetleri
 * - Sağ: Kopyala ikonu (`ic_copy` / `ic_check`)
 * - Sağ üst köşe: Sabitlenen (`ic_pinned`) ve Wildcard ikonları (14dp)
 */
@Composable
fun AliasCard(
    alias: UiAlias,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onCopyOrWildcard: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit = {}
) {
    var justCopied by remember { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1400)
            justCopied = false
        }
    }

    val descriptionText = remember(alias) {
        val parts = mutableListOf<String>()
        if (alias.description.isNotBlank()) {
            parts.add(alias.description)
        }
        if (alias.deletedAt.isNotBlank()) {
            parts.add("Silinme: ${formatIsoDateShort(alias.deletedAt)}")
            if (alias.description.isBlank()) {
                parts.add("Oluşturulma: ${formatIsoDateShort(alias.createdAt)}")
            }
        } else {
            parts.add("Oluşturulma: ${formatIsoDateShort(alias.createdAt)}")
            if (alias.description.isBlank()) {
                if (alias.updatedAt.isNotBlank()) {
                    parts.add("Güncellenme: ${formatIsoDateShort(alias.updatedAt)}")
                } else if (alias.recipients.isNotBlank()) {
                    parts.add("Alıcı: ${alias.recipients}")
                }
            }
        }
        parts.joinToString("\n")
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sol: addy-android DonutProgressView
                AddyDonutChart(
                    forwards = alias.forwards,
                    replies = alias.replies,
                    sends = alias.sends,
                    blocks = alias.blocks,
                    enabled = alias.enabled,
                    size = 42.dp,
                    strokeWidth = 6.5.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Orta: E-posta başlığı, 2 satır açıklama/tarih ve etiket rozetleri
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = alias.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp, end = if (alias.pinned || alias.wildcard) 24.dp else 0.dp)
                    )

                    Text(
                        text = descriptionText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Etiket / Durum rozetleri (addy-android layout_label_badge.xml karşılığı)
                    if (!alias.enabled || alias.wildcard || alias.isCustomDomain) {
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!alias.enabled) {
                                AliasLabelBadge(
                                    text = "Devre Dışı",
                                    color = AddyPalette.Grey500
                                )
                            }
                            if (alias.wildcard) {
                                AliasLabelBadge(
                                    text = "Wildcard",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (alias.isCustomDomain) {
                                AliasLabelBadge(
                                    text = alias.domainPart,
                                    color = AddyPalette.EasternBlue
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Sağ: Kopyala ikonu (addy-android aliases_recyclerview_list_copy)
                IconButton(
                    onClick = {
                        if (!alias.wildcard) justCopied = true
                        onCopyOrWildcard()
                    }
                ) {
                    Icon(
                        imageVector = when {
                            justCopied -> Icons.Default.Check
                            alias.wildcard -> Icons.Default.AutoAwesome
                            else -> Icons.Default.ContentCopy
                        },
                        contentDescription = "Takma adı kopyala",
                        tint = if (justCopied) {
                            AddyPalette.Green
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Sağ üst köşe: Sabitlenen ve Wildcard rozet ikonları (14dp, margin 8dp)
            if (alias.pinned || alias.wildcard) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (alias.wildcard) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Wildcard",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (alias.pinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Sabitlendi",
                            tint = AddyPalette.PortalOrange,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AliasLabelBadge(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.18f)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
