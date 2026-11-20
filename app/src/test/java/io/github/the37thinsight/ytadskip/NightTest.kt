package io.github.the37thinsight.ytadskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightTest {
    private val start = Night.parse("22:00")!!
    private val end = Night.parse("06:30")!!

    @Test fun parse() {
        assertEquals(22 * 60, start)
        assertEquals(6 * 60 + 30, end)
        assertEquals(7 * 60 + 5, Night.parse(" 7:05 "))
        assertNull(Night.parse("24:00"))
        assertNull(Night.parse("22.00"))
        assertNull(Night.parse(""))
    }

    @Test fun windowAcrossMidnight() {
        assertTrue(Night.isNight(22 * 60, start, end))
        assertTrue(Night.isNight(23 * 60 + 59, start, end))
        assertTrue(Night.isNight(0, start, end))
        assertTrue(Night.isNight(6 * 60 + 29, start, end))
        assertFalse(Night.isNight(6 * 60 + 30, start, end))
        assertFalse(Night.isNight(12 * 60, start, end))
        assertFalse(Night.isNight(21 * 60 + 59, start, end))
    }

    @Test fun sameDayWindowAndOff() {
        assertTrue(Night.isNight(13 * 60, 12 * 60, 14 * 60))
        assertFalse(Night.isNight(15 * 60, 12 * 60, 14 * 60))
        assertFalse(Night.isNight(0, 600, 600))
    }

    @Test fun duckLevelNeverSilent() {
        assertEquals(2, Night.duckLevel(25, 8))
        assertEquals(1, Night.duckLevel(15, 5))
        assertEquals(1, Night.duckLevel(25, 0))
    }
}
