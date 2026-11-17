package net.mailx.android.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.model.DefaultsUiState
import org.json.JSONObject

/**
 * `anonaddy/addy-android` içindeki `res/layout/bottomsheet_addalias.xml` (`AddAliasBottomDialogFragment.kt`)
 * alt penceresinin birebir Compose karşılığı.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAliasBottomSheet(
    defaultsState: DefaultsUiState,
    onDismiss: () -> Unit,
    onCreateAlias: (requestJson: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedDomain by remember { mutableStateOf(defaultsState.defaultDomain.ifEmpty { "mailx.net" }) }
    var selectedFormat by remember { mutableStateOf(defaultsState.defaultFormat.ifEmpty { "words" }) }
    var customLocalPart by remember { mutableStateOf("") }
    var wildcardLocalPart by remember { mutableStateOf("") }
    var wildcardDelimiter by remember { mutableStateOf("+") }
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

    val formats = listOf(
        "words" to "Rastgele Kelimeler",
        "random" to "Rastgele Karakterler",
        "uuid" to "UUID",
        "wildcard" to "Wildcard (*)",
        "custom" to "Özel"
    )
    val currentFormatLabel = formats.firstOrNull { it.first == selectedFormat }?.second ?: selectedFormat

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
                text = "Takma Ad Ekle",
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
                    text = "Alan adı ve format seçerek yeni bir takma ad oluşturun. Oluşturulan adres otomatik olarak panoya kopyalanır.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // bs_addalias_domain_til (AppTheme.ExposedDropdownMenu)
                ExposedDropdownMenuBox(
                    expanded = domainDropdownExpanded,
                    onExpandedChange = { domainDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedDomain,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Alan Adı") },
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

                // bs_addalias_alias_format_til (AppTheme.ExposedDropdownMenu)
                ExposedDropdownMenuBox(
                    expanded = formatDropdownExpanded,
                    onExpandedChange = { formatDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = currentFormatLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Takma Ad Formatı") },
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

                // bs_addalias_alias_local_part_til (Özel format seçildiğinde görünür)
                AnimatedVisibility(visible = selectedFormat == "custom") {
                    OutlinedTextField(
                        value = customLocalPart,
                        onValueChange = { customLocalPart = it.trim() },
                        label = { Text("Takma Ad Yerel Kısmı") },
                        suffix = { Text("@$selectedDomain") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Wildcard seçenekleri
                AnimatedVisibility(visible = selectedFormat == "wildcard") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.width(128.dp)) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                selected = wildcardDelimiter == "+",
                                onClick = { wildcardDelimiter = "+" },
                                label = { Text("*+") }
                            )
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                selected = wildcardDelimiter == ".",
                                onClick = { wildcardDelimiter = "." },
                                label = { Text("*.") }
                            )
                        }
                        OutlinedTextField(
                            value = wildcardLocalPart,
                            onValueChange = { wildcardLocalPart = it.trim() },
                            label = { Text("Sonek (Boşsa rastgele)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // bs_addalias_alias_desc_til (Çok satırlı açıklama alanı)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // bs_addalias_domain_help_textview2 + bs_addalias_recipients_chipgroup
                if (defaultsState.availableRecipients.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Bu takma ad için alıcıları seçin",
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

                // bs_addalias_alias_add_alias_button (CircularProgressButtonSecondary -> 56dp)
                Button(
                    onClick = {
                        isSubmitting = true
                        val recipientsStr = selectedRecipients.joinToString(",")
                            .ifEmpty { defaultsState.defaultRecipient }
                        val reqObj = JSONObject().apply {
                            put("description", description.trim())
                            put("enabled", true)
                            put("recipients", recipientsStr)
                            put("from_name", fromName.trim())
                            put("format", selectedFormat)
                            put("domain", selectedDomain)
                            if (selectedFormat == "custom" && customLocalPart.isNotEmpty()) {
                                put("local_part", customLocalPart)
                            }
                            if (selectedFormat == "wildcard") {
                                put("wildcard_delimiter", wildcardDelimiter)
                                if (wildcardLocalPart.isNotEmpty()) {
                                    put("wildcard_local_part", wildcardLocalPart)
                                }
                            }
                        }
                        onCreateAlias(reqObj.toString())
                    },
                    enabled = !isSubmitting && (selectedFormat != "custom" || customLocalPart.isNotBlank()),
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
                        Text("Ekleniyor...", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Text("Ekle", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
