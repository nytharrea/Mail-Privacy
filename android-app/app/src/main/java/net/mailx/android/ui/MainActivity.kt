@file:Suppress("OPT_IN_IS_NOT_ENABLED", "EXPERIMENTAL_IS_NOT_ENABLED", "UNNECESSARY_OPT_IN")

package net.mailx.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import net.mailx.android.ui.components.MailxGroupDivider
import net.mailx.android.ui.components.MailxModernDialog
import net.mailx.android.ui.components.MailxPrimaryButton
import net.mailx.android.ui.model.*
import net.mailx.android.ui.screens.*
import net.mailx.android.ui.sheets.*
import net.mailx.android.ui.theme.LocalReducedMotion
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
                expressiveMotion = themeState.expressiveMotionEnabled,
                reducedMotion = themeState.reducedMotionEnabled
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
    val reducedMotion = LocalReducedMotion.current

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
            onConnectWithPasskey = vm::connectWithPasskey,
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
            MailxCleanTopBar(
                title = currentTab.label,
                defaultDomain = defaultsState.defaultDomain,
                userInitials = userInitials,
                onPrivacyLabClick = { vm.openSheet(ActiveSheet.PrivacyLabSheet) },
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
                    shape = RoundedCornerShape(50),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        bottomBar = {
            MailxModernBottomBar(
                currentTab = currentTab,
                onSelectTab = vm::selectTab
            )
        },
        floatingActionButton = {
            if (currentTab == MainNavTab.ALIASES) {
                MailxPrimaryButton(
                    text = "New Alias",
                    icon = MailxIcons.Plus,
                    onClick = { vm.openSheet(ActiveSheet.CreateAlias) },
                    height = 50.dp
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
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    when {
                        reducedMotion -> EnterTransition.None togetherWith ExitTransition.None
                        else -> {
                            (fadeIn(animationSpec = tween(190)) +
                                slideInHorizontally(animationSpec = tween(220)) { (it / 14) * direction } +
                                scaleIn(initialScale = 0.985f, animationSpec = tween(210)))
                                .togetherWith(
                                    fadeOut(animationSpec = tween(140)) +
                                        slideOutHorizontally(animationSpec = tween(180)) { -(it / 18) * direction }
                                )
                        }
                    }
                },
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
                        onOpenPrivacyLab = { vm.openSheet(ActiveSheet.PrivacyLabSheet) },
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
                        onToggleReducedMotion = vm::toggleReducedMotion,
                        onSyncDefaults = { vm.syncServerDefaults(silent = false) },
                        onOpenReverseAliasTool = {
                            val firstActive = aliases.firstOrNull { it.enabled && !it.wildcard }?.name
                                ?: "example.alias@${defaultsState.defaultDomain}"
                            vm.openSheet(ActiveSheet.ReverseSend(firstActive))
                        },
                        onOpenPrivacyLabSheet = { vm.openSheet(ActiveSheet.PrivacyLabSheet) },
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
            onOpenPrivacyLab = { vm.openSheet(ActiveSheet.PrivacyLabSheet) },
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

        ActiveSheet.PrivacyLabSheet -> PrivacyLabBottomSheet(
            aliases = aliases,
            defaultDomain = defaultsState.defaultDomain,
            onDismiss = vm::closeSheet
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
            onRegisterPasskey = vm::registerPasskeyCredential,
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

        is ActiveSheet.ConfirmDelete -> MailxModernDialog(
            title = "Delete Alias",
            subtitle = "Move ${sheet.alias.name} to Trash? Incoming emails will be rejected and the alias can be restored within 90 days.",
            icon = MailxIcons.Trash2,
            iconTint = MailxPalette.BlockedRose,
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = { vm.deleteAliasConfirmed(sheet.alias) },
            onDismiss = vm::closeSheet
        ) {}

        ActiveSheet.ConfirmLogout -> MailxModernDialog(
            title = "Sign Out & Clear Cache",
            subtitle = "This clears your encrypted local session tokens and offline alias cache on this device.",
            icon = MailxIcons.LogOut,
            iconTint = MailxPalette.BlockedRose,
            confirmText = "Sign Out",
            isDestructive = true,
            onConfirm = vm::logoutConfirmed,
            onDismiss = vm::closeSheet
        ) {}
    }
}

@Composable
private fun MailxModernBottomBar(
    currentTab: MainNavTab,
    onSelectTab: (MainNavTab) -> Unit
) {
    val reducedMotion = LocalReducedMotion.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainNavTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (selected) 56.dp else 36.dp,
                        animationSpec = if (reducedMotion) snap() else spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "nav_pill_w_${tab.name}"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (selected && !reducedMotion) 1.05f else 1f,
                        animationSpec = if (reducedMotion) snap() else spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "nav_item_scale_${tab.name}"
                    )
                    val pillColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        animationSpec = if (reducedMotion) snap() else tween(180),
                        label = "nav_item_pill_${tab.name}"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (selected) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = if (reducedMotion) snap() else tween(180),
                        label = "nav_item_icon_${tab.name}"
                    )
                    val labelColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        },
                        animationSpec = if (reducedMotion) snap() else tween(180),
                        label = "nav_item_lbl_${tab.name}"
                    )
                    val interactionSource = remember { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelectTab(tab) }
                            )
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(indicatorWidth)
                                .height(30.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(RoundedCornerShape(50))
                                .background(pillColor)
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.24f),
                                            shape = RoundedCornerShape(50)
                                        )
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.label,
                                tint = iconColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = tab.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MailxCleanTopBar(
    title: String,
    defaultDomain: String,
    userInitials: String,
    onPrivacyLabClick: () -> Unit,
    onSendMailClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    var refreshTapCount by remember { mutableIntStateOf(0) }
    val refreshRotation by animateFloatAsState(
        targetValue = refreshTapCount * 360f,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "topbar_refresh_spin"
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MailxPalette.BrandPrimaryAccent,
                                    MailxPalette.BrandPrimary
                                )
                            )
                        )
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    onClick = onProfileClick,
                    shape = RoundedCornerShape(50),
                    color = MailxPalette.ReplySky.copy(alpha = 0.14f),
                    border = BorderStroke(
                        width = 0.75.dp,
                        color = MailxPalette.ReplySky.copy(alpha = 0.35f)
                    )
                ) {
                    Text(
                        text = "@$defaultDomain",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MailxPalette.ReplySky,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = onPrivacyLabClick,
                            shape = CircleShape,
                            color = MailxPalette.BrandTertiaryVioletDark.copy(alpha = 0.14f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = MailxIcons.Sparkles,
                                    contentDescription = "Rust Privacy Lab",
                                    tint = MailxPalette.BrandTertiaryVioletDark,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = onSendMailClick,
                            shape = CircleShape,
                            color = MailxPalette.ReplySky.copy(alpha = 0.14f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = MailxIcons.Send,
                                    contentDescription = "Send from Alias",
                                    tint = MailxPalette.ReplySky,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                refreshTapCount++
                                onRefreshClick()
                            },
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = MailxIcons.RefreshCw,
                                    contentDescription = "Refresh",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .graphicsLayer { rotationZ = refreshRotation }
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MailxPalette.BrandPrimaryDark,
                                    MailxPalette.BrandPrimaryDeep
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                        .clickable(onClick = onProfileClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userInitials,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
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
    onOpenPrivacyLab: () -> Unit,
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
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MailxPalette.BrandPrimaryDark,
                                        MailxPalette.BrandPrimaryDeep
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userInitials,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
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
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CompactMenuItemRow(
                        icon = MailxIcons.Sparkles,
                        title = "Rust Privacy & Security Lab",
                        subtitle = "URL sanitizer, phishing guard, header/MIME & hygiene radar",
                        iconTint = MailxPalette.BrandTertiaryVioletDark,
                        onClick = onOpenPrivacyLab
                    )
                    MailxGroupDivider()
                    CompactMenuItemRow(
                        icon = MailxIcons.Send,
                        title = "Send via Reverse Alias",
                        subtitle = "Compose outgoing email from any alias",
                        iconTint = MailxPalette.ReplySky,
                        onClick = onOpenReverseSend
                    )
                    MailxGroupDivider()
                    CompactMenuItemRow(
                        icon = MailxIcons.ShieldCheck,
                        title = "Trackers Report Decoder",
                        subtitle = "Inspect removed email trackers & vendor intelligence in Rust",
                        iconTint = MailxPalette.ForwardedGreen,
                        onClick = onOpenTrackersReport
                    )
                    MailxGroupDivider()
                    CompactMenuItemRow(
                        icon = MailxIcons.Bug,
                        title = "Delivery & Bounce Logs",
                        subtitle = "Server health check & RFC-3463 MTA bounce analyzer",
                        iconTint = MailxPalette.PausedAmber,
                        onClick = onOpenDiagnostics
                    )
                    MailxGroupDivider()
                    CompactMenuItemRow(
                        icon = MailxIcons.UserCog,
                        title = "Account, 2FA, Keys & CSV",
                        subtitle = "TOTP, passkeys, API access keys & CSV backup",
                        iconTint = MailxPalette.SentIndigo,
                        onClick = onOpenAccount
                    )
                    MailxGroupDivider()
                    CompactMenuItemRow(
                        icon = MailxIcons.Megaphone,
                        title = "Service Announcements",
                        subtitle = "Updates from your Mailx instance",
                        iconTint = MailxPalette.ReplySky,
                        onClick = onOpenAnnouncements
                    )
                    MailxGroupDivider()
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
                text = "$baseUrl • Rust Core v0.5.0 • Material 3 Expressive",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
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
            .padding(horizontal = 15.dp, vertical = 12.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = iconTint.copy(alpha = 0.15f),
            border = BorderStroke(0.75.dp, iconTint.copy(alpha = 0.30f)),
            modifier = Modifier.size(38.dp)
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
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = MailxIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(17.dp)
        )
    }
}
