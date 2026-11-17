package net.mailx.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import net.mailx.android.R

object MailxWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)

        val quickIds = manager.getAppWidgetIds(
            ComponentName(context, QuickAliasWidgetProvider::class.java)
        )
        if (quickIds.isNotEmpty()) {
            QuickAliasWidgetProvider.updateWidgets(context, manager, quickIds)
        }

        val statsIds = manager.getAppWidgetIds(
            ComponentName(context, PrivacyStatsWidgetProvider::class.java)
        )
        if (statsIds.isNotEmpty()) {
            PrivacyStatsWidgetProvider.updateWidgets(context, manager, statsIds)
        }

        val pinnedIds = manager.getAppWidgetIds(
            ComponentName(context, PinnedAliasesWidgetProvider::class.java)
        )
        if (pinnedIds.isNotEmpty()) {
            manager.notifyAppWidgetViewDataChanged(pinnedIds, R.id.widget_pinned_list)
            PinnedAliasesWidgetProvider.updateWidgets(context, manager, pinnedIds)
        }
    }
}
