package com.example.houseelf

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class EventStatus {
    /** Включено подтверждение действий: ждём, пока пользователь нажмёт «Добавить». */
    Pending,
    /** Ждём разрешение на доступ к календарю. */
    NeedsPermission,
    Added,
    Failed,
    Dismissed,
}

/** Событие, которое модель вносит в календарь. */
data class EventProposal(
    val id: Long,
    val title: String,
    val start: LocalDateTime,
    /** Для событий «на весь день» — последний день включительно (время игнорируется). */
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String?,
    val description: String?,
    val status: EventStatus = EventStatus.Pending,
    val error: String? = null,
    /** Номер созданного события в календаре телефона. */
    val calendarEventId: Long? = null,
)

/**
 * Инструменты, которые LiteRT-LM показывает модели (function calling).
 * Класс находится через рефлексию, поэтому он перечислен в keepRules/rules.keep.
 * Описания — на английском: так маленькие модели надёжнее выбирают функцию и заполняют аргументы.
 *
 * [handler] делает всю работу (запись или показ карточки) и возвращает ответ для модели:
 * успех или нет + текст, по которому она сообщит пользователю результат.
 */
class CalendarTools(
    private val nextId: () -> Long,
    private val handler: (EventProposal) -> Pair<Boolean, String>,
) : ToolSet {

    @Tool(
        description = "Adds an event to the user's calendar. Call it whenever the user asks to add, " +
            "schedule, book or remind about a meeting, appointment, event, task or deadline. " +
            "Report the outcome to the user exactly as the tool result says."
    )
    fun addCalendarEvent(
        @ToolParam(description = "Short event title, in the user's language") title: String,
        @ToolParam(description = "Start date and time in ISO 8601 local time without time zone, e.g. 2026-10-25T15:00. For an all-day event use only the date, e.g. 2026-10-25") start: String,
        @ToolParam(description = "End date and time in the same format. Omit if the user did not say when it ends") end: String? = null,
        @ToolParam(description = "Place of the event, if mentioned") location: String? = null,
        @ToolParam(description = "Extra notes or description, if mentioned") description: String? = null,
        @ToolParam(description = "true if the event lasts all day or the user gave no time, otherwise false") allDay: Boolean = false,
    ): Map<String, Any> {
        return try {
            val name = title.trim()
            if (name.isEmpty()) return fail("The title is empty. Ask the user what the event is called.")

            val (startValue, startHasTime) = parseDateTime(start)
                ?: return fail("Could not read the start '$start'. Use the format 2026-10-25T15:00 or 2026-10-25.")
            val isAllDay = allDay || !startHasTime

            val endValue = end?.takeIf { it.isNotBlank() }?.let { parseDateTime(it)?.first }
            val (from, to) = if (isAllDay) {
                val firstDay = startValue.toLocalDate()
                val lastDay = endValue?.toLocalDate()?.takeIf { !it.isBefore(firstDay) } ?: firstDay
                firstDay.atStartOfDay() to lastDay.atStartOfDay()
            } else {
                val finish = if (endValue != null && endValue.isAfter(startValue)) endValue else startValue.plusHours(1)
                startValue to finish
            }

            val (ok, message) = handler(
                EventProposal(
                    id = nextId(),
                    title = name,
                    start = from,
                    end = to,
                    allDay = isAllDay,
                    location = location?.trim()?.takeIf { it.isNotEmpty() },
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                )
            )
            mapOf("status" to (if (ok) "ok" else "error"), "message" to message)
        } catch (t: Throwable) {
            fail("Failed to prepare the event: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun fail(message: String): Map<String, Any> = mapOf("status" to "error", "message" to message)

    /** Возвращает дату-время и признак «время было указано». */
    private fun parseDateTime(raw: String): Pair<LocalDateTime, Boolean>? {
        val s = raw.trim().replace(' ', 'T')
        try { return LocalDateTime.parse(s) to true } catch (_: Exception) {}
        try { return OffsetDateTime.parse(s).toLocalDateTime() to true } catch (_: Exception) {}
        try { return LocalDate.parse(s).atStartOfDay() to false } catch (_: Exception) {}
        return null
    }
}

// ───────── Доступ к календарю телефона ─────────

val CALENDAR_PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

fun hasCalendarPermission(context: Context): Boolean =
    CALENDAR_PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

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
        if (ev.allDay) {
            // Событие «на весь день»: границы — полночь по UTC, конец исключительный
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events.DTSTART, utcMidnight(ev.start.toLocalDate()))
            put(CalendarContract.Events.DTEND, utcMidnight(ev.end.toLocalDate().plusDays(1)))
        } else {
            val zone = ZoneId.systemDefault()
            put(CalendarContract.Events.ALL_DAY, 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.DTSTART, ev.start.atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, ev.end.atZone(zone).toInstant().toEpochMilli())
        }
    }
    val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
        ?: throw IllegalStateException("календарь не принял событие")
    return ContentUris.parseId(uri)
}

private fun utcMidnight(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

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

/** «Сб, 25 окт. 2026, 15:00–16:00» — для карточки в чате. */
fun formatEventWhen(ev: EventProposal): String {
    val loc = Locale.getDefault()
    val day = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", loc)
    val dayShort = DateTimeFormatter.ofPattern("d MMM yyyy", loc)
    val time = DateTimeFormatter.ofPattern("HH:mm", loc)
    val s = ev.start
    val e = ev.end
    return when {
        ev.allDay && e.toLocalDate() == s.toLocalDate() -> "${s.format(day)}, весь день"
        ev.allDay -> "${s.format(dayShort)} – ${e.format(dayShort)}, весь день"
        s.toLocalDate() == e.toLocalDate() -> "${s.format(day)}, ${s.format(time)}–${e.format(time)}"
        else -> "${s.format(dayShort)} ${s.format(time)} – ${e.format(dayShort)} ${e.format(time)}"
    }
}

/** Текущие дата и время для системной подсказки: без них модель не поймёт «завтра» и «в пятницу». */
fun currentTimeForPrompt(): String {
    val now = ZonedDateTime.now()
    val human = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru")))
    val iso = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
    return "$human (в формате ISO: $iso, часовой пояс ${now.zone.id})"
}
