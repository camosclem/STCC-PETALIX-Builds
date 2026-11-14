package com.nuvio.tv.petalix

import org.junit.Assert.*
import org.junit.Test

class PetalixDailyViewingPolicyTest {
    @Test fun startupChoicesKeepHomeGuideAndLastChannelSeparate() {
        assertEquals("HOME", petalixStartupChoice("HOME", true))
        assertEquals("LIVE_TV", petalixStartupChoice("LIVE_TV", false))
        assertEquals("LAST_CHANNEL", petalixStartupChoice("LIVE_TV", true))
        assertEquals("SPORTS", petalixStartupChoice("SPORTS", true))
    }

    @Test fun guideRestoresOnlyAChannelInTheVisibleGroupWithoutStartingPlayback() {
        val keys = listOf("one", "two", "three")
        assertEquals(1, petalixGuideResumeRow(keys, null, "two", true, 0))
        assertEquals(2, petalixGuideResumeRow(keys, "three", "two", true, 0))
        assertEquals(0, petalixGuideResumeRow(keys, null, "two", false, 0))
        assertEquals(0, petalixGuideResumeRow(keys, null, "removed", true, 0))
        assertEquals(2, petalixGuideResumeRow(keys, null, null, true, 99))
        assertEquals(0, petalixGuideResumeRow(emptyList(), null, null, true, 99))
    }

    @Test fun manualRecoveryAppearsWhenRetriesAreOffOrExhausted() {
        assertTrue(petalixPlaybackNeedsRecovery(true, false, 1))
        assertTrue(petalixPlaybackNeedsRecovery(true, true, 9))
        assertFalse(petalixPlaybackNeedsRecovery(true, true, 8))
        assertFalse(petalixPlaybackNeedsRecovery(false, false, 9))
    }
}
