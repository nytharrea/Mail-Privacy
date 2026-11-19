package net.mailx.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.mailx.android.R
import net.mailx.android.core.EncryptedPrefsManager
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Periodically checks watched aliases for new forwarded messages.
 *
 * WorkManager enforces Android background execution limits. The interval is
 * intentionally the platform minimum (15 minutes); the OS may defer execution.
 */
class AliasWatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = runCatching { EncryptedPrefsManager(applicationContext) }
            .getOrElse { return Result.retry() }

        if (!prefs.isConfigured() || !prefs.aliasWatchEnabled) return Result.success()

        val watched = runCatching { JSONArray(prefs.watchedAliasIdsJson) }
            .getOrElse { JSONArray() }
        if (watched.length() == 0) return Result.success()

        val ids = buildList {
            for (i in 0 until watched.length()) {
                watched.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct().take(MAX_WATCHED_ALIASES)
        if (ids.isEmpty()) return Result.success()

        // Fetch all relevant pages. Do not assume watched aliases are on page one.
        val current = JSONArray()
        val seen = mutableSetOf<String>()
        for (page in 1..100) {
            val response = runCatching {
                MailxRustBridge.fetchAliases(
                    baseUrl = prefs.baseUrl,
                    accessKey = prefs.accessKey,
                    bearerToken = prefs.bearerToken,
                    status = "active_inactive",
                    page = page
                )
            }.getOrElse { return Result.retry() }
            prefs.syncRefreshedTokenIfPresent(response)
            if (!response.optBoolean("ok", false)) {
                val code = response.optInt("error_code", 0)
                return if (code == 401 || code == 403) Result.failure() else Result.retry()
            }
            val batch = response.optJSONObject("data")?.optJSONArray("aliases")
                ?: return Result.retry()
            if (batch.length() == 0) break
            var added = 0
            for (i in 0 until batch.length()) {
                val alias = batch.optJSONObject(i) ?: continue
                val id = alias.optString("id")
                if (id.isNotBlank() && seen.add(id)) {
                    if (id in ids) current.put(alias)
                    added++
                }
            }
            if (ids.all { it in seen } || added == 0) break
            if (page == 100) return Result.retry() // Incomplete pagination; keep old baseline.
        }
        val previous = runCatching { JSONArray(prefs.previousWatchedAliasesJson) }
            .getOrElse { JSONArray() }
        val watchedJson = JSONArray().apply { ids.forEach { put(it) } }

        val result = runCatching {
            JSONObject(
                MailxRustBridge.nativeDetectAliasWatchEvents(
                    previous.toString(),
                    current.toString(),
                    watchedJson.toString()
                )
            )
        }.getOrElse { return Result.retry() }

        if (result.optBoolean("ok", false)) {
            val events = result.optJSONArray("data") ?: JSONArray()
            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                postAliasNotification(
                    event.optString("alias_id"),
                    event.optString("alias_email"),
                    event.optLong("new_forwards").toInt()
                )
            }
            // Store only watched aliases, reducing encrypted preference size.
            val nextBaseline = JSONArray()
            for (i in 0 until previous.length()) {
                val old = previous.optJSONObject(i) ?: continue
                if (old.optString("id") in ids && old.optString("id") !in seen) nextBaseline.put(old)
            }
            for (i in 0 until current.length()) {
                val alias = current.optJSONObject(i) ?: continue
                if (ids.contains(alias.optString("id"))) nextBaseline.put(alias)
            }
            prefs.previousWatchedAliasesJson = nextBaseline.toString()
            return Result.success()
        }

        return Result.retry()
    }

    private fun postAliasNotification(aliasId: String, email: String, count: Int) {
        if (aliasId.isBlank() || count <= 0) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Alias activity",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications when watched aliases receive forwarded messages"
                }
            )
        }

        val intent = MainActivity::class.java.let {
            android.content.Intent(applicationContext, it).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_WATCHED_ALIAS_ID, aliasId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            aliasId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (email.isBlank()) {
            "$count new forwarded message(s)"
        } else {
            "$count new forwarded message(s) for $email"
        }
        val stopIntent = android.content.Intent(
            applicationContext,
            StopAliasWatchReceiver::class.java
        ).apply {
            action = StopAliasWatchReceiver.ACTION_STOP_WATCHING
            putExtra(StopAliasWatchReceiver.EXTRA_ALIAS_ID, aliasId)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            aliasId.hashCode() xor 0x4D41,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .addAction(0, "Stop watching", stopPendingIntent)
            .setSmallIcon(R.drawable.ic_mailx_web_mark)
            .setContentTitle("Watched alias activity")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { manager.notify(aliasId.hashCode(), notification) }
    }

    companion object {
        const val EXTRA_WATCHED_ALIAS_ID = "watched_alias_id"
        private const val CHANNEL_ID = "mailx_alias_watch"
        private const val UNIQUE_WORK = "mailx_alias_watch"
        private const val MAX_WATCHED_ALIASES = 25

        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                work.cancelUniqueWork(UNIQUE_WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<AliasWatchWorker>(
                15, TimeUnit.MINUTES
            ).build()
            work.enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
