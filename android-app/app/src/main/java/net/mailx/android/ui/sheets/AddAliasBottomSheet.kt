package net.mailx.android.ui.sheets

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.components.MailxSegmentedControl
import net.mailx.android.ui.components.mailxTextFieldColors
import net.mailx.android.ui.model.DefaultsUiState
import net.mailx.android.ui.model.UiCustomDomain
import net.mailx.android.ui.model.UiWildcardDomainInfo
import net.mailx.android.ui.theme.MailxIcons
import net.mailx.android.ui.theme.MailxPalette
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
    var domainDropdownExpanded by remember { mutableStateOf(false) }
    var formatDropdownExpanded by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var previewSeed by remember { mutableIntStateOf(0) }

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
        "words" to "Random Words",
        "random" to "Random Characters",
        "uuid" to "UUID",
        "wildcard" to "Wildcard (*)",
        "custom" to "Custom"
    )
    val currentFormatLabel = formats.firstOrNull { it.first == selectedFormat }?.second ?: selectedFormat

    val wildcardSuffixValid = remember(wildcardLocalPart) {
        val len = wildcardLocalPart.trim().length
        len in 6..12 && wildcardLocalPart.trim().all { it.isLetterOrDigit() }
    }

    val fieldColors = mailxTextFieldColors()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 26.dp)
        ) {
            Text(
                text = "Create New Alias",
                fontSize = 16.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )

            HorizontalDivider(
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = domainDropdownExpanded,
                        onExpandedChange = { domainDropdownExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedDomain,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Domain") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = domainDropdownExpanded)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = domainDropdownExpanded,
                            onDismissRequest = { domainDropdownExpanded = false }
                        ) {
                            defaultsState.availableDomains.forEach { dom ->
                                DropdownMenuItem(
                                    text = { Text(dom) },
                                    onClick = {
                                        selectedDomain = dom
                                        domainDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = formatDropdownExpanded,
                        onExpandedChange = { formatDropdownExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = currentFormatLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Format") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatDropdownExpanded)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = formatDropdownExpanded,
                            onDismissRequest = { formatDropdownExpanded = false }
                        ) {
                            formats.forEach { (fmtKey, fmtLabel) ->
                                DropdownMenuItem(
                                    text = { Text(fmtLabel) },
                                    onClick = {
                                        selectedFormat = fmtKey
                                        formatDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = rustCandidatePreview != null) {
                    rustCandidatePreview?.let { cand ->
                        val sampleAddress = cand.optString("address")
                        val entropyBits = cand.optDouble("entropy_bits", 0.0)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MailxPalette.CryptoCyan.copy(alpha = 0.09f),
                            border = BorderStroke(0.75.dp, MailxPalette.CryptoCyan.copy(alpha = 0.28f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "RUST FORMAT SAMPLE (${"%.1f".format(entropyBits)} BITS ENTROPY)",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp,
                                        color = MailxPalette.CryptoCyan
                                    )
                                    Text(
                                        text = sampleAddress,
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { previewSeed++ },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = MailxIcons.RefreshCw,
                                        contentDescription = "Sample another candidate",
                                        tint = MailxPalette.CryptoCyan,
                                        modifier = Modifier.size(16.dp)
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
                            shape = RoundedCornerShape(12.dp),
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
                                        Icon(MailxIcons.RefreshCw, contentDescription = "Random Suffix", modifier = Modifier.size(18.dp))
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = "Preview: *${wildcardDelimiter}${wildcardLocalPart}@$selectedDomain (${wildcardDomainInfo.count}/${wildcardDomainInfo.limit} used)",
                            fontSize = 11.sp,
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
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fromName,
                    onValueChange = { fromName = it },
                    label = { Text("Sender Display Name (Optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                if (defaultsState.availableRecipients.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "FORWARDING RECIPIENT(S)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.65.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            defaultsState.availableRecipients.forEach { rec ->
                                val isChecked = selectedRecipients.contains(rec)
                                FilterChip(
                                    selected = isChecked,
                                    onClick = {
                                        val next = selectedRecipients.toMutableSet()
                                        if (isChecked) {
                                            if (next.size > 1) next.remove(rec)
                                        } else {
                                            next.add(rec)
                                        }
                                        selectedRecipients = next
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    label = { Text(rec, fontSize = 12.sp) }
                                )
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
                    shape = RoundedCornerShape(13.dp),
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
                        Text("Create & Copy Alias", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
