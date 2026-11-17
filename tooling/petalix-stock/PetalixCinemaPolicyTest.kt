package com.nuvio.tv.petalix

import org.junit.Assert.*
import org.junit.Test

class PetalixCinemaPolicyTest {
    @Test fun `preview stops when viewer leaves Home or moves focus`() {
        assertTrue(PetalixCinemaPolicy.canPreview(true, true, false, false, false))
        assertFalse(PetalixCinemaPolicy.canPreview(true, false, false, false, false))
        assertFalse(PetalixCinemaPolicy.canPreview(true, true, true, false, false))
        assertFalse(PetalixCinemaPolicy.canPreview(true, true, false, true, false))
        assertFalse(PetalixCinemaPolicy.canPreview(true, true, false, false, true))
        assertFalse(PetalixCinemaPolicy.canPreview(false, true, false, false, false))
    }
    @Test fun `landscape shelves fit common TV resolutions without tiny cards`() {
        for (width in listOf(960, 1280, 1920)) {
            val card = PetalixCinemaPolicy.landscapeWidthDp(width)
            assertTrue(card >= 224 && card <= 320)
            assertTrue(card * 2 + 104 < width)
        }
    }
    @Test fun `service and live shortcuts never pretend to play a movie`() {
        assertEquals("Explore collection", PetalixCinemaPolicy.actionLabel(true, false, false, false, false))
        assertEquals("Open guide", PetalixCinemaPolicy.actionLabel(false, true, false, false, false))
        assertEquals("Watch channel", PetalixCinemaPolicy.actionLabel(false, false, true, false, false))
        assertEquals("Resume watching", PetalixCinemaPolicy.actionLabel(false, false, false, true, true))
        assertEquals("Episodes & details", PetalixCinemaPolicy.actionLabel(false, false, false, false, true))
    }
}
