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
        key: String = "stable-key",
        id: String = "epg-id",
        sourceId: String = "test-source"
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
        url = "https://example.invalid/stream/" + key
    )

    @Test fun sportsAndLocalCategories() {
        assertEquals("Sports | Major Networks", PetalixLineup.category("USA | Sports", "ESPN FHD"))
        assertEquals("Sports | FanDuel", PetalixLineup.category("USA | Sports", "FanDuel Sports Network Carolinas"))
        assertEquals("Sports | TSN+", PetalixLineup.category("Canada | Sports", "TSN+ Events 1"))
        assertEquals("Sports | NFL", PetalixLineup.category("USA | Sports | NFL", "Carolina Panthers"))
        assertEquals("Sports | NBA", PetalixLineup.category("USA | Sports | NBA", "Boston Celtics"))
        assertEquals("Sports | Racing", PetalixLineup.category("USA | Sports", "NASCAR Live"))
        assertEquals("Locals", PetalixLineup.category("USA | Locals", "WCNC Charlotte"))
        assertEquals("24/7 | TV & Series", PetalixLineup.category("USA | 24/7 | DRAMA", "24/7 Mayday"))
    }

    @Test fun preservesIdsAndKeepsBestQualityPlusBackup() {
        val channels = listOf(
            channel("ESPN FHD", "USA | Sports", key = "fhd", id = "espn.us"),
            channel("ESPN HD", "USA | Sports", key = "hd", id = "espn.us"),
            channel("ESPN SD", "USA | Sports", key = "sd", id = "espn.us"),
            channel("ABC Charlotte", "USA | Locals", key = "local", id = "abc.charlotte"),
            channel("Toronto News", "Canada | News", key = "foreign")
        )
        val result = PetalixLineup.organize(channels, multiplePlaylists = false)
        val primary = result.filter { it.group == "Sports | Major Networks" }
        assertEquals(1, primary.size)
        assertEquals("fhd", primary.first().key)
        assertEquals("espn.us", primary.first().tvgId)
        assertEquals("ESPN", primary.first().name)
        assertEquals(2, result.count { it.group == "Backup Streams" })
        assertEquals(1, result.count { it.group == "Locals" })
        assertFalse(result.any { it.key == "foreign" })
        assertTrue(result.any { it.key == "hd" })
        assertTrue(result.any { it.key == "sd" })
    }

    @Test fun laterHigherQualityWinsAndAlternativeFeedsRemain() {
        val result = PetalixLineup.organize(
            listOf(
                channel("ESPN SD", "USA | Sports", key = "sd"),
                channel("ESPN FHD", "USA | Sports", key = "fhd")
            ), multiplePlaylists = false
        )
        assertEquals("fhd", result.first { it.group == "Sports | Major Networks" }.key)
        assertEquals("sd", result.first { it.group == "Backup Streams" }.key)
    }

    @Test fun keepSeparateStationsAndSourceBoundaries() {
        val result = PetalixLineup.organize(
            listOf(
                channel("CBS Charlotte HD", "USA | Locals", key = "clt", id = "wbtv"),
                channel("CBS Raleigh HD", "USA | Locals", key = "rdu", id = "wral"),
                channel("CBS Charlotte HD", "USA | Locals", key = "other-provider", sourceId = "second")
            ), multiplePlaylists = true
        )
        assertEquals(3, result.size)
        assertEquals(3, result.count { it.group == "Locals" })
        assertTrue(result.any { it.groupId == "second::Locals" })
    }

    @Test fun tsnExplicitExceptionAndAtoZ() {
        val channels = listOf(
            channel("Toronto News", "Canada | News", key = "toronto"),
            channel("TSN+ Events 1", "Canada | Sports", key = "tsn"),
            channel("Western Movie Channel", "USA | Western", key = "western"),
            channel("ABC Charlotte", "USA | Locals", key = "abc")
        )
        val result = PetalixLineup.organize(channels, multiplePlaylists = false)
        assertEquals(listOf("Locals", "Sports | TSN+", "Western"), result.map { it.group })
        assertEquals("tsn", result[1].key)
    }
}
