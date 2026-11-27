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
import net.mailx.android.ui.components.MailxPrimaryButton
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxSegmentedControl
import net.mailx.android.ui.components.MailxTonalButton
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

    val homeMetrics = remember(aliases, userStats) {
        var active = 0
        var pinned = 0
        var fw = 0
        var blk = 0
        var rep = 0
        var snd = 0
        for (alias in aliases) {
            if (alias.deletedAt.isBlank()) {
                if (alias.enabled) active++
                if (alias.pinned) pinned++
            }
            fw += alias.forwards
            blk += alias.blocks
            rep += alias.replies
            snd += alias.sends
        }
        intArrayOf(
            active,
            pinned,
            maxOf(userStats.forwards, fw),
            maxOf(userStats.blocks, blk),
            maxOf(userStats.replies, rep),
            maxOf(userStats.sends, snd)
        )
    }
    val activeCount = homeMetrics[0]
    val pinnedCount = homeMetrics[1]
    val totalForwards = homeMetrics[2]
    val totalBlocks = homeMetrics[3]
    val totalReplies = homeMetrics[4]
    val totalSends = homeMetrics[5]
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
        privacyScore >= 75 -> MailxPalette.ForwardedGreen
        else -> MailxPalette.PausedAmber
    }

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

    val trafficTotal = (totalForwards + totalBlocks + totalOutbound).coerceAtLeast(1)
    val fwdWeight = (totalForwards.toFloat() / trafficTotal.toFloat()).coerceAtLeast(0.15f)
    val blkWeight = (totalBlocks.toFloat() / trafficTotal.toFloat()).coerceAtLeast(0.08f)
    val outWeight = (totalOutbound.toFloat() / trafficTotal.toFloat()).coerceAtLeast(0.08f)

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
            item(key = "relay_overview_card") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = MailxIcons.ShieldCheck,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Relay Activity",
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$activeCount of ${aliases.size} active • $recipientCount recipient${if (recipientCount == 1) "" else "s"}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                onClick = { showPrivacyScoreSheet = true },
                                shape = RoundedCornerShape(50),
                                color = postureDotColor.copy(alpha = 0.14f),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = postureDotColor.copy(alpha = 0.38f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(postureDotColor)
                                    )
                                    Text(
                                        text = "$animatedScore · $privacyGrade",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = MailxIcons.ChevronRight,
                                        contentDescription = null,
                                        tint = postureDotColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(fwdWeight)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(MailxPalette.ForwardedGreen)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(blkWeight)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(MailxPalette.BlockedRose)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(outWeight)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(MailxPalette.ReplySky)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ExpressiveMetricTile(
                                    value = animatedForwards.toString(),
                                    label = "Forwarded",
                                    icon = MailxIcons.MailCheck,
                                    accentColor = MailxPalette.ForwardedGreen,
                                    onClick = { onNavigateToFilter("active") },
                                    modifier = Modifier.weight(1f)
                                )
                                ExpressiveMetricTile(
                                    value = animatedBlocks.toString(),
                                    label = "Blocked",
                                    icon = MailxIcons.Shield,
                                    accentColor = MailxPalette.BlockedRose,
                                    onClick = onNavigateToRules,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ExpressiveMetricTile(
                                    value = animatedOutbound.toString(),
                                    label = "Sent & Replied",
                                    icon = MailxIcons.Send,
                                    accentColor = MailxPalette.ReplySky,
                                    onClick = onOpenReverseAliasTool,
                                    modifier = Modifier.weight(1f)
                                )
                                ExpressiveMetricTile(
                                    value = animatedActive.toString(),
                                    label = "Active Aliases",
                                    icon = MailxIcons.AtSign,
                                    accentColor = MailxPalette.SentSlate,
                                    onClick = { onNavigateToFilter("active_inactive") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MailxPrimaryButton(
                                text = "New Alias",
                                icon = MailxIcons.Plus,
                                onClick = onOpenCreateAlias,
                                modifier = Modifier.weight(1f),
                                height = 46.dp
                            )

                            MailxTonalButton(
                                text = "Send Mail",
                                icon = MailxIcons.Send,
                                accentColor = MailxPalette.ReplySky,
                                onClick = onOpenReverseAliasTool,
                                modifier = Modifier.weight(1f),
                                height = 46.dp
                            )
                        }
                    }
                }
            }

            item(key = "defense_status_row") {
                val highSpamCount = hygieneSummary?.optInt("high_block_ratio_count", 0) ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DefensePillTile(
                        title = "OpenPGP",
                        status = if (pgpActive) "Encrypted" else "Off",
                        icon = MailxIcons.Lock,
                        accentColor = MailxPalette.ReplySky,
                        isActive = pgpActive,
                        onClick = onNavigateToRecipients,
                        modifier = Modifier.weight(1f)
                    )
                    DefensePillTile(
                        title = "Trackers",
                        status = if (blockTrackers) "Shield On" else "Off",
                        icon = MailxIcons.ShieldCheck,
                        accentColor = MailxPalette.ForwardedGreen,
                        isActive = blockTrackers,
                        onClick = onOpenTrackersReport,
                        modifier = Modifier.weight(1f)
                    )
                    DefensePillTile(
                        title = "Rules",
                        status = if (highSpamCount > 0) "$highSpamCount Spam" else "$ruleCount Active",
                        icon = MailxIcons.SlidersHorizontal,
                        accentColor = if (highSpamCount > 0) MailxPalette.BlockedRose else MailxPalette.BrandTertiaryNeutralDark,
                        isActive = ruleCount > 0,
                        isWarning = highSpamCount > 0,
                        onClick = if (highSpamCount > 0) onOpenPrivacyLab else onNavigateToRules,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

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
                    shape = RoundedCornerShape(24.dp),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MailxPalette.BrandTertiaryNeutralDark.copy(alpha = 0.16f),
                                    border = BorderStroke(0.75.dp, MailxPalette.BrandTertiaryNeutralDark.copy(alpha = 0.35f)),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = MailxIcons.Cpu,
                                            contentDescription = null,
                                            tint = MailxPalette.BrandTertiaryNeutralDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Quick Identity & Passphrase",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Offline Rust CSPRNG • ${"%.0f".format(candidateEntropy)}-bit entropy",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                onClick = { studioSeed++ },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = MailxIcons.RefreshCw,
                                        contentDescription = "Regenerate",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(15.dp)
                                            .graphicsLayer { rotationZ = spinAngle }
                                    )
                                }
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
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (copiedAlias) {
                                        MailxPalette.ForwardedGreen.copy(alpha = 0.65f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
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
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp,
                                            color = if (copiedAlias) MailxPalette.ForwardedGreen else MaterialTheme.colorScheme.primary
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
                                    Surface(
                                        shape = CircleShape,
                                        color = if (copiedAlias) {
                                            MailxPalette.ForwardedGreen.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (copiedAlias) MailxIcons.Check else MailxIcons.Copy,
                                                contentDescription = "Copy Candidate",
                                                tint = if (copiedAlias) {
                                                    MailxPalette.ForwardedGreen
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (passphrase.isNotBlank()) {
                            Surface(
                                onClick = {
                                    copiedPassphrase = true
                                    copyToClipboard(context, "Passphrase", passphrase, "Passphrase copied to clipboard")
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (copiedPassphrase) {
                                        MailxPalette.ForwardedGreen.copy(alpha = 0.65f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
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
                                            color = if (copiedPassphrase) MailxPalette.ForwardedGreen else MailxPalette.ReplySky
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
                                    Surface(
                                        shape = CircleShape,
                                        color = if (copiedPassphrase) {
                                            MailxPalette.ForwardedGreen.copy(alpha = 0.18f)
                                        } else {
                                            MailxPalette.ReplySky.copy(alpha = 0.16f)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (copiedPassphrase) MailxIcons.Check else MailxIcons.Copy,
                                                contentDescription = "Copy Passphrase",
                                                tint = if (copiedPassphrase) {
                                                    MailxPalette.ForwardedGreen
                                                } else {
                                                    MailxPalette.ReplySky
                                                },
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (userStats.sevenDayForwards.sum() + userStats.sevenDayBlocks.sum() > 0) {
                item(key = "seven_day_chart") {
                    MailxSevenDayChartCard(userStats = userStats)
                }
            }

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
                items(spotlightAliases, key = { "home_${it.id}" }, contentType = { "alias_card" }) { alias ->
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

    if (showPrivacyScoreSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPrivacyScoreSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
                        shape = RoundedCornerShape(50),
                        color = postureDotColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, postureDotColor.copy(alpha = 0.40f))
                    ) {
                        Text(
                            text = "$privacyGrade • $privacyScore/100",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = postureDotColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
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

                MailxPrimaryButton(
                    text = "Open Rust Privacy & Security Lab",
                    icon = MailxIcons.Sparkles,
                    onClick = {
                        showPrivacyScoreSheet = false
                        onOpenPrivacyLab()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ExpressiveMetricTile(
    value: String,
    label: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = 0.16f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = value,
                    fontSize = 17.sp,
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
    }
}

@Composable
private fun DefensePillTile(
    title: String,
    status: String,
    icon: ImageVector,
    accentColor: Color,
    isActive: Boolean,
    isWarning: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAccent = when {
        isWarning -> MailxPalette.BlockedRose
        isActive -> accentColor
        else -> MailxPalette.Slate500
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = activeAccent.copy(alpha = if (isActive || isWarning) 0.35f else 0.20f)
        ),
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = activeAccent.copy(alpha = 0.16f),
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = activeAccent,
                            modifier = Modifier.size(11.5.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = status,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive || isWarning) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
    val dotColor = if (passed) MailxPalette.ForwardedGreen else MailxPalette.PausedAmber
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = dotColor.copy(alpha = 0.30f)
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
                color = dotColor.copy(alpha = 0.16f),
                modifier = Modifier.size(30.dp)
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
        shape = RoundedCornerShape(22.dp),
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
                    text = "7-Day Relay Activity",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChartLegendDot(color = MailxPalette.ForwardedGreen, label = "Forwarded")
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
                    val heightFrac = (dayTotal.toFloat() / maxDay.toFloat()).coerceIn(0.10f, 1f)
                    val lbl = labels.getOrElse(i) { "" }.take(3)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .weight(1f),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(heightFrac)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        when {
                                            b > f -> MailxPalette.BlockedRose
                                            dayTotal > 0 -> MailxPalette.ForwardedGreen
                                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                        }
                                    )
                            )
                        }
                        Text(
                            text = lbl,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
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
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
