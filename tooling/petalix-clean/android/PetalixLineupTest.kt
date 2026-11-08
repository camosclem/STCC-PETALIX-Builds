package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.model.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetalixLineupTest {
    private fun channel(
        name: String,
        group: String,
        key: String = name,
        id: String = name,
        sourceId: String = "test-source",
        url: String = "https://example.invalid/live/stream.ts"
    ) = LiveChannel(
        key = key,
        sourceId = sourceId,
        sourceName = "My IPTV",
        name = name,
        tvgId = id,
        tvgName = null,
        logo = "https://example.invalid/logo.png",
        groupId = group,
        group = group,
        number = 17,
        url = url
    )

    @Test fun oneSportsGroupIncludesAllTypes() {
        for (name in listOf(
            "ESPN 4K", "FS1 HD", "FanDuel Sports Network 1 HD",
            "TSN 3 FHD", "Carolina Panthers NFL", "NASCAR Live",
            "NBA League Pass", "UFC Fight"
        )) {
            assertEquals("Sports", PetalixLineup.category("USA | Sports", name))
        }
        assertEquals("Locals", PetalixLineup.category("USA | Locals", "WCNC Charlotte"))
        assertEquals("24/7 | TV & Series", PetalixLineup.category("USA | 24/7 | Drama", "24/7 Mayday"))
    }

    @Test fun sportsPriorityMajorFirstThenFanduelTsnOneToFiveThenRest() {
        val source = listOf(
            channel("NFL Sunday Ticket 4K", "USA | Sports"),
            channel("TSN 5 FHD", "Canada | Sports"),
            channel("FanDuel Sports Network 4 HD", "USA | Sports"),
            channel("ESPN HD", "USA | Sports"),
            channel("TSN 1 FHD", "Canada | Sports"),
            channel("FANDUEL 1 UHD", "USA | Sports"),
            channel("ESPN2 HD", "USA | Sports"),
            channel("FanDuel 5 FHD", "USA | Sports"),
            channel("TSN 3 HD", "Canada | Sports"),
            channel("NASCAR 4K", "USA | Sports")
        )
        val result = PetalixLineup.organize(source, false)
        assertTrue(result.all { it.group == "Sports" })
        assertEquals(
            listOf("ESPN", "ESPN2", "FANDUEL 1", "FanDuel Sports Network 4", "FanDuel 5",
                "TSN 1", "TSN 3", "TSN 5", "NASCAR", "NFL Sunday Ticket"),
            result.map { it.name }
        )
    }

    @Test fun highestResolutionWinsAndNoDuplicatesOrBackupGroup() {
        val source = listOf(
            channel("ESPN HD", "USA | Sports", key = "hd"),
            channel("ESPN 4K", "USA | Sports", key = "4k", sourceId = "other"),
            channel("ESPN SD", "USA | Sports", key = "sd"),
            channel("ESPN FHD", "USA | Sports", key = "fhd"),
            channel("FanDuel Sports Network 1 FHD", "USA | Sports", key = "fan-fhd"),
            channel("FANDUEL 1 4K", "USA | Sports", key = "fan-4k"),
            channel("TSN+ Events 2 HD", "Canada | Sports", key = "tsn-hd"),
            channel("TSN 2 FHD", "Canada | Sports", key = "tsn-fhd")
        )
        val result = PetalixLineup.organize(source, true)
        assertEquals(3, result.size)
        assertEquals(listOf("4k", "fan-4k", "tsn-fhd"), result.map { it.key })
        assertTrue(result.none { it.group == "Backup Streams" })
        assertEquals("ESPN", result[0].name)
        assertEquals("USA | Sports", source.first().group)
        assertEquals("https://example.invalid/live/stream.ts", result[0].url)
    }

    @Test fun resolutionSortWithinEveryCategory() {
        val source = listOf(
            channel("ABC Charlotte SD", "USA | Locals"),
            channel("NBC Raleigh FHD", "USA | Locals"),
            channel("CBS Asheville 4K", "USA | Locals"),
            channel("News Channel HD", "USA | News"),
            channel("ABC Charlotte 4K", "USA | Locals", key = "abc4k")
        )
        val result = PetalixLineup.organize(source, false)
        assertEquals(
            listOf("CBS Asheville", "ABC Charlotte", "NBC Raleigh", "News Channel"),
            result.map { it.name }
        )
        assertEquals("abc4k", result[1].key)
    }

    @Test fun internationalHiddenExceptTsnOneToFive() {
        val source = listOf(
            channel("Toronto News", "Canada | News"),
            channel("BBC One", "UK | Entertainment"),
            channel("French Sports", "International | Sports"),
            channel("TSN 1 HD", "Canada | Sports"),
            channel("TSN 5 HD", "Canada | Sports"),
            channel("TSN 6 HD", "Canada | Sports"),
            channel("TSN+ Events 8", "Canada | Sports"),
            channel("USA News HD", "USA | News")
        )
        val result = PetalixLineup.organize(source, false)
        assertEquals(listOf("TSN 1", "TSN 5", "USA News"), result.map { it.name })
        assertTrue(result.none { it.name.contains("Toronto") || it.name.contains("BBC") })
    }

    @Test fun removeVODButKeepLinearMovieAnd247Channels() {
        val source = listOf(
            channel("Movie Title 2025", "USA | VOD", url = "https://example.invalid/live/111.ts"),
            channel("Episode 101", "USA | TV Shows", url = "https://example.invalid/live/112.ts"),
            channel("Movie Title 2023", "USA | Movies", url = "https://example.invalid/movie/1.ts"),
            channel("Slam Dunk", "USA | Sports", url = "https://example.invalid/live/video.mp4"),
            channel("HBO HD", "USA | Movies", url = "https://example.invalid/live/113.ts"),
            channel("24/7 Movies 4K", "USA | 24/7 Movies", url = "https://example.invalid/live/114.ts")
        )
        val result = PetalixLineup.organize(source, false)
        assertEquals(listOf("HBO", "24/7 Movies"), result.map { it.name })
        assertFalse(result.any { it.name.contains("Movie Title") || it.name.contains("Episode") })
    }

    @Test fun distinctLocalStationsAreNotMergedIfDifferentEpgIds() {
        val result = PetalixLineup.organize(
            listOf(
                channel("ABC HD", "USA | Locals", key = "clt", id = "abc.charlotte"),
                channel("ABC FHD", "USA | Locals", key = "rdu", id = "abc.raleigh"),
                channel("ABC 4K", "USA | Locals", key = "clt4k", id = "abc.charlotte")
            ), multiplePlaylists = false
        )
        assertEquals(2, result.size)
        assertTrue(result.any { it.key == "clt4k" })
        assertTrue(result.any { it.key == "rdu" })
    }

    @Test fun onlyFanDuelOneToFiveAndTsnOneToFive() {
        val result = PetalixLineup.organize(
            listOf(
                channel("FanDuel 1 HD", "USA | Sports"),
                channel("FanDuel 5 UHD", "USA | Sports"),
                channel("FanDuel 6 UHD", "USA | Sports"),
                channel("FanDuel Sports Carolinas HD", "USA | Sports"),
                channel("TSN 2 HD", "Canada | Sports"),
                channel("TSN 7 HD", "Canada | Sports")
            ), false
        )
        assertEquals(listOf("FanDuel 1", "FanDuel 5", "TSN 2"), result.map { it.name })
    }

    @Test fun originalStreamsAndStableMetadataRemain() {
        val ch = channel("ESPN UHD", "USA | Sports", key="stable", id="espn.us")
        val result = PetalixLineup.organize(listOf(ch), false).single()
        assertEquals("stable", result.key)
        assertEquals("espn.us", result.tvgId)
        assertEquals(ch.logo, result.logo)
        assertEquals(ch.url, result.url)
    }
}
