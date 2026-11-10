package com.nuvio.tv.petalix

import android.content.Context
import androidx.datastore.preferences.core.*
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.*
import com.nuvio.tv.livetv.data.LiveTvPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Only the native preferences described by the bundled whitelist can be managed. */
internal class NativeControl(val spec:JSONObject) {
    val key=spec.getString("key")
    val feature=spec.getString("feature")
    val preference=spec.getString("preference")
    private val kind=spec.getString("kind")
    @Suppress("UNCHECKED_CAST")
    val typed:Preferences.Key<Any> = when(kind) {
        "boolean"->booleanPreferencesKey(preference)
        "integer"->intPreferencesKey(preference)
        "long"->longPreferencesKey(preference)
        "float"->floatPreferencesKey(preference)
        "string"->stringPreferencesKey(preference)
        "stringSet"->stringSetPreferencesKey(preference)
        else->error("Unsupported native preference type")
    } as Preferences.Key<Any>
    fun native(value:Any,checkValue:Boolean=true):Any {
        if(kind=="stringSet") {
            val array=when(value) { is String->JSONArray(value); is JSONArray->value; else->error("Invalid set value") }
            require(array.length()<=500)
            return (0 until array.length()).map { i ->
                val item=array.get(i);require(item is String && item.length<=2000);item as String
            }.toSet()
        }
        require(when(kind){"boolean"->value is Boolean;"string"->value is String;else->value is Number}) { "Invalid type for $key" }
        if(value is Number) {
            val n=value.toDouble();require(n.isFinite())
            if(kind!="float")require(n==kotlin.math.floor(n))
            if(checkValue){if(spec.has("min"))require(n>=spec.getDouble("min"));if(spec.has("max"))require(n<=spec.getDouble("max"))}
        }
        if(checkValue&&value is String) {
            require(value.length<=spec.optInt("maxLength",200))
            if(spec.has("pattern"))require(Regex(spec.getString("pattern")).matches(value))
        }
        if(checkValue&&spec.has("options")) {
            val options=spec.getJSONArray("options")
            require((0 until options.length()).any { val allowed=options.getJSONArray(it).get(0); if(allowed is Number&&value is Number)allowed.toDouble()==value.toDouble() else allowed==value })
        }
        return when(kind){"integer"->(value as Number).toInt();"long"->(value as Number).toLong();"float"->(value as Number).toFloat();else->value}
    }
}
internal data class NativePatch(val preferences:Preferences,val beforeWrite:JSONObject,val afterWrite:JSONObject)
/** Keep the first local value until the override is released, even across repeated deliveries. */
internal fun nativePreferencePatch(old:Preferences,specs:List<NativeControl>,values:JSONObject,original:JSONObject):NativePatch {
    val out=old.toMutablePreferences();val before=JSONObject(original.toString());val after=JSONObject(original.toString())
    for(s in specs) {
        if(values.has(s.key)) {
            val value=s.native(values.get(s.key))
            if(!before.has(s.key))before.put(s.key,(old[s.typed] as? Set<*>)?.let { JSONArray(it.toList()) } ?: old[s.typed] ?: JSONObject.NULL)
            after.put(s.key,before.get(s.key));out[s.typed]=value
        } else if(before.has(s.key)) {
            if(before.isNull(s.key))out.remove(s.typed) else out[s.typed]=s.native(before.get(s.key),false)
            after.remove(s.key)
        }
    }
    return NativePatch(out.toPreferences(),before,after)
}

