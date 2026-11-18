@file:Suppress("OPT_IN_IS_NOT_ENABLED", "EXPERIMENTAL_IS_NOT_ENABLED", "UNNECESSARY_OPT_IN")

package net.mailx.android.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
            delay(1200)
            justCopied = false
        }
    }

    val statusColor = when {
        alias.deletedAt.isNotBlank() -> MailxPalette.BlockedRose
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
        targetValue = if (isSelected) 18.dp else 14.dp,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 160),
        label = "alias_card_selected_corner"
    )

    val cardShape = RoundedCornerShape(cardCorner)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = if (selectionMode) onToggleSelect else onClick),
        shape = cardShape,
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
            }
        ),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                alias.wildcard -> MailxIcons.Sparkles
                                alias.isPaused -> MailxIcons.PauseCircle
                                else -> MailxIcons.AtSign
                            },
                            contentDescription = null,
                            tint = when {
                                alias.deletedAt.isNotBlank() -> MailxPalette.BlockedRose
                                alias.isPaused -> MailxPalette.PausedAmber
                                alias.enabled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                append("@${alias.domainPart}")
                            }
                        },
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (alias.pinned) {
                        Icon(
                            imageVector = MailxIcons.Pin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Text(
                    text = subtitleText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniMetricBadge(
                        label = "↑ ${alias.forwards}",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (alias.blocks > 0) {
                        MiniMetricBadge(
                            label = "⊘ ${alias.blocks}",
                            containerColor = MailxPalette.BlockedRose.copy(alpha = 0.14f),
                            contentColor = MailxPalette.BlockedRose
                        )
                    }
                    if (alias.replies > 0 || alias.sends > 0) {
                        MiniMetricBadge(
                            label = "⇄ ${alias.replies + alias.sends}",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!alias.enabled) {
                        MiniMetricBadge(
                            label = "Disabled",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (alias.isPaused) {
                        MiniMetricBadge(
                            label = "Paused",
                            containerColor = MailxPalette.PausedAmber.copy(alpha = 0.16f),
                            contentColor = MailxPalette.PausedAmber
                        )
                    }
                    if (alias.wildcard) {
                        MiniMetricBadge(
                            label = "Wildcard",
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = {
                    if (!alias.wildcard) justCopied = true
                    onCopyOrWildcard()
                },
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(10.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (justCopied) {
                        MailxPalette.ForwardEmerald.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (justCopied) {
                        MailxPalette.ForwardEmerald
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            ) {
                Icon(
                    imageVector = when {
                        justCopied -> MailxIcons.Check
                        alias.wildcard -> MailxIcons.Sparkles
                        else -> MailxIcons.Copy
                    },
                    contentDescription = "Copy alias",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniMetricBadge(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
