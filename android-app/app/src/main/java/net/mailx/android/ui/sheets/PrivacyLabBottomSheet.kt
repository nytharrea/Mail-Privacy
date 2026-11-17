package net.mailx.android.ui.sheets

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.components.LiquidGlassSurface
import net.mailx.android.ui.components.LiquidMaterialPreset
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.copyToClipboard
import net.mailx.android.ui.theme.MailxPalette
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyLabBottomSheet(
    aliases: List<UiAlias>,
    defaultDomain: String,
    defaultRecipient: String,
    onCreateContextualAlias: (String, String) -> Unit,
    onSelectAliasId: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedSection by remember { mutableIntStateOf(0) }

    var urlInput by remember {
        mutableStateOf("https://store.example.com/offer?item=pro&utm_source=newsletter&utm_medium=email&fbclid=IwAR998877&mc_eid=445566")
    }
    var urlReportJson by remember { mutableStateOf<JSONObject?>(null) }

    var headerInput by remember {
        mutableStateOf(
            "From: Security Team <alerts@service.org>\n" +
                "Return-Path: <bounce@service.org>\n" +
                "Reply-To: <alerts@service.org>\n" +
                "Subject: New login to your account\n" +
                "Authentication-Results: mx.mailx.net; spf=pass; dkim=pass; dmarc=pass\n" +
                "X-Mailx-Alias: vault@${defaultDomain.ifBlank { "mailx.net" }}\n" +
                "Received: from mta01.service.org by mx.mailx.net with ESMTPS; Tue, 17 Nov 2026 20:15:00 +0000"
        )
    }
    var headerReportJson by remember { mutableStateOf<JSONObject?>(null) }

    var serviceInput by remember { mutableStateOf("github.com") }
    var contextualJson by remember { mutableStateOf<JSONObject?>(null) }
    var hygieneJson by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(urlInput) {
        if (urlInput.isNotBlank()) {
            runCatching {
                val env = JSONObject(MailxRustBridge.nativeSanitizeTrackingUrls(urlInput))
                if (env.optBoolean("ok", false)) {
                    urlReportJson = env.optJSONObject("data")
                }
            }
        }
    }

    LaunchedEffect(headerInput) {
        if (headerInput.isNotBlank()) {
            runCatching {
                val env = JSONObject(MailxRustBridge.nativeAnalyzeEmailHeaders(headerInput))
                if (env.optBoolean("ok", false)) {
                    headerReportJson = env.optJSONObject("data")
                }
            }
        }
    }

    LaunchedEffect(serviceInput, defaultDomain) {
        runCatching {
            val env = JSONObject(
                MailxRustBridge.nativeGenerateContextualAliases(
                    serviceInput.ifBlank { "vault" },
                    defaultDomain.ifBlank { "mailx.net" }
                )
            )
            if (env.optBoolean("ok", false)) {
                contextualJson = env.optJSONObject("data")
            }
        }
    }

    LaunchedEffect(aliases) {
        runCatching {
            val arr = JSONArray()
            aliases.forEach { arr.put(it.rawJson) }
            val env = JSONObject(MailxRustBridge.nativeAuditAliasHygiene(arr.toString()))
            if (env.optBoolean("ok", false)) {
                hygieneJson = env.optJSONObject("data")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LiquidGlassSurface(
                cornerRadius = 20.dp,
                preset = LiquidMaterialPreset.Tinted,
                tintColor = MailxPalette.BrandAccentMint.copy(alpha = 0.28f),
                estimatedWidthDp = 360f,
                estimatedHeightDp = 70f,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MailxPalette.BrandAccentMint.copy(alpha = 0.20f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MailxPalette.BrandAccentMint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rust Privacy & Security Lab",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Zero-cloud offline URL sanitizer, SPF/DKIM header analyzer & alias hygiene engine",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    label = { Text("URL Sanitizer") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                FilterChip(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    label = { Text("Header & SPF Audit") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                FilterChip(
                    selected = selectedSection == 2,
                    onClick = { selectedSection = 2 },
                    label = { Text("Hygiene & Smart Gen") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.HealthAndSafety,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            when (selectedSection) {
                0 -> {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Paste URL or message containing links") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            FilledTonalIconButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val pasted = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                    if (pasted.isNotBlank()) urlInput = pasted
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    )

                    val rep = urlReportJson
                    if (rep != null) {
                        val cleaned = rep.optString("cleaned_text", "")
                        val inspected = rep.optInt("urls_inspected", 0)
                        val removedCount = rep.optInt("trackers_removed", 0)
                        val itemsArr = rep.optJSONArray("items")

                        LiquidGlassSurface(
                            cornerRadius = 18.dp,
                            preset = LiquidMaterialPreset.Regular,
                            estimatedWidthDp = 360f,
                            estimatedHeightDp = 160f,
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CleaningServices,
                                            contentDescription = null,
                                            tint = MailxPalette.ForwardEmerald,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "$removedCount tracking params stripped across $inspected link(s)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            copyToClipboard(
                                                context,
                                                "Sanitized URL",
                                                cleaned,
                                                "Sanitized link copied"
                                            )
                                        },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy Clean", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = cleaned.ifBlank { "No URLs detected" },
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MailxPalette.ForwardEmerald,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }

                                if (itemsArr != null && itemsArr.length() > 0) {
                                    val firstObj = itemsArr.optJSONObject(0)
                                    val remArr = firstObj?.optJSONArray("removed_params")
                                    if (remArr != null && remArr.length() > 0) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            for (i in 0 until remArr.length()) {
                                                val p = remArr.optString(i)
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MailxPalette.BlockedRose.copy(alpha = 0.16f)
                                                ) {
                                                    Text(
                                                        text = "⊘ $p",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MailxPalette.BlockedRose,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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

                1 -> {
                    OutlinedTextField(
                        value = headerInput,
                        onValueChange = { headerInput = it },
                        label = { Text("Paste raw RFC 5322 email headers") },
                        minLines = 4,
                        maxLines = 7,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val hdr = headerReportJson
                    if (hdr != null) {
                        val score = hdr.optInt("security_score", 0)
                        val verdict = hdr.optString("verdict", "")
                        val spf = hdr.optString("spf_status", "none").uppercase()
                        val dkim = hdr.optString("dkim_status", "none").uppercase()
                        val dmarc = hdr.optString("dmarc_status", "none").uppercase()
                        val findingsArr = hdr.optJSONArray("findings")
                        val hopsArr = hdr.optJSONArray("hops")

                        val scoreColor = when {
                            score >= 80 -> MailxPalette.ForwardEmerald
                            score >= 55 -> MailxPalette.PausedAmber
                            else -> MailxPalette.BlockedRose
                        }

                        LiquidGlassSurface(
                            cornerRadius = 18.dp,
                            preset = LiquidMaterialPreset.Regular,
                            estimatedWidthDp = 360f,
                            estimatedHeightDp = 220f,
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
                                    Column {
                                        Text(
                                            text = verdict,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = scoreColor
                                        )
                                        Text(
                                            text = "SPF: $spf • DKIM: $dkim • DMARC: $dmarc",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = scoreColor.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "$score / 100",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = scoreColor,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )

                                if (findingsArr != null) {
                                    for (i in 0 until findingsArr.length()) {
                                        val fText = findingsArr.optString(i)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (fText.contains("failed", ignoreCase = true) ||
                                                    fText.contains("differs", ignoreCase = true) ||
                                                    fText.contains("exposed", ignoreCase = true)
                                                ) {
                                                    Icons.Default.Warning
                                                } else {
                                                    Icons.Default.CheckCircle
                                                },
                                                contentDescription = null,
                                                tint = if (fText.contains("failed", ignoreCase = true) ||
                                                    fText.contains("differs", ignoreCase = true)
                                                ) {
                                                    MailxPalette.BlockedRose
                                                } else {
                                                    MailxPalette.ForwardEmerald
                                                },
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = fText,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                if (hopsArr != null && hopsArr.length() > 0) {
                                    Text(
                                        text = "MTA Relay Hop Chain (${hopsArr.length()} hops)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    for (i in 0 until hopsArr.length()) {
                                        val hop = hopsArr.optJSONObject(i) ?: continue
                                        Text(
                                            text = "#${hop.optInt("index")} ${hop.optString("from_host")} → ${hop.optString("by_host")} (${hop.optString("protocol")})",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    val hyg = hygieneJson
                    if (hyg != null) {
                        val score = hyg.optInt("hygiene_score", 100)
                        val grade = hyg.optString("grade", "A+")
                        val burned = hyg.optInt("burned_count", 0)
                        val unlabeled = hyg.optInt("unlabeled_count", 0)
                        val dupes = hyg.optInt("duplicate_service_count", 0)
                        val issuesArr = hyg.optJSONArray("issues")

                        LiquidGlassSurface(
                            cornerRadius = 18.dp,
                            preset = LiquidMaterialPreset.Regular,
                            estimatedWidthDp = 360f,
                            estimatedHeightDp = 160f,
                            modifier = Modifier.fillMaxWidth()
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
                                    Column {
                                        Text(
                                            text = "Vault Hygiene Grade: $grade ($score/100)",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$burned burned • $unlabeled unlabeled • $dupes duplicate service",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Policy,
                                        contentDescription = null,
                                        tint = MailxPalette.BrandAccentMint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                if (issuesArr != null && issuesArr.length() > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    )
                                    val maxItems = minOf(issuesArr.length(), 4)
                                    for (i in 0 until maxItems) {
                                        val iss = issuesArr.optJSONObject(i) ?: continue
                                        val aliasId = iss.optString("alias_id", "")
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = aliasId.isNotBlank()) {
                                                    onSelectAliasId(aliasId)
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = iss.optString("alias_name"),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = iss.optString("detail"),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Surface(
                                                shape = CircleShape,
                                                color = MailxPalette.PausedAmber.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = iss.optString("category").uppercase(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MailxPalette.PausedAmber,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = serviceInput,
                        onValueChange = { serviceInput = it },
                        label = { Text("Service or website for smart compartment alias") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val ctxObj = contextualJson
                    val candArr = ctxObj?.optJSONArray("candidates")
                    if (candArr != null) {
                        for (i in 0 until candArr.length()) {
                            val cand = candArr.optJSONObject(i) ?: continue
                            val label = cand.optString("label")
                            val email = cand.optString("alias_email")
                            val localPart = cand.optString("local_part")
                            val entropy = cand.optDouble("entropy_bits", 48.0).toFloat()

                            LiquidGlassSurface(
                                cornerRadius = 14.dp,
                                preset = LiquidMaterialPreset.Regular,
                                estimatedWidthDp = 360f,
                                estimatedHeightDp = 58f,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MailxPalette.BrandAccentMint
                                            )
                                            Text(
                                                text = "${entropy.toInt()} bits entropy",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = email,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilledTonalIconButton(
                                            onClick = {
                                                copyToClipboard(context, "Candidate Alias", email)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        if (defaultRecipient.isNotBlank()) {
                                            Button(
                                                onClick = {
                                                    onCreateContextualAlias(
                                                        localPart,
                                                        serviceInput.trim().ifBlank { "Service" }
                                                    )
                                                },
                                                shape = CircleShape,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Create", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    }
}
