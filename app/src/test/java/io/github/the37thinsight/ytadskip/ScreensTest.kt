package io.github.the37thinsight.ytadskip

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreensTest {
    private val yt = "com.google.android.youtube"

    @Test fun minimisedYouTubeIsNotLeavingYouTube() {
        // Home screen in front, YouTube playing in the picture-in-picture window.
        assertEquals(Screen.YOUTUBE_PIP, Screens.classify("com.google.android.apps.nexuslauncher", youTubeVisible = true))
    }

    @Test fun otherCases() {
        assertEquals(Screen.YOUTUBE_ACTIVE, Screens.classify(yt, youTubeVisible = true))
        assertEquals(Screen.OTHER_APP, Screens.classify("com.whatsapp", youTubeVisible = false))
        assertEquals(Screen.UNREADABLE, Screens.classify(null, youTubeVisible = false))
        assertEquals(Screen.UNREADABLE, Screens.classify("com.android.systemui", youTubeVisible = false))
    }
}
