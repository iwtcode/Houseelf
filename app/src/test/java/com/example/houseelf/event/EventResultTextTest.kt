package com.example.houseelf.event

import com.example.houseelf.calendar.EventAction
import com.example.houseelf.calendar.EventProposal
import org.junit.Assert.assertEquals
import java.time.LocalDate
import org.junit.Test

class EventResultTextTest {

    private fun outcome(action: EventAction, error: String? = null) = EventOutcome(
        event = EventProposal(
            id = 1, title = "Событие 1",
            start = LocalDate.of(2026, 10, 26).atStartOfDay(), end = LocalDate.of(2026, 10, 26).atStartOfDay(),
            allDay = true, location = null, description = null, action = action,
        ),
        error = error,
    )

    @Test
    fun emptyRunSaysNothingWasFound() {
        assertEquals(EventResultText.NOTHING_FOUND, EventResultText.headline(EventBatchSummary(emptyList())))
    }

    @Test
    fun headlineCountsEachKindOfAction() {
        val summary = EventBatchSummary(
            listOf(
                outcome(EventAction.Add), outcome(EventAction.Add),
                outcome(EventAction.Update), outcome(EventAction.Delete),
            ),
        )
        assertEquals("Добавлено: 2 · Изменено: 1 · Удалено: 1", EventResultText.headline(summary))
    }

    @Test
    fun failureDoesNotHideSuccesses() {
        val summary = EventBatchSummary(listOf(outcome(EventAction.Add), outcome(EventAction.Update, error = "нет доступа")))
        assertEquals("Добавлено: 1 · Не удалось: 1", EventResultText.headline(summary))
    }
}
