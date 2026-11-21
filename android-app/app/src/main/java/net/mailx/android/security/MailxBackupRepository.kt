package net.mailx.android.security

import net.mailx.android.core.SecurePrefsManager
import org.json.JSONObject

/** Local preferences only by default. Optional session secrets are explicitly requested. */
object MailxBackupRepository {
    private const val VERSION = 1

    fun export(prefs: SecurePrefsManager, includeSession: Boolean): JSONObject =
        JSONObject().apply {
            put("format", "mailx-android-settings")
            put("version", VERSION)
            put("settings", JSONObject().apply {
                put("baseUrl", prefs.baseUrl)
                put("defaultDomain", prefs.defaultDomain)
                put("defaultRecipient", prefs.defaultRecipient)
                put("defaultFormat", prefs.defaultFormat)
                put("defaultFromName", prefs.defaultFromName)
                put("blockTrackers", prefs.blockTrackers)
                put("removeHeader", prefs.removeHeader)
                put("logIssues", prefs.logIssues)
                put("unsubscribeBehaviour", prefs.unsubscribeBehaviour)
                put("darkModeMode", prefs.darkModeMode)
                put("dynamicColorEnabled", prefs.dynamicColorEnabled)
                put("amoledDarkEnabled", prefs.amoledDarkEnabled)
                put("expressiveMotionEnabled", prefs.expressiveMotionEnabled)
                put("reducedMotionEnabled", prefs.reducedMotionEnabled)
                put("biometricEnabled", prefs.biometricEnabled)
                put("aliasWatchEnabled", prefs.aliasWatchEnabled)
                put("watchedAliasIdsJson", prefs.watchedAliasIdsJson)
                put("aliasSearchEnabled", prefs.aliasSearchEnabled)
            })
            if (includeSession) {
                put("session", JSONObject().apply {
                    put("accessKey", prefs.accessKey)
                    put("bearerToken", prefs.bearerToken)
                    put("accountEmail", prefs.accountEmail)
                })
            }
        }

    /** Validate before mutating preferences; do not restore watch counter snapshots. */
    suspend fun restore(prefs: SecurePrefsManager, payload: JSONObject, restoreSession: Boolean) {
        require(payload.optString("format") == "mailx-android-settings" &&
            payload.optInt("version") == VERSION) { "Unsupported backup format" }
        val s = payload.optJSONObject("settings") ?: error("Missing settings")
        val base = s.optString("baseUrl", "")
        require(base.startsWith("https://") && base.length <= 2048) { "Invalid server URL" }
        val watched = org.json.JSONArray(s.optString("watchedAliasIdsJson", "[]"))
        require(watched.length() <= 25) { "Too many watched aliases" }
        val session = if (restoreSession) payload.optJSONObject("session") else null
        if (restoreSession) require(session != null) { "This backup has no session credentials" }

        prefs.baseUrl = base
        prefs.defaultDomain = s.optString("defaultDomain", prefs.defaultDomain)
        prefs.defaultRecipient = s.optString("defaultRecipient", "")
        prefs.defaultFormat = s.optString("defaultFormat", "words")
        prefs.defaultFromName = s.optString("defaultFromName", "")
        prefs.blockTrackers = s.optBoolean("blockTrackers", false)
        prefs.removeHeader = s.optBoolean("removeHeader", false)
        prefs.logIssues = s.optBoolean("logIssues", false)
        prefs.unsubscribeBehaviour = s.optInt("unsubscribeBehaviour", 0)
        prefs.darkModeMode = s.optInt("darkModeMode", -1)
        prefs.dynamicColorEnabled = s.optBoolean("dynamicColorEnabled", false)
        prefs.amoledDarkEnabled = s.optBoolean("amoledDarkEnabled", false)
        prefs.expressiveMotionEnabled = s.optBoolean("expressiveMotionEnabled", true)
        prefs.reducedMotionEnabled = s.optBoolean("reducedMotionEnabled", false)
        // Never enable the app lock without first validating biometrics on this device.
        prefs.biometricEnabled = false
        prefs.watchedAliasIdsJson = watched.toString()
        prefs.previousWatchedAliasesJson = "[]"
        prefs.aliasWatchEnabled = s.optBoolean("aliasWatchEnabled", false) && watched.length() > 0
        // Search index is rebuilt only after an explicit new opt-in on this device.
        prefs.aliasSearchEnabled = false
        if (session != null) {
            prefs.setSessionSecretsAndPersist(
                accessKey = session.optString("accessKey", ""),
                bearerToken = session.optString("bearerToken", ""),
                accountEmail = session.optString("accountEmail", "")
            )
        } else {
            prefs.persistPendingWrites()
        }
    }
}
