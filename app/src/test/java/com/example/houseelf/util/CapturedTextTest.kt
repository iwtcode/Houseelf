package com.example.houseelf.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturedTextTest {

    @Test
    fun blankTextIsNothing() {
        assertNull(normalizeCapturedText("  \r\n\t ", maxChars = 100))
    }

    @Test
    fun trimsEdgesAndUnifiesLineBreaks() {
        val result = normalizeCapturedText("\n  встреча\r\nв 15:00  \n", maxChars = 100)!!
        assertEquals("встреча\nв 15:00", result.text)
        assertFalse(result.truncated)
    }

    @Test
    fun cutsLongTextAndSaysSo() {
        val result = normalizeCapturedText("a".repeat(50), maxChars = 10)!!
        assertEquals("a".repeat(10), result.text)
        assertTrue(result.truncated)
    }

    @Test
    fun doesNotSplitSurrogatePair() {
        // «😀» занимает два символа: граница лимита приходится ровно посередине
        val result = normalizeCapturedText("ab😀cd", maxChars = 3)!!
        assertEquals("ab", result.text)
        assertTrue(result.truncated)
    }
}
