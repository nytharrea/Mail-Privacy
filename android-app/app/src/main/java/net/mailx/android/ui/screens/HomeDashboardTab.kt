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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.components.AliasCard
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.components.MailxActivityRing
import net.mailx.android.ui.components.MailxAliasSkeletonList
import net.mailx.android.ui.components.MailxModernDialog
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxSegmentedControl
import net.mailx.android.ui.components.MailxTelemetryBar
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.UiUserStats
import net.mailx.android.ui.model.copyToClipboard
import net.mailx.android.ui.theme.MailxIcons
import net.mailx.android.ui.theme.MailxPalette
import org.json.JSONArray
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
    val context = LocalContext.current
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

    val hygieneSummary = remember(aliases) {
        runCatching {
            val rawArr = JSONArray().apply { aliases.forEach { put(it.rawJson) } }.toString()
            val res = JSONObject(MailxRustBridge.nativeAuditAliasHygiene(rawArr))
            res.optJSONObject("data")
        }.getOrNull()
    }

    val privacyScore = posturePair.first
    val privacyGrade = posturePair.second
    val scoreColor = when {
        privacyScore >= 82 -> MailxPalette.ForwardEmerald
        privacyScore >= 65 -> MaterialTheme.colorScheme.primary
        else -> MailxPalette.PausedAmber
    }

    // Instant Rust Alias & Passphrase Studio State
    var studioFormatIndex by remember { mutableIntStateOf(0) }
    var studioSeed by remember { mutableIntStateOf(0) }
    val studioFormats = remember { listOf("words" to "Words", "random" to "Random", "uuid" to "UUID") }
    val studioFormatKey = studioFormats[studioFormatIndex].first

    val studioData = remember(studioFormatKey, defaultDomain, studioSeed) {
        runCatching {
            val res = JSONObject(
                MailxRustBridge.nativeGeneratePassphraseAndCandidate(
                    studioFormatKey,
                    defaultDomain.ifBlank { "mailx.net" },
                    4
                )
            )
            res.optJSONObject("data")
        }.getOrNull()
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Material 3 Expressive Hero Privacy Shield Card
            item(key = "hero_summary_card") {
                val heroGradient = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f),
                        Color.Transparent
                    )
                )

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                    ),
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(heroGradient)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(15.dp)
                        ) {
                            // Top Row: Domain Title + Interactive Shield Grade Pill
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        Text(
                                            text = "@$defaultDomain",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "RUST CORE",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "$activeCount active of ${aliases.size} aliases • $recipientCount recipient${if (recipientCount == 1) "" else "s"} • $blocklistCount blocked",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    onClick = { showPrivacyScoreDialog = true },
                                    shape = RoundedCornerShape(11.dp),
                                    color = scoreColor.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.38f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = MailxIcons.ShieldCheck,
                                            contentDescription = "Privacy Score",
                                            tint = scoreColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Shield $privacyGrade · $privacyScore",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = scoreColor
                                        )
                                    }
                                }
                            }

                            // Active Defense Status Pills
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DefenseStatusChip(
                                    label = if (pgpActive) "OpenPGP Active" else "OpenPGP Off",
                                    active = pgpActive,
                                    onClick = onNavigateToRecipients
                                )
                                DefenseStatusChip(
                                    label = if (blockTrackers) "Tracker Shield On" else "Tracker Shield Off",
                                    active = blockTrackers,
                                    onClick = onOpenTrackersReport
                                )
                                DefenseStatusChip(
                                    label = "$ruleCount Active Rule${if (ruleCount == 1) "" else "s"}",
                                    active = ruleCount > 0,
                                    onClick = onNavigateToRules
                                )
                                val highSpamCount = hygieneSummary?.optInt("high_block_ratio_count", 0) ?: 0
                                if (highSpamCount > 0) {
                                    Surface(
                                        onClick = onOpenPrivacyLab,
                                        shape = RoundedCornerShape(8.dp),
                                        color = MailxPalette.BlockedRose.copy(alpha = 0.15f),
                                        border = BorderStroke(0.75.dp, MailxPalette.BlockedRose.copy(alpha = 0.38f))
                                    ) {
                                        Text(
                                            text = "$highSpamCount High-Spam Alias",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MailxPalette.BlockedRose,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                                        )
                                    }
                                }
                            }

                            // Clean Horizontal Metric Strip + Activity Ring
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onNavigateToFilter("active") }
                                            .padding(vertical = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = totalForwards.toString(),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(MailxPalette.ForwardEmerald)
                                            )
                                            Text(
                                                text = "Forwarded",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .padding(vertical = 4.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    )

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onNavigateToFilter("active_inactive") }
                                            .padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = totalBlocks.toString(),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (totalBlocks > 0) MailxPalette.BlockedRose else MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (totalBlocks > 0) MailxPalette.BlockedRose else MailxPalette.Slate500
                                                    )
                                            )
                                            Text(
                                                text = "Blocked",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .padding(vertical = 4.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    )

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(onClick = onOpenReverseAliasTool)
                                            .padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = (totalReplies + totalSends).toString(),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$totalReplies rep · $totalSends sent",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    MailxActivityRing(
                                        forwards = totalForwards,
                                        replies = totalReplies,
                                        sends = totalSends,
                                        blocks = totalBlocks,
                                        enabled = true,
                                        size = 42.dp,
                                        strokeWidth = 4.5.dp,
                                        showCenterText = false
                                    )
                                }

                                MailxTelemetryBar(
                                    forwards = totalForwards,
                                    replies = totalReplies,
                                    sends = totalSends,
                                    blocks = totalBlocks
                                )
                            }

                            // Action Row: Primary New Alias + Send Mail + Rust Privacy Lab
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onOpenCreateAlias,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(42.dp)
                                ) {
                                    Icon(
                                        imageVector = MailxIcons.Plus,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("New Alias", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }

                                FilledTonalButton(
                                    onClick = onOpenReverseAliasTool,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                ) {
                                    Icon(
                                        imageVector = MailxIcons.Send,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Send Mail", fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                }

                                FilledTonalButton(
                                    onClick = onOpenPrivacyLab,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                                        contentColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                ) {
                                    Icon(
                                        imageVector = MailxIcons.Sparkles,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Privacy Lab", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // 2. NEW FEATURE: Instant Rust Alias & Passphrase Studio Card
            if (studioData != null) {
                item(key = "rust_instant_studio_card") {
                    val candidateEmail = studioData.optString("full_address", "")
                    val aliasEntropy = studioData.optInt("alias_entropy_bits", 34)
                    val passphrase = studioData.optString("passphrase", "")
                    val passphraseEntropy = studioData.optInt("passphrase_entropy_bits", 52)

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                        ),
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = MailxIcons.Cpu,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = "Instant Alias & Passphrase Studio",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Generated offline in Rust • $aliasEntropy-bit alias & $passphraseEntropy-bit passphrase",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { studioSeed++ },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = MailxIcons.RefreshCw,
                                        contentDescription = "Regenerate in Rust",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            MailxSegmentedControl(
                                options = studioFormats.map { it.second },
                                selectedIndex = studioFormatIndex,
                                onSelect = { studioFormatIndex = it },
                                compact = true
                            )

                            // Candidate Alias Row
                            Surface(
                                onClick = {
                                    copyToClipboard(
                                        context,
                                        "Rust Alias Candidate",
                                        candidateEmail,
                                        "Alias candidate copied: $candidateEmail"
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = 0.75.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ALIAS CANDIDATE",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = candidateEmail,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = MailxIcons.Copy,
                                        contentDescription = "Copy Alias Candidate",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            // Cryptographic Passphrase Row
                            Surface(
                                onClick = {
                                    copyToClipboard(
                                        context,
                                        "Rust Passphrase",
                                        passphrase,
                                        "Passphrase copied ($passphraseEntropy-bit entropy)"
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = 0.75.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "CRYPTOGRAPHIC PASSPHRASE ($passphraseEntropy BITS)",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = passphrase,
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = MailxIcons.Copy,
                                        contentDescription = "Copy Passphrase",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. 7-Day Telemetry Chart (only when activity exists)
            if (userStats.dailyPoints.any { it.total > 0 }) {
                item(key = "mailx_7day_chart") {
                    MailxSevenDayChartCard(userStats = userStats)
                }
            }

            // 4. Pinned & Recent Aliases Section
            item(key = "header_spotlight_aliases") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        MailxModernDialog(
            title = "Privacy Score: $privacyScore/100 ($privacyGrade)",
            subtitle = "Your Privacy Protection Score is computed in Rust from active shielding and alias hygiene:",
            icon = MailxIcons.ShieldCheck,
            iconTint = scoreColor,
            confirmText = "Open Privacy Lab",
            onConfirm = {
                showPrivacyScoreDialog = false
                onOpenPrivacyLab()
            },
            dismissText = "Close",
            onDismiss = { showPrivacyScoreDialog = false }
        ) {
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
    }
}

@Composable
private fun DefenseStatusChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val color = if (active) MailxPalette.ForwardEmerald else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (active) {
            MailxPalette.ForwardEmerald.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.70f)
        },
        border = BorderStroke(
            width = 0.75.dp,
            color = if (active) {
                MailxPalette.ForwardEmerald.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.5.dp)
                    .clip(CircleShape)
                    .background(if (active) MailxPalette.ForwardEmerald else MailxPalette.Slate500)
            )
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
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
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
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
                        Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
private fun MailxSevenDayChartCard(userStats: UiUserStats) {
    val maxDayTotal = remember(userStats.dailyPoints) {
        (userStats.dailyPoints.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
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
                    text = "7-Day Relay Telemetry",
                    fontSize = 13.5.sp,
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
