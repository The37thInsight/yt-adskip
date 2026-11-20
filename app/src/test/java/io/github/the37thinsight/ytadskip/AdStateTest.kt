package io.github.the37thinsight.ytadskip

import io.github.the37thinsight.ytadskip.Action.CLICK_SKIP
import io.github.the37thinsight.ytadskip.Action.MUTE
import io.github.the37thinsight.ytadskip.Action.UNMUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AdStateTest {
    private val none = emptyList<Action>()

    @Test fun mutesThenUnmutesAfterDelay() {
        val s = AdState(unmuteDelayMs = 1000)
        assertEquals(listOf(MUTE), s.onScan(0, adVisible = true, skipVisible = false, streamAlreadyMuted = false))
        assertEquals(none, s.onScan(500, false, false, true))
        assertEquals(listOf(UNMUTE), s.onScan(1000, false, false, true))
    }

    @Test fun backToBackAdsStayMuted() {
        val s = AdState(unmuteDelayMs = 1000)
        s.onScan(0, true, false, false)
        assertEquals(none, s.onScan(300, false, false, true)) // gap between ad 1 and 2
        assertEquals(none, s.onScan(700, true, false, true))  // ad 2: no second MUTE
        assertEquals(none, s.onScan(1600, false, false, true))
        assertEquals(listOf(UNMUTE), s.onScan(1700, false, false, true))
    }

    @Test fun longAdStaysMuted() {
        val s = AdState()
        s.onScan(0, true, false, false)
        for (t in 500L..30 * 60_000L step 500) {
            assertFalse(UNMUTE in s.onScan(t, true, false, true))
        }
    }

    @Test fun leavesUserMuteAlone() {
        val s = AdState()
        assertEquals(none, s.onScan(0, true, false, streamAlreadyMuted = true))
        assertEquals(none, s.onScan(5000, false, false, true))
        assertEquals(none, s.onLeft())
    }

    @Test fun unreadableScreenUnmutesOnlyWhenStale() {
        val s = AdState(staleMs = 8000)
        s.onScan(0, true, false, false)
        assertEquals(none, s.onNoView(7999))
        assertEquals(listOf(UNMUTE), s.onNoView(8000))
    }

    @Test fun leavingYouTubeUnmutesAtOnce() {
        val s = AdState()
        s.onScan(0, true, false, false)
        assertEquals(listOf(UNMUTE), s.onLeft())
        assertEquals(none, s.onLeft())
    }

    @Test fun skipClicksAreThrottled() {
        val s = AdState()
        assertEquals(listOf(MUTE, CLICK_SKIP), s.onScan(0, false, true, false))
        assertEquals(none, s.onScan(500, false, true, true))
        assertEquals(listOf(CLICK_SKIP), s.onScan(1500, false, true, true))
    }
}
