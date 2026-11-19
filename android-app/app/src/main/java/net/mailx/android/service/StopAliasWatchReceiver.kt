package net.mailx.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Notification action that removes an alias from the local watch list. */
class StopAliasWatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val aliasId = intent.getStringExtra(EXTRA_ALIAS_ID)?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            AliasWatchManager(context).setWatched(aliasId, false)
            NotificationManagerCompat.from(context).cancel(aliasId.hashCode())
        }
    }

    companion object {
        const val ACTION_STOP_WATCHING = "net.mailx.android.action.STOP_ALIAS_WATCH"
        const val EXTRA_ALIAS_ID = "alias_id"
    }
}
