package net.mailx.android.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore

/**
 * `anonaddy/addy-android` içindeki `SettingsManager.kt` mimarisinden uyarlanan,
 * Access Key, Bearer Token, sunucu varsayılanları ve çevrimdışı alias önbelleğini
 * Android Keystore (AES256-GCM) ile şifreli saklayan yönetici.
 */
class EncryptedPrefsManager(context: Context) {

    private val prefs: SharedPreferences = createSafeEncryptedPrefs(context.applicationContext)

    private fun createSafeEncryptedPrefs(appContext: Context): SharedPreferences {
        return runCatching {
            buildEncryptedPrefs(appContext)
        }.getOrElse {
            // Cihaz kilit ekranı sıfırlandığında veya Keystore bozulduğunda temizleyip yeniden oluştur
            runCatching {
                appContext.deleteSharedPreferences(PREFS_FILE_NAME)
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
            buildEncryptedPrefs(appContext)
        }
    }

    private fun buildEncryptedPrefs(appContext: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun normalizeApiBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return when (trimmed.lowercase()) {
            "", "https://mailx.net", "https://www.mailx.net", "https://app.mailx.net" ->
                DEFAULT_API_BASE_URL
            else -> trimmed
        }
    }

    var baseUrl: String
        get() {
            val saved = prefs.getString(KEY_BASE_URL, DEFAULT_API_BASE_URL) ?: DEFAULT_API_BASE_URL
            val normalized = normalizeApiBaseUrl(saved)
            if (normalized != saved) {
                prefs.edit().putString(KEY_BASE_URL, normalized).apply()
            }
            return normalized
        }
        set(value) = prefs.edit().putString(KEY_BASE_URL, normalizeApiBaseUrl(value)).apply()

    var accessKey: String
        get() = prefs.getString(KEY_ACCESS_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACCESS_KEY, value.trim()).apply()

    var bearerToken: String
        get() = prefs.getString(KEY_BEARER_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BEARER_TOKEN, value.trim()).apply()

    var defaultDomain: String
        get() = prefs.getString(KEY_DEFAULT_DOMAIN, "mailx.net") ?: "mailx.net"
        set(value) = prefs.edit().putString(KEY_DEFAULT_DOMAIN, value.trim()).apply()

    var defaultRecipient: String
        get() = prefs.getString(KEY_DEFAULT_RECIPIENT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEFAULT_RECIPIENT, value.trim()).apply()

    var defaultFormat: String
        get() = prefs.getString(KEY_DEFAULT_FORMAT, "words") ?: "words"
        set(value) = prefs.edit().putString(KEY_DEFAULT_FORMAT, value.trim()).apply()

    var defaultFromName: String
        get() = prefs.getString(KEY_DEFAULT_FROM_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEFAULT_FROM_NAME, value.trim()).apply()

    var availableDomainsJson: String
        get() = prefs.getString(KEY_AVAILABLE_DOMAINS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AVAILABLE_DOMAINS, value).apply()

    var availableRecipientsJson: String
        get() = prefs.getString(KEY_AVAILABLE_RECIPIENTS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AVAILABLE_RECIPIENTS, value).apply()

    var blockTrackers: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_TRACKERS, false)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_TRACKERS, value).apply()

    var removeHeader: Boolean
        get() = prefs.getBoolean(KEY_REMOVE_HEADER, false)
        set(value) = prefs.edit().putBoolean(KEY_REMOVE_HEADER, value).apply()

    var logIssues: Boolean
        get() = prefs.getBoolean(KEY_LOG_ISSUES, false)
        set(value) = prefs.edit().putBoolean(KEY_LOG_ISSUES, value).apply()

    var cachedAliasesJson: String
        get() = prefs.getString(KEY_CACHED_ALIASES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CACHED_ALIASES, value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var dynamicColorEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR_ENABLED, value).apply()

    var amoledDarkEnabled: Boolean
        get() = prefs.getBoolean(KEY_AMOLED_DARK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AMOLED_DARK_ENABLED, value).apply()

    fun getAvailableDomains(): List<String> {
        val result = linkedSetOf<String>()
        if (defaultDomain.isNotEmpty()) result.add(defaultDomain)
        if (availableDomainsJson.isNotEmpty()) {
            runCatching {
                val arr = JSONArray(availableDomainsJson)
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotEmpty()) result.add(item)
                }
            }
        }
        if (result.isEmpty()) result.add("mailx.net")
        return result.toList()
    }

    fun getAvailableRecipients(): List<String> {
        val result = linkedSetOf<String>()
        if (defaultRecipient.isNotEmpty()) {
            defaultRecipient.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                result.add(it)
            }
        }
        if (availableRecipientsJson.isNotEmpty()) {
            runCatching {
                val arr = JSONArray(availableRecipientsJson)
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotEmpty()) result.add(item)
                }
            }
        }
        return result.toList()
    }

    fun isConfigured(): Boolean = accessKey.isNotEmpty() && bearerToken.isNotEmpty()

    fun syncRefreshedTokenIfPresent(response: JSONObject) {
        val newToken = response.optString("refreshed_token", "").trim()
        if (newToken.isNotEmpty() && newToken != bearerToken) {
            bearerToken = newToken
        }
    }

    /**
     * `POST /v1/api/authenticate` veya `GET /v1/api/defaults` yanıtındaki (`access_key.go`)
     * `domain`, `domains`, `custom_domains`, `recipient`, `recipients`, `alias_format`,
     * `from_name`, `block_trackers`, `remove_header` ve `log_issues` alanlarını kaydeder.
     */
    fun applyServerDefaults(data: JSONObject) {
        val domainsSet = linkedSetOf<String>()
        val domainsArr = data.optJSONArray("domains")
        if (domainsArr != null) {
            for (i in 0 until domainsArr.length()) {
                val d = domainsArr.optString(i, "").trim()
                if (d.isNotEmpty()) domainsSet.add(d)
            }
        }
        val customDomainsArr = data.optJSONArray("custom_domains")
        if (customDomainsArr != null) {
            for (i in 0 until customDomainsArr.length()) {
                val cdObj = customDomainsArr.optJSONObject(i)
                val cdName = cdObj?.optString("name", "")?.trim().orEmpty()
                val cdEnabled = cdObj?.optBoolean("enabled", true) ?: true
                if (cdName.isNotEmpty() && cdEnabled) {
                    domainsSet.add(cdName)
                }
            }
        }

        val firstDomain = domainsSet.firstOrNull().orEmpty()
        val dom = data.optString("domain", "").trim().ifEmpty {
            firstDomain.ifEmpty { defaultDomain.ifEmpty { "mailx.net" } }
        }
        if (dom.isNotEmpty()) {
            defaultDomain = dom
            domainsSet.add(dom)
        }
        if (domainsSet.isNotEmpty()) {
            val outArr = JSONArray()
            domainsSet.forEach { outArr.put(it) }
            availableDomainsJson = outArr.toString()
        }

        val recipientsSet = linkedSetOf<String>()
        val recipientsArr = data.optJSONArray("recipients")
        if (recipientsArr != null) {
            for (i in 0 until recipientsArr.length()) {
                val r = recipientsArr.optString(i, "").trim()
                if (r.isNotEmpty()) recipientsSet.add(r)
            }
        }
        val firstRecipient = recipientsSet.firstOrNull().orEmpty()
        val rec = data.optString("recipient", "").trim().ifEmpty {
            firstRecipient.ifEmpty { defaultRecipient }
        }
        if (rec.isNotEmpty()) {
            defaultRecipient = rec
            rec.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                recipientsSet.add(it)
            }
        }
        if (recipientsSet.isNotEmpty()) {
            val outRecArr = JSONArray()
            recipientsSet.forEach { outRecArr.put(it) }
            availableRecipientsJson = outRecArr.toString()
        }

        val fmt = data.optString("alias_format", "").trim().ifEmpty {
            defaultFormat.ifEmpty { "words" }
        }
        if (fmt.isNotEmpty()) {
            defaultFormat = fmt
        }

        if (data.has("from_name")) {
            defaultFromName = data.optString("from_name", "")
        }
        if (data.has("block_trackers")) {
            blockTrackers = data.optBoolean("block_trackers", false)
        }
        if (data.has("remove_header")) {
            removeHeader = data.optBoolean("remove_header", false)
        }
        if (data.has("log_issues")) {
            logIssues = data.optBoolean("log_issues", false)
        }
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_KEY)
            .remove(KEY_BEARER_TOKEN)
            .remove(KEY_CACHED_ALIASES)
            .remove(KEY_AVAILABLE_DOMAINS)
            .remove(KEY_AVAILABLE_RECIPIENTS)
            .apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "mailx_encrypted_prefs"
        const val DEFAULT_API_BASE_URL = "https://api.mailx.net"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_ACCESS_KEY = "access_key"
        private const val KEY_BEARER_TOKEN = "bearer_token"
        private const val KEY_DEFAULT_DOMAIN = "default_domain"
        private const val KEY_DEFAULT_RECIPIENT = "default_recipient"
        private const val KEY_DEFAULT_FORMAT = "default_format"
        private const val KEY_DEFAULT_FROM_NAME = "default_from_name"
        private const val KEY_AVAILABLE_DOMAINS = "available_domains"
        private const val KEY_AVAILABLE_RECIPIENTS = "available_recipients"
        private const val KEY_BLOCK_TRACKERS = "block_trackers"
        private const val KEY_REMOVE_HEADER = "remove_header"
        private const val KEY_LOG_ISSUES = "log_issues"
        private const val KEY_CACHED_ALIASES = "cached_aliases"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_DYNAMIC_COLOR_ENABLED = "dynamic_color_enabled"
        private const val KEY_AMOLED_DARK_ENABLED = "amoled_dark_enabled"
    }
}
