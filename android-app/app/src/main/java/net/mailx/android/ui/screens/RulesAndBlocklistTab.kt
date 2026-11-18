package net.mailx.android.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.model.*
import net.mailx.android.ui.theme.MailxPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesAndBlocklistTab(
    rules: List<UiRule>,
    blocklist: List<UiBlocklistItem>,
    recipients: List<UiRecipient> = emptyList(),
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
    var selectedSection by remember { mutableIntStateOf(0) }
    var editingRule by remember { mutableStateOf<UiRule?>(null) }
    var isDuplicatingRule by remember { mutableStateOf(false) }
    var showCreateRuleSheet by remember { mutableStateOf(false) }
    var showAddBlockDialog by remember { mutableStateOf(false) }

    val recipientIdToEmail = remember(recipients, availableRecipients) {
        buildMap {
            recipients.forEach { r ->
                if (r.id.isNotBlank()) put(r.id, r.email)
                if (r.email.isNotBlank()) put(r.email, r.email)
            }
            availableRecipients.forEach { em -> put(em, em) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item(key = "rules_blocklist_segmented") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    label = { Text("Rules (${rules.size})") }
                )
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    label = { Text("Blocklist (${blocklist.size})") }
                )
            }
        }

        if (selectedSection == 0) {
            item(key = "rules_header") {
                MailxSectionHeader(
                    title = "Email Processing Rules",
                    countBadge = rules.size,
                    actionText = "New Rule",
                    onActionClick = {
                        editingRule = null
                        isDuplicatingRule = false
                        showCreateRuleSheet = true
                    }
                )
            }

            if (rules.isEmpty()) {
                item(key = "rules_empty") {
                    EmptyStateBox(
                        icon = Icons.Default.FilterAlt,
                        title = "No rules configured yet",
                        subtitle = "Create rules to automatically block, forward, replace subjects, or strip attachments based on sender, subject, or alias conditions.",
                        actionLabel = "Create Rule",
                        onActionClick = {
                            editingRule = null
                            isDuplicatingRule = false
                            showCreateRuleSheet = true
                        }
                    )
                }
            } else {
                itemsIndexed(rules, key = { _, r -> r.id.ifEmpty { r.name } }) { index, rule ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
                                        val relayLabels = buildList {
                                            if (rule.forwards) add("Forwards")
                                            if (rule.replies) add("Replies")
                                            if (rule.sends) add("Sends")
                                        }.joinToString(", ")
                                        Text(
                                            text = "Applied: ${rule.applied} times • $relayLabels",
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

                            rule.conditions.forEachIndexed { cIdx, cond ->
                                val isBool = MailxRuleCatalog.booleanConditionTypes.contains(cond.type)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = buildString {
                                            append(if (cIdx == 0) "IF: " else "${rule.operator}: ")
                                            append(MailxRuleCatalog.conditionLabel(cond.type))
                                            if (!isBool) {
                                                append(" • ")
                                                append(MailxRuleCatalog.matchLabel(cond.match))
                                                if (cond.values.isNotEmpty()) {
                                                    append(" -> \"${cond.values.joinToString(", ")}\"")
                                                }
                                            }
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            rule.actions.forEach { act ->
                                val displayVal = if (act.type == "forward_to") {
                                    act.value.split(",")
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .joinToString(", ") { id -> recipientIdToEmail[id] ?: id }
                                } else {
                                    act.value
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = buildString {
                                            append("THEN: ")
                                            append(MailxRuleCatalog.actionLabel(act.type))
                                            if (displayVal.isNotBlank() && displayVal != "true") {
                                                append(" -> \"$displayVal\"")
                                            }
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

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
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                                    }
                                    IconButton(
                                        onClick = { onMoveRuleOrder(rule.id, false) },
                                        enabled = index < rules.lastIndex
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                                    }
                                    IconButton(
                                        onClick = {
                                            editingRule = rule
                                            isDuplicatingRule = true
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Rule")
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            editingRule = rule
                                            isDuplicatingRule = false
                                        },
                                        shape = CircleShape
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Edit")
                                    }
                                    FilledTonalButton(
                                        onClick = { onDeleteRule(rule.id) },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MailxPalette.BlockedRose.copy(alpha = 0.14f),
                                            contentColor = MailxPalette.BlockedRose
                                        )
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "blocklist_header") {
                MailxSectionHeader(
                    title = "Blocked Senders & Domains",
                    countBadge = blocklist.size,
                    actionText = "Add Block",
                    onActionClick = { showAddBlockDialog = true }
                )
            }

            if (blocklist.isEmpty()) {
                item(key = "blocklist_empty") {
                    EmptyStateBox(
                        icon = Icons.Default.Block,
                        title = "Your blocklist is empty",
                        subtitle = "Block unwanted sender email addresses (e.g. spam@example.com) or entire domains (e.g. spammer.com).",
                        actionLabel = "Block Email or Domain",
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
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
                                    tint = MailxPalette.BlockedRose
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
                                            append(if (item.type == "domain") "Domain" else "Email Address")
                                            append(" • Blocked: ${item.blocked}")
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
                                    contentDescription = "Remove",
                                    tint = MailxPalette.BlockedRose
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
            title = { Text("Block Email or Domain") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            selected = blockType == "email",
                            onClick = { blockType = "email" },
                            label = { Text("Email Address") }
                        )
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            selected = blockType == "domain",
                            onClick = { blockType = "domain" },
                            label = { Text("Domain") }
                        )
                    }

                    OutlinedTextField(
                        value = blockValue,
                        onValueChange = { blockValue = it },
                        label = {
                            Text(if (blockType == "domain") "Domain (e.g. spam.com)" else "Email (e.g. info@spam.com)")
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
                    Text("Block")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBlockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateRuleSheet || editingRule != null) {
        RuleEditorBottomSheet(
            initialRule = editingRule,
            duplicateMode = isDuplicatingRule,
            recipients = recipients,
            availableRecipients = availableRecipients,
            onDismiss = {
                showCreateRuleSheet = false
                editingRule = null
                isDuplicatingRule = false
            },
            onSave = { id, name, op, fw, rp, sn, conds, acts, act ->
                showCreateRuleSheet = false
                editingRule = null
                isDuplicatingRule = false
                onSaveRule(id, name, op, fw, rp, sn, conds, acts, act)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorBottomSheet(
    initialRule: UiRule?,
    duplicateMode: Boolean,
    recipients: List<UiRecipient>,
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
    var name by remember(initialRule, duplicateMode) {
        val base = initialRule?.name ?: ""
        mutableStateOf(
            if (duplicateMode && base.isNotEmpty()) {
                "${base.take(43).trimEnd()} (copy)"
            } else {
                base
            }
        )
    }
    var operator by remember(initialRule) { mutableStateOf(initialRule?.operator ?: "AND") }
    var forwards by remember(initialRule) { mutableStateOf(initialRule?.forwards ?: true) }
    var replies by remember(initialRule) { mutableStateOf(initialRule?.replies ?: false) }
    var sends by remember(initialRule) { mutableStateOf(initialRule?.sends ?: false) }

    var conditions by remember(initialRule) {
        mutableStateOf(
            if (!initialRule?.conditions.isNullOrEmpty()) {
                initialRule!!.conditions
            } else {
                listOf(UiRuleCondition(type = "subject", match = "contains", values = emptyList()))
            }
        )
    }

    var actions by remember(initialRule) {
        mutableStateOf(
            if (!initialRule?.actions.isNullOrEmpty()) {
                initialRule!!.actions.filter { it.type != "stop" }.ifEmpty {
                    listOf(UiRuleAction(type = "block", value = ""))
                }
            } else {
                listOf(UiRuleAction(type = "block", value = ""))
            }
        )
    }

    var stopProcessingMore by remember(initialRule) {
        mutableStateOf(initialRule?.actions?.any { it.type == "stop" } ?: false)
    }

    var pickingCondTypeIndex by remember { mutableStateOf<Int?>(null) }
    var pickingMatchTypeIndex by remember { mutableStateOf<Int?>(null) }
    var pickingActionTypeIndex by remember { mutableStateOf<Int?>(null) }

    val verifiedRecipientPairs = remember(recipients, availableRecipients) {
        val realVerified = recipients.filter { it.isActive && !it.id.startsWith("fallback_") && !it.id.startsWith("default_") }
        if (realVerified.isNotEmpty()) {
            realVerified.map { it.id to it.email }
        } else {
            availableRecipients.map { it to it }
        }
    }

    val isValid = remember(name, forwards, replies, sends, conditions, actions) {
        name.trim().isNotEmpty() &&
            (forwards || replies || sends) &&
            conditions.all { c ->
                MailxRuleCatalog.booleanConditionTypes.contains(c.type) ||
                    c.values.any { it.isNotBlank() }
            } &&
            actions.all { a ->
                when (a.type) {
                    "replace_subject", "set_display_name", "forward_to" -> a.value.isNotBlank()
                    else -> true
                }
            }
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
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = when {
                    initialRule == null || duplicateMode -> "New Rule"
                    else -> "Edit Rule"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(50) },
                label = { Text("Rule Name") },
                placeholder = { Text("e.g. Block Marketing Newsletters") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Apply to Email Types:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = forwards,
                    onClick = { forwards = !forwards },
                    label = { Text("Forwards") }
                )
                FilterChip(
                    selected = replies,
                    onClick = { replies = !replies },
                    label = { Text("Replies") }
                )
                FilterChip(
                    selected = sends,
                    onClick = { sends = !sends },
                    label = { Text("Sends") }
                )
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    selected = operator == "AND",
                    onClick = { operator = "AND" },
                    label = { Text("AND (All Conditions)") }
                )
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    selected = operator == "OR",
                    onClick = { operator = "OR" },
                    label = { Text("OR (Any Condition)") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CONDITIONS (${conditions.size}/5)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (conditions.size < 5) {
                    TextButton(
                        onClick = {
                            conditions = conditions + UiRuleCondition(type = "subject", match = "contains", values = emptyList())
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Condition")
                    }
                }
            }

            conditions.forEachIndexed { idx, cond ->
                val isBool = MailxRuleCatalog.booleanConditionTypes.contains(cond.type)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (idx == 0) "IF" else operator,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (conditions.size > 1) {
                                IconButton(
                                    onClick = { conditions = conditions.filterIndexed { i, _ -> i != idx } },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove Condition", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { pickingCondTypeIndex = idx },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("If: ${MailxRuleCatalog.conditionLabel(cond.type)}")
                        }

                        if (!isBool) {
                            OutlinedButton(
                                onClick = { pickingMatchTypeIndex = idx },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Match: ${MailxRuleCatalog.matchLabel(cond.match.ifEmpty { "contains" })}")
                            }

                            OutlinedTextField(
                                value = cond.values.joinToString(", "),
                                onValueChange = { raw ->
                                    val split = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    conditions = conditions.toMutableList().also { list ->
                                        list[idx] = cond.copy(values = split)
                                    }
                                },
                                label = { Text("Values (comma-separated)") },
                                placeholder = { Text("newsletter, promo@example.com") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ACTIONS (${actions.size}/5)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (actions.size < 5) {
                    TextButton(
                        onClick = {
                            actions = actions + UiRuleAction(type = "replace_subject", value = "")
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Action")
                    }
                }
            }

            actions.forEachIndexed { idx, act ->
                val needsText = MailxRuleCatalog.textValueActions.contains(act.type)
                val isForwardTo = act.type == "forward_to"

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (idx == 0) "THEN" else "AND",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (actions.size > 1) {
                                IconButton(
                                    onClick = { actions = actions.filterIndexed { i, _ -> i != idx } },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove Action", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { pickingActionTypeIndex = idx },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Action: ${MailxRuleCatalog.actionLabel(act.type)}")
                        }

                        if (isForwardTo) {
                            if (verifiedRecipientPairs.isNotEmpty()) {
                                val currentIds = act.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    verifiedRecipientPairs.forEach { (recId, recEmail) ->
                                        val isChecked = currentIds.contains(recId) || currentIds.contains(recEmail)
                                        FilterChip(
                                            selected = isChecked,
                                            onClick = {
                                                val next = currentIds.toMutableSet()
                                                if (isChecked) {
                                                    next.remove(recId)
                                                    next.remove(recEmail)
                                                } else {
                                                    next.add(recId)
                                                }
                                                actions = actions.toMutableList().also { list ->
                                                    list[idx] = act.copy(value = next.joinToString(","))
                                                }
                                            },
                                            label = { Text(recEmail) }
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "No verified recipients found.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else if (needsText) {
                            OutlinedTextField(
                                value = act.value,
                                onValueChange = { v ->
                                    actions = actions.toMutableList().also { list ->
                                        list[idx] = act.copy(value = v.take(100))
                                    }
                                },
                                label = {
                                    Text(
                                        when (act.type) {
                                            "replace_subject" -> "Replacement Subject (use {subject} for original)"
                                            "set_display_name" -> "Sender Display Name"
                                            else -> "Alias Description"
                                        }
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Stop processing more rules when this rule matches",
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = stopProcessingMore,
                    onCheckedChange = { stopProcessingMore = it }
                )
            }

            Button(
                enabled = isValid,
                onClick = {
                    val normalizedConditions = conditions.map { c ->
                        val isBool = MailxRuleCatalog.booleanConditionTypes.contains(c.type)
                        UiRuleCondition(
                            type = c.type,
                            match = if (isBool) "" else c.match.ifEmpty { "contains" },
                            values = if (isBool) emptyList() else c.values
                        )
                    }
                    val normalizedActions = actions.map { a ->
                        val needsVal = MailxRuleCatalog.textValueActions.contains(a.type) || a.type == "forward_to"
                        UiRuleAction(
                            type = a.type,
                            value = if (needsVal) a.value.trim() else ""
                        )
                    }.toMutableList()

                    if (stopProcessingMore && normalizedActions.none { it.type == "stop" }) {
                        normalizedActions.add(UiRuleAction(type = "stop", value = ""))
                    }

                    onSave(
                        if (duplicateMode) null else initialRule?.id,
                        name.trim(),
                        operator,
                        forwards,
                        replies,
                        sends,
                        normalizedConditions,
                        normalizedActions,
                        initialRule?.active ?: true
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = CircleShape
            ) {
                Text(
                    text = if (initialRule == null || duplicateMode) "Create Rule" else "Save Changes",
                    fontSize = 16.sp
                )
            }
        }
    }

    pickingCondTypeIndex?.let { idx ->
        SimplePickerDialog(
            title = "Select Condition (IF)",
            options = MailxRuleCatalog.conditionTypes,
            selectedKey = conditions[idx].type,
            onSelect = { selectedType ->
                val isBool = MailxRuleCatalog.booleanConditionTypes.contains(selectedType)
                conditions = conditions.toMutableList().also { list ->
                    list[idx] = conditions[idx].copy(
                        type = selectedType,
                        match = if (isBool) "" else "contains",
                        values = emptyList()
                    )
                }
                pickingCondTypeIndex = null
            },
            onDismiss = { pickingCondTypeIndex = null }
        )
    }

    pickingMatchTypeIndex?.let { idx ->
        SimplePickerDialog(
            title = "Select Match Type",
            options = MailxRuleCatalog.matchTypes,
            selectedKey = conditions[idx].match.ifEmpty { "contains" },
            onSelect = { selectedMatch ->
                conditions = conditions.toMutableList().also { list ->
                    list[idx] = conditions[idx].copy(match = selectedMatch)
                }
                pickingMatchTypeIndex = null
            },
            onDismiss = { pickingMatchTypeIndex = null }
        )
    }

    pickingActionTypeIndex?.let { idx ->
        SimplePickerDialog(
            title = "Select Action (THEN)",
            options = MailxRuleCatalog.actionTypes,
            selectedKey = actions[idx].type,
            onSelect = { selectedAction ->
                actions = actions.toMutableList().also { list ->
                    list[idx] = UiRuleAction(type = selectedAction, value = "")
                }
                pickingActionTypeIndex = null
            },
            onDismiss = { pickingActionTypeIndex = null }
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
                Text("Close")
            }
        }
    )
}
