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

class MailxViewModel(val prefs: EncryptedPrefsManager) : ViewModel() {

    private val _authState = MutableStateFlow(
        AuthUiState(
            isLoggedIn = prefs.isConfigured(),
            baseUrl = prefs.baseUrl
        )
    )
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _currentTab = MutableStateFlow(MainNavTab.ALIASES)
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
            availableDomains = listOf(prefs.defaultDomain.ifEmpty { "mailx.net" }).distinct(),
            availableRecipients = prefs.defaultRecipient
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        )
    )
    val defaultsState: StateFlow<DefaultsUiState> = _defaultsState.asStateFlow()

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
            refreshAliases()
            syncServerDefaults(silent = true)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    private fun emitToast(msg: String) {
        _toastMessage.value = msg
    }

    fun selectTab(tab: MainNavTab) {
        _currentTab.value = tab
    }

    fun openSheet(sheet: ActiveSheet) {
        _activeSheet.value = sheet
    }

    fun closeSheet() {
        _activeSheet.value = ActiveSheet.None
    }

    fun updateBaseUrl(url: String) {
        _authState.update { it.copy(baseUrl = url, errorMessage = null) }
    }

    fun updateAccessKeyInput(key: String) {
        _authState.update { it.copy(accessKeyInput = key, errorMessage = null) }
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

                syncServerDefaults(silent = true)
                _authState.update {
                    it.copy(
                        isLoggedIn = true,
                        baseUrl = prefs.baseUrl,
                        accessKeyInput = "",
                        isLoading = false,
                        errorMessage = null
                    )
                }
                refreshAliases()
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
            emitToast(res.optString("error", "Alias listesi alınamadı"))
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
            for (i in 0 until arr.length()) {
                val cd = arr.optJSONObject(i)
                val name = cd?.optString("name", "")?.trim().orEmpty()
                val enabled = cd?.optBoolean("enabled", true) ?: true
                if (name.isNotEmpty() && enabled) domSet.add(name)
            }
        }
        if (domSet.isEmpty()) domSet.add("mailx.net")

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
                removeHeader = defObj.optBoolean("remove_header", prev.removeHeader)
            )
        }
    }

    fun updateDefaultDomain(domain: String) {
        prefs.defaultDomain = domain
        _defaultsState.update { it.copy(defaultDomain = domain) }
        emitToast("Varsayılan alan adı: @$domain")
    }

    fun updateDefaultFormat(format: String) {
        prefs.defaultFormat = format
        _defaultsState.update { it.copy(defaultFormat = format) }
        emitToast("Varsayılan format güncellendi")
    }

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
                emitToast(res.optString("error", "Alias oluşturulamadı"))
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
                emitToast("Alias bilgileri kaydedildi")
                loadAliasesInternal()
            } else {
                emitToast(res.optString("error", "Güncelleme başarısız"))
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
                emitToast("Silindi: ${alias.name}")
                loadAliasesInternal()
            } else {
                emitToast(res.optString("error", "Silinemedi"))
            }
        }
    }

    fun logoutConfirmed() {
        val oldUrl = prefs.baseUrl
        val oldToken = prefs.bearerToken
        prefs.clearSession()
        _aliases.value = emptyList()
        _activeSheet.value = ActiveSheet.None
        _authState.update { it.copy(isLoggedIn = false, accessKeyInput = "", errorMessage = null) }
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
