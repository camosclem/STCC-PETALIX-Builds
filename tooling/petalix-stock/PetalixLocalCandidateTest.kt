package com.nuvio.tv.petalix
import org.junit.Assert.*
import org.junit.Test
class PetalixLocalCandidateTest {
    @Test fun teamAndEventCopiesAreNotLocals() {
        assertFalse(petalixLocalStationCandidate("NFL Teams: CBS Panthers (WBTV) Charlotte NC", "NFL Teams"))
        assertFalse(petalixLocalStationCandidate("CBS WBTV Charlotte", "Sports Events"))
        assertFalse(petalixLocalStationCandidate("NFL Teams: CBS Panthers (WBTV)", "Locals"))
        assertTrue(petalixLocalStationCandidate("NC | Charlotte | CBS WBTV", "USA Locals"))
        assertTrue(petalixLocalStationCandidate("NC | Belmont | FOX WJZY", "Local Stations"))
    }
}
