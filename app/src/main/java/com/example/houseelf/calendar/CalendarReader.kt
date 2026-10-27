package com.example.houseelf.calendar

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private val EVENT_PROJECTION = arrayOf(
    CalendarContract.Events._ID,
    CalendarContract.Events.TITLE,
    CalendarContract.Events.DTSTART,
    CalendarContract.Events.DTEND,
    CalendarContract.Events.DURATION,
    CalendarContract.Events.ALL_DAY,
    CalendarContract.Events.EVENT_LOCATION,
    CalendarContract.Events.DESCRIPTION,
    CalendarContract.Events.RRULE,
    CalendarContract.Events.RDATE,
    CalendarContract.Events.CALENDAR_ACCESS_LEVEL,
)

internal fun eventUri(id: Long) = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)

/** Одно событие по номеру. null — такого события нет (например, его уже удалили). */
fun loadCalendarEvent(context: Context, id: Long): CalendarEventInfo? {
    context.contentResolver.query(
        eventUri(id), EVENT_PROJECTION, "${CalendarContract.Events.DELETED} = 0", null, null,
    )?.use { c ->
        if (!c.moveToFirst()) return null
        return eventFromCursor(c)
    }
    return null
}

private fun eventFromCursor(c: Cursor): CalendarEventInfo {
    val allDay = c.getInt(5) == 1
    val begin = c.getLong(2)
    // У повторяющихся событий вместо конца хранится длительность
    val finish = if (!c.isNull(3)) c.getLong(3)
    else begin + (parseRfcDuration(c.getString(4))?.toMillis() ?: if (allDay) 86_400_000L else 3_600_000L)
    val (start, end) = toLocalRange(begin, finish, allDay)
    return CalendarEventInfo(
        id = c.getLong(0),
        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Без названия",
        start = start,
        end = end,
        allDay = allDay,
        location = c.getString(6)?.takeIf { it.isNotBlank() },
        description = c.getString(7)?.takeIf { it.isNotBlank() },
        recurring = !c.getString(8).isNullOrBlank() || !c.getString(9).isNullOrBlank(),
        editable = c.getInt(10) >= CalendarContract.Calendars.CAL_ACCESS_EDITOR,
    )
}

/** Длительность в записи календаря бывает «PT1H» или «P3600S». Другие записи (недели и т. п.) не разбираем. */
private fun parseRfcDuration(raw: String?): Duration? {
    var s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (Regex("P\\d+S").matches(s)) s = "PT" + s.substring(1)
    return try { Duration.parse(s) } catch (_: Exception) { null }
}

/**
 * Границы события в местном времени. Событие «на весь день» хранится в UTC с исключительным концом,
 * поэтому там конец — последний день включительно.
 */
private fun toLocalRange(begin: Long, finish: Long, allDay: Boolean): Pair<LocalDateTime, LocalDateTime> {
    if (allDay) {
        val first = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
        val last = Instant.ofEpochMilli(finish).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
        return first.atStartOfDay() to (if (last.isBefore(first)) first else last).atStartOfDay()
    }
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(begin).atZone(zone).toLocalDateTime() to
        Instant.ofEpochMilli(finish).atZone(zone).toLocalDateTime()
}

/** Все события, затрагивающие дни с [first] по [last] включительно, по порядку начала. Нужны разрешения. */
fun loadCalendarEvents(context: Context, first: LocalDate, last: LocalDate): List<CalendarEventInfo> {
    val zone = ZoneId.systemDefault()
    val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(uri, from)
    ContentUris.appendId(uri, to)

    val result = mutableListOf<CalendarEventInfo>()
    context.contentResolver.query(
        uri.build(),
        arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.RDATE,
            CalendarContract.Instances.DESCRIPTION,
        ),
        null, null,
        "${CalendarContract.Instances.BEGIN} ASC",
    )?.use { c ->
        while (c.moveToNext()) {
            val allDay = c.getInt(4) == 1
            val (start, end) = toLocalRange(c.getLong(2), c.getLong(3), allDay)
            // Событие, которое заканчивается ровно в полночь, не относится к следующему дню
            val lastDay = when {
                allDay -> end.toLocalDate()
                end.isAfter(start) -> end.minusNanos(1).toLocalDate()
                else -> start.toLocalDate()
            }
            if (start.toLocalDate().isAfter(last) || lastDay.isBefore(first)) continue
            result += CalendarEventInfo(
                id = c.getLong(0),
                title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Без названия",
                start = start,
                end = end,
                allDay = allDay,
                location = c.getString(5)?.takeIf { it.isNotBlank() },
                // Без описания модель не увидит заметки дела и не сможет разбить его на шаги по ним
                description = c.getString(8)?.takeIf { it.isNotBlank() },
                recurring = !c.getString(6).isNullOrBlank() || !c.getString(7).isNullOrBlank(),
                editable = true, // точный ответ даёт loadCalendarEvent перед изменением
            )
        }
    }
    return result.sortedBy { it.start }
}
