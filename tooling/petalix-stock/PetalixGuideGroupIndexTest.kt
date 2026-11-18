package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.ChannelGroup
import com.nuvio.tv.livetv.model.LiveChannel
import org.junit.Assert.*
import org.junit.Test

class PetalixGuideGroupIndexTest {
    private fun channel(key: String) = LiveChannel(key = key, sourceId = "source", sourceName = "Test",
        name = key, tvgId = null, tvgName = null, logo = null, groupId = "sports", group = "Sports", number = 1, url = "https://example.invalid/$key")
    @Test fun duplicateNamesCombineMembershipWithoutDeletingSourcesOrSavedIds() {
        val originals = listOf(ChannelGroup("one", "Sports", 2), ChannelGroup("two", " sports ", 2))
        val pool = mapOf("one" to listOf(channel("a"), channel("b")), "two" to listOf(channel("b"), channel("c")))
        val index = PetalixGuideGroupIndex(originals) { pool[it].orEmpty() }
        assertEquals(1, index.groups.size)
        assertEquals(3, index.groups.single().count)
        assertEquals("one", index.canonicalId("two"))
        assertEquals(listOf("one", "two"), index.idsFor("one"))
        assertEquals(2, originals.size)
    }
    @Test fun streamingCategoriesDisappearAndSpecialListsKeepNativeIds() {
        val groups = listOf(ChannelGroup("old", "Streaming Channels", 3), ChannelGroup("old2", "US | STREAMING SERVICES", 2),
            ChannelGroup(ChannelGroup.FAVORITES, "Favorites", 0, special = true),
            ChannelGroup(ChannelGroup.RECENT, "Recently Watched", 0, special = true))
        val index = PetalixGuideGroupIndex(groups) { emptyList() }
        assertEquals(listOf(ChannelGroup.FAVORITES, ChannelGroup.RECENT), index.groups.map { it.id })
        assertEquals(ChannelGroup.FAVORITES, petalixNativeHomeGroup("native:favorites", index.groups))
        assertEquals(ChannelGroup.RECENT, petalixNativeHomeGroup("native:recent", index.groups))
        assertNull(petalixNativeHomeGroup("Sports", index.groups))
    }
    @Test fun lockedCopiesStaySubjectToNativeMembershipAndDistinctLocalNamesSurvive() {
        val groups = listOf(ChannelGroup("locked", "News", 2, locked = true), ChannelGroup("open", "News", 1),
            ChannelGroup("nc", "NC Locals", 1), ChannelGroup("sc", "SC Locals", 1))
        val index = PetalixGuideGroupIndex(groups) { if (it == "locked") emptyList() else listOf(channel(it)) }
        assertEquals("open", index.canonicalId("locked"))
        assertEquals(1, index.groups.first().count)
        assertEquals(3, index.groups.size)
        assertEquals("open", petalixNativeHomeGroup("News", index.groups))
    }
}
