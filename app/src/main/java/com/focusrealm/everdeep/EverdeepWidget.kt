package com.focusrealm.everdeep

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

class EverdeepWidget : AppWidgetProvider() {

    companion object {
        fun update(ctx: Context, line1: String, line2: String) {
            val views = RemoteViews(ctx.packageName, R.layout.widget)
            views.setTextViewText(R.id.widgetTitle, line1)
            views.setTextViewText(R.id.widgetStatus, line2)
            views.setOnClickPendingIntent(R.id.widgetStatus, android.app.PendingIntent.getActivity(
                ctx, 0, android.content.Intent(ctx, MainActivity::class.java),
                android.app.PendingIntent.FLAG_IMMUTABLE))
            try {
                AppWidgetManager.getInstance(ctx).updateAppWidget(
                    ComponentName(ctx, EverdeepWidget::class.java), views)
            } catch (_: Exception) {}
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, "Everdeep", "the hero waits below")
    }
}
