package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Privacy-safe launcher widget: never displays stale or invented health data. */
class MangoHomeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val launch = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_2)
            views.setTextViewText(android.R.id.text1, "🥭 FITMango • Mój dzień")
            views.setTextViewText(android.R.id.text2, "Dotknij, aby otworzyć posiłki, wodę i trening")
            views.setOnClickPendingIntent(android.R.id.text1, pending)
            views.setOnClickPendingIntent(android.R.id.text2, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
