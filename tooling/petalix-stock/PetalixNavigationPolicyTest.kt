package com.nuvio.tv.petalix

import org.junit.Assert.*
import org.junit.Test

class PetalixNavigationPolicyTest {
    @Test fun movieAndSeriesHeadersNeverMixContentTypes() {
        assertTrue(matchesPetalixBrowseType("movie", "movie"))
        assertFalse(matchesPetalixBrowseType("series", "movie"))
        assertTrue(matchesPetalixBrowseType("tv", "series"))
        assertTrue(matchesPetalixBrowseType("series", "series"))
        assertTrue(matchesPetalixBrowseType("movie", null))
    }
    @Test fun browseUsesPenguAndKeepsVodAsFallback() {
        assertEquals(listOf("Pengu"), preferPetalixRows(listOf("Pengu"), listOf("VOD")))
        assertEquals(listOf("VOD"), preferPetalixRows(emptyList(), listOf("VOD")))
        assertTrue(preferPetalixRows<String>(emptyList(), emptyList()).isEmpty())
    }
    @Test fun oneLocalGroupAfterZipPreservesOtherGroups() {
        val groups = listOf(PETALIX_ZIP_LOCALS to "My Locals", "provider:locals" to "Locals", "custom:old" to "Local Stations", "provider:sports" to "Sports Networks")
        assertEquals(listOf("My Locals", "Sports Networks"), groups.filter { showPetalixGuideGroup(it.first, it.second, true) }.map { it.second })
        assertEquals(groups, groups.filter { showPetalixGuideGroup(it.first, it.second, false) })
        assertTrue(showPetalixGuideGroup("provider:entertainment", "Localish Entertainment", true))
    }
    @Test fun playbackWaitsForPenguBeforeUsingOnlyTheProviderVodFallback() {
        val sources = listOf("Other addon", "VOD", "Pengu")
        for (ordered in listOf(sources, sources.reversed(), listOf("VOD", "Pengu", "Other addon"))) {
            assertEquals("Pengu", preferredPetalixPlayback(ordered, false, { it == "Pengu" }, { it == "VOD" }))
            assertEquals("Pengu", preferredPetalixPlayback(ordered, true, { it == "Pengu" }, { it == "VOD" }))
        }
        assertNull(preferredPetalixPlayback(listOf("VOD"), false, { it == "Pengu" }, { it == "VOD" }))
        assertEquals("VOD", preferredPetalixPlayback(listOf("Other addon", "VOD"), true, { it == "Pengu" }, { it == "VOD" }))
        assertNull(preferredPetalixPlayback(listOf("Other addon"), true, { it == "Pengu" }, { it == "VOD" }))
    }
}
