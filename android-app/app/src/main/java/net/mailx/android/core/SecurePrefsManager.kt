package net.mailx.android.core

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

class SecurePrefsManager internal constructor(
    private val store: DataStore<JSONObject>
) {

    constructor(context: Context) : this(getStore(context.applicationContext))

    companion object {
        private val processScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        private val storeLock = Any()

        @Volatile
        private var processStore: DataStore<JSONObject>? = null

        private fun getStore(context: Context): DataStore<JSONObject> {
            processStore?.let { return it }

            return synchronized(storeLock) {
                processStore ?: run {
                    context.deleteSharedPreferences(LEGACY_PREFS_FILE)
                    runCatching {
                        KeyStore.getInstance("AndroidKeyStore").apply {
                            load(null)
                            deleteEntry(LEGACY_MASTER_KEY_ALIAS)
                        }
                    }.getOrElse {
                        throw IllegalStateException(
                            "Unable to remove legacy storage key.",
                            it
                        )
                    }

                    createStore(context).also { processStore = it }
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
                ensureHardwareBackedMasterKey()

                val keyset = AndroidKeysetManager.Builder()
                    .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_FILE)
                    .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                    .withMasterKeyUri(MASTER_KEY_URI)
                    .build()

                check(keyset.isUsingKeystore) {
                    "Android Keystore-backed Tink keyset is unavailable"
                }
                check(isHardwareSecurityLevel(masterKeySecurityLevel())) {
                    "Mailx storage master key is not hardware-backed"
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

        private fun ensureHardwareBackedMasterKey() {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                check(isHardwareSecurityLevel(masterKeySecurityLevel())) {
                    "Existing Mailx storage key is not hardware-backed; clear app storage to re-provision it."
                }
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    generateMasterKey(strongBox = true)
                    return
                } catch (_: StrongBoxUnavailableException) {
                } catch (error: ProviderException) {
                    if (!isStrongBoxUnavailable(error)) throw error
                }
            }

            generateMasterKey(strongBox = false)
            check(isHardwareSecurityLevel(masterKeySecurityLevel())) {
                "TEE-backed Android Keystore is required for Mailx secure storage."
            }
        }

        private fun isStrongBoxUnavailable(error: ProviderException): Boolean {
            val message = error.message.orEmpty().lowercase()
            return message.contains("strongbox") &&
                (message.contains("unavailable") ||
                    message.contains("not supported") ||
                    message.contains("not available"))
        }

        private fun generateMasterKey(strongBox: Boolean) {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val spec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        setIsStrongBoxBacked(strongBox)
                    }
                }
                .build()
            generator.init(spec)
            generator.generateKey()
            check(isHardwareSecurityLevel(masterKeySecurityLevel())) {
                "Android Keystore generated a software-backed master key."
            }
        }

        private fun masterKeySecurityLevel(): String {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
                ?: return "missing"
            val factory = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> "strongbox"
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "tee"
                    else -> "software"
                }
            } else {
                @Suppress("DEPRECATION")
                if (info.isInsideSecureHardware) "hardware" else "software"
            }
        }

        internal fun isHardwareSecurityLevel(level: String): Boolean =
            level == "strongbox" || level == "tee" || level == "hardware"

        internal fun createSerializer(aead: Aead): Serializer<JSONObject> =
            EncryptedJsonSerializer(aead)

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
        private const val MASTER_KEY_ALIAS = "mailx_tink_master_key"
        private const val MASTER_KEY_URI = "android-keystore://mailx_tink_master_key"
        private val ASSOCIATED_DATA = "net.mailx.android.secure-store.v1".toByteArray(StandardCharsets.UTF_8)

        private const val KEY_ACCESS_KEY = "access_key"
        private const val KEY_BEARER_TOKEN = "bearer_token"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
        private const val KEY_AVAILABLE_DOMAINS = "available_domains"
        private const val KEY_AVAILABLE_RECIPIENTS = "available_recipients"
        private const val KEY_CACHED_ALIASES = "cached_aliases"
        private const val KEY_WATCHED_ALIAS_IDS = "watched_alias_ids"
        private const val KEY_PREVIOUS_WATCHED_ALIASES = "previous_watched_aliases"
        private const val KEY_ALIAS_WATCH_ENABLED = "alias_watch_enabled"
        private const val KEY_ALIAS_SEARCH_ENABLED = "alias_search_enabled"
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

    val dataFlow: Flow<JSONObject>
        get() = store.data.map { JSONObject(it.toString()) }

    suspend fun readDurableSnapshot(): JSONObject =
        JSONObject(store.data.first().toString())

    suspend fun writeValuesAndPersist(
        values: Map<String, Any?>,
        removals: Set<String> = emptySet()
    ) {
        store.updateData { current ->
            JSONObject(current.toString()).apply {
                removals.forEach(::remove)
                values.forEach { (key, value) ->
                    if (value == null) remove(key) else put(key, value)
                }
            }
        }
    }

    fun getMasterKeySecurityLevel(): String = masterKeySecurityLevel()

    suspend fun setSessionSecretsAndPersist(
        accessKey: String,
        bearerToken: String,
        accountEmail: String
    ) {
        writeValuesAndPersist(
            values = mapOf(
                KEY_ACCESS_KEY to accessKey.trim(),
                KEY_BEARER_TOKEN to bearerToken.trim(),
                KEY_ACCOUNT_EMAIL to accountEmail.trim()
            )
        )
    }

    suspend fun clearSessionAndPersist() {
        writeValuesAndPersist(
            values = emptyMap(),
            removals = setOf(
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
        )
    }

    suspend fun getWatchedAliasIdsDurable(): Set<String> {
        val stored = readDurableSnapshot()
        return runCatching {
            val array = JSONArray(stored.optString(KEY_WATCHED_ALIAS_IDS, "[]"))
            buildSet {
                for (i in 0 until array.length()) {
                    array.optString(i).trim().takeIf(String::isNotEmpty)?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
    }

    suspend fun setAliasWatchedDurable(aliasId: String, watched: Boolean): Boolean {
        val normalizedId = aliasId.trim()
        if (normalizedId.isEmpty()) return false

        var updated = false
        store.updateData { current ->
            val ids = runCatching {
                val array = JSONArray(current.optString(KEY_WATCHED_ALIAS_IDS, "[]"))
                buildSet {
                    for (i in 0 until array.length()) {
                        array.optString(i).trim().takeIf(String::isNotEmpty)?.let(::add)
                    }
                }
            }.getOrDefault(emptySet()).toMutableSet()

            if (watched && normalizedId !in ids && ids.size >= 25) {
                updated = false
                return@updateData current
            }
            if (watched) ids.add(normalizedId) else ids.remove(normalizedId)
            updated = true

            JSONObject(current.toString()).apply {
                put(
                    KEY_WATCHED_ALIAS_IDS,
                    JSONArray().apply { ids.sorted().forEach(::put) }.toString()
                )
                if (!watched) {
                    val previous = runCatching {
                        val old = JSONArray(current.optString(KEY_PREVIOUS_WATCHED_ALIASES, "[]"))
                        JSONArray().apply {
                            for (i in 0 until old.length()) {
                                val item = old.optJSONObject(i) ?: continue
                                if (item.optString("id") != normalizedId) put(item)
                            }
                        }.toString()
                    }.getOrDefault("[]")
                    put(KEY_PREVIOUS_WATCHED_ALIASES, previous)
                }
            }
        }
        return updated
    }
}
