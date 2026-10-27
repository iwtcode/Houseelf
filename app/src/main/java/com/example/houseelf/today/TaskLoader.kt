package com.example.houseelf.today

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import com.example.houseelf.calendar.hasCalendarPermission
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** На сколько дней вперёд (кроме сегодняшнего) показываем дела на вкладке «Сегодня». */
const val TASKS_AHEAD_DAYS = 30L

/** События на сегодня и ближайшие [TASKS_AHEAD_DAYS] дней. Без разрешения на календарь возвращает пустой список. */
fun loadTasks(context: Context, today: LocalDate): List<DayTask> {
    if (!hasCalendarPermission(context)) return emptyList()
    val zone = ZoneId.systemDefault()
    val lastDay = today.plusDays(TASKS_AHEAD_DAYS)
    val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = lastDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(uri, from)
    ContentUris.appendId(uri, to)

    val subtasks = SubtaskStore(context)
    val result = mutableListOf<DayTask>()
    try {
        context.contentResolver.query(
            uri.build(),
            arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DESCRIPTION,
            ),
            null, null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val eventId = c.getLong(0)
                val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Без названия"
                val begin = c.getLong(2)
                val finish = c.getLong(3)
                val allDay = c.getInt(4) == 1
                val location = c.getString(5)?.takeIf { it.isNotBlank() }
                val description = c.getString(6)?.takeIf { it.isNotBlank() }

                val start: LocalDateTime
                val end: LocalDateTime
                if (allDay) {
                    // Событие «на весь день» хранится в UTC, поэтому дату берём оттуда
                    val day = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                    start = day.atStartOfDay()
                    end = day.atTime(23, 59)
                } else {
                    start = Instant.ofEpochMilli(begin).atZone(zone).toLocalDateTime()
                    end = Instant.ofEpochMilli(finish).atZone(zone).toLocalDateTime()
                }
                // Многодневное событие, начавшееся раньше, показываем как дело на сегодня
                val date = if (start.toLocalDate().isBefore(today)) today else start.toLocalDate()
                if (date.isAfter(lastDay)) continue

                result += DayTask(
                    key = "$eventId:${date.toEpochDay()}",
                    eventId = eventId,
                    title = title,
                    date = date,
                    start = start,
                    end = end,
                    allDay = allDay,
                    location = location,
                    description = description,
                    steps = subtasks.steps(eventId),
                )
            }
        }
    } catch (_: SecurityException) {
        return emptyList()
    } catch (_: RuntimeException) {
        return emptyList()
    }
    return result.sortedWith(compareBy<DayTask> { it.date }.thenBy { it.start })
}
