@file:Suppress("OPT_IN_IS_NOT_ENABLED", "EXPERIMENTAL_IS_NOT_ENABLED", "UNNECESSARY_OPT_IN")

package net.mailx.android.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import net.mailx.android.ui.theme.LocalReducedMotion
import net.mailx.android.ui.theme.MailxIcons
import net.mailx.android.ui.theme.MailxPalette
import org.json.JSONArray
import org.json.JSONObject

private fun copyToClipboard(context: Context, label: String, text: String, toastMsg: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardTab(
    aliases: List<UiAlias>,
    userStats: UiUserStats = UiUserStats(),
    isLoading: Boolean = false,
    defaultDomain: String,
    recipientCount: Int = 1,
    ruleCount: Int = 0,
    blocklistCount: Int = 0,
    blockTrackers: Boolean = true,
    removeHeader: Boolean = false,
    pgpActive: Boolean = false,
    onRefresh: () -> Unit = {},
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

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val activeCount = remember(aliases) { aliases.count { it.enabled && it.deletedAt.isBlank() } }
    val pinnedCount = remember(aliases) { aliases.count { it.pinned && it.deletedAt.isBlank() } }

    val totalForwards = remember(aliases, userStats) {
        maxOf(userStats.forwards, aliases.sumOf { it.forwards })
    }
    val totalBlocks = remember(aliases, userStats) {
        maxOf(userStats.blocks, aliases.sumOf { it.blocks })
    }
    val totalReplies = remember(aliases, userStats) {
        maxOf(userStats.replies, aliases.sumOf { it.replies })
    }
    val totalSends = remember(aliases, userStats) {
        maxOf(userStats.sends, aliases.sumOf { it.sends })
    }
    val totalOutbound = totalReplies + totalSends

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
            val score = data?.optInt("score", 82) ?: 82
            val grade = data?.optString("grade", "A") ?: "A"
            score to grade
        }.getOrDefault(82 to "A")
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
    val postureDotColor = when {
        privacyScore >= 75 -> MailxPalette.ForwardEmerald
        else -> MailxPalette.PausedAmber
    }

    // Animated counters
    val animatedScore by animateIntAsState(
        targetValue = if (entered) privacyScore else 0,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "anim_score"
    )
    val animatedForwards by animateIntAsState(
        targetValue = if (entered) totalForwards else 0,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 450),
        label = "anim_fwd"
    )
    val animatedBlocks by animateIntAsState(
        targetValue = if (entered) totalBlocks else 0,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 450),
        label = "anim_blk"
    )
    val animatedOutbound by animateIntAsState(
        targetValue = if (entered) totalOutbound else 0,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 450),
        label = "anim_out"
    )
    val animatedActive by animateIntAsState(
        targetValue = if (entered) activeCount else 0,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 450),
        label = "anim_active"
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
            dampingRatio = Spring.DampingRatioNoBouncy,
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
            // 1. Unified Relay Overview & Telemetry Card (Calm Monochrome Zinc Hierarchy)
            item(key = "relay_overview_card") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    ),
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Top Row: Relay Summary + Posture Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Relay Overview",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$activeCount of ${aliases.size} active • $recipientCount recipient${if (recipientCount == 1) "" else "s"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                onClick = { showPrivacyScoreSheet = true },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    width = 0.75.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.5.dp)
                                            .clip(CircleShape)
                                            .background(postureDotColor)
                                    )
                                    Text(
                                        text = "Score $animatedScore · $privacyGrade",
                                        fontSize = 11.5.sp,
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

                        // Middle 4-Column Architectural Telemetry Strip
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                width = 0.75.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompactTelemetryColumn(
                                    value = animatedForwards.toString(),
                                    label = "Forwarded",
                                    onClick = { onNavigateToFilter("active") },
                                    modifier = Modifier.weight(1f)
                                )
                                VerticalMetricDivider()
                                CompactTelemetryColumn(
                                    value = animatedBlocks.toString(),
                                    label = "Blocked",
                                    onClick = onNavigateToRules,
                                    modifier = Modifier.weight(1f)
                                )
                                VerticalMetricDivider()
                                CompactTelemetryColumn(
                                    value = animatedOutbound.toString(),
                                    label = "Sent",
                                    onClick = onOpenReverseAliasTool,
                                    modifier = Modifier.weight(1f)
                                )
                                VerticalMetricDivider()
                                CompactTelemetryColumn(
                                    value = animatedActive.toString(),
                                    label = "Active",
                                    onClick = { onNavigateToFilter("active_inactive") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Dual Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenCreateAlias,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.5.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = MailxIcons.Plus,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "New Alias",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            FilledTonalButton(
                                onClick = onOpenReverseAliasTool,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.5.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = MailxIcons.Send,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Send Mail",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Compact 3-Column Defense Status Strip
            item(key = "defense_status_row") {
                val highSpamCount = hygieneSummary?.optInt("high_block_ratio_count", 0) ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DefensePillTile(
                        title = "OpenPGP",
                        status = if (pgpActive) "Encrypted" else "Off",
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

            // 3. Offline Rust Quick Identity & Passphrase Studio
            item(key = "rust_instant_studio") {
                val candObj = studioData?.optJSONObject("alias_candidate")
                val candidateAddr = studioData?.optString("full_address", "")
                    ?.takeIf { it.isNotBlank() }
                    ?: candObj?.optString("address", "").orEmpty()
                val candidateEntropy = studioData?.optDouble("alias_entropy_bits", 0.0)
                    ?.takeIf { it > 0.0 }
                    ?: candObj?.optDouble("entropy_bits", 0.0)
                    ?: 0.0
                val passphrase = studioData?.optString("passphrase", "").orEmpty()
                val passphraseEntropy = studioData?.optDouble("passphrase_entropy_bits", 0.0) ?: 0.0

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                    ),
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
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
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = MailxIcons.Cpu,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Quick Identity & Passphrase",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
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
                                        .size(15.dp)
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
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = 0.75.dp,
                                    color = if (copiedAlias) {
                                        MaterialTheme.colorScheme.outline
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
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
                                            text = if (copiedAlias) "COPIED TO CLIPBOARD" else "ALIAS CANDIDATE (${"%.0f".format(candidateEntropy)} BITS)",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.6.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            MaterialTheme.colorScheme.onSurface
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
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = 0.75.dp,
                                    color = if (copiedPassphrase) {
                                        MaterialTheme.colorScheme.outline
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
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
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.6.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                fontWeight = FontWeight.Medium,
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
                                            MaterialTheme.colorScheme.onSurface
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

            // 4. 7-Day Activity Chart (when data is present)
            if (userStats.sevenDayForwards.sum() + userStats.sevenDayBlocks.sum() > 0) {
                item(key = "seven_day_chart") {
                    MailxSevenDayChartCard(userStats = userStats)
                }
            }

            // 5. Pinned & Recent Aliases Section
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
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "$privacyGrade • $privacyScore/100",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
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
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
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
private fun CompactTelemetryColumn(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VerticalMetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    )
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
        isActive -> MailxPalette.ForwardEmerald
        else -> MailxPalette.Slate500
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        ),
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
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
    val dotColor = if (passed) MailxPalette.ForwardEmerald else MailxPalette.PausedAmber
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
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
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (passed) MailxIcons.Check else MailxIcons.ShieldCheck,
                        contentDescription = null,
                        tint = dotColor,
                        modifier = Modifier.size(14.dp)
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
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
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
                    .height(64.dp),
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
