package net.mailx.android.ui.sheets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.copyToClipboard
import net.mailx.android.ui.theme.MailxIcons
import org.json.JSONArray
import org.json.JSONObject

/**
 * Rust-Powered Privacy Lab Bottom Sheet (Material 3 Expressive + Lucide Icons).
 * Overwrites stale PrivacyLabBottomSheet.kt in repository and binds to Rust skeleton tools.
 */
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
    var rawUrlInput by remember { mutableStateOf("") }
    var cleanedUrlOutput by remember { mutableStateOf("") }
    var removedParamsCount by remember { mutableIntStateOf(0) }

    var rawHeadersInput by remember { mutableStateOf("") }
    var headerVerdict by remember { mutableStateOf("") }

    var serviceNameInput by remember { mutableStateOf("") }
    var generatedCandidates by remember { mutableStateOf<List<String>>(emptyList()) }

    val hygieneSummary = remember(aliases) {
        runCatching {
            val rawArr = JSONArray().apply { aliases.forEach { put(it.rawJson) } }.toString()
            val res = JSONObject(MailxRustBridge.nativeAuditAliasHygiene(rawArr))
            res.optJSONObject("data")
        }.getOrNull()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
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
            Text(
                text = "Rust Privacy Lab",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    selected = selectedTool == 0,
                    onClick = { selectedTool = 0 },
                    label = { Text("URL Sanitizer", fontSize = 11.sp) }
                )
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    selected = selectedTool == 1,
                    onClick = { selectedTool = 1 },
                    label = { Text("Headers", fontSize = 11.sp) }
                )
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    selected = selectedTool == 2,
                    onClick = { selectedTool = 2 },
                    label = { Text("Contextual", fontSize = 11.sp) }
                )
            }

            when (selectedTool) {
                0 -> {
                    OutlinedTextField(
                        value = rawUrlInput,
                        onValueChange = {
                            rawUrlInput = it
                            val res = JSONObject(MailxRustBridge.nativeSanitizeTrackingUrls(it))
                            if (res.optBoolean("ok", false)) {
                                val data = res.optJSONObject("data")
                                cleanedUrlOutput = data?.optString("sanitized_text", "").orEmpty()
                                removedParamsCount = data?.optInt("removed_params_count", 0) ?: 0
                            }
                        },
                        label = { Text("Paste URL with tracking parameters (utm_*, fbclid...)") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (cleanedUrlOutput.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Removed $removedParamsCount tracking parameters in Rust:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                SelectionContainer {
                                    Text(
                                        text = cleanedUrlOutput,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Button(
                                    onClick = {
                                        copyToClipboard(context, "Sanitized URL", cleanedUrlOutput)
                                    },
                                    shape = CircleShape
                                ) {
                                    Icon(MailxIcons.Copy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Clean URL")
                                }
                            }
                        }
                    }
                }
                1 -> {
                    OutlinedTextField(
                        value = rawHeadersInput,
                        onValueChange = {
                            rawHeadersInput = it
                            val res = JSONObject(MailxRustBridge.nativeAnalyzeEmailHeaders(it))
                            if (res.optBoolean("ok", false)) {
                                val d = res.optJSONObject("data")
                                headerVerdict = if (d != null) {
                                    "SPF: ${d.optString("spf_status")} • DKIM: ${d.optString("dkim_status")} • DMARC: ${d.optString("dmarc_status")} • Hops: ${d.optInt("hop_count")}"
                                } else ""
                            }
                        },
                        label = { Text("Paste Raw Email Headers") },
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (headerVerdict.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = headerVerdict,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = serviceNameInput,
                            onValueChange = { serviceNameInput = it },
                            label = { Text("Service Name (e.g. github, steam)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val res = JSONObject(
                                    MailxRustBridge.nativeGenerateContextualAliases(
                                        serviceNameInput,
                                        defaultDomain,
                                        4
                                    )
                                )
                                val arr = res.optJSONObject("data")?.optJSONArray("candidates")
                                generatedCandidates = buildList {
                                    if (arr != null) {
                                        for (i in 0 until arr.length()) {
                                            add(arr.optString(i))
                                        }
                                    }
                                }
                            },
                            shape = CircleShape
                        ) {
                            Text("Generate")
                        }
                    }
                    generatedCandidates.forEach { cand ->
                        Surface(
                            onClick = { copyToClipboard(context, "Contextual Alias", cand) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cand, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Icon(MailxIcons.Copy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (hygieneSummary != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Hygiene Audit: ${hygieneSummary.optInt("total_audited")} aliases • ${hygieneSummary.optInt("missing_description_count")} without notes • ${hygieneSummary.optInt("high_block_ratio_count")} high-spam",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
