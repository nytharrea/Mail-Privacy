package net.mailx.android.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

/**
 * `anonaddy/addy-android` içindeki `SettingsManager.kt` mimarisinden uyarlanan,
 * Access Key, Bearer Token ve çevrimdışı alias önbelleğini Android Keystore (AES256-GCM)
 * ile şifreli saklayan yönetici.
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
        set(value) = prefs.edit().putString(KEY_DEFAULT_DOMAIN, value).apply()

    var defaultRecipient: String
        get() = prefs.getString(KEY_DEFAULT_RECIPIENT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEFAULT_RECIPIENT, value).apply()

    var defaultFormat: String
        get() = prefs.getString(KEY_DEFAULT_FORMAT, "words") ?: "words"
        set(value) = prefs.edit().putString(KEY_DEFAULT_FORMAT, value).apply()

    var cachedAliasesJson: String
        get() = prefs.getString(KEY_CACHED_ALIASES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CACHED_ALIASES, value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    fun isConfigured(): Boolean = accessKey.isNotEmpty() && bearerToken.isNotEmpty()

    fun syncRefreshedTokenIfPresent(response: org.json.JSONObject) {
        val newToken = response.optString("refreshed_token", "").trim()
        if (newToken.isNotEmpty() && newToken != bearerToken) {
            bearerToken = newToken
        }
    }

    /**
     * `POST /v1/api/authenticate` veya `GET /v1/api/defaults` yanıtındaki (`access_key.go`)
     * `domain`, `domains`, `recipient`, `recipients` ve `alias_format` alanlarını WXT eklentisiyle
     * (`wxt/components/popup/AliasCreate.vue` satır 57 ve 94) birebir aynı geri dönüş (fallback)
     * mantığıyla kaydeder.
     */
    fun applyServerDefaults(data: org.json.JSONObject) {
        val firstDomain = data.optJSONArray("domains")?.optString(0, "")?.trim().orEmpty()
        val dom = data.optString("domain", "").trim().ifEmpty {
            firstDomain.ifEmpty { defaultDomain.ifEmpty { "mailx.net" } }
        }
        if (dom.isNotEmpty()) {
            defaultDomain = dom
        }

        val firstRecipient = data.optJSONArray("recipients")?.optString(0, "")?.trim().orEmpty()
        val rec = data.optString("recipient", "").trim().ifEmpty {
            firstRecipient.ifEmpty { defaultRecipient }
        }
        if (rec.isNotEmpty()) {
            defaultRecipient = rec
        }

        val fmt = data.optString("alias_format", "").trim().ifEmpty {
            defaultFormat.ifEmpty { "words" }
        }
        if (fmt.isNotEmpty()) {
            defaultFormat = fmt
        }
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_KEY)
            .remove(KEY_BEARER_TOKEN)
            .remove(KEY_CACHED_ALIASES)
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
        private const val KEY_CACHED_ALIASES = "cached_aliases"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }
}
