package net.mailx.android.ui.viewmodel

import android.util.Log
import net.mailx.android.core.uniffi.AppEvent
import net.mailx.android.core.uniffi.AppState
import net.mailx.android.core.uniffi.MailxCore
import net.mailx.android.core.uniffi.Route
import net.mailx.android.core.uniffi.SecureStorePort
import net.mailx.android.core.uniffi.SessionSnapshot
import net.mailx.android.core.uniffi.Sheet
import net.mailx.android.ui.model.ActiveSheet
import net.mailx.android.ui.model.MainNavTab

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
