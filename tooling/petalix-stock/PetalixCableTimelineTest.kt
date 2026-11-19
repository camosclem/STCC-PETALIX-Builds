package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.EpgProgram
import com.nuvio.tv.livetv.ui.PETALIX_HALF_HOUR
import com.nuvio.tv.livetv.ui.PetalixAirtimes
import com.nuvio.tv.livetv.ui.petalixWindowFor
import org.junit.Assert.*
import org.junit.Test

class PetalixCableTimelineTest {
    private val half = PETALIX_HALF_HOUR

    @Test fun remoteStepsFollowTheSameCellsThatAreOnScreen() {
        val first = EpgProgram(0, half, "News")
        val later = EpgProgram(3 * half, 4 * half, "Sports")
        val line = PetalixAirtimes(listOf(later, first))
        val visible = line.visible(1000, 4 * half)
        assertEquals(listOf("News", null, "Sports"), visible.map { it.program?.title })
        assertEquals(visible[1], line.at(2 * half))
        assertEquals(visible[2], line.at(line.step(half, 1, 0, 4 * half)))
        assertEquals(visible[0], line.at(line.step(2 * half, -1, 0, 4 * half)))
    }

    @Test fun invalidAndOverlappingListingsKeepOneActionPerInstant() {
        val earlier = EpgProgram(20, 80, "Early")
        val later = EpgProgram(60, 100, "Late")
        val line = PetalixAirtimes(listOf(later, EpgProgram(5, 3, "Invalid"), earlier))
        assertEquals(earlier, line.at(79).program)
        assertEquals(later, line.at(80).program)
        assertEquals(80L, line.at(90).start)
        assertNull(line.at(100).program)
    }

    @Test fun emptyScheduleHasFocusableCellsAndStopsAtArchiveLimits() {
        val line = PetalixAirtimes(emptyList())
        assertEquals(4, line.visible(0, 4 * half).size)
        assertEquals(half, line.step(half, -4, half, 10 * half))
        assertEquals(3 * half, line.step(2 * half, 4, 0, 3 * half))
        assertTrue(line.at(-1).contains(-1))
    }

    @Test fun longProgramsScrollDirectlyToTheSelectedClockTime() {
        val start = 48 * half
        assertEquals(start, petalixWindowFor(start + 4 * half - 1, start, 4 * half))
        assertEquals(start + half, petalixWindowFor(start + 4 * half, start, 4 * half))
        assertEquals(start - half, petalixWindowFor(start - 1, start, 4 * half))
    }
}
