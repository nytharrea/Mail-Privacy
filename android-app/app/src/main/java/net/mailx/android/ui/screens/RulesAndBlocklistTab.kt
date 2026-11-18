package net.mailx.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.EmptyStateBox
import net.mailx.android.ui.components.MailxModernDialog
import net.mailx.android.ui.components.MailxOptionPickerDialog
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxSegmentedControl
import net.mailx.android.ui.components.mailxSwitchColors
import net.mailx.android.ui.components.mailxTextFieldColors
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "rules_blocklist_segmented") {
            MailxSegmentedControl(
                options = listOf("Rules", "Blocklist"),
                selectedIndex = selectedSection,
                onSelect = { selectedSection = it },
                badges = listOf(rules.size, blocklist.size)
            )
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
                        icon = Icons.Outlined.FilterAlt,
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
                    var showRuleMenu by remember { mutableStateOf(false) }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(15.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
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
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rule.name,
                                            fontSize = 15.sp,
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
                                            text = "Applied ${rule.applied}× • $relayLabels",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = rule.active,
                                        onCheckedChange = { onToggleRuleActive(rule, it) },
                                        colors = mailxSwitchColors()
                                    )
                                    Box {
                                        IconButton(
                                            onClick = { showRuleMenu = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.MoreVert,
                                                contentDescription = "Rule Actions",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showRuleMenu,
                                            onDismissRequest = { showRuleMenu = false },
                                            shape = RoundedCornerShape(14.dp),
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit Rule", fontSize = 13.5.sp) },
                                                leadingIcon = {
                                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                                },
                                                onClick = {
                                                    showRuleMenu = false
                                                    editingRule = rule
                                                    isDuplicatingRule = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Duplicate Rule", fontSize = 13.5.sp) },
                                                leadingIcon = {
                                                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                                },
                                                onClick = {
                                                    showRuleMenu = false
                                                    editingRule = rule
                                                    isDuplicatingRule = true
                                                }
                                            )
                                            if (index > 0) {
                                                DropdownMenuItem(
                                                    text = { Text("Move Up Priority", fontSize = 13.5.sp) },
                                                    leadingIcon = {
                                                        Icon(Icons.Outlined.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    },
                                                    onClick = {
                                                        showRuleMenu = false
                                                        onMoveRuleOrder(rule.id, true)
                                                    }
                                                )
                                            }
                                            if (index < rules.lastIndex) {
                                                DropdownMenuItem(
                                                    text = { Text("Move Down Priority", fontSize = 13.5.sp) },
                                                    leadingIcon = {
                                                        Icon(Icons.Outlined.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    },
                                                    onClick = {
                                                        showRuleMenu = false
                                                        onMoveRuleOrder(rule.id, false)
                                                    }
                                                )
                                            }
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        "Delete Rule",
                                                        fontSize = 13.5.sp,
                                                        color = MailxPalette.BlockedRose
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Outlined.DeleteOutline,
                                                        contentDescription = null,
                                                        tint = MailxPalette.BlockedRose,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                onClick = {
                                                    showRuleMenu = false
                                                    onDeleteRule(rule.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            rule.conditions.forEachIndexed { cIdx, cond ->
                                val isBool = MailxRuleCatalog.booleanConditionTypes.contains(cond.type)
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = buildString {
                                            append(if (cIdx == 0) "IF " else "${rule.operator} ")
                                            append(MailxRuleCatalog.conditionLabel(cond.type))
                                            if (!isBool) {
                                                append(" • ")
                                                append(MailxRuleCatalog.matchLabel(cond.match))
                                                if (cond.values.isNotEmpty()) {
                                                    append(" → \"${cond.values.joinToString(", ")}\"")
                                                }
                                            }
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
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
                                    shape = RoundedCornerShape(9.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = buildString {
                                            append("THEN ")
                                            append(MailxRuleCatalog.actionLabel(act.type))
                                            if (displayVal.isNotBlank() && displayVal != "true") {
                                                append(" → \"$displayVal\"")
                                            }
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                                    )
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
                        icon = Icons.Outlined.Block,
                        title = "Your blocklist is empty",
                        subtitle = "Block unwanted sender email addresses (e.g. spam@example.com) or entire domains (e.g. spammer.com).",
                        actionLabel = "Block Email or Domain",
                        onActionClick = { showAddBlockDialog = true }
                    )
                }
            } else {
                itemsIndexed(blocklist, key = { _, b -> b.id.ifEmpty { b.value } }) { _, item ->
                    Surface(
                        shape = RoundedCornerShape(15.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MailxPalette.BlockedRose.copy(alpha = 0.14f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (item.type == "domain") Icons.Outlined.Language else Icons.Outlined.AlternateEmail,
                                            contentDescription = null,
                                            tint = MailxPalette.BlockedRose,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = item.value,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
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
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Remove",
                                    tint = MailxPalette.BlockedRose,
                                    modifier = Modifier.size(18.dp)
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

        MailxModernDialog(
            title = "Block Email or Domain",
            subtitle = "Incoming emails matching this sender or domain will be rejected automatically.",
            icon = Icons.Outlined.Block,
            iconTint = MailxPalette.BlockedRose,
            confirmText = "Block",
            confirmEnabled = blockValue.trim().isNotEmpty(),
            isDestructive = true,
            onConfirm = {
                val t = blockType
                val v = blockValue.trim()
                showAddBlockDialog = false
                onAddBlocklistItem(t, v)
            },
            onDismiss = { showAddBlockDialog = false }
        ) {
            MailxSegmentedControl(
                options = listOf("Email Address", "Domain"),
                selectedIndex = if (blockType == "email") 0 else 1,
                onSelect = { idx -> blockType = if (idx == 0) "email" else "domain" },
                compact = true
            )

            OutlinedTextField(
                value = blockValue,
                onValueChange = { blockValue = it },
                label = {
                    Text(if (blockType == "domain") "Domain (e.g. spam.com)" else "Email (e.g. info@spam.com)")
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
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
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Text(
                text = when {
                    initialRule == null || duplicateMode -> "New Rule"
                    else -> "Edit Rule"
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            HorizontalDivider(
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(50) },
                label = { Text("Rule Name") },
                placeholder = { Text("e.g. Block Marketing Newsletters") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "APPLY TO EMAIL TYPES",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = forwards,
                    onClick = { forwards = !forwards },
                    shape = RoundedCornerShape(10.dp),
                    label = { Text("Forwards", fontSize = 12.5.sp) }
                )
                FilterChip(
                    selected = replies,
                    onClick = { replies = !replies },
                    shape = RoundedCornerShape(10.dp),
                    label = { Text("Replies", fontSize = 12.5.sp) }
                )
                FilterChip(
                    selected = sends,
                    onClick = { sends = !sends },
                    shape = RoundedCornerShape(10.dp),
                    label = { Text("Sends", fontSize = 12.5.sp) }
                )
            }

            MailxSegmentedControl(
                options = listOf("AND (All Conditions)", "OR (Any Condition)"),
                selectedIndex = if (operator == "AND") 0 else 1,
                onSelect = { idx -> operator = if (idx == 0) "AND" else "OR" }
            )

            MailxSectionHeader(
                title = "Conditions (${conditions.size}/5)",
                actionText = if (conditions.size < 5) "+ Add Condition" else null,
                onActionClick = {
                    if (conditions.size < 5) {
                        conditions = conditions + UiRuleCondition(type = "subject", match = "contains", values = emptyList())
                    }
                }
            )

            conditions.forEachIndexed { idx, cond ->
                val isBool = MailxRuleCatalog.booleanConditionTypes.contains(cond.type)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
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
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (conditions.size > 1) {
                                IconButton(
                                    onClick = { conditions = conditions.filterIndexed { i, _ -> i != idx } },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove Condition", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Surface(
                            onClick = { pickingCondTypeIndex = idx },
                            shape = RoundedCornerShape(11.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 13.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "If: ${MailxRuleCatalog.conditionLabel(cond.type)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isBool) {
                            Surface(
                                onClick = { pickingMatchTypeIndex = idx },
                                shape = RoundedCornerShape(11.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 13.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Match: ${MailxRuleCatalog.matchLabel(cond.match.ifEmpty { "contains" })}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                                colors = mailxTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            MailxSectionHeader(
                title = "Actions (${actions.size}/5)",
                actionText = if (actions.size < 5) "+ Add Action" else null,
                onActionClick = {
                    if (actions.size < 5) {
                        actions = actions + UiRuleAction(type = "replace_subject", value = "")
                    }
                }
            )

            actions.forEachIndexed { idx, act ->
                val needsText = MailxRuleCatalog.textValueActions.contains(act.type)
                val isForwardTo = act.type == "forward_to"

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
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
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (actions.size > 1) {
                                IconButton(
                                    onClick = { actions = actions.filterIndexed { i, _ -> i != idx } },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove Action", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Surface(
                            onClick = { pickingActionTypeIndex = idx },
                            shape = RoundedCornerShape(11.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 13.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Action: ${MailxRuleCatalog.actionLabel(act.type)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Outlined.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                                            shape = RoundedCornerShape(10.dp),
                                            label = { Text(recEmail, fontSize = 12.sp) }
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
                                colors = mailxTextFieldColors(),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    .height(48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (initialRule == null || duplicateMode) "Create Rule" else "Save Changes",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    pickingCondTypeIndex?.let { idx ->
        MailxOptionPickerDialog(
            title = "Select Condition (IF)",
            subtitle = "Choose which email field or property to inspect.",
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
        MailxOptionPickerDialog(
            title = "Select Match Type",
            subtitle = "Choose how the condition value should be matched.",
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
        MailxOptionPickerDialog(
            title = "Select Action (THEN)",
            subtitle = "Choose the action to execute when conditions match.",
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
