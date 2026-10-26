package com.example.houseelf.calendar

import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
