package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.data.M3uParser
import com.nuvio.tv.livetv.model.LiveChannel
import com.nuvio.tv.livetv.model.LiveUserState
import com.nuvio.tv.livetv.model.LiveTvSettings
import com.nuvio.tv.livetv.model.ChannelGroup
import com.nuvio.tv.livetv.ui.buildUi
import org.junit.Assert.*
import org.junit.Test

class PetalixGuideFeedPolicyTest {
    @Test fun hiddenGuideFeedRetainsItsStreamGuideAndName() {
        val text = """#EXTM3U
#EXTINF:-1 tvg-id="espn.us" group-title="Sports" petalix-guide-hidden="true",ESPN SD
https://example.test/live/user/pass/123.ts
"""
        val channel = M3uParser.parse(text.reader().buffered()).entries.single()
        assertTrue(channel.petalixGuideHidden)
        assertEquals("ESPN SD", channel.name)
        assertEquals("espn.us", channel.tvgId)
        assertEquals("https://example.test/live/user/pass/123.ts", channel.url)
        assertFalse(petalixShowGuideFeed(channel.petalixGuideHidden, false))
        assertTrue(petalixShowGuideFeed(channel.petalixGuideHidden, true))
    }

    @Test fun oldPlaylistsRemainVisibleByDefault() {
        val text = """#EXTM3U
#EXTINF:-1 tvg-id="espn.us" group-title="Sports",ESPN HD
https://example.test/live/user/pass/123.ts
"""
        val channel = M3uParser.parse(text.reader().buffered()).entries.single()
        assertFalse(channel.petalixGuideHidden)
        assertTrue(petalixShowGuideFeed(channel.petalixGuideHidden, false))
    }

    @Test fun guideAndPlayerListsStayCompactButSavedFavoritesRemainReachable() {
        val main = LiveChannel("main", "petalix-managed", "Petalix", "ESPN HD", "espn.us", null,
            null, "Sports", "Sports", 1, "https://example.test/1.ts")
        val backup = main.copy(key = "backup", name = "ESPN SD", number = 2,
            url = "https://example.test/2.ts", petalixGuideHidden = true)
        val channels = listOf(main, backup)
        val user = LiveUserState(favorites = listOf(backup.key))
        val all = buildUi(channels, user, LiveTvSettings(), ChannelGroup.ALL, "", true)
        assertEquals(listOf(main.key), all.channels.map { it.key })
        val favorites = buildUi(channels, user, LiveTvSettings(), ChannelGroup.FAVORITES, "", true)
        assertEquals(listOf(backup.key), favorites.channels.map { it.key })
        assertEquals(2, channels.size)
        assertEquals("https://example.test/2.ts", channels[1].url)
    }
}
