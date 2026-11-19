package net.mailx.android.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.mailx.android.core.EncryptedPrefsManager
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.core.PasskeyManager
import net.mailx.android.ui.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class MailxViewModel(val prefs: EncryptedPrefsManager) : ViewModel() {

    private val _authState = MutableStateFlow(
        AuthUiState(
            isLoggedIn = prefs.isConfigured(),
            isWebSession = prefs.isWebSession(),
            baseUrl = prefs.baseUrl,
            emailInput = prefs.accountEmail
        )
    )
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _currentTab = MutableStateFlow(MainNavTab.HOME)
    val currentTab: StateFlow<MainNavTab> = _currentTab.asStateFlow()

    private val _aliases = MutableStateFlow<List<UiAlias>>(emptyList())
    val aliases: StateFlow<List<UiAlias>> = _aliases.asStateFlow()

    private val _isLoadingAliases = MutableStateFlow(false)
    val isLoadingAliases: StateFlow<Boolean> = _isLoadingAliases.asStateFlow()

    private val _filterState = MutableStateFlow(FilterUiState())
    val filterState: StateFlow<FilterUiState> = _filterState.asStateFlow()

    private val _defaultsState = MutableStateFlow(
        DefaultsUiState(
            defaultDomain = prefs.defaultDomain.ifEmpty { "mailx.net" },
            defaultRecipient = prefs.defaultRecipient,
            defaultFormat = prefs.defaultFormat.ifEmpty { "words" },
            defaultFromName = prefs.defaultFromName,
            availableDomains = prefs.getAvailableDomains(),
            availableRecipients = prefs.getAvailableRecipients(),
            blockTrackers = prefs.blockTrackers,
            removeHeader = prefs.removeHeader,
            logIssues = prefs.logIssues,
            unsubscribeBehaviour = prefs.unsubscribeBehaviour
        )
    )
    val defaultsState: StateFlow<DefaultsUiState> = _defaultsState.asStateFlow()

    private val _themeState = MutableStateFlow(
        ThemeUiState(
            darkModeMode = prefs.darkModeMode,
            dynamicColorEnabled = prefs.dynamicColorEnabled,
            amoledDarkEnabled = prefs.amoledDarkEnabled,
            expressiveMotionEnabled = prefs.expressiveMotionEnabled,
            reducedMotionEnabled = prefs.reducedMotionEnabled
        )
    )
    val themeState: StateFlow<ThemeUiState> = _themeState.asStateFlow()

    private val _recipients = MutableStateFlow<List<UiRecipient>>(emptyList())
    val recipients: StateFlow<List<UiRecipient>> = _recipients.asStateFlow()

    private val _customDomains = MutableStateFlow<List<UiCustomDomain>>(emptyList())
    val customDomains: StateFlow<List<UiCustomDomain>> = _customDomains.asStateFlow()

    private val _dnsConfig = MutableStateFlow(UiDnsConfig())
    val dnsConfig: StateFlow<UiDnsConfig> = _dnsConfig.asStateFlow()

    private val _dnsCheckResults = MutableStateFlow<Map<String, List<UiDnsCheckResult>>>(emptyMap())
    val dnsCheckResults: StateFlow<Map<String, List<UiDnsCheckResult>>> = _dnsCheckResults.asStateFlow()

    private val _wildcardDomainInfo = MutableStateFlow(UiWildcardDomainInfo())
    val wildcardDomainInfo: StateFlow<UiWildcardDomainInfo> = _wildcardDomainInfo.asStateFlow()

    private val _blocklist = MutableStateFlow<List<UiBlocklistItem>>(emptyList())
    val blocklist: StateFlow<List<UiBlocklistItem>> = _blocklist.asStateFlow()

    private val _rules = MutableStateFlow<List<UiRule>>(emptyList())
    val rules: StateFlow<List<UiRule>> = _rules.asStateFlow()

    private val _userStats = MutableStateFlow(UiUserStats())
    val userStats: StateFlow<UiUserStats> = _userStats.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<UiDiagnosticLog>>(emptyList())
    val diagnosticLogs: StateFlow<List<UiDiagnosticLog>> = _diagnosticLogs.asStateFlow()

    private val _serverHealthStatus = MutableStateFlow("")
    val serverHealthStatus: StateFlow<String> = _serverHealthStatus.asStateFlow()

    private val _accessKeys = MutableStateFlow<List<UiAccessKey>>(emptyList())
    val accessKeys: StateFlow<List<UiAccessKey>> = _accessKeys.asStateFlow()

    private val _passkeys = MutableStateFlow<List<UiPasskeyCredential>>(emptyList())
    val passkeys: StateFlow<List<UiPasskeyCredential>> = _passkeys.asStateFlow()

    private val _userAccount = MutableStateFlow(
        UiUserAccount(email = prefs.accountEmail.ifBlank { prefs.defaultRecipient })
    )
    val userAccount: StateFlow<UiUserAccount> = _userAccount.asStateFlow()

    private val _announcements = MutableStateFlow<List<UiAnnouncement>>(emptyList())
    val announcements: StateFlow<List<UiAnnouncement>> = _announcements.asStateFlow()

    private val _activeSheet = MutableStateFlow<ActiveSheet>(ActiveSheet.None)
    val activeSheet: StateFlow<ActiveSheet> = _activeSheet.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Persistent Rust app-shell snapshot. Revision is preserved across events so Rust,
    // not a Kotlin fallback, remains authoritative for route and sheet transitions.
    private var rustShellState = JSONObject()
        .put("route", "home")
        .put("authenticated", prefs.isConfigured())
        .put("active_sheet", JSONObject.NULL)
        .put("revision", 0L)

    private var searchJob: Job? = null
    private var hasLoadedInitialData = false
    private var hasLoadedDeletedAliases = false

    init {
        if (prefs.cachedAliasesJson.isNotEmpty()) {
            runCatching {
                _aliases.value = UiAlias.listFromJsonArray(JSONArray(prefs.cachedAliasesJson))
            }
        }
        if (prefs.isConfigured()) {
            hasLoadedInitialData = true
            refreshAllData(showIndicator = _aliases.value.isEmpty())
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun emitToast(msg: String) {
        _toastMessage.value = msg
    }

    fun selectTab(tab: MainNavTab) {
        if (_currentTab.value == tab) return

        // Rust validates the event and returns the next persistent shell snapshot.
        // On an error, retain the existing route instead of silently falling back.
        val accepted = dispatchShellEvent(
            JSONObject().put("type", "navigate").put("route", tab.name.lowercase())
        )
        if (!accepted) return
        _currentTab.value = MainNavTab.entries.firstOrNull {
            it.name.equals(rustShellState.optString("route"), ignoreCase = true)
        } ?: return
        if (!prefs.isConfigured()) return
        when (tab) {
            MainNavTab.HOME -> {
                if (!hasLoadedInitialData) {
                    hasLoadedInitialData = true
                    refreshAllData(showIndicator = _aliases.value.isEmpty())
                }
            }
            MainNavTab.ALIASES -> {
                if (!hasLoadedInitialData) {
                    hasLoadedInitialData = true
                    refreshAliases(showIndicator = _aliases.value.isEmpty())
                }
            }
            MainNavTab.RECIPIENTS -> {
                if (_recipients.value.isEmpty()) fetchRecipients()
                if (_customDomains.value.isEmpty()) fetchDomains()
            }
            MainNavTab.RULES -> {
                if (_recipients.value.isEmpty()) fetchRecipients()
                if (_rules.value.isEmpty()) fetchRules()
                if (_blocklist.value.isEmpty()) fetchBlocklist()
            }
            MainNavTab.SETTINGS -> {
                if (_userAccount.value.id.isBlank()) fetchAccountAndSubscription()
            }
        }
    }

    private fun dispatchShellEvent(event: JSONObject): Boolean {
        return runCatching {
            val response = JSONObject(
                MailxRustBridge.nativeReduceAppShellState(rustShellState.toString(), event.toString())
            )
            if (!response.optBoolean("ok", false)) {
                emitToast(response.optString("error", "Navigation transition rejected"))
                return false
            }
            val next = response.optJSONObject("data") ?: return false
            rustShellState = next
            true
        }.getOrElse {
            emitToast("Application navigation failed")
            false
        }
    }

    private fun syncRustAuthentication(authenticated: Boolean) {
        dispatchShellEvent(JSONObject().put("type", if (authenticated) "authenticate" else "sign_out"))
    }

    fun openSheet(sheet: ActiveSheet) {
        if (sheet != ActiveSheet.None) {
            if (!dispatchShellEvent(JSONObject().put("type", "open_sheet").put("sheet", sheet.name.lowercase()))) return
        }
        _activeSheet.value = sheet
        when (sheet) {
            ActiveSheet.DiagnosticsSheet -> fetchDiagnosticLogs()
            ActiveSheet.AccountSheet -> fetchAccountAndSubscription()
            ActiveSheet.AnnouncementsSheet -> fetchAnnouncements()
            else -> Unit
        }
    }

    fun closeSheet() {
        dispatchShellEvent(JSONObject().put("type", "close_sheet"))
        _activeSheet.value = ActiveSheet.None
    }

    fun updateBaseUrl(url: String) {
        _authState.update { it.copy(baseUrl = url, errorMessage = null) }
    }

    fun updateLoginMode(mode: String) {
        _authState.update { it.copy(loginMode = mode, errorMessage = null, totpRequired = false) }
    }

    fun updateEmailInput(email: String) {
        _authState.update { it.copy(emailInput = email, totpRequired = false, otpInput = "", errorMessage = null) }
    }

    fun updatePasswordInput(password: String) {
        _authState.update { it.copy(passwordInput = password, totpRequired = false, otpInput = "", errorMessage = null) }
    }

    fun updateOtpInput(otp: String) {
        _authState.update { it.copy(otpInput = otp, errorMessage = null) }
    }

    fun dismissTotpDialog() {
        _authState.update {
            it.copy(
                totpRequired = false,
                otpInput = "",
                errorMessage = null
            )
        }
    }

    fun updateAccessKeyInput(key: String) {
        _authState.update { it.copy(accessKeyInput = key, errorMessage = null) }
    }

    fun connectWithCredentials() {
        val currentAuth = _authState.value
        if (currentAuth.emailInput.isBlank() || currentAuth.passwordInput.isEmpty()) {
            _authState.update { it.copy(errorMessage = "Please enter your email address and password.") }
            return
        }
        if (currentAuth.totpRequired && currentAuth.otpInput.trim().isEmpty()) {
            _authState.update { it.copy(errorMessage = "Please enter your 6-digit 2FA verification code.") }
            return
        }

        viewModelScope.launch {
            val hadOtp = currentAuth.otpInput.trim().isNotEmpty()
            _authState.update { it.copy(isLoading = true, errorMessage = null) }
            val loginRes = MailxRustBridge.login(
                baseUrl = currentAuth.baseUrl,
                email = currentAuth.emailInput.trim(),
                password = currentAuth.passwordInput,
                otp = currentAuth.otpInput.trim()
            )
            if (loginRes.optBoolean("ok", false)) {
                val data = loginRes.optJSONObject("data") ?: JSONObject()
                prefs.baseUrl = currentAuth.baseUrl
                prefs.accountEmail = currentAuth.emailInput.trim()
                prefs.accessKey = ""
                prefs.bearerToken = data.optString("token")
                prefs.applyServerDefaults(data)
                applyDefaultsJsonToState(data)
                syncRustAuthentication(true)

                _authState.update {
                    it.copy(
                        isLoggedIn = true,
                        isWebSession = true,
                        baseUrl = prefs.baseUrl,
                        passwordInput = "",
                        otpInput = "",
                        totpRequired = false,
                        isLoading = false,
                        errorMessage = null
                    )
                }
                hasLoadedInitialData = true
                hasLoadedDeletedAliases = false
                refreshAllData(showIndicator = true)
            } else {
                val errCode = loginRes.optInt("error_code", 0)
                val errMsg = loginRes.optString("error", "Sign in failed")
                val isInitialTotpPrompt = errCode == 70001 && !hadOtp
                _authState.update {
                    it.copy(
                        isLoading = false,
                        totpRequired = it.totpRequired || errCode == 70001,
                        errorMessage = when {
                            isInitialTotpPrompt -> null
                            errCode == 70001 -> "Invalid 2FA verification code. Please try again."
                            else -> errMsg
                        }
                    )
                }
            }
        }
    }

    fun connectWithPasskey(activityContext: Context) {
        val currentAuth = _authState.value
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, errorMessage = null) }
            val beginRes = MailxRustBridge.passkeyLoginBegin(
                baseUrl = currentAuth.baseUrl,
                email = currentAuth.emailInput.trim()
            )
            if (!beginRes.optBoolean("ok", false)) {
                _authState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = beginRes.optString("error", "Could not start passkey sign-in")
                    )
                }
                return@launch
            }

            val beginData = beginRes.optJSONObject("data") ?: JSONObject()
            val optionsObj = beginData.optJSONObject("options") ?: JSONObject()
            val ceremonyCookie = beginData.optString("ceremony_cookie", "")
            val isDiscoverable = beginData.optBoolean("discoverable", true)

            val assertionResult = PasskeyManager.requestPasskeyAssertion(activityContext, optionsObj)
            val credentialJson = assertionResult.getOrElse { err ->
                _authState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Passkey verification cancelled"
                    )
                }
                return@launch
            }

            val finishRes = MailxRustBridge.passkeyLoginFinish(
                baseUrl = currentAuth.baseUrl,
                ceremonyCookie = ceremonyCookie,
                credentialJson = credentialJson,
                discoverable = isDiscoverable,
                fallbackEmail = currentAuth.emailInput.trim()
            )

            if (finishRes.optBoolean("ok", false)) {
                val data = finishRes.optJSONObject("data") ?: JSONObject()
                prefs.baseUrl = currentAuth.baseUrl
                val resolvedEmail = data.optString("recipient", "").ifBlank { currentAuth.emailInput.trim() }
                if (resolvedEmail.isNotBlank()) {
                    prefs.accountEmail = resolvedEmail
                }
                prefs.accessKey = ""
                prefs.bearerToken = data.optString("token")
                prefs.applyServerDefaults(data)
                applyDefaultsJsonToState(data)
                syncRustAuthentication(true)

                _authState.update {
                    it.copy(
                        isLoggedIn = true,
                        isWebSession = true,
                        baseUrl = prefs.baseUrl,
                        emailInput = prefs.accountEmail,
                        passwordInput = "",
                        otpInput = "",
                        totpRequired = false,
                        isLoading = false,
                        errorMessage = null
                    )
                }
                hasLoadedInitialData = true
                hasLoadedDeletedAliases = false
                refreshAllData(showIndicator = true)
            } else {
                _authState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = finishRes.optString("error", "Passkey sign-in failed")
                    )
                }
            }
        }
    }

    fun registerPasskeyCredential(activityContext: Context) {
        viewModelScope.launch {
            val beginRes = MailxRustBridge.passkeyRegisterBegin(
                baseUrl = prefs.baseUrl,
                bearerToken = prefs.bearerToken
            )
            if (!beginRes.optBoolean("ok", false)) {
                emitToast(beginRes.optString("error", "Could not start passkey registration"))
                return@launch
            }

            val beginData = beginRes.optJSONObject("data") ?: JSONObject()
            val optionsObj = beginData.optJSONObject("options") ?: JSONObject()
            val ceremonyCookie = beginData.optString("ceremony_cookie", "")

            val createResult = PasskeyManager.createPasskeyCredential(activityContext, optionsObj)
            val registrationJson = createResult.getOrElse { err ->
                emitToast(err.message ?: "Passkey creation cancelled")
                return@launch
            }

            val finishRes = MailxRustBridge.passkeyRegisterFinish(
                baseUrl = prefs.baseUrl,
                bearerToken = prefs.bearerToken,
                ceremonyCookie = ceremonyCookie,
                credentialJson = registrationJson
            )
            prefs.syncRefreshedTokenIfPresent(finishRes)
            if (finishRes.optBoolean("ok", false)) {
                emitToast("Passkey added to your account")
                fetchAccountAndSubscription()
            } else {
                emitToast(finishRes.optString("error", "Failed to complete passkey registration"))
            }
        }
    }

    fun connectWithAccessKey() {
        val currentAuth = _authState.value
        val check = JSONObject(MailxRustBridge.nativeValidateAccessKey(currentAuth.accessKeyInput))
        if (!check.optBoolean("ok", false)) {
            _authState.update { it.copy(errorMessage = check.optString("error")) }
            return
        }

        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, errorMessage = null) }
            val authRes = MailxRustBridge.authenticate(currentAuth.baseUrl, currentAuth.accessKeyInput)
            if (authRes.optBoolean("ok", false)) {
                val data = authRes.optJSONObject("data") ?: JSONObject()
                prefs.baseUrl = currentAuth.baseUrl
                prefs.accessKey = currentAuth.accessKeyInput
                prefs.bearerToken = data.optString("token")
                prefs.applyServerDefaults(data)
                applyDefaultsJsonToState(data)
                syncRustAuthentication(true)

                _authState.update {
                    it.copy(
                        isLoggedIn = true,
                        isWebSession = false,
                        baseUrl = prefs.baseUrl,
                        accessKeyInput = "",
                        isLoading = false,
                        errorMessage = null
                    )
                }
                hasLoadedInitialData = true
                hasLoadedDeletedAliases = false
                refreshAllData(showIndicator = true)
            } else {
                _authState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = authRes.optString("error", "Authentication failed")
                    )
                }
            }
        }
    }

    fun initiatePasswordReset(email: String) {
        val clean = email.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val body = JSONObject().put("email", clean).toString()
            val res = MailxRustBridge.apiRequest(
                _authState.value.baseUrl,
                "",
                "",
                "POST",
                "/v1/initiatepasswordreset",
                body
            )
            if (res.optBoolean("ok", false)) {
                _authState.update {
                    it.copy(errorMessage = "Password reset instructions have been sent to $clean if the account exists.")
                }
            } else {
                _authState.update {
                    it.copy(errorMessage = res.optString("error", "Could not initiate password reset"))
                }
            }
        }
    }

    fun confirmPasswordReset(otpToken: String, newPassword: String) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("otp", otpToken.trim())
                put("password", newPassword)
            }.toString()
            val res = MailxRustBridge.apiRequest(
                _authState.value.baseUrl,
                "",
                "",
                "PUT",
                "/v1/resetpassword",
                body
            )
            if (res.optBoolean("ok", false)) {
                _authState.update {
                    it.copy(errorMessage = "Your password has been updated. You can now sign in.")
                }
            } else {
                _authState.update {
                    it.copy(errorMessage = res.optString("error", "Failed to reset password"))
                }
            }
        }
    }

    fun refreshAllData(showIndicator: Boolean = true) {
        refreshAliases(showIndicator = showIndicator)
        syncServerDefaults(silent = true)
        fetchRecipients()
        fetchDomains()
        fetchRules()
        fetchBlocklist()
        fetchUserStats()
        fetchAccountAndSubscription()
    }

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotEmpty()) {
                delay(300)
                loadAliasesInternal(showIndicator = false)
            } else if (_aliases.value.isEmpty() && prefs.cachedAliasesJson.isNotEmpty()) {
                runCatching {
                    _aliases.value = UiAlias.listFromJsonArray(JSONArray(prefs.cachedAliasesJson))
                }
            }
        }
    }

    fun selectFilterChip(chipKey: String) {
        if (_filterState.value.selectedChip == chipKey) return
        _filterState.update { it.copy(selectedChip = chipKey) }
        if (chipKey == "deleted" && !hasLoadedDeletedAliases) {
            refreshAliases(showIndicator = false)
        }
    }

    fun navigateToFilteredAliases(chipKey: String) {
        _filterState.update { it.copy(selectedChip = chipKey) }
        _currentTab.value = MainNavTab.ALIASES
        if (chipKey == "deleted" && !hasLoadedDeletedAliases) {
            refreshAliases(showIndicator = false)
        }
    }

    fun applyFilterOptions(
        chipKey: String,
        sortField: AliasSortField,
        sortDescending: Boolean,
        pinnedFirst: Boolean
    ) {
        _filterState.update {
            it.copy(
                selectedChip = chipKey,
                sortField = sortField,
                sortDescending = sortDescending,
                pinnedFirst = pinnedFirst
            )
        }
        _activeSheet.value = ActiveSheet.None
        if (chipKey == "deleted" && !hasLoadedDeletedAliases) {
            refreshAliases(showIndicator = false)
        }
    }

    fun resetFilterOptions() {
        _filterState.value = FilterUiState()
        _activeSheet.value = ActiveSheet.None
    }

    fun refreshAliases(showIndicator: Boolean = true) {
        viewModelScope.launch {
            loadAliasesInternal(showIndicator = showIndicator)
        }
    }

    private suspend fun loadAliasesInternal(showIndicator: Boolean = false) {
        if (!prefs.isConfigured()) return
        if (showIndicator) {
            _isLoadingAliases.value = true
        }
        try {
            val filter = _filterState.value
            val wantDeleted = filter.selectedChip == "deleted"
            val serverStatus = if (wantDeleted) "deleted" else "active_inactive"

            val res = MailxRustBridge.fetchAliases(
                baseUrl = prefs.baseUrl,
                accessKey = prefs.accessKey,
                bearerToken = prefs.bearerToken,
                search = filter.searchQuery,
                status = serverStatus,
                wildcard = ""
            )
            prefs.syncRefreshedTokenIfPresent(res)
            if (res.optBoolean("ok", false)) {
                val arr = res.optJSONObject("data")?.optJSONArray("aliases")
                val parsed = UiAlias.listFromJsonArray(arr)
                if (wantDeleted) {
                    hasLoadedDeletedAliases = true
                    val activeAndInactive = _aliases.value.filter { it.deletedAt.isBlank() }
                    _aliases.value = (activeAndInactive + parsed).distinctBy { it.id }
                } else {
                    val deletedItems = _aliases.value.filter { it.deletedAt.isNotBlank() }
                    if (filter.searchQuery.isEmpty()) {
                        _aliases.value = (parsed + deletedItems).distinctBy { it.id }
                        if (arr != null) {
                            prefs.cachedAliasesJson = arr.toString()
                        }
                    } else {
                        _aliases.value = (parsed + _aliases.value).distinctBy { it.id }
                    }
                }
            } else if (showIndicator) {
                emitToast(res.optString("error", "Failed to fetch aliases"))
            }
        } finally {
            if (showIndicator) {
                _isLoadingAliases.value = false
            }
        }
    }

    fun syncServerDefaults(silent: Boolean = false) {
        viewModelScope.launch {
            val res = MailxRustBridge.fetchDefaults(
                baseUrl = prefs.baseUrl,
                accessKey = prefs.accessKey,
                bearerToken = prefs.bearerToken
            )
            prefs.syncRefreshedTokenIfPresent(res)
            if (res.optBoolean("ok", false)) {
                res.optJSONObject("data")?.let { defObj ->
                    prefs.applyServerDefaults(defObj)
                    applyDefaultsJsonToState(defObj)
                    if (!silent) emitToast("Server settings synchronized")
                }
            } else if (!silent) {
                emitToast(res.optString("error", "Synchronization failed"))
            }
        }
    }

    private fun applyDefaultsJsonToState(defObj: JSONObject) {
        val defDomain = prefs.defaultDomain.ifEmpty { "mailx.net" }
        val defRec = prefs.defaultRecipient
        val defFmt = prefs.defaultFormat.ifEmpty { "words" }

        val domSet = linkedSetOf<String>()
        if (defDomain.isNotEmpty()) domSet.add(defDomain)
        defObj.optJSONArray("domains")?.let { arr ->
            for (i in 0 until arr.length()) {
                val d = arr.optString(i, "").trim()
                if (d.isNotEmpty()) domSet.add(d)
            }
        }
        defObj.optJSONArray("custom_domains")?.let { arr ->
            val parsedDomains = UiCustomDomain.listFromJsonArray(arr)
            if (parsedDomains.isNotEmpty()) {
                _customDomains.value = parsedDomains
            }
            parsedDomains.forEach { cd ->
                if (cd.name.isNotEmpty() && cd.enabled) domSet.add(cd.name)
            }
        }
        EncryptedPrefsManager.DEFAULT_SHARED_DOMAINS.forEach { domSet.add(it) }

        val recSet = linkedSetOf<String>()
        defRec.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { recSet.add(it) }
        defObj.optJSONArray("recipients")?.let { arr ->
            for (i in 0 until arr.length()) {
                val r = arr.optString(i, "").trim()
                if (r.isNotEmpty()) recSet.add(r)
            }
        }

        _defaultsState.update { prev ->
            prev.copy(
                defaultDomain = defDomain,
                defaultRecipient = defRec,
                defaultFormat = defFmt,
                defaultFromName = defObj.optString("from_name", prev.defaultFromName),
                availableDomains = domSet.toList(),
                availableRecipients = recSet.toList(),
                blockTrackers = defObj.optBoolean("block_trackers", prev.blockTrackers),
                removeHeader = defObj.optBoolean("remove_header", prev.removeHeader),
                logIssues = defObj.optBoolean("log_issues", prev.logIssues),
                unsubscribeBehaviour = defObj.optInt("list_unsubscribe_behaviour", prev.unsubscribeBehaviour)
            )
        }

        if (_recipients.value.isEmpty() && recSet.isNotEmpty()) {
            _recipients.value = recSet.mapIndexed { idx, email ->
                UiRecipient(
                    id = "default_$idx",
                    createdAt = "",
                    email = email,
                    isActive = true,
                    pgpKey = "",
                    pgpEnabled = false,
                    pgpInline = false
                )
            }
        }
    }

    fun updateDefaultDomain(domain: String) {
        prefs.defaultDomain = domain
        _defaultsState.update { it.copy(defaultDomain = domain) }
        viewModelScope.launch {
            val body = JSONObject().put("domain", domain).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/settings/domain", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Default domain updated: @$domain")
            } else {
                emitToast("Local default domain set: @$domain")
            }
        }
    }

    fun updateDefaultRecipient(recipient: String) {
        prefs.defaultRecipient = recipient
        _defaultsState.update { it.copy(defaultRecipient = recipient) }
        viewModelScope.launch {
            val body = JSONObject().put("recipient", recipient).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/settings/recipient", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Default recipient updated")
            } else {
                emitToast("Default recipient saved: $recipient")
            }
        }
    }

    fun updateDefaultFormat(format: String) {
        prefs.defaultFormat = format
        _defaultsState.update { it.copy(defaultFormat = format) }
        viewModelScope.launch {
            val body = JSONObject().put("alias_format", format).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/settings/alias-format", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Default format updated: ${formatAliasTypeLabel(format)}")
            } else {
                emitToast("Default format set: ${formatAliasTypeLabel(format)}")
            }
        }
    }

    fun updateDefaultFromName(fromName: String) {
        val clean = fromName.trim()
        prefs.defaultFromName = clean
        _defaultsState.update { it.copy(defaultFromName = clean) }
        viewModelScope.launch {
            val body = JSONObject().put("from_name", clean).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/settings/from-name", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("From Name updated")
            } else {
                emitToast(res.optString("error", "From Name saved locally"))
            }
        }
    }

    fun updateIncludeMailxHeader(includeHeader: Boolean) {
        val removeHeader = !includeHeader
        prefs.removeHeader = removeHeader
        _defaultsState.update { it.copy(removeHeader = removeHeader) }
        viewModelScope.launch {
            val body = JSONObject().put("remove_header", removeHeader).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/settings/remove-header", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast(if (includeHeader) "Mailx header enabled" else "Mailx header hidden")
            } else {
                emitToast(res.optString("error", "Failed to update setting"))
            }
        }
    }

    fun updateBlockTrackers(block: Boolean) {
        prefs.blockTrackers = block
        _defaultsState.update { it.copy(blockTrackers = block) }
        viewModelScope.launch {
            val body = JSONObject().put("block_trackers", block).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/settings/block-trackers", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast(if (block) "Email tracker blocking enabled" else "Email tracker blocking disabled")
            } else {
                emitToast(res.optString("error", "Failed to update setting"))
            }
        }
    }

    fun updateUnsubscribeBehaviour(behaviour: Int) {
        prefs.unsubscribeBehaviour = behaviour
        _defaultsState.update { it.copy(unsubscribeBehaviour = behaviour) }
        viewModelScope.launch {
            val body = JSONObject().put("list_unsubscribe_behaviour", behaviour).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/settings/list-unsubscribe-behaviour", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Unsubscribe behaviour updated")
            } else {
                emitToast(res.optString("error", "Failed to update setting"))
            }
        }
    }

    fun updateLogIssues(enable: Boolean) {
        prefs.logIssues = enable
        _defaultsState.update { it.copy(logIssues = enable) }
        viewModelScope.launch {
            val body = JSONObject().put("log_issues", enable).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/settings/log-issues", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast(if (enable) "Diagnostics logging enabled" else "Diagnostics logging disabled")
            } else {
                emitToast(res.optString("error", "Failed to update setting"))
            }
        }
    }

    fun updateDarkModeMode(mode: Int) {
        prefs.darkModeMode = mode
        _themeState.update { it.copy(darkModeMode = mode) }
    }

    fun toggleDynamicColor(enabled: Boolean) {
        prefs.dynamicColorEnabled = enabled
        _themeState.update { it.copy(dynamicColorEnabled = enabled) }
    }

    fun toggleAmoledDark(enabled: Boolean) {
        prefs.amoledDarkEnabled = enabled
        _themeState.update { it.copy(amoledDarkEnabled = enabled) }
    }

    fun toggleExpressiveMotion(enabled: Boolean) {
        prefs.expressiveMotionEnabled = enabled
        _themeState.update { it.copy(expressiveMotionEnabled = enabled) }
    }

    fun toggleReducedMotion(enabled: Boolean) {
        prefs.reducedMotionEnabled = enabled
        _themeState.update { it.copy(reducedMotionEnabled = enabled) }
    }

    fun fetchWildcardDomainInfo(domainParam: String) {
        if (!prefs.isConfigured() || domainParam.isBlank()) return
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/alias/wildcard-domain-info?domain=$domainParam"
            )
            if (res.optBoolean("ok", false)) {
                _wildcardDomainInfo.value = UiWildcardDomainInfo.fromJson(res.optJSONObject("data"))
            } else {
                _wildcardDomainInfo.value = UiWildcardDomainInfo()
            }
        }
    }

    fun createAlias(requestJson: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val res = MailxRustBridge.createAlias(
                prefs.baseUrl,
                prefs.accessKey,
                prefs.bearerToken,
                requestJson
            )
            prefs.syncRefreshedTokenIfPresent(res)
            if (res.optBoolean("ok", false)) {
                val aliasObj = res.optJSONObject("data")?.optJSONObject("alias")
                val newName = aliasObj?.optString("name", "") ?: ""
                if (aliasObj != null) {
                    val createdUi = UiAlias.fromJson(aliasObj)
                    _aliases.update { listOf(createdUi) + it.filterNot { a -> a.id == createdUi.id } }
                }
                _activeSheet.value = ActiveSheet.None
                if (newName.isNotEmpty()) onCreated(newName)
                loadAliasesInternal(showIndicator = false)
            } else {
                emitToast(res.optString("error", "Failed to create alias"))
            }
        }
    }

    fun toggleAliasEnabled(alias: UiAlias, newEnabled: Boolean) {
        _aliases.update { list ->
            list.map { if (it.id == alias.id) it.copy(enabled = newEnabled) else it }
        }
        viewModelScope.launch {
            val req = JSONObject().apply {
                put("description", alias.description)
                put("enabled", newEnabled)
                put("recipients", alias.recipients)
                put("from_name", alias.fromName)
                put("format", if (alias.wildcard) "wildcard" else "words")
                put("domain", alias.domainPart)
            }.toString()
            val res = MailxRustBridge.updateAlias(
                prefs.baseUrl,
                prefs.accessKey,
                prefs.bearerToken,
                alias.id,
                req
            )
            prefs.syncRefreshedTokenIfPresent(res)
            if (!res.optBoolean("ok", false)) {
                emitToast(res.optString("error", "Failed to update alias status"))
            }
            loadAliasesInternal(showIndicator = false)
        }
    }

    fun toggleAliasPinned(alias: UiAlias, newPinned: Boolean) {
        _aliases.update { list ->
            list.map { if (it.id == alias.id) it.copy(pinned = newPinned) else it }
        }
        viewModelScope.launch {
            val res = MailxRustBridge.pinAlias(
                prefs.baseUrl,
                prefs.accessKey,
                prefs.bearerToken,
                alias.id,
                newPinned
            )
            prefs.syncRefreshedTokenIfPresent(res)
            if (!res.optBoolean("ok", false)) {
                emitToast(res.optString("error", "Failed to update pin status"))
            }
            loadAliasesInternal(showIndicator = false)
        }
    }

    fun updateAliasMetadata(alias: UiAlias, description: String, fromName: String, recipients: String) {
        _aliases.update { list ->
            list.map {
                if (it.id == alias.id) {
                    it.copy(description = description, fromName = fromName, recipients = recipients)
                } else {
                    it
                }
            }
        }
        viewModelScope.launch {
            val req = JSONObject().apply {
                put("description", description)
                put("enabled", alias.enabled)
                put("recipients", recipients)
                put("from_name", fromName)
                put("format", if (alias.wildcard) "wildcard" else "words")
                put("domain", alias.domainPart)
            }.toString()
            val res = MailxRustBridge.updateAlias(
                prefs.baseUrl,
                prefs.accessKey,
                prefs.bearerToken,
                alias.id,
                req
            )
            prefs.syncRefreshedTokenIfPresent(res)
            if (res.optBoolean("ok", false)) {
                emitToast("Alias updated")
                loadAliasesInternal(showIndicator = false)
            } else {
                emitToast(res.optString("error", "Update failed"))
            }
        }
    }

    fun pauseAlias(aliasId: String, value: Int, unit: String) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("value", value.coerceIn(1, 10))
                put("unit", unit)
            }.toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/alias/$aliasId/pause", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Alias paused temporarily")
                loadAliasesInternal(showIndicator = false)
            } else {
                emitToast(res.optString("error", "Failed to pause alias"))
            }
        }
    }

    fun unpauseAlias(aliasId: String) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "DELETE", "/v1/alias/$aliasId/pause"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Alias unpaused")
                loadAliasesInternal(showIndicator = false)
            } else {
                emitToast(res.optString("error", "Failed to unpause alias"))
            }
        }
    }

    fun restoreAlias(alias: UiAlias) {
        _aliases.update { list ->
            list.map { if (it.id == alias.id) it.copy(deletedAt = "") else it }
        }
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/alias/restore/${alias.id}"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Alias restored: ${alias.name}")
                _activeSheet.value = ActiveSheet.None
                loadAliasesInternal(showIndicator = false)
            } else {
                emitToast(res.optString("error", "Failed to restore alias"))
            }
        }
    }

    fun forgetAlias(alias: UiAlias) {
        _aliases.update { list -> list.filterNot { it.id == alias.id } }
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "DELETE", "/v1/alias/forget/${alias.id}"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Alias permanently forgotten: ${alias.name}")
                _activeSheet.value = ActiveSheet.None
                loadAliasesInternal(showIndicator = false)
            } else {
                emitToast(res.optString("error", "Operation failed"))
            }
        }
    }

    fun deleteAliasConfirmed(alias: UiAlias) {
        _activeSheet.value = ActiveSheet.None
        _aliases.update { list -> list.filterNot { it.id == alias.id } }
        viewModelScope.launch {
            val res = MailxRustBridge.deleteAlias(
                prefs.baseUrl,
                prefs.accessKey,
                prefs.bearerToken,
                alias.id
            )
            prefs.syncRefreshedTokenIfPresent(res)
            if (res.optBoolean("ok", false)) {
                emitToast("Alias deleted: ${alias.name}")
                hasLoadedDeletedAliases = false
                loadAliasesInternal(showIndicator = false)
            } else {
                emitToast(res.optString("error", "Failed to delete alias"))
                loadAliasesInternal(showIndicator = false)
            }
        }
    }

    fun bulkSetAliasesEnabled(ids: List<String>, enabled: Boolean) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        _aliases.update { list ->
            list.map { if (it.id in idSet) it.copy(enabled = enabled) else it }
        }
        viewModelScope.launch {
            val idsArr = JSONArray().apply { ids.forEach { put(it) } }
            val body = JSONObject().apply {
                put("ids", idsArr)
                put("enabled", enabled)
            }.toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/aliases/bulk/enable", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Updated ${ids.size} aliases")
                loadAliasesInternal(showIndicator = false)
            } else {
                emitToast(res.optString("error", "Bulk update failed"))
            }
        }
    }

    fun bulkSetAliasesPinned(ids: List<String>, pinned: Boolean) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        _aliases.update { list ->
            list.map { if (it.id in idSet) it.copy(pinned = pinned) else it }
        }
        viewModelScope.launch {
            val idsArr = JSONArray().apply { ids.forEach { put(it) } }
            val body = JSONObject().apply {
                put("ids", idsArr)
                put("pinned", pinned)
            }.toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/aliases/bulk/pin", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Updated pin status for ${ids.size} aliases")
                loadAliasesInternal(showIndicator = false)
            } else {
                emitToast(res.optString("error", "Bulk pin failed"))
            }
        }
    }

    fun bulkDeleteAliases(ids: List<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        _aliases.update { list -> list.filterNot { it.id in idSet } }
        viewModelScope.launch {
            val idsArr = JSONArray().apply { ids.forEach { put(it) } }
            val body = JSONObject().put("ids", idsArr).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/aliases/bulk/delete", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Deleted ${ids.size} aliases")
                hasLoadedDeletedAliases = false
                loadAliasesInternal(showIndicator = false)
            } else {
                emitToast(res.optString("error", "Bulk delete failed"))
            }
        }
    }

    fun bulkRestoreAliases(ids: List<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        _aliases.update { list ->
            list.map { if (it.id in idSet) it.copy(deletedAt = "") else it }
        }
        viewModelScope.launch {
            val idsArr = JSONArray().apply { ids.forEach { put(it) } }
            val body = JSONObject().put("ids", idsArr).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/aliases/bulk/restore", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Restored ${ids.size} aliases")
                loadAliasesInternal(showIndicator = false)
            } else {
                emitToast(res.optString("error", "Bulk restore failed"))
            }
        }
    }

    fun bulkForgetAliases(ids: List<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        _aliases.update { list -> list.filterNot { it.id in idSet } }
        viewModelScope.launch {
            val idsArr = JSONArray().apply { ids.forEach { put(it) } }
            val body = JSONObject().put("ids", idsArr).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/aliases/bulk/forget", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Permanently deleted ${ids.size} aliases")
                loadAliasesInternal(showIndicator = false)
            } else {
                emitToast(res.optString("error", "Bulk forget failed"))
            }
        }
    }

    fun fetchRecipients() {
        if (!prefs.isConfigured()) return
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/recipients"
            )
            if (res.optBoolean("ok", false)) {
                val arr = res.optJSONArray("data")
                if (arr != null) {
                    val list = UiRecipient.listFromJsonArray(arr)
                    _recipients.value = list
                    val verifiedEmails = list.filter { it.isActive }.map { it.email }
                    if (verifiedEmails.isNotEmpty()) {
                        _defaultsState.update { it.copy(availableRecipients = verifiedEmails) }
                    }
                }
            }
        }
    }

    fun addRecipient(email: String) {
        val clean = email.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val body = JSONObject().put("email", clean).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/recipient", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Recipient added. Verification code sent to your email.")
                fetchRecipients()
            } else {
                emitToast(res.optString("error", "Failed to add recipient"))
            }
        }
    }

    fun sendRecipientOtp(recipientId: String) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/recipient/sendotp/$recipientId"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("6-digit verification code sent")
            } else {
                emitToast(res.optString("error", "Failed to send code"))
            }
        }
    }

    fun verifyRecipientOtp(recipientId: String, otp: String) {
        viewModelScope.launch {
            val body = JSONObject().put("otp", otp.trim()).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/recipient/activate/$recipientId", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Recipient email address verified!")
                fetchRecipients()
                syncServerDefaults(silent = true)
            } else {
                emitToast(res.optString("error", "Invalid verification code"))
            }
        }
    }

    fun updateRecipientPgp(
        recipient: UiRecipient,
        pgpKey: String,
        pgpEnabled: Boolean,
        pgpInline: Boolean
    ) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("id", recipient.id)
                put("pgp_key", pgpKey.trim())
                put("pgp_enabled", pgpEnabled)
                put("pgp_inline", pgpInline)
            }.toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/recipient", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("PGP encryption settings updated")
                fetchRecipients()
            } else {
                emitToast(res.optString("error", "Failed to update PGP settings"))
            }
        }
    }

    fun deleteRecipient(recipientId: String, fallbackRecipients: String) {
        viewModelScope.launch {
            val body = JSONObject().put("recipients", fallbackRecipients.trim()).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/recipient/delete/$recipientId", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Recipient address deleted")
                fetchRecipients()
                syncServerDefaults(silent = true)
            } else {
                emitToast(res.optString("error", "Failed to delete recipient"))
            }
        }
    }

    fun fetchDomains() {
        if (!prefs.isConfigured()) return
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/domains"
            )
            if (res.optBoolean("ok", false)) {
                val arr = res.optJSONArray("data")
                if (arr != null) {
                    _customDomains.value = UiCustomDomain.listFromJsonArray(arr)
                }
            }
            val cfgRes = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/domains/dns-config"
            )
            if (cfgRes.optBoolean("ok", false)) {
                _dnsConfig.value = UiDnsConfig.fromJson(cfgRes.optJSONObject("data"))
            }
        }
    }

    fun addCustomDomain(domainName: String, onCreated: (UiCustomDomain?) -> Unit = {}) {
        val clean = domainName.trim().lowercase()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val body = JSONObject().put("name", clean).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/domain", body
            )
            if (res.optBoolean("ok", false)) {
                val domObj = res.optJSONObject("data")?.optJSONObject("domain")
                val created = domObj?.let { UiCustomDomain.fromJson(it) }
                emitToast("Domain added: $clean. Now verify your DNS TXT record.")
                fetchDomains()
                onCreated(created)
            } else {
                emitToast(res.optString("error", "Failed to add domain"))
            }
        }
    }

    fun verifyDomainDns(domainId: String) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/domain/$domainId/verify-dns"
            )
            val dataObj = res.optJSONObject("data")
            val checksArr = dataObj?.optJSONArray("checks")
            if (checksArr != null) {
                val list = buildList {
                    for (i in 0 until checksArr.length()) {
                        val c = checksArr.optJSONObject(i) ?: continue
                        add(
                            UiDnsCheckResult(
                                name = c.optString("name", ""),
                                passed = c.optBoolean("passed", false),
                                error = c.optString("error", "")
                            )
                        )
                    }
                }
                _dnsCheckResults.update { it + (domainId to list) }
            }
            if (res.optBoolean("ok", false)) {
                emitToast(dataObj?.optString("message", "DNS records verified!") ?: "DNS verified")
                fetchDomains()
                syncServerDefaults(silent = true)
            } else {
                emitToast(res.optString("error", "DNS verification failed, please check your records"))
                fetchDomains()
            }
        }
    }

    fun updateCustomDomain(
        domain: UiCustomDomain,
        description: String = domain.description,
        recipient: String = domain.recipient,
        fromName: String = domain.fromName,
        enabled: Boolean = domain.enabled,
        catchAll: Boolean = domain.catchAll,
        createAlias: Boolean = domain.createAlias
    ) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("id", domain.id)
                put("description", description.trim())
                put("recipient", recipient.trim())
                put("from_name", fromName.trim())
                put("enabled", enabled)
                put("catch_all", catchAll)
                put("create_alias", createAlias)
            }.toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/domain/${domain.id}", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Domain settings updated")
                fetchDomains()
                syncServerDefaults(silent = true)
            } else {
                emitToast(res.optString("error", "Failed to update domain"))
            }
        }
    }

    fun deleteCustomDomain(domainId: String) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "DELETE", "/v1/domain/$domainId"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Domain deleted")
                fetchDomains()
                syncServerDefaults(silent = true)
            } else {
                emitToast(res.optString("error", "Failed to delete domain"))
            }
        }
    }

    fun fetchBlocklist() {
        if (!prefs.isConfigured()) return
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/blocklist?limit=100&page=1"
            )
            if (res.optBoolean("ok", false)) {
                val itemsArr = res.optJSONObject("data")?.optJSONArray("items")
                _blocklist.value = UiBlocklistItem.listFromJsonArray(itemsArr)
            }
        }
    }

    fun addBlocklistItem(type: String, value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("type", type)
                put("value", clean)
            }.toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/blocklist", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Added to blocklist: $clean")
                fetchBlocklist()
            } else {
                emitToast(res.optString("error", "Failed to add blocklist item"))
            }
        }
    }

    fun deleteBlocklistItem(id: String) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "DELETE", "/v1/blocklist/$id"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Removed from blocklist")
                fetchBlocklist()
            } else {
                emitToast(res.optString("error", "Failed to remove item"))
            }
        }
    }

    fun fetchRules() {
        if (!prefs.isConfigured()) return
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/rules"
            )
            if (res.optBoolean("ok", false)) {
                val arr = res.optJSONArray("data")
                _rules.value = UiRule.listFromJsonArray(arr)
            }
        }
    }

    fun saveRule(
        ruleId: String?,
        name: String,
        operator: String,
        forwards: Boolean,
        replies: Boolean,
        sends: Boolean,
        conditions: List<UiRuleCondition>,
        actions: List<UiRuleAction>,
        active: Boolean = true
    ) {
        viewModelScope.launch {
            val condsArr = JSONArray().apply { conditions.forEach { put(it.toJson()) } }
            val actsArr = JSONArray().apply { actions.forEach { put(it.toJson()) } }
            val body = JSONObject().apply {
                if (!ruleId.isNullOrBlank()) put("id", ruleId)
                put("name", name.trim())
                put("operator", operator)
                put("forwards", forwards)
                put("replies", replies)
                put("sends", sends)
                put("active", active)
                put("conditions", condsArr)
                put("actions", actsArr)
            }.toString()

            val isUpdate = !ruleId.isNullOrBlank()
            val method = if (isUpdate) "PUT" else "POST"
            val path = if (isUpdate) "/v1/rule/$ruleId" else "/v1/rule"
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                method, path, body
            )
            if (res.optBoolean("ok", false)) {
                emitToast(if (isUpdate) "Rule updated" else "New rule created")
                fetchRules()
            } else {
                emitToast(res.optString("error", "Failed to save rule"))
            }
        }
    }

    fun toggleRuleActive(rule: UiRule, active: Boolean) {
        _rules.update { list -> list.map { if (it.id == rule.id) it.copy(active = active) else it } }
        viewModelScope.launch {
            val body = JSONObject().put("active", active).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/rule/${rule.id}/active", body
            )
            if (res.optBoolean("ok", false)) {
                fetchRules()
            } else {
                emitToast(res.optString("error", "Failed to update rule status"))
                fetchRules()
            }
        }
    }

    fun moveRuleOrder(ruleId: String, moveUp: Boolean) {
        val current = _rules.value.toMutableList()
        val idx = current.indexOfFirst { it.id == ruleId }
        if (idx == -1) return
        val targetIdx = if (moveUp) idx - 1 else idx + 1
        if (targetIdx !in current.indices) return

        val item = current.removeAt(idx)
        current.add(targetIdx, item)
        _rules.value = current

        viewModelScope.launch {
            val idsArr = JSONArray().apply { current.forEach { put(it.id) } }
            val body = JSONObject().put("ids", idsArr).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/rules/order", body
            )
            if (res.optBoolean("ok", false)) {
                fetchRules()
            } else {
                emitToast(res.optString("error", "Failed to update rule order"))
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "DELETE", "/v1/rule/$ruleId"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Rule deleted")
                fetchRules()
            } else {
                emitToast(res.optString("error", "Failed to delete rule"))
            }
        }
    }

    fun fetchUserStats() {
        if (!prefs.isConfigured()) return
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/user/stats"
            )
            if (res.optBoolean("ok", false)) {
                val data = res.optJSONObject("data") ?: return@launch
                val aliasesArrJson = JSONArray().apply {
                    _aliases.value.forEach { put(it.rawJson) }
                }.toString()
                val nowEpochSecs = System.currentTimeMillis() / 1000L

                val nativeTelemetry = runCatching {
                    JSONObject(
                        MailxRustBridge.nativeComputeSevenDayTelemetry(
                            data.toString(),
                            aliasesArrJson,
                            nowEpochSecs
                        )
                    )
                }.getOrNull()

                if (nativeTelemetry != null && nativeTelemetry.optBoolean("ok", false)) {
                    val tData = nativeTelemetry.optJSONObject("data")
                    val ptsArr = tData?.optJSONArray("daily_points")
                    val points = buildList {
                        if (ptsArr != null) {
                            for (i in 0 until ptsArr.length()) {
                                val pt = ptsArr.optJSONObject(i) ?: continue
                                add(
                                    UiDailyStatPoint(
                                        dateIso = pt.optString("date_iso", ""),
                                        label = pt.optString("label", ""),
                                        forwards = pt.optInt("forwards", 0),
                                        replies = pt.optInt("replies", 0),
                                        sends = pt.optInt("sends", 0),
                                        blocks = pt.optInt("blocks", 0)
                                    )
                                )
                            }
                        }
                    }
                    _userStats.value = UiUserStats(
                        forwards = tData?.optInt("forwards", 0) ?: 0,
                        blocks = tData?.optInt("blocks", 0) ?: 0,
                        replies = tData?.optInt("replies", 0) ?: 0,
                        sends = tData?.optInt("sends", 0) ?: 0,
                        aliases = tData?.optInt("aliases", _aliases.value.size) ?: _aliases.value.size,
                        dailyPoints = points
                    )
                }
            }
        }
    }

    fun checkServerHealth() {
        viewModelScope.launch {
            _serverHealthStatus.value = "Checking server health..."
            val liveRes = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/livez"
            )
            if (liveRes.optBoolean("ok", false)) {
                _serverHealthStatus.value = "Online (HTTP 200 OK - ${prefs.baseUrl})"
                emitToast("Server is online and healthy")
            } else {
                val err = liveRes.optString("error", "Unreachable")
                _serverHealthStatus.value = "Issue detected: $err"
                emitToast("Server health check: $err")
            }
        }
    }

    fun fetchDiagnosticLogs() {
        if (!prefs.isConfigured()) return
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/logs"
            )
            if (res.optBoolean("ok", false)) {
                val arr = res.optJSONArray("data")
                _diagnosticLogs.value = UiDiagnosticLog.listFromJsonArray(arr)
            }
        }
    }

    fun fetchBounceLogFile(logId: String, onLoaded: (String) -> Unit) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/log/file/$logId"
            )
            if (res.optBoolean("ok", false)) {
                val text: String = res.optJSONObject("data")?.optString("text", "")
                    ?: res.optString("data", "")
                onLoaded(text.ifBlank { "Log file content is empty." })
            } else {
                emitToast(res.optString("error", "Failed to load log file"))
            }
        }
    }

    fun deleteAllDiagnosticLogs() {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "DELETE", "/v1/logs"
            )
            if (res.optBoolean("ok", false)) {
                _diagnosticLogs.value = emptyList()
                emitToast("All diagnostic logs deleted")
            } else {
                emitToast(res.optString("error", "Failed to delete logs"))
            }
        }
    }

    fun fetchAccountAndSubscription() {
        if (!prefs.isConfigured()) return
        viewModelScope.launch {
            val userRes = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/user"
            )
            if (userRes.optBoolean("ok", false)) {
                val u = userRes.optJSONObject("data")
                if (u != null) {
                    val em: String = u.optString("email", "").ifBlank { prefs.accountEmail }
                    if (em.isNotBlank()) prefs.accountEmail = em
                    _userAccount.update { prev ->
                        prev.copy(
                            id = u.optString("id", prev.id),
                            createdAt = u.optString("created_at", prev.createdAt),
                            email = em,
                            isActive = u.optBoolean("is_active", true),
                            totpEnabled = u.optBoolean("totp_enabled", false),
                            hasPassword = u.optBoolean("has_password", true)
                        )
                    }
                }
            }

            val subRes = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/sub"
            )
            if (subRes.optBoolean("ok", false)) {
                val s = subRes.optJSONObject("data")
                if (s != null) {
                    _userAccount.update { prev ->
                        prev.copy(
                            subStatus = s.optString("status", "Active").ifBlank { "Active" },
                            subActiveUntil = s.optString("active_until", ""),
                            subTier = s.optString("tier", "Pro").ifBlank { "Pro" }
                        )
                    }
                }
            }

            val keysRes = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/accesskeys"
            )
            if (keysRes.optBoolean("ok", false)) {
                _accessKeys.value = UiAccessKey.listFromJsonArray(keysRes.optJSONArray("data"))
            }

            val credsRes = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/user/credentials"
            )
            if (credsRes.optBoolean("ok", false)) {
                _passkeys.value = UiPasskeyCredential.listFromJsonArray(credsRes.optJSONArray("data"))
            }
        }
    }

    fun sendAccountVerificationOtp() {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/user/sendotp"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Verification OTP sent to your primary email")
            } else {
                emitToast(res.optString("error", "Failed to send OTP"))
            }
        }
    }

    fun verifyAccountEmailOtp(otp: String) {
        viewModelScope.launch {
            val body = JSONObject().put("otp", otp.trim()).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/user/activate", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Primary email address verified!")
                fetchAccountAndSubscription()
            } else {
                emitToast(res.optString("error", "Invalid OTP code"))
            }
        }
    }

    fun deletePasskeyCredential(credentialId: String) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "DELETE", "/v1/user/credential/$credentialId"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Passkey removed")
                fetchAccountAndSubscription()
            } else {
                emitToast(res.optString("error", "Failed to remove passkey"))
            }
        }
    }

    fun fetchAnnouncements() {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/announcements"
            )
            if (res.optBoolean("ok", false)) {
                _announcements.value = UiAnnouncement.listFromJsonArray(res.optJSONArray("data"))
            }
        }
    }

    fun changeAccountPassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("old_password", oldPassword)
                put("password", newPassword)
            }.toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/user/changepassword", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Your password has been updated")
            } else {
                emitToast(res.optString("error", "Failed to change password"))
            }
        }
    }

    fun changeAccountEmail(newEmail: String, currentPassword: String) {
        viewModelScope.launch {
            if (currentPassword.isNotEmpty()) {
                val stepBody = JSONObject().put("password", currentPassword).toString()
                MailxRustBridge.apiRequest(
                    prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                    "POST", "/v1/user/stepup/password", stepBody
                )
            }
            val body = JSONObject().put("email", newEmail.trim()).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/user/changeemail", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Email updated. A verification code has been sent to your new address.")
                fetchAccountAndSubscription()
            } else {
                emitToast(res.optString("error", "Failed to change email address"))
            }
        }
    }

    fun initiateTotpSetup() {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/user/totp/enable"
            )
            if (res.optBoolean("ok", false)) {
                val data = res.optJSONObject("data")
                _userAccount.update {
                    it.copy(
                        totpSetupSecret = data?.optString("secret", "") ?: "",
                        totpSetupUri = data?.optString("uri", "") ?: ""
                    )
                }
            } else {
                emitToast(res.optString("error", "Failed to initiate 2FA setup"))
            }
        }
    }

    fun confirmTotpSetup(otp: String) {
        viewModelScope.launch {
            val body = JSONObject().put("otp", otp.trim()).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/user/totp/enable/confirm", body
            )
            if (res.optBoolean("ok", false)) {
                val backup = res.optJSONObject("data")?.optString("backup", "") ?: ""
                _userAccount.update {
                    it.copy(
                        totpEnabled = true,
                        totpSetupSecret = "",
                        totpSetupUri = "",
                        totpBackupCodes = backup
                    )
                }
                emitToast("2FA Authenticator enabled!")
            } else {
                emitToast(res.optString("error", "Invalid 2FA verification code"))
            }
        }
    }

    fun disableTotp(otp: String) {
        viewModelScope.launch {
            val body = JSONObject().put("otp", otp.trim()).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "PUT", "/v1/user/totp/disable", body
            )
            if (res.optBoolean("ok", false)) {
                _userAccount.update { it.copy(totpEnabled = false, totpBackupCodes = "") }
                emitToast("2FA Authenticator disabled")
            } else {
                emitToast(res.optString("error", "Failed to disable 2FA"))
            }
        }
    }

    fun createAccessKey(name: String, expiresDays: Int?) {
        viewModelScope.launch {
            val resolvedDays = (expiresDays ?: 365).coerceIn(1, 365)
            val expIso = Instant.now().plusSeconds(resolvedDays.toLong() * 86400L).toString()
            val body = JSONObject().apply {
                put("name", name.trim().ifEmpty { "Mailx Android" })
                put("expires_at", expIso)
            }.toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/accesskeys", body
            )
            if (res.optBoolean("ok", false)) {
                val tok = res.optJSONObject("data")?.optString("token", "") ?: ""
                _userAccount.update { it.copy(newlyCreatedAccessKeyToken = tok) }
                emitToast("New access key created")
                fetchAccountAndSubscription()
            } else {
                emitToast(res.optString("error", "Failed to create access key"))
            }
        }
    }

    fun clearNewlyCreatedAccessKeyToken() {
        _userAccount.update { it.copy(newlyCreatedAccessKeyToken = "") }
    }

    fun deleteAccessKey(id: String) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "DELETE", "/v1/accesskeys/$id"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Access key deleted")
                fetchAccountAndSubscription()
            } else {
                emitToast(res.optString("error", "Failed to delete access key"))
            }
        }
    }

    fun exportAliasesCsv(onCsvReady: (String) -> Unit) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/aliases/export"
            )
            if (res.optBoolean("ok", false)) {
                val csv = res.optJSONObject("data")?.optString("text", "") ?: ""
                if (csv.isNotBlank()) {
                    onCsvReady(csv)
                    return@launch
                }
            }
            val rawArr = JSONArray().apply { _aliases.value.forEach { put(it.rawJson) } }.toString()
            val rustCsvRes = runCatching {
                JSONObject(MailxRustBridge.nativeFormatAliasesCsv(rawArr))
            }.getOrNull()
            if (rustCsvRes != null && rustCsvRes.optBoolean("ok", false)) {
                onCsvReady(rustCsvRes.optString("data", ""))
            }
        }
    }

    fun importAliasesFromCsvText(csvText: String) {
        viewModelScope.launch {
            val domainsArr = JSONArray().apply {
                _customDomains.value.forEach { d ->
                    put(
                        JSONObject().apply {
                            put("id", d.id)
                            put("name", d.name)
                            put("enabled", d.enabled)
                        }
                    )
                }
            }.toString()

            val planObj = runCatching {
                JSONObject(
                    MailxRustBridge.nativeParseAliasesCsvImport(
                        csvText,
                        domainsArr,
                        prefs.defaultRecipient
                    )
                )
            }.getOrNull()

            val data = planObj?.optJSONObject("data")
            val requestsArr = data?.optJSONArray("requests")
            var skippedCount = data?.optInt("skipped", 0) ?: 0
            var importedCount = 0

            if (requestsArr != null) {
                for (i in 0 until requestsArr.length()) {
                    val reqJson = requestsArr.optJSONObject(i)?.toString().orEmpty()
                    if (reqJson.isEmpty()) continue
                    val res = MailxRustBridge.createAlias(
                        prefs.baseUrl,
                        prefs.accessKey,
                        prefs.bearerToken,
                        reqJson
                    )
                    if (res.optBoolean("ok", false)) {
                        importedCount++
                    } else {
                        skippedCount++
                    }
                }
            }

            emitToast("Imported $importedCount aliases ($skippedCount skipped)")
            loadAliasesInternal(showIndicator = false)
        }
    }

    fun requestAccountDeletion(currentPassword: String) {
        viewModelScope.launch {
            if (currentPassword.isNotEmpty()) {
                val stepBody = JSONObject().put("password", currentPassword).toString()
                MailxRustBridge.apiRequest(
                    prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                    "POST", "/v1/user/stepup/password", stepBody
                )
            }
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/user/delete/request"
            )
            if (res.optBoolean("ok", false)) {
                val code = res.optJSONObject("data")?.optString("otp", "") ?: ""
                _userAccount.update { it.copy(deleteAccountOtp = code) }
            } else {
                emitToast(res.optString("error", "Failed to request account deletion code"))
            }
        }
    }

    fun confirmAccountDeletion(otp: String) {
        viewModelScope.launch {
            val body = JSONObject().put("otp", otp.trim()).toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/user/delete", body
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Account deleted permanently")
                logoutConfirmed()
            } else {
                emitToast(res.optString("error", "Account deletion failed"))
            }
        }
    }

    fun logoutConfirmed() {
        val oldUrl = prefs.baseUrl
        val oldToken = prefs.bearerToken
        prefs.clearSession()
        syncRustAuthentication(false)
        hasLoadedInitialData = false
        hasLoadedDeletedAliases = false
        _aliases.value = emptyList()
        _recipients.value = emptyList()
        _customDomains.value = emptyList()
        _blocklist.value = emptyList()
        _rules.value = emptyList()
        _diagnosticLogs.value = emptyList()
        _accessKeys.value = emptyList()
        _passkeys.value = emptyList()
        _activeSheet.value = ActiveSheet.None
        _authState.update {
            it.copy(
                isLoggedIn = false,
                isWebSession = false,
                accessKeyInput = "",
                passwordInput = "",
                otpInput = "",
                totpRequired = false,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            if (oldToken.isNotEmpty()) {
                MailxRustBridge.logout(oldUrl, oldToken)
            }
        }
    }
}

class MailxViewModelFactory(private val prefs: EncryptedPrefsManager) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MailxViewModel(prefs) as T
    }
}
