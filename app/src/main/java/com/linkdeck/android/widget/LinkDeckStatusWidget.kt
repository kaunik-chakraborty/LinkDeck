package com.linkdeck.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.linkdeck.android.MainActivity
import com.linkdeck.android.R
import com.linkdeck.android.core.settings.AppSettingsStore
import com.linkdeck.android.ui.testlink.TestLinkActivity

/**
 * Compact AppWidget showing LinkDeck status and direct diagnostic shortcuts.
 */
class LinkDeckStatusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_status)
            val settingsStore = AppSettingsStore(context)

            val protectionText = if (settingsStore.isTrackingCleanerEnabled) {
                "Tracking Protection Active"
            } else {
                "Protection Inactive"
            }
            views.setTextViewText(R.id.widgetStatusSubtitle, protectionText)

            // Test link intent
            val testIntent = Intent(context, TestLinkActivity::class.java)
            val testPending = PendingIntent.getActivity(
                context,
                3001,
                testIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetBtnQuickTest, testPending)

            // Open app intent
            val appIntent = Intent(context, MainActivity::class.java)
            val appPending = PendingIntent.getActivity(
                context,
                3002,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetBtnOpenApp, appPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
