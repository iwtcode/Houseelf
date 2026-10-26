package com.example.houseelf.calendar

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import java.time.ZoneId

/** Открыть уже созданное событие в приложении календаря. */
fun calendarViewIntent(eventId: Long): Intent =
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))

/** Запасной путь: форма нового события в календаре, заполненная данными. Разрешения не нужны. */
fun calendarInsertIntent(ev: EventProposal): Intent {
    val begin: Long
    val finish: Long
    if (ev.allDay) {
        begin = utcMidnight(ev.start.toLocalDate())
        finish = utcMidnight(ev.end.toLocalDate())
    } else {
        val zone = ZoneId.systemDefault()
        begin = ev.start.atZone(zone).toInstant().toEpochMilli()
        finish = ev.end.atZone(zone).toInstant().toEpochMilli()
    }
    return Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, finish)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, ev.allDay)
        putExtra(CalendarContract.Events.TITLE, ev.title)
        ev.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        ev.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
    }
}
