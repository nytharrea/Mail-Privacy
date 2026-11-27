package net.mailx.android.core

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.mailx.android.security.MailxBackupRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SecureStorageSecurityUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    companion object {
        @JvmStatic
        @BeforeClass
        fun registerTink() {
            AeadConfig.register()
        }

        private fun newAead(): Aead {
            val handle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            return handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }
    }

    private fun openPrefs(
        file: File,
        aead: Aead,
        scope: CoroutineScope
    ): SecurePrefsManager {
        val store = DataStoreFactory.create(
            serializer = SecurePrefsManager.createSerializer(aead),
            scope = scope,
            produceFile = { file }
        )
        return SecurePrefsManager(store)
    }

    @Test
    fun hardwareSecurityLevelRejectsSoftwareAndMissing() {
        assertTrue(SecurePrefsManager.isHardwareSecurityLevel("strongbox"))
        assertTrue(SecurePrefsManager.isHardwareSecurityLevel("tee"))
        assertTrue(SecurePrefsManager.isHardwareSecurityLevel("hardware"))
        assertFalse(SecurePrefsManager.isHardwareSecurityLevel("software"))
        assertFalse(SecurePrefsManager.isHardwareSecurityLevel("missing"))
    }

    @Test
    fun corruptedEncryptedFileFailsClosedWithoutOpeningSession() = runBlocking {
        val file = File(tempFolder.newFolder("corrupt"), "mailx_secure_store.bin")
        val aead = newAead()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val prefs = openPrefs(file, aead, scope1)
            prefs.setSessionSecretsAndPersist("ak_123", "tok_secret", "user@example.com")
        } finally {
            scope1.cancel()
        }

        val tamperedFile = File(tempFolder.newFolder("corrupt_reopen"), "mailx_secure_store.bin")
        val raw = file.readBytes()
        assertTrue(raw.size > 16)
        raw[raw.lastIndex] = (raw[raw.lastIndex].toInt() xor 0xFF).toByte()
        tamperedFile.writeBytes(raw)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = openPrefs(tamperedFile, aead, scope2)
            try {
                reopened.readDurableSnapshot()
                fail("Expected CorruptionException for tampered ciphertext")
            } catch (_: CorruptionException) {
            }
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun wrongKeyFailsDecryption() = runBlocking {
        val file = File(tempFolder.newFolder("wrong_key"), "mailx_secure_store.bin")
        val keyA = newAead()
        val keyB = newAead()

        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val prefs = openPrefs(file, keyA, scope1)
            prefs.setSessionSecretsAndPersist("ak_a", "tok_a", "a@example.com")
        } finally {
            scope1.cancel()
        }

        val wrongKeyFile = File(tempFolder.newFolder("wrong_key_reopen"), "mailx_secure_store.bin")
        file.copyTo(wrongKeyFile, overwrite = true)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val wrongPrefs = openPrefs(wrongKeyFile, keyB, scope2)
            try {
                wrongPrefs.readDurableSnapshot()
                fail("Expected CorruptionException when decrypting with a different key")
            } catch (_: CorruptionException) {
            }
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun concurrentWritesPreserveAllKeysWithoutLostUpdates() = runBlocking {
        val file = File(tempFolder.newFolder("concurrent"), "mailx_secure_store.bin")
        val aead = newAead()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val prefs = openPrefs(file, aead, scope)
            (1..20).map { idx ->
                async(Dispatchers.IO) {
                    prefs.writeValuesAndPersist(mapOf("k_$idx" to "v_$idx"))
                }
            }.awaitAll()

            val snap = prefs.readDurableSnapshot()
            for (idx in 1..20) {
                assertEquals("v_$idx", snap.optString("k_$idx"))
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun logoutPermanentlyClearsSessionSecretsWhileKeepingThemeSettings() = runBlocking {
        val file = File(tempFolder.newFolder("logout"), "mailx_secure_store.bin")
        val aead = newAead()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val prefs = openPrefs(file, aead, scope1)
            prefs.writeValuesAndPersist(
                mapOf(
                    "dark_mode_v3_mode" to 0,
                    "amoled_dark_v3_enabled" to true,
                    "cached_aliases" to """[{"id":"1","name":"a@mailx.net"}]""",
                    "alias_search_enabled" to true
                )
            )
            prefs.setSessionSecretsAndPersist("ak_live", "tok_live", "me@example.com")
            prefs.clearSessionAndPersist()
        } finally {
            scope1.cancel()
        }

        val reopenedFile = File(tempFolder.newFolder("reopen_${System.nanoTime()}"), "mailx_secure_store.bin")
        file.copyTo(reopenedFile, overwrite = true)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = openPrefs(reopenedFile, aead, scope2)
            val snap = reopened.readDurableSnapshot()
            assertEquals("", snap.optString("bearer_token", ""))
            assertEquals("", snap.optString("access_key", ""))
            assertEquals("", snap.optString("account_email", ""))
            assertEquals("", snap.optString("cached_aliases", ""))
            assertFalse(snap.optBoolean("alias_search_enabled", false))
            assertEquals(0, snap.optInt("dark_mode_v3_mode", -1))
            assertTrue(snap.optBoolean("amoled_dark_v3_enabled", false))
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun backupRestoreIsAtomicAndExcludesBiometricsAndSearchOptIn() = runBlocking {
        val file = File(tempFolder.newFolder("backup"), "mailx_secure_store.bin")
        val aead = newAead()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val prefs = openPrefs(file, aead, scope)
            prefs.setSessionSecretsAndPersist("ak_orig", "tok_orig", "orig@example.com")
            prefs.writeValuesAndPersist(
                mapOf(
                    "base_url" to "https://api.mailx.net",
                    "default_domain" to "zeropost.net",
                    "biometric_enabled" to true,
                    "alias_search_enabled" to true,
                    "block_trackers" to true
                )
            )

            val exported = MailxBackupRepository.export(prefs, includeSession = false)
            assertFalse(exported.has("session"))

            val invalidPayload = JSONObject(exported.toString()).apply {
                getJSONObject("settings").put("baseUrl", "http://insecure.example")
            }
            try {
                MailxBackupRepository.restore(prefs, invalidPayload, restoreSession = false)
                fail("Expected IllegalArgumentException for non-HTTPS baseUrl")
            } catch (_: IllegalArgumentException) {
            }

            MailxBackupRepository.restore(prefs, exported, restoreSession = false)
            val snap = prefs.readDurableSnapshot()
            assertEquals("zeropost.net", snap.optString("default_domain"))
            assertTrue(snap.optBoolean("block_trackers", false))
            assertFalse(snap.optBoolean("biometric_enabled", true))
            assertFalse(snap.optBoolean("alias_search_enabled", true))
            assertEquals("tok_orig", snap.optString("bearer_token"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun persistedSettingsReloadAfterProcessRestart() = runBlocking {
        val file = File(tempFolder.newFolder("restart"), "mailx_secure_store.bin")
        val aead = newAead()

        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val prefs = openPrefs(file, aead, scope1)
            prefs.setSessionSecretsAndPersist("ak_persist", "tok_persist", "persist@example.com")
            prefs.writeValuesAndPersist(mapOf("default_domain" to "layermail.net"))
            assertTrue(prefs.setAliasWatchedDurable("alias-1", true))
        } finally {
            scope1.cancel()
        }

        val reopenedFile = File(tempFolder.newFolder("reopen_${System.nanoTime()}"), "mailx_secure_store.bin")
        file.copyTo(reopenedFile, overwrite = true)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = openPrefs(reopenedFile, aead, scope2)
            val emitted = reopened.dataFlow.first()
            assertEquals("tok_persist", emitted.optString("bearer_token"))
            assertEquals("layermail.net", emitted.optString("default_domain"))
            assertEquals(setOf("alias-1"), reopened.getWatchedAliasIdsDurable())
        } finally {
            scope2.cancel()
        }
    }
}
