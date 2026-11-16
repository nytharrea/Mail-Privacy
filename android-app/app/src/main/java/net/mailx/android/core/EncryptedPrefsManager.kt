package net.mailx.android.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * `anonaddy/addy-android` içindeki `SettingsManager.kt` mimarisinden uyarlanan,
 * Access Key, Bearer Token ve çevrimdışı alias önbelleğini Android Keystore (AES256-GCM)
 * ile şifreli saklayan yönetici.
 */
class EncryptedPrefsManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "mailx_encrypted_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "https://mailx.net") ?: "https://mailx.net"
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim().trimEnd('/')).apply()

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

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_KEY)
            .remove(KEY_BEARER_TOKEN)
            .remove(KEY_CACHED_ALIASES)
            .apply()
    }

    companion object {
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
