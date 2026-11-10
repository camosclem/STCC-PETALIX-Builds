package com.nuvio.tv.petalix

import com.nuvio.tv.livetv.data.RenamedChannels
import com.nuvio.tv.livetv.model.LiveChannel
import org.junit.Assert.*
import org.junit.Test

class PanelChannelIdentityTest {
    private fun channel(key: String, url: String) = LiveChannel(key=key,sourceId="petalix-managed",sourceName="Petalix",name="New Channel Name",tvgId="new.epg",tvgName="New Channel Name",logo=null,groupId="Sports",group="Sports",number=7,url=url)
    @Test fun displayEditsAndCustomerFeedOriginDoNotChangeStableIdentity() {
        val original="https://provider.example/live/customer/password/501.ts"
        val curated="https://panel.example/live/pd_device/token/501.m3u8"
        assertEquals(PanelChannelIdentity.key("petalix-managed",original,"old.epg|Old Name"),PanelChannelIdentity.key("petalix-managed",curated,"new.epg|New Name"))
        assertEquals("petalix-managed|stream:501",PanelChannelIdentity.key("petalix-managed",original,"old"))
    }
    @Test fun unrelatedPlaylistsRetainTheirOwnIdentity() {
        assertEquals("custom-key",PanelChannelIdentity.key("personal-playlist","https://example/live/u/p/501.ts","custom-key"))
        assertNull(PanelChannelIdentity.streamId("https://example/movie/u/p/501.mkv"))
    }
    @Test fun existingFavoritesMoveAcrossRenameGuideAndFeedOriginChanges() {
        val old="petalix-managed|old.epg|Old Channel Name"
        val fresh=channel("petalix-managed|stream:501","https://panel.example/live/pd_device/token/501.ts")
        val moves=RenamedChannels.match(listOf(old),listOf(fresh),mapOf(old to "https://provider.example/live/customer/password/501.ts"),onlyNew=true)
        assertEquals(fresh.key,moves[old])
    }
    @Test fun ambiguousMatchesAreNeverGuessed() {
        val old="petalix-managed|old.epg|Old Name"
        val url="https://panel.example/live/pd_device/token/501.ts"
        assertTrue(RenamedChannels.match(listOf(old),listOf(channel("new-a",url),channel("new-b",url)),mapOf(old to "https://provider.example/live/u/p/501.ts"),onlyNew=true).isEmpty())
    }
}
