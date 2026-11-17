package net.mailx.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ForwardToInbox
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.AddyHomeStatCard
import net.mailx.android.ui.components.AddySectionHeader
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.UiUserStats
import net.mailx.android.ui.theme.AddyPalette

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
    val pinnedCount = remember(aliases) { aliases.count { it.pinned } }
    val wildcardCount = remember(aliases) { aliases.count { it.wildcard } }
    val deletedCount = remember(aliases) { aliases.count { it.deletedAt.isNotBlank() } }

    val activeRatio = remember(aliases.size, activeCount) {
        if (aliases.isEmpty()) 0f else activeCount.toFloat() / aliases.size.toFloat()
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "home_stat_card_bandwidth") {
                AddyHomeStatCard(
                    title = "Default Domain",
                    description = "@$defaultDomain • $activeCount/${aliases.size} active aliases",
                    icon = Icons.Default.Speed,
                    progressFraction = activeRatio,
                    onClick = { onNavigateToFilter("active_inactive") }
                )
            }

            item(key = "header_statistics") {
                AddySectionHeader(
                    title = "Statistics (Last 90 Days / Total)",
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item(key = "home_stats_grid_row1") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AddyHomeStatCard(
                        title = "Forwarded",
                        description = totalForwards.toString(),
                        icon = Icons.Default.Inbox,
                        modifier = Modifier.weight(1f)
                    )
                    AddyHomeStatCard(
                        title = "Blocked",
                        description = totalBlocks.toString(),
                        icon = Icons.Default.Block,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "home_stats_grid_row2") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AddyHomeStatCard(
                        title = "Replied",
                        description = totalReplies.toString(),
                        icon = Icons.AutoMirrored.Filled.Reply,
                        modifier = Modifier.weight(1f)
                    )
                    AddyHomeStatCard(
                        title = "Sent",
                        description = totalSends.toString(),
                        icon = Icons.AutoMirrored.Filled.ForwardToInbox,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (userStats.dailyPoints.isNotEmpty()) {
                item(key = "mailx_7day_chart") {
                    MailxSevenDayChartCard(userStats = userStats)
                }
            }

            item(key = "header_aliases") {
                AddySectionHeader(
                    title = "Aliases",
                    actionText = "New Alias",
                    onActionClick = onOpenCreateAlias,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            item(key = "home_aliases_grid_row1") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AddyHomeStatCard(
                        title = "Total Aliases",
                        description = aliases.size.toString(),
                        icon = Icons.Default.AlternateEmail,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToFilter("active_inactive") }
                    )
                    AddyHomeStatCard(
                        title = "Active Aliases",
                        description = activeCount.toString(),
                        icon = Icons.Default.Check,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToFilter("active") }
                    )
                }
            }

            item(key = "home_aliases_grid_row2") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AddyHomeStatCard(
                        title = "Disabled Aliases",
                        description = inactiveCount.toString(),
                        icon = Icons.Default.Close,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToFilter("inactive") }
                    )
                    AddyHomeStatCard(
                        title = "Pinned",
                        description = pinnedCount.toString(),
                        icon = Icons.Default.PushPin,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToFilter("pinned_only") }
                    )
                }
            }

            item(key = "home_aliases_grid_row3") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AddyHomeStatCard(
                        title = "Wildcard (*)",
                        description = wildcardCount.toString(),
                        icon = Icons.Default.AutoAwesome,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToFilter("wildcard_only") }
                    )
                    AddyHomeStatCard(
                        title = "Deleted",
                        description = deletedCount.toString(),
                        icon = Icons.Default.DeleteOutline,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToFilter("deleted") }
                    )
                }
            }

            item(key = "header_recipients_tools") {
                AddySectionHeader(
                    title = "Recipients, Rules & Tools",
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            item(key = "home_recipients_row1") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AddyHomeStatCard(
                        title = "Recipients & Domains",
                        description = "$recipientCount recipients",
                        icon = Icons.Default.Inbox,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToRecipients
                    )
                    AddyHomeStatCard(
                        title = "Rules & Blocklist",
                        description = "$ruleCount rules • $blocklistCount blocked",
                        icon = Icons.Default.FilterAlt,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToRules
                    )
                }
            }

            item(key = "home_recipients_row2") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AddyHomeStatCard(
                        title = "Send from Alias",
                        description = "Reverse-Alias Tool",
                        icon = Icons.AutoMirrored.Filled.ForwardToInbox,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenReverseAliasTool
                    )
                    AddyHomeStatCard(
                        title = "Diagnostics Logs",
                        description = "Delivery & Bounce Logs",
                        icon = Icons.Default.BugReport,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenDiagnostics
                    )
                }
            }

            item(key = "home_trackers_account_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AddyHomeStatCard(
                        title = "Trackers Report",
                        description = "Inspect Removed Trackers",
                        icon = Icons.Default.Shield,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenTrackersReport
                    )
                    AddyHomeStatCard(
                        title = "Account & Keys",
                        description = "2FA, Passkeys & CSV",
                        icon = Icons.Default.Key,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenAccountSheet
                    )
                }
            }
        }
    }
}

@Composable
private fun MailxSevenDayChartCard(userStats: UiUserStats) {
    val maxDayTotal = remember(userStats.dailyPoints) {
        (userStats.dailyPoints.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Last 7 Days Email Traffic",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChartLegendDot(color = AddyPalette.PortalOrange, label = "Forwarded")
                    ChartLegendDot(color = AddyPalette.PortalBlue, label = "Replied")
                    ChartLegendDot(color = AddyPalette.EasternBlue, label = "Sent")
                    ChartLegendDot(color = AddyPalette.SoftRed, label = "Blocked")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                userStats.dailyPoints.forEach { pt ->
                    val frac = (pt.total.toFloat() / maxDayTotal.toFloat()).coerceIn(0.06f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            text = pt.total.toString(),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .fillMaxHeight(fraction = frac * 0.72f)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    when {
                                        pt.blocks > pt.forwards -> AddyPalette.SoftRed
                                        pt.replies > 0 -> AddyPalette.PortalBlue
                                        pt.sends > 0 -> AddyPalette.EasternBlue
                                        pt.total > 0 -> AddyPalette.PortalOrange
                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pt.label,
                            fontSize = 11.sp,
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
