package net.mailx.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.theme.AddyPalette

/**
 * Hızlı taranabilir, düşük yoğunluklu kompakt Alias kartı.
 * Detaylar `ManageAliasBottomSheet` içinde yer alır; kart üzerinde yalnızca
 * Donut istatistiği, adres, tek satır açıklama ve birincil kopyala/durum aksiyonu bulunur.
 */
@Composable
fun AliasCard(
    alias: UiAlias,
    onClick: () -> Unit,
    onCopyOrWildcard: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    var justCopied by remember { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1300)
            justCopied = false
        }
    }

    val borderColor = if (alias.pinned) {
        AddyPalette.ForwardedOrange.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .alpha(if (alias.enabled) 1f else 0.62f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AddyDonutChart(
                forwards = alias.forwards,
                replies = alias.replies,
                sends = alias.sends,
                blocks = alias.blocks,
                enabled = alias.enabled,
                size = 40.dp,
                strokeWidth = 4.5.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (alias.pinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Sabit",
                            tint = AddyPalette.ForwardedOrange,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                append(alias.localPart)
                            }
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                append("@${alias.domainPart}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                val subline = when {
                    alias.description.isNotBlank() -> alias.description
                    alias.recipients.isNotBlank() -> "→ ${alias.recipients}"
                    else -> "Açıklama yok"
                }
                Text(
                    text = subline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            val copyTint by animateColorAsState(
                targetValue = if (justCopied) AddyPalette.ActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "copyTint"
            )
            IconButton(
                onClick = {
                    if (!alias.wildcard) justCopied = true
                    onCopyOrWildcard()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = when {
                        justCopied -> Icons.Default.Check
                        alias.wildcard -> Icons.Default.AutoAwesome
                        else -> Icons.Default.ContentCopy
                    },
                    contentDescription = "Kopyala",
                    tint = copyTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Switch(
                checked = alias.enabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.scale(0.78f)
            )
        }
    }
}
