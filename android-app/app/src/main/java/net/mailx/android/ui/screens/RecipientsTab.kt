package net.mailx.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.components.MailxGroupDivider
import net.mailx.android.ui.components.MailxModernDialog
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxSectionRow
import net.mailx.android.ui.components.MailxSegmentedControl
import net.mailx.android.ui.components.MailxSettingsGroupCard
import net.mailx.android.ui.components.mailxSwitchColors
import net.mailx.android.ui.components.mailxTextFieldColors
import net.mailx.android.ui.model.*
import net.mailx.android.ui.theme.MailxPalette
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipientsTab(
    aliases: List<UiAlias>,
    recipients: List<UiRecipient>,
    customDomains: List<UiCustomDomain>,
    dnsConfig: UiDnsConfig,
    dnsCheckResults: Map<String, List<UiDnsCheckResult>>,
    defaultsState: DefaultsUiState,
    onAddRecipient: (String) -> Unit,
    onSendRecipientOtp: (String) -> Unit,
    onVerifyRecipientOtp: (recipientId: String, otp: String) -> Unit,
    onUpdateRecipientPgp: (recipient: UiRecipient, pgpKey: String, pgpEnabled: Boolean, pgpInline: Boolean) -> Unit,
    onDeleteRecipient: (recipientId: String, fallbackRecipients: String) -> Unit,
    onAddCustomDomain: (domainName: String, onCreated: (UiCustomDomain?) -> Unit) -> Unit,
    onVerifyDomainDns: (domainId: String) -> Unit,
    onUpdateCustomDomain: (
        domain: UiCustomDomain,
        description: String,
        recipient: String,
        fromName: String,
        enabled: Boolean,
        catchAll: Boolean,
        createAlias: Boolean
    ) -> Unit,
    onDeleteCustomDomain: (domainId: String) -> Unit,
    onUpdateDefaultRecipient: (String) -> Unit,
    onUpdateDefaultDomain: (String) -> Unit,
    onUpdateDefaultFormat: (String) -> Unit,
    onSyncDefaults: () -> Unit
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableIntStateOf(0) }
    var showVerifiedOnly by remember { mutableStateOf(false) }
    var expandedRecipientId by remember { mutableStateOf<String?>(null) }

    var showAddRecipientDialog by remember { mutableStateOf(false) }
    var verifyingRecipient by remember { mutableStateOf<UiRecipient?>(null) }
    var pgpEditingRecipient by remember { mutableStateOf<UiRecipient?>(null) }
    var deletingRecipient by remember { mutableStateOf<UiRecipient?>(null) }

    var showAddDomainWizard by remember { mutableStateOf(false) }
    var inspectingDnsDomain by remember { mutableStateOf<UiCustomDomain?>(null) }
    var editingCustomDomain by remember { mutableStateOf<UiCustomDomain?>(null) }

    val displayedRecipients = remember(recipients, defaultsState.availableRecipients, showVerifiedOnly) {
        val base = if (recipients.isNotEmpty()) {
            recipients
        } else {
            defaultsState.availableRecipients.mapIndexed { idx, email ->
                UiRecipient(
                    id = "fallback_$idx",
                    createdAt = "",
                    email = email,
                    isActive = true,
                    pgpKey = "",
                    pgpEnabled = false,
                    pgpInline = false
                )
            }
        }
        if (showVerifiedOnly) base.filter { it.isActive } else base
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "recipients_domains_segmented") {
            MailxSegmentedControl(
                options = listOf("Recipients", "Domains"),
                selectedIndex = selectedSection,
                onSelect = { selectedSection = it },
                badges = listOf(
                    displayedRecipients.size,
                    customDomains.size + defaultsState.availableDomains.size
                )
            )
        }

        if (selectedSection == 0) {
            item(key = "recipients_top_filter_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !showVerifiedOnly,
                            onClick = { showVerifiedOnly = false },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            label = {
                                Text(
                                    text = "All",
                                    fontSize = 12.sp,
                                    fontWeight = if (!showVerifiedOnly) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                        FilterChip(
                            selected = showVerifiedOnly,
                            onClick = { showVerifiedOnly = true },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            label = {
                                Text(
                                    text = "Verified",
                                    fontSize = 12.sp,
                                    fontWeight = if (showVerifiedOnly) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }

                    Button(
                        onClick = { showAddRecipientDialog = true },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 7.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("New Recipient", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item(key = "recipients_header") {
                MailxSectionHeader(
                    title = "Recipient Addresses",
                    countBadge = displayedRecipients.size,
                    actionText = "Sync",
                    onActionClick = onSyncDefaults
                )
            }

            if (displayedRecipients.isEmpty()) {
                item(key = "recipients_empty") {
                    EmptyStateBox(
                        icon = Icons.Outlined.Inbox,
                        title = "No recipients found",
                        subtitle = "Add a new recipient email address or sync from the server.",
                        actionLabel = "Add Recipient",
                        onActionClick = { showAddRecipientDialog = true }
                    )
                }
            } else {
                items(displayedRecipients, key = { it.id.ifEmpty { it.email } }) { rec ->
                    val aliasCountForRec = remember(aliases, rec.email) {
                        aliases.count {
                            it.recipients.contains(rec.email, ignoreCase = true) ||
                                (it.recipients.isBlank() && defaultsState.defaultRecipient.contains(rec.email, ignoreCase = true))
                        }
                    }
                    val forwardedForRec = remember(aliases, rec.email) {
                        aliases.filter {
                            it.recipients.contains(rec.email, ignoreCase = true) ||
                                (it.recipients.isBlank() && defaultsState.defaultRecipient.contains(rec.email, ignoreCase = true))
                        }.sumOf { it.forwards }
                    }
                    val isDefault = defaultsState.defaultRecipient.equals(rec.email, ignoreCase = true)
                    val isExpanded = expandedRecipientId == rec.email
                    val canDelete = !rec.id.startsWith("fallback_") && !rec.id.startsWith("default_")
                    var showOverflowMenu by remember { mutableStateOf(false) }

                    val iconColor = when {
                        !rec.isActive -> MailxPalette.BlockedRose
                        rec.hasPgpKey && rec.pgpEnabled -> MailxPalette.ForwardEmerald
                        isDefault -> MaterialTheme.colorScheme.primary
                        else -> MailxPalette.ReplySky
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                expandedRecipientId = if (isExpanded) null else rec.email
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isExpanded) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                            }
                        ),
                        tonalElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp, vertical = 13.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = iconColor.copy(alpha = 0.14f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (rec.hasPgpKey && rec.pgpEnabled) {
                                                Icons.Outlined.Lock
                                            } else {
                                                Icons.Outlined.Mail
                                            },
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = rec.email,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$aliasCountForRec aliases • $forwardedForRec forwarded",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        copyToClipboard(context, "Recipient Email", rec.email, "Recipient email copied")
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy Email",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showOverflowMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.MoreVert,
                                            contentDescription = "More Actions",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showOverflowMenu,
                                        onDismissRequest = { showOverflowMenu = false },
                                        shape = RoundedCornerShape(14.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        if (!isDefault && rec.isActive) {
                                            DropdownMenuItem(
                                                text = { Text("Set as Default", fontSize = 13.5.sp) },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Outlined.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    onUpdateDefaultRecipient(rec.email)
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (rec.hasPgpKey) "Configure PGP Key" else "Add PGP Key",
                                                    fontSize = 13.5.sp
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Outlined.Key,
                                                    contentDescription = null,
                                                    tint = MailxPalette.ForwardEmerald,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                pgpEditingRecipient = rec
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Copy Address", fontSize = 13.5.sp) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Outlined.ContentCopy,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                copyToClipboard(context, "Recipient Email", rec.email, "Recipient email copied")
                                            }
                                        )
                                        if (!rec.isActive) {
                                            DropdownMenuItem(
                                                text = { Text("Enter OTP Code", fontSize = 13.5.sp) },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Outlined.VerifiedUser,
                                                        contentDescription = null,
                                                        tint = MailxPalette.PausedAmber,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    verifyingRecipient = rec
                                                }
                                            )
                                        }
                                        if (canDelete) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "Delete Recipient",
                                                        fontSize = 13.5.sp,
                                                        color = MailxPalette.BlockedRose
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Outlined.DeleteOutline,
                                                        contentDescription = null,
                                                        tint = MailxPalette.BlockedRose,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    deletingRecipient = rec
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            val domainTrustInfo = remember(rec.email) {
                                runCatching {
                                    val res = JSONObject(MailxRustBridge.nativeEvaluatePhishingRisk(rec.email, "", ""))
                                    res.optJSONObject("data")
                                }.getOrNull()
                            }
                            val pgpInspectionInfo = remember(rec.pgpKey) {
                                if (rec.hasPgpKey) {
                                    runCatching {
                                        val res = JSONObject(MailxRustBridge.nativeInspectOpenPgpKey(rec.pgpKey))
                                        res.optJSONObject("data")
                                    }.getOrNull()
                                } else null
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPillBadge(
                                        text = if (rec.isActive) "Verified" else "Unverified",
                                        color = if (rec.isActive) MailxPalette.ForwardEmerald else MailxPalette.BlockedRose
                                    )
                                    if (isDefault) {
                                        StatusPillBadge(
                                            text = "Default",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (rec.hasPgpKey) {
                                        StatusPillBadge(
                                            text = if (rec.pgpEnabled) {
                                                if (rec.pgpInline) "PGP/Inline" else "PGP/MIME"
                                            } else {
                                                "PGP Added"
                                            },
                                            color = if (rec.pgpEnabled) MailxPalette.SentIndigo else MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    val trustScore = domainTrustInfo?.optInt("domain_trust_score", 0) ?: 0
                                    if (trustScore > 0) {
                                        StatusPillBadge(
                                            text = "Trust $trustScore",
                                            color = if (trustScore >= 85) MailxPalette.BrandAccentTeal else MailxPalette.ReplySky
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = "Expand details",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HorizontalDivider(
                                        thickness = 0.75.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                                    )

                                    domainTrustInfo?.let { dt ->
                                        val domClass = dt.optString("domain_classification", "")
                                        val domProvider = dt.optString("domain_provider", "")
                                        val supportsDane = dt.optBoolean("supports_dane_or_mta_sts", false)
                                        if (domClass.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainer,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = buildString {
                                                        append("Rust Domain Audit: $domClass")
                                                        if (domProvider.isNotBlank()) append(" • $domProvider")
                                                        if (supportsDane) append(" • DANE/MTA-STS")
                                                    },
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                                )
                                            }
                                        }
                                    }

                                    pgpInspectionInfo?.let { pgpInfo ->
                                        val summary = pgpInfo.optString("summary", "")
                                        val crcOk = pgpInfo.optBoolean("crc24_verified", false)
                                        if (summary.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (crcOk) {
                                                    MailxPalette.ForwardEmerald.copy(alpha = 0.12f)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainer
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "RFC-4880 PGP Key: $summary",
                                                    fontSize = 11.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (crcOk) MailxPalette.ForwardEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (!rec.isActive) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { verifyingRecipient = rec },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Verify OTP", fontSize = 12.5.sp)
                                            }
                                            FilledTonalButton(
                                                onClick = { onSendRecipientOtp(rec.id) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("Resend OTP", fontSize = 12.5.sp)
                                            }
                                        }
                                    }

                                    if (rec.hasPgpKey) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "OpenPGP Encryption",
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Encrypt forwarded emails to this recipient",
                                                    fontSize = 11.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = rec.pgpEnabled,
                                                onCheckedChange = { enabled ->
                                                    onUpdateRecipientPgp(rec, rec.pgpKey, enabled, rec.pgpInline)
                                                },
                                                colors = mailxSwitchColors()
                                            )
                                        }

                                        if (rec.pgpEnabled) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "PGP/Inline Mode",
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "Use inline armor instead of PGP/MIME",
                                                        fontSize = 11.5.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Switch(
                                                    checked = rec.pgpInline,
                                                    onCheckedChange = { inlineMode ->
                                                        onUpdateRecipientPgp(rec, rec.pgpKey, rec.pgpEnabled, inlineMode)
                                                    },
                                                    colors = mailxSwitchColors()
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilledTonalButton(
                                            onClick = { pgpEditingRecipient = rec },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Key,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (rec.hasPgpKey) "Update PGP Key" else "Add PGP Key",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }

                                        if (!isDefault && rec.isActive) {
                                            FilledTonalButton(
                                                onClick = { onUpdateDefaultRecipient(rec.email) },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                    contentColor = MaterialTheme.colorScheme.primary
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = "Make Default",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "custom_domains_header") {
                MailxSectionHeader(
                    title = "Custom Domains",
                    countBadge = customDomains.size,
                    actionText = "New Domain",
                    onActionClick = { showAddDomainWizard = true }
                )
            }

            if (customDomains.isEmpty()) {
                item(key = "custom_domains_empty") {
                    EmptyStateBox(
                        icon = Icons.Outlined.Language,
                        title = "No custom domains yet",
                        subtitle = "Add your own domain (e.g. mydomain.com) and verify TXT, MX, SPF, and DKIM records to use custom aliases.",
                        actionLabel = "Add Custom Domain",
                        onActionClick = { showAddDomainWizard = true }
                    )
                }
            } else {
                items(customDomains, key = { it.id.ifEmpty { it.name } }) { dom ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val domColor = if (dom.isFullyVerified) MailxPalette.ForwardEmerald else MailxPalette.PausedAmber
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = domColor.copy(alpha = 0.14f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.Language,
                                                contentDescription = null,
                                                tint = domColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = dom.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (dom.description.isNotBlank()) {
                                            Text(
                                                text = dom.description,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Switch(
                                    checked = dom.enabled,
                                    onCheckedChange = { en ->
                                        onUpdateCustomDomain(
                                            dom, dom.description, dom.recipient,
                                            dom.fromName, en, dom.catchAll, dom.createAlias
                                        )
                                    },
                                    colors = mailxSwitchColors()
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPillBadge(
                                    text = if (dom.isOwnerVerified) "TXT ✓" else "TXT Pending",
                                    color = if (dom.isOwnerVerified) MailxPalette.ForwardEmerald else MailxPalette.PausedAmber
                                )
                                StatusPillBadge(
                                    text = if (dom.isMxVerified) "MX ✓" else "MX Pending",
                                    color = if (dom.isMxVerified) MailxPalette.ForwardEmerald else MailxPalette.PausedAmber
                                )
                                StatusPillBadge(
                                    text = if (dom.isSendVerified) "SPF/DKIM ✓" else "SPF/DKIM Pending",
                                    color = if (dom.isSendVerified) MailxPalette.ForwardEmerald else MailxPalette.PausedAmber
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Catch-All Routing", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "Accept incoming emails sent to any address on this domain",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = dom.catchAll,
                                    onCheckedChange = { ca ->
                                        onUpdateCustomDomain(
                                            dom, dom.description, dom.recipient,
                                            dom.fromName, dom.enabled, ca, dom.createAlias
                                        )
                                    },
                                    colors = mailxSwitchColors()
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = { inspectingDnsDomain = dom },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("DNS & Verify", fontSize = 12.sp, maxLines = 1)
                                }
                                FilledTonalIconButton(
                                    onClick = { editingCustomDomain = dom },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                }
                                FilledTonalIconButton(
                                    onClick = { onDeleteCustomDomain(dom.id) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MailxPalette.BlockedRose.copy(alpha = 0.14f),
                                        contentColor = MailxPalette.BlockedRose
                                    ),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            item(key = "shared_domains_header") {
                MailxSectionHeader(
                    title = "Available Domains & Default Selection",
                    countBadge = defaultsState.availableDomains.size,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (defaultsState.availableDomains.isNotEmpty()) {
                item(key = "shared_domains_group") {
                    MailxSettingsGroupCard {
                        defaultsState.availableDomains.forEachIndexed { idx, domain ->
                            val isSelected = defaultsState.defaultDomain == domain
                            val domainAliasCount = remember(aliases, domain) {
                                aliases.count { it.domainPart.equals(domain, ignoreCase = true) }
                            }
                            if (idx > 0) {
                                MailxGroupDivider()
                            }
                            MailxSectionRow(
                                title = "@$domain",
                                description = if (isSelected) {
                                    "Active default domain • $domainAliasCount aliases"
                                } else {
                                    "$domainAliasCount aliases • Tap to set as default"
                                },
                                startIcon = Icons.Outlined.Language,
                                iconTint = if (isSelected) MaterialTheme.colorScheme.primary else MailxPalette.ReplySky,
                                trailingBadge = if (isSelected) "Default" else null,
                                endIcon = if (isSelected) Icons.Outlined.Check else null,
                                accentColor = if (isSelected) MaterialTheme.colorScheme.primary else null,
                                grouped = true,
                                onClick = { onUpdateDefaultDomain(domain) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "recipients_footer_info") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                HorizontalDivider(
                    thickness = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .alpha(0.8f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${displayedRecipients.size} recipient addresses, ${customDomains.size} custom domains, and ${defaultsState.availableDomains.size} available domains configured.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAddRecipientDialog) {
        var newEmail by remember { mutableStateOf("") }
        MailxModernDialog(
            title = "New Recipient",
            subtitle = "Enter the real email address where your aliases should forward messages. A 6-digit OTP verification code will be sent.",
            icon = Icons.Outlined.Mail,
            confirmText = "Add Recipient",
            confirmEnabled = newEmail.contains("@"),
            onConfirm = {
                val target = newEmail.trim()
                showAddRecipientDialog = false
                onAddRecipient(target)
            },
            onDismiss = { showAddRecipientDialog = false }
        ) {
            OutlinedTextField(
                value = newEmail,
                onValueChange = { newEmail = it },
                label = { Text("Recipient Email Address") },
                placeholder = { Text("example@proton.me") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    verifyingRecipient?.let { rec ->
        var otpCode by remember { mutableStateOf("") }
        MailxModernDialog(
            title = "Verify Recipient",
            subtitle = "Enter the 6-digit verification code (OTP) sent to ${rec.email}:",
            icon = Icons.Outlined.VerifiedUser,
            confirmText = "Verify",
            confirmEnabled = otpCode.trim().length == 6,
            onConfirm = {
                val code = otpCode.trim()
                verifyingRecipient = null
                onVerifyRecipientOtp(rec.id, code)
            },
            onDismiss = { verifyingRecipient = null }
        ) {
            OutlinedTextField(
                value = otpCode,
                onValueChange = { otpCode = it.take(6) },
                label = { Text("6-Digit OTP Code") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    pgpEditingRecipient?.let { rec ->
        var pgpText by remember(rec) { mutableStateOf(rec.pgpKey) }
        val livePgpInspect = remember(pgpText) {
            if (pgpText.isNotBlank()) {
                runCatching {
                    val res = JSONObject(MailxRustBridge.nativeInspectOpenPgpKey(pgpText))
                    res.optJSONObject("data")
                }.getOrNull()
            } else null
        }

        MailxModernDialog(
            title = "OpenPGP Public Key",
            subtitle = "Paste your public PGP key for ${rec.email} starting with '-----BEGIN PGP PUBLIC KEY BLOCK-----':",
            icon = Icons.Outlined.Key,
            iconTint = MailxPalette.ForwardEmerald,
            confirmText = "Save Key",
            onConfirm = {
                val clean = pgpText.trim()
                pgpEditingRecipient = null
                onUpdateRecipientPgp(
                    rec,
                    clean,
                    clean.isNotEmpty(),
                    rec.pgpInline
                )
            },
            onDismiss = { pgpEditingRecipient = null },
            extraDismissAction = if (rec.hasPgpKey) {
                {
                    TextButton(
                        onClick = {
                            pgpEditingRecipient = null
                            onUpdateRecipientPgp(rec, "", false, false)
                        }
                    ) {
                        Text("Remove", color = MailxPalette.BlockedRose, fontSize = 13.sp)
                    }
                }
            } else null
        ) {
            OutlinedTextField(
                value = pgpText,
                onValueChange = { pgpText = it },
                placeholder = {
                    Text("Starts with '-----BEGIN PGP PUBLIC KEY BLOCK-----'", fontSize = 12.sp)
                },
                minLines = 5,
                maxLines = 9,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            livePgpInspect?.let { info ->
                val validArmor = info.optBoolean("valid_armor", false)
                val crcOk = info.optBoolean("crc24_verified", false)
                val summary = info.optString("summary", "")
                val statusColor = when {
                    validArmor && crcOk -> MailxPalette.ForwardEmerald
                    validArmor -> MailxPalette.PausedAmber
                    else -> MailxPalette.BlockedRose
                }
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (validArmor) "Rust RFC-4880 Inspector: Valid Armor" else "Invalid OpenPGP Armor Block",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        if (summary.isNotBlank()) {
                            Text(
                                text = summary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    deletingRecipient?.let { rec ->
        val otherRecipients = displayedRecipients.filter { it.email != rec.email && it.isActive }.map { it.email }
        var fallbackEmail by remember { mutableStateOf(otherRecipients.firstOrNull().orEmpty()) }
        MailxModernDialog(
            title = "Delete Recipient",
            subtitle = "Are you sure you want to delete recipient ${rec.email}?",
            icon = Icons.Outlined.DeleteOutline,
            iconTint = MailxPalette.BlockedRose,
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                val id = rec.id
                deletingRecipient = null
                onDeleteRecipient(id, fallbackEmail)
            },
            onDismiss = { deletingRecipient = null }
        ) {
            if (otherRecipients.isNotEmpty()) {
                Text(
                    text = "Fallback recipient for aliases currently using this address:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = fallbackEmail,
                    onValueChange = { fallbackEmail = it },
                    label = { Text("Fallback Recipient Email") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = mailxTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showAddDomainWizard) {
        var domainInput by remember { mutableStateOf("") }
        MailxModernDialog(
            title = "Add Custom Domain",
            subtitle = "Enter your domain name and add the TXT ownership verification record in your DNS settings.",
            icon = Icons.Outlined.Language,
            confirmText = "Add & Configure DNS",
            confirmEnabled = domainInput.contains("."),
            onConfirm = {
                val target = domainInput.trim()
                showAddDomainWizard = false
                onAddCustomDomain(target) { created ->
                    if (created != null) {
                        inspectingDnsDomain = created
                    }
                }
            },
            onDismiss = { showAddDomainWizard = false }
        ) {
            OutlinedTextField(
                value = domainInput,
                onValueChange = { domainInput = it },
                label = { Text("Domain Name") },
                placeholder = { Text("example.net") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            if (dnsConfig.verify.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            copyToClipboard(context, "TXT Record", dnsConfig.verify, "TXT verification value copied")
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("Type: TXT • Host: @", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = dnsConfig.verify,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Tap to copy record", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    inspectingDnsDomain?.let { dom ->
        val checks = dnsCheckResults[dom.id].orEmpty()
        MailxModernDialog(
            title = "DNS Records: ${dom.name}",
            subtitle = "Add these DNS records at your domain registrar (tap any card to copy):",
            icon = Icons.Outlined.Dns,
            confirmText = "Verify DNS",
            onConfirm = { onVerifyDomainDns(dom.id) },
            dismissText = "Close",
            onDismiss = { inspectingDnsDomain = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dnsConfig.verify.isNotBlank()) {
                    DnsRecordCard(
                        type = "TXT (Ownership)",
                        host = "@",
                        value = dnsConfig.verify,
                        onCopy = { copyToClipboard(context, "TXT", dnsConfig.verify) }
                    )
                }

                dnsConfig.mxHosts.forEachIndexed { idx, mxHost ->
                    DnsRecordCard(
                        type = "MX (Priority ${(idx + 1) * 10})",
                        host = "@",
                        value = mxHost,
                        onCopy = { copyToClipboard(context, "MX", mxHost) }
                    )
                }

                val spfVal = "v=spf1 include:spf.${dnsConfig.domain} ~all"
                DnsRecordCard(
                    type = "TXT (SPF)",
                    host = "@",
                    value = spfVal,
                    onCopy = { copyToClipboard(context, "SPF", spfVal) }
                )

                dnsConfig.dkimSelectors.forEach { sel ->
                    val host = "$sel._domainkey"
                    val target = "$sel._domainkey.${dnsConfig.domain}."
                    DnsRecordCard(
                        type = "CNAME (DKIM)",
                        host = host,
                        value = target,
                        onCopy = { copyToClipboard(context, "DKIM", target) }
                    )
                }

                val dmarcVal = "v=DMARC1; p=quarantine; pct=100"
                DnsRecordCard(
                    type = "TXT (DMARC)",
                    host = "_dmarc",
                    value = dmarcVal,
                    onCopy = { copyToClipboard(context, "DMARC", dmarcVal) }
                )

                if (checks.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
                    Text("Verification Status:", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                    checks.forEach { chk ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (chk.passed) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = if (chk.passed) MailxPalette.ForwardEmerald else MailxPalette.BlockedRose,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (chk.passed) "${chk.name}: Verified" else "${chk.name}: ${chk.error}",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    editingCustomDomain?.let { dom ->
        var desc by remember(dom) { mutableStateOf(dom.description) }
        var fromName by remember(dom) { mutableStateOf(dom.fromName) }
        var rec by remember(dom) { mutableStateOf(dom.recipient) }
        var createAliasAuto by remember(dom) { mutableStateOf(dom.createAlias) }

        MailxModernDialog(
            title = "${dom.name} Settings",
            subtitle = "Configure routing metadata and catch-all behavior.",
            icon = Icons.Outlined.Language,
            confirmText = "Save",
            onConfirm = {
                editingCustomDomain = null
                onUpdateCustomDomain(
                    dom, desc, rec, fromName,
                    dom.enabled, dom.catchAll, createAliasAuto
                )
            },
            onDismiss = { editingCustomDomain = null }
        ) {
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text("Description") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = fromName,
                onValueChange = { fromName = it },
                label = { Text("From Name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = rec,
                onValueChange = { rec = it },
                label = { Text("Default Recipient Email") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Auto-Create Alias on Catch-All Match",
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = createAliasAuto,
                    onCheckedChange = { createAliasAuto = it },
                    colors = mailxSwitchColors()
                )
            }
        }
    }
}

@Composable
private fun StatusPillBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun DnsRecordCard(
    type: String,
    host: String,
    value: String,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onCopy)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$type • Host: $host",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
