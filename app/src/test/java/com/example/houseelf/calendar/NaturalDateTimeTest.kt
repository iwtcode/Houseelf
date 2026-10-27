package com.example.houseelf.calendar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Дни недели, «послезавтра», «через неделю», время словами: считает код, а не модель. */
class NaturalDateTimeTest {

    /** Среда, 28 октября 2026, 01:15 — как на скриншоте с ошибкой. */
    private val now = LocalDateTime.of(2026, 10, 28, 1, 15)

    private fun check(date: String?, time: String?, expectedDate: LocalDate?, expectedTime: LocalTime?) {
        val r = NaturalDateTime.resolve(date, time, now)
        assertEquals("date for ($date; $time)", expectedDate, r.date)
        assertEquals("time for ($date; $time)", expectedTime, r.time)
    }

    @Test
    fun resolvesNaturalPhrases() {
        check("завтра", "12 часов", LocalDate.of(2026, 10, 29), LocalTime.of(12, 0))
        check("завтра в 12 часов", null, LocalDate.of(2026, 10, 29), LocalTime.of(12, 0))
        check("послезавтра", "18:30", LocalDate.of(2026, 10, 30), LocalTime.of(18, 30))
        check("после завтра", null, LocalDate.of(2026, 10, 30), null)
        check("сегодня", "в 6 вечера", LocalDate.of(2026, 10, 28), LocalTime.of(18, 0))
        check("в субботу", "10", LocalDate.of(2026, 10, 31), LocalTime.of(10, 0))
        check("в субботу", null, LocalDate.of(2026, 10, 31), null)
        check("в среду", null, LocalDate.of(2026, 11, 4), null)  // today is Wed -> next Wed
        check("в следующий вторник", null, LocalDate.of(2026, 11, 3), null)
        check("в следующую субботу", null, LocalDate.of(2026, 11, 7), null)
        check("на следующей неделе в понедельник", "9 утра", LocalDate.of(2026, 11, 2), LocalTime.of(9, 0))
        check("в эту пятницу", "15:00", LocalDate.of(2026, 10, 30), LocalTime.of(15, 0))
        check("в эту среду", null, LocalDate.of(2026, 10, 28), null)
        check("в понедельник", null, LocalDate.of(2026, 11, 2), null)
        check("в воскресенье", "в 7 утра", LocalDate.of(2026, 11, 1), LocalTime.of(7, 0))
        check("через неделю", null, LocalDate.of(2026, 11, 4), null)
        check("через 2 недели", "14:00", LocalDate.of(2026, 11, 11), LocalTime.of(14, 0))
        check("через 3 дня", null, LocalDate.of(2026, 10, 31), null)
        check("через пару дней", "в 5", LocalDate.of(2026, 10, 30), LocalTime.of(5, 0))
        check("через месяц", null, LocalDate.of(2026, 11, 28), null)
        check("через 3 дня", "в 5 часов вечера", LocalDate.of(2026, 10, 31), LocalTime.of(17, 0))
        check("через 2 часа", null, LocalDate.of(2026, 10, 28), LocalTime.of(3, 15))
        check(null, "через полчаса", LocalDate.of(2026, 10, 28), LocalTime.of(1, 45))
        check("25 октября", null, LocalDate.of(2027, 10, 25), null)
        check("5 ноября", "в 19:00", LocalDate.of(2026, 11, 5), LocalTime.of(19, 0))
        check("5 ноября 2026", null, LocalDate.of(2026, 11, 5), null)
        check("25.12", null, LocalDate.of(2026, 12, 25), null)
        check("1.11.2026", "9:05", LocalDate.of(2026, 11, 1), LocalTime.of(9, 5))
        check("2026-11-02", null, LocalDate.of(2026, 11, 2), null)
        check("2026-11-02T15:00", null, LocalDate.of(2026, 11, 2), LocalTime.of(15, 0))
        check("15 числа", null, LocalDate.of(2026, 11, 15), null)
        check("28 числа", null, LocalDate.of(2026, 10, 28), null)
        check("завтра", "половина седьмого", LocalDate.of(2026, 10, 29), LocalTime.of(6, 30))
        check("завтра", "половина седьмого вечера", LocalDate.of(2026, 10, 29), LocalTime.of(18, 30))
        check("завтра", "без пятнадцати семь вечера", LocalDate.of(2026, 10, 29), LocalTime.of(18, 45))
        check("завтра", "в три часа дня", LocalDate.of(2026, 10, 29), LocalTime.of(15, 0))
        check("завтра", "в 2 ночи", LocalDate.of(2026, 10, 29), LocalTime.of(2, 0))
        check("завтра", "в 12 ночи", LocalDate.of(2026, 10, 29), LocalTime.of(0, 0))
        check("завтра", "в 12 дня", LocalDate.of(2026, 10, 29), LocalTime.of(12, 0))
        check("завтра", "в полдень", LocalDate.of(2026, 10, 29), LocalTime.of(12, 0))
        check("завтра", "18.30", LocalDate.of(2026, 10, 29), LocalTime.of(18, 30))
        check("завтра", "в 5 часов 30 минут", LocalDate.of(2026, 10, 29), LocalTime.of(5, 30))
        check("завтра", "в шесть вечера", LocalDate.of(2026, 10, 29), LocalTime.of(18, 0))
        check("завтра вечером", null, LocalDate.of(2026, 10, 29), LocalTime.of(19, 0))
        check("завтра вечером в 6", null, LocalDate.of(2026, 10, 29), LocalTime.of(18, 0))
        check("завтра утром", null, LocalDate.of(2026, 10, 29), LocalTime.of(9, 0))
        check("в пятницу", "в 18:00", LocalDate.of(2026, 10, 30), LocalTime.of(18, 0))
        check("tomorrow", "6 pm", LocalDate.of(2026, 10, 29), LocalTime.of(18, 0))
        check("next monday", null, LocalDate.of(2026, 11, 2), null)
        check("saturday", "at 10:00", LocalDate.of(2026, 10, 31), LocalTime.of(10, 0))
        check("in 2 days", null, LocalDate.of(2026, 10, 30), null)
        check("day after tomorrow", null, LocalDate.of(2026, 10, 30), null)
        check("на следующей неделе", null, LocalDate.of(2026, 11, 2), null)
        check("в следующем месяце", null, LocalDate.of(2026, 11, 28), null)
        check("среди недели", null, null, null)  // must NOT become Wednesday
        check("в 5 октября", null, LocalDate.of(2027, 10, 5), null)  // month word, not hour 5
        check("завтра", "12", LocalDate.of(2026, 10, 29), LocalTime.of(12, 0))
        check(null, "в 8:00", null, LocalTime.of(8, 0))
        check("вторник", "вечером", LocalDate.of(2026, 11, 3), LocalTime.of(19, 0))
        check("пн", null, LocalDate.of(2026, 11, 2), null)
    }

