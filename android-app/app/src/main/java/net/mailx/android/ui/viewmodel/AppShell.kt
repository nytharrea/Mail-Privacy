package net.mailx.android.ui.viewmodel

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mailx.android.core.uniffi.ApiCallResult
import net.mailx.android.core.uniffi.AppEvent
import net.mailx.android.core.uniffi.AppState
import net.mailx.android.core.uniffi.MailxCore
import net.mailx.android.core.uniffi.Route
import net.mailx.android.core.uniffi.SecureStorePort
import net.mailx.android.core.uniffi.SessionSnapshot
import net.mailx.android.core.uniffi.Sheet
import net.mailx.android.ui.model.ActiveSheet
import net.mailx.android.ui.model.MainNavTab
import org.json.JSONArray
import org.json.JSONObject

internal class AppShell(authenticated: Boolean) : AutoCloseable {
    private val core = MailxCore()

    var state: AppState = core.state()
        private set

    val tab: MainNavTab
        get() = state.route.toTab()

    val canGoBack: Boolean
        get() = state.canGoBack

    val hasSheet: Boolean
        get() = state.sheet != null

    init {
        if (authenticated) authenticate()
    }

    fun attachSecureStore(store: SecureStorePort) {
        core.attachSecureStore(store)
    }

    fun sessionSnapshot(): SessionSnapshot = core.sessionSnapshot()

    fun restoreSession(): SessionSnapshot {
        val snapshot = core.restoreSession()
        state = core.state()
        return snapshot
    }

    fun saveSession(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        accountEmail: String
    ): SessionSnapshot {
        val snapshot = core.saveSession(baseUrl, accessKey, bearerToken, accountEmail)
        state = core.state()
        return snapshot
    }

    fun rotateSessionToken(expectedToken: String, newToken: String): Boolean =
        core.rotateSessionToken(expectedToken, newToken)

    fun clearSession(): SessionSnapshot {
        val snapshot = core.clearSession()
        state = core.state()
        return snapshot
    }

    suspend fun loginWithAccessKey(baseUrl: String, accessKey: String): ApiCallResult =
        withContext(Dispatchers.IO) {
            core.loginWithAccessKey(baseUrl, accessKey).also { state = core.state() }
        }

    suspend fun loginWithPassword(
        baseUrl: String,
        email: String,
        password: String,
        otp: String = ""
    ): ApiCallResult = withContext(Dispatchers.IO) {
        core.loginWithPassword(baseUrl, email, password, otp).also { state = core.state() }
    }

    suspend fun passkeyLoginBegin(baseUrl: String, email: String = ""): ApiCallResult =
        withContext(Dispatchers.IO) {
            core.passkeyLoginBegin(baseUrl, email)
        }

    suspend fun passkeyLoginFinish(
        baseUrl: String,
        ceremonyCookie: String,
        credentialJson: String,
        discoverable: Boolean,
        fallbackEmail: String = ""
    ): ApiCallResult = withContext(Dispatchers.IO) {
        core.passkeyLoginFinish(
            baseUrl,
            ceremonyCookie,
            credentialJson,
            discoverable,
            fallbackEmail
        ).also { state = core.state() }
    }

    suspend fun passkeyRegisterBegin(): ApiCallResult = withContext(Dispatchers.IO) {
        core.passkeyRegisterBegin()
    }

    suspend fun passkeyRegisterFinish(
        ceremonyCookie: String,
        credentialJson: String
    ): ApiCallResult = withContext(Dispatchers.IO) {
        core.passkeyRegisterFinish(ceremonyCookie, credentialJson)
    }

    suspend fun apiRequest(
        method: String,
        path: String,
        bodyJson: String = ""
    ): ApiCallResult = withContext(Dispatchers.IO) {
        core.apiRequest(method, path, bodyJson)
    }

    suspend fun importAliasesCsv(csvText: String): ApiCallResult = withContext(Dispatchers.IO) {
        core.importAliasesCsv(csvText)
    }

    suspend fun fetchDefaults(): ApiCallResult = withContext(Dispatchers.IO) {
        core.fetchDefaults()
    }

    suspend fun fetchAliases(
        search: String = "",
        status: String = "active_inactive",
        wildcard: String = "",
        page: Int = 1
    ): ApiCallResult = withContext(Dispatchers.IO) {
        core.fetchAliases(search, status, wildcard, page.coerceAtLeast(1).toUInt())
    }

