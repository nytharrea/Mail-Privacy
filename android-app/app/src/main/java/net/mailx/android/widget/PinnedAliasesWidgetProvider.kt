package net.mailx.android.widget
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.Toast
import net.mailx.android.R
import net.mailx.android.ui.MainActivity
class PinnedAliasesWidgetProvider : AppWidgetProvider() {
override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
updateWidgets(context, appWidgetManager, appWidgetIds)
}
override fun onReceive(context: Context, intent: Intent) {
super.onReceive(context, intent)
when (intent.action) {
ACTION_COPY_ITEM -> {
val email = intent.getStringExtra(EXTRA_ALIAS_EMAIL).orEmpty()
if (email.isNotBlank()) {
val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
cb.setPrimaryClip(ClipData.newPlainText("Mailx Alias", email))
Toast.makeText(context, "Copied: $email", Toast.LENGTH_SHORT).show()
}
}
ACTION_REFRESH_LIST -> {
val manager = AppWidgetManager.getInstance(context)
val ids = manager.getAppWidgetIds(ComponentName(context, PinnedAliasesWidgetProvider::class.java))
manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_pinned_list)
updateWidgets(context, manager, ids)
}
}
}
companion object {
const val ACTION_COPY_ITEM = "net.mailx.android.widget.ACTION_COPY_PINNED_ITEM"
const val EXTRA_ALIAS_EMAIL = "extra_alias_email"
private const val ACTION_REFRESH_LIST = "net.mailx.android.widget.ACTION_REFRESH_PINNED"
fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
val openIntent = Intent(context, MainActivity::class.java).apply {
flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
val openPending = PendingIntent.getActivity(context, 300, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
val refreshIntent = Intent(context, PinnedAliasesWidgetProvider::class.java).apply {
action = ACTION_REFRESH_LIST
component = ComponentName(context, PinnedAliasesWidgetProvider::class.java)
}
val refreshPending = PendingIntent.getBroadcast(context, 301, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
for (appWidgetId in appWidgetIds) {
val views = RemoteViews(context.packageName, R.layout.widget_pinned_aliases)
val svcIntent = Intent(context, PinnedAliasesWidgetService::class.java).apply {
putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
}
@Suppress("DEPRECATION")
views.setRemoteAdapter(R.id.widget_pinned_list, svcIntent)
views.setEmptyView(R.id.widget_pinned_list, R.id.widget_pinned_empty)
val itemClickTemplate = Intent(context, PinnedAliasesWidgetProvider::class.java).apply {
action = ACTION_COPY_ITEM
component = ComponentName(context, PinnedAliasesWidgetProvider::class.java)
}
val itemPending = PendingIntent.getBroadcast(context, 302, itemClickTemplate, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
views.setPendingIntentTemplate(R.id.widget_pinned_list, itemPending)
views.setOnClickPendingIntent(R.id.widget_pinned_header, openPending)
views.setOnClickPendingIntent(R.id.widget_pinned_refresh_btn, refreshPending)
appWidgetManager.updateAppWidget(appWidgetId, views)
}
}
}
}
