package net.mailx.android.core

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.mailx.android.core.uniffi.SecureStorePort
import net.mailx.android.core.uniffi.SecureStorePortException
import java.nio.charset.StandardCharsets

class AndroidSecureStoreAdapter(
    private val prefs: SecurePrefsManager
) : SecureStorePort {

    override fun get(key: String): ByteArray? {
        return try {
            runBlocking(Dispatchers.IO) {
                val stored = prefs.readDurableSnapshot()
                if (!stored.has(key) || stored.isNull(key)) {
                    null
                } else {
                    stored.optString(key, "").toByteArray(StandardCharsets.UTF_8)
                }
            }
        } catch (_: CorruptionException) {
            throw SecureStorePortException.CorruptData()
        } catch (error: Exception) {
            throw SecureStorePortException.Backend(
                detail = error.message ?: error.javaClass.simpleName
            )
        }
    }

    override fun put(key: String, value: ByteArray) {
        try {
            val encoded = String(value, StandardCharsets.UTF_8)
            runBlocking(Dispatchers.IO) {
                prefs.writeValuesAndPersist(mapOf(key to encoded))
            }
        } catch (_: CorruptionException) {
            throw SecureStorePortException.CorruptData()
        } catch (error: Exception) {
            throw SecureStorePortException.Backend(
                detail = error.message ?: error.javaClass.simpleName
            )
        }
    }

    override fun delete(key: String) {
        try {
            runBlocking(Dispatchers.IO) {
                prefs.writeValuesAndPersist(values = emptyMap(), removals = setOf(key))
            }
        } catch (_: CorruptionException) {
            throw SecureStorePortException.CorruptData()
        } catch (error: Exception) {
            throw SecureStorePortException.Backend(
                detail = error.message ?: error.javaClass.simpleName
            )
        }
    }
}
