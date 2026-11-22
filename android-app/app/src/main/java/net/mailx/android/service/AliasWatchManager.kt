package net.mailx.android.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mailx.android.core.SecurePrefsManager

/**
 * Coroutine-based API for the encrypted alias watch list.
 * All reads and writes are performed against durable DataStore state.
 */
class AliasWatchManager(context: Context) {
    private val appContext = context.applicationContext

    private suspend fun durablePrefs(): SecurePrefsManager = withContext(Dispatchers.IO) {
        SecurePrefsManager(appContext)
    }

    suspend fun watchedIds(): Set<String> = withContext(Dispatchers.IO) {
        durablePrefs().getWatchedAliasIdsDurable()
    }

    suspend fun isWatched(aliasId: String): Boolean = aliasId in watchedIds()

    suspend fun setWatched(aliasId: String, watched: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val prefs = durablePrefs()
            val updated = prefs.setAliasWatchedDurable(aliasId, watched)
            if (updated) {
                val enabled = prefs.getWatchedAliasIdsDurable().isNotEmpty()
                AliasWatchWorker.schedule(appContext, enabled)
            }
            updated
        }

    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val prefs = durablePrefs()
        val shouldEnable = enabled && prefs.getWatchedAliasIdsDurable().isNotEmpty()
        prefs.writeValuesAndPersist(mapOf("alias_watch_enabled" to shouldEnable))
        AliasWatchWorker.schedule(appContext, shouldEnable)
    }
}
