package net.mailx.android.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MotionPhotosOff
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Unsubscribe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxSectionRow
import net.mailx.android.ui.model.DefaultsUiState
import net.mailx.android.ui.model.MailxRuleCatalog
import net.mailx.android.ui.model.ThemeUiState
import net.mailx.android.ui.model.formatAliasTypeLabel
import net.mailx.android.ui.theme.MailxIcons
import net.mailx.android.ui.theme.MailxPalette

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
    onToggleExpressiveMotion: (Boolean) -> Unit = {},
    onToggleReducedMotion: (Boolean) -> Unit = {},
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
        1 -> "Dark"
        0 -> "Light"
        else -> "System"
    }

    val motionModeLabel = when {
        themeState.reducedMotionEnabled -> "Reduced Motion"
        themeState.expressiveMotionEnabled -> "Expressive Motion"
        else -> "Standard Motion"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header_general") {
            MailxSectionHeader(title = "Appearance & Privacy Tools")
        }

        item(key = "section_uiux_interface") {
            MailxSectionRow(
                title = "Theme & Motion Preferences",
                description = "Theme: $darkModeLabel • $motionModeLabel",
                startIcon = MailxIcons.MoonStar,
                endIcon = MailxIcons.ChevronRight,
                onClick = { showInterfaceSheet = true }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item(key = "section_dynamic_colors") {
                MailxSectionRow(
                    title = "Material You Dynamic Color",
                    description = "Optional system wallpaper colors (off by default to preserve Mailx Teal)",
                    startIcon = MailxIcons.Palette,
                    showSwitch = true,
                    switchChecked = themeState.dynamicColorEnabled,
                    onSwitchChange = onToggleDynamicColor
                )
            }
        }

        item(key = "section_amoled_dark") {
            MailxSectionRow(
                title = "Pure Black Surface (AMOLED)",
                description = "True #000000 background with WCAG AA high-contrast text",
                startIcon = Icons.Outlined.Contrast,
                showSwitch = true,
                switchChecked = themeState.amoledDarkEnabled,
                onSwitchChange = onToggleAmoledDark
            )
        }

        item(key = "section_reduced_motion") {
            MailxSectionRow(
                title = "Reduced Motion",
                description = "Minimize transitions and disable pulsing animations",
                startIcon = Icons.Outlined.MotionPhotosOff,
                showSwitch = true,
                switchChecked = themeState.reducedMotionEnabled,
                onSwitchChange = onToggleReducedMotion
            )
        }

        item(key = "section_expressive_motion") {
            MailxSectionRow(
                title = "Expressive Spring Motion",
                description = "Optional spring physics (default uses calm MotionScheme.standard())",
                startIcon = MailxIcons.Sparkles,
                showSwitch = true,
                enabled = !themeState.reducedMotionEnabled,
                switchChecked = themeState.expressiveMotionEnabled && !themeState.reducedMotionEnabled,
                onSwitchChange = onToggleExpressiveMotion
            )
        }

        item(key = "section_mailto_integration") {
            MailxSectionRow(
                title = "Send via Reverse Alias",
                description = "Generate outbound relay addresses for any recipient",
                startIcon = MailxIcons.Send,
                endIcon = MailxIcons.ChevronRight,
                onClick = onOpenReverseAliasTool
            )
        }

        item(key = "section_trackers_report_decoder") {
            MailxSectionRow(
                title = "Trackers Report Decoder",
                description = "Decode removed email tracker fragments offline in Rust",
                startIcon = MailxIcons.ShieldCheck,
                endIcon = MailxIcons.ChevronRight,
                onClick = onOpenTrackersReportSheet
            )
        }

        item(key = "header_defaults") {
            MailxSectionHeader(
                title = "Server Forwarding Defaults",
                actionText = "Sync",
                onActionClick = onSyncDefaults,
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        item(key = "section_default_domain") {
            MailxSectionRow(
                title = "Default Domain",
                description = "@${defaultsState.defaultDomain}",
                startIcon = MailxIcons.Globe,
                endIcon = MailxIcons.Edit3,
                onClick = { showDomainDialog = true }
            )
        }

        item(key = "section_default_recipient") {
            MailxSectionRow(
                title = "Default Recipient",
                description = defaultsState.defaultRecipient.ifBlank { "Select default recipient" },
                startIcon = MailxIcons.Inbox,
                endIcon = MailxIcons.Edit3,
                onClick = { showRecipientDialog = true }
            )
        }

        item(key = "section_default_format") {
            MailxSectionRow(
                title = "Default Alias Format",
                description = formatAliasTypeLabel(defaultsState.defaultFormat),
                startIcon = MailxIcons.AtSign,
                endIcon = MailxIcons.Edit3,
                onClick = { showFormatDialog = true }
            )
        }

        item(key = "section_from_name") {
            MailxSectionRow(
                title = "Default Sender Name",
                description = defaultsState.defaultFromName.ifBlank {
                    "Uses the alias address when left blank"
                },
                startIcon = Icons.Outlined.Badge,
                endIcon = MailxIcons.Edit3,
                onClick = { showFromNameDialog = true }
            )
        }

        item(key = "section_mailx_header") {
            val includeHeader = !defaultsState.removeHeader
            MailxSectionRow(
                title = "Include Relay Banner Header",
                description = "Show 'Sent to <alias> from <sender>' header in forwarded emails",
                startIcon = Icons.Outlined.MailOutline,
                showSwitch = true,
                switchChecked = includeHeader,
                onSwitchChange = onUpdateIncludeMailxHeader
            )
        }

        item(key = "section_block_trackers") {
            MailxSectionRow(
                title = "Strip Spy Pixels & Trackers",
                description = "Automatically remove tracking pixels and spy links before forwarding",
                startIcon = MailxIcons.ShieldCheck,
                showSwitch = true,
                switchChecked = defaultsState.blockTrackers,
                onSwitchChange = onUpdateBlockTrackers
            )
        }

        item(key = "section_unsubscribe_behaviour") {
            MailxSectionRow(
                title = "List-Unsubscribe Action",
                description = MailxRuleCatalog.unsubscribeLabel(defaultsState.unsubscribeBehaviour),
                startIcon = Icons.Outlined.Unsubscribe,
                endIcon = MailxIcons.Edit3,
                onClick = { showUnsubscribeDialog = true }
            )
        }

        item(key = "section_enable_diagnostics") {
            MailxSectionRow(
                title = "Diagnostic Issue Logging",
                description = "Record delivery errors, bounces, and rule blocks",
                startIcon = MailxIcons.Bug,
                showSwitch = true,
                switchChecked = defaultsState.logIssues,
                onSwitchChange = onUpdateLogIssues
            )
        }

        item(key = "section_open_diagnostics") {
            MailxSectionRow(
                title = "Inspect Delivery & Bounce Logs",
                description = "View MTA delivery logs, raw bounce files & server health",
                startIcon = Icons.Outlined.ReceiptLong,
                endIcon = MailxIcons.ChevronRight,
                onClick = onOpenDiagnosticsSheet
            )
        }

        item(key = "header_manage_data") {
            MailxSectionHeader(
                title = "Account, Security & Backup",
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        item(key = "section_account_security") {
            MailxSectionRow(
                title = "Account, 2FA, Passkeys & CSV",
                description = "Manage TOTP, passkeys, API access keys, and CSV import/export",
                startIcon = MailxIcons.UserCog,
                endIcon = MailxIcons.ChevronRight,
                onClick = onOpenAccountSheet
            )
        }

        item(key = "section_announcements") {
            MailxSectionRow(
                title = "Service Announcements",
                description = "Instance updates and operational notices",
                startIcon = MailxIcons.Megaphone,
                endIcon = MailxIcons.ChevronRight,
                onClick = onOpenAnnouncementsSheet
            )
        }

        item(key = "section_reset_logout") {
            MailxSectionRow(
                title = "Sign Out & Clear Cache",
                description = "Wipe encrypted credentials and local cache from this device",
                startIcon = MailxIcons.LogOut,
                accentColor = MailxPalette.BlockedRose,
                onClick = onRequestLogout
            )
        }

        item(key = "settings_footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Mailx Privacy • Rust Native Core (16 KB Aligned)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "v0.5.0 • Material 3 • $baseUrl",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
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
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Theme & Motion",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

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
                    Text(
                        text = "Color Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            selected = themeState.darkModeMode == 1,
                            onClick = { onUpdateDarkModeMode(1) },
                            label = { Text("Dark") }
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
                            label = { Text("Light") }
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MailxSectionRow(
                            title = "Material You Dynamic Color",
                            description = "Adapt colors to your device wallpaper",
                            startIcon = MailxIcons.Palette,
                            showSwitch = true,
                            switchChecked = themeState.dynamicColorEnabled,
                            onSwitchChange = onToggleDynamicColor
                        )
                    }

                    MailxSectionRow(
                        title = "Pure Black Surface (AMOLED)",
                        description = "True black background with WCAG AA contrast",
                        startIcon = Icons.Outlined.Contrast,
                        showSwitch = true,
                        switchChecked = themeState.amoledDarkEnabled,
                        onSwitchChange = onToggleAmoledDark
                    )

                    MailxSectionRow(
                        title = "Reduced Motion",
                        description = "Disable non-essential animations",
                        startIcon = Icons.Outlined.MotionPhotosOff,
                        showSwitch = true,
                        switchChecked = themeState.reducedMotionEnabled,
                        onSwitchChange = onToggleReducedMotion
                    )

                    MailxSectionRow(
                        title = "Expressive Motion Physics",
                        description = "Optional spring overshoot (default is MotionScheme.standard())",
                        startIcon = MailxIcons.Sparkles,
                        showSwitch = true,
                        enabled = !themeState.reducedMotionEnabled,
                        switchChecked = themeState.expressiveMotionEnabled && !themeState.reducedMotionEnabled,
                        onSwitchChange = onToggleExpressiveMotion
                    )
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
                                .padding(vertical = 8.dp, horizontal = 6.dp),
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
                                .padding(vertical = 6.dp, horizontal = 6.dp),
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
                                .padding(vertical = 8.dp, horizontal = 6.dp),
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
            title = { Text("Default Sender Name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Display name used when replying or sending via an alias (leave blank to use the alias address):",
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
            title = { Text("List-Unsubscribe Action") },
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
                                .padding(vertical = 6.dp, horizontal = 6.dp),
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
