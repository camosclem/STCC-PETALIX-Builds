package com.nuvio.tv.updater

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.api.GitHubReleaseApi
import com.nuvio.tv.updater.model.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal class NoEligibleUpdateException(channel:UpdateChannel):IllegalStateException("No Petalix update is published for this app.")

@Singleton
class UpdateRepository @Inject constructor(private val gitHubReleaseApi:GitHubReleaseApi,private val coordinator:com.nuvio.tv.petalix.PetalixManagedCoordinator) {
    val deviceUpdateRequests get()=coordinator.updateRequests
    private val http=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
    suspend fun getLatestUpdate(channel:UpdateChannel):Result<AppUpdate> = withContext(Dispatchers.IO) {
        runCatching {
            if(coordinator.ready)return@runCatching parse(coordinator.appUpdate(),channel)
            val request=Request.Builder().url(BuildConfig.PETALIX_PANEL+"/api/app/update?brand=petalix&packageName="+BuildConfig.APPLICATION_ID+"&versionCode="+BuildConfig.VERSION_CODE).build()
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Petalix updates are unavailable." }
                parse(JSONObject(response.body?.string().orEmpty()),channel)
            }
        }
    }
    private fun parse(payload:JSONObject,channel:UpdateChannel):AppUpdate {
                val doc=payload.optJSONObject("update")?:throw NoEligibleUpdateException(channel)
                require(doc.getString("packageName")==BuildConfig.APPLICATION_ID) { "No update is published for this preview." }
                require(doc.getInt("versionCode")>BuildConfig.VERSION_CODE)
                require(doc.getString("sha256").matches(Regex("[a-f0-9]{64}")))
                require(doc.getString("certificateSha256")=="b6809a3733b57474d01a7b77bcddef1241b7f85f35f04142d52ed68071cd1ba1")
                require(doc.getString("downloadUrl").startsWith("https://github.com/"))
                return AppUpdate(tag=doc.getString("versionName"),title="Petalix "+doc.getString("versionName"),notes=doc.optString("notes"),
                    releaseUrl=doc.optString("releaseUrl"),assetName=doc.getString("assetName"),assetUrl=doc.getString("downloadUrl"),
                    assetSizeBytes=doc.getLong("sizeBytes"),assetSha256=doc.getString("sha256"),expectedVersionCode=doc.getInt("versionCode"))
    }

}
