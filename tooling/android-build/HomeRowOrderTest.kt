package com.nuvio.tv.petalix

import org.junit.Assert.*
import org.junit.Test

class HomeRowOrderTest {
    @Test fun servicesPrecedeNativeRowsWithoutRemovingThem() {
        val rows = listOf("continue_watching", "upcoming_section", STREAMING_SERVICES_ROW, "movies", "series")
        assertEquals(listOf(STREAMING_SERVICES_ROW, "continue_watching", "upcoming_section", "movies", "series"), streamingServicesFirst(rows) { it })
    }
    @Test fun otherCustomersKeepTheirNativeOrder() {
        val rows = listOf("continue_watching", "collection_something-else", "movies")
        assertSame(rows, streamingServicesFirst(rows) { it })
    }
    @Test fun aServiceOnlyHomeKeepsLiveTvAfterTheTiles() {
        for (above in listOf(true, false)) {
            assertFalse(liveTvBeforeRow(true, above, false, STREAMING_SERVICES_ROW))
            assertTrue(liveTvAfterRow(true, above, false, STREAMING_SERVICES_ROW))
        }
    }
    @Test fun nativeLiveTvPlacementIsUnchangedElsewhere() {
        for (here in listOf(true, false)) for (above in listOf(true, false)) for (cw in listOf(true, false)) {
            assertEquals(here && (above || !cw), liveTvBeforeRow(here, above, cw, "movies"))
            assertEquals(here && !above && cw, liveTvAfterRow(here, above, cw, "continue_watching"))
        }
    }
    @Test fun upDoesNotRevealALiveRowAboveServices() {
        assertFalse(liveTvAboveFirstRow(true, true, STREAMING_SERVICES_ROW))
        assertTrue(liveTvAboveFirstRow(true, true, "continue_watching"))
    }
    @Test fun noHiddenOrDuplicateNativeRowsAreCreated() {
        val rows = listOf(STREAMING_SERVICES_ROW, "continue_watching", "movies")
        val next = streamingServicesFirst(rows) { it }
        assertEquals(rows, next)
        assertEquals(rows.size, next.size)
        assertFalse(liveTvAfterRow(false, false, false, STREAMING_SERVICES_ROW))
    }
}
