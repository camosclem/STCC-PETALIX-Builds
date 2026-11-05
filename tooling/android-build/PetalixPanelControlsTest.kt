package com.nuvio.tv.petalix

import androidx.datastore.preferences.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PetalixPanelControlsTest {
    private val control=NativeControl(JSONObject("""{"key":"pref:player_settings:auto_skip_segment_types","feature":"player_settings","preference":"auto_skip_segment_types","kind":"stringSet"}"""))
    @Test fun selectedValuesRoundTripAndReleaseRestoresOriginalSet() {
        val key=stringSetPreferencesKey("auto_skip_segment_types")
        val initial=preferencesOf(key to setOf("INTRO"))
        val first=nativePreferencePatch(initial,listOf(control),JSONObject().put(control.key,"[\"RECAP\",\"OUTRO\"]"),JSONObject())
        assertEquals(setOf("RECAP","OUTRO"),first.preferences[key])
        val saved=JSONObject(first.afterWrite.toString())
        val restored=nativePreferencePatch(first.preferences,listOf(control),JSONObject(),saved)
        assertEquals(setOf("INTRO"),restored.preferences[key])
    }
    @Test fun invalidSelectionTypesAreRejected() {
        assertTrue(runCatching {control.native("[1]")}.isFailure)
        assertTrue(runCatching {control.native("not json")}.isFailure)
        assertEquals(emptySet<String>(),control.native("[]"))
    }
}
