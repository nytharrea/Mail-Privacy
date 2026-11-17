package net.mailx.android.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.mailx.android.ui.components.MetricPill
import net.mailx.android.ui.components.SectionActionRow
import net.mailx.android.ui.components.SectionSwitchRow
import net.mailx.android.ui.model.NatoPhoneticHelper
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.copyToClipboard
import net.mailx.android.ui.model.formatIsoDateShort
import net.mailx.android.ui.theme.AddyPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAliasBottomSheet(
    alias: UiAlias,
    availableRecipients: List<String>,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePinned: (Boolean) -> Unit,
    onUpdateMetadata: (description: String, fromName: String, recipients: String) -> Unit,
    onOpenSendFromAlias: () -> Unit,
    onOpenWildcardGenerator: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showNato by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = alias.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Oluşturulma: ${formatIsoDateShort(alias.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { showNato = !showNato },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = if (showNato) "Fonetik Gizle" else "NATO Kodlama",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    AnimatedVisibility(visible = showNato) {
                        val pairs = remember(alias.name) { NatoPhoneticHelper.toPhoneticPairs(alias.name) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pairs.forEachIndexed { idx, (ch, word) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = ch,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${idx + 1}. $word",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Birincil Aksiyonlar (Kopyala, Gönder, Sil)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        if (alias.wildcard) {
                            onOpenWildcardGenerator()
                        } else {
                            copyToClipboard(context, "Mailx Alias", alias.name)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = if (alias.wildcard) Icons.Default.AutoAwesome else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (alias.wildcard) "Üret" else "Kopyala",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = onOpenSendFromAlias,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gönder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onRequestDelete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AddyPalette.BlockedRed.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AddyPalette.BlockedRed),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sil", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Kompakt 4'lü İstatistik Şeridi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(
                    modifier = Modifier.weight(1f),
                    label = "İletilen",
                    value = alias.forwards,
                    dotColor = AddyPalette.ForwardedOrange
                )
                MetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Engel",
                    value = alias.blocks,
                    dotColor = AddyPalette.BlockedRed
                )
                MetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Yanıt",
                    value = alias.replies,
                    dotColor = AddyPalette.RepliedBlue
                )
                MetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Gönderim",
                    value = alias.sends,
                    dotColor = AddyPalette.SentCyan
                )
            }

            SectionSwitchRow(
                icon = Icons.Default.CheckCircle,
                title = "Alias Aktif",
                subtitle = if (alias.enabled) "E-postalar yönlendiriliyor" else "Duraklatıldı",
                checked = alias.enabled,
                onCheckedChange = onToggleEnabled
            )

            SectionSwitchRow(
                icon = Icons.Default.PushPin,
                title = "Listenin Üstüne Sabitle",
                subtitle = "Özet ekranında ve listenin başında tut",
                checked = alias.pinned,
                onCheckedChange = onTogglePinned
            )

            SectionActionRow(
                icon = Icons.Default.Description,
                title = "Açıklama",
                subtitle = alias.description.ifBlank { "Açıklama eklemek için dokunun" },
                trailingIcon = Icons.Default.Edit,
                onClick = { editingField = "description" }
            )

            SectionActionRow(
                icon = Icons.Default.Inbox,
                title = "Alıcılar",
                subtitle = alias.recipients.ifBlank { "Varsayılan alıcı" },
                trailingIcon = Icons.Default.Edit,
                onClick = { editingField = "recipients" }
            )

            SectionActionRow(
                icon = Icons.Default.Person,
                title = "Gönderici Adı (From Name)",
                subtitle = alias.fromName.ifBlank { "Ayarlanmamış" },
                trailingIcon = Icons.Default.Edit,
                onClick = { editingField = "from_name" }
            )
        }
    }

    editingField?.let { field ->
        var textVal by remember(field, alias) {
            mutableStateOf(
                when (field) {
                    "description" -> alias.description
                    "from_name" -> alias.fromName
                    "recipients" -> alias.recipients
                    else -> ""
                }
            )
        }
        AlertDialog(
            onDismissRequest = { editingField = null },
            title = {
                Text(
                    when (field) {
                        "description" -> "Açıklamayı Düzenle"
                        "from_name" -> "Gönderici Adını Düzenle"
                        else -> "Alıcıları Düzenle"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (field == "recipients" && availableRecipients.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableRecipients.forEach { rec ->
                                val list = textVal.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val selected = list.contains(rec)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val m = list.toMutableList()
                                        if (selected) {
                                            if (m.size > 1) m.remove(rec)
                                        } else {
                                            m.add(rec)
                                        }
                                        textVal = m.joinToString(",")
                                    },
                                    label = { Text(rec) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = textVal,
                        onValueChange = { textVal = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val d = if (field == "description") textVal.trim() else alias.description
                        val f = if (field == "from_name") textVal.trim() else alias.fromName
                        val r = if (field == "recipients") textVal.trim() else alias.recipients
                        editingField = null
                        onUpdateMetadata(d, f, r)
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingField = null }) {
                    Text("İptal")
                }
            }
        )
    }
}
