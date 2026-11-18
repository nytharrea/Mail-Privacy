package net.mailx.android.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.mailxTextFieldColors
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.copyToClipboard
import net.mailx.android.ui.theme.MailxIcons
import net.mailx.android.ui.theme.MailxPalette
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyLabBottomSheet(
    aliases: List<UiAlias> = emptyList(),
    defaultDomain: String = "mailx.net",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTool by remember { mutableIntStateOf(0) }

    // Tool 0: URL Sanitizer state
    var rawUrlInput by remember { mutableStateOf("") }
    var urlReport by remember { mutableStateOf<JSONObject?>(null) }

    // Tool 1: Phishing & Spoofing Guard state
    var phishFromInput by remember { mutableStateOf("") }
    var phishReplyToInput by remember { mutableStateOf("") }
    var phishReturnPathInput by remember { mutableStateOf("") }
    var phishReport by remember { mutableStateOf<JSONObject?>(null) }

    // Tool 2: Header & MIME Inspector state
    var rawHeadersInput by remember { mutableStateOf("") }
    var headerReport by remember { mutableStateOf<JSONObject?>(null) }

    // Tool 3: Contextual Alias & Passphrase Studio state
    var serviceNameInput by remember { mutableStateOf("") }
    var contextualReport by remember { mutableStateOf<JSONObject?>(null) }

    // Tool 4: Exposure & Hygiene Radar state
    val hygieneSummary = remember(aliases) {
        runCatching {
            val rawArr = JSONArray().apply { aliases.forEach { put(it.rawJson) } }.toString()
            val res = JSONObject(MailxRustBridge.nativeAuditAliasHygiene(rawArr))
            res.optJSONObject("data")
        }.getOrNull()
    }

    fun evaluateUrl(input: String) {
        rawUrlInput = input
        if (input.isBlank()) {
            urlReport = null
            return
        }
        runCatching {
            val res = JSONObject(MailxRustBridge.nativeSanitizeTrackingUrls(input))
            if (res.optBoolean("ok", false)) {
                urlReport = res.optJSONObject("data")
            }
        }
    }

    fun evaluatePhishing() {
        if (phishFromInput.isBlank()) {
            phishReport = null
            return
        }
        runCatching {
            val res = JSONObject(
                MailxRustBridge.nativeEvaluatePhishingRisk(
                    phishFromInput,
                    phishReplyToInput,
                    phishReturnPathInput
                )
            )
            if (res.optBoolean("ok", false)) {
                phishReport = res.optJSONObject("data")
            }
        }
    }

    fun evaluateHeaders(raw: String) {
        rawHeadersInput = raw
        if (raw.isBlank()) {
            headerReport = null
            return
        }
        runCatching {
            val res = JSONObject(MailxRustBridge.nativeAnalyzeEmailHeaders(raw))
            if (res.optBoolean("ok", false)) {
                headerReport = res.optJSONObject("data")
            }
        }
    }

    fun generateContextual(service: String) {
        val target = service.ifBlank { "stripe" }
        serviceNameInput = target
        runCatching {
            val res = JSONObject(
                MailxRustBridge.nativeGenerateContextualAliases(
                    target,
                    defaultDomain.ifBlank { "mailx.net" },
                    4
                )
            )
            if (res.optBoolean("ok", false)) {
                contextualReport = res.optJSONObject("data")
            }
        }
    }

    val toolTabs = listOf(
        "URL Sanitizer",
        "Phishing Guard",
        "Headers & MIME",
        "Alias Studio",
        "Hygiene Radar"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MailxIcons.ShieldCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Rust Privacy & Security Lab",
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "OFFLINE RUST",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "Zero-network security inspection & cryptographic alias tools executing natively in Rust.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Horizontal Scrollable Material 3 Tool Selector Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                toolTabs.forEachIndexed { idx, label ->
                    val isSelected = selectedTool == idx
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTool = idx },
                        shape = RoundedCornerShape(10.dp),
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            when (selectedTool) {
                // TOOL 0: URL SANITIZER
                0 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "200+ Tracking Parameter Stripper",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                evaluateUrl("https://shop.example.com/deal?id=904&utm_source=newsletter&utm_medium=email&fbclid=IwAR29x&mc_eid=881a&_hsenc=p2ANq")
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Try Sample URL", fontSize = 11.5.sp)
                        }
                    }

                    OutlinedTextField(
                        value = rawUrlInput,
                        onValueChange = { evaluateUrl(it) },
                        label = { Text("Paste URL or text with tracking parameters") },
                        placeholder = { Text("https://example.com/?utm_source=...&fbclid=...") },
                        shape = RoundedCornerShape(12.dp),
                        colors = mailxTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    urlReport?.let { d ->
                        val cleaned = d.optString("sanitized_text", "")
                        val removedCount = d.optInt("removed_params_count", 0)
                        val keysArr = d.optJSONArray("removed_param_keys")
                        val removedKeys = buildList {
                            if (keysArr != null) {
                                for (i in 0 until keysArr.length()) {
                                    add(keysArr.optString(i))
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Stripped $removedCount tracking parameter(s)",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MailxPalette.ForwardEmerald
                                    )
                                    if (removedKeys.isNotEmpty()) {
                                        Text(
                                            text = removedKeys.joinToString(", "),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MailxPalette.BlockedRose,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                SelectionContainer {
                                    Text(
                                        text = cleaned,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Button(
                                    onClick = {
                                        copyToClipboard(context, "Sanitized URL", cleaned, "Clean URL copied")
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(MailxIcons.Copy, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Sanitized URL", fontSize = 12.5.sp)
                                }
                            }
                        }
                    }
                }

                // TOOL 1: PHISHING & SPOOFING GUARD
                1 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Homoglyph, Brand Spoof & Domain Trust",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                phishFromInput = "\"PayPal Security\" <alert@paypa1.com>"
                                phishReplyToInput = "verify@mailinator.com"
                                phishReturnPathInput = "bounce@paypa1.com"
                                evaluatePhishing()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Test Spoof Sample", fontSize = 11.5.sp)
                        }
                    }

                    OutlinedTextField(
                        value = phishFromInput,
                        onValueChange = {
                            phishFromInput = it
                            evaluatePhishing()
                        },
                        label = { Text("From Header (e.g. \"PayPal\" <support@paypa1.com>)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = mailxTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = phishReplyToInput,
                            onValueChange = {
                                phishReplyToInput = it
                                evaluatePhishing()
                            },
                            label = { Text("Reply-To (Optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = mailxTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = phishReturnPathInput,
                            onValueChange = {
                                phishReturnPathInput = it
                                evaluatePhishing()
                            },
                            label = { Text("Return-Path (Optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = mailxTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    phishReport?.let { p ->
                        val riskScore = p.optInt("risk_score", 0)
                        val riskLevel = p.optString("risk_level", "Low")
                        val brand = p.optString("impersonated_brand", "")
                        val domClass = p.optString("domain_classification", "")
                        val domProvider = p.optString("domain_provider", "")
                        val domTrust = p.optInt("domain_trust_score", 50)
                        val triggersArr = p.optJSONArray("triggers")
                        val triggers = buildList {
                            if (triggersArr != null) {
                                for (i in 0 until triggersArr.length()) {
                                    add(triggersArr.optString(i))
                                }
                            }
                        }

                        val riskColor = when {
                            riskScore >= 55 -> MailxPalette.BlockedRose
                            riskScore >= 25 -> MailxPalette.PausedAmber
                            else -> MailxPalette.ForwardEmerald
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, riskColor.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Phishing Risk: $riskLevel ($riskScore/100)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = riskColor
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "Trust $domTrust/100",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                if (brand.isNotBlank()) {
                                    Text(
                                        text = "⚠ Impersonation Target Detected: $brand",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MailxPalette.BlockedRose
                                    )
                                }

                                Text(
                                    text = "Domain Profile: $domClass${if (domProvider.isNotBlank()) " • $domProvider" else ""}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                triggers.forEach { trig ->
                                    Text(
                                        text = "• $trig",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // TOOL 2: HEADER & MIME INSPECTOR
                2 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RFC-5322 Header & MIME Security",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                evaluateHeaders(
                                    """
                                    Received: from mx1.mailx.net (mx1.mailx.net [198.51.100.12]) using ESMTPS
                                    Authentication-Results: mx1.mailx.net; spf=pass; dkim=pass; dmarc=pass
                                    From: Billing <billing@stripe.com>
                                    Return-Path: <billing@stripe.com>
                                    Subject: Your monthly invoice
                                    List-Unsubscribe: <https://stripe.com/unsub>
                                    List-Unsubscribe-Post: List-Unsubscribe=One-Click
                                    Content-Type: multipart/encrypted; protocol="application/pgp-encrypted"
                                    """.trimIndent()
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Load Sample Headers", fontSize = 11.5.sp)
                        }
                    }

                    OutlinedTextField(
                        value = rawHeadersInput,
                        onValueChange = { evaluateHeaders(it) },
                        label = { Text("Paste Raw Email Headers / MIME Source") },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(12.dp),
                        colors = mailxTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    headerReport?.let { d ->
                        val spf = d.optString("spf_status", "WARN")
                        val dkim = d.optString("dkim_status", "WARN")
                        val dmarc = d.optString("dmarc_status", "WARN")
                        val hops = d.optInt("hop_count", 0)
                        val secScore = d.optInt("security_score", 50)
                        val verdict = d.optString("verdict", "")
                        val pgpMime = d.optBoolean("is_pgp_mime_encrypted", false)
                        val pgpSigned = d.optBoolean("is_pgp_mime_signed", false)
                        val oneClickUnsub = d.optBoolean("has_one_click_unsubscribe", false)
                        val highRiskArr = d.optJSONArray("high_risk_attachments")
                        val highRiskFiles = buildList {
                            if (highRiskArr != null) {
                                for (i in 0 until highRiskArr.length()) {
                                    add(highRiskArr.optString(i))
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = verdict,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (highRiskFiles.isNotEmpty()) MailxPalette.BlockedRose else MailxPalette.ForwardEmerald
                                    )
                                    Text(
                                        text = "Score $secScore/100 • $hops hop(s)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    HeaderAuthBadge("SPF: $spf", spf == "PASS")
                                    HeaderAuthBadge("DKIM: $dkim", dkim == "PASS")
                                    HeaderAuthBadge("DMARC: $dmarc", dmarc == "PASS")
                                }

                                if (pgpMime || pgpSigned || oneClickUnsub) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (pgpMime) HeaderAuthBadge("PGP/MIME Encrypted", true)
                                        if (pgpSigned) HeaderAuthBadge("PGP Signed", true)
                                        if (oneClickUnsub) HeaderAuthBadge("RFC-8058 1-Click Unsub", true)
                                    }
                                }

                                if (highRiskFiles.isNotEmpty()) {
                                    Text(
                                        text = "⚠ Dangerous Attachment(s): ${highRiskFiles.joinToString(", ")}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MailxPalette.BlockedRose
                                    )
                                }
                            }
                        }
                    }
                }

                // TOOL 3: CONTEXTUAL ALIAS & PASSPHRASE STUDIO
                3 -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = serviceNameInput,
                            onValueChange = { serviceNameInput = it },
                            label = { Text("Service Name or URL (e.g. stripe.com, github)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = mailxTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { generateContextual(serviceNameInput) },
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Generate")
                        }
                    }

                    contextualReport?.let { c ->
                        val category = c.optString("category", "General Service")
                        val recDesc = c.optString("recommended_description", "")
                        val passphrase = c.optString("suggested_passphrase", "")
                        val entropy = c.optInt("passphrase_entropy_bits", 52)
                        val arr = c.optJSONArray("candidates")
                        val candidates = buildList {
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    add(arr.optString(i))
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Category: $category",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Suggested Note: $recDesc",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        candidates.forEach { cand ->
                            Surface(
                                onClick = {
                                    copyToClipboard(context, "Contextual Alias", cand, "Alias copied: $cand")
                                },
                                shape = RoundedCornerShape(13.dp),
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
                                        .padding(horizontal = 13.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = cand,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = MailxIcons.Copy,
                                        contentDescription = "Copy",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (passphrase.isNotBlank()) {
                            Surface(
                                onClick = {
                                    copyToClipboard(context, "Service Passphrase", passphrase, "Passphrase copied")
                                },
                                shape = RoundedCornerShape(13.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 13.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "SUGGESTED PASSPHRASE ($entropy BITS)",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = passphrase,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Icon(
                                        imageVector = MailxIcons.Copy,
                                        contentDescription = "Copy Passphrase",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // TOOL 4: EXPOSURE & HYGIENE RADAR
                else -> {
                    if (hygieneSummary != null) {
                        val score = hygieneSummary.optInt("overall_score", 90)
                        val grade = hygieneSummary.optString("grade", "A")
                        val totalAudited = hygieneSummary.optInt("total_audited", 0)
                        val missingDesc = hygieneSummary.optInt("missing_description_count", 0)
                        val highBlock = hygieneSummary.optInt("high_block_ratio_count", 0)
                        val dormant = hygieneSummary.optInt("dormant_active_count", 0)
                        val compromisedArr = hygieneSummary.optJSONArray("compromised_aliases")

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Portfolio Hygiene: $grade ($score/100)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "$totalAudited Aliases Audited",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = "• $missingDesc without service description notes\n• $highBlock high-spam / breach-exposed aliases\n• $dormant dormant active aliases",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (compromisedArr != null && compromisedArr.length() > 0) {
                            MailxSectionHeader(
                                title = "High-Spam / Breach Exposure Radar",
                                countBadge = compromisedArr.length()
                            )
                            for (i in 0 until compromisedArr.length()) {
                                val item = compromisedArr.optJSONObject(i) ?: continue
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MailxPalette.BlockedRose.copy(alpha = 0.10f),
                                    border = BorderStroke(1.dp, MailxPalette.BlockedRose.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = item.optString("name"),
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MailxPalette.BlockedRose
                                        )
                                        Text(
                                            text = "Blocked: ${item.optInt("blocks")} • Forwarded: ${item.optInt("forwards")}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = item.optString("recommendation"),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun HeaderAuthBadge(label: String, pass: Boolean) {
    val color = if (pass) MailxPalette.ForwardEmerald else MailxPalette.PausedAmber
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
