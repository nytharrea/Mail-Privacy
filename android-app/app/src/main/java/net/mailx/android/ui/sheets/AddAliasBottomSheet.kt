package net.mailx.android.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mailx.android.ui.model.DefaultsUiState
import org.json.JSONObject

/**
 * Kompakt Yeni Alias Oluşturma Paneli:
 * Varsayılan olarak yalnızca Açıklama ve Format seçimi gösterilir;
 * Alan adı, Alıcı ve Gönderici Adı "Gelişmiş Seçenekler" altında açılır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAliasBottomSheet(
    defaultsState: DefaultsUiState,
    onDismiss: () -> Unit,
    onCreateAlias: (requestJson: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var description by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf(defaultsState.defaultFormat.ifEmpty { "words" }) }
    var selectedDomain by remember { mutableStateOf(defaultsState.defaultDomain.ifEmpty { "mailx.net" }) }
    var customLocalPart by remember { mutableStateOf("") }
    var wildcardLocalPart by remember { mutableStateOf("") }
    var wildcardDelimiter by remember { mutableStateOf("+") }
    var fromName by remember { mutableStateOf(defaultsState.defaultFromName) }
    var recipientsText by remember { mutableStateOf(defaultsState.defaultRecipient) }
    var showAdvanced by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val formats = listOf(
        "words" to "Kelime",
        "random" to "Rastgele",
        "uuid" to "UUID",
        "wildcard" to "Wildcard (*)",
        "custom" to "Özel Ad"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Yeni Takma Ad Oluştur",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Açıklama (Örn: GitHub, Amazon, Kargo)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                formats.forEach { (fmtKey, fmtLabel) ->
                    FilterChip(
                        selected = selectedFormat == fmtKey,
                        onClick = { selectedFormat = fmtKey },
                        label = { Text(fmtLabel) }
                    )
                }
            }

            AnimatedVisibility(visible = selectedFormat == "custom") {
                OutlinedTextField(
                    value = customLocalPart,
                    onValueChange = { customLocalPart = it.trim() },
                    label = { Text("Özel Yerel Ad") },
                    suffix = { Text("@$selectedDomain", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(visible = selectedFormat == "wildcard") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = wildcardDelimiter == "+",
                        onClick = { wildcardDelimiter = "+" },
                        label = { Text("*+") }
                    )
                    FilterChip(
                        selected = wildcardDelimiter == ".",
                        onClick = { wildcardDelimiter = "." },
                        label = { Text("*.") }
                    )
                    OutlinedTextField(
                        value = wildcardLocalPart,
                        onValueChange = { wildcardLocalPart = it.trim() },
                        label = { Text("Sonek (Boşsa rastgele)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAdvanced = !showAdvanced },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alan Adı ve Alıcı Ayarları",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "@$selectedDomain • ${recipientsText.ifBlank { "Varsayılan alıcı" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (defaultsState.availableDomains.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            defaultsState.availableDomains.forEach { dom ->
                                FilterChip(
                                    selected = selectedDomain == dom,
                                    onClick = { selectedDomain = dom },
                                    label = { Text("@$dom") }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = recipientsText,
                        onValueChange = { recipientsText = it },
                        label = { Text("Yönlendirilecek Alıcı E-posta") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = fromName,
                        onValueChange = { fromName = it },
                        label = { Text("Gönderici Adı (İsteğe Bağlı)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Button(
                onClick = {
                    isSubmitting = true
                    val reqObj = JSONObject().apply {
                        put("description", description.trim())
                        put("enabled", true)
                        put("recipients", recipientsText.trim())
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
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSubmitting) "Oluşturuluyor..." else "Oluştur ve Kopyala",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
