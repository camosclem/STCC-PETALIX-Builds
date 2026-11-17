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
    @Test fun `affiliates keep separate identities and ESPN plus gets the streaming group`() {
        val locals = listOf(channel("a", "CBS (WBTV)", "Locals"), channel("b", "CBS (WCCO)", "Locals"))
        assertEquals(2, petalixGuidePresentation(locals, emptyMap()).size)
        val streaming = petalixGuidePresentation(listOf(channel("c", "ESPN+ 1")), emptyMap())
        assertEquals("Streaming Channels", streaming.single().group)
    }
    @Test fun `quality suffixes disappear and golf channel moves to Sports`() {
        assertEquals("ESPN", petalixCleanChannelName("ESPN UHD"))
        assertEquals("FOX 46", petalixCleanChannelName("FOX 46 (HD)"))
        val view = petalixGuidePresentation(listOf(channel("g", "Golf Channel", "Golf")), emptyMap())
        assertEquals("Sports", view.single().group)
    }
}
