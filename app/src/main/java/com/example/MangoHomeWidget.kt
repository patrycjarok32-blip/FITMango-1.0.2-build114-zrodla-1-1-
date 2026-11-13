package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Three small offline launcher widgets. No sensitive or stale health values are exposed. */
open class MangoHomeWidget : AppWidgetProvider() {
    protected open val icon = "🥭"
    protected open val title = "🥭 FITMango • Mój dzień"
    protected open val subtitle = "Otwórz podsumowanie dnia"
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.mango_widget_card)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_icon, icon)
            views.setTextViewText(R.id.widget_subtitle, subtitle)
            views.setOnClickPendingIntent(R.id.widget_title, pending)
            views.setOnClickPendingIntent(R.id.widget_icon, pending)
            views.setOnClickPendingIntent(R.id.widget_subtitle, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
class MangoMealsWidget : MangoHomeWidget() {
    override val icon = "🥗"
    override val title = "🥗 FITMango • Posiłki"
    override val subtitle = "Jadłospis i przepisy w aplikacji"
}
class MangoWorkoutWidget : MangoHomeWidget() {
    override val icon = "🏋️"
    override val title = "🏋️ FITMango • Trening"
    override val subtitle = "Otwórz trening i historię"
}

class MangoWaterWidget : MangoHomeWidget() {
    override val icon = "💧"
    override val title = "💧 FITMango • Woda"
    override val subtitle = "Otwórz dziennik nawodnienia"
}
