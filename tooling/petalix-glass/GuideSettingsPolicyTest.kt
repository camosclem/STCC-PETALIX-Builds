package com.nuvio.tv.petalix

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GuideSettingsPolicyTest {
    private val header = booleanPreferencesKey("small_header")
    private val preview = booleanPreferencesKey("show_preview")
    private val local = stringSetPreferencesKey(GUIDE_LOCAL_CHOICES)
    private fun control(name: String, raw: Boolean = false) = NativeControl(JSONObject()
        .put("key", if (raw) "raw:live_tv:$name" else "pref:live_tv:$name")
        .put("feature", "live_tv").put("preference", name).put("kind", "boolean"))

    @Test fun compactHeaderOverridesPanelWhileUntouchedSettingsStillUpdate() {
        val compact = control("small_header")
        val video = control("show_preview")
        val stored = mutablePreferencesOf(header to true, preview to false, local to setOf("small_header"))
        val patch = nativePreferencePatch(stored, listOf(compact, video),
            JSONObject().put(compact.key, false).put(video.key, true), JSONObject())
        assertEquals(true, patch.preferences[header])
        assertEquals(true, patch.preferences[preview])
    }

    @Test fun rawPanelOverrideCannotResetCompactHeader() {
        val compact = control("small_header", raw = true)
        val stored = mutablePreferencesOf(header to true, local to setOf("small_header"))
        val patch = nativePreferencePatch(stored, listOf(compact), JSONObject().put(compact.key, false), JSONObject())
        assertEquals(true, patch.preferences[header])
    }

    @Test fun releasingAnOldPanelOverrideKeepsTheUsersChoice() {
        val compact = control("small_header")
        val stored = mutablePreferencesOf(header to true, local to setOf("small_header"))
        val patch = nativePreferencePatch(stored, listOf(compact), JSONObject(), JSONObject().put(compact.key, false))
        assertEquals(true, patch.preferences[header])
        assertFalse(patch.afterWrite.has(compact.key))
    }

    @Test fun unrelatedSaveDoesNotClaimEveryDefaultAsALocalPreference() {
        val before = mapOf<String, Any>("small_header" to false, "show_preview" to true)
        assertEquals(setOf("small_header"), guideSettingChanges(before, before + ("small_header" to true)))
        assertTrue(guideSettingChanges(before, before).isEmpty())
    }

    @Test fun userCanChangeAnExistingOverrideBackToTheDefault() {
        val changed = guideSettingChanges(mapOf("small_header" to true), mapOf("small_header" to false))
        assertEquals(setOf("small_header"), guideWritableSettings(changed, setOf("small_header"), true))
        assertTrue(guideWritableSettings(changed, setOf("small_header"), false).isEmpty())
    }

    @Test fun nonGuideManagedControlsRetainTheirExistingBehavior() {
        val spec = NativeControl(JSONObject().put("key", "pref:player_settings:test")
            .put("feature", "player_settings").put("preference", "small_header").put("kind", "boolean"))
        val stored = mutablePreferencesOf(header to true, local to setOf("small_header"))
        val patch = nativePreferencePatch(stored, listOf(spec), JSONObject().put(spec.key, false), JSONObject())
        assertEquals(false, patch.preferences[header])
    }
}
