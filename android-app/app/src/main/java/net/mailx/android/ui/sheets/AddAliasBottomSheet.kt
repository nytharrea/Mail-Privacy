package net.mailx.android.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.model.DefaultsUiState
import net.mailx.android.ui.model.UiCustomDomain
import net.mailx.android.ui.model.UiWildcardDomainInfo
import org.json.JSONObject
import kotlin.random.Random

private fun randomAlphanumeric(length: Int = 8): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return buildString(length) {
        repeat(length) { append(chars[Random.nextInt(chars.length)]) }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "New Alias",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Choose a domain and format to create a new alias. The created address will be copied to your clipboard automatically.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = domainDropdownExpanded,
                    onExpandedChange = { domainDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedDomain,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Domain") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = domainDropdownExpanded)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                    onExpandedChange = { formatDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = currentFormatLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Alias Format") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatDropdownExpanded)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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

                AnimatedVisibility(visible = selectedFormat == "custom") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = customLocalPart,
                            onValueChange = { customLocalPart = it.trim() },
                            label = { Text("Custom Local Part (1-64 chars)") },
                            suffix = { Text("@$selectedDomain") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (matchingCustomDomain == null) {
                            Text(
                                text = "Note: Mailx only supports custom local parts on verified custom domains.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = selectedFormat == "wildcard") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.width(128.dp)) {
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    selected = wildcardDelimiter == ".",
                                    onClick = { wildcardDelimiter = "." },
                                    label = { Text("*.") }
                                )
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    selected = wildcardDelimiter == "+",
                                    onClick = { wildcardDelimiter = "+" },
                                    label = { Text("*+") }
                                )
                            }
                            OutlinedTextField(
                                value = wildcardLocalPart,
                                onValueChange = {
                                    wildcardLocalPart = it.filter { ch -> ch.isLetterOrDigit() }.take(12)
                                },
                                label = { Text("Suffix (6-12 alphanumeric)") },
                                trailingIcon = {
                                    IconButton(onClick = { wildcardLocalPart = randomAlphanumeric(8) }) {
                                        Icon(Icons.Default.Casino, contentDescription = "Random Suffix")
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = "Preview: *${wildcardDelimiter}${wildcardLocalPart}@$selectedDomain (${wildcardDomainInfo.count}/${wildcardDomainInfo.limit} used on domain)",
                            fontSize = 12.sp,
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
                    label = { Text("Description (Optional)") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fromName,
                    onValueChange = { fromName = it },
                    label = { Text("From Name (Optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (defaultsState.availableRecipients.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Select recipient(s) for this alias",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                    label = { Text(rec) }
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
                        .height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Creating...", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Text("Create & Copy to Clipboard", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
