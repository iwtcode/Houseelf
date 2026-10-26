package com.example.houseelf.event

import com.example.houseelf.calendar.EventAction
import com.example.houseelf.calendar.formatEventWhen

/** Слова для уведомления: что сделано с календарём. Чистые функции — без Android. */
internal object EventResultText {

    const val NOTHING_FOUND = "В тексте не нашлось событий"

    /** Короткая строка для самого уведомления: «Добавлено: 2 · Удалено: 1». */
    fun headline(summary: EventBatchSummary): String {
        if (summary.isEmpty) return NOTHING_FOUND
        val parts = buildList {
            addCount("Добавлено", summary.count(EventAction.Add))
            addCount("Изменено", summary.count(EventAction.Update))
            addCount("Удалено", summary.count(EventAction.Delete))
            addCount("Не удалось", summary.failed.size)
        }
        return parts.joinToString(" · ")
    }

    /** По строке на каждое событие — для раскрытого уведомления. */
    fun details(summary: EventBatchSummary): List<String> = summary.outcomes.map { line(it) }

    private fun MutableList<String>.addCount(label: String, count: Int) {
        if (count > 0) add("$label: $count")
    }

    private fun line(outcome: EventOutcome): String {
        val event = outcome.event
        return if (outcome.succeeded) {
            "${pastTense(event.action)}: ${event.title} — ${formatEventWhen(event)}"
        } else {
            "Не удалось: ${event.title} — ${outcome.error}"
        }
    }

    private fun pastTense(action: EventAction): String = when (action) {
        EventAction.Add -> "Добавлено"
        EventAction.Update -> "Изменено"
        EventAction.Delete -> "Удалено"
    }
}
