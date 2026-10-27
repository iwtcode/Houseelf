package com.example.houseelf.calendar

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Что подставить в событие, если в тексте не хватило данных. Работает и в Домовом.Событие, и в чате:
 * модель не переспрашивает, а вносит событие со значениями по умолчанию, пользователь сам поправит его.
 *
 *  - нет названия — «Событие 1», «Событие 2» и так далее;
 *  - нет даты (или она не читается) — сегодня;
 *  - нет времени — событие на весь день.
 */
class EventDefaults(private val today: () -> LocalDate = { LocalDate.now() }) {

    private val untitledCount = AtomicInteger(0)

    /** Название для события без названия. Нумерация сквозная в пределах одного прогона. */
    fun nextTitle(): String = "$UNTITLED_PREFIX ${untitledCount.incrementAndGet()}"

    /** Новый разговор — нумерация безымянных событий начинается заново. */
    fun reset() = untitledCount.set(0)

    /** Начало события без даты: сегодня, без времени — значит, на весь день. Дату берём в момент вызова. */
    fun start(): LocalDateTime = today().atStartOfDay()

    private companion object {
        const val UNTITLED_PREFIX = "Событие"
    }
}
