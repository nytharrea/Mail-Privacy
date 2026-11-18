package net.mailx.android.ui.sheets

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.components.MailxSegmentedControl
import net.mailx.android.ui.components.mailxTextFieldColors
import net.mailx.android.ui.model.DefaultsUiState
import net.mailx.android.ui.model.UiCustomDomain
import net.mailx.android.ui.model.UiWildcardDomainInfo
import net.mailx.android.ui.theme.LocalReducedMotion
import net.mailx.android.ui.theme.MailxIcons
import org.json.JSONObject
import kotlin.random.Random

private fun randomAlphanumeric(length: Int = 8): String {
    return runCatching {
        val obj = JSONObject(MailxRustBridge.nativeGenerateRandomSuffix(length))
        if (obj.optBoolean("ok", false)) {
            obj.optString("data").takeIf { it.isNotBlank() }
        } else {
            null
        }
    }.getOrNull() ?: run {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        buildString(length) {
            repeat(length) { append(chars[Random.nextInt(chars.length)]) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAliasBottomSheet(
    defaultsState: DefaultsUiState,
    customDomains: List<UiCustomDomain> = emptyList(),
    wildcardDomainInfo: UiWildcardDomainInfo = UiWildcardDomainInfo(),
    onFetchWildcardDomainInfo: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onCreateAlias: (requestJson: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reducedMotion = LocalReducedMotion.current

    var selectedDomain by remember { mutableStateOf(defaultsState.defaultDomain.ifEmpty { "mailx.net" }) }
    var selectedFormat by remember { mutableStateOf(defaultsState.defaultFormat.ifEmpty { "words" }) }
    var customLocalPart by remember { mutableStateOf("") }
    var wildcardLocalPart by remember { mutableStateOf(randomAlphanumeric(8)) }
    var wildcardDelimiter by remember { mutableStateOf(".") }
    var description by remember { mutableStateOf("") }
    var fromName by remember { mutableStateOf(defaultsState.defaultFromName) }
    var selectedRecipients by remember {
        mutableStateOf(
            defaultsState.defaultRecipient
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        )
    }
    var isSubmitting by remember { mutableStateOf(false) }
    var previewSeed by remember { mutableIntStateOf(0) }

    val spinAngle by animateFloatAsState(
        targetValue = previewSeed * 180f,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "preview_spin"
    )

    val rustCandidatePreview = remember(selectedDomain, selectedFormat, previewSeed) {
        if (selectedFormat in listOf("words", "random", "uuid")) {
            runCatching {
                val res = JSONObject(
                    MailxRustBridge.nativeGeneratePassphraseAndCandidate(selectedFormat, selectedDomain, 4)
                )
                res.optJSONObject("data")?.optJSONObject("alias_candidate")
            }.getOrNull()
        } else {
            null
        }
    }

    val matchingCustomDomain = remember(selectedDomain, customDomains) {
        customDomains.find { it.name.equals(selectedDomain, ignoreCase = true) }
    }

    LaunchedEffect(selectedDomain, selectedFormat) {
        if (selectedFormat == "wildcard") {
            val domainParam = matchingCustomDomain?.id?.ifBlank { selectedDomain } ?: selectedDomain
            onFetchWildcardDomainInfo(domainParam)
        }
    }

    val formats = listOf(
        "words" to "Words",
        "random" to "Random",
        "uuid" to "UUID",
        "wildcard" to "Wildcard *",
        "custom" to "Custom"
    )

    val wildcardSuffixValid = remember(wildcardLocalPart) {
        val len = wildcardLocalPart.trim().length
        len in 6..12 && wildcardLocalPart.trim().all { it.isLetterOrDigit() }
    }

    val fieldColors = mailxTextFieldColors()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Create New Alias",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Select domain, generation format, and routing destination",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. Tactile Domain Pill Selector (Replaces clunky ExposedDropdownMenu)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "DOMAIN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.75.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    val domains = defaultsState.availableDomains.ifEmpty { listOf("mailx.net") }
                    domains.forEach { dom ->
                        val isSelected = selectedDomain.equals(dom, ignoreCase = true)
                        val pillBg by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            animationSpec = tween(150),
                            label = "dom_bg_$dom"
                        )
                        Surface(
                            onClick = { selectedDomain = dom },
                            shape = RoundedCornerShape(11.dp),
                            color = pillBg,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "@$dom",
                                    fontSize = 12.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = MailxIcons.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Tactile Format Selector (Replaces clunky ExposedDropdownMenu)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "GENERATION FORMAT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.75.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    formats.forEach { (fmtKey, fmtLabel) ->
                        val isSelected = selectedFormat == fmtKey
                        val chipBg by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            animationSpec = tween(150),
                            label = "fmt_bg_$fmtKey"
                        )
                        Surface(
                            onClick = { selectedFormat = fmtKey },
                            shape = RoundedCornerShape(11.dp),
                            color = chipBg,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                }
                            )
                        ) {
                            Text(
                                text = fmtLabel,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 3. Live Rust Format Preview Banner
            AnimatedVisibility(visible = rustCandidatePreview != null) {
                rustCandidatePreview?.let { cand ->
                    val sampleAddress = cand.optString("address")
                    val entropyBits = cand.optDouble("entropy_bits", 0.0)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "RUST SAMPLE (${"%.0f".format(entropyBits)} BITS ENTROPY)",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                AnimatedContent(
                                    targetState = sampleAddress,
                                    transitionSpec = {
                                        (fadeIn(tween(140)) + slideInVertically { it / 3 })
                                            .togetherWith(fadeOut(tween(100)) + slideOutVertically { -it / 3 })
                                    },
                                    label = "sample_addr_anim"
                                ) { addr ->
                                    Text(
                                        text = addr,
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            FilledTonalIconButton(
                                onClick = { previewSeed++ },
                                modifier = Modifier.size(32.dp),
                                shape = RoundedCornerShape(9.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = MailxIcons.RefreshCw,
                                    contentDescription = "Sample another candidate",
                                    modifier = Modifier
                                        .size(15.dp)
                                        .graphicsLayer { rotationZ = spinAngle }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selectedFormat == "custom") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = customLocalPart,
                        onValueChange = { customLocalPart = it.trim() },
                        label = { Text("Custom Local Part (1-64 chars)") },
                        suffix = { Text("@$selectedDomain") },
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (matchingCustomDomain == null) {
                        Text(
                            text = "Custom local parts require a verified custom domain.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = selectedFormat == "wildcard") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MailxSegmentedControl(
                            options = listOf("*.", "*+"),
                            selectedIndex = if (wildcardDelimiter == ".") 0 else 1,
                            onSelect = { idx -> wildcardDelimiter = if (idx == 0) "." else "+" },
                            compact = true,
                            modifier = Modifier.width(108.dp)
                        )
                        OutlinedTextField(
                            value = wildcardLocalPart,
                            onValueChange = {
                                wildcardLocalPart = it.filter { ch -> ch.isLetterOrDigit() }.take(12)
                            },
                            label = { Text("Suffix (6-12 chars)") },
                            trailingIcon = {
                                IconButton(onClick = { wildcardLocalPart = randomAlphanumeric(8) }) {
                                    Icon(
                                        MailxIcons.RefreshCw,
                                        contentDescription = "Random Suffix",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(13.dp),
                            colors = fieldColors,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "Preview: *${wildcardDelimiter}${wildcardLocalPart}@$selectedDomain (${wildcardDomainInfo.count}/${wildcardDomainInfo.limit} used)",
                        fontSize = 11.5.sp,
                        color = if (wildcardDomainInfo.isAtLimit) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Service Note (Optional)") },
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = fromName,
                onValueChange = { fromName = it },
                label = { Text("Sender Display Name (Optional)") },
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            if (defaultsState.availableRecipients.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "FORWARDING RECIPIENT(S)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.75.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        defaultsState.availableRecipients.forEach { rec ->
                            val isChecked = selectedRecipients.contains(rec)
                            Surface(
                                onClick = {
                                    val next = selectedRecipients.toMutableSet()
                                    if (isChecked) {
                                        if (next.size > 1) next.remove(rec)
                                    } else {
                                        next.add(rec)
                                    }
                                    selectedRecipients = next
                                },
                                shape = RoundedCornerShape(11.dp),
                                color = if (isChecked) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isChecked) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isChecked) {
                                        Icon(
                                            imageVector = MailxIcons.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = rec,
                                        fontSize = 12.sp,
                                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isChecked) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    isSubmitting = true
                    val recipientsStr = selectedRecipients.joinToString(",")
                        .ifEmpty { defaultsState.defaultRecipient }
                    val resolvedDomainParam = matchingCustomDomain?.id?.ifBlank { selectedDomain } ?: selectedDomain
                    val reqObj = JSONObject().apply {
                        put("description", description.trim())
                        put("enabled", true)
                        put("recipients", recipientsStr)
                        put("from_name", fromName.trim())
                        put("format", selectedFormat)
                        put("domain", resolvedDomainParam)
                        if (selectedFormat == "custom" && customLocalPart.isNotEmpty()) {
                            put("local_part", customLocalPart)
                        }
                        if (selectedFormat == "wildcard") {
                            put("wildcard_delimiter", wildcardDelimiter)
                            val suffix = if (wildcardSuffixValid) wildcardLocalPart.trim() else randomAlphanumeric(8)
                            put("wildcard_local_part", suffix)
                        }
                    }
                    onCreateAlias(reqObj.toString())
                },
                enabled = !isSubmitting &&
                    (selectedFormat != "custom" || customLocalPart.isNotBlank()) &&
                    (selectedFormat != "wildcard" || wildcardSuffixValid),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creating...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Create & Copy Alias", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
