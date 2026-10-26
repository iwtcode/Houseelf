package com.example.houseelf.event

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.houseelf.R
import com.example.houseelf.calendar.EventAction
import com.example.houseelf.calendar.calendarViewIntent

/**
 * Всё, что Домовой.Событие говорит пользователю: «разбираю текст…» на время работы и итог в конце.
 * Если уведомления выключены, итог показываем всплывающей подписью — молча работать нельзя.
 */
internal class EventNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(PROGRESS_CHANNEL, "Домовой.Событие: работа", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(RESULT_CHANNEL, "Домовой.Событие: результат", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** Уведомление, с которым сервис работает на переднем плане. */
    fun progress(): Notification =
        Notification.Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_event)
            .setContentTitle(TITLE)
            .setContentText("Разбираю текст…")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()

    /** Итог прогона: что добавлено, изменено, удалено и что не удалось. */
    fun showResult(summary: EventBatchSummary) {
        show(
            headline = EventResultText.headline(summary),
            details = EventResultText.details(summary),
            open = firstViewableEvent(summary),
        )
    }

    /** Короткое сообщение без деталей: нет текста, нет доступа к календарю, сбой. */
    fun showMessage(text: String) = show(headline = text, details = emptyList(), open = null)

    private fun show(headline: String, details: List<String>, open: Intent?) {
        if (!manager.areNotificationsEnabled()) {
            Toast.makeText(context, headline, Toast.LENGTH_LONG).show()
            return
        }
        val builder = Notification.Builder(context, RESULT_CHANNEL)
            .setSmallIcon(R.drawable.ic_event)
            .setContentTitle(TITLE)
            .setContentText(details.singleOrNull() ?: headline)
            .setAutoCancel(true)
        if (details.size > 1) builder.setStyle(Notification.BigTextStyle().bigText(details.joinToString("\n")))
        if (open != null) builder.setContentIntent(pendingActivity(open))
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    /** Нажатие на уведомление открывает первое добавленное или изменённое событие. */
    private fun firstViewableEvent(summary: EventBatchSummary): Intent? =
        summary.succeeded
            .firstOrNull { it.event.action != EventAction.Delete && it.event.calendarEventId != null }
            ?.let { calendarViewIntent(it.event.calendarEventId!!) }

    private fun pendingActivity(intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context, 0, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val FOREGROUND_ID = 4201
        private const val TITLE = "Домовой.Событие"
        private const val PROGRESS_CHANNEL = "quick_event_progress"
        private const val RESULT_CHANNEL = "quick_event_result"
    }
}
