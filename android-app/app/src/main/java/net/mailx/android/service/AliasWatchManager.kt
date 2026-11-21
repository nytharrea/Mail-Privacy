package net.mailx.android.service

import android.content.Context
import net.mailx.android.core.SecurePrefsManager

/**
 * Application-facing API for alias watch-list management.
 * The watch list and baseline are stored in encrypted DataStore.
 */
class AliasWatchManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = SecurePrefsManager(appContext)

    fun watchedIds(): Set<String> = prefs.getWatchedAliasIds()

    fun isWatched(aliasId: String): Boolean = aliasId in prefs.getWatchedAliasIds()

    fun setWatched(aliasId: String, watched: Boolean): Boolean {
        val updated = prefs.setAliasWatched(aliasId, watched)
        if (!updated) return false

        val ids = prefs.getWatchedAliasIds()
        prefs.aliasWatchEnabled = ids.isNotEmpty()
        AliasWatchWorker.schedule(appContext, prefs.aliasWatchEnabled)
        if (ids.isEmpty()) prefs.previousWatchedAliasesJson = "[]"
        return true
    }

    fun setEnabled(enabled: Boolean) {
        prefs.aliasWatchEnabled = enabled && prefs.getWatchedAliasIds().isNotEmpty()
        AliasWatchWorker.schedule(appContext, prefs.aliasWatchEnabled)
    }
}
