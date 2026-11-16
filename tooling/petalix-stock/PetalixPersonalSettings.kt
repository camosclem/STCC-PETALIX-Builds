package com.nuvio.tv.petalix

import org.json.JSONObject

/** Only an explicit settings publication changes this stamp; playlist/content refreshes do not. */
internal fun petalixPersonalSettingsStamp(managed: JSONObject, rawRevision: String, profileId: Int): String =
    managed.optString("controlRevision").takeIf { it.isNotBlank() }
        .let { it ?: managed.getString("revision") } + ":" + rawRevision + ":" + profileId + ":personal-settings-v1"
