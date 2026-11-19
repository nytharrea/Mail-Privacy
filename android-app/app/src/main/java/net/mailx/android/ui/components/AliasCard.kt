@file:Suppress("OPT_IN_IS_NOT_ENABLED", "EXPERIMENTAL_IS_NOT_ENABLED", "UNNECESSARY_OPT_IN")

package net.mailx.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.graphicsLayer
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
            delay(1400)
            justCopied = false
        }
    }

    val isHighSpamRisk = alias.deletedAt.isBlank() && alias.blocks >= 3 && alias.blocks >= alias.forwards

    val statusColor = when {
        alias.deletedAt.isNotBlank() -> MailxPalette.BlockedRose
        isHighSpamRisk -> MailxPalette.BlockedRose
        alias.isPaused -> MailxPalette.PausedAmber
        alias.enabled -> MailxPalette.ForwardedGreen
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
        targetValue = if (isSelected) 24.dp else 20.dp,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "alias_card_selected_corner"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 160),
        label = "alias_card_bg"
    )
    val copyScale by animateFloatAsState(
        targetValue = if (justCopied && !reducedMotion) 1.10f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "copy_btn_scale"
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
                isSelected -> MaterialTheme.colorScheme.primary
                isHighSpamRisk -> MailxPalette.BlockedRose.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
            }
        ),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 10.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.5.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )

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
                        shape = RoundedCornerShape(50),
                        color = MailxPalette.ReplySky.copy(alpha = 0.13f),
                        border = BorderStroke(
                            width = 0.75.dp,
                            color = MailxPalette.ReplySky.copy(alpha = 0.34f)
                        )
                    ) {
                        Text(
                            text = "@${alias.domainPart}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = MailxPalette.ReplySky,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.5.dp, vertical = 2.dp)
                        )
                    }

                    if (alias.pinned) {
                        Icon(
                            imageVector = MailxIcons.Pin,
                            contentDescription = "Pinned",
                            tint = MailxPalette.PausedAmber,
                            modifier = Modifier.size(13.5.dp)
                        )
                    }
                    if (alias.wildcard) {
                        Icon(
                            imageVector = MailxIcons.Sparkles,
                            contentDescription = "Wildcard",
                            tint = MailxPalette.BrandTertiaryNeutralDark,
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

                Text(
                    text = subtitleText,
                    fontSize = 12.2.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (alias.forwards > 0) {
                            MailxPalette.ForwardedGreen.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ) {
                        Text(
                            text = "${alias.forwards} fwd",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (alias.forwards > 0) {
                                MailxPalette.ForwardedGreen
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                        )
                    }

                    if (alias.blocks > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MailxPalette.BlockedRose.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${alias.blocks} blocked",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MailxPalette.BlockedRose,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                            )
                        }
                    }

                    if (alias.replies > 0 || alias.sends > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MailxPalette.ReplySky.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${alias.replies + alias.sends} sent",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MailxPalette.ReplySky,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                            )
                        }
                    }

                    if (isHighSpamRisk) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MailxPalette.BlockedRose.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "High Spam",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MailxPalette.BlockedRose,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp)
                            )
                        }
                    } else if (!alias.enabled) {
                        Text(
                            text = "• Disabled",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    } else if (alias.isPaused) {
                        Text(
                            text = "• Paused",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MailxPalette.PausedAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            val btnAccent = when {
                justCopied -> MailxPalette.ForwardedGreen
                alias.wildcard -> MailxPalette.BrandTertiaryNeutralDark
                else -> MaterialTheme.colorScheme.primary
            }

            Surface(
                onClick = {
                    if (!alias.wildcard) justCopied = true
                    onCopyOrWildcard()
                },
                shape = CircleShape,
                color = btnAccent.copy(alpha = 0.16f),
                border = BorderStroke(
                    width = 1.dp,
                    color = btnAccent.copy(alpha = 0.38f)
                ),
                modifier = Modifier
                    .size(38.dp)
                    .graphicsLayer {
                        scaleX = copyScale
                        scaleY = copyScale
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = justCopied,
                        transitionSpec = {
                            (fadeIn(tween(140)) + scaleIn(initialScale = 0.75f))
                                .togetherWith(fadeOut(tween(100)) + scaleOut(targetScale = 0.75f))
                        },
                        label = "copy_icon_anim"
                    ) { copied ->
                        Icon(
                            imageVector = when {
                                copied -> MailxIcons.Check
                                alias.wildcard -> MailxIcons.Sparkles
                                else -> MailxIcons.Copy
                            },
                            contentDescription = "Copy alias",
                            tint = btnAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
