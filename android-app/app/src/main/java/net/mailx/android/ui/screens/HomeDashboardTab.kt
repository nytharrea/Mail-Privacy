package net.mailx.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ForwardToInbox
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.components.AliasCard
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.components.LiquidGlassSurface
import net.mailx.android.ui.components.LiquidMaterialPreset
import net.mailx.android.ui.components.MailxActivityRing
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.UiUserStats
import net.mailx.android.ui.theme.MailxPalette
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardTab(
    aliases: List<UiAlias>,
    userStats: UiUserStats = UiUserStats(),
    isLoading: Boolean,
    defaultDomain: String,
    recipientCount: Int = 1,
    ruleCount: Int = 0,
    blocklistCount: Int = 0,
    blockTrackers: Boolean = true,
    removeHeader: Boolean = false,
    pgpActive: Boolean = false,
    onRefresh: () -> Unit,
    onNavigateToFilter: (String) -> Unit,
    onNavigateToRecipients: () -> Unit = {},
    onNavigateToRules: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenAccountSheet: () -> Unit = {},
    onOpenTrackersReport: () -> Unit = {},
    onOpenPrivacyLab: () -> Unit = {},
    onSelectAlias: (UiAlias) -> Unit,
    onCopyOrWildcard: (UiAlias) -> Unit,
    onToggleEnabled: (UiAlias, Boolean) -> Unit,
    onOpenCreateAlias: () -> Unit,
    onOpenReverseAliasTool: () -> Unit
) {
    val aliasForwards = remember(aliases) { aliases.sumOf { it.forwards } }
    val aliasBlocks = remember(aliases) { aliases.sumOf { it.blocks } }
    val aliasReplies = remember(aliases) { aliases.sumOf { it.replies } }
    val aliasSends = remember(aliases) { aliases.sumOf { it.sends } }

    val totalForwards = maxOf(aliasForwards, userStats.forwards)
    val totalBlocks = maxOf(aliasBlocks, userStats.blocks)
    val totalReplies = maxOf(aliasReplies, userStats.replies)
    val totalSends = maxOf(aliasSends, userStats.sends)

    val activeCount = remember(aliases) { aliases.count { it.enabled && it.deletedAt.isBlank() } }
    val inactiveCount = remember(aliases) { aliases.count { !it.enabled && it.deletedAt.isBlank() } }
    val pinnedCount = remember(aliases) { aliases.count { it.pinned && it.deletedAt.isBlank() } }
    val wildcardCount = remember(aliases) { aliases.count { it.wildcard && it.deletedAt.isBlank() } }
    val deletedCount = remember(aliases) { aliases.count { it.deletedAt.isNotBlank() } }

    val posturePair = remember(
        totalForwards,
        totalBlocks,
        activeCount,
        aliases.size,
        pgpActive,
        blockTrackers,
        removeHeader,
        ruleCount
    ) {
        runCatching {
            val env = JSONObject(
                MailxRustBridge.nativeComputePrivacyPosture(
                    totalForwards,
                    totalBlocks,
                    activeCount,
                    aliases.size,
                    pgpActive,
                    blockTrackers,
                    removeHeader,
                    ruleCount
                )
            )
            val d = env.optJSONObject("data")
            Pair(
                d?.optInt("score", 85) ?: 85,
                d?.optString("grade", "A") ?: "A"
            )
        }.getOrDefault(Pair(85, "A"))
    }

    val spotlightAliases = remember(aliases) {
        aliases
            .filter { it.deletedAt.isBlank() }
            .sortedWith(compareByDescending<UiAlias> { it.pinned }.thenByDescending { it.totalActivity })
            .take(4)
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "hero_summary_card") {
                LiquidGlassSurface(
                    cornerRadius = 24.dp,
                    preset = LiquidMaterialPreset.Regular,
                    tintColor = MailxPalette.BrandAccentMint.copy(alpha = 0.14f),
                    estimatedWidthDp = 360f,
                    estimatedHeightDp = 152f,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                MailxActivityRing(
                                    forwards = totalForwards,
                                    replies = totalReplies,
                                    sends = totalSends,
                                    blocks = totalBlocks,
                                    enabled = true,
                                    size = 46.dp,
                                    strokeWidth = 5.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "@$defaultDomain",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = MailxPalette.ForwardEmerald.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = "Shield ${posturePair.second} (${posturePair.first})",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MailxPalette.ForwardEmerald,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "$activeCount active of ${aliases.size} aliases • $recipientCount recipient${if (recipientCount == 1) "" else "s"}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = onOpenCreateAlias,
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CleanStatColumn(
                                value = totalForwards.toString(),
                                label = "Forwarded",
                                color = MailxPalette.ForwardEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            CleanStatColumn(
                                value = totalBlocks.toString(),
                                label = "Blocked",
                                color = MailxPalette.BlockedRose,
                                modifier = Modifier.weight(1f)
                            )
                            CleanStatColumn(
                                value = totalReplies.toString(),
                                label = "Replied",
                                color = MailxPalette.ReplySky,
                                modifier = Modifier.weight(1f)
                            )
                            CleanStatColumn(
                                value = totalSends.toString(),
                                label = "Sent",
                                color = MailxPalette.SentIndigo,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item(key = "privacy_lab_banner") {
                LiquidGlassSurface(
                    cornerRadius = 18.dp,
                    preset = LiquidMaterialPreset.Tinted,
                    tintColor = MailxPalette.ReplySky.copy(alpha = 0.22f),
                    estimatedWidthDp = 360f,
                    estimatedHeightDp = 64f,
                    interactiveScale = true,
                    onClick = onOpenPrivacyLab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MailxPalette.BrandAccentMint.copy(alpha = 0.18f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.HealthAndSafety,
                                        contentDescription = null,
                                        tint = MailxPalette.BrandAccentMint,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Rust Privacy Lab & Hygiene Auditor",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Strip tracking URLs, inspect SPF/DKIM headers & generate smart aliases",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = onOpenPrivacyLab,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Open Lab", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item(key = "quick_filter_pills") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickFilterChip(
                        icon = Icons.Default.CheckCircle,
                        label = "Active",
                        count = activeCount,
                        tint = MailxPalette.ForwardEmerald,
                        onClick = { onNavigateToFilter("active") }
                    )
                    QuickFilterChip(
                        icon = Icons.Default.PushPin,
                        label = "Pinned",
                        count = pinnedCount,
                        tint = MailxPalette.PausedAmber,
                        onClick = { onNavigateToFilter("pinned_only") }
                    )
                    QuickFilterChip(
                        icon = Icons.Default.AutoAwesome,
                        label = "Wildcard",
                        count = wildcardCount,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigateToFilter("wildcard_only") }
                    )
                    QuickFilterChip(
                        icon = Icons.Default.PauseCircleOutline,
                        label = "Disabled",
                        count = inactiveCount,
                        tint = MailxPalette.Slate400,
                        onClick = { onNavigateToFilter("inactive") }
                    )
                    if (deletedCount > 0) {
                        QuickFilterChip(
                            icon = Icons.Default.DeleteOutline,
                            label = "Trash",
                            count = deletedCount,
                            tint = MailxPalette.BlockedRose,
                            onClick = { onNavigateToFilter("deleted") }
                        )
                    }
                }
            }

            if (userStats.dailyPoints.any { it.total > 0 }) {
                item(key = "mailx_7day_chart") {
                    MailxSevenDayChartCard(userStats = userStats)
                }
            }

            item(key = "header_spotlight_aliases") {
                MailxSectionHeader(
                    title = if (pinnedCount > 0) "Pinned & Recent Aliases" else "Recent Aliases",
                    countBadge = aliases.size,
                    actionText = "See All",
                    onActionClick = { onNavigateToFilter("active_inactive") }
                )
            }

            if (spotlightAliases.isEmpty()) {
                item(key = "empty_aliases_home") {
                    EmptyStateBox(
                        icon = Icons.Default.AlternateEmail,
                        title = "No aliases yet",
                        subtitle = "Create your first disposable or wildcard alias on @$defaultDomain.",
                        actionLabel = "Create Alias",
                        onActionClick = onOpenCreateAlias
                    )
                }
            } else {
                items(spotlightAliases, key = { "home_${it.id}" }) { alias ->
                    AliasCard(
                        alias = alias,
                        onClick = { onSelectAlias(alias) },
                        onCopyOrWildcard = { onCopyOrWildcard(alias) },
                        onToggleEnabled = { en -> onToggleEnabled(alias, en) }
                    )
                }
            }

            item(key = "header_quick_tools") {
                MailxSectionHeader(title = "Quick Tools")
            }

            item(key = "grouped_quick_tools_card") {
                LiquidGlassSurface(
                    cornerRadius = 20.dp,
                    preset = LiquidMaterialPreset.Regular,
                    estimatedWidthDp = 360f,
                    estimatedHeightDp = 280f,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CompactToolRow(
                            icon = Icons.Default.AutoAwesome,
                            title = "Rust Privacy & Header Lab",
                            subtitle = "URL tracker cleaner, SPF/DKIM inspector & hygiene audit",
                            tint = MailxPalette.BrandAccentMint,
                            onClick = onOpenPrivacyLab
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        CompactToolRow(
                            icon = Icons.AutoMirrored.Filled.ForwardToInbox,
                            title = "Send from Alias",
                            subtitle = "Generate a reverse-alias address to compose an email",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onOpenReverseAliasTool
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        CompactToolRow(
                            icon = Icons.Default.Shield,
                            title = "Tracker Report Inspector",
                            subtitle = "Decode removed spy pixels & tracking links offline",
                            tint = MailxPalette.ForwardEmerald,
                            onClick = onOpenTrackersReport
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        CompactToolRow(
                            icon = Icons.Default.BugReport,
                            title = "Delivery & Bounce Logs",
                            subtitle = "Check server health and inspect failed deliveries",
                            tint = MailxPalette.PausedAmber,
                            onClick = onOpenDiagnostics
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        CompactToolRow(
                            icon = Icons.Default.Key,
                            title = "Account, 2FA & CSV Backup",
                            subtitle = "Manage TOTP, passkeys, access keys & alias export",
                            tint = MailxPalette.ReplySky,
                            onClick = onOpenAccountSheet
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanStatColumn(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun QuickFilterChip(
    icon: ImageVector,
    label: String,
    count: Int,
    tint: Color,
    onClick: () -> Unit
) {
    LiquidGlassSurface(
        cornerRadius = 20.dp,
        preset = LiquidMaterialPreset.Clear,
        tintColor = tint.copy(alpha = 0.14f),
        estimatedWidthDp = 110f,
        estimatedHeightDp = 36f,
        interactiveScale = true,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.18f)
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun CompactToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = tint.copy(alpha = 0.16f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun MailxSevenDayChartCard(userStats: UiUserStats) {
    val maxDayTotal = remember(userStats.dailyPoints) {
        (userStats.dailyPoints.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
    }

    LiquidGlassSurface(
        cornerRadius = 20.dp,
        preset = LiquidMaterialPreset.Regular,
        estimatedWidthDp = 360f,
        estimatedHeightDp = 140f,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "7-Day Activity",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChartLegendDot(color = MailxPalette.ForwardEmerald, label = "Fwd")
                    ChartLegendDot(color = MailxPalette.ReplySky, label = "Rep")
                    ChartLegendDot(color = MailxPalette.SentIndigo, label = "Sent")
                    ChartLegendDot(color = MailxPalette.BlockedRose, label = "Blk")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                userStats.dailyPoints.forEach { pt ->
                    val frac = (pt.total.toFloat() / maxDayTotal.toFloat()).coerceIn(0.08f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            text = pt.total.toString(),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .fillMaxHeight(fraction = frac * 0.68f)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(
                                    when {
                                        pt.blocks > pt.forwards -> MailxPalette.BlockedRose
                                        pt.replies > 0 -> MailxPalette.ReplySky
                                        pt.sends > 0 -> MailxPalette.SentIndigo
                                        pt.total > 0 -> MailxPalette.ForwardEmerald
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pt.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
