package com.linkdeck.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.linkdeck.android.R

/**
 * AppWidget for fast link deck routing:
 * - 1-tap clipboard paste & open
 * - 1-tap clipboard save to quick links
 * - Custom user-managed quick link deck slots
 * - Dedicated Widget Quick Links Settings launcher
 */
class LinkDeckMultiLinkWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_multilink)

            // 1. Settings / Configure button pending intent
            val settingsIntent = Intent(context, WidgetSettingsActivity::class.java)
            val settingsPending = PendingIntent.getActivity(
                context,
                1001,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetBtnSettings, settingsPending)
            views.setOnClickPendingIntent(R.id.widgetBtnManage, settingsPending)
            views.setOnClickPendingIntent(R.id.widgetQuickLinksHeader, settingsPending)
            views.setOnClickPendingIntent(R.id.widgetEmptyLinksLayout, settingsPending)

            // 2. Paste & Open link pending intent
            val pasteOpenIntent = Intent(context, WidgetActionActivity::class.java).apply {
                action = WidgetActionActivity.ACTION_PASTE_AND_OPEN
            }
            val pasteOpenPending = PendingIntent.getActivity(
                context,
                1002,
                pasteOpenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetBtnPasteOpen, pasteOpenPending)

            // 3. Paste & Save link pending intent
            val pasteSaveIntent = Intent(context, WidgetActionActivity::class.java).apply {
                action = WidgetActionActivity.ACTION_PASTE_AND_SAVE
            }
            val pasteSavePending = PendingIntent.getActivity(
                context,
                1003,
                pasteSaveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetBtnPasteSave, pasteSavePending)

            // 4. Populate User's Custom Quick Links
            val quickLinksStore = WidgetQuickLinksStore(context)
            val savedLinks = quickLinksStore.getQuickLinks()

            if (savedLinks.isEmpty()) {
                views.setViewVisibility(R.id.widgetEmptyLinksLayout, View.VISIBLE)
                views.setViewVisibility(R.id.widgetSlot1, View.GONE)
                views.setViewVisibility(R.id.widgetSlot2, View.GONE)
                views.setViewVisibility(R.id.widgetSlot3, View.GONE)
            } else {
                views.setViewVisibility(R.id.widgetEmptyLinksLayout, View.GONE)

                // Slot 1
                if (savedLinks.isNotEmpty()) {
                    views.setViewVisibility(R.id.widgetSlot1, View.VISIBLE)
                    val link1 = savedLinks[0]
                    bindSlot(context, views, R.id.widgetTextLink1, R.id.widgetBtnOpen1, link1.title, link1.url, 2001)
                } else {
                    views.setViewVisibility(R.id.widgetSlot1, View.GONE)
                }

                // Slot 2
                if (savedLinks.size > 1) {
                    views.setViewVisibility(R.id.widgetSlot2, View.VISIBLE)
                    val link2 = savedLinks[1]
                    bindSlot(context, views, R.id.widgetTextLink2, R.id.widgetBtnOpen2, link2.title, link2.url, 2002)
                } else {
                    views.setViewVisibility(R.id.widgetSlot2, View.GONE)
                }

                // Slot 3
                if (savedLinks.size > 2) {
                    views.setViewVisibility(R.id.widgetSlot3, View.VISIBLE)
                    val link3 = savedLinks[2]
                    bindSlot(context, views, R.id.widgetTextLink3, R.id.widgetBtnOpen3, link3.title, link3.url, 2003)
                } else {
                    views.setViewVisibility(R.id.widgetSlot3, View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun bindSlot(
            context: Context,
            views: RemoteViews,
            textResId: Int,
            btnResId: Int,
            label: String,
            url: String,
            requestCode: Int
        ) {
            views.setTextViewText(textResId, label)

            val openIntent = Intent(context, WidgetActionActivity::class.java).apply {
                putExtra(WidgetActionActivity.EXTRA_DIRECT_URL, url)
            }
            val openPending = PendingIntent.getActivity(
                context,
                requestCode,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(btnResId, openPending)
            views.setOnClickPendingIntent(textResId, openPending)
        }
    }
}
