package com.nuvio.tv.petalix.ui2

import com.nuvio.tv.livetv.model.LiveChannel
import org.junit.Assert.*
import org.junit.Test

class PetalixLocalSuggestionsTest {
    private fun channel(key:String,name:String,group:String="Locals") =
        LiveChannel(key,"source","Playlist",name,null,null,null,group,group,1,"https://example.invalid/$key")

    @Test fun curatesFourAvailableAffiliatesFromOneMarket() {
        val nearby=listOf(
            NearbyStation("WSOC","CHARLOTTE","NC",43),NearbyStation("WCNC","CHARLOTTE","NC",44),
            NearbyStation("WBTV","CHARLOTTE","NC",45),NearbyStation("WJZY","BELMONT","NC",47),
            NearbyStation("WLOS","ASHEVILLE","NC",67))
        val channels=listOf(
            channel("abc","NC | Charlotte | ABC WSOC"),channel("nbc","NC | Charlotte | NBC WCNC"),
            channel("cbs","NC | Charlotte | CBS WBTV"),channel("fox","NC | Charlotte | FOX WJZY"),
            channel("other","NC | Asheville | ABC WLOS"),channel("national","ABC News","News"))
        val matches=LocalStationMatcher.matches(channels,nearby)
        assertEquals(listOf("abc","nbc","cbs","fox"),
            LocalStationMatcher.suggestions(channels,matches,"Charlotte, NC").map{it.key})
        assertTrue(matches.none{it.channel.key=="national"})
    }

    @Test fun doesNotSuggestUnlistedOrAnotherMarketsStation() {
        val stations=listOf(NearbyStation("WSOC","CHARLOTTE","NC",43),NearbyStation("WLOS","ASHEVILLE","NC",67))
        val channels=listOf(channel("asheville","NC | Asheville | ABC WLOS"))
        val matches=LocalStationMatcher.matches(channels,stations)
        assertTrue(LocalStationMatcher.suggestions(channels,matches,"Charlotte, NC").isEmpty())
    }

    @Test fun usesCuratedMarketNamesWhenOldStationDatasetLacksAffiliates() {
        val channels=listOf(channel("abc","NC | Charlotte | ABC WSOC"),channel("nbc","NC | Charlotte | NBC WCNC"),
            channel("cbs","NC | Charlotte | CBS WBTV"),channel("fox","NC | Charlotte | FOX WJZY"))
        val nearby=listOf(NearbyStation("WBTV","CHARLOTTE","NC",45),NearbyStation("WJZY","BELMONT","NC",47))
        val matches=LocalStationMatcher.matches(channels,nearby)
        assertEquals(listOf("abc","nbc","cbs","fox"),
            LocalStationMatcher.suggestions(channels,matches,"Charlotte, NC").map{it.key})
    }
}
