package net.mailx.android.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.AddySectionHeader
import net.mailx.android.ui.components.AddySectionView
import net.mailx.android.ui.model.DefaultsUiState
import net.mailx.android.ui.model.MailxRuleCatalog
import net.mailx.android.ui.model.ThemeUiState
import net.mailx.android.ui.model.formatAliasTypeLabel
import net.mailx.android.ui.theme.AddyPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    baseUrl: String,
    defaultsState: DefaultsUiState,
    themeState: ThemeUiState,
    onUpdateDefaultDomain: (String) -> Unit,
    onUpdateDefaultRecipient: (String) -> Unit = {},
    onUpdateDefaultFormat: (String) -> Unit,
    onUpdateDefaultFromName: (String) -> Unit = {},
    onUpdateIncludeMailxHeader: (Boolean) -> Unit = {},
    onUpdateBlockTrackers: (Boolean) -> Unit = {},
    onUpdateUnsubscribeBehaviour: (Int) -> Unit = {},
    onUpdateLogIssues: (Boolean) -> Unit = {},
    onUpdateDarkModeMode: (Int) -> Unit = {},
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleAmoledDark: (Boolean) -> Unit,
    onSyncDefaults: () -> Unit,
    onOpenReverseAliasTool: () -> Unit,
    onOpenTrackersReportSheet: () -> Unit = {},
    onOpenDiagnosticsSheet: () -> Unit = {},
    onOpenAccountSheet: () -> Unit = {},
    onOpenAnnouncementsSheet: () -> Unit = {},
    onRequestLogout: () -> Unit
) {
    var showInterfaceSheet by remember { mutableStateOf(false) }
    var showDomainDialog by remember { mutableStateOf(false) }
    var showRecipientDialog by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var showFromNameDialog by remember { mutableStateOf(false) }
    var showUnsubscribeDialog by remember { mutableStateOf(false) }

    val darkModeLabel = when (themeState.darkModeMode) {
        1 -> "Dark Mode: On"
        0 -> "Dark Mode: Off"
        else -> "Dark Mode: System Default"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header_general") {
            AddySectionHeader(title = "General & Appearance")
        }

        item(key = "section_uiux_interface") {
            AddySectionView(
                title = "Appearance & Interface",
                description = "$darkModeLabel • Customize theme and color options",
                startIcon = Icons.Default.Brightness6,
                onClick = { showInterfaceSheet = true }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item(key = "section_dynamic_colors") {
                AddySectionView(
                    title = "Dynamic Colors",
                    description = "Adapt application colors to your device wallpaper",
                    startIcon = Icons.Default.Palette,
                    showSwitch = true,
                    switchChecked = themeState.dynamicColorEnabled,
                    onSwitchChange = onToggleDynamicColor
                )
            }
        }

        item(key = "section_amoled_dark") {
            AddySectionView(
                title = "Pure Black Theme (AMOLED)",
                description = "Use a pure black background in dark mode",
                startIcon = Icons.Default.Contrast,
                showSwitch = true,
                switchChecked = themeState.amoledDarkEnabled,
                onSwitchChange = onToggleAmoledDark
            )
        }

        item(key = "section_mailto_integration") {
            AddySectionView(
                title = "Send from Alias",
                description = "Generate reverse-alias forwarding addresses for recipients",
                startIcon = Icons.AutoMirrored.Filled.Send,
                onClick = onOpenReverseAliasTool
            )
        }

        item(key = "section_trackers_report_decoder") {
            AddySectionView(
                title = "Trackers Report Inspector",
                description = "Decode removed email tracker links locally without contacting any server",
                startIcon = Icons.Default.Policy,
                onClick = onOpenTrackersReportSheet
            )
        }

        item(key = "header_defaults") {
            AddySectionHeader(
                title = "Mailx Server Settings",
                actionText = "Sync",
                onActionClick = onSyncDefaults,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item(key = "section_default_domain") {
            AddySectionView(
                title = "Default Domain",
                description = "@${defaultsState.defaultDomain}",
                startIcon = Icons.Default.Language,
                endIcon = Icons.Default.Edit,
                onClick = { showDomainDialog = true }
            )
        }

        item(key = "section_default_recipient") {
            AddySectionView(
                title = "Default Recipient",
                description = defaultsState.defaultRecipient.ifBlank { "Select default recipient" },
                startIcon = Icons.Default.Inbox,
                endIcon = Icons.Default.Edit,
                onClick = { showRecipientDialog = true }
            )
        }

        item(key = "section_default_format") {
            AddySectionView(
                title = "Default Alias Format",
                description = formatAliasTypeLabel(defaultsState.defaultFormat),
                startIcon = Icons.Default.AlternateEmail,
                endIcon = Icons.Default.Edit,
                onClick = { showFormatDialog = true }
            )
        }

        item(key = "section_from_name") {
            AddySectionView(
                title = "From Name",
                description = defaultsState.defaultFromName.ifBlank {
                    "Uses the alias email address when left blank"
                },
                startIcon = Icons.Default.Badge,
                endIcon = Icons.Default.Edit,
                onClick = { showFromNameDialog = true }
            )
        }

        item(key = "section_mailx_header") {
            val includeHeader = !defaultsState.removeHeader
            AddySectionView(
                title = "Add Mailx Header",
                description = "Include 'Sent to <alias> from <sender>' banner in forwarded emails",
                startIcon = Icons.Default.MailOutline,
                showSwitch = true,
                switchChecked = includeHeader,
                onSwitchChange = onUpdateIncludeMailxHeader
            )
        }

        item(key = "section_block_trackers") {
            AddySectionView(
                title = "Block Email Trackers",
                description = "Remove known tracking pixels and tracking links from forwarded emails",
                startIcon = Icons.Default.Shield,
                showSwitch = true,
                switchChecked = defaultsState.blockTrackers,
                onSwitchChange = onUpdateBlockTrackers
            )
        }

        item(key = "section_unsubscribe_behaviour") {
            AddySectionView(
                title = "Unsubscribe Behaviour",
                description = MailxRuleCatalog.unsubscribeLabel(defaultsState.unsubscribeBehaviour),
                startIcon = Icons.Default.Unsubscribe,
                endIcon = Icons.Default.Edit,
                onClick = { showUnsubscribeDialog = true }
            )
        }

        item(key = "section_enable_diagnostics") {
            AddySectionView(
                title = "Enable Diagnostics Logging",
                description = "Log delivery failures, bounced messages, and rule-blocked emails",
                startIcon = Icons.Default.BugReport,
                showSwitch = true,
                switchChecked = defaultsState.logIssues,
                onSwitchChange = onUpdateLogIssues
            )
        }

        item(key = "section_open_diagnostics") {
            AddySectionView(
                title = "View & Clear Diagnostic Logs",
                description = "Inspect delivery logs, view raw bounce files, or delete all logs",
                startIcon = Icons.Default.ReceiptLong,
                onClick = onOpenDiagnosticsSheet
            )
        }

        item(key = "header_manage_data") {
            AddySectionHeader(
                title = "Account, Security & Data",
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item(key = "section_account_security") {
            AddySectionView(
                title = "Account, Subscription & Security",
                description = "Subscription status, 2FA (TOTP), passkeys, access keys, password, and CSV import/export",
                startIcon = Icons.Default.ManageAccounts,
                onClick = onOpenAccountSheet
            )
        }

        item(key = "section_announcements") {
            AddySectionView(
                title = "Announcements",
                description = "Mailx service updates and announcements",
                startIcon = Icons.Default.Campaign,
                onClick = onOpenAnnouncementsSheet
            )
        }

        item(key = "section_reset_logout") {
            AddySectionView(
                title = "Reset App (Log Out)",
                description = "Clear all local session data and cache on this device and sign out",
                startIcon = Icons.Default.RestartAlt,
                accentColor = AddyPalette.SoftRed,
                onClick = onRequestLogout
            )
        }

        item(key = "settings_footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailUnread,
                            contentDescription = "Mailx",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Text(
                    text = "Designed with privacy and care",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "v0.3.0 • $baseUrl",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showInterfaceSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showInterfaceSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Appearance & Interface",
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        AddySectionView(
                            title = "Dynamic Colors",
                            description = "Adapt application colors to your device wallpaper",
                            startIcon = Icons.Default.Palette,
                            showSwitch = true,
                            switchChecked = themeState.dynamicColorEnabled,
                            onSwitchChange = onToggleDynamicColor
                        )
                    }

                    AddySectionView(
                        title = "Pure Black Theme (AMOLED)",
                        description = "Use a pure black background in dark mode",
                        startIcon = Icons.Default.Contrast,
                        showSwitch = true,
                        switchChecked = themeState.amoledDarkEnabled,
                        onSwitchChange = onToggleAmoledDark
                    )

                    Text(
                        text = "Dark Mode",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            selected = themeState.darkModeMode == 1,
                            onClick = { onUpdateDarkModeMode(1) },
                            label = { Text("On") }
                        )
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            selected = themeState.darkModeMode == -1,
                            onClick = { onUpdateDarkModeMode(-1) },
                            label = { Text("System") }
                        )
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            selected = themeState.darkModeMode == 0,
                            onClick = { onUpdateDarkModeMode(0) },
                            label = { Text("Off") }
                        )
                    }
                }
            }
        }
    }

    if (showDomainDialog) {
        AlertDialog(
            onDismissRequest = { showDomainDialog = false },
            title = { Text("Default Domain") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    defaultsState.availableDomains.forEach { dom ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onUpdateDefaultDomain(dom)
                                    showDomainDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultsState.defaultDomain == dom,
                                onClick = {
                                    onUpdateDefaultDomain(dom)
                                    showDomainDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("@$dom")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDomainDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showRecipientDialog) {
        var customRec by remember { mutableStateOf(defaultsState.defaultRecipient) }
        AlertDialog(
            onDismissRequest = { showRecipientDialog = false },
            title = { Text("Default Recipient") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    defaultsState.availableRecipients.forEach { rec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onUpdateDefaultRecipient(rec)
                                    showRecipientDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultsState.defaultRecipient == rec,
                                onClick = {
                                    onUpdateDefaultRecipient(rec)
                                    showRecipientDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(rec)
                        }
                    }
                    OutlinedTextField(
                        value = customRec,
                        onValueChange = { customRec = it },
                        label = { Text("Recipient Email Address") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRecipientDialog = false
                        if (customRec.isNotBlank()) onUpdateDefaultRecipient(customRec.trim())
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecipientDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showFormatDialog) {
        val formats = listOf("words", "random", "uuid")
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Default Alias Format") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    formats.forEach { fmt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onUpdateDefaultFormat(fmt)
                                    showFormatDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultsState.defaultFormat == fmt,
                                onClick = {
                                    onUpdateDefaultFormat(fmt)
                                    showFormatDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(formatAliasTypeLabel(fmt))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFormatDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showFromNameDialog) {
        var nameInput by remember { mutableStateOf(defaultsState.defaultFromName) }
        AlertDialog(
            onDismissRequest = { showFromNameDialog = false },
            title = { Text("From Name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Default display name used when replying from or sending via an alias (leave blank to use the alias email address):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("From Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFromNameDialog = false
                        onUpdateDefaultFromName(nameInput)
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFromNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUnsubscribeDialog) {
        AlertDialog(
            onDismissRequest = { showUnsubscribeDialog = false },
            title = { Text("Unsubscribe Behaviour") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MailxRuleCatalog.unsubscribeBehaviours.forEach { (code, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onUpdateUnsubscribeBehaviour(code)
                                    showUnsubscribeDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultsState.unsubscribeBehaviour == code,
                                onClick = {
                                    onUpdateUnsubscribeBehaviour(code)
                                    showUnsubscribeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUnsubscribeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
