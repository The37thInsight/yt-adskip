package io.github.the37thinsight.ytadskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceSwitchTest {
    private val me = "io.github.the37thinsight.ytadskip/io.github.the37thinsight.ytadskip.AdSkipService"
    private val other = "com.example/com.example.Svc"

    @Test fun turningOnKeepsOtherServices() {
        assertEquals("$other:$me", ServiceSwitch.withService("$other", me, on = true))
        assertEquals(me, ServiceSwitch.withService(null, me, on = true))
        assertEquals(me, ServiceSwitch.withService("", me, on = true))
        assertEquals("$other:$me", ServiceSwitch.withService("$other:$me", me, on = true))
    }

    @Test fun turningOffRemovesOnlyThisService() {
        assertEquals(other, ServiceSwitch.withService("$me:$other", me, on = false))
        assertEquals("", ServiceSwitch.withService(me, me, on = false))
    }

    @Test fun isOnMatchesWholeEntries() {
        assertTrue(ServiceSwitch.contains("$other:$me", me))
        assertFalse(ServiceSwitch.contains("${me}2", me))
        assertFalse(ServiceSwitch.contains(null, me))
    }
}
