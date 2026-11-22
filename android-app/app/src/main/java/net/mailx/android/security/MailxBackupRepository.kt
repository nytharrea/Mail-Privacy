package net.mailx.android.security

import net.mailx.android.core.SecurePrefsManager
import org.json.JSONObject

/** Local preferences only by default. Optional session secrets are explicitly requested. */
object MailxBackupRepository {
    private const val VERSION = 1

    suspend fun export(prefs: SecurePrefsManager, includeSession: Boolean): JSONObject {
        val stored = prefs.readDurableSnapshot()
        return JSONObject().apply {
            put("format", "mailx-android-settings")
            put("version", VERSION)
            put("settings", JSONObject().apply {
                put("baseUrl", stored.optString("base_url", SecurePrefsManager.DEFAULT_API_BASE_URL))
                put("defaultDomain", stored.optString("default_domain", "mailx.net"))
                put("defaultRecipient", stored.optString("default_recipient", ""))
                put("defaultFormat", stored.optString("default_format", "words"))
                put("defaultFromName", stored.optString("default_from_name", ""))
                put("blockTrackers", stored.optBoolean("block_trackers", false))
                put("removeHeader", stored.optBoolean("remove_header", false))
                put("logIssues", stored.optBoolean("log_issues", false))
                put("unsubscribeBehaviour", stored.optInt("unsubscribe_behaviour", 0))
                put("darkModeMode", stored.optInt("dark_mode_v3_mode", 1))
                put("dynamicColorEnabled", stored.optBoolean("dynamic_color_v6_enabled", false))
                put("amoledDarkEnabled", stored.optBoolean("amoled_dark_v3_enabled", false))
                put("expressiveMotionEnabled", stored.optBoolean("expressive_motion_v6_enabled", true))
                put("reducedMotionEnabled", stored.optBoolean("reduced_motion_v1_enabled", false))
                put("biometricEnabled", stored.optBoolean("biometric_enabled", false))
                put("aliasWatchEnabled", stored.optBoolean("alias_watch_enabled", false))
                put("watchedAliasIdsJson", stored.optString("watched_alias_ids", "[]"))
                put("aliasSearchEnabled", stored.optBoolean("alias_search_enabled", false))
            })
            if (includeSession) {
                put("session", JSONObject().apply {
                    put("accessKey", stored.optString("access_key", ""))
                    put("bearerToken", stored.optString("bearer_token", ""))
                    put("accountEmail", stored.optString("account_email", ""))
                })
            }
        }
    }

    /** Validate before mutating preferences; do not restore watch counter snapshots. */
    suspend fun restore(prefs: SecurePrefsManager, payload: JSONObject, restoreSession: Boolean) {
        require(payload.optString("format") == "mailx-android-settings" &&
            payload.optInt("version") == VERSION) { "Unsupported backup format" }
        val s = payload.optJSONObject("settings") ?: error("Missing settings")
        val base = s.optString("baseUrl", "")
        require(base.startsWith("https://") && base.length <= 2048) { "Invalid server URL" }
        val current = prefs.readDurableSnapshot()
        val watched = org.json.JSONArray(s.optString("watchedAliasIdsJson", "[]"))
        require(watched.length() <= 25) { "Too many watched aliases" }
        val session = if (restoreSession) payload.optJSONObject("session") else null
        if (restoreSession) require(session != null) { "This backup has no session credentials" }

        val values = linkedMapOf<String, Any?>(
            "base_url" to base,
            "default_domain" to s.optString(
                "defaultDomain",
                current.optString("default_domain", "mailx.net")
            ),
            "default_recipient" to s.optString("defaultRecipient", ""),
            "default_format" to s.optString("defaultFormat", "words"),
            "default_from_name" to s.optString("defaultFromName", ""),
            "block_trackers" to s.optBoolean("blockTrackers", false),
            "remove_header" to s.optBoolean("removeHeader", false),
            "log_issues" to s.optBoolean("logIssues", false),
            "unsubscribe_behaviour" to s.optInt("unsubscribeBehaviour", 0),
            "dark_mode_v3_mode" to s.optInt("darkModeMode", -1),
            "dynamic_color_v6_enabled" to s.optBoolean("dynamicColorEnabled", false),
            "amoled_dark_v3_enabled" to s.optBoolean("amoledDarkEnabled", false),
            "expressive_motion_v6_enabled" to s.optBoolean("expressiveMotionEnabled", true),
            "reduced_motion_v1_enabled" to s.optBoolean("reducedMotionEnabled", false),
            // Never enable the app lock without validating biometrics on this device.
            "biometric_enabled" to false,
            "watched_alias_ids" to watched.toString(),
            "previous_watched_aliases" to "[]",
            "alias_watch_enabled" to (s.optBoolean("aliasWatchEnabled", false) && watched.length() > 0),
            // Search index is rebuilt only after an explicit new opt-in on this device.
            "alias_search_enabled" to false
        )
        if (session != null) {
            values["access_key"] = session.optString("accessKey", "").trim()
            values["bearer_token"] = session.optString("bearerToken", "").trim()
            values["account_email"] = session.optString("accountEmail", "").trim()
        }

        // One ordered durable transaction: no partially restored settings if a
        // later write fails, and no success response before DataStore commits.
        prefs.writeValuesAndPersist(values)
    }
}
