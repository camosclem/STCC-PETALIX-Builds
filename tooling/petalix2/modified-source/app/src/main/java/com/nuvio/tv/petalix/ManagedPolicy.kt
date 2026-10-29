package com.nuvio.tv.petalix

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.json.JSONArray
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class CustomerPolicy(val hideSettings:Boolean=true,val lockSettings:Boolean=true,val lockSources:Boolean=true)

object ManagerWrite : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<ManagerWrite>
}

object ManagedPolicy {
    val state=MutableStateFlow(CustomerPolicy())
    private var initialized=false
    @Synchronized fun initialize(context:Context) {
        ServiceHubPolicy.initialize(context)
        if(initialized)return
        val p=context.getSharedPreferences("petalix_management",Context.MODE_PRIVATE)
        state.value=CustomerPolicy(p.getBoolean("hide",true),p.getBoolean("lock",true),p.getBoolean("sources",true))
        initialized=true
    }
    fun save(context:Context,values:JSONObject) {
        val next=CustomerPolicy(values.getBoolean("mode.hideSettings"),values.getBoolean("mode.lockSettings"),values.getBoolean("mode.lockSources"))
        check(context.getSharedPreferences("petalix_management",Context.MODE_PRIVATE).edit().putBoolean("hide",next.hideSettings).putBoolean("lock",next.lockSettings).putBoolean("sources",next.lockSources).commit())
        state.value=next
        ServiceHubPolicy.save(context,values)
    }
    val setupRoutes=setOf("Settings","LiveTvSettings","ThemeSettings","PlaybackSettings","LayoutSettings","TmdbSettings","Tracking","ManageProfiles","ExperienceModeSelection","LayoutSelection","Account","AuthSignIn","AuthQrSignIn","SyncCodeGenerate","SyncCodeClaim")
    val sourceRoutes=setOf("AddonManager","Plugins","CatalogOrder","MediaServers","MediaServer")
    fun blocked(route:String,policy:CustomerPolicy=state.value)=
        ((route in setupRoutes||route.endsWith("Settings"))&&(policy.hideSettings||policy.lockSettings)) ||
        (route in sourceRoutes&&(policy.hideSettings||policy.lockSettings||policy.lockSources))
}

/** Protect setup preferences at their write boundary, including background imports. */
class ManagedStore(private val name:String,private val delegate:DataStore<Preferences>,context:Context?=null):DataStore<Preferences> {
    init { if(context!=null)ManagedPolicy.initialize(context) }
    override val data=delegate.data
    override suspend fun updateData(transform:suspend(Preferences)->Preferences):Preferences {
        val manager=currentCoroutineContext()[ManagerWrite.Key]!=null
        return delegate.updateData { old ->
            val next=transform(old)
            if(manager)next else protect(old,next,ManagedPolicy.state.value)
        }
    }
    internal fun protect(old:Preferences,next:Preferences,policy:CustomerPolicy):Preferences {
        val out=next.toMutablePreferences()
        val feature=name.replace(Regex("_p[0-9]+$"),"")
        val keys=(old.asMap().keys+next.asMap().keys).distinctBy { it.name }
        keys.forEach { key ->
            val live=name=="live_tv"
            val source=live&&key.name in setOf("playlists","epg_sources")
            val addonSource=feature=="addon_preferences"
            val config=live&&key.name in LiveSettingKeys.names
            val profileConfig=!live&&(feature.endsWith("settings")||feature in setOf("theme","layout","experience_mode","device_local_player_prefs","collections"))
            if(((source||addonSource)&&policy.lockSources)||((config||profileConfig)&&policy.lockSettings)) {
                @Suppress("UNCHECKED_CAST") val typed=key as Preferences.Key<Any>
                val value=old[typed]
                if(value==null)out.remove(typed) else out[typed]=if(source&&value is String)sourceMetadata(value,next[typed] as? String) else value
            }
            if(live&&key.name=="sports"&&policy.lockSettings) {
                @Suppress("UNCHECKED_CAST") val typed=key as Preferences.Key<String>
                val before=runCatching { JSONObject(old[typed]?:"{}") }.getOrDefault(JSONObject())
                val after=runCatching { JSONObject(next[typed]?:"{}") }.getOrDefault(JSONObject())
                listOf("showInSidebar","region","hideScores","scoreOnInfoBar","scoringAlerts","videoWindow","bestChannelFirst","remindMyTeams","remindMinutes","autoSwitch","onlyMyChannels").forEach { field ->
                    if(before.has(field))after.put(field,before.get(field)) else after.remove(field)
                }
                out[typed]=after.toString()
            }
        }
        return out.toPreferences()
    }
    private fun sourceMetadata(previous:String,proposed:String?):String=runCatching {
        val before=JSONArray(previous)
        val after=JSONArray(proposed?:"[]")
        val updates=(0 until after.length()).map { after.getJSONObject(it) }.associateBy { it.optString("id") }
        val result=JSONArray()
        for(i in 0 until before.length()) {
            val item=JSONObject(before.getJSONObject(i).toString())
            updates[item.optString("id")]?.let { incoming ->
                listOf("lastUpdatedMs","lastError","channelCount","programCount","serverTimezone","catchupNoHlsSince").forEach { field ->
                    if(incoming.has(field))item.put(field,incoming.get(field))
                }
            }
            result.put(item)
        }
        result.toString()
    }.getOrDefault(previous)
}
