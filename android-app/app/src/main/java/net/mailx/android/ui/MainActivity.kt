@file:Suppress("OPT_IN_IS_NOT_ENABLED", "EXPERIMENTAL_IS_NOT_ENABLED", "UNNECESSARY_OPT_IN")

package net.mailx.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.model.*
import net.mailx.android.ui.screens.*
import net.mailx.android.ui.sheets.*
import net.mailx.android.ui.theme.MailxIcons
import net.mailx.android.ui.theme.MailxPalette
import net.mailx.android.ui.theme.MailxTheme
import net.mailx.android.ui.viewmodel.MailxViewModel
import net.mailx.android.ui.viewmodel.MailxViewModelFactory
import net.mailx.android.widget.MailxWidgetUpdater
import org.json.JSONArray
import org.json.JSONObject

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

            MailxTheme(
                darkTheme = useDarkTheme,
                dynamicColor = themeState.dynamicColorEnabled,
                amoledDark = themeState.amoledDarkEnabled,
                expressiveMotion = themeState.expressiveMotionEnabled
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    BackHandler(enabled = activeSheet != ActiveSheet.None || currentTab != MainNavTab.HOME) {
        if (activeSheet != ActiveSheet.None) {
            vm.closeSheet()
        } else if (currentTab != MainNavTab.HOME) {
            vm.selectTab(MainNavTab.HOME)
        }
    }

    LaunchedEffect(aliases, authState.isLoggedIn) {
        if (authState.isLoggedIn) {
            MailxWidgetUpdater.updateAll(context)
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            vm.clearToast()
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Rust Skeleton handles alias filtering, search matching, and multi-column sorting natively via JNI
    val displayedAliases = remember(aliases, filterState) {
        runCatching {
            val rawArray = JSONArray().apply { aliases.forEach { put(it.rawJson) } }.toString()
            val res = JSONObject(
                MailxRustBridge.nativeFilterAndSortAliases(
                    rawArray,
                    filterState.searchQuery,
                    filterState.selectedChip,
                    filterState.sortField.key,
                    filterState.sortDescending,
                    filterState.pinnedFirst
                )
            )
            if (res.optBoolean("ok", false)) {
                UiAlias.listFromJsonArray(res.optJSONArray("data"))
            } else {
                null
            }
        }.getOrNull() ?: aliases
    }

    if (!authState.isLoggedIn) {
        SetupScreen(
            authState = authState,
            onBaseUrlChange = vm::updateBaseUrl,
            onLoginModeChange = vm::updateLoginMode,
            onEmailChange = vm::updateEmailInput,
            onPasswordChange = vm::updatePasswordInput,
            onOtpChange = vm::updateOtpInput,
            onDismissTotp = vm::dismissTotpDialog,
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

    val spatialSpring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsSpring = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MailxExpressiveTopBar(
                title = currentTab.label,
                defaultDomain = defaultsState.defaultDomain,
                userInitials = userInitials,
                onSendMailClick = {
                    val firstActive = aliases.firstOrNull { it.enabled && !it.wildcard }?.name
                        ?: "example.alias@${defaultsState.defaultDomain}"
                    vm.openSheet(ActiveSheet.ReverseSend(firstActive))
                },
                onRefreshClick = { vm.refreshAllData(showIndicator = true) },
                onProfileClick = { vm.openSheet(ActiveSheet.ProfileMenu) }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                MainNavTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { vm.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == MainNavTab.ALIASES) {
                ExtendedFloatingActionButton(
                    onClick = { vm.openSheet(ActiveSheet.CreateAlias) },
                    shape = RoundedCornerShape(22.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = {
                        Icon(
                            imageVector = MailxIcons.Plus,
                            contentDescription = "New Alias",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = {
                        Text(
                            text = "New Alias",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    (fadeIn(animationSpec = effectsSpring) + scaleIn(initialScale = 0.96f, animationSpec = spatialSpring))
                        .togetherWith(fadeOut(animationSpec = effectsSpring))
                },
                label = "expressive_main_tab_transition"
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
                        ruleCount = rules.count { it.active },
                        blocklistCount = blocklist.size,
                        blockTrackers = defaultsState.blockTrackers,
                        removeHeader = defaultsState.removeHeader,
                        pgpActive = recipients.any { it.hasPgpKey && it.pgpEnabled },
                        onRefresh = { vm.refreshAllData(showIndicator = true) },
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
                        onRefresh = { vm.refreshAliases(showIndicator = true) },
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
                        onToggleExpressiveMotion = vm::toggleExpressiveMotion,
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

        ActiveSheet.ProfileMenu -> MailxCommandBottomSheet(
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
                    imageVector = MailxIcons.Trash2,
                    contentDescription = null,
                    tint = MailxPalette.BlockedRose
                )
            },
            title = { Text("Delete Alias") },
            text = {
                Text(
                    text = "Move ${sheet.alias.name} to Trash? Incoming emails will be rejected and the alias can be restored within 90 days."
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MailxPalette.BlockedRose,
                        contentColor = Color.White
                    ),
                    onClick = { vm.deleteAliasConfirmed(sheet.alias) }
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
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
                    imageVector = MailxIcons.LogOut,
                    contentDescription = null,
                    tint = MailxPalette.BlockedRose
                )
            },
            title = { Text("Sign Out & Clear Cache") },
            text = {
                Text("This clears your encrypted local session tokens and offline alias cache on this device.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MailxPalette.BlockedRose,
                        contentColor = Color.White
                    ),
                    onClick = vm::logoutConfirmed
                ) {
                    Text("Sign Out", fontWeight = FontWeight.SemiBold)
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
private fun MailxExpressiveTopBar(
    title: String,
    defaultDomain: String,
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
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = "@$defaultDomain",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onSendMailClick,
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = MailxIcons.Send,
                        contentDescription = "Send from Alias",
                        modifier = Modifier.size(18.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = MailxIcons.RefreshCw,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    onClick = onProfileClick,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = userInitials,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MailxCommandBottomSheet(
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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = userInitials,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userAccount.email.ifBlank {
                                defaultsState.defaultRecipient.ifBlank { "@${defaultsState.defaultDomain}" }
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "@${defaultsState.defaultDomain} • $aliasCount aliases • ${userAccount.subStatus}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CompactMenuItemRow(
                        icon = MailxIcons.Send,
                        title = "Send via Reverse Alias",
                        subtitle = "Compose outgoing email from any alias",
                        onClick = onOpenReverseSend
                    )
                    CompactMenuItemRow(
                        icon = MailxIcons.ShieldCheck,
                        title = "Trackers Report Decoder",
                        subtitle = "Inspect removed email trackers offline in Rust",
                        onClick = onOpenTrackersReport
                    )
                    CompactMenuItemRow(
                        icon = MailxIcons.Bug,
                        title = "Delivery & Bounce Logs",
                        subtitle = "Server health check & raw MTA bounce viewer",
                        onClick = onOpenDiagnostics
                    )
                    CompactMenuItemRow(
                        icon = MailxIcons.UserCog,
                        title = "Account, 2FA, Keys & CSV",
                        subtitle = "TOTP, passkeys, API access keys & CSV backup",
                        onClick = onOpenAccount
                    )
                    CompactMenuItemRow(
                        icon = MailxIcons.Megaphone,
                        title = "Service Announcements",
                        subtitle = "Updates from your Mailx instance",
                        onClick = onOpenAnnouncements
                    )
                    CompactMenuItemRow(
                        icon = MailxIcons.LogOut,
                        title = "Sign Out & Clear Cache",
                        subtitle = "Wipe local credentials and offline cache",
                        iconTint = MailxPalette.BlockedRose,
                        titleColor = MailxPalette.BlockedRose,
                        onClick = onRequestLogout
                    )
                }
            }

            Text(
                text = "$baseUrl • Rust Skeleton v0.5.0 • Material 3 Expressive",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CompactMenuItemRow(
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
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = iconTint.copy(alpha = 0.14f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