    @Test
    fun weekdayIsAlwaysAfterToday() {
        // 28.10.2026 — среда: «в среду» — это следующая среда, а не сегодня
        assertEquals(LocalDate.of(2026, 11, 4), NaturalDateTime.parseDate("в среду", now.toLocalDate()))
        for (offset in 0L..13L) {
            val today = LocalDate.of(2026, 10, 1).plusDays(offset)
            val result = NaturalDateTime.parseDate("в пятницу", today)!!
            assertEquals(java.time.DayOfWeek.FRIDAY, result.dayOfWeek)
            assertTrue(result.isAfter(today))
            assertTrue(result.isBefore(today.plusDays(8)))
        }
    }

    @Test
    fun nextWeekdayIsInNextCalendarWeek() {
        for (offset in 0L..13L) {
            val today = LocalDate.of(2026, 10, 1).plusDays(offset)
            val result = NaturalDateTime.parseDate("в следующий вторник", today)!!
            assertEquals(java.time.DayOfWeek.TUESDAY, result.dayOfWeek)
            val mondayNext = today.with(java.time.DayOfWeek.MONDAY).plusWeeks(1)
            assertEquals(mondayNext.plusDays(1), result)
        }
    }

    @Test
    fun unreadableTextIsReportedNotGuessed() {
        val r = NaturalDateTime.resolve("на днях", null, now)
        assertTrue(r.dateProblem)
        assertEquals(null, r.date)
        val t = NaturalDateTime.resolve("завтра", "когда-нибудь", now)
        assertTrue(t.timeProblem)
        assertFalse(t.dateProblem)
    }
}
