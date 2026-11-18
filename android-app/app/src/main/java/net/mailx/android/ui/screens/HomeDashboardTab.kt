package net.mailx.android.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.components.AliasCard
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.components.MailxAliasSkeletonList
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxSegmentedControl
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.UiUserStats
import net.mailx.android.ui.model.copyToClipboard
import net.mailx.android.ui.theme.LocalReducedMotion
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
    val reducedMotion = LocalReducedMotion.current
    var showPrivacyScoreSheet by remember { mutableStateOf(false) }

    // Entrance animation trigger
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val aliasForwards = remember(aliases) { aliases.sumOf { it.forwards } }
    val aliasBlocks = remember(aliases) { aliases.sumOf { it.blocks } }
    val aliasReplies = remember(aliases) { aliases.sumOf { it.replies } }
    val aliasSends = remember(aliases) { aliases.sumOf { it.sends } }

    val totalForwards = maxOf(aliasForwards, userStats.forwards)
    val totalBlocks = maxOf(aliasBlocks, userStats.blocks)
    val totalReplies = maxOf(aliasReplies, userStats.replies)
    val totalSends = maxOf(aliasSends, userStats.sends)
    val totalOutbound = totalReplies + totalSends

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
        privacyScore >= 80 -> MaterialTheme.colorScheme.primary
        privacyScore >= 60 -> MailxPalette.BrandAccentTealDark
        else -> MailxPalette.PausedAmber
    }

    // Animated counters
    val animatedScore by animateIntAsState(
        targetValue = if (entered) privacyScore else 0,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "anim_score"
    )
    val animatedGaugeProgress by animateFloatAsState(
        targetValue = if (entered) (privacyScore.coerceIn(0, 100) / 100f) else 0f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "anim_gauge"
    )
    val animatedForwards by animateIntAsState(
        targetValue = if (entered) totalForwards else 0,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 500),
        label = "anim_fwd"
    )
    val animatedBlocks by animateIntAsState(
        targetValue = if (entered) totalBlocks else 0,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 500),
        label = "anim_blk"
    )
    val animatedOutbound by animateIntAsState(
        targetValue = if (entered) totalOutbound else 0,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 500),
        label = "anim_out"
    )

    // Instant Rust Alias & Passphrase Studio State
    var studioFormatIndex by remember { mutableIntStateOf(0) }
    var studioSeed by remember { mutableIntStateOf(0) }
    var copiedAlias by remember { mutableStateOf(false) }
    var copiedPassphrase by remember { mutableStateOf(false) }

    LaunchedEffect(copiedAlias) {
        if (copiedAlias) {
            delay(1300)
            copiedAlias = false
        }
    }
    LaunchedEffect(copiedPassphrase) {
        if (copiedPassphrase) {
            delay(1300)
            copiedPassphrase = false
        }
    }

    val spinAngle by animateFloatAsState(
        targetValue = studioSeed * 180f,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "studio_spin"
    )

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Clean Bento Hero Card: Animated Radial Shield Gauge + Relay Summary + Dual Actions
            item(key = "bento_hero_card") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
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
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Animated Circular Shield Gauge
                            Box(
                                modifier = Modifier
                                    .size(74.dp)
                                    .clip(CircleShape)
                                    .clickable { showPrivacyScoreSheet = true },
                                contentAlignment = Alignment.Center
                            ) {
                                val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
                                    val strokePx = 6.dp.toPx()
                                    val arcSize = Size(size.width, size.height)
                                    drawArc(
                                        color = trackColor,
                                        startAngle = 135f,
                                        sweepAngle = 270f,
                                        useCenter = false,
                                        topLeft = Offset.Zero,
                                        size = arcSize,
                                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = scoreColor,
                                        startAngle = 135f,
                                        sweepAngle = 270f * animatedGaugeProgress,
                                        useCenter = false,
                                        topLeft = Offset.Zero,
                                        size = arcSize,
                                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = animatedScore.toString(),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "GRADE $privacyGrade",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = scoreColor
                                    )
                                }
                            }

                            // Relay Status Summary (Never truncates!)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Text(
                                        text = "Relay Shield",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "RUST",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.5.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "$activeCount of ${aliases.size} aliases active • $recipientCount recipient${if (recipientCount == 1) "" else "s"}",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    onClick = { showPrivacyScoreSheet = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = MailxIcons.ShieldCheck,
                                            contentDescription = null,
                                            tint = scoreColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "View Security Posture",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = MailxIcons.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Dual Primary Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenCreateAlias,
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = MailxIcons.Plus,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = "New Alias",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            FilledTonalButton(
                                onClick = onOpenReverseAliasTool,
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = MailxIcons.Send,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = "Send Mail",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 2. 2x2 Bento Telemetry & Security Grid
            item(key = "bento_telemetry_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BentoMetricTile(
                            label = "Forwarded",
                            value = animatedForwards.toString(),
                            subtitle = "$activeCount active aliases",
                            icon = MailxIcons.Inbox,
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = { onNavigateToFilter("active") },
                            modifier = Modifier.weight(1f)
                        )
                        BentoMetricTile(
                            label = "Blocked",
                            value = animatedBlocks.toString(),
                            subtitle = "$blocklistCount blocklist entries",
                            icon = MailxIcons.ShieldCheck,
                            accentColor = if (totalBlocks > 0) MailxPalette.BlockedRose else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onNavigateToRules,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BentoMetricTile(
                            label = "Sent & Replied",
                            value = animatedOutbound.toString(),
                            subtitle = "${totalReplies} rep • ${totalSends} sent",
                            icon = MailxIcons.Send,
                            accentColor = MailxPalette.ReplySky,
                            onClick = onOpenReverseAliasTool,
                            modifier = Modifier.weight(1f)
                        )
                        BentoMetricTile(
                            label = "Privacy Lab",
                            value = "5 Tools",
                            subtitle = "URL, MIME & Phishing",
                            icon = MailxIcons.Sparkles,
                            accentColor = MaterialTheme.colorScheme.primary,
                            onClick = onOpenPrivacyLab,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Compact 3-Column Defense Status Bar
            item(key = "defense_status_row") {
                val highSpamCount = hygieneSummary?.optInt("high_block_ratio_count", 0) ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DefensePillTile(
                        title = "OpenPGP",
                        status = if (pgpActive) "Active" else "Off",
                        isActive = pgpActive,
                        onClick = onNavigateToRecipients,
                        modifier = Modifier.weight(1f)
                    )
                    DefensePillTile(
                        title = "Trackers",
                        status = if (blockTrackers) "Shield On" else "Off",
                        isActive = blockTrackers,
                        onClick = onOpenTrackersReport,
                        modifier = Modifier.weight(1f)
                    )
                    DefensePillTile(
                        title = "Rules",
                        status = if (highSpamCount > 0) "$highSpamCount Spam" else "$ruleCount Active",
                        isActive = ruleCount > 0,
                        isWarning = highSpamCount > 0,
                        onClick = if (highSpamCount > 0) onOpenPrivacyLab else onNavigateToRules,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. Redesigned Rust Quick Alias & Passphrase Studio (Never truncates!)
            item(key = "rust_instant_studio") {
                val candObj = studioData?.optJSONObject("alias_candidate")
                val candidateAddr = candObj?.optString("address", "").orEmpty()
                val candidateEntropy = candObj?.optDouble("entropy_bits", 0.0) ?: 0.0
                val passphrase = studioData?.optString("passphrase", "").orEmpty()
                val passphraseEntropy = studioData?.optDouble("passphrase_entropy_bits", 0.0) ?: 0.0

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = MailxIcons.Cpu,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Quick Identity & Passphrase",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Offline Rust CSPRNG • ${"%.0f".format(candidateEntropy)}-bit alias",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = { studioSeed++ },
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = MailxIcons.RefreshCw,
                                    contentDescription = "Regenerate",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .graphicsLayer { rotationZ = spinAngle }
                                )
                            }
                        }

                        MailxSegmentedControl(
                            options = studioFormats.map { it.second },
                            selectedIndex = studioFormatIndex,
                            onSelect = { studioFormatIndex = it },
                            compact = true
                        )

                        if (candidateAddr.isNotBlank()) {
                            Surface(
                                onClick = {
                                    copiedAlias = true
                                    copyToClipboard(context, "Alias Candidate", candidateAddr, "Copied alias candidate")
                                },
                                shape = RoundedCornerShape(13.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = 0.75.dp,
                                    color = if (copiedAlias) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 13.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = if (copiedAlias) "COPIED TO CLIPBOARD" else "SAMPLE ALIAS CANDIDATE",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp,
                                            color = if (copiedAlias) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        AnimatedContent(
                                            targetState = candidateAddr,
                                            transitionSpec = {
                                                (fadeIn(tween(150)) + slideInVertically { it / 3 })
                                                    .togetherWith(fadeOut(tween(110)) + slideOutVertically { -it / 3 })
                                            },
                                            label = "cand_addr_anim"
                                        ) { addr ->
                                            Text(
                                                text = addr,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = if (copiedAlias) MailxIcons.Check else MailxIcons.Copy,
                                        contentDescription = "Copy Candidate",
                                        tint = if (copiedAlias) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (passphrase.isNotBlank()) {
                            Surface(
                                onClick = {
                                    copiedPassphrase = true
                                    copyToClipboard(context, "Passphrase", passphrase, "Passphrase copied to clipboard")
                                },
                                shape = RoundedCornerShape(13.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = 0.75.dp,
                                    color = if (copiedPassphrase) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 13.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = if (copiedPassphrase) {
                                                "COPIED TO CLIPBOARD"
                                            } else {
                                                "PASSPHRASE (${"%.0f".format(passphraseEntropy)} BITS)"
                                            },
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp,
                                            color = if (copiedPassphrase) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        AnimatedContent(
                                            targetState = passphrase,
                                            transitionSpec = {
                                                (fadeIn(tween(150)) + slideInVertically { it / 3 })
                                                    .togetherWith(fadeOut(tween(110)) + slideOutVertically { -it / 3 })
                                            },
                                            label = "passphrase_anim"
                                        ) { phrase ->
                                            Text(
                                                text = phrase,
                                                fontSize = 12.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 17.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = if (copiedPassphrase) MailxIcons.Check else MailxIcons.Copy,
                                        contentDescription = "Copy Passphrase",
                                        tint = if (copiedPassphrase) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. 7-Day Activity Chart (when data is present)
            if (userStats.sevenDayForwards.sum() + userStats.sevenDayBlocks.sum() > 0) {
                item(key = "seven_day_chart") {
                    MailxSevenDayChartCard(userStats = userStats)
                }
            }

            // 6. Pinned & Recent Aliases Section
            item(key = "spotlight_header") {
                MailxSectionHeader(
                    title = if (pinnedCount > 0) "Pinned & Active Aliases" else "Recent Aliases",
                    countBadge = spotlightAliases.size,
                    actionText = "See All",
                    onActionClick = { onNavigateToFilter("active_inactive") }
                )
            }

            if (isLoading && aliases.isEmpty()) {
                item(key = "skeleton_aliases") {
                    MailxAliasSkeletonList(count = 3)
                }
            } else if (spotlightAliases.isEmpty()) {
                item(key = "empty_aliases") {
                    EmptyStateBox(
                        icon = MailxIcons.AtSign,
                        title = "No aliases yet",
                        subtitle = "Create your first disposable or custom alias to start protecting your inbox.",
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
                        onToggleEnabled = { onToggleEnabled(alias, it) }
                    )
                }
            }
        }
    }

    // Sleek Bottom Sheet for Privacy Posture & Rust Hygiene Audit
    if (showPrivacyScoreSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPrivacyScoreSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Privacy Posture & Hygiene",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Evaluated locally by the Rust security engine",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = scoreColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.40f))
                    ) {
                        Text(
                            text = "$privacyGrade • $privacyScore/100",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                PrivacyScoreCheckRow(
                    title = "Spy Pixel & Tracker Stripping",
                    subtitle = if (blockTrackers) {
                        "Active — known spy pixels and tracking links are stripped before forwarding"
                    } else {
                        "Disabled — enable tracker stripping in Settings"
                    },
                    passed = blockTrackers
                )
                PrivacyScoreCheckRow(
                    title = "OpenPGP Recipient Encryption",
                    subtitle = if (pgpActive) {
                        "Active — forwarded messages are encrypted with your public key"
                    } else {
                        "Not configured — add an OpenPGP public key in Recipients"
                    },
                    passed = pgpActive
                )
                PrivacyScoreCheckRow(
                    title = "Inbound Routing Rules",
                    subtitle = if (ruleCount > 0) {
                        "$ruleCount active rule(s) filtering incoming mail"
                    } else {
                        "No active routing rules configured"
                    },
                    passed = ruleCount > 0
                )
                PrivacyScoreCheckRow(
                    title = "Relay Header Stripping",
                    subtitle = if (removeHeader) {
                        "Relay banner header is stripped from forwarded emails"
                    } else {
                        "Relay banner header is included in forwarded emails"
                    },
                    passed = removeHeader
                )

                FilledTonalButton(
                    onClick = {
                        showPrivacyScoreSheet = false
                        onOpenPrivacyLab()
                    },
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = MailxIcons.Sparkles,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Rust Privacy & Security Lab", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BentoMetricTile(
    label: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
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
private fun DefensePillTile(
    title: String,
    status: String,
    isActive: Boolean,
    isWarning: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = when {
        isWarning -> MailxPalette.BlockedRose
        isActive -> MaterialTheme.colorScheme.primary
        else -> MailxPalette.Slate500
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (isWarning) {
                MailxPalette.BlockedRose.copy(alpha = 0.40f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = status,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isWarning) MailxPalette.BlockedRose else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PrivacyScoreCheckRow(
    title: String,
    subtitle: String,
    passed: Boolean
) {
    val tint = if (passed) MaterialTheme.colorScheme.primary else MailxPalette.PausedAmber
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.14f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (passed) MailxIcons.Check else MailxIcons.ShieldCheck,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MailxSevenDayChartCard(userStats: UiUserStats) {
    val labels: List<String> = userStats.sevenDayLabels.ifEmpty {
        listOf("M", "T", "W", "T", "F", "S", "S")
    }
    val fwd: List<Int> = userStats.sevenDayForwards
    val blk: List<Int> = userStats.sevenDayBlocks
    val maxDay: Int = remember(fwd, blk) {
        (0 until 7).maxOfOrNull { idx ->
            fwd.getOrElse(idx) { 0 } + blk.getOrElse(idx) { 0 }
        }?.coerceAtLeast(1) ?: 1
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
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
                    text = "7-Day Relay Activity",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChartLegendDot(color = MaterialTheme.colorScheme.primary, label = "Forwarded")
                    ChartLegendDot(color = MailxPalette.BlockedRose, label = "Blocked")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                for (i in 0 until 7) {
                    val f = fwd.getOrElse(i) { 0 }
                    val b = blk.getOrElse(i) { 0 }
                    val dayTotal = f + b
                    val heightFrac = (dayTotal.toFloat() / maxDay.toFloat()).coerceIn(0.08f, 1f)
                    val lbl = labels.getOrElse(i) { "" }.take(3)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .weight(1f),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(heightFrac)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when {
                                            b > f -> MailxPalette.BlockedRose
                                            dayTotal > 0 -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                        }
                                    )
                            )
                        }
                        Text(
                            text = lbl,
                            fontSize = 10.sp,
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
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
