package net.mailx.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ForwardToInbox
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.mailx.android.core.EncryptedPrefsManager
import net.mailx.android.ui.model.*
import net.mailx.android.ui.screens.*
import net.mailx.android.ui.sheets.*
import net.mailx.android.ui.theme.AddyPalette
import net.mailx.android.ui.theme.MailxAddyTheme
import net.mailx.android.ui.viewmodel.MailxViewModel
import net.mailx.android.ui.viewmodel.MailxViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = EncryptedPrefsManager(this)

        setContent {
            val vm: MailxViewModel = viewModel(factory = MailxViewModelFactory(prefs))
            val themeState by vm.themeState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeState.darkModeMode) {
                1 -> true
                0 -> false
                else -> systemDark
            }

            MailxAddyTheme(
                darkTheme = useDarkTheme,
                dynamicColor = themeState.dynamicColorEnabled,
                amoledDark = themeState.amoledDarkEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MailxAppScreen(vm = vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailxAppScreen(vm: MailxViewModel) {
    val context = LocalContext.current

    val authState by vm.authState.collectAsStateWithLifecycle()
    val currentTab by vm.currentTab.collectAsStateWithLifecycle()
    val aliases by vm.aliases.collectAsStateWithLifecycle()
    val isLoading by vm.isLoadingAliases.collectAsStateWithLifecycle()
    val filterState by vm.filterState.collectAsStateWithLifecycle()
    val defaultsState by vm.defaultsState.collectAsStateWithLifecycle()
    val themeState by vm.themeState.collectAsStateWithLifecycle()
    val recipients by vm.recipients.collectAsStateWithLifecycle()
    val customDomains by vm.customDomains.collectAsStateWithLifecycle()
    val dnsConfig by vm.dnsConfig.collectAsStateWithLifecycle()
    val dnsCheckResults by vm.dnsCheckResults.collectAsStateWithLifecycle()
    val wildcardDomainInfo by vm.wildcardDomainInfo.collectAsStateWithLifecycle()
    val blocklist by vm.blocklist.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val userStats by vm.userStats.collectAsStateWithLifecycle()
    val diagnosticLogs by vm.diagnosticLogs.collectAsStateWithLifecycle()
    val serverHealthStatus by vm.serverHealthStatus.collectAsStateWithLifecycle()
    val accessKeys by vm.accessKeys.collectAsStateWithLifecycle()
    val passkeys by vm.passkeys.collectAsStateWithLifecycle()
    val userAccount by vm.userAccount.collectAsStateWithLifecycle()
    val announcements by vm.announcements.collectAsStateWithLifecycle()
    val activeSheet by vm.activeSheet.collectAsStateWithLifecycle()
    val toastMessage by vm.toastMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            vm.clearToast()
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    val displayedAliases = remember(aliases, filterState) {
        val filtered = aliases.filter { alias ->
            when (filterState.selectedChip) {
                "pinned_only" -> alias.pinned && alias.deletedAt.isBlank()
                "wildcard_only" -> alias.wildcard && alias.deletedAt.isBlank()
                "active" -> alias.enabled && alias.deletedAt.isBlank()
                "inactive" -> !alias.enabled && alias.deletedAt.isBlank()
                "deleted" -> alias.deletedAt.isNotBlank()
                else -> alias.deletedAt.isBlank()
            }
        }
        val comparator = Comparator<UiAlias> { a, b ->
            if (filterState.pinnedFirst && a.pinned != b.pinned) {
                if (a.pinned) -1 else 1
            } else {
                val rawCmp = when (filterState.sortField) {
                    AliasSortField.CREATED_AT -> a.createdAt.compareTo(b.createdAt)
                    AliasSortField.UPDATED_AT -> a.updatedAt.compareTo(b.updatedAt)
                    AliasSortField.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                    AliasSortField.FORWARDS -> a.forwards.compareTo(b.forwards)
                    AliasSortField.BLOCKS -> a.blocks.compareTo(b.blocks)
                    AliasSortField.REPLIES -> a.replies.compareTo(b.replies)
                    AliasSortField.SENDS -> a.sends.compareTo(b.sends)
                }
                if (filterState.sortDescending) -rawCmp else rawCmp
            }
        }
        filtered.sortedWith(comparator)
    }

    if (!authState.isLoggedIn) {
        SetupScreen(
            authState = authState,
            onBaseUrlChange = vm::updateBaseUrl,
            onLoginModeChange = vm::updateLoginMode,
            onEmailChange = vm::updateEmailInput,
            onPasswordChange = vm::updatePasswordInput,
            onOtpChange = vm::updateOtpInput,
            onAccessKeyChange = vm::updateAccessKeyInput,
            onConnectWithCredentials = vm::connectWithCredentials,
            onConnectWithAccessKey = vm::connectWithAccessKey,
            onInitiatePasswordReset = vm::initiatePasswordReset,
            onConfirmPasswordReset = vm::confirmPasswordReset
        )
        return
    }

    val userInitials = remember(userAccount.email, defaultsState.defaultRecipient, defaultsState.defaultDomain) {
        val src = userAccount.email.ifBlank {
            defaultsState.defaultRecipient.ifBlank { defaultsState.defaultDomain }
        }
        src.take(2).uppercase()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AddyMainTopAppBar(
                title = currentTab.label,
                subtitle = if (isLoading) "Refreshing data..." else "@${defaultsState.defaultDomain}",
                userInitials = userInitials,
                onSendMailClick = {
                    val firstActive = aliases.firstOrNull { it.enabled && !it.wildcard }?.name
                        ?: "example.alias@${defaultsState.defaultDomain}"
                    vm.openSheet(ActiveSheet.ReverseSend(firstActive))
                },
                onRefreshClick = vm::refreshAllData,
                onProfileClick = { vm.openSheet(ActiveSheet.ProfileMenu) }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp
            ) {
                MainNavTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { vm.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == MainNavTab.ALIASES) {
                FloatingActionButton(
                    onClick = { vm.openSheet(ActiveSheet.CreateAlias) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Alias"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "main_tab_transition"
            ) { targetTab ->
                when (targetTab) {
                    MainNavTab.HOME -> HomeDashboardTab(
                        aliases = aliases,
                        userStats = userStats,
                        isLoading = isLoading,
                        defaultDomain = defaultsState.defaultDomain,
                        recipientCount = maxOf(
                            recipients.size,
                            defaultsState.availableRecipients.size.coerceAtLeast(
                                if (defaultsState.defaultRecipient.isNotBlank()) 1 else 0
                            )
                        ),
                        ruleCount = rules.size,
                        blocklistCount = blocklist.size,
                        onRefresh = vm::refreshAllData,
                        onNavigateToFilter = vm::navigateToFilteredAliases,
                        onNavigateToRecipients = { vm.selectTab(MainNavTab.RECIPIENTS) },
                        onNavigateToRules = { vm.selectTab(MainNavTab.RULES) },
                        onOpenDiagnostics = { vm.openSheet(ActiveSheet.DiagnosticsSheet) },
                        onOpenAccountSheet = { vm.openSheet(ActiveSheet.AccountSheet) },
                        onOpenTrackersReport = { vm.openSheet(ActiveSheet.TrackersReportSheet) },
                        onSelectAlias = { vm.openSheet(ActiveSheet.ManageAlias(it.id)) },
                        onCopyOrWildcard = { alias ->
                            if (alias.wildcard) {
                                vm.openSheet(ActiveSheet.WildcardGen(alias))
                            } else {
                                copyToClipboard(context, "Mailx Alias", alias.name)
                            }
                        },
                        onToggleEnabled = vm::toggleAliasEnabled,
                        onOpenCreateAlias = { vm.openSheet(ActiveSheet.CreateAlias) },
                        onOpenReverseAliasTool = {
                            val firstActive = aliases.firstOrNull { it.enabled && !it.wildcard }?.name
                                ?: "example.alias@${defaultsState.defaultDomain}"
                            vm.openSheet(ActiveSheet.ReverseSend(firstActive))
                        }
                    )

                    MainNavTab.ALIASES -> AliasesTab(
                        aliases = displayedAliases,
                        isLoading = isLoading,
                        filterState = filterState,
                        onRefresh = vm::refreshAliases,
                        onSearchQueryChange = vm::updateSearchQuery,
                        onSelectFilterChip = vm::selectFilterChip,
                        onOpenFilterSheet = { vm.openSheet(ActiveSheet.FilterOptions) },
                        onSelectAlias = { vm.openSheet(ActiveSheet.ManageAlias(it.id)) },
                        onCopyOrWildcard = { alias ->
                            if (alias.wildcard) {
                                vm.openSheet(ActiveSheet.WildcardGen(alias))
                            } else {
                                copyToClipboard(context, "Mailx Alias", alias.name)
                            }
                        },
                        onToggleEnabled = vm::toggleAliasEnabled,
                        onBulkSetEnabled = vm::bulkSetAliasesEnabled,
                        onBulkSetPinned = vm::bulkSetAliasesPinned,
                        onBulkDelete = vm::bulkDeleteAliases,
                        onBulkRestore = vm::bulkRestoreAliases,
                        onBulkForget = vm::bulkForgetAliases,
                        onOpenCreateSheet = { vm.openSheet(ActiveSheet.CreateAlias) }
                    )

                    MainNavTab.RECIPIENTS -> RecipientsTab(
                        aliases = aliases,
                        recipients = recipients,
                        customDomains = customDomains,
                        dnsConfig = dnsConfig,
                        dnsCheckResults = dnsCheckResults,
                        defaultsState = defaultsState,
                        onAddRecipient = vm::addRecipient,
                        onSendRecipientOtp = vm::sendRecipientOtp,
                        onVerifyRecipientOtp = vm::verifyRecipientOtp,
                        onUpdateRecipientPgp = vm::updateRecipientPgp,
                        onDeleteRecipient = vm::deleteRecipient,
                        onAddCustomDomain = vm::addCustomDomain,
                        onVerifyDomainDns = vm::verifyDomainDns,
                        onUpdateCustomDomain = vm::updateCustomDomain,
                        onDeleteCustomDomain = vm::deleteCustomDomain,
                        onUpdateDefaultRecipient = vm::updateDefaultRecipient,
                        onUpdateDefaultDomain = vm::updateDefaultDomain,
                        onUpdateDefaultFormat = vm::updateDefaultFormat,
                        onSyncDefaults = { vm.syncServerDefaults(silent = false) }
                    )

                    MainNavTab.RULES -> RulesAndBlocklistTab(
                        rules = rules,
                        blocklist = blocklist,
                        recipients = recipients,
                        availableRecipients = defaultsState.availableRecipients,
                        onSaveRule = vm::saveRule,
                        onToggleRuleActive = vm::toggleRuleActive,
                        onMoveRuleOrder = vm::moveRuleOrder,
                        onDeleteRule = vm::deleteRule,
                        onAddBlocklistItem = vm::addBlocklistItem,
                        onDeleteBlocklistItem = vm::deleteBlocklistItem,
                        onRefresh = {
                            vm.fetchRules()
                            vm.fetchBlocklist()
                        }
                    )

                    MainNavTab.SETTINGS -> SettingsTab(
                        baseUrl = authState.baseUrl,
                        defaultsState = defaultsState,
                        themeState = themeState,
                        onUpdateDefaultDomain = vm::updateDefaultDomain,
                        onUpdateDefaultRecipient = vm::updateDefaultRecipient,
                        onUpdateDefaultFormat = vm::updateDefaultFormat,
                        onUpdateDefaultFromName = vm::updateDefaultFromName,
                        onUpdateIncludeMailxHeader = vm::updateIncludeMailxHeader,
                        onUpdateBlockTrackers = vm::updateBlockTrackers,
                        onUpdateUnsubscribeBehaviour = vm::updateUnsubscribeBehaviour,
                        onUpdateLogIssues = vm::updateLogIssues,
                        onUpdateDarkModeMode = vm::updateDarkModeMode,
                        onToggleDynamicColor = vm::toggleDynamicColor,
                        onToggleAmoledDark = vm::toggleAmoledDark,
                        onSyncDefaults = { vm.syncServerDefaults(silent = false) },
                        onOpenReverseAliasTool = {
                            val firstActive = aliases.firstOrNull { it.enabled && !it.wildcard }?.name
                                ?: "example.alias@${defaultsState.defaultDomain}"
                            vm.openSheet(ActiveSheet.ReverseSend(firstActive))
                        },
                        onOpenTrackersReportSheet = { vm.openSheet(ActiveSheet.TrackersReportSheet) },
                        onOpenDiagnosticsSheet = { vm.openSheet(ActiveSheet.DiagnosticsSheet) },
                        onOpenAccountSheet = { vm.openSheet(ActiveSheet.AccountSheet) },
                        onOpenAnnouncementsSheet = { vm.openSheet(ActiveSheet.AnnouncementsSheet) },
                        onRequestLogout = { vm.openSheet(ActiveSheet.ConfirmLogout) }
                    )
                }
            }
        }
    }

    when (val sheet = activeSheet) {
        ActiveSheet.None -> Unit
        ActiveSheet.AppearanceSheet -> Unit

        ActiveSheet.ProfileMenu -> AddyProfileBottomSheet(
            baseUrl = authState.baseUrl,
            defaultsState = defaultsState,
            userAccount = userAccount,
            aliasCount = aliases.size,
            userInitials = userInitials,
            onDismiss = vm::closeSheet,
            onNavigateToAliases = { chip ->
                vm.closeSheet()
                vm.navigateToFilteredAliases(chip)
            },
            onNavigateToRecipients = {
                vm.closeSheet()
                vm.selectTab(MainNavTab.RECIPIENTS)
            },
            onNavigateToRules = {
                vm.closeSheet()
                vm.selectTab(MainNavTab.RULES)
            },
            onNavigateToSettings = {
                vm.closeSheet()
                vm.selectTab(MainNavTab.SETTINGS)
            },
            onOpenDiagnostics = { vm.openSheet(ActiveSheet.DiagnosticsSheet) },
            onOpenAccount = { vm.openSheet(ActiveSheet.AccountSheet) },
            onOpenAnnouncements = { vm.openSheet(ActiveSheet.AnnouncementsSheet) },
            onOpenTrackersReport = { vm.openSheet(ActiveSheet.TrackersReportSheet) },
            onOpenReverseSend = {
                val firstActive = aliases.firstOrNull { it.enabled && !it.wildcard }?.name
                    ?: "example.alias@${defaultsState.defaultDomain}"
                vm.openSheet(ActiveSheet.ReverseSend(firstActive))
            },
            onRequestLogout = { vm.openSheet(ActiveSheet.ConfirmLogout) }
        )

        ActiveSheet.AccountSheet -> AccountBottomSheet(
            userAccount = userAccount,
            accessKeys = accessKeys,
            passkeys = passkeys,
            aliasCount = aliases.size,
            onDismiss = vm::closeSheet,
            onSendAccountOtp = vm::sendAccountVerificationOtp,
            onVerifyAccountOtp = vm::verifyAccountEmailOtp,
            onChangePassword = vm::changeAccountPassword,
            onChangeEmail = vm::changeAccountEmail,
            onInitiateTotp = vm::initiateTotpSetup,
            onConfirmTotp = vm::confirmTotpSetup,
            onDisableTotp = vm::disableTotp,
            onDeletePasskey = vm::deletePasskeyCredential,
            onCreateAccessKey = vm::createAccessKey,
            onClearNewAccessKeyToken = vm::clearNewlyCreatedAccessKeyToken,
            onDeleteAccessKey = vm::deleteAccessKey,
            onExportAliasesCsv = vm::exportAliasesCsv,
            onImportAliasesCsv = vm::importAliasesFromCsvText,
            onRequestDeleteAccount = vm::requestAccountDeletion,
            onConfirmDeleteAccount = vm::confirmAccountDeletion
        )

        ActiveSheet.DiagnosticsSheet -> DiagnosticsBottomSheet(
            logs = diagnosticLogs,
            logIssuesEnabled = defaultsState.logIssues,
            serverHealthStatus = serverHealthStatus,
            onCheckServerHealth = vm::checkServerHealth,
            onToggleLogIssues = vm::updateLogIssues,
            onFetchBounceFile = vm::fetchBounceLogFile,
            onDeleteAllLogs = vm::deleteAllDiagnosticLogs,
            onRefreshLogs = vm::fetchDiagnosticLogs,
            onDismiss = vm::closeSheet
        )

        ActiveSheet.AnnouncementsSheet -> AnnouncementsBottomSheet(
            announcements = announcements,
            onDismiss = vm::closeSheet
        )

        ActiveSheet.TrackersReportSheet -> TrackersReportBottomSheet(
            onDismiss = vm::closeSheet
        )

        ActiveSheet.CreateAlias -> AddAliasBottomSheet(
            defaultsState = defaultsState,
            customDomains = customDomains,
            wildcardDomainInfo = wildcardDomainInfo,
            onFetchWildcardDomainInfo = vm::fetchWildcardDomainInfo,
            onDismiss = vm::closeSheet,
            onCreateAlias = { reqJson ->
                vm.createAlias(reqJson) { createdName ->
                    copyToClipboard(
                        context,
                        "Mailx Alias",
                        createdName,
                        "Alias created and copied: $createdName"
                    )
                }
            }
        )

        ActiveSheet.FilterOptions -> FilterOptionsBottomSheet(
            filterState = filterState,
            onApply = vm::applyFilterOptions,
            onReset = vm::resetFilterOptions,
            onDismiss = vm::closeSheet
        )

        is ActiveSheet.ManageAlias -> {
            val target = aliases.find { it.id == sheet.aliasId }
            if (target != null) {
                ManageAliasBottomSheet(
                    alias = target,
                    availableRecipients = defaultsState.availableRecipients,
                    onDismiss = vm::closeSheet,
                    onToggleEnabled = { vm.toggleAliasEnabled(target, it) },
                    onTogglePinned = { vm.toggleAliasPinned(target, it) },
                    onUpdateMetadata = { desc, from, rec ->
                        vm.updateAliasMetadata(target, desc, from, rec)
                    },
                    onPauseAlias = { v, u -> vm.pauseAlias(target.id, v, u) },
                    onUnpauseAlias = { vm.unpauseAlias(target.id) },
                    onRestoreAlias = { vm.restoreAlias(target) },
                    onForgetAlias = { vm.forgetAlias(target) },
                    onOpenSendFromAlias = { vm.openSheet(ActiveSheet.ReverseSend(target.name)) },
                    onOpenWildcardGenerator = { vm.openSheet(ActiveSheet.WildcardGen(target)) },
                    onRequestDelete = { vm.openSheet(ActiveSheet.ConfirmDelete(target)) }
                )
            }
        }

        is ActiveSheet.WildcardGen -> WildcardGeneratorBottomSheet(
            wildcardAlias = sheet.alias,
            onDismiss = vm::closeSheet
        )

        is ActiveSheet.ReverseSend -> ReverseAliasSendBottomSheet(
            initialAliasName = sheet.initialAlias,
            availableAliases = aliases.filter { it.enabled && !it.wildcard }.map { it.name },
            onDismiss = vm::closeSheet
        )

        is ActiveSheet.ConfirmDelete -> AlertDialog(
            onDismissRequest = vm::closeSheet,
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = AddyPalette.SoftRed
                )
            },
            title = { Text("Delete Alias") },
            text = {
                Text(
                    text = "Are you sure you want to delete ${sheet.alias.name}? Deleted aliases reject incoming emails and can be restored within 90 days."
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AddyPalette.SoftRed,
                        contentColor = Color.White
                    ),
                    onClick = { vm.deleteAliasConfirmed(sheet.alias) }
                ) {
                    Text("Delete", fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::closeSheet) {
                    Text("Cancel")
                }
            }
        )

        ActiveSheet.ConfirmLogout -> AlertDialog(
            onDismissRequest = vm::closeSheet,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = AddyPalette.SoftRed
                )
            },
            title = { Text("Reset App") },
            text = {
                Text("This will clear your local session credentials and offline cache on this device. Do you want to log out?")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AddyPalette.SoftRed,
                        contentColor = Color.White
                    ),
                    onClick = vm::logoutConfirmed
                ) {
                    Text("Log Out", fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::closeSheet) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AddyMainTopAppBar(
    title: String,
    subtitle: String,
    userInitials: String,
    onSendMailClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onSendMailClick,
                    modifier = Modifier.size(46.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ForwardToInbox,
                        contentDescription = "Send from Alias",
                        modifier = Modifier.size(20.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier.size(46.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Surface(
                    onClick = onProfileClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = userInitials,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddyProfileBottomSheet(
    baseUrl: String,
    defaultsState: DefaultsUiState,
    userAccount: UiUserAccount,
    aliasCount: Int,
    userInitials: String,
    onDismiss: () -> Unit,
    onNavigateToAliases: (String) -> Unit,
    onNavigateToRecipients: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenTrackersReport: () -> Unit,
    onOpenReverseSend: () -> Unit,
    onRequestLogout: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = userInitials,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = userAccount.email.ifBlank {
                    defaultsState.defaultRecipient.ifBlank { "@${defaultsState.defaultDomain}" }
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "@${defaultsState.defaultDomain} • $aliasCount aliases • Subscription: ${userAccount.subStatus}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ProfileMenuItemRow(
                        icon = Icons.Default.AlternateEmail,
                        title = "Aliases",
                        subtitle = "$aliasCount total aliases",
                        onClick = { onNavigateToAliases("active_inactive") }
                    )
                    ProfileMenuItemRow(
                        icon = Icons.Default.AutoAwesome,
                        title = "Wildcard Aliases (*)",
                        subtitle = "Generate on-the-fly custom prefix addresses",
                        onClick = { onNavigateToAliases("wildcard_only") }
                    )
                    ProfileMenuItemRow(
                        icon = Icons.Default.Inbox,
                        title = "Recipients & PGP Encryption",
                        subtitle = defaultsState.defaultRecipient.ifBlank { "${defaultsState.availableRecipients.size} verified recipients" },
                        onClick = onNavigateToRecipients
                    )
                    ProfileMenuItemRow(
                        icon = Icons.Default.Language,
                        title = "Custom Domains & DNS",
                        subtitle = "Default: @${defaultsState.defaultDomain}",
                        onClick = onNavigateToRecipients
                    )
                    ProfileMenuItemRow(
                        icon = Icons.Default.FilterAlt,
                        title = "Rules & Blocklist",
                        subtitle = "Automated email filtering rules and sender blocklist",
                        onClick = onNavigateToRules
                    )
                    ProfileMenuItemRow(
                        icon = Icons.AutoMirrored.Filled.ForwardToInbox,
                        title = "Send from Alias",
                        subtitle = "Generate reverse-alias forwarding addresses",
                        onClick = onOpenReverseSend
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ProfileMenuItemRow(
                        icon = Icons.Default.ManageAccounts,
                        title = "Account, Subscription & Keys",
                        subtitle = "2FA TOTP, passkeys, access keys, password, and CSV import/export",
                        onClick = onOpenAccount
                    )
                    ProfileMenuItemRow(
                        icon = Icons.Default.Policy,
                        title = "Trackers Report Inspector",
                        subtitle = "Decode removed email tracker links locally",
                        onClick = onOpenTrackersReport
                    )
                    ProfileMenuItemRow(
                        icon = Icons.Default.BugReport,
                        title = "Diagnostics Logs",
                        subtitle = "Delivery, bounce, and blocked email logs",
                        onClick = onOpenDiagnostics
                    )
                    ProfileMenuItemRow(
                        icon = Icons.Default.Campaign,
                        title = "Announcements",
                        subtitle = "Mailx service announcements",
                        onClick = onOpenAnnouncements
                    )
                    ProfileMenuItemRow(
                        icon = Icons.Default.Settings,
                        title = "Server & App Settings",
                        subtitle = "Defaults, tracker blocking, header & theme • v0.3.0",
                        onClick = onNavigateToSettings
                    )
                    ProfileMenuItemRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "Reset App (Log Out)",
                        subtitle = "Clear local session data on this device",
                        iconTint = AddyPalette.SoftRed,
                        titleColor = AddyPalette.SoftRed,
                        onClick = onRequestLogout
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "Designed with privacy and care",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ProfileMenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
