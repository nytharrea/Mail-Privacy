package net.mailx.android.ui.viewmodel

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
import net.mailx.android.ui.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
            amoledDarkEnabled = prefs.amoledDarkEnabled
        )
    )
    val themeState: StateFlow<ThemeUiState> = _themeState.asStateFlow()

    // Mailx tam özellik durum akışları
    private val _recipients = MutableStateFlow<List<UiRecipient>>(emptyList())
    val recipients: StateFlow<List<UiRecipient>> = _recipients.asStateFlow()

    private val _customDomains = MutableStateFlow<List<UiCustomDomain>>(emptyList())
    val customDomains: StateFlow<List<UiCustomDomain>> = _customDomains.asStateFlow()

    private val _dnsConfig = MutableStateFlow(UiDnsConfig())
    val dnsConfig: StateFlow<UiDnsConfig> = _dnsConfig.asStateFlow()

    private val _dnsCheckResults = MutableStateFlow<Map<String, List<UiDnsCheckResult>>>(emptyMap())
    val dnsCheckResults: StateFlow<Map<String, List<UiDnsCheckResult>>> = _dnsCheckResults.asStateFlow()

    private val _blocklist = MutableStateFlow<List<UiBlocklistItem>>(emptyList())
    val blocklist: StateFlow<List<UiBlocklistItem>> = _blocklist.asStateFlow()

    private val _rules = MutableStateFlow<List<UiRule>>(emptyList())
    val rules: StateFlow<List<UiRule>> = _rules.asStateFlow()

    private val _userStats = MutableStateFlow(UiUserStats())
    val userStats: StateFlow<UiUserStats> = _userStats.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<UiDiagnosticLog>>(emptyList())
    val diagnosticLogs: StateFlow<List<UiDiagnosticLog>> = _diagnosticLogs.asStateFlow()

    private val _accessKeys = MutableStateFlow<List<UiAccessKey>>(emptyList())
    val accessKeys: StateFlow<List<UiAccessKey>> = _accessKeys.asStateFlow()

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

    private var searchJob: Job? = null

    init {
        if (prefs.cachedAliasesJson.isNotEmpty()) {
            runCatching {
                _aliases.value = UiAlias.listFromJsonArray(JSONArray(prefs.cachedAliasesJson))
            }
        }
        if (prefs.isConfigured()) {
            refreshAllData()
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun emitToast(msg: String) {
        _toastMessage.value = msg
    }

    fun selectTab(tab: MainNavTab) {
        _currentTab.value = tab
        when (tab) {
            MainNavTab.HOME -> {
                refreshAliases()
                fetchUserStats()
            }
            MainNavTab.ALIASES -> refreshAliases()
            MainNavTab.RECIPIENTS -> {
                fetchRecipients()
                fetchDomains()
            }
            MainNavTab.RULES -> {
                fetchRules()
                fetchBlocklist()
            }
            MainNavTab.SETTINGS -> {
                syncServerDefaults(silent = true)
                fetchAccountAndSubscription()
            }
        }
    }

    fun openSheet(sheet: ActiveSheet) {
        _activeSheet.value = sheet
        when (sheet) {
            ActiveSheet.DiagnosticsSheet -> fetchDiagnosticLogs()
            ActiveSheet.AccountSheet -> fetchAccountAndSubscription()
            ActiveSheet.AnnouncementsSheet -> fetchAnnouncements()
            else -> Unit
        }
    }

    fun closeSheet() {
        _activeSheet.value = ActiveSheet.None
    }

    fun updateBaseUrl(url: String) {
        _authState.update { it.copy(baseUrl = url, errorMessage = null) }
    }

    fun updateLoginMode(mode: String) {
        _authState.update { it.copy(loginMode = mode, errorMessage = null, totpRequired = false) }
    }

    fun updateEmailInput(email: String) {
        _authState.update { it.copy(emailInput = email, errorMessage = null) }
    }

    fun updatePasswordInput(password: String) {
        _authState.update { it.copy(passwordInput = password, errorMessage = null) }
    }

    fun updateOtpInput(otp: String) {
        _authState.update { it.copy(otpInput = otp, errorMessage = null) }
    }

    fun updateAccessKeyInput(key: String) {
        _authState.update { it.copy(accessKeyInput = key, errorMessage = null) }
    }

    /**
     * `POST /v1/login` ile E-posta, Parola ve 2FA TOTP kodu kullanarak tam yetkili Web Oturumu başlatır.
     */
    fun connectWithCredentials() {
        val currentAuth = _authState.value
        if (currentAuth.emailInput.isBlank() || currentAuth.passwordInput.isEmpty()) {
            _authState.update { it.copy(errorMessage = "Lütfen e-posta adresinizi ve parolanızı girin.") }
            return
        }

        viewModelScope.launch {
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
                refreshAllData()
            } else {
                val errCode = loginRes.optInt("error_code", 0)
                val errMsg = loginRes.optString("error", "Giriş başarısız")
                _authState.update {
                    it.copy(
                        isLoading = false,
                        totpRequired = it.totpRequired || errCode == 70001,
                        errorMessage = if (errCode == 70001) {
                            "İki aşamalı doğrulama (2FA) aktif. Lütfen 6 haneli doğrulama kodunu girin."
                        } else {
                            errMsg
                        }
                    )
                }
            }
        }
    }

    /**
     * `POST /v1/api/authenticate` ile 85 karakterlik Access Key kullanarak oturum açar.
     */
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
                refreshAllData()
            } else {
                _authState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = authRes.optString("error", "Kimlik doğrulama başarısız")
                    )
                }
            }
        }
    }

    fun refreshAllData() {
        refreshAliases()
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
            if (query.isNotEmpty()) delay(260)
            loadAliasesInternal()
        }
    }

    fun selectFilterChip(chipKey: String) {
        _filterState.update { it.copy(selectedChip = chipKey) }
        refreshAliases()
    }

    fun navigateToFilteredAliases(chipKey: String) {
        _filterState.update { it.copy(selectedChip = chipKey) }
        _currentTab.value = MainNavTab.ALIASES
        refreshAliases()
    }

    fun applyFilterOptions(
        chipKey: String,
        sortField: AliasSortField,
        sortDescending: Boolean,
        pinnedFirst: Boolean
    ) {
        val chipChanged = _filterState.value.selectedChip != chipKey
        _filterState.update {
            it.copy(
                selectedChip = chipKey,
                sortField = sortField,
                sortDescending = sortDescending,
                pinnedFirst = pinnedFirst
            )
        }
        _activeSheet.value = ActiveSheet.None
        if (chipChanged) refreshAliases()
    }

    fun resetFilterOptions() {
        _filterState.value = FilterUiState()
        _activeSheet.value = ActiveSheet.None
        refreshAliases()
    }

    fun refreshAliases() {
        viewModelScope.launch {
            loadAliasesInternal()
        }
    }

    private suspend fun loadAliasesInternal() {
        if (!prefs.isConfigured()) return
        _isLoadingAliases.value = true
        val filter = _filterState.value

        val serverStatus = when (filter.selectedChip) {
            "active" -> "active"
            "inactive" -> "inactive"
            "deleted" -> "deleted"
            else -> "active_inactive"
        }
        val wildcardParam = if (filter.selectedChip == "wildcard_only") "true" else ""

        val res = MailxRustBridge.fetchAliases(
            baseUrl = prefs.baseUrl,
            accessKey = prefs.accessKey,
            bearerToken = prefs.bearerToken,
            search = filter.searchQuery,
            status = serverStatus,
            wildcard = wildcardParam
        )
        prefs.syncRefreshedTokenIfPresent(res)
        if (res.optBoolean("ok", false)) {
            val arr = res.optJSONObject("data")?.optJSONArray("aliases")
            val parsed = UiAlias.listFromJsonArray(arr)
            _aliases.value = parsed
            if (arr != null && filter.searchQuery.isEmpty() && serverStatus == "active_inactive" && wildcardParam.isEmpty()) {
                prefs.cachedAliasesJson = arr.toString()
            }
        } else {
            emitToast(res.optString("error", "Takma ad listesi alınamadı"))
        }
        _isLoadingAliases.value = false
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
                    if (!silent) emitToast("Sunucu ayarları senkronize edildi")
                }
            } else if (!silent) {
                emitToast(res.optString("error", "Senkronizasyon başarısız"))
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

        // Eğer henüz `/v1/recipients` çekilmediyse varsayılan alıcılardan liste oluştur
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

    // =========================================================================
    // SUNUCU AYARLARI (`/v1/settings/*` - `43678.jpg`..`43690.jpg`)
    // =========================================================================

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
                emitToast("Varsayılan alan adı güncellendi: @$domain")
            } else {
                emitToast("Yerel varsayılan alan adı: @$domain")
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
                emitToast("Varsayılan alıcı güncellendi")
            } else {
                emitToast("Varsayılan alıcı kaydedildi: $recipient")
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
                emitToast("Varsayılan format güncellendi: ${formatAliasTypeLabel(format)}")
            } else {
                emitToast("Varsayılan format: ${formatAliasTypeLabel(format)}")
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
                emitToast("Gönderici adı (From Name) güncellendi")
            } else {
                emitToast(res.optString("error", "Gönderici adı yerel olarak kaydedildi"))
            }
        }
    }

    /**
     * `43682.jpg`: "Add Mailx Header" (`Sent to <alias> from <sender>`).
     * Backend'de `remove_header = !includeMailxHeader` olarak saklanır.
     */
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
                emitToast(if (includeHeader) "Mailx başlığı eklenecek" else "Mailx başlığı gizlendi")
            } else {
                emitToast(res.optString("error", "Ayar güncellenemedi"))
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
                emitToast(if (block) "E-posta takipçileri engelleniyor" else "Takipçi engelleme kapatıldı")
            } else {
                emitToast(res.optString("error", "Ayar güncellenemedi"))
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
                emitToast("Abonelikten çıkma davranışı güncellendi")
            } else {
                emitToast(res.optString("error", "Ayar güncellenemedi"))
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
                emitToast(if (enable) "Tanılama günlükleri etkinleştirildi" else "Tanılama günlükleri kapatıldı")
            } else {
                emitToast(res.optString("error", "Ayar güncellenemedi"))
            }
        }
    }

    // =========================================================================
    // TEMA VE GÖRÜNÜM
    // =========================================================================

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

    // =========================================================================
    // TAKMA AD İŞLEMLERİ (Pause, Restore, Forget, Bulk, CSV Import/Export)
    // =========================================================================

    fun createAlias(requestJson: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _isLoadingAliases.value = true
            val res = MailxRustBridge.createAlias(
                prefs.baseUrl,
                prefs.accessKey,
                prefs.bearerToken,
                requestJson
            )
            prefs.syncRefreshedTokenIfPresent(res)
            if (res.optBoolean("ok", false)) {
                val newName = res.optJSONObject("data")
                    ?.optJSONObject("alias")
                    ?.optString("name", "") ?: ""
                _activeSheet.value = ActiveSheet.None
                if (newName.isNotEmpty()) onCreated(newName)
                loadAliasesInternal()
            } else {
                _isLoadingAliases.value = false
                emitToast(res.optString("error", "Takma ad oluşturulamadı"))
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
                emitToast(res.optString("error", "Durum güncellenemedi"))
            }
            loadAliasesInternal()
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
                emitToast(res.optString("error", "Sabitleme güncellenemedi"))
            }
            loadAliasesInternal()
        }
    }

    fun updateAliasMetadata(alias: UiAlias, description: String, fromName: String, recipients: String) {
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
                emitToast("Takma ad güncellendi")
                loadAliasesInternal()
            } else {
                emitToast(res.optString("error", "Güncelleme başarısız"))
            }
        }
    }

    /**
     * `PUT /v1/alias/:id/pause` (`value`: 1..10, `unit`: `"hours"` | `"days"` | `"weeks"`)
     */
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
                emitToast("Takma ad geçici olarak duraklatıldı")
                loadAliasesInternal()
            } else {
                emitToast(res.optString("error", "Takma ad duraklatılamadı"))
            }
        }
    }

    /**
     * `DELETE /v1/alias/:id/pause`
     */
    fun unpauseAlias(aliasId: String) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "DELETE", "/v1/alias/$aliasId/pause"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Duraklatma kaldırıldı")
                loadAliasesInternal()
            } else {
                emitToast(res.optString("error", "Duraklatma kaldırılamadı"))
            }
        }
    }

    /**
     * `POST /v1/alias/restore/:id` (Silinen takma adı geri yükle)
     */
    fun restoreAlias(alias: UiAlias) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/alias/restore/${alias.id}"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Takma ad geri yüklendi: ${alias.name}")
                _activeSheet.value = ActiveSheet.None
                loadAliasesInternal()
            } else {
                emitToast(res.optString("error", "Takma ad geri yüklenemedi"))
            }
        }
    }

    /**
     * `DELETE /v1/alias/forget/:id` (Özel alan adı takma adını kalıcı olarak sil / unut)
     */
    fun forgetAlias(alias: UiAlias) {
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "DELETE", "/v1/alias/forget/${alias.id}"
            )
            if (res.optBoolean("ok", false)) {
                emitToast("Takma ad kalıcı olarak unutuldu: ${alias.name}")
                _activeSheet.value = ActiveSheet.None
                loadAliasesInternal()
            } else {
                emitToast(res.optString("error", "İşlem başarısız"))
            }
        }
    }

    fun deleteAliasConfirmed(alias: UiAlias) {
        _activeSheet.value = ActiveSheet.None
        viewModelScope.launch {
            val res = MailxRustBridge.deleteAlias(
                prefs.baseUrl,
                prefs.accessKey,
                prefs.bearerToken,
                alias.id
            )
            prefs.syncRefreshedTokenIfPresent(res)
            if (res.optBoolean("ok", false)) {
                emitToast("Takma ad silindi: ${alias.name}")
                loadAliasesInternal()
            } else {
                emitToast(res.optString("error", "Silinemedi"))
            }
        }
    }

    // =========================================================================
    // ALICILAR VE PGP ŞİFRELEME (`/v1/recipients`, `/v1/recipient` - `43640.jpg`..`43646.jpg`)
    // =========================================================================

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
                emitToast("Alıcı eklendi. Doğrulama kodu e-postanıza gönderildi.")
                fetchRecipients()
            } else {
                emitToast(res.optString("error", "Alıcı eklenemedi"))
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
                emitToast("6 haneli doğrulama kodu gönderildi")
            } else {
                emitToast(res.optString("error", "Kod gönderilemedi"))
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
                emitToast("Alıcı e-posta adresi doğrulandı!")
                fetchRecipients()
                syncServerDefaults(silent = true)
            } else {
                emitToast(res.optString("error", "Doğrulama kodu geçersiz"))
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
                emitToast("PGP şifreleme ayarları güncellendi")
                fetchRecipients()
            } else {
                emitToast(res.optString("error", "PGP ayarları güncellenemedi"))
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
                emitToast("Alıcı adresi silindi")
                fetchRecipients()
                syncServerDefaults(silent = true)
            } else {
                emitToast(res.optString("error", "Alıcı silinemedi"))
            }
        }
    }

    // =========================================================================
    // ÖZEL ALAN ADLARI (`/v1/domains`, `/v1/domains/dns-config`, `/v1/domain/:id/verify-dns` - `43648.jpg`..`43654.jpg`)
    // =========================================================================

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
                emitToast("Alan adı eklendi: $clean. Şimdi DNS TXT kaydını doğrulayın.")
                fetchDomains()
                onCreated(created)
            } else {
                emitToast(res.optString("error", "Alan adı eklenemedi"))
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
                emitToast(dataObj?.optString("message", "DNS kayıtları doğrulandı!") ?: "DNS doğrulandı")
                fetchDomains()
                syncServerDefaults(silent = true)
            } else {
                emitToast(res.optString("error", "DNS doğrulaması başarısız, kayıtları kontrol edin"))
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
                emitToast("Alan adı ayarları güncellendi")
                fetchDomains()
                syncServerDefaults(silent = true)
            } else {
                emitToast(res.optString("error", "Alan adı güncellenemedi"))
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
                emitToast("Alan adı silindi")
                fetchDomains()
                syncServerDefaults(silent = true)
            } else {
                emitToast(res.optString("error", "Alan adı silinemedi"))
            }
        }
    }

    // =========================================================================
    // ENGELLENENLER LİSTESİ (`/v1/blocklist` - `43656.jpg`)
    // =========================================================================

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
                emitToast("Engelleme listesine eklendi: $clean")
                fetchBlocklist()
            } else {
                emitToast(res.optString("error", "Engelleme listesine eklenemedi"))
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
                emitToast("Engelleme kaldırıldı")
                fetchBlocklist()
            } else {
                emitToast(res.optString("error", "Silinemedi"))
            }
        }
    }

    // =========================================================================
    // KURALLAR (`/v1/rules`, `/v1/rule` - `43658.jpg`..`43664.jpg`)
    // =========================================================================

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
                emitToast(if (isUpdate) "Kural güncellendi" else "Yeni kural oluşturuldu")
                fetchRules()
            } else {
                emitToast(res.optString("error", "Kural kaydedilemedi"))
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
                emitToast(res.optString("error", "Kural durumu güncellenemedi"))
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
                emitToast(res.optString("error", "Sıralama güncellenemedi"))
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
                emitToast("Kural silindi")
                fetchRules()
            } else {
                emitToast(res.optString("error", "Kural silinemedi"))
            }
        }
    }

    // =========================================================================
    // İSTATİSTİKLER VE TANILAMA GÜNLÜKLERİ (`/v1/user/stats`, `/v1/logs` - `43668.jpg`..`43672.jpg`)
    // =========================================================================

    fun fetchUserStats() {
        if (!prefs.isConfigured()) return
        viewModelScope.launch {
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "GET", "/v1/user/stats"
            )
            if (res.optBoolean("ok", false)) {
                val data = res.optJSONObject("data") ?: return@launch
                val msgsArr = data.optJSONArray("messages")
                val today = runCatching { LocalDate.now(ZoneOffset.UTC) }.getOrDefault(LocalDate.of(2026, 11, 17))
                val dayBuckets = linkedMapOf<LocalDate, IntArray>() // [forwards, replies, sends, blocks]
                for (offset in 6 downTo 0) {
                    dayBuckets[today.minusDays(offset.toLong())] = intArrayOf(0, 0, 0, 0)
                }

                if (msgsArr != null) {
                    for (i in 0 until msgsArr.length()) {
                        val m = msgsArr.optJSONObject(i) ?: continue
                        val rawDate = m.optString("created_at", "")
                        val msgType = m.optInt("type", 1)
                        val msgDay = runCatching {
                            Instant.parse(rawDate).atZone(ZoneOffset.UTC).toLocalDate()
                        }.getOrElse {
                            runCatching { LocalDate.parse(rawDate.take(10)) }.getOrNull()
                        }
                        if (msgDay != null && dayBuckets.containsKey(msgDay)) {
                            val arr = dayBuckets[msgDay]!!
                            when (msgType) {
                                1 -> arr[0]++
                                2 -> arr[1]++
                                3 -> arr[2]++
                                4 -> arr[3]++
                            }
                        }
                    }
                }

                val fmt = DateTimeFormatter.ofPattern("dd.MM")
                val points = dayBuckets.map { (date, counts) ->
                    UiDailyStatPoint(
                        dateIso = date.toString(),
                        label = date.format(fmt),
                        forwards = counts[0],
                        replies = counts[1],
                        sends = counts[2],
                        blocks = counts[3]
                    )
                }

                _userStats.value = UiUserStats(
                    forwards = data.optInt("forwards", 0),
                    blocks = data.optInt("blocks", 0),
                    replies = data.optInt("replies", 0),
                    sends = data.optInt("sends", 0),
                    aliases = data.optInt("aliases", _aliases.value.size),
                    dailyPoints = points
                )
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
                val text = res.optJSONObject("data")?.optString("text", "")
                    ?: res.optString("data", "")
                onLoaded(text.ifBlank { "Günlük dosyası içeriği boş." })
            } else {
                emitToast(res.optString("error", "Günlük dosyası alınamadı"))
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
                emitToast("Tüm tanılama günlükleri silindi")
            } else {
                emitToast(res.optString("error", "Günlükler silinemedi"))
            }
        }
    }

    // =========================================================================
    // HESAP, ABONELİK, 2FA TOTP, ERİŞİM ANAHTARLARI VE CSV İÇE/DIŞA AKTARIM (`43692.jpg`..`43702.jpg`)
    // =========================================================================

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
                    val em = u.optString("email", "").ifBlank { prefs.accountEmail }
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
                emitToast("Parolanız başarıyla değiştirildi")
            } else {
                emitToast(res.optString("error", "Parola değiştirilemedi"))
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
                emitToast("E-posta güncellendi. Doğrulama kodu yeni adresinize gönderildi.")
                fetchAccountAndSubscription()
            } else {
                emitToast(res.optString("error", "E-posta adresi değiştirilemedi"))
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
                emitToast(res.optString("error", "2FA kurulumu başlatılamadı"))
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
                emitToast("İki aşamalı doğrulama (2FA) etkinleştirildi!")
            } else {
                emitToast(res.optString("error", "2FA doğrulama kodu hatalı"))
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
                emitToast("İki aşamalı doğrulama devre dışı bırakıldı")
            } else {
                emitToast(res.optString("error", "2FA devre dışı bırakılamadı"))
            }
        }
    }

    fun createAccessKey(name: String, expiresDays: Int?) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("name", name.trim().ifEmpty { "Mailx Android" })
                if (expiresDays != null && expiresDays > 0) {
                    val expIso = Instant.now().plusSeconds(expiresDays.toLong() * 86400L).toString()
                    put("expires_at", expIso)
                }
            }.toString()
            val res = MailxRustBridge.apiRequest(
                prefs.baseUrl, prefs.accessKey, prefs.bearerToken,
                "POST", "/v1/accesskeys", body
            )
            if (res.optBoolean("ok", false)) {
                val tok = res.optJSONObject("data")?.optString("token", "") ?: ""
                _userAccount.update { it.copy(newlyCreatedAccessKeyToken = tok) }
                emitToast("Yeni erişim anahtarı oluşturuldu")
                fetchAccountAndSubscription()
            } else {
                emitToast(res.optString("error", "Erişim anahtarı oluşturulamadı"))
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
                emitToast("Erişim anahtarı silindi")
                fetchAccountAndSubscription()
            } else {
                emitToast(res.optString("error", "Erişim anahtarı silinemedi"))
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
            // API scope'unda ise mevcut takma adlardan resmi CSV formatında üret (`43700.jpg`)
            val sb = StringBuilder("alias,description,enabled,recipients\n")
            _aliases.value.forEach { a ->
                sb.append("${a.name},${a.description.replace(",", " ")},${a.enabled},${a.recipients.replace(",", " ")}\n")
            }
            onCsvReady(sb.toString())
        }
    }

    fun logoutConfirmed() {
        val oldUrl = prefs.baseUrl
        val oldToken = prefs.bearerToken
        prefs.clearSession()
        _aliases.value = emptyList()
        _recipients.value = emptyList()
        _customDomains.value = emptyList()
        _blocklist.value = emptyList()
        _rules.value = emptyList()
        _diagnosticLogs.value = emptyList()
        _accessKeys.value = emptyList()
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
