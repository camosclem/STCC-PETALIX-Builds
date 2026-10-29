package com.nuvio.tv.petalix

import androidx.datastore.preferences.core.edit
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.local.AppOnboardingDataStore
import com.nuvio.tv.data.local.AddonPreferences
import com.nuvio.tv.data.local.ExperienceModeDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.livetv.data.LiveTvPreferences
import com.nuvio.tv.livetv.data.LiveTvRepository
import com.nuvio.tv.livetv.model.PlaylistSource
import com.nuvio.tv.livetv.sports.SportsSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

class ActivationRequired:Exception("Activate this TV with a PIN from Petalix support.")

@Singleton
class ManagedCoordinator @Inject constructor(
    @ApplicationContext private val context:Context,
    private val live:LiveTvPreferences,
    private val repository:LiveTvRepository,
    private val onboarding:AppOnboardingDataStore,
    private val experience:ExperienceModeDataStore,
    private val layout:LayoutPreferenceDataStore,
    private val addons:AddonPreferences,
    private val nativeSettings:ManagedNativeSettings
) {
    private val prefs=context.getSharedPreferences("petalix_session_v2",Context.MODE_PRIVATE)
    private val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
    private val mutex=Mutex()
    val deviceId:String=prefs.getString("device",null)?:UUID.randomUUID().toString().also { prefs.edit().putString("device",it).commit() }
    val hasSavedActivation:Boolean get()=token().isNotEmpty()
    val ready:Boolean get()=ActivationSession.ready(hasSavedActivation,prefs.getString("expires",""),System.currentTimeMillis())&&!prefs.getBoolean("accessDenied",false)
    init { ManagedPolicy.initialize(context) }

    suspend fun activate(pin:String)=mutex.withLock {
        val doc=post("activate",report().put("code",pin),null)
        val value=doc.optString("token")
        require(value.isNotBlank()) { "Activation could not be completed." }
        apply(doc)
        storeToken(value)
        persistSession(doc)
        ActivationSession.acknowledge { acknowledge() }
    }

    suspend fun refresh()=mutex.withLock {
        if(!hasSavedActivation)throw ActivationRequired()
        val doc=post("status",report(),token())
        try { apply(doc) }
        catch(e:Exception) {
            runCatching { post("settings-ack",report().put("settingsRequested",doc.optJSONObject("management")?.optString("revision")).put("settingsPending",nativeSettings.controlCount).put("settingsError",true),token()) }
            throw e
        }
        persistSession(doc)
        val updateRequest=doc.optJSONObject("appUpdate")?.optString("requestId").orEmpty()
        if(updateRequest.isNotBlank())pendingUpdateRequest.value=updateRequest
        ActivationSession.acknowledge { acknowledge() }
    }

    private val pendingUpdateRequest=kotlinx.coroutines.flow.MutableStateFlow("")
    val updateRequests:kotlinx.coroutines.flow.StateFlow<String> get()=pendingUpdateRequest
    suspend fun appUpdate():JSONObject {
        if(!hasSavedActivation)throw ActivationRequired()
        return post("update",report(),token())
    }

    private fun persistSession(doc:JSONObject) {
        check(prefs.edit().putString("expires",doc.getString("expires")).putLong("verified",System.currentTimeMillis()).putBoolean("accessDenied",false).commit())
    }
    private fun report()=JSONObject().put("deviceId",deviceId).put("label",Build.MANUFACTURER+" "+Build.MODEL)
        .put("brand","petalix").put("packageName",BuildConfig.APPLICATION_ID)
        .put("clientVersion",BuildConfig.VERSION_NAME).put("clientBuild",BuildConfig.VERSION_CODE)
        .put("capabilities",JSONArray(listOf("management-v2","management-v3","playlist-v1","kaptain-manifest-v1","device-updates-v1","service-hubs-v1","panel-session-origin-v1")))
        .put("settingsRevision",prefs.getString("revision","")).put("settingsApplied",nativeSettings.controlCount).put("settingsPending",0)

    private suspend fun acknowledge(){
        val data=report().put("playlistRevision",prefs.getString("playlistRevision",""))
        val channels=repository.channels.value.filter { it.sourceId=="petalix-managed" }.take(10000)
        val array=JSONArray()
        for(c in channels)array.put(JSONObject().put("id",c.key.substringAfter('|')).put("name",c.name).put("group",c.group))
        val raw=array.toString()
        val digest=java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString(""){"%02x".format(it)}
        val send=channels.isNotEmpty()&&raw.length<1_500_000&&prefs.getString("playlistCatalogHash","")!=digest
        if(send)data.put("playlistCatalog",array)
        post("settings-ack",data,token())
        if(send)prefs.edit().putString("playlistCatalogHash",digest).commit()
    }

    private suspend fun apply(doc:JSONObject)=withContext(Dispatchers.IO+ManagerWrite) {
        val management=doc.optJSONObject("management")?:error("Update Petalix support before activating this preview.")
        require(management.optInt("schemaVersion")==2) { "This settings profile needs a newer app." }
        val revision=management.getString("revision")
        require(revision.matches(Regex("[a-f0-9]{64}")))
        val values=management.getJSONObject("values")
        ManagedValues.validate(values)
        nativeSettings.validate(values)
        if(!prefs.getBoolean("bundledVod",false)||values.getBoolean("mode.lockSources")) {
            addons.ensureBundledAddon()
            check(prefs.edit().putBoolean("bundledVod",true).commit())
        }
        val server=PlaylistSource.cleanXtreamServer(doc.getString("server"))
        require(server.startsWith("https://")||server.startsWith("http://"))
        val username=doc.getString("username");val password=doc.getString("password")
        var changed=false
        live.updatePlaylists { existing ->
            val old=existing.firstOrNull { it.id=="petalix-managed" }
            val fresh=PlaylistSource(id="petalix-managed",name="Petalix",url="",xtreamServer=server,xtreamUsername=username,xtreamPassword=password,importLive=true,importVod=true,streamFormat="auto")
            val same=old!=null&&old.xtreamServer==server&&old.xtreamUsername==username&&old.xtreamPassword==password&&old.importLive&&old.importVod&&old.enabled
            changed=!same
            val assigned=if(same)old!! else fresh
            if(values.getBoolean("mode.lockSources"))listOf(assigned) else listOf(assigned)+existing.filter { it.id!="petalix-managed" }
        }
        if(values.getBoolean("mode.lockSources"))live.updateEpgSources { emptyList() }
        if(nativeSettings.profileId!=prefs.getInt("managedProfile",-1)||revision!=prefs.getString("revision","")||!prefs.getBoolean("bootstrapped",false)||values.getBoolean("mode.lockSettings")) {
            live.updateSettings { s -> s.copy(
                startPage=values.getString("start.page"),startOnLiveTv=values.getString("start.page")=="LIVE_TV",
                autoPlayLastChannel=values.getBoolean("live.autoPlayLast"),
                guideSize=values.getString("live.guideSize"),compactRows=values.getString("live.guideSize")=="COMPACT",
                showPreview=values.getBoolean("live.preview"),showChannelNames=values.getBoolean("live.names"),
                showChannelNumbers=values.getBoolean("live.numbers"),showChannelLogos=values.getBoolean("live.logos"),
                channelSurf=values.getBoolean("live.surfing"),use24HourClock=values.getBoolean("live.clock24"),
                epgRefreshHours=values.getInt("live.epgRefresh"),autoReconnect=values.getBoolean("play.reconnect"),
                autoBackupChannel=values.getBoolean("play.backup"),frameRateMode=if(values.getBoolean("play.frameRate"))"START_STOP" else "OFF",
                audioPassthrough=values.getBoolean("play.passthrough"),onDemandInSidebar=values.getBoolean("vod.visible"),
                onDemandStreamsPosition=if(values.getBoolean("vod.first"))"FIRST" else "LAST",vodMergeDuplicates=values.getBoolean("vod.merge")
            ) }
            live.editSportsSettings { json -> SportsSettings.fromJson(json).copy(
                showInSidebar=values.getBoolean("sports.visible"),region=values.getString("sports.region"),
                hideScores=values.getBoolean("sports.hideScores"),scoreOnInfoBar=values.getBoolean("sports.infoScore"),
                scoringAlerts=values.getBoolean("sports.alerts"),videoWindow=values.getBoolean("sports.video")
            ).toJson() }
            if(!prefs.getBoolean("bootstrapped",false)||values.getBoolean("mode.lockSettings")) {
                onboarding.setHasSeenAuthQrOnFirstLaunch(true)
                experience.setMode(ExperienceMode.ADVANCED)
                experience.setAddonSetupSkipped(true)
                layout.setLayout(HomeLayout.MODERN)
            }
            nativeSettings.apply(values)
            ManagedPolicy.save(context,values)
            check(prefs.edit().putString("revision",revision).putInt("managedProfile",nativeSettings.profileId).putBoolean("bootstrapped",true).putString("startPage",values.getString("start.page")).commit())
        }
        doc.optJSONObject("playlist")?.let { playlist ->
            live.managedPreferences().edit { PanelPlaylist.store(it,playlist) }
            check(prefs.edit().putString("playlistRevision",playlist.optString("revision")).commit())
        }
        if(changed)repository.reloadAfterSync(setOf("petalix-managed"))
    }
    fun startupPage()=prefs.getString("startPage","LIVE_TV")?:"LIVE_TV"

    private suspend fun post(path:String,json:JSONObject,bearer:String?):JSONObject=withContext(Dispatchers.IO) {
        var url=PanelConnectionPolicy.currentOrigin+"/api/device/"+path
        for(attempt in 0 until 3) {
            val request=Request.Builder().url(url).post(json.toString().toRequestBody("application/json".toMediaType()))
            if(bearer!=null)request.header("Authorization","Bearer "+bearer)
            http.newCall(request.build()).execute().use { response ->
                if(response.code in listOf(301,302,303,307,308)) {
                    url=PanelConnectionPolicy.redirected(url,response.header("Location").orEmpty())?:error("The panel address could not be verified. Your saved activation is retained.")
                } else {
                    val raw=response.body?.string().orEmpty()
                    val doc=runCatching { JSONObject(raw) }.getOrElse { error("The panel is unavailable. Retry the connection; your saved activation is retained.") }
                    if(!response.isSuccessful) {
                        if(bearer!=null&&response.code in listOf(401,403)) {
                            check(prefs.edit().putBoolean("accessDenied",true).commit())
                            throw ActivationRequired()
                        }
                        error(doc.optString("error","Could not connect to the panel."))
                    }
                    if(bearer!=null)check(prefs.edit().putBoolean("accessDenied",false).commit())
                    return@withContext doc
                }
            }
        }
        error("The panel redirected too many times. Your saved activation is retained.")
    }

    private fun encryptionKey():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("petalix.session.v2",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("petalix.session.v2",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun storeToken(token:String){val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,encryptionKey());val encrypted=cipher.doFinal(token.toByteArray());check(prefs.edit().putString("token",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+"."+Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit())}
    private fun token():String=runCatching { val parts=prefs.getString("token","")!!.split('.');val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,encryptionKey(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP))) }.getOrDefault("")
}

object ManagedValues {
    private val booleans=setOf("mode.hideSettings","mode.lockSettings","mode.lockSources","live.autoPlayLast","live.preview","live.names","live.numbers","live.logos","live.surfing","live.clock24","play.reconnect","play.backup","play.frameRate","play.passthrough","vod.visible","vod.first","vod.merge","sports.visible","sports.hideScores","sports.infoScore","sports.alerts","sports.video")
    fun validate(values:JSONObject){
        booleans.forEach { require(values.get(it) is Boolean) }
        require(values.getString("start.page") in listOf("HOME","LIVE_TV","SPORTS","ON_DEMAND"))
        require(values.getString("live.guideSize") in listOf("COMPACT","STANDARD","LARGE","XLARGE"))
        require(values.getInt("live.epgRefresh") in listOf(6,12,24))
        require(values.getString("sports.region") in listOf("US","UK","IE","CA","AU","OTHER"))
    }
}
