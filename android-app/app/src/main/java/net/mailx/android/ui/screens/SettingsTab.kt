package net.mailx.android.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import net.mailx.android.ui.components.MailxGroupDivider
import net.mailx.android.ui.components.MailxModernDialog
import net.mailx.android.ui.components.MailxOptionPickerDialog
import net.mailx.android.ui.components.MailxSectionHeader
import net.mailx.android.ui.components.MailxSectionRow
import net.mailx.android.ui.components.MailxSegmentedControl
import net.mailx.android.ui.components.MailxSettingsGroupCard
import net.mailx.android.ui.components.mailxTextFieldColors
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
    onOpenPrivacyLabSheet: () -> Unit = {},
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
        themeState.reducedMotionEnabled -> "Reduced"
        themeState.expressiveMotionEnabled -> "Spring"
        else -> "Standard"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        item(key = "header_appearance") {
            MailxSectionHeader(title = "Appearance & Display")
        }

        item(key = "group_appearance") {
            val colorModes = listOf(1 to "Dark", -1 to "System", 0 to "Light")
            val selectedColorIdx = colorModes.indexOfFirst { it.first == themeState.darkModeMode }.coerceAtLeast(0)

            MailxSettingsGroupCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Color Mode",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$darkModeLabel · $motionModeLabel",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MailxSegmentedControl(
                        options = colorModes.map { it.second },
                        selectedIndex = selectedColorIdx,
                        onSelect = { idx -> onUpdateDarkModeMode(colorModes[idx].first) },
                        compact = true
                    )
                }

                MailxGroupDivider(startIndent = false)
                MailxSectionRow(
                    title = "Pure Black Surface (AMOLED)",
                    description = "True #000000 OLED background with high-contrast carbon cards",
                    startIcon = Icons.Outlined.Contrast,
                    showSwitch = true,
                    switchChecked = themeState.amoledDarkEnabled,
                    onSwitchChange = onToggleAmoledDark,
                    grouped = true
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MailxGroupDivider()
                    MailxSectionRow(
                        title = "Dynamic Wallpaper Accents",
                        description = "Adapt accent colors to wallpaper while keeping carbon surfaces",
                        startIcon = MailxIcons.Palette,
                        showSwitch = true,
                        switchChecked = themeState.dynamicColorEnabled,
                        onSwitchChange = onToggleDynamicColor,
                        grouped = true
                    )
                }

                MailxGroupDivider()
                MailxSectionRow(
                    title = "Expressive Spring Motion",
                    description = "Fluid spring physics across navigation, sheets, and cards",
                    startIcon = MailxIcons.Sparkles,
                    showSwitch = true,
                    enabled = !themeState.reducedMotionEnabled,
                    switchChecked = themeState.expressiveMotionEnabled && !themeState.reducedMotionEnabled,
                    onSwitchChange = onToggleExpressiveMotion,
                    grouped = true
                )

                MailxGroupDivider()
                MailxSectionRow(
                    title = "Reduced Motion",
                    description = "Minimize transitions and disable pulsing animations",
                    startIcon = Icons.Outlined.MotionPhotosOff,
                    showSwitch = true,
                    switchChecked = themeState.reducedMotionEnabled,
                    onSwitchChange = onToggleReducedMotion,
                    grouped = true
                )
            }
        }

        item(key = "header_privacy_tools") {
            MailxSectionHeader(
                title = "Privacy & Relay Tools",
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        item(key = "group_privacy_tools") {
            MailxSettingsGroupCard {
                MailxSectionRow(
                    title = "Rust Privacy & Security Lab",
                    description = "URL sanitizer, phishing guard, header/MIME & hygiene radar",
                    startIcon = MailxIcons.Sparkles,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    trailingBadge = "5 Tools",
                    endIcon = MailxIcons.ChevronRight,
                    grouped = true,
                    onClick = onOpenPrivacyLabSheet
                )
                MailxGroupDivider()
                MailxSectionRow(
                    title = "Send via Reverse Alias",
                    description = "Generate outbound relay addresses for any recipient",
                    startIcon = MailxIcons.Send,
                    iconTint = MailxPalette.BrandAccentTeal,
                    endIcon = MailxIcons.ChevronRight,
                    grouped = true,
                    onClick = onOpenReverseAliasTool
                )
                MailxGroupDivider()
                MailxSectionRow(
                    title = "Trackers Report Decoder",
                    description = "Decode removed email tracker fragments & vendor intelligence in Rust",
                    startIcon = MailxIcons.ShieldCheck,
                    iconTint = MailxPalette.ForwardEmerald,
                    endIcon = MailxIcons.ChevronRight,
                    grouped = true,
                    onClick = onOpenTrackersReportSheet
                )
            }
        }

        item(key = "header_defaults") {
            MailxSectionHeader(
                title = "Server Forwarding Defaults",
                actionText = "Sync",
                onActionClick = onSyncDefaults,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        item(key = "group_forwarding_defaults") {
            MailxSettingsGroupCard {
                MailxSectionRow(
                    title = "Default Domain",
                    description = "Primary domain used when creating new aliases",
                    startIcon = MailxIcons.Globe,
                    iconTint = MailxPalette.ReplySky,
                    trailingBadge = "@${defaultsState.defaultDomain}",
                    endIcon = MailxIcons.Edit3,
                    grouped = true,
                    onClick = { showDomainDialog = true }
                )

                MailxGroupDivider()
                MailxSectionRow(
                    title = "Default Recipient",
                    description = defaultsState.defaultRecipient.ifBlank { "Select default forwarding recipient" },
                    startIcon = MailxIcons.Inbox,
                    iconTint = MailxPalette.BrandAccentTeal,
                    endIcon = MailxIcons.Edit3,
                    grouped = true,
                    onClick = { showRecipientDialog = true }
                )

                MailxGroupDivider()
                MailxSectionRow(
                    title = "Default Alias Format",
                    description = formatAliasTypeLabel(defaultsState.defaultFormat),
                    startIcon = MailxIcons.AtSign,
                    iconTint = MailxPalette.SentIndigo,
                    trailingBadge = defaultsState.defaultFormat.uppercase(),
                    endIcon = MailxIcons.Edit3,
                    grouped = true,
                    onClick = { showFormatDialog = true }
                )

                MailxGroupDivider()
                MailxSectionRow(
                    title = "Default Sender Name",
                    description = defaultsState.defaultFromName.ifBlank {
                        "Uses the alias address when left blank"
                    },
                    startIcon = Icons.Outlined.Badge,
                    iconTint = MailxPalette.ReplySky,
                    endIcon = MailxIcons.Edit3,
                    grouped = true,
                    onClick = { showFromNameDialog = true }
                )

                MailxGroupDivider()
                val includeHeader = !defaultsState.removeHeader
                MailxSectionRow(
                    title = "Include Relay Banner Header",
                    description = "Show 'Sent to <alias> from <sender>' header in forwarded emails",
                    startIcon = Icons.Outlined.MailOutline,
                    iconTint = MailxPalette.BrandAccentTeal,
                    showSwitch = true,
                    switchChecked = includeHeader,
                    onSwitchChange = onUpdateIncludeMailxHeader,
                    grouped = true
                )

                MailxGroupDivider()
                MailxSectionRow(
                    title = "Strip Spy Pixels & Trackers",
                    description = "Automatically remove tracking pixels and spy links before forwarding",
                    startIcon = MailxIcons.ShieldCheck,
                    iconTint = MailxPalette.ForwardEmerald,
                    showSwitch = true,
                    switchChecked = defaultsState.blockTrackers,
                    onSwitchChange = onUpdateBlockTrackers,
                    grouped = true
                )

                MailxGroupDivider()
                MailxSectionRow(
                    title = "List-Unsubscribe Action",
                    description = MailxRuleCatalog.unsubscribeLabel(defaultsState.unsubscribeBehaviour),
                    startIcon = Icons.Outlined.Unsubscribe,
                    iconTint = MailxPalette.PausedAmber,
                    endIcon = MailxIcons.Edit3,
                    grouped = true,
                    onClick = { showUnsubscribeDialog = true }
                )
            }
        }

        item(key = "header_diagnostics") {
            MailxSectionHeader(
                title = "Diagnostics & Delivery Logs",
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        item(key = "group_diagnostics") {
            MailxSettingsGroupCard {
                MailxSectionRow(
                    title = "Diagnostic Issue Logging",
                    description = "Record delivery errors, bounces, and rule blocks",
                    startIcon = MailxIcons.Bug,
                    iconTint = MailxPalette.PausedAmber,
                    showSwitch = true,
                    switchChecked = defaultsState.logIssues,
                    onSwitchChange = onUpdateLogIssues,
                    grouped = true
                )
                MailxGroupDivider()
                MailxSectionRow(
                    title = "Inspect Delivery & Bounce Logs",
                    description = "View MTA delivery logs, raw bounce files & server health",
                    startIcon = Icons.Outlined.ReceiptLong,
                    iconTint = MailxPalette.ReplySky,
                    endIcon = MailxIcons.ChevronRight,
                    grouped = true,
                    onClick = onOpenDiagnosticsSheet
                )
            }
        }

        item(key = "header_manage_data") {
            MailxSectionHeader(
                title = "Account, Security & Backup",
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        item(key = "group_account_security") {
            MailxSettingsGroupCard {
                MailxSectionRow(
                    title = "Account, 2FA, Passkeys & CSV",
                    description = "Manage TOTP, passkeys, API access keys, and CSV import/export",
                    startIcon = MailxIcons.UserCog,
                    iconTint = MailxPalette.SentIndigo,
                    endIcon = MailxIcons.ChevronRight,
                    grouped = true,
                    onClick = onOpenAccountSheet
                )
                MailxGroupDivider()
                MailxSectionRow(
                    title = "Service Announcements",
                    description = "Instance updates and operational notices",
                    startIcon = MailxIcons.Megaphone,
                    iconTint = MailxPalette.BrandAccentTeal,
                    endIcon = MailxIcons.ChevronRight,
                    grouped = true,
                    onClick = onOpenAnnouncementsSheet
                )
            }
        }

        item(key = "section_reset_logout") {
            MailxSectionRow(
                title = "Sign Out & Clear Cache",
                description = "Wipe encrypted credentials and local cache from this device",
                startIcon = MailxIcons.LogOut,
                accentColor = MailxPalette.BlockedRose,
                onClick = onRequestLogout,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        item(key = "settings_footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 6.dp),
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
                    text = "v0.5.0 • Material 3 Expressive • $baseUrl",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showInterfaceSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val colorModes = listOf(1 to "Dark", -1 to "System", 0 to "Light")
        val selectedColorIdx = colorModes.indexOfFirst { it.first == themeState.darkModeMode }.coerceAtLeast(0)

        ModalBottomSheet(
            onDismissRequest = { showInterfaceSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 26.dp)
            ) {
                Text(
                    text = "Theme & Motion",
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                HorizontalDivider(
                    thickness = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "COLOR MODE",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.75.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    MailxSegmentedControl(
                        options = colorModes.map { it.second },
                        selectedIndex = selectedColorIdx,
                        onSelect = { idx -> onUpdateDarkModeMode(colorModes[idx].first) }
                    )

                    MailxSettingsGroupCard {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            MailxSectionRow(
                                title = "Material You Surface Harmonization",
                                description = "Harmonize surfaces with wallpaper while keeping Mailx Teal",
                                startIcon = MailxIcons.Palette,
                                iconTint = MailxPalette.ReplySky,
                                showSwitch = true,
                                switchChecked = themeState.dynamicColorEnabled,
                                onSwitchChange = onToggleDynamicColor,
                                grouped = true
                            )
                            MailxGroupDivider()
                        }

                        MailxSectionRow(
                            title = "Pure Black Surface (AMOLED)",
                            description = "True black background with WCAG AA contrast",
                            startIcon = Icons.Outlined.Contrast,
                            iconTint = MailxPalette.BrandAccentTeal,
                            showSwitch = true,
                            switchChecked = themeState.amoledDarkEnabled,
                            onSwitchChange = onToggleAmoledDark,
                            grouped = true
                        )

                        MailxGroupDivider()
                        MailxSectionRow(
                            title = "Reduced Motion",
                            description = "Disable non-essential animations",
                            startIcon = Icons.Outlined.MotionPhotosOff,
                            iconTint = MailxPalette.SentIndigo,
                            showSwitch = true,
                            switchChecked = themeState.reducedMotionEnabled,
                            onSwitchChange = onToggleReducedMotion,
                            grouped = true
                        )

                        MailxGroupDivider()
                        MailxSectionRow(
                            title = "Expressive Motion Physics",
                            description = "Optional spring overshoot (default is standard motion)",
                            startIcon = MailxIcons.Sparkles,
                            iconTint = MailxPalette.SentIndigo,
                            showSwitch = true,
                            enabled = !themeState.reducedMotionEnabled,
                            switchChecked = themeState.expressiveMotionEnabled && !themeState.reducedMotionEnabled,
                            onSwitchChange = onToggleExpressiveMotion,
                            grouped = true
                        )
                    }
                }
            }
        }
    }

    if (showDomainDialog) {
        MailxOptionPickerDialog(
            title = "Default Domain",
            subtitle = "Choose the default domain for newly created aliases.",
            options = defaultsState.availableDomains.map { it to "@$it" },
            selectedKey = defaultsState.defaultDomain,
            onSelect = { dom ->
                onUpdateDefaultDomain(dom)
                showDomainDialog = false
            },
            onDismiss = { showDomainDialog = false }
        )
    }

    if (showRecipientDialog) {
        if (defaultsState.availableRecipients.isNotEmpty()) {
            MailxOptionPickerDialog(
                title = "Default Recipient",
                subtitle = "Choose the verified destination address for newly created aliases.",
                options = defaultsState.availableRecipients.map { it to it },
                selectedKey = defaultsState.defaultRecipient,
                onSelect = { rec ->
                    onUpdateDefaultRecipient(rec)
                    showRecipientDialog = false
                },
                onDismiss = { showRecipientDialog = false }
            )
        } else {
            var customRec by remember { mutableStateOf(defaultsState.defaultRecipient) }
            MailxModernDialog(
                title = "Default Recipient",
                subtitle = "Enter the default forwarding destination address.",
                icon = MailxIcons.Inbox,
                confirmText = "Save",
                onConfirm = {
                    showRecipientDialog = false
                    if (customRec.isNotBlank()) onUpdateDefaultRecipient(customRec.trim())
                },
                onDismiss = { showRecipientDialog = false }
            ) {
                OutlinedTextField(
                    value = customRec,
                    onValueChange = { customRec = it },
                    label = { Text("Recipient Email Address") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = mailxTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showFormatDialog) {
        val formats = listOf("words", "random", "uuid")
        MailxOptionPickerDialog(
            title = "Default Alias Format",
            subtitle = "Select how random aliases are generated by default.",
            options = formats.map { it to formatAliasTypeLabel(it) },
            selectedKey = defaultsState.defaultFormat,
            onSelect = { fmt ->
                onUpdateDefaultFormat(fmt)
                showFormatDialog = false
            },
            onDismiss = { showFormatDialog = false }
        )
    }

    if (showFromNameDialog) {
        var nameInput by remember { mutableStateOf(defaultsState.defaultFromName) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFromNameDialog = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Default Sender Name",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Display name used when replying or sending via an alias (leave blank to use the alias address).",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Sender Display Name") },
                    placeholder = { Text("e.g. Alex Privacy") },
                    singleLine = true,
                    shape = RoundedCornerShape(13.dp),
                    colors = mailxTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showFromNameDialog = false },
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            showFromNameDialog = false
                            onUpdateDefaultFromName(nameInput.trim())
                        },
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Name", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showUnsubscribeDialog) {
        MailxOptionPickerDialog(
            title = "List-Unsubscribe Action",
            subtitle = "Choose how List-Unsubscribe headers are processed in forwarded emails.",
            options = MailxRuleCatalog.unsubscribeBehaviours,
            selectedKey = defaultsState.unsubscribeBehaviour,
            onSelect = { code ->
                onUpdateUnsubscribeBehaviour(code)
                showUnsubscribeDialog = false
            },
            onDismiss = { showUnsubscribeDialog = false }
        )
    }
}
