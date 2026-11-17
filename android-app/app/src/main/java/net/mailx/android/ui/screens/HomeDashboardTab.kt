package net.mailx.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ForwardToInbox
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.components.AliasCard
import net.mailx.android.ui.components.MailxActivityRing
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxStatTile
import net.mailx.android.ui.components.MailxTelemetryBar
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
            val raw = MailxRustBridge.nativeComputePrivacyPosture(
                totalForwards,
                totalBlocks,
                activeCount,
                aliases.size,
                pgpActive,
                blockTrackers,
                removeHeader,
                ruleCount
            )
            val data = JSONObject(raw).optJSONObject("data")
            val score = data?.optInt("score", 85) ?: 85
            val grade = data?.optString("grade", "A") ?: "A"
            score to grade
        }.getOrElse { 85 to "A" }
    }

    val spotlightAliases = remember(aliases) {
        aliases
            .filter { it.deletedAt.isBlank() }
            .sortedWith(compareByDescending<UiAlias> { it.pinned }.thenByDescending { it.totalActivity })
            .take(3)
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "hero_privacy_telemetry_card") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                    size = 52.dp,
                                    strokeWidth = 5.5.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "@$defaultDomain",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = MailxPalette.ForwardEmerald.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = "Shield ${posturePair.second} (${posturePair.first}%)",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MailxPalette.ForwardEmerald,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "$activeCount active of ${aliases.size} aliases • $recipientCount recipients",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onOpenCreateAlias,
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("+ Alias", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        MailxTelemetryBar(
                            forwards = totalForwards,
                            replies = totalReplies,
                            sends = totalSends,
                            blocks = totalBlocks
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CompactMetricPill(
                                label = "Forwarded",
                                value = totalForwards.toString(),
                                color = MailxPalette.ForwardEmerald,
                                modifier = Modifier.weight(1f)
                            )
                            CompactMetricPill(
                                label = "Blocked",
                                value = totalBlocks.toString(),
                                color = MailxPalette.BlockedRose,
                                modifier = Modifier.weight(1f)
                            )
                            CompactMetricPill(
                                label = "Replied",
                                value = totalReplies.toString(),
                                color = MailxPalette.ReplySky,
                                modifier = Modifier.weight(1f)
                            )
                            CompactMetricPill(
                                label = "Sent",
                                value = totalSends.toString(),
                                color = MailxPalette.SentIndigo,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (userStats.dailyPoints.isNotEmpty()) {
                item(key = "mailx_7day_chart") {
                    MailxSevenDayChartCard(userStats = userStats)
                }
            }

            item(key = "header_aliases_matrix") {
                MailxSectionHeader(
                    title = "Alias Matrix",
                    countBadge = aliases.size,
                    actionText = "All Aliases",
                    onActionClick = { onNavigateToFilter("active_inactive") }
                )
            }

            item(key = "aliases_compact_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MailxStatTile(
                            title = "Active",
                            value = activeCount.toString(),
                            icon = Icons.Default.Check,
                            accentColor = MailxPalette.ForwardEmerald,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToFilter("active") }
                        )
                        MailxStatTile(
                            title = "Pinned",
                            value = pinnedCount.toString(),
                            icon = Icons.Default.PushPin,
                            accentColor = MailxPalette.PausedAmber,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToFilter("pinned_only") }
                        )
                        MailxStatTile(
                            title = "Wildcard (*)",
                            value = wildcardCount.toString(),
                            icon = Icons.Default.AutoAwesome,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToFilter("wildcard_only") }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MailxStatTile(
                            title = "Total",
                            value = aliases.size.toString(),
                            icon = Icons.Default.AlternateEmail,
                            accentColor = MailxPalette.ReplySky,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToFilter("active_inactive") }
                        )
                        MailxStatTile(
                            title = "Disabled",
                            value = inactiveCount.toString(),
                            icon = Icons.Default.Close,
                            accentColor = MailxPalette.Slate500,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToFilter("inactive") }
                        )
                        MailxStatTile(
                            title = "Trash",
                            value = deletedCount.toString(),
                            icon = Icons.Default.DeleteOutline,
                            accentColor = MailxPalette.BlockedRose,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateToFilter("deleted") }
                        )
                    }
                }
            }

            if (spotlightAliases.isNotEmpty()) {
                item(key = "header_spotlight_aliases") {
                    MailxSectionHeader(
                        title = "Quick Access Aliases",
                        actionText = "New Alias",
                        onActionClick = onOpenCreateAlias
                    )
                }

                items(spotlightAliases, key = { "home_${it.id}" }) { alias ->
                    AliasCard(
                        alias = alias,
                        onClick = { onSelectAlias(alias) },
                        onCopyOrWildcard = { onCopyOrWildcard(alias) },
                        onToggleEnabled = { en -> onToggleEnabled(alias, en) }
                    )
                }
            }

            item(key = "header_privacy_tools") {
                MailxSectionHeader(
                    title = "Privacy & Relay Tools"
                )
            }

            item(key = "tools_compact_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MailxStatTile(
                            title = "Send via Alias",
                            value = "Reverse Relay",
                            icon = Icons.AutoMirrored.Filled.ForwardToInbox,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenReverseAliasTool
                        )
                        MailxStatTile(
                            title = "Tracker Decoder",
                            value = "Local Rust",
                            icon = Icons.Default.Shield,
                            accentColor = MailxPalette.ForwardEmerald,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenTrackersReport
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MailxStatTile(
                            title = "Recipients & PGP",
                            value = "$recipientCount Verified",
                            icon = Icons.Default.Inbox,
                            accentColor = MailxPalette.ReplySky,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToRecipients
                        )
                        MailxStatTile(
                            title = "Rules & Blocklist",
                            value = "$ruleCount Rules • $blocklistCount Blk",
                            icon = Icons.Default.FilterAlt,
                            accentColor = MailxPalette.SentIndigo,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToRules
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MailxStatTile(
                            title = "Delivery Logs",
                            value = "Bounce & MTA",
                            icon = Icons.Default.BugReport,
                            accentColor = MailxPalette.PausedAmber,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenDiagnostics
                        )
                        MailxStatTile(
                            title = "Account & Keys",
                            value = "2FA • CSV • Keys",
                            icon = Icons.Default.Key,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenAccountSheet
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MailxSevenDayChartCard(userStats: UiUserStats) {
    val maxDayTotal = remember(userStats.dailyPoints) {
        (userStats.dailyPoints.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
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
                    text = "7-Day Relay Activity",
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
                    .height(82.dp),
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
