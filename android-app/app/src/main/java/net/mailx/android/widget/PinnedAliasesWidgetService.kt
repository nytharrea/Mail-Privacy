package net.mailx.android.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import net.mailx.android.R
import net.mailx.android.core.SecurePrefsManager
import org.json.JSONArray

class PinnedAliasesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return PinnedAliasesRemoteFactory(applicationContext)
    }
}

private data class WidgetAliasRow(
    val name: String,
    val meta: String
)

private class PinnedAliasesRemoteFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<WidgetAliasRow> = emptyList()

    override fun onCreate() {
        loadFromCache()
    }

    override fun onDataSetChanged() {
        loadFromCache()
    }

    private fun loadFromCache() {
        val prefs = SecurePrefsManager(context)
        val parsed = mutableListOf<Triple<Boolean, Int, WidgetAliasRow>>()

        runCatching {
            val arr = JSONArray(prefs.cachedAliasesJson)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val deletedRaw = obj.opt("deleted_at")
                val isDeleted = deletedRaw != null && deletedRaw.toString() != "null" && deletedRaw.toString().isNotBlank()
                if (isDeleted) continue
                val enabled = obj.optBoolean("enabled", true)
                if (!enabled) continue

                val name = obj.optString("name", "")
                if (name.isBlank()) continue
                val pinned = obj.optBoolean("pinned", false)
                val desc = obj.optString("description", "").trim()
                val stats = obj.optJSONObject("stats")
                val fwds = stats?.optInt("forwards", 0) ?: 0
                val blks = stats?.optInt("blocks", 0) ?: 0
                val activity = fwds + blks + (stats?.optInt("replies", 0) ?: 0) + (stats?.optInt("sends", 0) ?: 0)

                val meta = buildString {
                    if (pinned) append("Pinned • ")
                    if (desc.isNotBlank()) {
                        append(desc)
                    } else {
                        append("$fwds fwd • $blks blk")
                    }
                }
                parsed.add(Triple(pinned, activity, WidgetAliasRow(name, meta)))
            }
        }

        rows = parsed
            .sortedWith(compareByDescending<Triple<Boolean, Int, WidgetAliasRow>> { it.first }.thenByDescending { it.second })
            .take(12)
            .map { it.third }
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_pinned_alias_item)
        val views = RemoteViews(context.packageName, R.layout.widget_pinned_alias_item)
        views.setTextViewText(R.id.widget_item_email, row.name)
        views.setTextViewText(R.id.widget_item_meta, row.meta)
        val fillInIntent = Intent().apply {
            putExtra(PinnedAliasesWidgetProvider.EXTRA_ALIAS_EMAIL, row.name)
        }
        views.setOnClickFillInIntent(R.id.widget_pinned_item_root, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
