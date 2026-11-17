package net.mailx.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
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
import net.mailx.android.ui.components.MailxActivityRing
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxTelemetryBar
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
        privacyScore >= 65 -> MailxPalette.ReplySky
        else -> MailxPalette.PausedAmber
    }

    val privacySummarySubtitle = remember(blockTrackers, pgpActive, removeHeader, ruleCount) {
        val activeProtections = buildList {
            if (blockTrackers) add("Tracker shield")
            if (pgpActive) add("OpenPGP")
            if (removeHeader) add("Header hidden")
            if (ruleCount > 0) add("$ruleCount rule${if (ruleCount == 1) "" else "s"}")
        }
        if (activeProtections.isEmpty()) {
            "Tap to view privacy score breakdown"
        } else {
            activeProtections.joinToString(" • ") + " • Tap for details"
        }
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Clean, Organized Hero Summary Card
            item(key = "hero_summary_card") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Top Row: Activity Ring + Domain & Alias/Recipient Summary + New Alias CTA
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
                                    size = 48.dp,
                                    strokeWidth = 5.dp
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "@$defaultDomain",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$activeCount of ${aliases.size} active • $recipientCount recipient${if (recipientCount == 1) "" else "s"}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = onOpenCreateAlias,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Icon(
                                    imageVector = MailxIcons.Plus,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("New Alias", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Interactive Privacy Protection Score Banner (replaces cramped "Shield B (65)")
                        Surface(
                            onClick = { showPrivacyScoreDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                        color = scoreColor.copy(alpha = 0.16f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = MailxIcons.ShieldCheck,
                                                contentDescription = null,
                                                tint = scoreColor,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            text = "Privacy Protection Score",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = privacySummarySubtitle,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = scoreColor.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "$privacyScore/100 ($privacyGrade)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = scoreColor,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                    Icon(
                                        imageVector = MailxIcons.ChevronRight,
                                        contentDescription = "Details",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        MailxTelemetryBar(
                            forwards = totalForwards,
                            replies = totalReplies,
                            sends = totalSends,
                            blocks = totalBlocks
                        )

                        // 4 Tonal Mini-Cards for Telemetry Breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OverviewMetricCard(
                                value = totalForwards.toString(),
                                label = "Forwarded",
                                color = MailxPalette.ForwardEmerald,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigateToFilter("active") }
                            )
                            OverviewMetricCard(
                                value = totalBlocks.toString(),
                                label = "Blocked",
                                color = MailxPalette.BlockedRose,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigateToFilter("active_inactive") }
                            )
                            OverviewMetricCard(
                                value = totalReplies.toString(),
                                label = "Replied",
                                color = MailxPalette.ReplySky,
                                modifier = Modifier.weight(1f),
                                onClick = { onNavigateToFilter("active_inactive") }
                            )
                            OverviewMetricCard(
                                value = totalSends.toString(),
                                label = "Sent",
                                color = MailxPalette.SentIndigo,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenReverseAliasTool
                            )
                        }
                    }
                }
            }

            // 2. Quick Status Filter Chips
            item(key = "quick_filter_pills") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickFilterChip(
                        icon = MailxIcons.CheckCircle,
                        label = "Active",
                        count = activeCount,
                        tint = MailxPalette.ForwardEmerald,
                        onClick = { onNavigateToFilter("active") }
                    )
                    QuickFilterChip(
                        icon = MailxIcons.Pin,
                        label = "Pinned",
                        count = pinnedCount,
                        tint = MailxPalette.PausedAmber,
                        onClick = { onNavigateToFilter("pinned_only") }
                    )
                    QuickFilterChip(
                        icon = MailxIcons.Sparkles,
                        label = "Wildcard",
                        count = wildcardCount,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigateToFilter("wildcard_only") }
                    )
                    QuickFilterChip(
                        icon = MailxIcons.PauseCircle,
                        label = "Disabled",
                        count = inactiveCount,
                        tint = MailxPalette.Slate400,
                        onClick = { onNavigateToFilter("inactive") }
                    )
                    if (deletedCount > 0) {
                        QuickFilterChip(
                            icon = MailxIcons.Trash2,
                            label = "Trash",
                            count = deletedCount,
                            tint = MailxPalette.BlockedRose,
                            onClick = { onNavigateToFilter("deleted") }
                        )
                    }
                }
            }

            // 3. 2x2 Quick Actions Bento Grid (Promoted for instant access)
            item(key = "quick_actions_bento") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MailxSectionHeader(title = "Quick Actions")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionBentoTile(
                            icon = MailxIcons.Send,
                            title = "Send Mail",
                            subtitle = "Compose via alias",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onOpenReverseAliasTool,
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionBentoTile(
                            icon = MailxIcons.ShieldCheck,
                            title = "Tracker Check",
                            subtitle = "Inspect spy pixels",
                            tint = MailxPalette.ForwardEmerald,
                            onClick = onOpenTrackersReport,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionBentoTile(
                            icon = MailxIcons.KeyRound,
                            title = "Account & 2FA",
                            subtitle = "Passkeys, TOTP & CSV",
                            tint = MailxPalette.ReplySky,
                            onClick = onOpenAccountSheet,
                            modifier = Modifier.weight(1f)
                        )
                        QuickActionBentoTile(
                            icon = MailxIcons.Bug,
                            title = "Delivery Logs",
                            subtitle = "Bounces & server health",
                            tint = MailxPalette.PausedAmber,
                            onClick = onOpenDiagnostics,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. 7-Day Telemetry Chart (when activity exists)
            if (userStats.dailyPoints.any { it.total > 0 }) {
                item(key = "mailx_7day_chart") {
                    MailxSevenDayChartCard(userStats = userStats)
                }
            }

            // 5. Pinned & Recent Aliases Section
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
                        icon = MailxIcons.AtSign,
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
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your Privacy Protection Score measures how many email shielding features are currently active on your account:",
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
                    Text("Got it")
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (active) MailxPalette.ForwardEmerald else MailxPalette.Slate500)
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
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
                        Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = if (active) {
                        MailxPalette.ForwardEmerald.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Text(
                        text = points,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
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
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
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
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
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
                        .background(color)
                )
                Text(
                    text = label,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickActionBentoTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = tint.copy(alpha = 0.15f),
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        )
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
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.16f)
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
private fun MailxSevenDayChartCard(userStats: UiUserStats) {
    val maxDayTotal = remember(userStats.dailyPoints) {
        (userStats.dailyPoints.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
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
