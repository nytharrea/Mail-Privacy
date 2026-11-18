package net.mailx.android.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ForwardToInbox
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.MailxActivityRing
import net.mailx.android.ui.components.MailxGroupDivider
import net.mailx.android.ui.components.MailxModernDialog
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxSectionRow
import net.mailx.android.ui.components.MailxSegmentedControl
import net.mailx.android.ui.components.MailxSettingsGroupCard
import net.mailx.android.ui.components.MailxTelemetryBar
import net.mailx.android.ui.components.mailxTextFieldColors
import net.mailx.android.ui.model.NatoPhoneticHelper
import net.mailx.android.ui.model.UiAlias
import net.mailx.android.ui.model.copyToClipboard
import net.mailx.android.ui.model.formatIsoDateShort
import net.mailx.android.ui.theme.MailxPalette

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = { showNato = !showNato },
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (showNato) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Spellcheck,
                        contentDescription = "NATO Phonetic",
                        tint = if (showNato) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(17.dp)
                    )
                }

                Text(
                    text = "Alias Inspector",
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                FilledTonalIconButton(
                    onClick = { onTogglePinned(!alias.pinned) },
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (alias.pinned) {
                            MailxPalette.PausedAmber.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = "Pin",
                        tint = if (alias.pinned) {
                            MailxPalette.PausedAmber
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = alias.name,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (alias.isPaused) {
                            Surface(
                                shape = CircleShape,
                                color = MailxPalette.PausedAmber.copy(alpha = 0.16f),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    text = "Paused until ${formatIsoDateShort(alias.pausedUntil)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MailxPalette.PausedAmber,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = showNato) {
                            val pairs = remember(alias.name) { NatoPhoneticHelper.toPhoneticPairs(alias.name) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                pairs.forEachIndexed { idx, (ch, word) ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = ch,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${idx + 1}.$word",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            MailxActivityRing(
                                forwards = alias.forwards,
                                replies = alias.replies,
                                sends = alias.sends,
                                blocks = alias.blocks,
                                enabled = alias.enabled,
                                size = 64.dp,
                                strokeWidth = 6.dp
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MailxTelemetryBar(
                                    forwards = alias.forwards,
                                    replies = alias.replies,
                                    sends = alias.sends,
                                    blocks = alias.blocks
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    ManageAliasStatLegendRow(
                                        icon = Icons.Outlined.Inbox,
                                        tint = MailxPalette.ForwardEmerald,
                                        text = "${alias.forwards} fwd"
                                    )
                                    ManageAliasStatLegendRow(
                                        icon = Icons.AutoMirrored.Outlined.Reply,
                                        tint = MailxPalette.ReplySky,
                                        text = "${alias.replies} rep"
                                    )
                                    ManageAliasStatLegendRow(
                                        icon = Icons.AutoMirrored.Outlined.ForwardToInbox,
                                        tint = MailxPalette.SentIndigo,
                                        text = "${alias.sends} sent"
                                    )
                                    ManageAliasStatLegendRow(
                                        icon = Icons.Outlined.Block,
                                        tint = MailxPalette.BlockedRose,
                                        text = "${alias.blocks} blk"
                                    )
                                }
                            }
                        }

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
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(11.dp)
                            ) {
                                Icon(
                                    imageVector = if (alias.wildcard) Icons.Outlined.AutoAwesome else Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (alias.wildcard) "Wildcard" else "Copy",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = onOpenSendFromAlias,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(11.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ForwardToInbox,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Send Email",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                MailxSectionHeader(title = "Routing & Controls")

                MailxSettingsGroupCard {
                    if (alias.deletedAt.isNotBlank()) {
                        MailxSectionRow(
                            title = "Restore Deleted Alias",
                            description = "Restore from Trash and resume forwarding",
                            startIcon = Icons.Outlined.Restore,
                            accentColor = MailxPalette.ForwardEmerald,
                            grouped = true,
                            onClick = onRestoreAlias
                        )
                        if (alias.isCustomDomain) {
                            MailxGroupDivider()
                            MailxSectionRow(
                                title = "Permanently Forget",
                                description = "Completely erase this custom-domain alias",
                                startIcon = Icons.Outlined.DeleteForever,
                                accentColor = MailxPalette.BlockedRose,
                                grouped = true,
                                onClick = onForgetAlias
                            )
                        }
                    } else {
                        MailxSectionRow(
                            title = if (alias.enabled) "Forwarding Active" else "Forwarding Disabled",
                            description = "Toggle incoming email forwarding for this alias",
                            startIcon = Icons.Outlined.CheckCircleOutline,
                            iconTint = MailxPalette.ForwardEmerald,
                            showSwitch = true,
                            switchChecked = alias.enabled,
                            onSwitchChange = onToggleEnabled,
                            grouped = true
                        )

                        MailxGroupDivider()
                        MailxSectionRow(
                            title = if (alias.isPaused) "Resume Alias" else "Snooze / Pause Temporarily",
                            description = if (alias.isPaused) {
                                "Paused until ${formatIsoDateShort(alias.pausedUntil)} • Tap to resume"
                            } else {
                                "Pause incoming forwards for 1-10 hours, days, or weeks"
                            },
                            startIcon = if (alias.isPaused) Icons.Outlined.PlayCircleOutline else Icons.Outlined.PauseCircleOutline,
                            iconTint = MailxPalette.PausedAmber,
                            grouped = true,
                            onClick = {
                                if (alias.isPaused) {
                                    onUnpauseAlias()
                                } else {
                                    showPauseDialog = true
                                }
                            }
                        )

                        MailxGroupDivider()
                        MailxSectionRow(
                            title = "Pin to Top",
                            description = "Keep this alias in Quick Access and at the top of lists",
                            startIcon = Icons.Outlined.PushPin,
                            iconTint = MailxPalette.BrandAccentTeal,
                            showSwitch = true,
                            switchChecked = alias.pinned,
                            onSwitchChange = onTogglePinned,
                            grouped = true
                        )
                    }
                }

                MailxSectionHeader(title = "Metadata & Destinations")

                MailxSettingsGroupCard {
                    MailxSectionRow(
                        title = "Description / Note",
                        description = alias.description.ifBlank { "No description set" },
                        startIcon = Icons.Outlined.Notes,
                        iconTint = MailxPalette.ReplySky,
                        endIcon = Icons.Outlined.Edit,
                        grouped = true,
                        onClick = { editingField = "description" }
                    )

                    MailxGroupDivider()
                    MailxSectionRow(
                        title = "Target Recipients",
                        description = alias.recipients.ifBlank { "Default account recipient" },
                        startIcon = Icons.Outlined.Inbox,
                        iconTint = MailxPalette.BrandAccentTeal,
                        endIcon = Icons.Outlined.Edit,
                        grouped = true,
                        onClick = { editingField = "recipients" }
                    )

                    MailxGroupDivider()
                    MailxSectionRow(
                        title = "Sender Display Name",
                        description = alias.fromName.ifBlank { "Uses alias address" },
                        startIcon = Icons.Outlined.Badge,
                        iconTint = MailxPalette.SentIndigo,
                        endIcon = Icons.Outlined.Edit,
                        grouped = true,
                        onClick = { editingField = "from_name" }
                    )

                    MailxGroupDivider()
                    MailxSectionRow(
                        title = "Timestamps",
                        description = "Created ${formatIsoDateShort(alias.createdAt)} • Updated ${formatIsoDateShort(alias.updatedAt)}",
                        startIcon = Icons.Outlined.Schedule,
                        iconTint = MailxPalette.Slate400,
                        grouped = true
                    )
                }

                if (alias.deletedAt.isBlank()) {
                    MailxSettingsGroupCard {
                        MailxSectionRow(
                            title = "Move to Trash",
                            description = "Reject incoming mail (restorable for 90 days)",
                            startIcon = Icons.Outlined.DeleteOutline,
                            accentColor = MailxPalette.BlockedRose,
                            grouped = true,
                            onClick = onRequestDelete
                        )
                        if (alias.isCustomDomain) {
                            MailxGroupDivider()
                            MailxSectionRow(
                                title = "Permanently Forget",
                                description = "Immediately purge this custom-domain alias",
                                startIcon = Icons.Outlined.DeleteForever,
                                accentColor = MailxPalette.BlockedRose,
                                grouped = true,
                                onClick = onForgetAlias
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPauseDialog) {
        var durationVal by remember { mutableIntStateOf(1) }
        var durationUnit by remember { mutableStateOf("days") }
        val units = listOf(
            "hours" to "Hours",
            "days" to "Days",
            "weeks" to "Weeks"
        )
        val unitIdx = units.indexOfFirst { it.first == durationUnit }.coerceAtLeast(0)

        MailxModernDialog(
            title = "Snooze Alias",
            subtitle = "How long should incoming forwards be paused?",
            icon = Icons.Outlined.PauseCircleOutline,
            iconTint = MailxPalette.PausedAmber,
            confirmText = "Pause Alias",
            onConfirm = {
                showPauseDialog = false
                onPauseAlias(durationVal, durationUnit)
            },
            onDismiss = { showPauseDialog = false }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Duration: $durationVal", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                Slider(
                    value = durationVal.toFloat(),
                    onValueChange = { durationVal = it.toInt().coerceIn(1, 10) },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.width(180.dp)
                )
            }

            MailxSegmentedControl(
                options = units.map { it.second },
                selectedIndex = unitIdx,
                onSelect = { idx -> durationUnit = units[idx].first },
                compact = true
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
        MailxModernDialog(
            title = when (field) {
                "description" -> "Edit Description"
                "from_name" -> "Edit Sender Name"
                else -> "Edit Recipients"
            },
            icon = Icons.Outlined.Edit,
            confirmText = "Save",
            onConfirm = {
                val d = if (field == "description") textVal.trim() else alias.description
                val f = if (field == "from_name") textVal.trim() else alias.fromName
                val r = if (field == "recipients") textVal.trim() else alias.recipients
                editingField = null
                onUpdateMetadata(d, f, r)
            },
            onDismiss = { editingField = null }
        ) {
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
                            shape = RoundedCornerShape(9.dp),
                            label = { Text(rec, fontSize = 12.sp) }
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
                colors = mailxTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
