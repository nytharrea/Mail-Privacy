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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.mailx.android.R
import net.mailx.android.core.EncryptedPrefsManager
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.MainActivity
import org.json.JSONArray
import org.json.JSONObject
class QuickAliasWidgetProvider : AppWidgetProvider() {
override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
updateWidgets(context, appWidgetManager, appWidgetIds)
}
override fun onReceive(context: Context, intent: Intent) {
super.onReceive(context, intent)
when (intent.action) {
ACTION_CREATE_ALIAS -> handleCreateAlias(context)
ACTION_COPY_LAST -> handleCopyLast(context)
}
}
private fun handleCopyLast(context: Context) {
val prefs = EncryptedPrefsManager(context)
val lastAlias = loadLastAlias(context, prefs)
if (lastAlias.isBlank()) {
Toast.makeText(context, "Create an alias first", Toast.LENGTH_SHORT).show()
return
}
val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
cb.setPrimaryClip(ClipData.newPlainText("Mailx Alias", lastAlias))
Toast.makeText(context, "Copied: $lastAlias", Toast.LENGTH_SHORT).show()
}
private fun handleCreateAlias(context: Context) {
val prefs = EncryptedPrefsManager(context)
if (!prefs.isConfigured()) {
Toast.makeText(context, "Please sign in to Mailx first", Toast.LENGTH_SHORT).show()
return
}
val pendingResult = goAsync()
val appContext = context.applicationContext
val mainHandler = Handler(Looper.getMainLooper())
CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
try {
val reqJson = JSONObject().apply {
put("description", "Widget (${System.currentTimeMillis() / 1000})")
put("enabled", true)
put("recipients", prefs.defaultRecipient)
put("from_name", prefs.defaultFromName)
put("format", prefs.defaultFormat)
put("domain", prefs.defaultDomain)
}.toString()
val response = MailxRustBridge.createAlias(baseUrl = prefs.baseUrl, accessKey = prefs.accessKey, bearerToken = prefs.bearerToken, requestJson = reqJson)
val refreshed = response.optString("refreshed_token", "")
if (refreshed.isNotBlank()) {
prefs.bearerToken = refreshed
}
if (response.optBoolean("ok", false)) {
val data = response.optJSONObject("data")
val aliasObj = data?.optJSONObject("alias") ?: data
val aliasName = aliasObj?.optString("name", "").orEmpty()
if (aliasName.isNotBlank()) {
saveLastAlias(appContext, aliasName)
if (aliasObj != null) {
val currentArr = runCatching { JSONArray(prefs.cachedAliasesJson) }.getOrDefault(JSONArray())
val nextArr = JSONArray().apply {
put(aliasObj)
for (i in 0 until currentArr.length()) {
put(currentArr.get(i))
}
}
prefs.cachedAliasesJson = nextArr.toString()
}
mainHandler.post {
val cb = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
cb.setPrimaryClip(ClipData.newPlainText("Mailx Alias", aliasName))
Toast.makeText(appContext, "Created & copied: $aliasName", Toast.LENGTH_SHORT).show()
MailxWidgetUpdater.updateAll(appContext)
}
}
} else {
val err = response.optString("error", "Failed to create alias")
mainHandler.post {
Toast.makeText(appContext, err, Toast.LENGTH_SHORT).show()
}
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
private fun saveLastAlias(context: Context, alias: String) {
context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_ALIAS, alias).apply()
}
private fun loadLastAlias(context: Context, prefs: EncryptedPrefsManager): String {
val saved = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_ALIAS, "").orEmpty()
if (saved.isNotBlank()) return saved
return runCatching {
val arr = JSONArray(prefs.cachedAliasesJson)
if (arr.length() > 0) arr.optJSONObject(0)?.optString("name", "").orEmpty() else ""
}.getOrDefault("")
}
fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
val prefs = EncryptedPrefsManager(context)
val configured = prefs.isConfigured()
val lastAlias = if (configured) loadLastAlias(context, prefs) else ""
val openIntent = Intent(context, MainActivity::class.java).apply {
flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
val openPending = PendingIntent.getActivity(context, 100, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
val createIntent = Intent(context, QuickAliasWidgetProvider::class.java).apply {
action = ACTION_CREATE_ALIAS
component = ComponentName(context, QuickAliasWidgetProvider::class.java)
}
val createPending = PendingIntent.getBroadcast(context, 101, createIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
val copyIntent = Intent(context, QuickAliasWidgetProvider::class.java).apply {
action = ACTION_COPY_LAST
component = ComponentName(context, QuickAliasWidgetProvider::class.java)
}
val copyPending = PendingIntent.getBroadcast(context, 102, copyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
for (appWidgetId in appWidgetIds) {
val views = RemoteViews(context.packageName, R.layout.widget_quick_alias)
views.setTextViewText(R.id.widget_quick_title, if (configured) "@${prefs.defaultDomain}" else "Mailx Privacy")
views.setTextViewText(R.id.widget_quick_subtitle, when {
!configured -> "Tap to sign in"
lastAlias.isNotBlank() -> lastAlias
else -> "Tap + to create & copy"
})
views.setOnClickPendingIntent(R.id.widget_quick_root, openPending)
views.setOnClickPendingIntent(R.id.widget_quick_create_btn, createPending)
views.setOnClickPendingIntent(R.id.widget_quick_copy_btn, copyPending)
appWidgetManager.updateAppWidget(appWidgetId, views)
}
}
}
}
