package com.example.houseelf.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.example.houseelf.today.SubtaskStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Записывает событие в основной календарь телефона. Возвращает номер события. Нужны разрешения на календарь. */
fun insertCalendarEvent(context: Context, ev: EventProposal): Long {
    val resolver = context.contentResolver
    val calendarId = findWritableCalendar(resolver)
        ?: throw IllegalStateException("на телефоне нет календаря, в который можно записывать")

    val values = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, ev.title)
        ev.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        ev.description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
        putEventTimes(this, ev)
    }
    val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
        ?: throw IllegalStateException("календарь не принял событие")
    return ContentUris.parseId(uri)
}

/** Время события в форме, которую ждёт календарь телефона. */
private fun putEventTimes(values: ContentValues, ev: EventProposal) {
    if (ev.allDay) {
        // Событие «на весь день»: границы — полночь по UTC, конец исключительный
        values.put(CalendarContract.Events.ALL_DAY, 1)
        values.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        values.put(CalendarContract.Events.DTSTART, utcMidnight(ev.start.toLocalDate()))
        values.put(CalendarContract.Events.DTEND, utcMidnight(ev.end.toLocalDate().plusDays(1)))
    } else {
        val zone = ZoneId.systemDefault()
        values.put(CalendarContract.Events.ALL_DAY, 0)
        values.put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        values.put(CalendarContract.Events.DTSTART, ev.start.atZone(zone).toInstant().toEpochMilli())
        values.put(CalendarContract.Events.DTEND, ev.end.atZone(zone).toInstant().toEpochMilli())
    }
}

private fun ContentValues.putTextOrNull(key: String, value: String?) {
    if (value == null) putNull(key) else put(key, value)
}

internal fun utcMidnight(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Основной календарь аккаунта, а если такого нет — любой видимый календарь с правом записи. */
private fun findWritableCalendar(resolver: ContentResolver): Long? {
    val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}" +
        " AND ${CalendarContract.Calendars.VISIBLE} = 1"
    val order = "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"
    resolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID),
        selection, null, order,
    )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }
    return null
}

/** Выполняет действие модели: добавляет, меняет или удаляет событие. Возвращает номер события. */
fun applyCalendarAction(context: Context, ev: EventProposal): Long = when (ev.action) {
    EventAction.Add -> insertCalendarEvent(context, ev)
    EventAction.Update -> applyEventUpdate(context, ev)
    EventAction.Delete -> applyEventDelete(context, ev)
}

/** Записывает в календарь изменения события. Меняет только то, что отличается от текущего. */
fun applyEventUpdate(context: Context, ev: EventProposal): Long {
    val id = ev.calendarEventId ?: throw IllegalStateException("не известен номер события")
    val cur = loadCalendarEvent(context, id)
        ?: throw IllegalStateException("событие не найдено — возможно, его уже удалили")
    if (!cur.editable) throw IllegalStateException("этот календарь только для чтения")

    val values = ContentValues()
    if (ev.title != cur.title) values.put(CalendarContract.Events.TITLE, ev.title)
    if (ev.location != cur.location) values.putTextOrNull(CalendarContract.Events.EVENT_LOCATION, ev.location)
    if (ev.description != cur.description) values.putTextOrNull(CalendarContract.Events.DESCRIPTION, ev.description)
    // Время повторяющегося события не трогаем: его длительность хранится иначе
    if (!cur.recurring && (ev.start != cur.start || ev.end != cur.end || ev.allDay != cur.allDay)) {
        putEventTimes(values, ev)
    }
    if (values.size() == 0) return id

    val rows = context.contentResolver.update(eventUri(id), values, null, null)
    if (rows < 1) throw IllegalStateException("календарь не принял изменения")
    return id
}

/** Удаляет событие из календаря телефона. У повторяющегося исчезают все повторы. */
fun applyEventDelete(context: Context, ev: EventProposal): Long {
    val id = ev.calendarEventId ?: throw IllegalStateException("не известен номер события")
    val cur = loadCalendarEvent(context, id)
        ?: throw IllegalStateException("событие не найдено — возможно, его уже удалили")
    if (!cur.editable) throw IllegalStateException("этот календарь только для чтения")
    val rows = context.contentResolver.delete(eventUri(id), null, null)
    if (rows < 1) throw IllegalStateException("календарь не удалил событие")
    SubtaskStore(context).clear(id) // шаги удалённого дела больше никому не нужны
    return id
}
