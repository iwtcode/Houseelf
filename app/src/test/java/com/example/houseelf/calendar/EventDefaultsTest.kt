package com.example.houseelf.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EventDefaultsTest {

    @Test
    fun untitledEventsAreNumberedInOrder() {
        val defaults = EventDefaults(LocalDate.of(2026, 10, 26))
        assertEquals("Событие 1", defaults.nextTitle())
        assertEquals("Событие 2", defaults.nextTitle())
        assertEquals("Событие 3", defaults.nextTitle())
    }

    @Test
    fun eventWithoutDateStartsTodayAtMidnight() {
        val defaults = EventDefaults(LocalDate.of(2026, 10, 26))
        assertEquals(LocalDate.of(2026, 10, 26).atStartOfDay(), defaults.start())
    }
}