    suspend fun createAlias(requestJson: String): ApiCallResult = withContext(Dispatchers.IO) {
        core.createAlias(requestJson)
    }

    suspend fun updateAlias(aliasId: String, requestJson: String): ApiCallResult =
        withContext(Dispatchers.IO) {
            core.updateAlias(aliasId, requestJson)
        }

    suspend fun pinAlias(aliasId: String, pinned: Boolean): ApiCallResult =
        withContext(Dispatchers.IO) {
            core.pinAlias(aliasId, pinned)
        }

    suspend fun deleteAlias(aliasId: String): ApiCallResult = withContext(Dispatchers.IO) {
        core.deleteAlias(aliasId)
    }

    suspend fun logoutSession(): ApiCallResult = withContext(Dispatchers.IO) {
        core.logoutSession().also { state = core.state() }
    }

    fun authenticate(): Boolean = send(AppEvent.Authenticate)

    fun signOut(): Boolean = send(AppEvent.SignOut)

    fun navigate(tab: MainNavTab): Boolean = send(AppEvent.Navigate(tab.toRoute()))

    fun openSheet(sheet: ActiveSheet): Boolean {
        val kind = sheet.toSheet() ?: return closeSheet()
        return send(AppEvent.OpenSheet(kind))
    }

    fun closeSheet(): Boolean = send(AppEvent.CloseSheet)

    fun back(): Boolean = send(AppEvent.Back)

    override fun close() = core.close()

    private fun send(event: AppEvent): Boolean {
        val next = try {
            core.dispatch(event)
        } catch (error: Exception) {
            Log.w(TAG, "Rust shell rejected $event", error)
            return false
        }
        state = next
        return true
    }

    private companion object {
        const val TAG = "AppShell"
    }
}

internal fun ApiCallResult.toJsonEnvelope(): JSONObject = JSONObject().apply {
    put("ok", ok)
    if (ok && dataJson.isNotBlank()) {
        val trimmed = dataJson.trim()
        when {
            trimmed.startsWith("{") -> put("data", JSONObject(trimmed))
            trimmed.startsWith("[") -> put("data", JSONArray(trimmed))
            else -> put("data", trimmed)
        }
    }
    error?.let { put("error", it) }
    errorCode?.let { put("error_code", it) }
}

private fun MainNavTab.toRoute(): Route = when (this) {
    MainNavTab.HOME -> Route.HOME
    MainNavTab.ALIASES -> Route.ALIASES
    MainNavTab.RECIPIENTS -> Route.RECIPIENTS
    MainNavTab.RULES -> Route.RULES
    MainNavTab.SETTINGS -> Route.SETTINGS
}

private fun Route.toTab(): MainNavTab = when (this) {
    Route.HOME -> MainNavTab.HOME
    Route.ALIASES -> MainNavTab.ALIASES
    Route.RECIPIENTS -> MainNavTab.RECIPIENTS
    Route.RULES -> MainNavTab.RULES
    Route.SETTINGS -> MainNavTab.SETTINGS
}

private fun ActiveSheet.toSheet(): Sheet? = when (this) {
    ActiveSheet.None -> null
    ActiveSheet.CreateAlias -> Sheet.CREATE_ALIAS
    ActiveSheet.FilterOptions -> Sheet.FILTER_OPTIONS
    ActiveSheet.ProfileMenu -> Sheet.PROFILE_MENU
    ActiveSheet.AppearanceSheet -> Sheet.APPEARANCE
    ActiveSheet.DiagnosticsSheet -> Sheet.DIAGNOSTICS
    ActiveSheet.AccountSheet -> Sheet.ACCOUNT
    ActiveSheet.AnnouncementsSheet -> Sheet.ANNOUNCEMENTS
    ActiveSheet.TrackersReportSheet -> Sheet.TRACKERS_REPORT
    ActiveSheet.PrivacyLabSheet -> Sheet.PRIVACY_LAB
    is ActiveSheet.ManageAlias -> Sheet.MANAGE_ALIAS
    is ActiveSheet.WildcardGen -> Sheet.WILDCARD_GEN
    is ActiveSheet.ReverseSend -> Sheet.REVERSE_SEND
    is ActiveSheet.ConfirmDelete -> Sheet.CONFIRM_DELETE
    ActiveSheet.ConfirmLogout -> Sheet.CONFIRM_LOGOUT
}
