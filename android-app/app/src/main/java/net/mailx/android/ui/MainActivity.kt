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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

            MailxAddyTheme(
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
    val activeSheet by vm.activeSheet.collectAsStateWithLifecycle()
    val toastMessage by vm.toastMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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
                "pinned_only" -> alias.pinned
                "wildcard_only" -> alias.wildcard
                "active" -> alias.enabled
                "inactive" -> !alias.enabled
                else -> true
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
            onAccessKeyChange = vm::updateAccessKeyInput,
            onConnect = vm::connectWithAccessKey
        )
        return
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = currentTab.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "@${defaultsState.defaultDomain}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (currentTab != MainNavTab.SETTINGS) {
                        FilledTonalIconButton(
                            onClick = vm::refreshAliases,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Yenile")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                MainNavTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { vm.selectTab(tab) },
                        icon = {
                            if (tab == MainNavTab.ALIASES && aliases.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Text(aliases.size.toString())
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.label
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label
                                )
                            }
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab != MainNavTab.SETTINGS) {
                ExtendedFloatingActionButton(
                    onClick = { vm.openSheet(ActiveSheet.CreateAlias) },
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Yeni Alias", fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "main_tab_transition"
            ) { targetTab ->
                when (targetTab) {
                    MainNavTab.HOME -> HomeDashboardTab(
                        aliases = aliases,
                        isLoading = isLoading,
                        defaultDomain = defaultsState.defaultDomain,
                        onRefresh = vm::refreshAliases,
                        onNavigateToFilter = vm::navigateToFilteredAliases,
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
                                ?: "ornek.alias@${defaultsState.defaultDomain}"
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
                        onOpenCreateSheet = { vm.openSheet(ActiveSheet.CreateAlias) }
                    )

                    MainNavTab.SETTINGS -> SettingsTab(
                        baseUrl = authState.baseUrl,
                        defaultsState = defaultsState,
                        themeState = themeState,
                        onUpdateDefaultDomain = vm::updateDefaultDomain,
                        onUpdateDefaultFormat = vm::updateDefaultFormat,
                        onToggleDynamicColor = vm::toggleDynamicColor,
                        onToggleAmoledDark = vm::toggleAmoledDark,
                        onSyncDefaults = { vm.syncServerDefaults(silent = false) },
                        onOpenReverseAliasTool = {
                            val firstActive = aliases.firstOrNull { it.enabled && !it.wildcard }?.name
                                ?: "ornek.alias@${defaultsState.defaultDomain}"
                            vm.openSheet(ActiveSheet.ReverseSend(firstActive))
                        },
                        onRequestLogout = { vm.openSheet(ActiveSheet.ConfirmLogout) }
                    )
                }
            }
        }
    }

    when (val sheet = activeSheet) {
        ActiveSheet.None -> Unit

        ActiveSheet.CreateAlias -> AddAliasBottomSheet(
            defaultsState = defaultsState,
            onDismiss = vm::closeSheet,
            onCreateAlias = { reqJson ->
                vm.createAlias(reqJson) { createdName ->
                    copyToClipboard(
                        context,
                        "Mailx Alias",
                        createdName,
                        "Oluşturuldu ve kopyalandı: $createdName"
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
                    tint = AddyPalette.BlockedRed
                )
            },
            title = { Text("Takma Ad Silinsin mi?") },
            text = {
                Text(
                    text = "${sheet.alias.name} silinenler (90g) kutusuna taşınacak ve gelen iletileri reddedecektir.",
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AddyPalette.BlockedRed,
                        contentColor = Color.White
                    ),
                    onClick = { vm.deleteAliasConfirmed(sheet.alias) }
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::closeSheet) {
                    Text("Vazgeç")
                }
            }
        )

        ActiveSheet.ConfirmLogout -> AlertDialog(
            onDismissRequest = vm::closeSheet,
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title = { Text("Oturumu Kapat") },
            text = { Text("Cihazdaki şifreli oturum anahtarı ve önbellek temizlenecek.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AddyPalette.BlockedRed,
                        contentColor = Color.White
                    ),
                    onClick = vm::logoutConfirmed
                ) {
                    Text("Çıkış Yap")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::closeSheet) {
                    Text("İptal")
                }
            }
        )
    }
}