@Singleton
class ManagedNativeSettings @Inject constructor(
    @ApplicationContext private val context:Context,
    private val factory:ProfileDataStoreFactory,
    private val profiles:ProfileManager,
    private val live:LiveTvPreferences,
    private val device:DeviceLocalPlayerPreferences,
    private val player:PlayerSettingsDataStore,
    private val badges:StreamBadgeSettingsDataStore,
    private val collections:CollectionsDataStore,
    private val profileSettings:ProfileDataStore,
    private val sentrySettings:SentrySettingsDataStore,
    private val torrentSettings:com.nuvio.tv.core.torrent.TorrentSettings,
    private val addons:AddonPreferences
) {
    private val backup=context.getSharedPreferences("managed_native_originals",Context.MODE_PRIVATE)
    private val controls:List<NativeControl> = context.assets.open("managed-native-controls.json").bufferedReader().use {
        val array=JSONArray(it.readText());(0 until array.length()).map { i->NativeControl(array.getJSONObject(i)) }
    }
    private val pinMap=JSONObject(context.assets.open("pin-settings-map.json").bufferedReader().use { it.readText() })
    val controlCount get()=26+controls.size
    val profileId get()=profiles.activeProfileId.value
    fun validate(values:JSONObject) {
        if (values.optBoolean("addon:penguplay")) {
            val url = values.optString("addon:penguplay_manifest").toHttpUrlOrNull()
            require(url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
                url.fragment == null && url.encodedPath.endsWith("/manifest.json", true)) { "Invalid Pengu Play manifest URL" }
        }
        values.keys().forEach { key->if(key !in setOf("addon:penguplay", "addon:penguplay_manifest") && (key.startsWith("pref:")||key.startsWith("sport:")||key.startsWith("addon:"))) {
            val spec=controls.firstOrNull { it.key==key }?:error("Unsupported native setting")
            spec.native(values.get(key))
        } }
        if(values.has("addon:kaptain")) {
            val url=values.getString("addon:kaptain").toHttpUrlOrNull()
            require(url!=null&&url.isHttps&&url.username.isEmpty()&&url.password.isEmpty()&&url.fragment==null&&url.encodedPath.endsWith("/manifest.json",true)) { "Invalid Kaptain manifest URL" }
        }
        if(values.has("addon:cncverse")) {
            val url=values.getString("addon:cncverse").toHttpUrlOrNull()
            require(url!=null&&url.isHttps&&url.username.isEmpty()&&url.password.isEmpty()&&url.fragment==null&&url.encodedPath.endsWith("/manifest.json",true)) { "Invalid CNCVerse addon URL" }
        }
        if(values.has("pref:collections:collections_json")) {
            val json=values.getString("pref:collections:collections_json")
            require(collections.validateCollectionsJson(json).valid) { "Invalid Kaptain collection export" }
            require(collections.importFromJson(json).isNotEmpty()) { "Collection export could not be read" }
        }
        val min="pref:player_settings:min_buffer_ms";val max="pref:player_settings:max_buffer_ms"
        require(values.has(min)==values.has(max))
        if(values.has(min))require(values.getInt(min)<=values.getInt(max))
    }
    private fun originals(key:String)=JSONObject(backup.getString(key,"{}")?:"{}")
    private fun save(key:String,value:JSONObject) { check(backup.edit().putString(key,value.toString()).commit()) }
    suspend fun apply(values:JSONObject) {
        validate(values)
        val prepared=JSONObject(values.toString())
        val collectionKey="pref:collections:collections_json"
        if(prepared.has(collectionKey)) prepared.put(collectionKey,PetalixServiceCatalog.reconcile(
            prepared.getString(collectionKey),context.assets.open("petalix/streaming-services.json").bufferedReader().use { it.readText() }
        ))
        // Finish one-time native migrations before assigning managed values.
        player.playerSettings.first();badges.settings.first()
        addons.applyManagedCncverse(if(values.has("addon:cncverse"))values.getString("addon:cncverse") else null)
        addons.applyManagedPengu(values.optString("addon:penguplay_manifest").takeIf { values.optBoolean("addon:penguplay") })
        addons.applyManagedKaptain(if(values.has("addon:kaptain"))values.getString("addon:kaptain") else null)
        applyControls(controls.filter { it.feature!="managed_addons" },prepared,"")
    }
    suspend fun applyRaw(raw:JSONObject) {
        val specs=mutableListOf<NativeControl>();val values=JSONObject()
        for(key in pinMap.keys()) {
            val spec=pinMap.getJSONObject(key);val targets=spec.getJSONArray("targets")
            for(i in 0 until targets.length()) {
                val target=targets.getString(i);val id="raw:$target:$key"
                val kind=if(spec.getString("type")=="set")"stringSet" else spec.getString("type")
                specs+=NativeControl(JSONObject().put("key",id).put("feature",target).put("preference",key).put("kind",kind).put("maxLength",1000000))
                if(raw.has(key))values.put(id,raw.get(key))
            }
        }
        require(raw.keys().asSequence().all { pinMap.has(it) }) { "Some panel settings need a newer native binding" }
        applyControls(specs,values,"raw:")
    }
    private suspend fun applyControls(selected:List<NativeControl>,values:JSONObject,scope:String) {
        val active=profileId
        for((feature,specs) in selected.groupBy { it.feature }) {
            val key=scope+"${if(feature in setOf("sports","live_tv","device_local_player_prefs","profile_settings","sentry_settings","torrent_settings","live_tv_device"))0 else active}:$feature"
            val original=originals(key)
            if(!specs.any { values.has(it.key)||original.has(it.key) })continue
            if(feature=="sports") {
                var committed:JSONObject?=null
                live.editSportsSettings { raw->
                    val json=JSONObject(raw?:"{}");val old=mutablePreferencesOf()
                    specs.forEach { s->if(json.has(s.preference))old[s.typed]=s.native(json.get(s.preference),false) }
                    val patch=nativePreferencePatch(old,specs,values,original)
                    save(key,patch.beforeWrite)
                    specs.forEach { s->val value=patch.preferences[s.typed];if(value==null)json.remove(s.preference) else json.put(s.preference,value) }
                    committed=patch.afterWrite;json.toString()
                }
                committed?.let { save(key,it) }
            } else {
                val store=when(feature){"live_tv"->live.managedPreferences();"live_tv_device"->live.managedDevicePreferences();"device_local_player_prefs"->device.managedPreferences();"profile_settings"->profileSettings.managedPreferences();"sentry_settings"->sentrySettings.managedPreferences();"torrent_settings"->torrentSettings.managedPreferences();else->factory.get(if(feature=="addon_preferences" && profiles.activeProfile?.usesPrimaryAddons==true)1 else active,feature)}
                var committed:JSONObject?=null
                store.updateData { old->
                    val patch=nativePreferencePatch(old,specs,values,original)
                    save(key,patch.beforeWrite);committed=patch.afterWrite;patch.preferences
                }
                committed?.let { save(key,it) }
            }
        }
    }
}
