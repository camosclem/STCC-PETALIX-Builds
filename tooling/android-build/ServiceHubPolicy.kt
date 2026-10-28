package com.nuvio.tv.petalix

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

object ServiceHubPolicy {
    val deduplicate=MutableStateFlow(true)
    private var initialized=false
    @Synchronized fun initialize(context:Context) {
        if(initialized)return
        deduplicate.value=context.getSharedPreferences("petalix_management",Context.MODE_PRIVATE).getBoolean("serviceHubDedupe",true)
        initialized=true
    }
    fun save(context:Context,values:JSONObject) {
        val enabled=values.optBoolean("pref:layout_settings:service_hub_deduplicate",true)
        check(context.getSharedPreferences("petalix_management",Context.MODE_PRIVATE).edit().putBoolean("serviceHubDedupe",enabled).commit())
        deduplicate.value=enabled
    }
}
