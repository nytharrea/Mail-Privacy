package net.mailx.android.core

import androidx.datastore.core.DataStoreFactory
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.mailx.android.core.uniffi.SecureStorePortException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

class AndroidSecureStoreAdapterUnitTest {

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

    private fun openAdapter(
        file: File,
        aead: Aead,
        scope: CoroutineScope
    ): AndroidSecureStoreAdapter {
        val store = DataStoreFactory.create(
            serializer = SecurePrefsManager.createSerializer(aead),
            scope = scope,
            produceFile = { file }
        )
        return AndroidSecureStoreAdapter(SecurePrefsManager(store))
    }

    @Test
    fun getReturnsNullForMissingKey() {
        val file = File(tempFolder.newFolder("missing"), "store.bin")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val adapter = openAdapter(file, newAead(), scope)
            assertNull(adapter.get("bearer_token"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun putAndGetRoundTripUtf8BytesDurably() {
        val file = File(tempFolder.newFolder("roundtrip"), "store.bin")
        val aead = newAead()
        val payload = "cookie:session_token_value".toByteArray(StandardCharsets.UTF_8)

        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val adapter = openAdapter(file, aead, scope1)
            adapter.put("bearer_token", payload)
            assertArrayEquals(payload, adapter.get("bearer_token"))
        } finally {
            scope1.cancel()
        }

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = openAdapter(file, aead, scope2)
            assertArrayEquals(payload, reopened.get("bearer_token"))
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun deleteRemovesKeyFromEncryptedStore() {
        val file = File(tempFolder.newFolder("delete"), "store.bin")
        val aead = newAead()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val adapter = openAdapter(file, aead, scope)
            adapter.put("access_key", "key_value".toByteArray(StandardCharsets.UTF_8))
            adapter.delete("access_key")
            assertNull(adapter.get("access_key"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun tamperedStoreMapsCorruptionToCorruptData() {
        val file = File(tempFolder.newFolder("tamper"), "store.bin")
        val aead = newAead()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val adapter = openAdapter(file, aead, scope1)
            adapter.put("bearer_token", "tok_123".toByteArray(StandardCharsets.UTF_8))
        } finally {
            scope1.cancel()
        }

        val bytes = file.readBytes()
        assertTrue(bytes.size > 16)
        bytes[bytes.lastIndex] = (bytes[bytes.lastIndex].toInt() xor 0xFF).toByte()
        file.writeBytes(bytes)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = openAdapter(file, aead, scope2)
            try {
                reopened.get("bearer_token")
                fail("Expected SecureStorePortException.CorruptData")
            } catch (_: SecureStorePortException.CorruptData) {
            }
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun multipleKeysStayIsolatedAcrossReopen() {
        val file = File(tempFolder.newFolder("isolated"), "store.bin")
        val aead = newAead()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val adapter = openAdapter(file, aead, scope1)
            adapter.put("base_url", "https://api.mailx.net".toByteArray(StandardCharsets.UTF_8))
            adapter.put("account_email", "user@example.com".toByteArray(StandardCharsets.UTF_8))
            adapter.delete("account_email")
        } finally {
            scope1.cancel()
        }

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = openAdapter(file, aead, scope2)
            assertArrayEquals(
                "https://api.mailx.net".toByteArray(StandardCharsets.UTF_8),
                reopened.get("base_url")
            )
            assertNull(reopened.get("account_email"))
        } finally {
            scope2.cancel()
        }
    }
}
