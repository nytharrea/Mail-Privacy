package net.mailx.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxSectionRow
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.model.*
import net.mailx.android.ui.theme.MailxPalette

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
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item(key = "recipients_domains_segmented") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    label = { Text("Recipients (${displayedRecipients.size})") }
                )
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    label = { Text("Domains (${customDomains.size + defaultsState.availableDomains.size})") }
                )
            }
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
                            label = { Text("All") }
                        )
                        FilterChip(
                            selected = showVerifiedOnly,
                            onClick = { showVerifiedOnly = true },
                            label = { Text("Verified") }
                        )
                    }

                    FilledTonalButton(
                        onClick = { showAddRecipientDialog = true },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Recipient", fontSize = 13.sp)
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
                        icon = Icons.Default.Inbox,
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

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                expandedRecipientId = if (isExpanded) null else rec.email
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (rec.hasPgpKey && rec.pgpEnabled) Icons.Default.Lock else Icons.Default.Mail,
                                    contentDescription = null,
                                    tint = when {
                                        !rec.isActive -> MailxPalette.BlockedRose
                                        rec.hasPgpKey && rec.pgpEnabled -> MailxPalette.ForwardEmerald
                                        isDefault -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rec.email,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$aliasCountForRec aliases • Forwarded: $forwardedForRec",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }

                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

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
                                            if (rec.pgpInline) "PGP/Inline Active" else "PGP/MIME Active"
                                        } else {
                                            "PGP Key Added"
                                        },
                                        color = if (rec.pgpEnabled) MailxPalette.SentIndigo else MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )

                                    if (!rec.isActive) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { verifyingRecipient = rec },
                                                modifier = Modifier.weight(1f),
                                                shape = CircleShape
                                            ) {
                                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Enter OTP")
                                            }
                                            FilledTonalButton(
                                                onClick = { onSendRecipientOtp(rec.id) },
                                                modifier = Modifier.weight(1f),
                                                shape = CircleShape
                                            ) {
                                                Text("Resend OTP")
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
                                                    text = "PGP Encryption",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Encrypt forwarded emails to this recipient with PGP",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = rec.pgpEnabled,
                                                onCheckedChange = { enabled ->
                                                    onUpdateRecipientPgp(rec, rec.pgpKey, enabled, rec.pgpInline)
                                                }
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
                                                        text = "PGP/Inline Format",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "Use PGP/Inline instead of PGP/MIME (attachments are not encrypted)",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Switch(
                                                    checked = rec.pgpInline,
                                                    onCheckedChange = { inlineMode ->
                                                        onUpdateRecipientPgp(rec, rec.pgpKey, rec.pgpEnabled, inlineMode)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = { pgpEditingRecipient = rec },
                                            modifier = Modifier.weight(1f),
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (rec.hasPgpKey) "PGP Key" else "Add PGP Key",
                                                maxLines = 1
                                            )
                                        }

                                        FilledTonalButton(
                                            onClick = { onUpdateDefaultRecipient(rec.email) },
                                            enabled = !isDefault && rec.isActive,
                                            modifier = Modifier.weight(1f),
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isDefault) "Default" else "Set Default",
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                copyToClipboard(context, "Recipient Email", rec.email, "Recipient email copied")
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copy", maxLines = 1)
                                        }

                                        if (!rec.id.startsWith("fallback_") && !rec.id.startsWith("default_")) {
                                            FilledTonalButton(
                                                onClick = { deletingRecipient = rec },
                                                modifier = Modifier.weight(1f),
                                                shape = CircleShape,
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MailxPalette.BlockedRose.copy(alpha = 0.14f),
                                                    contentColor = MailxPalette.BlockedRose
                                                )
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Delete", maxLines = 1)
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
                        icon = Icons.Default.Language,
                        title = "No custom domains yet",
                        subtitle = "Add your own domain (e.g. mydomain.com) and verify TXT, MX, SPF, and DKIM records to use custom aliases.",
                        actionLabel = "Add Custom Domain",
                        onActionClick = { showAddDomainWizard = true }
                    )
                }
            } else {
                items(customDomains, key = { it.id.ifEmpty { it.name } }) { dom ->
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = if (dom.isFullyVerified) MailxPalette.ForwardEmerald else MailxPalette.PausedAmber
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = dom.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (dom.description.isNotBlank()) {
                                            Text(
                                                text = dom.description,
                                                fontSize = 13.sp,
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
                                    }
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPillBadge(
                                    text = if (dom.isOwnerVerified) "TXT Ownership ✓" else "TXT Pending",
                                    color = if (dom.isOwnerVerified) MailxPalette.ForwardEmerald else MailxPalette.PausedAmber
                                )
                                StatusPillBadge(
                                    text = if (dom.isMxVerified) "MX ✓" else "MX Pending",
                                    color = if (dom.isMxVerified) MailxPalette.ForwardEmerald else MailxPalette.PausedAmber
                                )
                                StatusPillBadge(
                                    text = if (dom.isSendVerified) "SPF/DKIM/DMARC ✓" else "SPF/DKIM Pending",
                                    color = if (dom.isSendVerified) MailxPalette.ForwardEmerald else MailxPalette.PausedAmber
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Catch-All", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "Accept incoming emails sent to any address on this domain",
                                        fontSize = 12.sp,
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
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { inspectingDnsDomain = dom },
                                    modifier = Modifier.weight(1f),
                                    shape = CircleShape
                                ) {
                                    Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("DNS & Verify", maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = { editingCustomDomain = dom },
                                    shape = CircleShape
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                }
                                FilledTonalButton(
                                    onClick = { onDeleteCustomDomain(dom.id) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MailxPalette.BlockedRose.copy(alpha = 0.14f),
                                        contentColor = MailxPalette.BlockedRose
                                    )
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(16.dp))
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
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(defaultsState.availableDomains, key = { "shared_$it" }) { domain ->
                val isSelected = defaultsState.defaultDomain == domain
                val domainAliasCount = remember(aliases, domain) {
                    aliases.count { it.domainPart.equals(domain, ignoreCase = true) }
                }
                MailxSectionRow(
                    title = "@$domain",
                    description = if (isSelected) {
                        "Default domain • $domainAliasCount aliases"
                    } else {
                        "$domainAliasCount aliases • Tap to set as default"
                    },
                    startIcon = Icons.Default.Language,
                    endIcon = if (isSelected) Icons.Default.Check else null,
                    accentColor = if (isSelected) MaterialTheme.colorScheme.primary else null,
                    onClick = { onUpdateDefaultDomain(domain) }
                )
            }
        }

        item(key = "recipients_footer_info") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .alpha(0.8f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "${displayedRecipients.size} recipient addresses, ${customDomains.size} custom domains, and ${defaultsState.availableDomains.size} available domains configured.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showAddRecipientDialog) {
        var newEmail by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddRecipientDialog = false },
            title = { Text("New Recipient") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the real email address where your aliases should forward messages. A 6-digit OTP verification code will be sent to this address.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Recipient Email Address") },
                        placeholder = { Text("example@proton.me") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newEmail.contains("@"),
                    onClick = {
                        val target = newEmail.trim()
                        showAddRecipientDialog = false
                        onAddRecipient(target)
                    }
                ) {
                    Text("Add Recipient")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRecipientDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    verifyingRecipient?.let { rec ->
        var otpCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { verifyingRecipient = null },
            title = { Text("Verify Recipient Address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the 6-digit verification code (OTP) sent to ${rec.email}:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it.take(6) },
                        label = { Text("6-Digit OTP Code") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = otpCode.trim().length == 6,
                    onClick = {
                        val code = otpCode.trim()
                        verifyingRecipient = null
                        onVerifyRecipientOtp(rec.id, code)
                    }
                ) {
                    Text("Verify")
                }
            },
            dismissButton = {
                TextButton(onClick = { verifyingRecipient = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pgpEditingRecipient?.let { rec ->
        var pgpText by remember(rec) { mutableStateOf(rec.pgpKey) }
        AlertDialog(
            onDismissRequest = { pgpEditingRecipient = null },
            title = { Text("PGP Public Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste your public PGP key for ${rec.email} starting with '-----BEGIN PGP PUBLIC KEY BLOCK-----':",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = pgpText,
                        onValueChange = { pgpText = it },
                        placeholder = {
                            Text("Starts with '-----BEGIN PGP PUBLIC KEY BLOCK-----'")
                        },
                        minLines = 6,
                        maxLines = 10,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = pgpText.trim()
                        pgpEditingRecipient = null
                        onUpdateRecipientPgp(
                            rec,
                            clean,
                            clean.isNotEmpty(),
                            rec.pgpInline
                        )
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    if (rec.hasPgpKey) {
                        TextButton(
                            onClick = {
                                pgpEditingRecipient = null
                                onUpdateRecipientPgp(rec, "", false, false)
                            }
                        ) {
                            Text("Remove Key", color = MailxPalette.BlockedRose)
                        }
                    }
                    TextButton(onClick = { pgpEditingRecipient = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    deletingRecipient?.let { rec ->
        val otherRecipients = displayedRecipients.filter { it.email != rec.email && it.isActive }.map { it.email }
        var fallbackEmail by remember { mutableStateOf(otherRecipients.firstOrNull().orEmpty()) }
        AlertDialog(
            onDismissRequest = { deletingRecipient = null },
            title = { Text("Delete Recipient") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Are you sure you want to delete recipient ${rec.email}?")
                    if (otherRecipients.isNotEmpty()) {
                        Text(
                            text = "Fallback recipient for aliases currently using this address:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = fallbackEmail,
                            onValueChange = { fallbackEmail = it },
                            label = { Text("Fallback Recipient Email") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MailxPalette.BlockedRose,
                        contentColor = Color.White
                    ),
                    onClick = {
                        val id = rec.id
                        deletingRecipient = null
                        onDeleteRecipient(id, fallbackEmail)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecipient = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDomainWizard) {
        var domainInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDomainWizard = false },
            title = { Text("Add New Domain (Step 1/2)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1. Enter your domain name.\n2. Add the following TXT ownership verification record in your DNS settings:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        label = { Text("Domain Name") },
                        placeholder = { Text("example.net") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (dnsConfig.verify.isNotBlank()) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        copyToClipboard(context, "TXT Record", dnsConfig.verify, "TXT verification value copied")
                                    }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Type: TXT | Name/Host: @", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = dnsConfig.verify,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Tap to copy", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = domainInput.contains("."),
                    onClick = {
                        val target = domainInput.trim()
                        showAddDomainWizard = false
                        onAddCustomDomain(target) { created ->
                            if (created != null) {
                                inspectingDnsDomain = created
                            }
                        }
                    }
                ) {
                    Text("Add Domain & Configure DNS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDomainWizard = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    inspectingDnsDomain?.let { dom ->
        val checks = dnsCheckResults[dom.id].orEmpty()
        AlertDialog(
            onDismissRequest = { inspectingDnsDomain = null },
            title = { Text("DNS Configuration: ${dom.name}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Add the following DNS records at your domain provider so your domain can receive and send emails (tap any record to copy):",
                        style = MaterialTheme.typography.bodySmall
                    )

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
                        HorizontalDivider()
                        Text("Latest Verification Results:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        checks.forEach { chk ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (chk.passed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (chk.passed) MailxPalette.ForwardEmerald else MailxPalette.BlockedRose,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (chk.passed) "${chk.name}: Verified" else "${chk.name}: ${chk.error}",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onVerifyDomainDns(dom.id) }) {
                    Text("Verify DNS Records")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectingDnsDomain = null }) {
                    Text("Close")
                }
            }
        )
    }

    editingCustomDomain?.let { dom ->
        var desc by remember(dom) { mutableStateOf(dom.description) }
        var fromName by remember(dom) { mutableStateOf(dom.fromName) }
        var rec by remember(dom) { mutableStateOf(dom.recipient) }
        var createAliasAuto by remember(dom) { mutableStateOf(dom.createAlias) }

        AlertDialog(
            onDismissRequest = { editingCustomDomain = null },
            title = { Text("${dom.name} Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fromName,
                        onValueChange = { fromName = it },
                        label = { Text("From Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rec,
                        onValueChange = { rec = it },
                        label = { Text("Default Recipient Email") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
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
                            onCheckedChange = { createAliasAuto = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editingCustomDomain = null
                        onUpdateCustomDomain(
                            dom, desc, rec, fromName,
                            dom.enabled, dom.catchAll, createAliasAuto
                        )
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCustomDomain = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatusPillBadge(text: String, color: Color) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$type • Host: $host",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
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
