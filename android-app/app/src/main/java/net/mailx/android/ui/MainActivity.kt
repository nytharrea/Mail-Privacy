@file:Suppress("OPT_IN_IS_NOT_ENABLED", "EXPERIMENTAL_IS_NOT_ENABLED", "UNNECESSARY_OPT_IN")

package net.mailx.android.ui

import android.os.Bundle
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import net.mailx.android.core.SecurePrefsManager
import net.mailx.android.core.uniffi.AliasQuery
import net.mailx.android.core.uniffi.AliasRow
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
import net.mailx.android.service.AliasWatchWorker
import net.mailx.android.service.AliasWatchManager
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : FragmentActivity() {
    private var appLocked by mutableStateOf(false)
    private var biometricEnabled by mutableStateOf(false)
    private var sessionConfigured by mutableStateOf(false)
    private var initialPrefsLoaded by mutableStateOf(false)
    private var promptShowing = false
    private var externalDocumentFlow = false
    private var pendingAliasId by mutableStateOf<String?>(null)
    fun setExternalDocumentFlow(active: Boolean) { externalDocumentFlow = active }
    private lateinit var securePrefs: SecurePrefsManager

    private fun authenticate(afterSuccess: () -> Unit = { appLocked = false }) {
        if (promptShowing) return
        val authenticators = if (android.os.Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            android.widget.Toast.makeText(this, "Set up biometrics or device screen lock first", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        promptShowing = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    afterSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Mailx")
            .setSubtitle("Confirm your identity to access private aliases")
            .setAllowedAuthenticators(authenticators)
        if (android.os.Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("Cancel")
        prompt.authenticate(info.build())
    }

    private fun changeBiometric(enabled: Boolean) {
        authenticate {
            lifecycleScope.launch {
                runCatching {
                    securePrefs.writeValuesAndPersist(mapOf("biometric_enabled" to enabled))
                }.onSuccess {
                    biometricEnabled = enabled
                    appLocked = false
                }.onFailure {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "Could not save app lock preference",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (biometricEnabled && sessionConfigured && !externalDocumentFlow) appLocked = true
    }

    override fun onResume() {
        super.onResume()
        if (appLocked && !promptShowing) window.decorView.post { if (appLocked) authenticate() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAliasId = intent.getStringExtra(AliasWatchWorker.EXTRA_WATCHED_ALIAS_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        pendingAliasId = intent?.getStringExtra(AliasWatchWorker.EXTRA_WATCHED_ALIAS_ID)
        val prefs = SecurePrefsManager(this)
        securePrefs = prefs
        lifecycleScope.launch {
            prefs.dataFlow.collect { stored ->
                val bio = stored.optBoolean("biometric_enabled", false)
                val configured = stored.optString("bearer_token", "").trim().isNotEmpty()
                biometricEnabled = bio
                sessionConfigured = configured
                if (!initialPrefsLoaded) {
                    initialPrefsLoaded = true
                    appLocked = bio && configured
                    if (appLocked && !promptShowing) {
                        window.decorView.post { if (appLocked) authenticate() }
                    }
                } else if (!configured) {
                    appLocked = false
                }
            }
        }
        lifecycleScope.launch {
            val stored = runCatching { prefs.readDurableSnapshot() }.getOrNull()
            val enabled = stored?.optBoolean("alias_watch_enabled", false) == true
            val watched = prefs.getWatchedAliasIdsDurable()
            AliasWatchWorker.schedule(this@MainActivity, enabled && watched.isNotEmpty())
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED &&
                enabled
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 3401)
            }
        }

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
                    if (!initialPrefsLoaded) {
                        Box(Modifier.fillMaxSize())
                    } else if (appLocked && sessionConfigured) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Mailx is locked", style = MaterialTheme.typography.headlineSmall)
                                Button(onClick = { authenticate() }) { Text("Unlock") }
                            }
                        }
                    } else {
                        MailxAppScreen(vm = vm, biometricEnabled = biometricEnabled,
                            onToggleBiometric = ::changeBiometric,
                            requestedAliasId = pendingAliasId,
                            onAliasOpened = { pendingAliasId = null })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MailxAppScreen(
    vm: MailxViewModel,
    biometricEnabled: Boolean = false,
    onToggleBiometric: (Boolean) -> Unit = {},
    requestedAliasId: String? = null,
    onAliasOpened: () -> Unit = {}
) {
    val context = LocalContext.current
    val reducedMotion = LocalReducedMotion.current
    var showBackupDialog by remember { mutableStateOf(false) }
    val durablePrefs by vm.prefs.dataFlow.collectAsStateWithLifecycle(initialValue = null)
    val aliasSearchEnabled = durablePrefs?.optBoolean("alias_search_enabled", false) ?: false
    val localScope = rememberCoroutineScope()

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
    LaunchedEffect(requestedAliasId, authState.isLoggedIn, aliases) {
        if (!requestedAliasId.isNullOrBlank() && authState.isLoggedIn &&
            aliases.any { it.id == requestedAliasId }) {
            vm.selectTab(MainNavTab.ALIASES)
            vm.openSheet(ActiveSheet.ManageAlias(requestedAliasId))
            onAliasOpened()
        }
    }

    val canGoBack by vm.canGoBack.collectAsStateWithLifecycle()
    BackHandler(enabled = canGoBack) { vm.navigateBack() }

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

    val aliasLookup = remember(aliases) {
        runCatching {
            val rawArray = JSONArray().apply { aliases.forEach { put(it.rawJson) } }.toString()
            vm.syncAliasesToRustCore(rawArray)
        }
        aliases.associateBy { it.id }
    }

    val typedAliasPage = remember(aliasLookup, filterState) {
        val nowUnix = System.currentTimeMillis() / 1000L
        runCatching {
            vm.queryTypedAliasRows(
                AliasQuery(
                    chipKey = filterState.selectedChip,
                    searchQuery = filterState.searchQuery,
                    sortField = filterState.sortField.key,
                    sortDescending = filterState.sortDescending,
                    pinnedFirst = filterState.pinnedFirst
                ),
                nowUnix
            )
        }.getOrNull()
    }

    val aliasRowsById: Map<String, AliasRow> = remember(typedAliasPage) {
        typedAliasPage?.rows?.associateBy { it.id } ?: emptyMap()
    }

    val displayedAliases = remember(typedAliasPage, aliasLookup, aliases) {
        val rows = typedAliasPage?.rows
        if (rows != null) {
            rows.mapNotNull { aliasLookup[it.id] }
        } else {
            aliases
        }
    }

    LaunchedEffect(authState.isLoggedIn) {
        if (!authState.isLoggedIn) {
            net.mailx.android.service.AliasSearchIndex.clear(context)
            AliasWatchWorker.schedule(context, false)
        }
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
                        aliasRowsById = aliasRowsById,
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
                        biometricEnabled = biometricEnabled,
                        onToggleBiometric = onToggleBiometric,
                        aliasSearchEnabled = aliasSearchEnabled,
                        onToggleAliasSearch = { enabled ->
                            localScope.launch {
                                runCatching {
                                    vm.prefs.writeValuesAndPersist(mapOf("alias_search_enabled" to enabled))
                                }
                                if (!enabled) net.mailx.android.service.AliasSearchIndex.clear(context)
                            }
                        },
                        onOpenBackupDialog = { showBackupDialog = true },
                        onRequestLogout = { vm.openSheet(ActiveSheet.ConfirmLogout) }
                    )
                }
            }
        }
    }

    if (showBackupDialog) SecurityBackupDialog(onDismiss = { showBackupDialog = false })

    LaunchedEffect(aliases, aliasSearchEnabled, authState.isLoggedIn) {
        if (aliasSearchEnabled && authState.isLoggedIn && aliases.isNotEmpty()) {
            net.mailx.android.service.AliasSearchIndex.replace(context, aliases)
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
                val watchManager = remember(context) { AliasWatchManager(context) }
                var isWatched by remember(target.id) { mutableStateOf(false) }
                LaunchedEffect(target.id) {
                    isWatched = watchManager.isWatched(target.id)
                }
                ManageAliasBottomSheet(
                    alias = target,
                    availableRecipients = defaultsState.availableRecipients,
                    onDismiss = vm::closeSheet,
                    onToggleEnabled = { vm.toggleAliasEnabled(target, it) },
                    onTogglePinned = { vm.toggleAliasPinned(target, it) },
                    watched = isWatched,
                    onToggleWatched = { enabled ->
                        val updated = watchManager.setWatched(target.id, enabled)
                        if (!updated && enabled) {
                            android.widget.Toast.makeText(
                                context,
                                "Maximum 25 watched aliases",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        updated
                    },
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
            shadowElevation = 0.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
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
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        },
                        animationSpec = if (reducedMotion) snap() else tween(180),
                        label = "nav_item_pill_${tab.name}"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
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
,
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
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
                    ),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = MailxIcons.MailxMark,
                            contentDescription = "Mailx",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onProfileClick),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (defaultDomain.isNotBlank()) {
                        Text(
                            text = "@$defaultDomain",
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = onPrivacyLabClick,
                            shape = CircleShape,
                            color = MailxPalette.BrandTertiaryNeutralDark.copy(alpha = 0.14f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = MailxIcons.Sparkles,
                                    contentDescription = "Rust Privacy Lab",
                                    tint = MailxPalette.BrandTertiaryNeutralDark,
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
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .clickable(onClick = onProfileClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userInitials,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
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
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userInitials,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
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
                        iconTint = MailxPalette.BrandTertiaryNeutralDark,
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
                        iconTint = MailxPalette.SentSlate,
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
