package com.nuvio.tv.petalix

import android.content.Context
import androidx.datastore.preferences.core.*
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.livetv.data.LiveTvPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

object PetalixPanelPolicy {
    val state = MutableStateFlow(Triple(false, false, false))
    fun blocked(route: String): Boolean {
        val p = state.value
        // Customers need the native Settings screens for their ZIP, favorites and
        // playback preferences even when the panel supplies managed defaults.
        return p.third && route in setOf("addon_manager", "plugins", "catalog_order", "media_servers")
    }
}

@Singleton
class PetalixPanelSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    private val native: ManagedNativeSettings,
    private val profiles: ProfileManager,
    private val factory: ProfileDataStoreFactory,
    private val live: LiveTvPreferences
) {
    private val prefs = context.getSharedPreferences("petalix_panel_settings", Context.MODE_PRIVATE)
    private val pinMap = JSONObject(context.assets.open("pin-settings-map.json").bufferedReader().use { it.readText() })
    var hideOtherLocals: Boolean = true
        private set
    var appliedCount: Int = 0
        private set

    suspend fun apply(doc: JSONObject): String {
        val managed = doc.optJSONObject("management") ?: error("Panel settings are missing")
        require(managed.optInt("schemaVersion") == 2)
        val revision = managed.getString("revision")
        require(revision.matches(Regex("[a-f0-9]{64}")))
        val values = managed.getJSONObject("values")
        val raw = doc.optJSONObject("settings")?.optJSONObject("values") ?: JSONObject()
        hideOtherLocals = values.optBoolean("pref:live_tv:petalix_hide_other_locals", true)
        val stamp = revision + ":" + doc.optJSONObject("settings")?.optString("revision").orEmpty() + ":" + profiles.activeProfileId.value + ":home-lineup-v3"
        PetalixPanelPolicy.state.value = Triple(values.optBoolean("mode.hideSettings"), values.optBoolean("mode.lockSettings"), values.optBoolean("mode.lockSources"))
        if (stamp == prefs.getString("stamp", "") && !values.optBoolean("mode.lockSettings")) {
            appliedCount = prefs.getInt("count", 0)
            return revision
        }
        native.validate(values)
        // Match the native 13.1 settings model. The generic overrides below take priority.
        live.updateSettings { s -> s.copy(
            openOn = values.getString("start.page"), startPage = values.getString("start.page"),
            startOnLiveTv = values.getString("start.page") == "LIVE_TV",
            autoPlayLastChannel = values.getBoolean("live.autoPlayLast"),
            guideSize = values.getString("live.guideSize"), compactRows = values.getString("live.guideSize") == "COMPACT",
            showPreview = values.getBoolean("live.preview"), showChannelNames = values.getBoolean("live.names"),
            showChannelNumbers = values.getBoolean("live.numbers"), showChannelLogos = values.getBoolean("live.logos"),
            channelSurf = values.getBoolean("live.surfing"), use24HourClock = values.getBoolean("live.clock24"),
            epgRefreshHours = values.getInt("live.epgRefresh"), autoReconnect = values.getBoolean("play.reconnect"),
            autoBackupChannel = values.getBoolean("play.backup"), frameRateMode = if (values.getBoolean("play.frameRate")) "START_STOP" else "OFF",
            audioPassthrough = values.getBoolean("play.passthrough"), onDemandInSidebar = values.getBoolean("vod.visible"),
            onDemandStreamsPosition = if (values.getBoolean("vod.first")) "FIRST" else "LAST", vodMergeDuplicates = values.getBoolean("vod.merge")
        ) }
        live.editSportsSettings { existing ->
            JSONObject(existing ?: "{}").put("showInSidebar", values.getBoolean("sports.visible"))
                .put("region", values.getString("sports.region")).put("hideScores", values.getBoolean("sports.hideScores"))
                .put("scoreOnInfoBar", values.getBoolean("sports.infoScore")).put("scoringAlerts", values.getBoolean("sports.alerts"))
                .put("videoWindow", values.getBoolean("sports.video")).toString()
        }
        native.apply(values)
        applyRaw(raw)
        com.nuvio.tv.livetv.startup.StartPage.save(context, when (values.getString("start.page")) {
            "LIVE_TV" -> "live_tv"; "SPORTS" -> "sports"; "ON_DEMAND" -> "on_demand"; else -> null
        })
        appliedCount = values.length() + raw.length()
        check(prefs.edit().putString("stamp", stamp).putInt("count", appliedCount).commit())
        return revision
    }

    private suspend fun applyRaw(raw: JSONObject) = native.applyRaw(raw)
}
