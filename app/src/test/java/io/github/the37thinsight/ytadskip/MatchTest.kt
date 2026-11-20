package io.github.the37thinsight.ytadskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchTest {
    @Test fun wordBoundary() {
        assertTrue(Match.text("Hoppa över annonser", listOf("Hoppa över")))
        assertTrue(Match.text("  Sponsrad · 0:15 ", listOf("Sponsrad")))
        assertTrue(Match.text("SKIP", listOf("Skip")))
        assertFalse(Match.text("Advanced", listOf("Ad")))
        assertFalse(Match.text("Annonsen var bra", listOf("Annons")))
        assertFalse(Match.text("Så hoppar du över", listOf("Hoppa över")))
        assertFalse(Match.text(null, listOf("Skip")))
    }

    @Test fun viewIds() {
        assertEquals("com.google.android.youtube:id/skip_ad_button", Match.fullViewId("com.google.android.youtube", "skip_ad_button"))
        assertEquals("x:id/y", Match.fullViewId("com.google.android.youtube", "x:id/y"))
    }
}
