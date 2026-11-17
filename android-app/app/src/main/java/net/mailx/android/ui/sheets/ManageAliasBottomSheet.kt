package net.mailx.android.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ForwardToInbox
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.AddyDonutChart
import net.mailx.android.ui.components.AddySectionHeader
import net.mailx.android.ui.components.AddySectionView
import net.mailx.android.ui.model.NatoPhoneticHelper
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.copyToClipboard
import net.mailx.android.ui.model.formatIsoDateShort
import net.mailx.android.ui.theme.AddyPalette

/**
 * `anonaddy/addy-android` içindeki `res/layout/activity_manage_alias.xml` ve
 * `res/layout/alias_general_actions.xml` (`ManageAliasActivity.kt`) ekranının birebir karşılığı +
 * Mailx özel işlevleri: Takma Adı Duraklat (`PUT /v1/alias/:id/pause`), Silineni Geri Yükle (`POST /v1/alias/restore/:id`),
 * Kalıcı Olarak Unut (`DELETE /v1/alias/forget/:id`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAliasBottomSheet(
    alias: UiAlias,
    availableRecipients: List<String>,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePinned: (Boolean) -> Unit,
    onUpdateMetadata: (description: String, fromName: String, recipients: String) -> Unit,
    onPauseAlias: (value: Int, unit: String) -> Unit = { _, _ -> },
    onUnpauseAlias: () -> Unit = {},
    onRestoreAlias: () -> Unit = {},
    onForgetAlias: () -> Unit = {},
    onOpenSendFromAlias: () -> Unit,
    onOpenWildcardGenerator: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showNato by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<String?>(null) }
    var showPauseDialog by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { showNato = !showNato }) {
                    Icon(
                        imageVector = Icons.Default.Spellcheck,
                        contentDescription = "NATO Fonetik Alfabe",
                        tint = if (showNato) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Text(
                    text = "Takma Adı Düzenle",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                IconButton(onClick = { onTogglePinned(!alias.pinned) }) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Sabitle",
                        tint = if (alias.pinned) {
                            AddyPalette.PortalOrange
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SelectionContainer {
                    Text(
                        text = alias.name.uppercase(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (alias.isPaused) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = CircleShape,
                        color = AddyPalette.PortalOrange.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "Duraklatıldı: ${formatIsoDateShort(alias.pausedUntil)} tarihine kadar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AddyPalette.PortalOrange,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = showNato) {
                    val pairs = remember(alias.name) { NatoPhoneticHelper.toPhoneticPairs(alias.name) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pairs.forEachIndexed { idx, (ch, word) ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = ch,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${idx + 1}. $word",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    AddyDonutChart(
                        forwards = alias.forwards,
                        replies = alias.replies,
                        sends = alias.sends,
                        blocks = alias.blocks,
                        enabled = alias.enabled,
                        size = 104.dp,
                        strokeWidth = 20.dp
                    )

                    Spacer(modifier = Modifier.width(28.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ManageAliasStatLegendRow(
                            icon = Icons.Default.Inbox,
                            tint = AddyPalette.PortalOrange,
                            text = "${alias.forwards} iletilen"
                        )
                        ManageAliasStatLegendRow(
                            icon = Icons.AutoMirrored.Filled.Reply,
                            tint = AddyPalette.PortalBlue,
                            text = "${alias.replies} yanıtlanan"
                        )
                        ManageAliasStatLegendRow(
                            icon = Icons.AutoMirrored.Filled.ForwardToInbox,
                            tint = AddyPalette.EasternBlue,
                            text = "${alias.sends} gönderilen"
                        )
                        ManageAliasStatLegendRow(
                            icon = Icons.Default.Block,
                            tint = AddyPalette.SoftRed,
                            text = "${alias.blocks} engellenen"
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (alias.wildcard) {
                                onOpenWildcardGenerator()
                            } else {
                                copyToClipboard(context, "Mailx Alias", alias.name)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (alias.wildcard) Icons.Default.AutoAwesome else Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (alias.wildcard) "Wildcard Üret" else "Takma Adı Kopyala",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    FilledTonalButton(
                        onClick = onOpenSendFromAlias,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ForwardToInbox,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "İleti Gönder",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AddySectionHeader(title = "Genel")

                if (alias.deletedAt.isNotBlank()) {
                    AddySectionView(
                        title = "Silinen Takma Adı Geri Yükle",
                        description = "Bu takma adı silinenler kutusundan çıkarıp tekrar kullanılabilir yap",
                        startIcon = Icons.Default.Restore,
                        accentColor = AddyPalette.GreenActive,
                        onClick = onRestoreAlias
                    )
                    if (alias.isCustomDomain) {
                        AddySectionView(
                            title = "Kalıcı Olarak Unut (Forget)",
                            description = "Özel alan adına ait bu takma adı veritabanından tamamen kaldır",
                            startIcon = Icons.Default.DeleteForever,
                            accentColor = AddyPalette.SoftRed,
                            onClick = onForgetAlias
                        )
                    }
                } else {
                    AddySectionView(
                        title = if (alias.enabled) "Takma Ad Aktif" else "Takma Ad Devre Dışı",
                        description = "Bu takma ad için e-posta yönlendirmeyi aç veya kapat",
                        startIcon = Icons.Default.CheckCircleOutline,
                        showSwitch = true,
                        switchChecked = alias.enabled,
                        onSwitchChange = onToggleEnabled
                    )

                    AddySectionView(
                        title = if (alias.isPaused) "Duraklatmayı Kaldır" else "Geçici Olarak Duraklat (Snooze)",
                        description = if (alias.isPaused) {
                            "${formatIsoDateShort(alias.pausedUntil)} tarihine kadar duraklatıldı • Kaldırmak için dokunun"
                        } else {
                            "Belirli bir süre (saat, gün veya hafta) boyunca iletileri sessize al"
                        },
                        startIcon = if (alias.isPaused) Icons.Default.PlayCircleOutline else Icons.Default.PauseCircleOutline,
                        onClick = {
                            if (alias.isPaused) {
                                onUnpauseAlias()
                            } else {
                                showPauseDialog = true
                            }
                        }
                    )

                    AddySectionView(
                        title = "Takma Adı Sabitle",
                        description = "Bu takma adı listenin üstünde sabit tut",
                        startIcon = Icons.Default.PushPin,
                        showSwitch = true,
                        switchChecked = alias.pinned,
                        onSwitchChange = onTogglePinned
                    )
                }

                AddySectionView(
                    title = "Açıklama",
                    description = alias.description.ifBlank { "Açıklama yok" },
                    startIcon = Icons.Default.Notes,
                    endIcon = Icons.Default.Edit,
                    onClick = { editingField = "description" }
                )

                AddySectionView(
                    title = "Alıcılar",
                    description = alias.recipients.ifBlank { "Varsayılan alıcı" },
                    startIcon = Icons.Default.Inbox,
                    endIcon = Icons.Default.Edit,
                    onClick = { editingField = "recipients" }
                )

                AddySectionView(
                    title = "Gönderici Adı",
                    description = alias.fromName.ifBlank { "Gönderici adı yok" },
                    startIcon = Icons.Default.Badge,
                    endIcon = Icons.Default.Edit,
                    onClick = { editingField = "from_name" }
                )

                AddySectionView(
                    title = "Oluşturulma Tarihi",
                    description = formatIsoDateShort(alias.createdAt),
                    startIcon = Icons.Default.Schedule
                )

                AddySectionView(
                    title = "Son Güncellenme",
                    description = formatIsoDateShort(alias.updatedAt),
                    startIcon = Icons.Default.History
                )

                if (alias.deletedAt.isBlank()) {
                    AddySectionView(
                        title = "Takma Adı Sil",
                        description = "Bu takma adı silinenler kutusuna taşıyarak devre dışı bırak",
                        startIcon = Icons.Default.DeleteOutline,
                        accentColor = AddyPalette.SoftRed,
                        onClick = onRequestDelete
                    )
                }
            }
        }
    }

    if (showPauseDialog) {
        var durationVal by remember { mutableIntStateOf(1) }
        var durationUnit by remember { mutableStateOf("days") }
        val units = listOf(
            "hours" to "Saat",
            "days" to "Gün",
            "weeks" to "Hafta"
        )

        AlertDialog(
            onDismissRequest = { showPauseDialog = false },
            title = { Text("Takma Adı Duraklat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Bu takma adı ne kadar süreyle geçici olarak duraklatmak istiyorsunuz?",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Süre: $durationVal", fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = durationVal.toFloat(),
                            onValueChange = { durationVal = it.toInt().coerceIn(1, 10) },
                            valueRange = 1f..10f,
                            steps = 8,
                            modifier = Modifier.width(180.dp)
                        )
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        units.forEachIndexed { idx, (key, label) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = units.size),
                                selected = durationUnit == key,
                                onClick = { durationUnit = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPauseDialog = false
                        onPauseAlias(durationVal, durationUnit)
                    }
                ) {
                    Text("Duraklat")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPauseDialog = false }) {
                    Text("İptal")
                }
            }
        )
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        singleLine = field != "description",
                        minLines = if (field == "description") 3 else 1,
                        shape = RoundedCornerShape(12.dp),
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

@Composable
private fun ManageAliasStatLegendRow(
    icon: ImageVector,
    tint: Color,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
        )
    }
}
