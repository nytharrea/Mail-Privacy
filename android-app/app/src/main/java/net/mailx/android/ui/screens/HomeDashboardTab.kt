package net.mailx.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
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
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.components.MailxAliasSkeletonList
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.UiUserStats
import net.mailx.android.ui.theme.MailxIcons
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
    var showPrivacyScoreDialog by remember { mutableStateOf(false) }

    val aliasForwards = remember(aliases) { aliases.sumOf { it.forwards } }
    val aliasBlocks = remember(aliases) { aliases.sumOf { it.blocks } }
    val aliasReplies = remember(aliases) { aliases.sumOf { it.replies } }
    val aliasSends = remember(aliases) { aliases.sumOf { it.sends } }

    val totalForwards = maxOf(aliasForwards, userStats.forwards)
    val totalBlocks = maxOf(aliasBlocks, userStats.blocks)
    val totalReplies = maxOf(aliasReplies, userStats.replies)
    val totalSends = maxOf(aliasSends, userStats.sends)

    val activeCount = remember(aliases) { aliases.count { it.enabled && it.deletedAt.isBlank() } }
    val pinnedCount = remember(aliases) { aliases.count { it.pinned && it.deletedAt.isBlank() } }

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
            val res = JSONObject(
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
            val data = res.optJSONObject("data")
            val score = data?.optInt("score", 85) ?: 85
            val grade = data?.optString("grade", "A") ?: "A"
            score to grade
        }.getOrDefault(85 to "A")
    }

    val privacyScore = posturePair.first
    val privacyGrade = posturePair.second
    val scoreColor = when {
        privacyScore >= 82 -> MailxPalette.ForwardEmerald
        privacyScore >= 65 -> MaterialTheme.colorScheme.primary
        else -> MailxPalette.PausedAmber
    }

    val spotlightAliases = remember(aliases) {
        aliases
            .filter { it.deletedAt.isBlank() }
            .sortedWith(compareByDescending<UiAlias> { it.pinned }.thenByDescending { it.totalActivity })
            .take(5)
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Calm, Spacious Overview Card (No rings or duplicate filter chips)
            item(key = "hero_summary_card") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
                    ),
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "@$defaultDomain",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$activeCount active of ${aliases.size} aliases • $recipientCount recipient${if (recipientCount == 1) "" else "s"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                onClick = { showPrivacyScoreDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = MailxIcons.ShieldCheck,
                                        contentDescription = "Privacy Score",
                                        tint = scoreColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Shield $privacyGrade ($privacyScore)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Clean 4-column metric summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OverviewMetricCard(
                                value = totalForwards.toString(),
                                label = "Forwarded",
                                dotColor = MailxPalette.ForwardEmerald,
                                isCritical = false,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigateToFilter("active") }
                            )
                            OverviewMetricCard(
                                value = totalBlocks.toString(),
                                label = "Blocked",
                                dotColor = MailxPalette.BlockedRose,
                                isCritical = totalBlocks > 0,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigateToFilter("active_inactive") }
                            )
                            OverviewMetricCard(
                                value = totalReplies.toString(),
                                label = "Replied",
                                dotColor = MailxPalette.ReplySky,
                                isCritical = false,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigateToFilter("active_inactive") }
                            )
                            OverviewMetricCard(
                                value = totalSends.toString(),
                                label = "Sent",
                                dotColor = MailxPalette.SentIndigo,
                                isCritical = false,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenReverseAliasTool
                            )
                        }

                        // Clean 2-button action row with breathing room
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onOpenCreateAlias,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                            ) {
                                Icon(
                                    imageVector = MailxIcons.Plus,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Alias", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = onOpenReverseAliasTool,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                            ) {
                                Icon(
                                    imageVector = MailxIcons.Send,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Send Mail",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 2. 7-Day Telemetry Chart (only when activity exists)
            if (userStats.dailyPoints.any { it.total > 0 }) {
                item(key = "mailx_7day_chart") {
                    MailxSevenDayChartCard(userStats = userStats)
                }
            }

            // 3. Pinned & Recent Aliases Section
            item(key = "header_spotlight_aliases") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MailxSectionHeader(
                        title = if (pinnedCount > 0) "Pinned & Recent Aliases" else "Recent Aliases",
                        countBadge = aliases.size,
                        actionText = "See All",
                        onActionClick = { onNavigateToFilter("active_inactive") }
                    )

                    if (isLoading && spotlightAliases.isEmpty()) {
                        MailxAliasSkeletonList(count = 4)
                    } else if (spotlightAliases.isEmpty()) {
                        EmptyStateBox(
                            icon = MailxIcons.AtSign,
                            title = "No aliases yet",
                            subtitle = "Create your first disposable or wildcard alias on @$defaultDomain.",
                            actionLabel = "Create Alias",
                            onActionClick = onOpenCreateAlias
                        )
                    }
                }
            }

            if (spotlightAliases.isNotEmpty()) {
                items(spotlightAliases, key = { "home_${it.id}" }) { alias ->
                    AliasCard(
                        alias = alias,
                        onClick = { onSelectAlias(alias) },
                        onCopyOrWildcard = { onCopyOrWildcard(alias) },
                        onToggleEnabled = { en -> onToggleEnabled(alias, en) }
                    )
                }
            }
        }
    }

    if (showPrivacyScoreDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyScoreDialog = false },
            icon = {
                Icon(
                    imageVector = MailxIcons.ShieldCheck,
                    contentDescription = null,
                    tint = scoreColor
                )
            },
            title = {
                Text(
                    text = "Privacy Score: $privacyScore/100 ($privacyGrade)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your Privacy Protection Score reflects active email shielding features on your account:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    PrivacyScoreCheckRow(
                        label = "Base Alias Relay Protection",
                        points = "+40",
                        active = true
                    )
                    PrivacyScoreCheckRow(
                        label = "Email Tracker & Spy Pixel Blocking",
                        points = "+20",
                        active = blockTrackers
                    )
                    PrivacyScoreCheckRow(
                        label = "End-to-End OpenPGP Encryption",
                        points = "+20",
                        active = pgpActive,
                        actionLabel = if (!pgpActive) "Setup" else null,
                        onAction = {
                            showPrivacyScoreDialog = false
                            onNavigateToRecipients()
                        }
                    )
                    PrivacyScoreCheckRow(
                        label = "Strip Mailx Relay Banner Header",
                        points = "+8",
                        active = removeHeader
                    )
                    PrivacyScoreCheckRow(
                        label = "Active Forwarding Filter Rules",
                        points = "+7",
                        active = ruleCount > 0,
                        actionLabel = if (ruleCount == 0) "Add Rule" else null,
                        onAction = {
                            showPrivacyScoreDialog = false
                            onNavigateToRules()
                        }
                    )
                    PrivacyScoreCheckRow(
                        label = "Active Disposable Aliases",
                        points = "+5",
                        active = activeCount > 0
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyScoreDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun PrivacyScoreCheckRow(
    label: String,
    points: String,
    active: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (active) MailxPalette.ForwardEmerald else MailxPalette.Slate500)
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!active && actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (active) {
                        MailxPalette.ForwardEmerald.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Text(
                        text = points,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) MailxPalette.ForwardEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewMetricCard(
    value: String,
    label: String,
    dotColor: Color,
    isCritical: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCritical) dotColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = label,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
        ),
        tonalElevation = 0.dp,
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
                    text = "7-Day Activity",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
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
                    .height(74.dp),
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
                                .width(18.dp)
                                .fillMaxHeight(fraction = frac * 0.68f)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
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
