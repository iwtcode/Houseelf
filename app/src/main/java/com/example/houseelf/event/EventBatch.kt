package com.example.houseelf.event

import android.content.Context
import com.example.houseelf.calendar.EventAction
import com.example.houseelf.calendar.EventProposal
import com.example.houseelf.calendar.NaturalDateTime
import com.example.houseelf.calendar.applyCalendarAction
import com.example.houseelf.calendar.formatEventWhen
import java.time.LocalTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/** Одно действие над календарём и чем оно кончилось. */
data class EventOutcome(
    val event: EventProposal,
    /** null — действие выполнено, иначе причина неудачи. */
    val error: String? = null,
) {
    val succeeded: Boolean get() = error == null
}

/** Итог одного прогона: всё, что модель сделала с календарём за один раз. */
class EventBatchSummary(val outcomes: List<EventOutcome>) {
    val succeeded: List<EventOutcome> get() = outcomes.filter { it.succeeded }
    val failed: List<EventOutcome> get() = outcomes.filterNot { it.succeeded }

    fun count(action: EventAction): Int = succeeded.count { it.event.action == action }

    /** Модель ничего не сделала: в тексте не нашлось событий или не хватило данных. */
    val isEmpty: Boolean get() = outcomes.isEmpty()
}

/**
 * Выполняет действия, которые модель просит за один прогон, и запоминает результат каждого.
 * Событий может быть много: каждый вызов инструмента — одно действие, неудача одного не мешает остальным.
 * Вызывается из потока генерации.
 */
internal class EventBatch(private val context: Context, sourceText: String = "") {

    private val lastId = AtomicLong(0)
    private val outcomes = CopyOnWriteArrayList<EventOutcome>()

    fun nextId(): Long = lastId.incrementAndGet()

    /**
     * Единственное время, названное в исходном тексте. Если модель его потеряла и поставила событие на весь день,
     * подставляем его сами. null — времени нет, их несколько или пользователь просил «на весь день».
     */
    private val textTime: LocalTime? = run {
        if (ALL_DAY_PHRASE.containsMatchIn(sourceText.lowercase())) return@run null
        val clocks = CLOCK.findAll(sourceText).map { it.value }.toSet()
        if (clocks.size > 1) null else NaturalDateTime.parseTime(sourceText, standalone = false)
    }

    /** Событие на весь день, хотя в тексте есть время: модель его потеряла. Ставим время по часам, на час. */
    private fun withTextTime(event: EventProposal): EventProposal {
        val time = textTime ?: return event
        if (event.action != EventAction.Add || !event.allDay) return event
        if (outcomes.any { it.succeeded && !it.event.allDay }) return event // время уже занято другим событием текста
        val begin = event.start.toLocalDate().atTime(time)
        return event.copy(start = begin, end = begin.plusHours(1), allDay = false)
    }

    /** Пишет действие в календарь сразу, без подтверждения. Возвращает модели ответ: получилось ли и что дальше. */
    fun execute(original: EventProposal): Pair<Boolean, String> {
        val event = withTextTime(original)
        return try {
            val calendarEventId = applyCalendarAction(context, event)
            outcomes += EventOutcome(event.copy(calendarEventId = calendarEventId))
            true to successReply(event)
        } catch (t: Throwable) {
            val reason = t.message ?: t.javaClass.simpleName
            outcomes += EventOutcome(event, error = reason)
            false to "Could not ${verb(event.action)} the event '${event.title}': $reason. $CONTINUE"
        }
    }

    fun summary() = EventBatchSummary(outcomes.toList())

    /** Что уже записано в календарь в этом прогоне: при повторе модели это нужно показать, чтобы не было дублей. */
    fun handled(): List<EventProposal> = outcomes.filter { it.succeeded }.map { it.event }

    private fun successReply(event: EventProposal): String {
        val done = when (event.action) {
            EventAction.Add -> "added to"
            EventAction.Update -> "changed in"
            EventAction.Delete -> "deleted from"
        }
        return "The event '${event.title}' has been $done the calendar (${formatEventWhen(event)}). $CONTINUE"
    }

    private fun verb(action: EventAction): String = when (action) {
        EventAction.Add -> "add"
        EventAction.Update -> "change"
        EventAction.Delete -> "delete"
    }

    private companion object {
        val CLOCK = Regex("""(?<![\d:])\d{1,2}:\d{2}(?![\d:])""")
        val ALL_DAY_PHRASE = Regex("""весь день|целый день|на весь|all[ -]day""")

        /** Модель не должна останавливаться после первого события: в тексте их может быть несколько. */
        const val CONTINUE = "Now handle the next event from the text. If there are none left, reply with the single word: Done."
    }
}
