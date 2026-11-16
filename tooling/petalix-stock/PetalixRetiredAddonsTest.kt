package com.nuvio.tv.petalix

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PetalixRetiredAddonsTest {
    @Test fun retiredAssignmentsDoNotRemoveHomeVodOrPenguPolicy() {
        val source = JSONObject().put("addon:cncverse", "https://old.example/manifest.json")
            .put("addon:kaptain", "https://legacy.example/manifest.json")
            .put("pref:collections:collections_json", "[{\"id\":\"saved-home\"}]")
            .put("vod.first", true).put("addon:penguplay", false)
        val filtered = petalixActiveManagedValues(source)
        assertFalse(filtered.has("addon:cncverse"))
        assertFalse(filtered.has("addon:kaptain"))
        assertTrue(source.has("addon:kaptain"))
        assertEquals(source.getString("pref:collections:collections_json"), filtered.getString("pref:collections:collections_json"))
        assertTrue(filtered.getBoolean("vod.first"))
        assertFalse(filtered.getBoolean("addon:penguplay"))
    }
    @Test fun legacyDecorationsCannotBlockSupportedStartupAndGuidePreferences() {
        val source = JSONObject().put("show_preview", true).put("petalix_holiday_theme", "NONE")
            .put("petalix_birthday_name", "Fixture").put("petalix_launch_witch_laugh", false)
        val bindings = JSONObject().put("show_preview", JSONObject())
        val filtered = petalixBoundRawValues(source, bindings)
        assertTrue(filtered.getBoolean("show_preview"))
        assertEquals(1, filtered.length())
        assertTrue(source.has("petalix_birthday_name"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun unknownBindingsStillReportAnError() {
        petalixBoundRawValues(JSONObject().put("unknown_native_control", true), JSONObject())
    }
}
