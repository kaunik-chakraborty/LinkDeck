package com.linkdeck.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Helper to notify home screen widgets whenever routing rules or settings change.
 */
object WidgetUpdateHelper {

    fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // 1. MultiLink Widget
        val multiLinkComponent = ComponentName(context, LinkDeckMultiLinkWidget::class.java)
        val multiLinkIds = appWidgetManager.getAppWidgetIds(multiLinkComponent)
        if (multiLinkIds.isNotEmpty()) {
            for (id in multiLinkIds) {
                LinkDeckMultiLinkWidget.updateAppWidget(context, appWidgetManager, id)
            }
        }

        // 2. Status Widget
        val statusComponent = ComponentName(context, LinkDeckStatusWidget::class.java)
        val statusIds = appWidgetManager.getAppWidgetIds(statusComponent)
        if (statusIds.isNotEmpty()) {
            for (id in statusIds) {
                LinkDeckStatusWidget.updateAppWidget(context, appWidgetManager, id)
            }
        }
    }
}
