package io.github.the37thinsight.ytadskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class RulesTest {
    @Test fun bundledRulesParse() {
        assertNotNull(Rules.parse(File("src/main/assets/rules.json").readText()))
    }

    @Test fun blanksAreDropped() {
        val r = Rules.parse("""{"version":2,"adTexts":["", "  ", "Sponsrad"],"skipTexts":["Skip", 5]}""")!!
        assertEquals(listOf("Sponsrad"), r.adTexts)
        assertEquals(listOf("Skip"), r.skipTexts)
    }

    @Test fun unusableRulesAreRejected() {
        assertNull(Rules.parse("not json"))
        assertNull(Rules.parse("""{"adTexts":["Sponsrad"],"skipTexts":["Skip"]}""")) // no version
        assertNull(Rules.parse("""{"version":2,"adTexts":[""],"skipTexts":["Skip"]}"""))
        assertNull(Rules.parse("""{"version":2,"adTexts":["Sponsrad"],"skipTexts":[]}"""))
    }

    @Test fun timingsAreClamped() {
        val r = Rules.parse("""{"version":2,"adTexts":["A"],"skipTexts":["S"],"unmuteDelayMs":-5,"staleMs":999999}""")!!
        assertEquals(0L, r.unmuteDelayMs)
        assertEquals(60_000L, r.staleMs)
    }
}
