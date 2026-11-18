@file:Suppress("OPT_IN_IS_NOT_ENABLED", "EXPERIMENTAL_IS_NOT_ENABLED", "UNNECESSARY_OPT_IN")

package net.mailx.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.formatIsoDateShort
import net.mailx.android.ui.theme.LocalReducedMotion
import net.mailx.android.ui.theme.MailxIcons
import net.mailx.android.ui.theme.MailxPalette

@Composable
fun AliasCard(
    alias: UiAlias,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit = {},
    onCopyOrWildcard: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit = {}
) {
    val reducedMotion = LocalReducedMotion.current
    var justCopied by remember { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1300)
            justCopied = false
        }
    }

    val isHighSpamRisk = alias.deletedAt.isBlank() && alias.blocks >= 3 && alias.blocks >= alias.forwards

    val statusColor = when {
        alias.deletedAt.isNotBlank() -> MailxPalette.BlockedRose
        isHighSpamRisk -> MailxPalette.BlockedRose
        alias.isPaused -> MailxPalette.PausedAmber
        alias.enabled -> MailxPalette.ForwardEmerald
        else -> MailxPalette.Slate500
    }

    val subtitleText = remember(alias) {
        when {
            alias.description.isNotBlank() -> alias.description
            alias.deletedAt.isNotBlank() -> "Deleted ${formatIsoDateShort(alias.deletedAt)}"
            alias.recipients.isNotBlank() -> "→ ${alias.recipients} • ${formatIsoDateShort(alias.createdAt)}"
            else -> "Created ${formatIsoDateShort(alias.createdAt)}"
        }
    }

    val cardCorner by animateDpAsState(
        targetValue = if (isSelected) 22.dp else 18.dp,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 160),
        label = "alias_card_selected_corner"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 160),
        label = "alias_card_bg"
    )

    val cardShape = RoundedCornerShape(cardCorner)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = if (selectionMode) onToggleSelect else onClick),
        shape = cardShape,
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)
                isHighSpamRisk -> MailxPalette.BlockedRose.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            }
        ),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Material 3 Expressive status accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(13.dp))

            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Row 1: Local part + @domain pill + feature icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = alias.localPart,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                        border = BorderStroke(
                            width = 0.75.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
                        )
                    ) {
                        Text(
                            text = "@${alias.domainPart}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }

                    if (alias.pinned) {
                        Icon(
                            imageVector = MailxIcons.Pin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.5.dp)
                        )
                    }
                    if (alias.wildcard) {
                        Icon(
                            imageVector = MailxIcons.Sparkles,
                            contentDescription = "Wildcard",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(13.5.dp)
                        )
                    }
                    if (alias.isPaused) {
                        Icon(
                            imageVector = MailxIcons.PauseCircle,
                            contentDescription = "Paused",
                            tint = MailxPalette.PausedAmber,
                            modifier = Modifier.size(13.5.dp)
                        )
                    }
                }

                // Row 2: Description / routing subtitle
                Text(
                    text = subtitleText,
                    fontSize = 12.2.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Row 3: Telemetry counters + High-Spam Radar badge
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (alias.enabled) MailxPalette.ForwardEmerald else MailxPalette.Slate500
                                )
                        )
                        Text(
                            text = "${alias.forwards} fwd",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (alias.blocks > 0) {
                        Text(
                            text = "• ${alias.blocks} blk",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MailxPalette.BlockedRose
                        )
                    }

                    if (alias.replies > 0 || alias.sends > 0) {
                        Text(
                            text = "• ${alias.replies + alias.sends} out",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MailxPalette.ReplySky
                        )
                    }

                    if (isHighSpamRisk) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MailxPalette.BlockedRose.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "High Spam",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MailxPalette.BlockedRose,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    } else if (!alias.enabled) {
                        Text(
                            text = "• Disabled",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    } else if (alias.isPaused) {
                        Text(
                            text = "• Paused",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MailxPalette.PausedAmber
                        )
                    }
                }

                // Subtle micro-bar when alias has activity
                if (alias.totalActivity > 0) {
                    val total = alias.totalActivity.coerceAtLeast(1).toFloat()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .padding(top = 1.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                    ) {
                        if (alias.forwards > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(alias.forwards / total)
                                    .fillMaxHeight()
                                    .background(MailxPalette.ForwardEmerald)
                            )
                        }
                        if (alias.replies > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(alias.replies / total)
                                    .fillMaxHeight()
                                    .background(MailxPalette.ReplySky)
                            )
                        }
                        if (alias.sends > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(alias.sends / total)
                                    .fillMaxHeight()
                                    .background(MailxPalette.SentIndigo)
                            )
                        }
                        if (alias.blocks > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(alias.blocks / total)
                                    .fillMaxHeight()
                                    .background(MailxPalette.BlockedRose)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Material 3 Expressive Copy / Wildcard Tonal Button
            Surface(
                onClick = {
                    if (!alias.wildcard) justCopied = true
                    onCopyOrWildcard()
                },
                shape = RoundedCornerShape(12.dp),
                color = if (justCopied) {
                    MailxPalette.ForwardEmerald.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f)
                },
                border = BorderStroke(
                    width = 0.75.dp,
                    color = if (justCopied) {
                        MailxPalette.ForwardEmerald.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                    }
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            justCopied -> MailxIcons.Check
                            alias.wildcard -> MailxIcons.Sparkles
                            else -> MailxIcons.Copy
                        },
                        contentDescription = "Copy alias",
                        tint = when {
                            justCopied -> MailxPalette.ForwardEmerald
                            alias.wildcard -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
