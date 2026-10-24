package com.example.houseelf

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class EventStatus {
    /** Включено подтверждение действий: ждём, пока пользователь нажмёт «Добавить». */
    Pending,
    /** Ждём разрешение на доступ к календарю. */
    NeedsPermission,
    /** Действие выполнено (событие добавлено, изменено или удалено — смотри [EventProposal.action]). */
    Added,
    Failed,
    Dismissed,
}

/** Что модель делает с событием в календаре. */
enum class EventAction { Add, Update, Delete }

/** Событие, которое модель вносит в календарь, меняет или удаляет. */
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
    /**
     * Номер события в календаре телефона: у нового — после записи,
     * у изменяемого и удаляемого — известен сразу.
     */
    val calendarEventId: Long? = null,
    val action: EventAction = EventAction.Add,
    /** Повторяющееся событие: изменение текста и удаление касаются всех повторов. */
    val recurring: Boolean = false,
    /** Для изменения: как событие выглядело раньше (название и время), если это поменялось. */
    val before: String? = null,
)

/** Событие, прочитанное из календаря телефона. Для «весь день» [end] — последний день включительно. */
data class CalendarEventInfo(
    val id: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String?,
    val description: String?,
    val recurring: Boolean,
    /** Календарь позволяет менять и удалять это событие. */
    val editable: Boolean,
)

/**
 * Инструменты, которые LiteRT-LM показывает модели (function calling).
 * Класс находится через рефлексию, поэтому он перечислен в keepRules/rules.keep.
 * Описания — на английском: так маленькие модели надёжнее выбирают функцию и заполняют аргументы.
 *
 * Модель умеет: посмотреть календарь ([listCalendarEvents]), добавить, изменить и удалить событие.
 *
 * [handler] делает всю работу (запись или показ карточки) и возвращает ответ для модели:
 * успех или нет + текст, по которому она сообщит пользователю результат.
 * [ensurePermission] при необходимости просит у пользователя доступ к календарю и ждёт ответа.
 */
