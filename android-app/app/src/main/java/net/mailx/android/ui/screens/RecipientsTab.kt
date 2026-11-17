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
import net.mailx.android.ui.components.AddySectionHeader
import net.mailx.android.ui.components.AddySectionView
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.model.*
import net.mailx.android.ui.theme.AddyPalette

/**
 * `anonaddy/addy-android` `RecipientsFragment.kt` + `DomainSettingsActivity.kt` ve
 * Mailx `Recipients` (`43640.jpg`..`43646.jpg`: Yeni Alıcı, OTP Doğrulama, PGP Public Key, PGP Şifreleme, PGP/Inline) &
 * Mailx `Domains` (`43648.jpg`..`43654.jpg`: Yeni Alan Adı, TXT Sahiplik Doğrulaması, MX/SPF/DKIM/DMARC DNS Doğrulaması, Catch-All).
 */
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
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Alıcılar, 1 = Alan Adları
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Üst Sekme Seçici: Alıcılar (Recipients) / Alan Adları (Domains)
        item(key = "recipients_domains_segmented") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    label = { Text("Alıcılar (${displayedRecipients.size})") }
                )
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    label = { Text("Alan Adları (${customDomains.size + defaultsState.availableDomains.size})") }
                )
            }
        }

        if (selectedSection == 0) {
            // =================================================================
            // BÖLÜM 1: ALICILAR VE PGP ŞİFRELEME (`43640.jpg`..`43646.jpg`)
            // =================================================================
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
                            label = { Text("Tümü") }
                        )
                        FilterChip(
                            selected = showVerifiedOnly,
                            onClick = { showVerifiedOnly = true },
                            label = { Text("Doğrulanmış") }
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
                        Text("Yeni Alıcı", fontSize = 13.sp)
                    }
                }
            }

            item(key = "recipients_header") {
                AddySectionHeader(
                    title = "Alıcı Adresleri",
                    countBadge = displayedRecipients.size,
                    actionText = "Senkronize Et",
                    onActionClick = onSyncDefaults
                )
            }

            if (displayedRecipients.isEmpty()) {
                item(key = "recipients_empty") {
                    EmptyStateBox(
                        icon = Icons.Default.Inbox,
                        title = "Kayıtlı alıcı bulunamadı",
                        subtitle = "Yeni bir alıcı e-posta adresi ekleyebilir veya sunucudan senkronize edebilirsiniz.",
                        actionLabel = "Yeni Alıcı Ekle",
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
                                        !rec.isActive -> AddyPalette.SoftRed
                                        rec.hasPgpKey && rec.pgpEnabled -> AddyPalette.GreenActive
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
                                        text = "$aliasCountForRec takma ad • İletilen: $forwardedForRec",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }

                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Seçenekler",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Durum Rozetleri (`43640.jpg`, `43642.jpg`: Verified, PGP Key Added, Encryption Enabled, PGP/Inline)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPillBadge(
                                    text = if (rec.isActive) "Doğrulandı (Verified)" else "Doğrulanmadı",
                                    color = if (rec.isActive) AddyPalette.GreenActive else AddyPalette.SoftRed
                                )
                                if (isDefault) {
                                    StatusPillBadge(
                                        text = "Varsayılan",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (rec.hasPgpKey) {
                                    StatusPillBadge(
                                        text = if (rec.pgpEnabled) {
                                            if (rec.pgpInline) "PGP/Inline Aktif" else "PGP/MIME Aktif"
                                        } else {
                                            "PGP Anahtarı Ekli"
                                        },
                                        color = if (rec.pgpEnabled) AddyPalette.EasternBlue else MaterialTheme.colorScheme.secondary
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
                                                Text("OTP Kodu Gir")
                                            }
                                            FilledTonalButton(
                                                onClick = { onSendRecipientOtp(rec.id) },
                                                modifier = Modifier.weight(1f),
                                                shape = CircleShape
                                            ) {
                                                Text("Kodu Tekrar Gönder")
                                            }
                                        }
                                    }

                                    // PGP Anahtarı Ekle / Güncelle ve Şifreleme Kontrolleri (`43642.jpg`..`43646.jpg`)
                                    if (rec.hasPgpKey) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                               Text(
                                                    text = "PGP Şifreleme (Encryption)",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Bu alıcıya yönlendirilen e-postaları PGP ile şifrele",
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
                                                        text = "PGP/Inline Formatı",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "PGP/MIME yerine PGP/Inline kullan (Ek dosyalar şifrelenmez)",
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
                                                text = if (rec.hasPgpKey) "PGP Anahtarı" else "PGP Ekle",
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
                                                text = if (isDefault) "Varsayılan" else "Varsayılan Yap",
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
                                                copyToClipboard(context, "Recipient Email", rec.email, "Alıcı adresi kopyalandı")
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Kopyala", maxLines = 1)
                                        }

                                        if (!rec.id.startsWith("fallback_") && !rec.id.startsWith("default_")) {
                                            FilledTonalButton(
                                                onClick = { deletingRecipient = rec },
                                                modifier = Modifier.weight(1f),
                                                shape = CircleShape,
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = AddyPalette.SoftRed.copy(alpha = 0.14f),
                                                    contentColor = AddyPalette.SoftRed
                                                )
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Alıcıyı Sil", maxLines = 1)
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
            // =================================================================
            // BÖLÜM 2: ÖZEL ALAN ADLARI (CUSTOM DOMAINS & DNS) (`43648.jpg`..`43654.jpg`)
            // =================================================================
            item(key = "custom_domains_header") {
                AddySectionHeader(
                    title = "Özel Alan Adları (Custom Domains)",
                    countBadge = customDomains.size,
                    actionText = "Yeni Alan Adı",
                    onActionClick = { showAddDomainWizard = true }
                )
            }

            if (customDomains.isEmpty()) {
                item(key = "custom_domains_empty") {
                    EmptyStateBox(
                        icon = Icons.Default.Language,
                        title = "Henüz özel alan adınız yok",
                        subtitle = "Kendi alan adınızı (örn: alanadim.com) ekleyip TXT, MX, SPF ve DKIM kayıtlarını doğrulayarak sınırsız takma ad kullanabilirsiniz.",
                        actionLabel = "Yeni Alan Adı Ekle",
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
                                        tint = if (dom.isFullyVerified) AddyPalette.GreenActive else AddyPalette.PortalOrange
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

                            // DNS Doğrulama Rozetleri
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPillBadge(
                                    text = if (dom.isOwnerVerified) "TXT Sahiplik ✓" else "TXT Bekliyor",
                                    color = if (dom.isOwnerVerified) AddyPalette.GreenActive else AddyPalette.PortalOrange
                                )
                                StatusPillBadge(
                                    text = if (dom.isMxVerified) "MX ✓" else "MX Bekliyor",
                                    color = if (dom.isMxVerified) AddyPalette.GreenActive else AddyPalette.PortalOrange
                                )
                                StatusPillBadge(
                                    text = if (dom.isSendVerified) "SPF/DKIM/DMARC ✓" else "SPF/DKIM Bekliyor",
                                    color = if (dom.isSendVerified) AddyPalette.GreenActive else AddyPalette.PortalOrange
                                )
                            }

                            // Catch-All Anahtarı
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Catch-All (Tüm Adresleri Yakala)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "Bu alan adına gelen her e-postayı otomatik kabul et",
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
                                    Text("DNS & Doğrula", maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = { editingCustomDomain = dom },
                                    shape = CircleShape
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Düzenle", modifier = Modifier.size(16.dp))
                                }
                                FilledTonalButton(
                                    onClick = { onDeleteCustomDomain(dom.id) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = AddyPalette.SoftRed.copy(alpha = 0.14f),
                                        contentColor = AddyPalette.SoftRed
                                    )
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Sil", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Paylaşımlı Mailx Alan Adları (`43678.jpg`: mailx.net, zeropost.net, mailshift.net, layermail.net, ambox.net, spotsend.com)
            item(key = "shared_domains_header") {
                AddySectionHeader(
                    title = "Kullanılabilir Alan Adları ve Varsayılan Seçimi",
                    countBadge = defaultsState.availableDomains.size,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(defaultsState.availableDomains, key = { "shared_$it" }) { domain ->
                val isSelected = defaultsState.defaultDomain == domain
                val domainAliasCount = remember(aliases, domain) {
                    aliases.count { it.domainPart.equals(domain, ignoreCase = true) }
                }
                AddySectionView(
                    title = "@$domain",
                    description = if (isSelected) {
                        "Varsayılan alan adı • $domainAliasCount takma ad"
                    } else {
                        "$domainAliasCount takma ad • Varsayılan yapmak için dokunun"
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
                        contentDescription = "Bilgi",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Toplam ${displayedRecipients.size} alıcı adresi, ${customDomains.size} özel alan adı ve ${defaultsState.availableDomains.size} kullanılabilir alan adı tanımlı.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // =========================================================================
    // DİYALOGLAR: YENİ ALICI, OTP DOĞRULAMA, PGP ANAHTARI, YENİ ALAN ADI VE DNS TABLOSU
    // =========================================================================

    if (showAddRecipientDialog) {
        var newEmail by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddRecipientDialog = false },
            title = { Text("Yeni Alıcı Ekle (New Recipient)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Takma adlarınızdan gelen iletilerin yönlendirileceği gerçek e-posta adresinizi girin. Adresinize 6 haneli doğrulama kodu gönderilecektir.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Alıcı E-posta Adresi") },
                        placeholder = { Text("ornek@proton.me") },
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
                    Text("Alıcı Ekle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRecipientDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    verifyingRecipient?.let { rec ->
        var otpCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { verifyingRecipient = null },
            title = { Text("Alıcı Adresini Doğrula") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${rec.email} adresine gönderilen 6 haneli doğrulama kodunu (OTP) girin:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it.take(6) },
                        label = { Text("6 Haneli Doğrulama Kodu") },
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
                    Text("Doğrula")
                }
            },
            dismissButton = {
                TextButton(onClick = { verifyingRecipient = null }) {
                    Text("İptal")
                }
            }
        )
    }

    // `43646.jpg`: ADD PGP PUBLIC KEY ("Starts with '-----BEGIN PGP PUBLIC KEY BLOCK-----'")
    pgpEditingRecipient?.let { rec ->
        var pgpText by remember(rec) { mutableStateOf(rec.pgpKey) }
        AlertDialog(
            onDismissRequest = { pgpEditingRecipient = null },
            title = { Text("PGP Açık Anahtarı (Public Key)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${rec.email} için '-----BEGIN PGP PUBLIC KEY BLOCK-----' ile başlayan açık PGP anahtarınızı yapıştırın:",
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
                            if (clean.isNotEmpty()) true else false,
                            rec.pgpInline
                        )
                    }
                ) {
                    Text("Kaydet")
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
                            Text("Anahtarı Kaldır", color = AddyPalette.SoftRed)
                        }
                    }
                    TextButton(onClick = { pgpEditingRecipient = null }) {
                        Text("İptal")
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
            title = { Text("Alıcıyı Sil") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${rec.email} alıcısını silmek istediğinize emin misiniz?")
                    if (otherRecipients.isNotEmpty()) {
                        Text(
                            text = "Bu alıcıya bağlı takma adların aktarılacağı yedek alıcı:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = fallbackEmail,
                            onValueChange = { fallbackEmail = it },
                            label = { Text("Yedek Alıcı E-postası") },
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
                        containerColor = AddyPalette.SoftRed,
                        contentColor = Color.White
                    ),
                    onClick = {
                        val id = rec.id
                        deletingRecipient = null
                        onDeleteRecipient(id, fallbackEmail)
                    }
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRecipient = null }) {
                    Text("İptal")
                }
            }
        )
    }

    // `43650.jpg`: ADD NEW DOMAIN (Step 1/2 TXT Ownership + Step 2/2 DNS)
    if (showAddDomainWizard) {
        var domainInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDomainWizard = false },
            title = { Text("Yeni Alan Adı Ekle (Step 1/2)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1. Alan adınızı girin.\n2. Alan adınızın DNS ayarlarına aşağıdaki TXT doğrulama kaydını ekleyin:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = domainInput,
                        onValueChange = { domainInput = it },
                        label = { Text("Alan Adı (Domain)") },
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
                                        copyToClipboard(context, "TXT Record", dnsConfig.verify, "TXT doğrulama değeri kopyalandı")
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
                                Text("Kopyalamak için dokunun", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Alan Adını Ekle ve DNS'e Geç")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDomainWizard = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // `43652.jpg`, `43654.jpg`: Step 2/2 DNS Yapılandırması ve Doğrulama Tablosu
    inspectingDnsDomain?.let { dom ->
        val checks = dnsCheckResults[dom.id].orEmpty()
        AlertDialog(
            onDismissRequest = { inspectingDnsDomain = null },
            title = { Text("DNS Yapılandırması: ${dom.name}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Alan adınızın e-posta alıp gönderebilmesi için aşağıdaki DNS kayıtlarını sağlayıcınıza ekleyin (Kopyalamak için kayda dokunun):",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (dnsConfig.verify.isNotBlank()) {
                        DnsRecordCard(
                            type = "TXT (Sahiplik)",
                            host = "@",
                            value = dnsConfig.verify,
                            onCopy = { copyToClipboard(context, "TXT", dnsConfig.verify) }
                        )
                    }

                    dnsConfig.mxHosts.forEachIndexed { idx, mxHost ->
                        DnsRecordCard(
                            type = "MX (Öncelik ${(idx + 1) * 10})",
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
                        Text("Son Doğrulama Sonuçları:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        checks.forEach { chk ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (chk.passed) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (chk.passed) AddyPalette.GreenActive else AddyPalette.SoftRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (chk.passed) "${chk.name}: Doğrulandı" else "${chk.name}: ${chk.error}",
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onVerifyDomainDns(dom.id) }) {
                    Text("DNS Kayıtlarını Doğrula")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectingDnsDomain = null }) {
                    Text("Kapat")
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
            title = { Text("${dom.name} Ayarları") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Açıklama") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fromName,
                        onValueChange = { fromName = it },
                        label = { Text("Gönderici Adı (From Name)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rec,
                        onValueChange = { rec = it },
                        label = { Text("Varsayılan Alıcı E-postası") },
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
                            text = "Catch-All Eşleşmelerinde Otomatik Takma Ad Oluştur",
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
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCustomDomain = null }) {
                    Text("İptal")
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
