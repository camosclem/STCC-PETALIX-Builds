package com.openguide.tv

import org.junit.Assert.*
import org.junit.Test

class LocalStationMatcherTest {
    @Test fun hickoryUsesCharlotteAffiliates() {
        val market = LocalStationMatcher.findMarket("28601")!!
        assertEquals("charlotte", market.id)
        assertEquals(market, LocalStationMatcher.findMarket("28602"))
        assertNull(LocalStationMatcher.findMarket("00000"))
        assertNull(LocalStationMatcher.findMarket("2860x"))
    }
    @Test fun localFeedsNotNationalNetworkNames() {
        val market = LocalStationMatcher.findMarket("28601")!!
        val input = listOf(
            LocalStationMatcher.Station("1", "ABC WSOC TV Charlotte HD", "USA Locals"),
            LocalStationMatcher.Station("2", "WBTV 3 CBS Charlotte", "Locals"),
            LocalStationMatcher.Station("3", "WCNC-TV NBC Charlotte", "North Carolina"),
            LocalStationMatcher.Station("4", "WJZY Fox 46 Charlotte", "Locals"),
            LocalStationMatcher.Station("5", "ABC News Live", "National"),
            LocalStationMatcher.Station("6", "FOX Sports 1", "National Sports")
        )
        val matches = LocalStationMatcher.recommendations(market, input)
        assertEquals(listOf("ABC", "CBS", "NBC", "FOX"), matches.map {it.network})
        assertEquals(listOf("1", "2", "3", "4"), matches.mapNotNull { it.station?.id })
        assertEquals(4, matches.count{it.station!=null})
    }
    @Test fun zeroAvailableFeedsNotInvented() {
        val market = LocalStationMatcher.findMarket("28601")!!
        val result = LocalStationMatcher.recommendations(market, listOf(
            LocalStationMatcher.Station("other", "ABC News National", "News")
        ))
        assertEquals(4, result.size)
        assertTrue(result.all{it.station == null})
    }
}
