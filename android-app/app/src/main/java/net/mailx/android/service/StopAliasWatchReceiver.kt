package net.mailx.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Notification action that removes an alias from the local watch list. */
class StopAliasWatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val aliasId = intent.getStringExtra(EXTRA_ALIAS_ID)?.takeIf { it.isNotBlank() } ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AliasWatchManager(context).setWatched(aliasId, false)
                NotificationManagerCompat.from(context).cancel(aliasId.hashCode())
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP_WATCHING = "net.mailx.android.action.STOP_ALIAS_WATCH"
        const val EXTRA_ALIAS_ID = "alias_id"
    }
}