class CalendarTools(
    private val context: Context,
    private val nextId: () -> Long,
    private val ensurePermission: () -> Boolean,
    private val handler: (EventProposal) -> Pair<Boolean, String>,
) : ToolSet {

    /**
     * Номера событий, которые модель уже видела в ответе [listCalendarEvents].
     * Менять и удалять можно только их: придуманный номер не заденет чужое событие.
     */
    private val knownIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

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

    @Tool(
        description = "Reads the user's calendar and returns the events of a period. Call it whenever the user asks " +
            "what is planned or scheduled (today, tomorrow, this week, this month or on a given date), " +
            "whether a day is free, and ALWAYS before changing or deleting an event, because the result contains " +
            "the event ids. Tell the user only what the result says; never invent events."
    )
    fun listCalendarEvents(
        @ToolParam(description = "One of: today, tomorrow, week (the next 7 days starting today), month (the next 30 days starting today). For any other period leave it as today and set from and to") period: String = "today",
        @ToolParam(description = "First day of another period, e.g. 2026-10-25. Leave empty for today, tomorrow, week and month") from: String? = null,
        @ToolParam(description = "Last day of that period, inclusive, e.g. 2026-10-31. Omit for a single day") to: String? = null,
    ): Map<String, Any> {
        return try {
            val today = LocalDate.now()
            val fromRaw = from?.trim()?.takeIf { it.isNotEmpty() }
            val (first, requestedLast) = if (fromRaw != null) {
                val a = readDay(fromRaw) ?: return fail("Could not read the date '$fromRaw'. Use the format 2026-10-25.")
                val toRaw = to?.trim()?.takeIf { it.isNotEmpty() }
                val b = if (toRaw == null) a else readDay(toRaw)
                    ?: return fail("Could not read the date '$toRaw'. Use the format 2026-10-31.")
                Pair(a, b)
            } else when (period.trim().lowercase(Locale.ROOT)) {
                "today" -> Pair(today, today)
                "tomorrow" -> Pair(today.plusDays(1), today.plusDays(1))
                "week" -> Pair(today, today.plusDays(6))
                "month" -> Pair(today, today.plusDays(29))
                else -> return fail("Unknown period '$period'. Use today, tomorrow, week or month, or set from and to.")
            }
            var last = if (requestedLast.isBefore(first)) first else requestedLast
            var clipped = false
            if (ChronoUnit.DAYS.between(first, last) > MAX_RANGE_DAYS) {
                last = first.plusDays(MAX_RANGE_DAYS.toLong())
                clipped = true
            }

            if (!ensurePermission()) return fail(NO_ACCESS)
            val events = loadCalendarEvents(context, first, last)
            val range = if (first == last) "$first" else "$first to $last"
            if (events.isEmpty()) {
                return ok("No events for $range. The user has nothing planned in this period. Tell them so.")
            }
            val shown = events.take(MAX_LISTED)
            shown.forEach { knownIds.add(it.id) }
            val text = buildString {
                append("Events for $range (${events.size}):\n")
                shown.forEach { append(describeForModel(it)).append('\n') }
                if (events.size > shown.size) {
                    append("...and ${events.size - shown.size} more are not shown. Offer to look at a shorter period.\n")
                }
                if (clipped) append("The period was limited to ${MAX_RANGE_DAYS + 1} days.\n")
                append("Tell the user briefly, in their language, what is planned.")
            }
            ok(text)
        } catch (t: Throwable) {
            fail("Could not read the calendar: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    @Tool(
        description = "Changes an existing event in the user's calendar: its title, time, place or notes. " +
            "Only the fields you pass are changed. Call it when the user asks to move, reschedule, rename or edit an event. " +
            "You need the event id: get it from listCalendarEvents first. " +
            "Report the outcome to the user exactly as the tool result says."
    )
    fun updateCalendarEvent(
        @ToolParam(description = "The event id from listCalendarEvents, digits only") eventId: String,
        @ToolParam(description = "New title. Omit to keep the current one") title: String? = null,
        @ToolParam(description = "New start in ISO 8601 local time, e.g. 2026-10-25T15:00. A date only (2026-10-25) moves a timed event to that day and keeps its time. Omit to keep the current start") start: String? = null,
        @ToolParam(description = "New end in the same format. Omit to keep the current length of the event") end: String? = null,
        @ToolParam(description = "New place. Omit to keep the current one") location: String? = null,
        @ToolParam(description = "New notes. Omit to keep the current ones") description: String? = null,
    ): Map<String, Any> {
        return try {
            val id = parseEventId(eventId)
                ?: return fail("The event id must be the number from listCalendarEvents. Call listCalendarEvents first.")
            if (id !in knownIds) return fail(UNKNOWN_ID)
            if (!ensurePermission()) return fail(NO_ACCESS)
            val cur = loadCalendarEvent(context, id)
                ?: return fail("There is no event with id $id any more. Call listCalendarEvents again.")
            if (!cur.editable) return fail("The event '${cur.title}' is in a read-only calendar and cannot be changed. Tell the user.")

            val newTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: cur.title
            val newLocation = location?.trim()?.takeIf { it.isNotEmpty() } ?: cur.location
            val newDescription = description?.trim()?.takeIf { it.isNotEmpty() } ?: cur.description

            val startRaw = start?.trim()?.takeIf { it.isNotEmpty() }
            val endRaw = end?.trim()?.takeIf { it.isNotEmpty() }
            var allDay = cur.allDay
            var newStart = cur.start
            var newEnd = cur.end

            if (startRaw != null || endRaw != null) {
                if (cur.recurring) {
                    return fail("'${cur.title}' is a recurring event: its time cannot be changed here, only the title, place and notes. " +
                        "Tell the user to change the time in the calendar app.")
                }
                val s = startRaw?.let { parseDateTime(it) ?: return fail("Could not read the start '$it'. Use the format 2026-10-25T15:00.") }
                val e = endRaw?.let { parseDateTime(it) ?: return fail("Could not read the end '$it'. Use the format 2026-10-25T16:00.") }
                if (s != null) {
                    val (startValue, hasTime) = s
                    val keepTimeOfDay = !hasTime && !cur.allDay
                    if (hasTime || keepTimeOfDay) {
                        val begin = if (hasTime) startValue else startValue.toLocalDate().atTime(cur.start.toLocalTime())
                        val span = if (cur.allDay) Duration.ofHours(1) else Duration.between(cur.start, cur.end)
                            .let { if (it.isNegative || it.isZero) Duration.ofHours(1) else it }
                        allDay = false
                        newStart = begin
                        newEnd = if (e != null && e.first.isAfter(begin)) e.first else begin.plus(span)
                    } else {
                        val firstDay = startValue.toLocalDate()
                        val keptDays = ChronoUnit.DAYS.between(cur.start.toLocalDate(), cur.end.toLocalDate())
                        val lastDay = e?.first?.toLocalDate()?.takeIf { !it.isBefore(firstDay) } ?: firstDay.plusDays(keptDays)
                        allDay = true
                        newStart = firstDay.atStartOfDay()
                        newEnd = lastDay.atStartOfDay()
                    }
                } else if (e != null) {
                    if (allDay) {
                        val lastDay = e.first.toLocalDate()
                        if (lastDay.isBefore(newStart.toLocalDate())) return fail("The end is before the start of the event.")
                        newEnd = lastDay.atStartOfDay()
                    } else {
                        if (!e.first.isAfter(newStart)) return fail("The end must be after the start of the event.")
                        newEnd = e.first
                    }
                }
            }

            val timeChanged = newStart != cur.start || newEnd != cur.end || allDay != cur.allDay
            if (newTitle == cur.title && newLocation == cur.location && newDescription == cur.description && !timeChanged) {
                return ok("The event '${cur.title}' already has these values, nothing was changed. Tell the user.")
            }

            val before = if (!cur.recurring && (newTitle != cur.title || timeChanged)) {
                "${cur.title} — ${formatWhen(cur.start, cur.end, cur.allDay)}"
            } else null

            val (success, message) = handler(
                EventProposal(
                    id = nextId(),
                    title = newTitle,
                    start = newStart,
                    end = newEnd,
                    allDay = allDay,
                    location = newLocation,
                    description = newDescription,
                    calendarEventId = id,
                    action = EventAction.Update,
                    recurring = cur.recurring,
                    before = before,
                )
            )
            mapOf("status" to (if (success) "ok" else "error"), "message" to message)
        } catch (t: Throwable) {
            fail("Failed to prepare the change: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    @Tool(
        description = "Deletes an event from the user's calendar. Call it only when the user clearly asks to delete, " +
            "remove or cancel a specific event. You need the event id: get it from listCalendarEvents first. " +
            "If several events could match, ask the user which one. Never delete events the user did not ask about. " +
            "Report the outcome to the user exactly as the tool result says."
    )
    fun deleteCalendarEvent(
        @ToolParam(description = "The event id from listCalendarEvents, digits only") eventId: String,
        @ToolParam(description = "true only if the event repeats and the user agreed to delete ALL its repeats") deleteWholeSeries: Boolean = false,
    ): Map<String, Any> {
        return try {
            val id = parseEventId(eventId)
                ?: return fail("The event id must be the number from listCalendarEvents. Call listCalendarEvents first.")
            if (id !in knownIds) return fail(UNKNOWN_ID)
            if (!ensurePermission()) return fail(NO_ACCESS)
            val cur = loadCalendarEvent(context, id)
                ?: return fail("There is no event with id $id any more. Call listCalendarEvents again.")
            if (!cur.editable) return fail("The event '${cur.title}' is in a read-only calendar and cannot be deleted. Tell the user.")
            if (cur.recurring && !deleteWholeSeries) {
                return fail("'${cur.title}' is a recurring event: deleting it removes ALL its repeats, not just one. " +
                    "Ask the user whether to delete the whole series. If they agree, call this tool again with deleteWholeSeries=true.")
            }

            val (success, message) = handler(
                EventProposal(
                    id = nextId(),
                    title = cur.title,
                    start = cur.start,
                    end = cur.end,
                    allDay = cur.allDay,
                    location = cur.location,
                    description = cur.description,
                    calendarEventId = id,
                    action = EventAction.Delete,
                    recurring = cur.recurring,
                )
            )
            mapOf("status" to (if (success) "ok" else "error"), "message" to message)
        } catch (t: Throwable) {
            fail("Failed to prepare the deletion: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun ok(message: String): Map<String, Any> = mapOf("status" to "ok", "message" to message)

    private fun readDay(raw: String): LocalDate? = parseDateTime(raw)?.first?.toLocalDate()

    /** Достаёт число из «123», «id=123» или «[id=123]». */
    private fun parseEventId(raw: String): Long? = Regex("\\d+").find(raw)?.value?.toLongOrNull()

    /** Одна строка события для модели: номер, когда, название, место. */
    private fun describeForModel(e: CalendarEventInfo): String {
        val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        fun day(x: LocalDateTime) = "${x.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${x.format(dateFmt)}"
        val whenText = when {
            e.allDay && e.end.toLocalDate() == e.start.toLocalDate() -> "${day(e.start)}, all day"
            e.allDay -> "${day(e.start)} to ${day(e.end)}, all day"
            e.start.toLocalDate() == e.end.toLocalDate() -> "${day(e.start)} ${e.start.format(timeFmt)}-${e.end.format(timeFmt)}"
            else -> "${day(e.start)} ${e.start.format(timeFmt)} to ${day(e.end)} ${e.end.format(timeFmt)}"
        }
        return buildString {
            append("[id=").append(e.id).append("] ").append(whenText).append(" | ").append(e.title.take(80))
            e.location?.let { append(" | place: ").append(it.take(60)) }
            if (e.recurring) append(" | repeats")
        }
    }

    private companion object {
        const val MAX_LISTED = 40
        const val MAX_RANGE_DAYS = 92
        const val NO_ACCESS = "The user has not allowed access to the calendar, so it cannot be used. " +
            "Tell the user that calendar access is needed and they can allow it in the phone settings."
        const val UNKNOWN_ID = "That event id was not in the calendar results. Call listCalendarEvents first " +
            "and use an id from its result."
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

// ───────── Чтение, изменение и удаление ─────────

/** Выполняет действие модели: добавляет, меняет или удаляет событие. Возвращает номер события. */
fun applyCalendarAction(context: Context, ev: EventProposal): Long = when (ev.action) {
    EventAction.Add -> insertCalendarEvent(context, ev)
    EventAction.Update -> applyEventUpdate(context, ev)
    EventAction.Delete -> applyEventDelete(context, ev)
}

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

private fun eventUri(id: Long) = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)

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
                description = null,
                recurring = !c.getString(6).isNullOrBlank() || !c.getString(7).isNullOrBlank(),
                editable = true, // точный ответ даёт loadCalendarEvent перед изменением
            )
        }
    }
    return result.sortedBy { it.start }
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
    return id
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
fun formatEventWhen(ev: EventProposal): String = formatWhen(ev.start, ev.end, ev.allDay)

fun formatWhen(s: LocalDateTime, e: LocalDateTime, allDay: Boolean): String {
    val loc = Locale.getDefault()
    val day = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", loc)
    val dayShort = DateTimeFormatter.ofPattern("d MMM yyyy", loc)
    val time = DateTimeFormatter.ofPattern("HH:mm", loc)
    return when {
        allDay && e.toLocalDate() == s.toLocalDate() -> "${s.format(day)}, весь день"
        allDay -> "${s.format(dayShort)} – ${e.format(dayShort)}, весь день"
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
