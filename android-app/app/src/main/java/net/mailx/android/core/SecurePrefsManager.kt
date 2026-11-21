package net.mailx.android.core

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Encrypted application storage.
 *
 * The payload is serialized as JSON, encrypted with Tink AES-256-GCM, and stored
 * in an app-private DataStore file. Tink's keyset is wrapped by an Android
 * Keystore master key. We fail closed if Tink cannot use Android Keystore.
 *
 * The synchronous property surface is retained temporarily to avoid changing
 * hundreds of existing call sites in one risky sweep. Reads come from an
 * in-memory snapshot; writes are serialized and persisted asynchronously by
 * DataStore on Dispatchers.IO. Construction waits for the initial encrypted
 * snapshot on IO so callers never observe an uninitialized empty cache.
 */
class SecurePrefsManager(context: Context) {

    private val appContext = context.applicationContext
    private val snapshot = processSnapshot
    private val store: DataStore<JSONObject> = getStore(appContext)

    init {
        // The manager exposes a synchronous compatibility API to Android
        // components that cannot yet be converted to suspend/Flow APIs.
        // Initialize the in-memory snapshot before this instance is exposed.
        if (snapshotInitialized.compareAndSet(false, true)) {
            try {
                runBlocking(Dispatchers.IO) {
                    snapshot.set(JSONObject(store.data.first().toString()))
                }
            } catch (error: Throwable) {
                snapshotInitializationError.set(error)
                throw error
            } finally {
                snapshotReady.countDown()
            }
        } else {
            snapshotReady.await()
            snapshotInitializationError.get()?.let { throw it }
        }
    }

    companion object {
        private val processScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        private val processSnapshot = AtomicReference(JSONObject())
        private val snapshotInitialized = AtomicBoolean(false)
        private val snapshotReady = CountDownLatch(1)
        private val snapshotInitializationError = AtomicReference<Throwable?>(null)
        private val processMutations = Channel<StorageMutation>(Channel.UNLIMITED)
        private val storeLock = Any()

        @Volatile
        private var processStore: DataStore<JSONObject>? = null

        private data class StorageMutation(
            val values: Map<String, Any?> = emptyMap(),
            val removals: Set<String> = emptySet()
        )

        private fun getStore(context: Context): DataStore<JSONObject> {
            processStore?.let { return it }

            // Never call a suspending API while holding a JVM monitor. The
            // previous implementation called Flow.first() inside synchronized,
            // which Kotlin correctly rejects as a critical-section suspension.
            return synchronized(storeLock) {
                processStore ?: run {
                    // This app is intentionally single-user. Remove the previous
                    // AndroidX Security Crypto file and its Keystore master key;
                    // no legacy credentials or settings are migrated.
                    context.deleteSharedPreferences(LEGACY_PREFS_FILE)
                    runCatching {
                        java.security.KeyStore.getInstance("AndroidKeyStore").apply {
                            load(null)
                            deleteEntry(LEGACY_MASTER_KEY_ALIAS)
                        }
                    }.getOrElse {
                        throw IllegalStateException(
                            "Unable to remove legacy storage key.",
                            it
                        )
                    }

                    val created = createStore(context)
                    processStore = created
                    processScope.launch {
                        for (mutation in processMutations) {
                            created.updateData { current ->
                                JSONObject(current.toString()).apply {
                                    mutation.removals.forEach(::remove)
                                    mutation.values.forEach { (key, value) ->
                                        if (value == null) remove(key) else put(key, value)
                                    }
                                }
                            }
                        }
                    }
                    created
                }
            }
        }

    private fun createStore(context: Context): DataStore<JSONObject> {
            val aead = createAead(context)
            return DataStoreFactory.create(
                serializer = EncryptedJsonSerializer(aead),
                scope = processScope,
                produceFile = { File(context.filesDir, STORE_FILE_NAME) }
            )
        }

        private fun createAead(context: Context): Aead {
            try {
                AeadConfig.register()
                val keyset = AndroidKeysetManager.Builder()
                    .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_FILE)
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()

                // Fail closed: never silently downgrade to a cleartext keyset.
                check(keyset.isUsingKeystore) {
                    "Android Keystore-backed Tink keyset is unavailable"
                }

                return keyset.keysetHandle.getPrimitive(
                    RegistryConfiguration.get(),
                    Aead::class.java
                )
            } catch (error: Exception) {
                throw IllegalStateException(
                    "Secure storage initialization failed; refusing insecure fallback.",
                    error
                )
            }
        }

