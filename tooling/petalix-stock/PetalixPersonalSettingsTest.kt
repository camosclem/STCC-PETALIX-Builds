package com.nuvio.tv.petalix

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PetalixPersonalSettingsTest {
    @Test fun contentRefreshDoesNotReplacePersonalPreferences() {
        val first = JSONObject().put("controlRevision", "owner-send-one").put("revision", "playlist-one")
        val refreshed = JSONObject(first.toString()).put("revision", "playlist-two")
        assertEquals(petalixPersonalSettingsStamp(first, "raw-one", 1), petalixPersonalSettingsStamp(refreshed, "raw-one", 1))
        assertNotEquals(petalixPersonalSettingsStamp(first, "raw-one", 1),
            petalixPersonalSettingsStamp(JSONObject(first.toString()).put("controlRevision", "owner-send-two"), "raw-one", 1))
    }
    @Test fun explicitRawUpdatesAndProfileChangesStillApply() {
        val doc = JSONObject().put("controlRevision", "owner-send").put("revision", "content")
        assertNotEquals(petalixPersonalSettingsStamp(doc, "raw-one", 1), petalixPersonalSettingsStamp(doc, "raw-two", 1))
        assertNotEquals(petalixPersonalSettingsStamp(doc, "raw-one", 1), petalixPersonalSettingsStamp(doc, "raw-one", 2))
    }
    @Test fun olderPanelsHaveACompatibleFallback() {
        val legacy = JSONObject().put("revision", "legacy")
        assertTrue(petalixPersonalSettingsStamp(legacy, "", 1).startsWith("legacy:"))
    }
}
