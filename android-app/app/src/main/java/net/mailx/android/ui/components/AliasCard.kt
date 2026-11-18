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
        targetValue = if (isSelected) 18.dp else 15.dp,
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
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            }
        ),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left status accent bar
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(14.dp))

            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 10.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.5.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
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
                        fontSize = 14.5.sp,
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
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "↑ ${alias.forwards} forwarded",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    )
                    if (alias.blocks > 0) {
                        Text(
                            text = "· ⊘ ${alias.blocks} blocked",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MailxPalette.BlockedRose
                        )
                    }
                    if (alias.replies > 0 || alias.sends > 0) {
                        Text(
                            text = "· ⇄ ${alias.replies + alias.sends} sent",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    }
                    if (!alias.enabled) {
                        Text(
                            text = "· Disabled",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    } else if (alias.isPaused) {
                        Text(
                            text = "· Paused",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MailxPalette.PausedAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = {
                    if (!alias.wildcard) justCopied = true
                    onCopyOrWildcard()
                },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = when {
                        justCopied -> MailxIcons.Check
                        alias.wildcard -> MailxIcons.Sparkles
                        else -> MailxIcons.Copy
                    },
                    contentDescription = "Copy alias",
                    tint = if (justCopied) {
                        MailxPalette.ForwardEmerald
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
