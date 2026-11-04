package com.nuvio.tv.petalix.ui2

import com.nuvio.tv.livetv.model.LiveChannel
import org.junit.Assert.*
import org.junit.Test

class PetalixLocalStationMatcherTest {
    private fun channel(key:String,name:String,id:String?=null)=LiveChannel(key,"source","Playlist",name,id,null,null,"news","News",1,"https://example.invalid/live")
    private val nearby=listOf(NearbyStation("WBTV","Charlotte","NC",27),NearbyStation("WHKY-TV","Hickory","NC",1))
    @Test fun matchesCallsignFromNameAndRetainsOriginalEpgIdentity(){
        val c=channel("original","US: WBTV CBS HD","existing-epg-id")
        val match=LocalStationMatcher.matches(listOf(c),nearby).single()
        assertSame(c,match.channel);assertEquals("existing-epg-id",match.channel.tvgId)
    }
    @Test fun matchesTvgIdWithBroadcastSuffix(){
        val c=channel("hickory","Local News","WHKY-TV.us")
        assertEquals("WHKY-TV",LocalStationMatcher.matches(listOf(c),nearby).single().station.call)
    }
    @Test fun doesNotInventMatchesFromNetworkOnlyOrOutOfAreaCalls(){
        assertTrue(LocalStationMatcher.matches(listOf(channel("a","CBS News"),channel("b","WABC New York")),nearby).isEmpty())
    }
    @Test fun deduplicatesStableKeysButKeepsAvailableVariants(){
        val a=channel("same","WBTV HD");val b=channel("other","WBTV SD")
        assertEquals(listOf("same","other").toSet(),LocalStationMatcher.matches(listOf(a,a,b),nearby).map{it.channel.key}.toSet())
    }
    @Test fun geographyUsesGreatCircleDistance(){
        assertEquals(0,LocalStationMatcher.miles(35.73,-81.34,35.73,-81.34))
        assertTrue(LocalStationMatcher.miles(35.73,-81.34,35.3642,-81.1867) in 25..32)
        assertTrue(LocalStationMatcher.miles(35.73,-81.34,40.71,-74.0)>500)
    }
}
