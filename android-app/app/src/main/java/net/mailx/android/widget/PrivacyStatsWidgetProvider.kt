package net.mailx.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.mailx.android.R
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.core.SecurePrefsManager
import net.mailx.android.ui.MainActivity
import org.json.JSONArray

class PrivacyStatsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        widgetScope.launch {
            try {
                updateWidgetsDurable(appContext, appWidgetManager, appWidgetIds)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Keep existing widget views if encrypted storage is temporarily unavailable.
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_STATS) {
            refreshFromServer(context)
        }
    }

    private fun refreshFromServer(context: Context) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())

        widgetScope.launch {
            try {
                val prefs = SecurePrefsManager(appContext)
                val stored = prefs.readDurableSnapshot()
                val baseUrl = normalizeApiBaseUrl(stored.optString("base_url", ""))
                val accessKey = stored.optString("access_key", "").trim()
                val bearerToken = stored.optString("bearer_token", "").trim()

                if (bearerToken.isEmpty()) {
                    mainHandler.post {
                        Toast.makeText(appContext, "Sign in to Mailx first", Toast.LENGTH_SHORT)
                            .show()
                    }
                    return@launch
                }

                val res = MailxRustBridge.fetchAliases(
                    baseUrl = baseUrl,
                    accessKey = accessKey,
                    bearerToken = bearerToken,
                    status = "active_inactive",
                    page = 1
                )

                val updates = mutableMapOf<String, Any?>()
                val refreshed = res.optString("refreshed_token", "").trim()
                if (refreshed.isNotEmpty() && refreshed != bearerToken) {
                    updates["bearer_token"] = refreshed
                }

                if (res.optBoolean("ok", false)) {
                    val data = res.optJSONObject("data")
                    val arr = data?.optJSONArray("aliases")
                    if (arr != null) {
                        updates["cached_aliases"] = arr.toString()
                    }
                    if (updates.isNotEmpty()) {
                        prefs.writeValuesAndPersist(updates)
                    }
                    mainHandler.post {
                        MailxWidgetUpdater.updateAll(appContext)
                        Toast.makeText(appContext, "Mailx stats updated", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (updates.isNotEmpty()) {
                        prefs.writeValuesAndPersist(updates)
                    }
                    val err = res.optString("error", "Failed to refresh stats")
                    mainHandler.post {
                        Toast.makeText(appContext, err, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                mainHandler.post {
                    Toast.makeText(
                        appContext,
                        "Could not refresh stats. Check Mailx storage and connection.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH_STATS = "net.mailx.android.widget.ACTION_REFRESH_STATS"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private fun normalizeApiBaseUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            return when (trimmed.lowercase()) {
                "", "https://mailx.net", "https://www.mailx.net", "https://app.mailx.net" ->
                    SecurePrefsManager.DEFAULT_API_BASE_URL
                else -> trimmed
            }
        }

        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val appContext = context.applicationContext
            widgetScope.launch {
                try {
                    updateWidgetsDurable(appContext, appWidgetManager, appWidgetIds)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    // Keep existing widget views if encrypted storage is temporarily unavailable.
                }
            }
        }

        internal suspend fun updateWidgetsDurable(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val prefs = SecurePrefsManager(context)
            val stored = prefs.readDurableSnapshot()
            val configured = stored.optString("bearer_token", "").trim().isNotEmpty()
            val defaultDomain =
                stored.optString("default_domain", "").trim().ifEmpty { "mailx.net" }
            val cachedAliasesJson = stored.optString("cached_aliases", "")

            var totalAliases = 0
            var activeAliases = 0
            var forwards = 0
            var blocks = 0
            var replies = 0
            var sends = 0

            if (configured) {
                runCatching {
                    val arr = JSONArray(cachedAliasesJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val deletedRaw = obj.opt("deleted_at")
                        val isDeleted = deletedRaw != null &&
                            deletedRaw.toString() != "null" &&
                            deletedRaw.toString().isNotBlank()
                        if (!isDeleted) {
                            totalAliases++
                            if (obj.optBoolean("enabled", true)) activeAliases++
                        }
                        val stats = obj.optJSONObject("stats")
                        if (stats != null) {
                            forwards += stats.optInt("forwards", 0)
                            blocks += stats.optInt("blocks", 0)
                            replies += stats.optInt("replies", 0)
                            sends += stats.optInt("sends", 0)
                        }
                    }
                }
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                200,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val refreshIntent = Intent(context, PrivacyStatsWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_STATS
                component = ComponentName(context, PrivacyStatsWidgetProvider::class.java)
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                201,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_privacy_stats)
                views.setTextViewText(
                    R.id.widget_stats_domain,
                    if (configured) "@$defaultDomain" else "Mailx"
                )
                views.setTextViewText(
                    R.id.widget_stats_aliases_summary,
                    if (configured) {
                        "$activeAliases active of $totalAliases aliases"
                    } else {
                        "Tap to connect account"
                    }
                )
                views.setTextViewText(R.id.widget_stats_forwards, forwards.toString())
                views.setTextViewText(R.id.widget_stats_blocks, blocks.toString())
                views.setTextViewText(R.id.widget_stats_replies, replies.toString())
                views.setTextViewText(R.id.widget_stats_sends, sends.toString())
                views.setOnClickPendingIntent(R.id.widget_stats_root, openPending)
                views.setOnClickPendingIntent(R.id.widget_stats_refresh_btn, refreshPending)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
