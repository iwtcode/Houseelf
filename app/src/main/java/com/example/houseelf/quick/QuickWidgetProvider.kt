package com.example.houseelf.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.houseelf.R

/**
 * Виджет на рабочий стол: одно нажатие — запуск функции домового.
 * Вид у всех виджетов один ([R.layout.widget_quick]), различаются значок, подпись и то, что запускается.
 */
abstract class QuickWidgetProvider : AppWidgetProvider() {

    protected abstract val iconRes: Int
    protected abstract val textRes: Int

    /** Что открыть по нажатию. */
    protected abstract fun launchIntent(context: Context): Intent

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = PendingIntent.getActivity(
            context, 0, launchIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick).apply {
                setImageViewResource(R.id.widget_icon, iconRes)
                setTextViewText(R.id.widget_text, context.getString(textRes))
                setOnClickPendingIntent(R.id.widget_root, pending)
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
