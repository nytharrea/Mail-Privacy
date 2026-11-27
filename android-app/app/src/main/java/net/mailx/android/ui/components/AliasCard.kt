@file:Suppress("OPT_IN_IS_NOT_ENABLED", "EXPERIMENTAL_IS_NOT_ENABLED", "UNNECESSARY_OPT_IN")

package net.mailx.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.mailx.android.core.uniffi.AliasChip
import net.mailx.android.core.uniffi.AliasKind
import net.mailx.android.core.uniffi.AliasRow
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.theme.LocalMailxSemanticColors
import net.mailx.android.ui.theme.LocalReducedMotion
import net.mailx.android.ui.theme.MailxAddressStyle
import net.mailx.android.ui.theme.MailxIcons

private val DarkAvatarTones = listOf(
    Color(0xFF223A28) to Color(0xFFC7E4CD),
    Color(0xFF133B34) to Color(0xFFB4EFE3),
    Color(0xFF183842) to Color(0xFFBBE4F2),
    Color(0xFF22344A) to Color(0xFFC6DDF7),
    Color(0xFF302E4A) to Color(0xFFDAD6F7),
    Color(0xFF3C2B46) to Color(0xFFEBD2F7),
    Color(0xFF293836) to Color(0xFFC8E0DD),
    Color(0xFF2B3540) to Color(0xFFCCD9E8)
)

private val LightAvatarTones = listOf(
    Color(0xFFCDEAD3) to Color(0xFF2F4835),
    Color(0xFFC0EBE1) to Color(0xFF224941),
    Color(0xFFC7E7F2) to Color(0xFF21434F),
    Color(0xFFD1E2F6) to Color(0xFF263B54),
    Color(0xFFDFDCF6) to Color(0xFF343154),
    Color(0xFFECD7F6) to Color(0xFF422F4D),
    Color(0xFFD0E3E0) to Color(0xFF2B3F3C),
    Color(0xFFD5E0EC) to Color(0xFF2F3B47)
)

private data class AliasChipVisual(
    val icon: ImageVector,
    val label: String,
    val container: Color,
    val content: Color
)

@Composable
private fun resolveChipVisual(chip: AliasChip): AliasChipVisual {
    val scheme = MaterialTheme.colorScheme
    val semantic = LocalMailxSemanticColors.current
    return when (chip) {
        is AliasChip.HighSpam -> AliasChipVisual(
            icon = MailxIcons.Alert,
            label = "High spam",
            container = scheme.errorContainer,
            content = scheme.onErrorContainer
        )
        is AliasChip.Paused -> AliasChipVisual(
            icon = MailxIcons.Clock,
            label = if (chip.daysLeft == 0u) "Paused" else "Paused · ${chip.daysLeft}d",
            container = semantic.pausedContainer,
            content = semantic.onPausedContainer
        )
        is AliasChip.Disabled -> AliasChipVisual(
            icon = MailxIcons.EyeOff,
            label = "Disabled",
            container = scheme.surfaceContainerHigh,
            content = scheme.onSurfaceVariant
        )
        is AliasChip.Wildcard -> AliasChipVisual(
            icon = MailxIcons.Wildcard,
            label = "Wildcard",
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer
        )
    }
}

@Composable
fun AliasCard(
    alias: UiAlias,
    modifier: Modifier = Modifier,
    aliasRow: AliasRow? = null,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit = {},
    onCopyOrWildcard: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit = {}
) {
    val reducedMotion = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    var justCopied by remember { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1400)
            justCopied = false
        }
    }

    val isWildcard = aliasRow?.kind == AliasKind.WILDCARD || alias.wildcard
    val localPart = aliasRow?.local?.ifEmpty { alias.localPart } ?: alias.localPart
    val domainPart = aliasRow?.domain?.ifEmpty { alias.domainPart } ?: alias.domainPart
    val description = aliasRow?.description ?: alias.description
    val forwards = aliasRow?.forwards?.toInt() ?: alias.forwards
    val blocks = aliasRow?.blocks?.toInt() ?: alias.blocks
    val outbound = if (aliasRow != null) {
        (aliasRow.replies + aliasRow.sends).toInt()
    } else {
        alias.replies + alias.sends
    }
    val tintIndex = ((aliasRow?.tint?.toInt() ?: 0) and 7)
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val avatarPair = if (isDarkSurface) DarkAvatarTones[tintIndex] else LightAvatarTones[tintIndex]
    val avatarChar = if (isWildcard) {
        "*"
    } else {
        localPart.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "@"
    }

    val rowBg by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 150),
        label = "alias_row_bg"
    )

    val chip = aliasRow?.chip
    val chipVisual = chip?.let { resolveChipVisual(it) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(rowBg)
            .clickable(onClick = if (selectionMode) onToggleSelect else onClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.padding(end = 8.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatarPair.first),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = avatarChar,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = avatarPair.second
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    ) {
                        append(localPart)
                    }
                    if (domainPart.isNotEmpty()) {
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        ) {
                            append("@")
                            append(domainPart)
                        }
                    }
                },
                style = MailxAddressStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (chipVisual != null || forwards > 0 || blocks > 0 || outbound > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (chipVisual != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = chipVisual.container
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = chipVisual.icon,
                                    contentDescription = null,
                                    tint = chipVisual.content,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = chipVisual.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = chipVisual.content
                                )
                            }
                        }
                    }

                    if (forwards > 0) {
                        AliasInlineStat(icon = MailxIcons.Forward, count = forwards)
                    }
                    if (blocks > 0) {
                        AliasInlineStat(icon = MailxIcons.Ban, count = blocks)
                    }
                    if (outbound > 0) {
                        AliasInlineStat(icon = MailxIcons.Reply, count = outbound)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        val actionBg by animateColorAsState(
            targetValue = if (justCopied) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            animationSpec = if (reducedMotion) snap() else tween(durationMillis = 160),
            label = "copy_action_bg"
        )
        val actionTint by animateColorAsState(
            targetValue = if (justCopied) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            animationSpec = if (reducedMotion) snap() else tween(durationMillis = 160),
            label = "copy_action_tint"
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(actionBg)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!isWildcard) {
                        justCopied = true
                    }
                    onCopyOrWildcard()
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = justCopied,
                transitionSpec = {
                    fadeIn(tween(120)).togetherWith(fadeOut(tween(100)))
                },
                label = "copy_icon_anim"
            ) { copied ->
                Icon(
                    imageVector = when {
                        copied -> MailxIcons.Check
                        isWildcard -> MailxIcons.Dice
                        else -> MailxIcons.Copy
                    },
                    contentDescription = if (isWildcard) {
                        "Generate from ${alias.name}"
                    } else {
                        "Copy ${alias.name}"
                    },
                    tint = actionTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AliasInlineStat(
    icon: ImageVector,
    count: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
