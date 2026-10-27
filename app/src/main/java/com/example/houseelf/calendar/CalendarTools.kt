package com.example.houseelf.calendar

import android.content.Context
import com.example.houseelf.today.SubtaskStore
import com.example.houseelf.today.parseSteps
import com.example.houseelf.today.stepsFromNotes
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
 * Модель умеет: посмотреть календарь ([listCalendarEvents]), добавить, изменить и удалить событие,
 * а также разбить дело на шаги ([setTaskSteps]).
 *
 * Менять, удалять и разбивать на шаги можно по названию дела: маленькая модель часто теряет номер события
 * между вызовами или передаёт его не тем типом, поэтому одним вызовом надёжнее. Номер ([findEvent]) остаётся
 * запасным путём.
 *
 * [handler] делает всю работу (запись или показ карточки) и возвращает ответ для модели:
 * успех или нет + текст, по которому она сообщит пользователю результат.
 * [ensurePermission] при необходимости просит у пользователя доступ к календарю и ждёт ответа.
 *
 * [defaults] — что делать, если у нового события нет названия или даты. null (чат): вернуть модели ошибку,
 * и она переспросит пользователя. Не null (Домовой.Событие, спросить некого): подставить значения по умолчанию.
 */
class CalendarTools(
    private val context: Context,
    private val nextId: () -> Long,
    private val ensurePermission: () -> Boolean,
    private val handler: (EventProposal) -> Pair<Boolean, String>,
    private val defaults: EventDefaults? = null,
) : ToolSet {

    /**
     * Номера событий, которые модель уже видела в ответе [listCalendarEvents].
     * Менять и удалять можно только их: придуманный номер не заденет чужое событие.
     */
    private val knownIds: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    private val subtasks = SubtaskStore(context)

    @Tool(
        description = "Adds an event to the user's calendar. Call it whenever the user asks to add, " +
            "schedule, book or remind about a meeting, appointment, event, task or deadline. " +
            "Always put the details of the task (what to buy, take, prepare or do) into notes. " +
            "Report the outcome to the user exactly as the tool result says."
    )
    fun addCalendarEvent(
        @ToolParam(description = "Very short title, 1-3 words, in the user's language, without date, time or details, e.g. 'Поход в магазин'. Omit if the text gives no title") title: String? = null,
        @ToolParam(description = "The task description: everything the user said about what to do, buy, take or prepare, e.g. 'купить молоко, хлеб, яйца и сыр'. Fill it whenever the message has any such details. Leave empty only if there are none") notes: String? = null,
        @ToolParam(description = "Start date and time in ISO 8601 local time without time zone, e.g. 2026-10-25T15:00. For an all-day event use only the date, e.g. 2026-10-25. Omit if the text gives no date") start: String? = null,
        @ToolParam(description = "End date and time in the same format. Omit if the user did not say when it ends") end: String? = null,
        @ToolParam(description = "Place of the event, if mentioned") location: String? = null,
        @ToolParam(description = "true if the event lasts all day or the user gave no time, otherwise false") allDay: Boolean = false,
    ): Map<String, Any> {
        return try {
            // Если модель сложила детали в название («Поход в магазин: купить молоко») и пропустила notes — разделяем сами
            val (cleanTitle, cleanNotes) = separateNotes(title, notes)
            val name = resolveTitle(cleanTitle)
                ?: return fail("The title is empty. Ask the user what the event is called.")
            val (startValue, startHasTime) = resolveStart(start)
                ?: return fail(startProblem(start))
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
                    description = cleanNotes,
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
            "whether a day is free. Tell the user only what the result says; never invent events."
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
            "Only the fields you pass are changed. Call it right away when the user asks to move, reschedule, rename or edit an event: " +
            "pass the current title of the event in eventTitle, no need to read the calendar first. " +
            "Report the outcome to the user exactly as the tool result says."
    )
    fun updateCalendarEvent(
        @ToolParam(description = "Current title of the event to change, as the user names it, e.g. 'Поход в магазин'") eventTitle: String? = null,
        @ToolParam(description = "New title. Omit to keep the current one") title: String? = null,
        @ToolParam(description = "New start in ISO 8601 local time, e.g. 2026-10-25T15:00. A date only (2026-10-25) moves a timed event to that day and keeps its time. Omit to keep the current start") start: String? = null,
        @ToolParam(description = "New end in the same format. Omit to keep the current length of the event") end: String? = null,
        @ToolParam(description = "New place. Omit to keep the current one") location: String? = null,
        @ToolParam(description = "New task description (notes). Omit to keep the current one") notes: String? = null,
        @ToolParam(description = "Optional: the event id from listCalendarEvents, digits only. Not needed when eventTitle is given") eventId: String? = null,
    ): Map<String, Any> {
        return try {
            if (!ensurePermission()) return fail(NO_ACCESS)
            val lookup = findEvent(eventTitle, eventId, pickNearest = false)
            val id = lookup.id ?: return lookup.problem!!
            val cur = loadCalendarEvent(context, id)
                ?: return fail("The event is not in the calendar any more. Tell the user.")
            if (!cur.editable) return fail("The event '${cur.title}' is in a read-only calendar and cannot be changed. Tell the user.")

            val newTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: cur.title
            val newLocation = location?.trim()?.takeIf { it.isNotEmpty() } ?: cur.location
            val newDescription = notes?.trim()?.takeIf { it.isNotEmpty() } ?: cur.description

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
            "remove or cancel a specific event: pass its title in eventTitle, no need to read the calendar first. " +
            "If the result says several events match, ask the user which one. Never delete events the user did not ask about. " +
            "Report the outcome to the user exactly as the tool result says."
    )
    fun deleteCalendarEvent(
        @ToolParam(description = "Title of the event to delete, as the user names it, e.g. 'Уборка'") eventTitle: String? = null,
        @ToolParam(description = "true only if the event repeats and the user agreed to delete ALL its repeats") deleteWholeSeries: Boolean = false,
        @ToolParam(description = "Optional: the event id from listCalendarEvents, digits only. Not needed when eventTitle is given") eventId: String? = null,
    ): Map<String, Any> {
        return try {
            if (!ensurePermission()) return fail(NO_ACCESS)
            val lookup = findEvent(eventTitle, eventId, pickNearest = false)
            val id = lookup.id ?: return lookup.problem!!
            val cur = loadCalendarEvent(context, id)
                ?: return fail("The event is not in the calendar any more. Tell the user.")
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

    @Tool(
        description = "Splits a task into steps (subtasks) that the user sees in the Today tab. " +
            "Call it right away when the user asks to break a task into steps or subtasks: " +
            "pass the title of the task in taskTitle and the steps in steps, no need to read the calendar first. " +
            "Base the steps on the task notes if it has them, otherwise on its title. " +
            "Calling it again replaces the old steps. Report the outcome to the user exactly as the tool result says."
    )
    fun setTaskSteps(
        @ToolParam(description = "Title of the task to split, as the user names it, e.g. 'Поход в магазин'. Omit to take the nearest upcoming task") taskTitle: String? = null,
        @ToolParam(description = "3 to 6 short steps, one per line, in the user's language. Each step is an action of 2-6 words. No numbering") steps: String? = null,
        @ToolParam(description = "Optional: the event id from listCalendarEvents, digits only. Not needed when taskTitle is given") eventId: String? = null,
    ): Map<String, Any> {
        return try {
            if (!ensurePermission()) return fail(NO_ACCESS)
            val lookup = findEvent(taskTitle, eventId, pickNearest = true)
            val id = lookup.id ?: return lookup.problem!!
            val task = loadCalendarEvent(context, id)
                ?: return fail("The task is not in the calendar any more. Tell the user.")
            // Если модель прислала мало шагов или не прислала совсем — делим описание дела сами
            var parsed = parseSteps(steps.orEmpty())
            if (parsed.size < MIN_STEPS) parsed = stepsFromNotes(task.description)
            if (parsed.size < MIN_STEPS) {
                return fail("No steps were given. Call the tool again with 3 to 6 short steps, one per line, in the steps argument.")
            }

            subtasks.save(id, parsed)
            ok("The task '${task.title}' now has ${parsed.size} steps: ${parsed.joinToString("; ")}. " +
                "The user sees them in the Today tab. Tell the user briefly.")
        } catch (t: Throwable) {
            fail("Failed to save the steps: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    /** Результат поиска дела: либо номер, либо готовый ответ-ошибка для модели. */
    private class Lookup(val id: Long? = null, val problem: Map<String, Any>? = null)

    /**
     * Находит дело для изменения, удаления или шагов. Сначала по номеру (если модель его знает из просмотра
     * календаря), иначе по названию среди дел за неделю назад и ближайшие три месяца: сначала будущие, потом прошедшие.
     * Если подходит несколько разных дел: при [pickNearest] берём ближайшее, иначе просим модель спросить пользователя.
     */
    private fun findEvent(title: String?, idRaw: String?, pickNearest: Boolean): Lookup {
        val byId = idRaw?.let { parseEventId(it) }
        if (byId != null && byId in knownIds) return Lookup(id = byId)

        val today = LocalDate.now()
        val ordered = loadCalendarEvents(context, today.minusDays(SEARCH_DAYS_BACK), today.plusDays(SEARCH_DAYS_AHEAD))
            .sortedWith(compareBy<CalendarEventInfo>({ it.end.toLocalDate().isBefore(today) }, { it.start }))
            .distinctBy { it.id } // у повторяющегося события много вхождений, а дело одно

        val query = normalizeTitle(title.orEmpty())
        if (query.isEmpty()) {
            if (!pickNearest) return Lookup(problem = fail("Which event? Ask the user for the name of the event."))
            val nearest = ordered.firstOrNull() ?: return Lookup(problem = fail(NOTHING_PLANNED))
            return Lookup(id = nearest.id)
        }

        val scored = ordered.map { it to titleScore(query, normalizeTitle(it.title)) }.filter { it.second > 0 }
        if (scored.isEmpty()) {
            return Lookup(problem = fail("No event named '${title?.trim()}' was found in the calendar. " +
                "Do nothing and tell the user that there is no such event."))
        }
        val best = scored.maxOf { it.second }
        val top = scored.filter { it.second == best }.map { it.first }
        if (top.size == 1 || pickNearest) return Lookup(id = top.first().id)

        top.forEach { knownIds.add(it.id) }
        val variants = top.take(MAX_AMBIGUOUS).joinToString("; ") {
            "[id=${it.id}] ${it.title.take(60)} (${formatWhen(it.start, it.end, it.allDay)})"
        }
        return Lookup(problem = fail("Several events match: $variants. " +
            "Ask the user which one, then call the tool again with that event's id in eventId."))
    }

    private fun ok(message: String): Map<String, Any> = mapOf("status" to "ok", "message" to message)

    /** Название и описание. Если описания нет, а в названии есть «двоеточие и детали», делим по двоеточию. */
    private fun separateNotes(title: String?, notes: String?): Pair<String?, String?> {
        val t = title?.trim()
        val n = notes?.trim()?.takeIf { it.isNotEmpty() }
        if (n != null || t == null) return t to n
        val colon = t.indexOf(':')
        if (colon < 1 || colon == t.lastIndex) return t to null
        val head = t.substring(0, colon).trim()
        val tail = t.substring(colon + 1).trim()
        return if (head.isNotEmpty() && tail.isNotEmpty()) head to tail else t to null
    }

    /** Название из запроса модели, а если его нет — название по умолчанию (только когда оно разрешено). */
    private fun resolveTitle(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() } ?: defaults?.nextTitle()

    /** Начало из запроса модели, а если его нет или оно не читается — начало по умолчанию (только когда оно разрешено). */
    private fun resolveStart(raw: String?): Pair<LocalDateTime, Boolean>? =
        raw?.takeIf { it.isNotBlank() }?.let { parseDateTime(it) } ?: defaults?.let { it.start() to false }

    /** Почему начало не удалось определить: модель либо не передала его, либо передала нечитаемое. */
    private fun startProblem(raw: String?): String =
        if (raw.isNullOrBlank()) "The start is missing. Ask the user when the event takes place."
        else "Could not read the start '$raw'. Use the format 2026-10-25T15:00 or 2026-10-25."

    /** Название для сравнения: строчные буквы, «ё» как «е», без знаков препинания и лишних пробелов. */
    private fun normalizeTitle(raw: String): String = raw.lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    /** Насколько название события подходит под запрос: 0 — совсем не подходит. */
    private fun titleScore(query: String, title: String): Int {
        if (title.isEmpty()) return 0
        if (query == title) return 100
        if (title in query || query in title) return 80
        val q = query.split(' ').filter { it.length >= MIN_WORD }
        val t = title.split(' ').filter { it.length >= MIN_WORD }
        if (q.isEmpty() || t.isEmpty()) return 0
        val matched = q.count { a -> t.any { b -> sameWord(a, b) } }
        // Совпала хотя бы половина слов запроса: «уборка» подходит к «Генеральная уборка»
        return if (matched > 0 && matched * 2 >= q.size) 10 * matched else 0
    }

    /** Слова совпадают, если различаются только окончанием: «уборку» и «уборка», «врачу» и «врач». */
    private fun sameWord(a: String, b: String): Boolean {
        val n = minOf(a.length, b.length)
        val common = a.commonPrefixWith(b).length
        return common >= MIN_WORD && common >= n - 2
    }

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
            e.description?.let { append(" | notes: ").append(it.take(MAX_NOTES_LENGTH)) }
            if (e.recurring) append(" | repeats")
        }
    }

    private companion object {
        const val MAX_LISTED = 40
        const val MAX_NOTES_LENGTH = 200
        const val MIN_STEPS = 2
        const val MAX_RANGE_DAYS = 92
        const val SEARCH_DAYS_BACK = 7L
        const val SEARCH_DAYS_AHEAD = 90L
        const val MAX_AMBIGUOUS = 5
        const val MIN_WORD = 3
        const val NOTHING_PLANNED = "There are no events in the calendar. Tell the user."
        const val NO_ACCESS = "The user has not allowed access to the calendar, so it cannot be used. " +
            "Tell the user that calendar access is needed and they can allow it in the phone settings."
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
