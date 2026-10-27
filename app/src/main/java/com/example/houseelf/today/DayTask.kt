package com.example.houseelf.today

import java.time.LocalDate
import java.time.LocalDateTime

/** Дело дня — событие из календаря телефона. */
data class DayTask(
    /** Стабильный ключ: событие + день (повторяющееся событие даёт разные ключи в разные дни). */
    val key: String,
    val eventId: Long,
    val title: String,
    val date: LocalDate,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String?,
    val description: String?,
    /** Шаги дела по порядку; пусто, если дело не разбивали. */
    val steps: List<String> = emptyList(),
)
