package com.nuvio.tv.petalix

import org.json.JSONObject

private val retiredCatalogAssignments = setOf("addon:cncverse", "addon:kaptain")
private val legacyPanelDecorations = setOf(
    "petalix_holiday_theme", "petalix_birthday_greeting_enabled", "petalix_birthday_name",
    "petalix_birthday_date", "petalix_launch_witch_laugh"
)

/** Remove retired catalog assignments while preserving native Home collections and provider settings. */
internal fun petalixActiveManagedValues(input: JSONObject): JSONObject =
    JSONObject(input.toString()).also { result -> retiredCatalogAssignments.forEach { result.remove(it) } }

/** Older panels can retain decorative fields which this native app has never bound. */
internal fun petalixBoundRawValues(input: JSONObject, bindings: JSONObject): JSONObject {
    require(input.keys().asSequence().all { bindings.has(it) || it in legacyPanelDecorations }) {
        "Some panel settings need a newer native binding"
    }
    return JSONObject(input.toString()).also { result -> legacyPanelDecorations.forEach { result.remove(it) } }
}
