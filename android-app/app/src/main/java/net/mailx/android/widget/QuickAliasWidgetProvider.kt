package net.mailx.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ClipData
import android.content.ClipboardManager
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
import org.json.JSONObject

class QuickAliasWidgetProvider : AppWidgetProvider() {

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
        val action = intent.action ?: return
        if (action != ACTION_CREATE_ALIAS && action != ACTION_COPY_LAST) return
        val activeIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, QuickAliasWidgetProvider::class.java))
        val targetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (activeIds.isEmpty() || targetId !in activeIds) return
        when (action) {
            ACTION_CREATE_ALIAS -> handleCreateAlias(context)
            ACTION_COPY_LAST -> handleCopyLast(context)
        }
    }

    private fun handleCopyLast(context: Context) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())

        widgetScope.launch {
            try {
                val prefs = SecurePrefsManager(appContext)
                val stored = prefs.readDurableSnapshot()
                val configured = stored.optString("bearer_token", "").trim().isNotEmpty()
                val lastAlias = if (configured) loadLastAlias(appContext, stored) else ""
                mainHandler.post {
                    if (!configured) {
                        Toast.makeText(
                            appContext,
                            "Please sign in to Mailx first",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (lastAlias.isBlank()) {
                        Toast.makeText(appContext, "Create an alias first", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        val cb =
                            appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Mailx Alias", lastAlias))
                        Toast.makeText(appContext, "Copied: $lastAlias", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                mainHandler.post {
                    Toast.makeText(
                        appContext,
                        "Could not read Mailx storage",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleCreateAlias(context: Context) {
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
                val defaultDomain =
                    stored.optString("default_domain", "").trim().ifEmpty { "mailx.net" }
                val defaultRecipient = stored.optString("default_recipient", "").trim()
                val defaultFormat =
                    stored.optString("default_format", "").trim().ifEmpty { "words" }
                val defaultFromName = stored.optString("default_from_name", "").trim()
                val cachedAliasesJson = stored.optString("cached_aliases", "")

                if (bearerToken.isEmpty()) {
                    mainHandler.post {
                        Toast.makeText(
                            appContext,
                            "Please sign in to Mailx first",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val reqJson = JSONObject().apply {
                    put("description", "Widget (${System.currentTimeMillis() / 1000})")
                    put("enabled", true)
                    put("recipients", defaultRecipient)
                    put("from_name", defaultFromName)
                    put("format", defaultFormat)
                    put("domain", defaultDomain)
                }.toString()

                val response = MailxRustBridge.createAlias(
                    baseUrl = baseUrl,
                    accessKey = accessKey,
                    bearerToken = bearerToken,
                    requestJson = reqJson
                )

                val updates = mutableMapOf<String, Any?>()
                val refreshed = response.optString("refreshed_token", "").trim()
                if (refreshed.isNotEmpty() && refreshed != bearerToken) {
                    updates["bearer_token"] = refreshed
                }

                if (response.optBoolean("ok", false)) {
                    val data = response.optJSONObject("data")
                    val aliasObj = data?.optJSONObject("alias") ?: data
                    val aliasName = aliasObj?.optString("name", "").orEmpty()
                    if (aliasObj != null && aliasName.isNotBlank()) {
                        val currentArr = runCatching { JSONArray(cachedAliasesJson) }
                            .getOrDefault(JSONArray())
                        val nextArr = JSONArray().apply {
                            put(aliasObj)
                            for (i in 0 until currentArr.length()) {
                                put(currentArr.get(i))
                            }
                        }
                        updates["cached_aliases"] = nextArr.toString()
                    }
                    if (updates.isNotEmpty()) {
                        prefs.writeValuesAndPersist(updates)
                    }
                    if (aliasName.isNotBlank()) {
                        saveLastAlias(appContext, aliasName)
                        mainHandler.post {
                            val cb =
                                appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Mailx Alias", aliasName))
                            Toast.makeText(
                                appContext,
                                "Created & copied: $aliasName",
                                Toast.LENGTH_SHORT
                            ).show()
                            MailxWidgetUpdater.updateAll(appContext)
                        }
                    }
                } else {
                    if (updates.isNotEmpty()) {
                        prefs.writeValuesAndPersist(updates)
                    }
                    val err = response.optString("error", "Failed to create alias")
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
                        "Could not create alias. Check Mailx storage and connection.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_CREATE_ALIAS = "net.mailx.android.widget.ACTION_CREATE_ALIAS"
        private const val ACTION_COPY_LAST = "net.mailx.android.widget.ACTION_COPY_LAST"
        private const val WIDGET_PREFS = "mailx_widget_state"
        private const val KEY_LAST_ALIAS = "last_created_alias"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private fun normalizeApiBaseUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            return when (trimmed.lowercase()) {
                "", "https://mailx.net", "https://www.mailx.net", "https://app.mailx.net" ->
                    SecurePrefsManager.DEFAULT_API_BASE_URL
                else -> trimmed
            }
        }

        private fun saveLastAlias(context: Context, alias: String) {
            context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_ALIAS, alias)
                .apply()
        }

        private fun loadLastAlias(context: Context, stored: JSONObject): String {
            val saved = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_ALIAS, "")
                .orEmpty()
            if (saved.isNotBlank()) return saved
            return runCatching {
                val arr = JSONArray(stored.optString("cached_aliases", ""))
                if (arr.length() > 0) arr.optJSONObject(0)?.optString("name", "").orEmpty() else ""
            }.getOrDefault("")
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
            val lastAlias = if (configured) loadLastAlias(context, stored) else ""

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                100,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val primaryWidgetId = appWidgetIds.firstOrNull() ?: AppWidgetManager.INVALID_APPWIDGET_ID
            val createIntent = Intent(context, QuickAliasWidgetProvider::class.java).apply {
                action = ACTION_CREATE_ALIAS
                component = ComponentName(context, QuickAliasWidgetProvider::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, primaryWidgetId)
            }
            val createPending = PendingIntent.getBroadcast(
                context,
                101,
                createIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val copyIntent = Intent(context, QuickAliasWidgetProvider::class.java).apply {
                action = ACTION_COPY_LAST
                component = ComponentName(context, QuickAliasWidgetProvider::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, primaryWidgetId)
            }
            val copyPending = PendingIntent.getBroadcast(
                context,
                102,
                copyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_quick_alias)
                views.setTextViewText(
                    R.id.widget_quick_title,
                    if (configured) "@$defaultDomain" else "Mailx"
                )
                views.setTextViewText(
                    R.id.widget_quick_subtitle,
                    when {
                        !configured -> "Tap to sign in"
                        lastAlias.isNotBlank() -> lastAlias
                        else -> "Tap + to create & copy"
                    }
                )
                views.setOnClickPendingIntent(R.id.widget_quick_root, openPending)
                views.setOnClickPendingIntent(R.id.widget_quick_create_btn, createPending)
                views.setOnClickPendingIntent(R.id.widget_quick_copy_btn, copyPending)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
