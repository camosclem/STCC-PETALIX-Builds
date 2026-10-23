package com.nuvio.tv.petalix
import androidx.datastore.preferences.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class PanelPlaylistTest {
 @Test fun sharedRulesKeepBrandIdentitiesAndLocalPreferences() {
  val p=mutablePreferencesOf(stringSetPreferencesKey("hidden_channels") to setOf("local"))
  PanelPlaylist.store(p,JSONObject("""{"rules":{"hiddenChannels":["abc|News"],"hiddenGroups":["Sports"],"groupOrder":["Sports","News"],"channelOrder":{"Sports":["abc|News"]}}}"""))
  val a=PanelPlaylist.view(p,"petalix-managed");val b=PanelPlaylist.view(p,"stcc-managed")
  assertTrue(a[stringSetPreferencesKey("hidden_channels")]!!.contains("petalix-managed|abc|News"))
  assertTrue(b[stringSetPreferencesKey("hidden_channels")]!!.contains("stcc-managed|abc|News"))
  assertTrue(a[stringSetPreferencesKey("hidden_channels")]!!.contains("local"))
  assertEquals(setOf("local"),p[stringSetPreferencesKey("hidden_channels")])
  assertEquals("custom",a[stringPreferencesKey("group_sort")])
  assertTrue(a[stringPreferencesKey("channel_order")]!!.contains("petalix-managed|abc|News"))
  PanelPlaylist.store(p,JSONObject("""{"rules":{"hiddenChannels":[],"hiddenGroups":[],"groupOrder":[],"channelOrder":{}}}"""))
  assertEquals(setOf("local"),PanelPlaylist.view(p,"petalix-managed")[stringSetPreferencesKey("hidden_channels")])
 }
}
