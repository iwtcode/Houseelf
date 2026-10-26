package com.example.houseelf.calendar

import android.content.Context
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