        const val DEFAULT_API_BASE_URL = "https://api.mailx.net"
        val DEFAULT_SHARED_DOMAINS = listOf(
            "mailx.net", "zeropost.net", "mailshift.net",
            "layermail.net", "ambox.net", "spotsend.com"
        )
        private const val LEGACY_PREFS_FILE = "mailx_encrypted_prefs"
        private const val LEGACY_MASTER_KEY_ALIAS = "_androidx_security_master_key_"
        private const val STORE_FILE_NAME = "mailx_secure_store.bin"
        private const val KEYSET_NAME = "mailx_storage_keyset"
        private const val KEYSET_PREFS_FILE = "mailx_tink_keyset"
        private const val MASTER_KEY_URI = "android-keystore://mailx_tink_master_key"
        private val ASSOCIATED_DATA = "net.mailx.android.secure-store.v1".toByteArray(StandardCharsets.UTF_8)

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_ACCESS_KEY = "access_key"
        private const val KEY_BEARER_TOKEN = "bearer_token"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
        private const val KEY_DEFAULT_DOMAIN = "default_domain"
        private const val KEY_DEFAULT_RECIPIENT = "default_recipient"
        private const val KEY_DEFAULT_FORMAT = "default_format"
        private const val KEY_DEFAULT_FROM_NAME = "default_from_name"
        private const val KEY_AVAILABLE_DOMAINS = "available_domains"
        private const val KEY_AVAILABLE_RECIPIENTS = "available_recipients"
        private const val KEY_BLOCK_TRACKERS = "block_trackers"
        private const val KEY_REMOVE_HEADER = "remove_header"
        private const val KEY_LOG_ISSUES = "log_issues"
        private const val KEY_UNSUBSCRIBE_BEHAVIOUR = "unsubscribe_behaviour"
        private const val KEY_CACHED_ALIASES = "cached_aliases"
        private const val KEY_WATCHED_ALIAS_IDS = "watched_alias_ids"
        private const val KEY_PREVIOUS_WATCHED_ALIASES = "previous_watched_aliases"
        private const val KEY_ALIAS_WATCH_ENABLED = "alias_watch_enabled"
        private const val KEY_ALIAS_SEARCH_ENABLED = "alias_search_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_DYNAMIC_COLOR_ENABLED = "dynamic_color_v6_enabled"
        private const val KEY_AMOLED_DARK_ENABLED = "amoled_dark_v3_enabled"
        private const val KEY_EXPRESSIVE_MOTION_ENABLED = "expressive_motion_v6_enabled"
        private const val KEY_REDUCED_MOTION_ENABLED = "reduced_motion_v1_enabled"
        private const val KEY_DARK_MODE_MODE = "dark_mode_v3_mode"
    }

    private class EncryptedJsonSerializer(
        private val aead: Aead
    ) : Serializer<JSONObject> {
        override val defaultValue: JSONObject = JSONObject()

        override suspend fun readFrom(input: InputStream): JSONObject {
            val encrypted = input.readBytes()
            if (encrypted.isEmpty()) return JSONObject()
            return try {
                val plaintext = aead.decrypt(encrypted, ASSOCIATED_DATA)
                JSONObject(String(plaintext, StandardCharsets.UTF_8))
            } catch (error: GeneralSecurityException) {
                throw CorruptionException("Encrypted Mailx storage could not be authenticated.", error)
            } catch (error: Exception) {
                throw CorruptionException("Encrypted Mailx storage is invalid.", error)
            }
        }

        override suspend fun writeTo(t: JSONObject, output: OutputStream) {
            val plaintext = t.toString().toByteArray(StandardCharsets.UTF_8)
            val encrypted = aead.encrypt(plaintext, ASSOCIATED_DATA)
            output.write(encrypted)
        }
    }

    private fun string(key: String, default: String = ""): String =
        snapshot.get().optString(key, default).takeUnless { it == "null" } ?: default

    private fun boolean(key: String, default: Boolean = false): Boolean {
        val data = snapshot.get()
        return if (data.has(key)) data.optBoolean(key, default) else default
    }

    private fun integer(key: String, default: Int = 0): Int =
        if (snapshot.get().has(key)) snapshot.get().optInt(key, default) else default

    private fun put(key: String, value: Any?) {
        synchronized(snapshot) {
            val next = JSONObject(snapshot.get().toString())
            if (value == null) next.remove(key) else next.put(key, value)
            snapshot.set(next)
            check(processMutations.trySend(StorageMutation(values = mapOf(key to value))).isSuccess) {
                "Secure storage write queue is unavailable."
            }
        }
    }

    private fun remove(vararg keys: String) {
        synchronized(snapshot) {
            val next = JSONObject(snapshot.get().toString())
            keys.forEach(next::remove)
            snapshot.set(next)
            check(processMutations.trySend(StorageMutation(removals = keys.toSet())).isSuccess) {
                "Secure storage write queue is unavailable."
            }
        }
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
            val saved = string(KEY_BASE_URL, DEFAULT_API_BASE_URL)
            val normalized = normalizeApiBaseUrl(saved)
            if (normalized != saved) put(KEY_BASE_URL, normalized)
            return normalized
        }
        set(value) = put(KEY_BASE_URL, normalizeApiBaseUrl(value))

    var accessKey: String
        get() = string(KEY_ACCESS_KEY)
        set(value) = put(KEY_ACCESS_KEY, value.trim())

    var bearerToken: String
        get() = string(KEY_BEARER_TOKEN)
        set(value) = put(KEY_BEARER_TOKEN, value.trim())

    var accountEmail: String
        get() = string(KEY_ACCOUNT_EMAIL)
        set(value) = put(KEY_ACCOUNT_EMAIL, value.trim())

    var defaultDomain: String
        get() = string(KEY_DEFAULT_DOMAIN, "mailx.net")
        set(value) = put(KEY_DEFAULT_DOMAIN, value.trim())

    var defaultRecipient: String
        get() = string(KEY_DEFAULT_RECIPIENT)
        set(value) = put(KEY_DEFAULT_RECIPIENT, value.trim())

    var defaultFormat: String
        get() = string(KEY_DEFAULT_FORMAT, "words")
        set(value) = put(KEY_DEFAULT_FORMAT, value.trim())

    var defaultFromName: String
        get() = string(KEY_DEFAULT_FROM_NAME)
        set(value) = put(KEY_DEFAULT_FROM_NAME, value.trim())

    var availableDomainsJson: String
        get() = string(KEY_AVAILABLE_DOMAINS)
        set(value) = put(KEY_AVAILABLE_DOMAINS, value)

    var availableRecipientsJson: String
        get() = string(KEY_AVAILABLE_RECIPIENTS)
        set(value) = put(KEY_AVAILABLE_RECIPIENTS, value)

    var blockTrackers: Boolean
        get() = boolean(KEY_BLOCK_TRACKERS)
        set(value) = put(KEY_BLOCK_TRACKERS, value)

    var removeHeader: Boolean
        get() = boolean(KEY_REMOVE_HEADER)
        set(value) = put(KEY_REMOVE_HEADER, value)

    var logIssues: Boolean
        get() = boolean(KEY_LOG_ISSUES)
        set(value) = put(KEY_LOG_ISSUES, value)

    var unsubscribeBehaviour: Int
        get() = integer(KEY_UNSUBSCRIBE_BEHAVIOUR)
        set(value) = put(KEY_UNSUBSCRIBE_BEHAVIOUR, value)

    var watchedAliasIdsJson: String
        get() = string(KEY_WATCHED_ALIAS_IDS, "[]")
        set(value) = put(KEY_WATCHED_ALIAS_IDS, value)

    var previousWatchedAliasesJson: String
        get() = string(KEY_PREVIOUS_WATCHED_ALIASES, "[]")
        set(value) = put(KEY_PREVIOUS_WATCHED_ALIASES, value)

    var aliasSearchEnabled: Boolean
        get() = boolean(KEY_ALIAS_SEARCH_ENABLED)
        set(value) = put(KEY_ALIAS_SEARCH_ENABLED, value)

    var aliasWatchEnabled: Boolean
        get() = boolean(KEY_ALIAS_WATCH_ENABLED)
        set(value) = put(KEY_ALIAS_WATCH_ENABLED, value)

    var cachedAliasesJson: String
        get() = string(KEY_CACHED_ALIASES)
        set(value) = put(KEY_CACHED_ALIASES, value)

    var biometricEnabled: Boolean
        get() = boolean(KEY_BIOMETRIC_ENABLED)
        set(value) = put(KEY_BIOMETRIC_ENABLED, value)

    var dynamicColorEnabled: Boolean
        get() = boolean(KEY_DYNAMIC_COLOR_ENABLED)
        set(value) = put(KEY_DYNAMIC_COLOR_ENABLED, value)

    var amoledDarkEnabled: Boolean
        get() = boolean(KEY_AMOLED_DARK_ENABLED)
        set(value) = put(KEY_AMOLED_DARK_ENABLED, value)

    var expressiveMotionEnabled: Boolean
        get() = boolean(KEY_EXPRESSIVE_MOTION_ENABLED, true)
        set(value) = put(KEY_EXPRESSIVE_MOTION_ENABLED, value)

    var reducedMotionEnabled: Boolean
        get() = boolean(KEY_REDUCED_MOTION_ENABLED)
        set(value) = put(KEY_REDUCED_MOTION_ENABLED, value)

    var darkModeMode: Int
        get() = integer(KEY_DARK_MODE_MODE, 1)
        set(value) = put(KEY_DARK_MODE_MODE, value)

    fun getAvailableDomains(): List<String> {
        val result = linkedSetOf<String>()
        if (defaultDomain.isNotEmpty()) result.add(defaultDomain)
        if (availableDomainsJson.isNotEmpty()) {
            runCatching {
                val arr = JSONArray(availableDomainsJson)
                for (i in 0 until arr.length()) {
                    arr.optString(i).trim().takeIf(String::isNotEmpty)?.let(result::add)
                }
            }
        }
        DEFAULT_SHARED_DOMAINS.forEach(result::add)
        return result.toList()
    }

    fun getAvailableRecipients(): List<String> {
        val result = linkedSetOf<String>()
        defaultRecipient.split(",").map(String::trim).filter(String::isNotEmpty).forEach(result::add)
        if (availableRecipientsJson.isNotEmpty()) {
            runCatching {
                val arr = JSONArray(availableRecipientsJson)
                for (i in 0 until arr.length()) {
                    arr.optString(i).trim().takeIf(String::isNotEmpty)?.let(result::add)
                }
            }
        }
        return result.toList()
    }

    fun isConfigured(): Boolean = bearerToken.isNotEmpty()
    fun isWebSession(): Boolean = bearerToken.startsWith("cookie:")

    fun syncRefreshedTokenIfPresent(response: JSONObject) {
        val newToken = response.optString("refreshed_token", "").trim()
        if (newToken.isNotEmpty() && newToken != bearerToken) bearerToken = newToken
    }

    fun applyServerDefaults(data: JSONObject) {
        val domainsSet = linkedSetOf<String>()
        data.optJSONArray("domains")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i, "").trim()
                .takeIf(String::isNotEmpty)?.let(domainsSet::add)
        }
        data.optJSONArray("custom_domains")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val name = item.optString("name", "").trim()
                if (name.isNotEmpty() && item.optBoolean("enabled", true)) domainsSet.add(name)
            }
        }
        domainsSet.addAll(DEFAULT_SHARED_DOMAINS)
        val domain = data.optString("domain", "").trim()
            .ifEmpty { domainsSet.firstOrNull() ?: defaultDomain }
        if (domain.isNotEmpty()) {
            defaultDomain = domain
            domainsSet.add(domain)
        }
        if (domainsSet.isNotEmpty()) availableDomainsJson = JSONArray().apply {
            domainsSet.forEach(::put)
        }.toString()

        val recipients = linkedSetOf<String>()
        data.optJSONArray("recipients")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i, "").trim()
                .takeIf(String::isNotEmpty)?.let(recipients::add)
        }
        val recipient = data.optString("recipient", "").trim()
            .ifEmpty { recipients.firstOrNull() ?: defaultRecipient }
        if (recipient.isNotEmpty()) {
            defaultRecipient = recipient
            recipient.split(",").map(String::trim).filter(String::isNotEmpty).forEach(recipients::add)
        }
        if (recipients.isNotEmpty()) availableRecipientsJson = JSONArray().apply {
            recipients.forEach(::put)
        }.toString()

        data.optString("alias_format", "").trim().takeIf(String::isNotEmpty)?.let {
            defaultFormat = it
        }
        if (data.has("from_name")) defaultFromName = data.optString("from_name", "")
        if (data.has("block_trackers")) blockTrackers = data.optBoolean("block_trackers", false)
        if (data.has("remove_header")) removeHeader = data.optBoolean("remove_header", false)
        if (data.has("log_issues")) logIssues = data.optBoolean("log_issues", false)
        if (data.has("list_unsubscribe_behaviour")) {
            unsubscribeBehaviour = data.optInt("list_unsubscribe_behaviour", 0)
        }
    }

    fun getWatchedAliasIds(): Set<String> = runCatching {
        val array = JSONArray(watchedAliasIdsJson)
        buildSet {
            for (i in 0 until array.length()) {
                array.optString(i).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.getOrDefault(emptySet())

    fun setAliasWatched(aliasId: String, watched: Boolean): Boolean {
        if (aliasId.isBlank()) return false
        val ids = getWatchedAliasIds().toMutableSet()
        if (watched && ids.size >= 25 && aliasId !in ids) return false
        if (watched) ids.add(aliasId) else ids.remove(aliasId)
        watchedAliasIdsJson = JSONArray().apply { ids.sorted().forEach(::put) }.toString()
        if (!watched) {
            previousWatchedAliasesJson = runCatching {
                val old = JSONArray(previousWatchedAliasesJson)
                JSONArray().apply {
                    for (i in 0 until old.length()) {
                        val item = old.optJSONObject(i) ?: continue
                        if (item.optString("id") != aliasId) put(item)
                    }
                }.toString()
            }.getOrDefault("[]")
        }
        return true
    }

    fun clearSession() {
        remove(
            KEY_ACCESS_KEY,
            KEY_BEARER_TOKEN,
            KEY_ACCOUNT_EMAIL,
            KEY_CACHED_ALIASES,
            KEY_WATCHED_ALIAS_IDS,
            KEY_PREVIOUS_WATCHED_ALIASES,
            KEY_ALIAS_WATCH_ENABLED,
            KEY_ALIAS_SEARCH_ENABLED,
            KEY_AVAILABLE_DOMAINS,
            KEY_AVAILABLE_RECIPIENTS
        )
    }

}
