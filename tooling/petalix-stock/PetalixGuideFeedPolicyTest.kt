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
    @Test fun eventAndTeamCategoriesStayOutOfGuide() {
        for (group in listOf("NFL Games", "NFL Teams", "NBA Games", "NBA Teams", "MLB", "NHL",
                "College Football", "College Basketball", "NCAAF", "Soccer", "US | EPL", "Sports Events")) {
            assertTrue(group, hideSportsEventInGuide(group, "Feed 01"))
        }
        // A familiar network name must not resurrect an event category.
        assertTrue(hideSportsEventInGuide("NFL Games", "ESPN HD"))
    }

    @Test fun sportsGuideContainsNetworksRatherThanStreamingEventSlots() {
        for (name in listOf("Fubo US 01", "Disney+ USA", "Max US", "HBO Max US 2", "BTN+ 01",
                "ESPN+ 12", "Paramount+ USA", "Peacock 03", "NBA League Pass 02", "NFL Replay 01",
                "ESPN: Celtics vs Heat", "NBA TV 12", "Unrecognized Sports Feed 01")) {
            assertTrue(name, hideSportsEventInGuide("Sports", name))
        }
        for (name in listOf("US: ESPN HD", "ESPN2 FHD", "ESPN 1 USA HD", "ESPNU", "ESPNEWS", "ESPN News", "FS1", "FS2",
                "CBS Sports Network", "NFL Network", "NFL RedZone", "NBA TV", "MLB Network",
                "NHL Network", "Golf Channel", "Tennis Channel", "BTN", "SEC Network", "ACC Network",
                "FanDuel Sports Network South HD", "FanDuel 1", "FanDuel Sports Network 5", "TSN 1", "TSN5", "NBC Sports Philadelphia", "TNT")) {
            assertFalse(name, hideSportsEventInGuide("Sports Networks", name))
        }
    }

    @Test fun regularAnd247CategoriesRemainVisible() {
        for ((group, name) in listOf("Entertainment" to "AMC HD", "Kids & Family" to "Disney Channel",
                "Entertainment" to "HBO Max Comedy",
                "News" to "CBS News 24/7", "24/7 Movies" to "Max Action", "24/7 Sports" to "NFL Classics",
                "24/7 Drama" to "Vera", "Documentaries" to "BBC Earth")) {
            assertFalse("$group / $name", hideSportsEventInGuide(group, name))
        }
    }

    @Test fun managedEventsStayAvailableInThePoolButNotGuideSearchOrSavedGroups() {
        val main = LiveChannel("main", "petalix-managed", "Petalix", "ESPN HD", "espn.us", null,
            null, "Sports", "Sports", 1, "https://example.test/1.ts")
        val event = main.copy(key = "event", groupId = "nfl", group = "NFL Teams", name = "Carolina Panthers",
            url = "https://example.test/2.ts", petalixGuideHidden = false)
        val slot = main.copy(key = "slot", name = "BTN+ 01", url = "https://example.test/3.ts")
        val pool = listOf(main, event, slot)
        val user = LiveUserState(favorites = listOf(main.key, event.key))
        for (group in listOf(ChannelGroup.ALL, ChannelGroup.FAVORITES, ChannelGroup.SEARCH)) {
            val guide = buildUi(pool, user, LiveTvSettings(), group, "Panthers", true)
            assertFalse(guide.channels.any { it.key == event.key || it.key == slot.key })
            assertFalse(guide.groups.any { it.id == "nfl" })
        }
        assertEquals(listOf("main", "event", "slot"), pool.map { it.key })
        assertEquals("https://example.test/2.ts", pool[1].url)
        assertEquals(listOf("main", "event"), user.favorites)
    }

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
    @Test fun duplicateGuideCategorySelectionMergesChannelsButRetainsNativeLists() {
        val base = LiveChannel("z", "playlist", "Test", "Zulu", null, null, null,
            "one", "Entertainment", 1, "https://example.test/z.ts")
        val pool = listOf(base, base.copy(key = "a", name = "Alpha", groupId = "two", group = " entertainment "),
            base.copy(key = "stream", name = "Pluto Action", groupId = "stream", group = "Streaming Channels"))
        val settings = LiveTvSettings(channelSort = com.nuvio.tv.livetv.model.ChannelSort.NAME)
        val guide = buildUi(pool, LiveUserState(), settings, "two", "", true)
        assertEquals(listOf("a", "z"), guide.channels.map { it.key })
        assertEquals(1, guide.groups.count { it.title.trim().equals("Entertainment", true) })
        assertFalse(guide.groups.any { petalixStreamingCategory(it.title) })
        val favorites = buildUi(pool, LiveUserState(favorites = listOf("a")), settings, ChannelGroup.FAVORITES, "", true)
        assertEquals(listOf("a"), favorites.channels.map { it.key })
        assertEquals(3, pool.size)
    }

}
