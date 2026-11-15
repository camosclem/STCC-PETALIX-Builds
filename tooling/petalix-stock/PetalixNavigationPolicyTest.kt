package com.nuvio.tv.petalix

import org.junit.Assert.*
import org.junit.Test

class PetalixNavigationPolicyTest {
    @Test fun oneLocalGroupAfterZipPreservesOtherGroups() {
        val groups = listOf(PETALIX_ZIP_LOCALS to "My Locals", "provider:locals" to "Locals", "custom:old" to "Local Stations", "provider:sports" to "Sports Networks")
        assertEquals(listOf("My Locals", "Sports Networks"), groups.filter { showPetalixGuideGroup(it.first, it.second, true) }.map { it.second })
        assertEquals(groups, groups.filter { showPetalixGuideGroup(it.first, it.second, false) })
        assertTrue(showPetalixGuideGroup("provider:entertainment", "Localish Entertainment", true))
    }
}
