package net.mailx.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
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
import androidx.compose.ui.graphics.Color
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

@Composable
fun AliasCard(
    alias: UiAlias,
    modifier: Modifier = Modifier,
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

    val borderColor = when {
        alias.pinned -> AddyPalette.PinnedAmber.copy(alpha = 0.55f)
        alias.enabled -> MaterialTheme.colorScheme.outlineVariant
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    val badgeContainerColor = when {
        alias.pinned -> AddyPalette.PinnedAmber.copy(alpha = 0.14f)
        alias.enabled -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val badgeTint = when {
        alias.pinned -> AddyPalette.PinnedAmber
        alias.enabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp)
                .alpha(if (alias.enabled) 1f else 0.62f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Modern ikon rozeti (eski turuncu 0 halkası yerine)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badgeContainerColor,
                border = BorderStroke(1.dp, badgeTint.copy(alpha = 0.28f)),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            alias.pinned -> Icons.Default.PushPin
                            alias.wildcard -> Icons.Default.AutoAwesome
                            else -> Icons.Default.AlternateEmail
                        },
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            append(alias.localPart)
                        }
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            append("@${alias.domainPart}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (alias.enabled) AddyPalette.ActiveGreen else Color(0xFF5C6778))
                    )
                    val subline = when {
                        alias.description.isNotBlank() -> alias.description
                        alias.recipients.isNotBlank() -> alias.recipients
                        else -> if (alias.enabled) "Aktif yönlendirme" else "Duraklatıldı"
                    }
                    Text(
                        text = subline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (alias.totalActivity > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "${alias.forwards}↑ ${alias.blocks}✕",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            val copyContainerColor by animateColorAsState(
                targetValue = if (justCopied) {
                    AddyPalette.ActiveGreen.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                label = "copyContainerColor"
            )
            val copyTint by animateColorAsState(
                targetValue = if (justCopied) {
                    AddyPalette.ActiveGreen
                } else {
                    MaterialTheme.colorScheme.primary
                },
                label = "copyTint"
            )

            Surface(
                onClick = {
                    if (!alias.wildcard) justCopied = true
                    onCopyOrWildcard()
                },
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = copyContainerColor,
                border = BorderStroke(
                    1.dp,
                    if (justCopied) AddyPalette.ActiveGreen.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            justCopied -> Icons.Default.Check
                            alias.wildcard -> Icons.Default.AutoAwesome
                            else -> Icons.Default.ContentCopy
                        },
                        contentDescription = "Kopyala",
                        tint = copyTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = alias.enabled,
                onCheckedChange = onToggleEnabled,
                colors = amoledSwitchColors()
            )
        }
    }
}
