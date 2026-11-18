package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.LiveChannel
import org.junit.Assert.*
import org.junit.Test

class PetalixGuidePresentationTest {
    private fun channel(key: String, name: String, group: String = "Sports") = LiveChannel(
        key = key, sourceId = "playlist", sourceName = "Test", name = name,
        tvgId = null, tvgName = null, logo = null, groupId = group, group = group,
        number = 1, url = "https://example.invalid/$key"
    )
    @Test fun `measured bitrate wins over a UHD label without deleting the backup pool`() {
        val pool = listOf(channel("a", "ESPN UHD"), channel("b", "ESPN HD"))
        val view = petalixGuidePresentation(pool, mapOf("a" to 3_000_000L, "b" to 7_000_000L))
        assertEquals(listOf("b"), view.map { it.key })
        assertEquals(2, pool.size)
    }
    @Test fun `affiliates keep separate identities and linear streaming gets its own group`() {
        val locals = listOf(channel("a", "CBS (WBTV)", "Locals"), channel("b", "CBS (WCCO)", "Locals"))
        assertEquals(2, petalixGuidePresentation(locals, emptyMap()).size)
        val streaming = petalixGuidePresentation(listOf(channel("c", "Pluto TV Action", "Entertainment")), emptyMap())
        assertEquals("Streaming Channels", streaming.single().group)
    }
    @Test fun `quality suffixes disappear and golf channel moves to Sports`() {
        assertEquals("ESPN", petalixCleanChannelName("ESPN UHD"))
        assertEquals("FOX 46", petalixCleanChannelName("FOX 46 (HD)"))
        val view = petalixGuidePresentation(listOf(channel("g", "Golf Channel", "Golf")), emptyMap())
        assertEquals("Sports", view.single().group)
    }
    @Test fun sportsSlotsStayOutOfAllGuideViewsIncludingSavedFavorites() {
        for (group in listOf("Sports", "Sports Events", "Entertainment", "Streaming Services")) {
            for (name in listOf("BTN+ 01", "ESPN+ 1", "Fubo US 01", "Disney+ USA", "Max US")) {
                val slot = channel("slot", name, group)
                assertFalse("$group / $name", petalixGuideChannelAllowed(slot, false))
                assertFalse("saved $group / $name", petalixGuideChannelAllowed(slot, true))
            }
        }
        assertTrue(petalixGuideChannelAllowed(channel("network", "ESPN HD"), false))
    }
    @Test fun plusAndNonPlusAreDifferentChannelsAnd247KeepsItsGroup() {
        val pool = listOf(channel("one", "ESPN 1"), channel("plus", "ESPN+ 1"))
        assertEquals(2, petalixGuidePresentation(pool, emptyMap()).size)
        val aroundClock = channel("247", "Max Action", "24/7 Movies")
        assertEquals("24/7 Movies", petalixGuidePresentation(listOf(aroundClock), emptyMap()).single().group)
        assertTrue(petalixGuideChannelAllowed(aroundClock, false))
    }

    @Test fun cachedPolicyReusesWorkAndInvalidatesForDatasetSavedChoiceAndBitrate() {
        val cache = PetalixGuideCache()
        val pool = listOf(channel("a", "ESPN HD"), channel("b", "ESPN UHD"))
        val filtered = cache.channels(pool, emptySet())
        assertSame(filtered, cache.channels(pool, emptySet()))
        val rates = mapOf("a" to 7_000_000L, "b" to 3_000_000L)
        val first = cache.presentation(filtered, rates)
        assertSame(first, cache.presentation(filtered.toList(), rates))
        assertEquals("a", first.single().key)
        assertEquals("b", cache.presentation(filtered, mapOf("b" to 9_000_000L)).single().key)
        val hidden = channel("hidden", "CNN", "News").copy(petalixGuideHidden = true)
        val updated = pool + hidden
        assertFalse(cache.channels(updated, emptySet()).any { it.key == "hidden" })
        assertTrue(cache.channels(updated, setOf("hidden")).any { it.key == "hidden" })
        assertEquals(3, updated.size)
        assertNotSame(filtered, cache.channels(pool.toList(), emptySet()))
    }

}
