package net.mailx.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.AddySectionHeader
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.model.*
import net.mailx.android.ui.theme.AddyPalette

/**
 * Mailx `Rules` (`43658.jpg`..`43664.jpg`: Koşullar, Operatörler, Eylemler, Sıralama) ve
 * Mailx `Blocklist` (`43656.jpg`: E-posta / Alan Adı Engelleme) ekranı (`addy-android` Material 3 tasarımında).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesAndBlocklistTab(
    rules: List<UiRule>,
    blocklist: List<UiBlocklistItem>,
    availableRecipients: List<String>,
    onSaveRule: (
        ruleId: String?,
        name: String,
        operator: String,
        forwards: Boolean,
        replies: Boolean,
        sends: Boolean,
        conditions: List<UiRuleCondition>,
        actions: List<UiRuleAction>,
        active: Boolean
    ) -> Unit,
    onToggleRuleActive: (UiRule, Boolean) -> Unit,
    onMoveRuleOrder: (ruleId: String, moveUp: Boolean) -> Unit,
    onDeleteRule: (ruleId: String) -> Unit,
    onAddBlocklistItem: (type: String, value: String) -> Unit,
    onDeleteBlocklistItem: (id: String) -> Unit,
    onRefresh: () -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Kurallar (Rules), 1 = Engellenenler (Blocklist)
    var editingRule by remember { mutableStateOf<UiRule?>(null) }
    var showCreateRuleSheet by remember { mutableStateOf(false) }
    var showAddBlockDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "rules_blocklist_segmented") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    label = { Text("Kurallar (${rules.size})") }
                )
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    label = { Text("Engellenenler (${blocklist.size})") }
                )
            }
        }

        if (selectedSection == 0) {
            // =================================================================
            // KURALLAR (`43658.jpg`, `43660.jpg`, `43662.jpg`, `43664.jpg`)
            // =================================================================
            item(key = "rules_header") {
                AddySectionHeader(
                    title = "Gelen & Giden İleti Kuralları",
                    countBadge = rules.size,
                    actionText = "Yeni Kural",
                    onActionClick = { showCreateRuleSheet = true }
                )
            }

            if (rules.isEmpty()) {
                item(key = "rules_empty") {
                    EmptyStateBox(
                        icon = Icons.Default.FilterAlt,
                        title = "Henüz tanımlı kuralınız yok",
                        subtitle = "Gönderici, konu, takma ad veya ek dosya durumuna göre iletileri otomatik engellemek, yönlendirmek veya başlığı değiştirmek için kural oluşturun.",
                        actionLabel = "Yeni Kural Oluştur",
                        onActionClick = { showCreateRuleSheet = true }
                    )
                }
            } else {
                itemsIndexed(rules, key = { _, r -> r.id.ifEmpty { r.name } }) { index, rule ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = rule.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Uygulandı: ${rule.applied} kez • Operatör: ${rule.operator}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Switch(
                                    checked = rule.active,
                                    onCheckedChange = { onToggleRuleActive(rule, it) }
                                )
                            }

                            // Koşullar Özeti (IF)
                            rule.conditions.forEachIndexed { cIdx, cond ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = buildString {
                                            append(if (cIdx == 0) "EĞER: " else "${rule.operator}: ")
                                            append(MailxRuleCatalog.conditionLabel(cond.type))
                                            append(" • ")
                                            append(MailxRuleCatalog.matchLabel(cond.match))
                                            if (cond.values.isNotEmpty()) {
                                                append(" -> \"${cond.values.joinToString(", ")}\"")
                                            }
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Eylemler Özeti (THEN)
                            rule.actions.forEach { act ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = buildString {
                                            append("O ZAMAN: ")
                                            append(MailxRuleCatalog.actionLabel(act.type))
                                            if (act.value.isNotBlank() && act.value != "true") {
                                                append(" -> \"${act.value}\"")
                                            }
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Alt Butonlar (Yukarı/Aşağı Sırala, Düzenle, Sil)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { onMoveRuleOrder(rule.id, true) },
                                        enabled = index > 0
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Yukarı Taşı")
                                    }
                                    IconButton(
                                        onClick = { onMoveRuleOrder(rule.id, false) },
                                        enabled = index < rules.lastIndex
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Aşağı Taşı")
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { editingRule = rule },
                                        shape = CircleShape
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Düzenle")
                                    }
                                    FilledTonalButton(
                                        onClick = { onDeleteRule(rule.id) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = AddyPalette.SoftRed.copy(alpha = 0.14f),
                                            contentColor = AddyPalette.SoftRed
                                        )
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Sil", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // =================================================================
            // ENGELLENENLER LİSTESİ (BLOCKLIST - `43656.jpg`)
            // =================================================================
            item(key = "blocklist_header") {
                AddySectionHeader(
                    title = "Engellenen Göndericiler & Alan Adları",
                    countBadge = blocklist.size,
                    actionText = "Engelleme Ekle",
                    onActionClick = { showAddBlockDialog = true }
                )
            }

            if (blocklist.isEmpty()) {
                item(key = "blocklist_empty") {
                    EmptyStateBox(
                        icon = Icons.Default.Block,
                        title = "Engelleme listeniz boş",
                        subtitle = "İstenmeyen bir e-posta adresini (örn: spam@example.com) veya tüm bir alan adını (örn: spammer.com) engelleyebilirsiniz.",
                        actionLabel = "E-posta veya Alan Adı Engelle",
                        onActionClick = { showAddBlockDialog = true }
                    )
                }
            } else {
                itemsIndexed(blocklist, key = { _, b -> b.id.ifEmpty { b.value } }) { _, item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (item.type == "domain") Icons.Default.Language else Icons.Default.AlternateEmail,
                                    contentDescription = null,
                                    tint = AddyPalette.SoftRed
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = item.value,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = buildString {
                                            append(if (item.type == "domain") "Alan Adı" else "E-posta Adresi")
                                            append(" • Engellenen ileti: ${item.blocked}")
                                            if (item.createdAt.isNotBlank()) {
                                                append(" • ${formatIsoDateShort(item.createdAt)}")
                                            }
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(onClick = { onDeleteBlocklistItem(item.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Kaldır",
                                    tint = AddyPalette.SoftRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddBlockDialog) {
        var blockType by remember { mutableStateOf("email") }
        var blockValue by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddBlockDialog = false },
            title = { Text("E-posta veya Alan Adı Engelle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            selected = blockType == "email",
                            onClick = { blockType = "email" },
                            label = { Text("E-posta Adresi") }
                        )
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            selected = blockType == "domain",
                            onClick = { blockType = "domain" },
                            label = { Text("Alan Adı") }
                        )
                    }

                    OutlinedTextField(
                        value = blockValue,
                        onValueChange = { blockValue = it },
                        label = {
                            Text(if (blockType == "domain") "Alan Adı (örn: spam.com)" else "E-posta (örn: info@spam.com)")
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = blockValue.trim().isNotEmpty(),
                    onClick = {
                        val t = blockType
                        val v = blockValue.trim()
                        showAddBlockDialog = false
                        onAddBlocklistItem(t, v)
                    }
                ) {
                    Text("Engelle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBlockDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showCreateRuleSheet || editingRule != null) {
        RuleEditorBottomSheet(
            initialRule = editingRule,
            availableRecipients = availableRecipients,
            onDismiss = {
                showCreateRuleSheet = false
                editingRule = null
            },
            onSave = { id, name, op, fw, rp, sn, conds, acts, act ->
                showCreateRuleSheet = false
                editingRule = null
                onSaveRule(id, name, op, fw, rp, sn, conds, acts, act)
            }
        )
    }
}

/**
 * `43660.jpg`, `43662.jpg`, `43664.jpg`: NEW RULE / EDIT RULE alt penceresi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorBottomSheet(
    initialRule: UiRule?,
    availableRecipients: List<String>,
    onDismiss: () -> Unit,
    onSave: (
        ruleId: String?,
        name: String,
        operator: String,
        forwards: Boolean,
        replies: Boolean,
        sends: Boolean,
        conditions: List<UiRuleCondition>,
        actions: List<UiRuleAction>,
        active: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(initialRule) { mutableStateOf(initialRule?.name ?: "") }
    var operator by remember(initialRule) { mutableStateOf(initialRule?.operator ?: "AND") }
    var forwards by remember(initialRule) { mutableStateOf(initialRule?.forwards ?: true) }
    var replies by remember(initialRule) { mutableStateOf(initialRule?.replies ?: false) }
    var sends by remember(initialRule) { mutableStateOf(initialRule?.sends ?: false) }

    var condType by remember(initialRule) {
        mutableStateOf(initialRule?.conditions?.firstOrNull()?.type ?: "sender")
    }
    var condMatch by remember(initialRule) {
        mutableStateOf(initialRule?.conditions?.firstOrNull()?.match ?: "contains")
    }
    var condValue by remember(initialRule) {
        mutableStateOf(initialRule?.conditions?.firstOrNull()?.values?.joinToString(", ") ?: "")
    }

    var actionType by remember(initialRule) {
        mutableStateOf(
            initialRule?.actions?.firstOrNull { it.type != "stop" }?.type ?: "block"
        )
    }
    var actionValue by remember(initialRule) {
        mutableStateOf(
            initialRule?.actions?.firstOrNull { it.type != "stop" }?.value ?: "true"
        )
    }
    var stopProcessingMore by remember(initialRule) {
        mutableStateOf(initialRule?.actions?.any { it.type == "stop" } ?: false)
    }

    var showCondTypeDialog by remember { mutableStateOf(false) }
    var showMatchTypeDialog by remember { mutableStateOf(false) }
    var showActionTypeDialog by remember { mutableStateOf(false) }

    val isBooleanCondition = MailxRuleCatalog.booleanConditionTypes.contains(condType)
    val needsActionText = MailxRuleCatalog.textValueActions.contains(actionType)

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
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initialRule == null) "Yeni Kural Oluştur (New Rule)" else "Kuralı Düzenle",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Kural Adı (Rule Name)") },
                placeholder = { Text("Örn: Pazarlama Bültenlerini Engelle") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Hangi ileti türlerinde çalışsın (`Forwards`, `Replies`, `Sends`)
            Text("Uygulanacak İleti Türleri:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = forwards,
                    onClick = { forwards = !forwards },
                    label = { Text("İletilen (Forwards)") }
                )
                FilterChip(
                    selected = replies,
                    onClick = { replies = !replies },
                    label = { Text("Yanıtlar (Replies)") }
                )
                FilterChip(
                    selected = sends,
                    onClick = { sends = !sends },
                    label = { Text("Gönderim (Sends)") }
                )
            }

            // Operatör Seçimi (`AND` / `OR`)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    selected = operator == "AND",
                    onClick = { operator = "AND" },
                    label = { Text("VE (AND - Tüm Koşullar)") }
                )
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    selected = operator == "OR",
                    onClick = { operator = "OR" },
                    label = { Text("VEYA (OR - Herhangi Biri)") }
                )
            }

            // KOŞUL (IF) KARTI (`43662.jpg`)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("KOŞUL (IF)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    OutlinedButton(
                        onClick = { showCondTypeDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Alan: ${MailxRuleCatalog.conditionLabel(condType)}")
                    }

                    if (!isBooleanCondition) {
                        OutlinedButton(
                            onClick = { showMatchTypeDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Eşleşme: ${MailxRuleCatalog.matchLabel(condMatch)}")
                        }

                        OutlinedTextField(
                            value = condValue,
                            onValueChange = { condValue = it },
                            label = { Text("Aranan Değer (Virgülle birden fazla girilebilir)") },
                            placeholder = { Text("newsletter, promo@example.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Koşul Değeri Doğru mu? (True/False)", fontSize = 13.sp)
                            Switch(
                                checked = condValue != "false",
                                onCheckedChange = { condValue = if (it) "true" else "false" }
                            )
                        }
                    }
                }
            }

            // EYLEM (THEN) KARTI (`43664.jpg`)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("EYLEM (THEN)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    OutlinedButton(
                        onClick = { showActionTypeDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Eylem: ${MailxRuleCatalog.actionLabel(actionType)}")
                    }

                    if (needsActionText) {
                        if (actionType == "forward_to" && availableRecipients.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableRecipients.forEach { r ->
                                    FilterChip(
                                        selected = actionValue == r,
                                        onClick = { actionValue = r },
                                        label = { Text(r) }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = if (actionValue == "true") "" else actionValue,
                            onValueChange = { actionValue = it },
                            label = { Text("Eylem Değeri") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bu kural eşleştiğinde sonraki kuralları işlemeyi durdur (Stop processing more rules)",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Checkbox(
                            checked = stopProcessingMore,
                            onCheckedChange = { stopProcessingMore = it }
                        )
                    }
                }
            }

            Button(
                enabled = name.isNotBlank() && (isBooleanCondition || condValue.isNotBlank()),
                onClick = {
                    val valuesList = if (isBooleanCondition) {
                        listOf(if (condValue == "false") "false" else "true")
                    } else {
                        condValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    }
                    val condObj = UiRuleCondition(
                        type = condType,
                        match = if (isBooleanCondition) "is" else condMatch,
                        values = valuesList
                    )
                    val actList = mutableListOf(
                        UiRuleAction(
                            type = actionType,
                            value = if (needsActionText) actionValue.trim() else "true"
                        )
                    )
                    if (stopProcessingMore && actionType != "stop") {
                        actList.add(UiRuleAction(type = "stop", value = "true"))
                    }
                    onSave(
                        initialRule?.id,
                        name.trim(),
                        operator,
                        forwards,
                        replies,
                        sends,
                        listOf(condObj),
                        actList,
                        initialRule?.active ?: true
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = CircleShape
            ) {
                Text(if (initialRule == null) "Kuralı Oluştur" else "Kuralı Güncelle", fontSize = 16.sp)
            }
        }
    }

    if (showCondTypeDialog) {
        SimplePickerDialog(
            title = "Koşul Alanı Seçin (IF)",
            options = MailxRuleCatalog.conditionTypes,
            selectedKey = condType,
            onSelect = {
                condType = it
                showCondTypeDialog = false
            },
            onDismiss = { showCondTypeDialog = false }
        )
    }

    if (showMatchTypeDialog) {
        SimplePickerDialog(
            title = "Eşleşme Türü Seçin",
            options = MailxRuleCatalog.matchTypes,
            selectedKey = condMatch,
            onSelect = {
                condMatch = it
                showMatchTypeDialog = false
            },
            onDismiss = { showMatchTypeDialog = false }
        )
    }

    if (showActionTypeDialog) {
        SimplePickerDialog(
            title = "Eylem Seçin (THEN)",
            options = MailxRuleCatalog.actionTypes,
            selectedKey = actionType,
            onSelect = {
                actionType = it
                if (!MailxRuleCatalog.textValueActions.contains(it)) {
                    actionValue = "true"
                }
                showActionTypeDialog = false
            },
            onDismiss = { showActionTypeDialog = false }
        )
    }
}

@Composable
private fun SimplePickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(key) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedKey == key,
                            onClick = { onSelect(key) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}
