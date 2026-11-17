@file:Suppress("OPT_IN_IS_NOT_ENABLED", "EXPERIMENTAL_IS_NOT_ENABLED", "UNNECESSARY_OPT_IN")

package net.mailx.android.ui.components

import androidx.compose.animation.core.animateDpAsState
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
import net.mailx.android.ui.theme.MailxIcons
import net.mailx.android.ui.theme.MailxPalette

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    var justCopied by remember { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1300)
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

    // Material 3 Expressive Shape Morphing on selection/copy state
    val cardCorner by animateDpAsState(
        targetValue = if (isSelected || justCopied) 24.dp else 18.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "alias_card_corner_morph"
    )

    val actionBtnCorner by animateDpAsState(
        targetValue = if (justCopied) 18.dp else 12.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "action_btn_corner_morph"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardCorner))
            .clickable(onClick = if (selectionMode) onToggleSelect else onClick),
        shape = RoundedCornerShape(cardCorner),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                MailxActivityRing(
                    forwards = alias.forwards,
                    replies = alias.replies,
                    sends = alias.sends,
                    blocks = alias.blocks,
                    enabled = alias.enabled,
                    size = 38.dp,
                    strokeWidth = 4.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                append(alias.localPart)
                            }
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
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
                            tint = MailxPalette.PausedAmber,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (alias.wildcard) {
                        Icon(
                            imageVector = MailxIcons.Sparkles,
                            contentDescription = "Wildcard",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (alias.isPaused) {
                        Icon(
                            imageVector = MailxIcons.PauseCircle,
                            contentDescription = "Paused",
                            tint = MailxPalette.PausedAmber,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Text(
                    text = subtitleText,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniMetricBadge(
                        label = "↑${alias.forwards}",
                        color = MailxPalette.ForwardEmerald
                    )
                    if (alias.blocks > 0) {
                        MiniMetricBadge(
                            label = "⊘${alias.blocks}",
                            color = MailxPalette.BlockedRose
                        )
                    }
                    if (alias.replies > 0 || alias.sends > 0) {
                        MiniMetricBadge(
                            label = "⇄${alias.replies + alias.sends}",
                            color = MailxPalette.ReplySky
                        )
                    }
                    if (!alias.enabled) {
                        MiniMetricBadge(
                            label = "Off",
                            color = MailxPalette.Slate500
                        )
                    } else if (alias.isPaused) {
                        MiniMetricBadge(
                            label = "Paused",
                            color = MailxPalette.PausedAmber
                        )
                    }
                    if (alias.wildcard) {
                        MiniMetricBadge(
                            label = "Wildcard",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (alias.isCustomDomain) {
                        MiniMetricBadge(
                            label = "Custom",
                            color = MailxPalette.SentIndigo
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            FilledTonalIconButton(
                onClick = {
                    if (!alias.wildcard) justCopied = true
                    onCopyOrWildcard()
                },
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(actionBtnCorner),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (justCopied) {
                        MailxPalette.ForwardEmerald.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
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
    color: Color
) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}
