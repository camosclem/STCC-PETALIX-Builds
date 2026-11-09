package com.nuvio.tv.petalix

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.livetv.data.LiveTvPreferences
import com.nuvio.tv.livetv.data.LiveTvRepository
import com.nuvio.tv.livetv.model.PlaylistSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

class PetalixActivationRequired : Exception("Activate this TV with your Petalix PIN.")

@Singleton
class PetalixManagedCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val live: LiveTvPreferences,
    private val collections: com.nuvio.tv.data.local.CollectionsDataStore,
    private val tmdb: com.nuvio.tv.data.local.TmdbSettingsDataStore,
    private val layout: com.nuvio.tv.data.local.LayoutPreferenceDataStore,
    private val experience: com.nuvio.tv.data.local.ExperienceModeDataStore,
    private val profiles: com.nuvio.tv.core.profile.ProfileManager,
    private val repository: LiveTvRepository
) {
    // EXACT old preference and keystore aliases, so an in-place signed update retains sessions.
    private val prefs = context.getSharedPreferences("petalix_session_v2", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private val deviceId: String = prefs.getString("device", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).commit() }

    val hasSavedActivation: Boolean get() = token().isNotEmpty()
    val homeServicesMigrationPending: Boolean get() = !prefs.getBoolean("nativeHomeServicesV4:${profiles.activeProfileId.value}", false)
    val ready: Boolean get() = hasSavedActivation && !prefs.getBoolean("accessDenied", false) && runCatching { Instant.parse(prefs.getString("expires", "")).toEpochMilli() > System.currentTimeMillis() }.getOrDefault(false)

    suspend fun activate(pin: String) = mutex.withLock {
        require(pin.matches(Regex("[0-9]{8}"))) { "Enter your 8-digit activation PIN." }
        val doc = post("activate", report().put("code", pin), null)
        val freshToken = doc.optString("token")
        require(freshToken.isNotBlank()) { "The panel did not return an activation." }
        require(listOf("server", "username", "password").all { doc.optString(it).isNotBlank() }) {
            "Your Petalix account has no playlist assigned. Open the panel and check its provider login."
        }
        applySettings(doc)
        storeToken(freshToken)
        persistSession(doc)
        acknowledge()
    }

    suspend fun refresh() = mutex.withLock {
        if (!hasSavedActivation) throw PetalixActivationRequired()
        val doc = post("status", report(), token())
        applySettings(doc)
        persistSession(doc)
        acknowledge()
    }

    init { PetalixRuntimeConfig.load(context) }

    /** Seed each profile from bundled assets before contacting the panel. */
    suspend fun observeProfiles() {
        profiles.activeProfileReady.first { it }
        profiles.activeProfileId.collectLatest { profileId ->
            mutex.withLock { ensureLocalHome(profileId) }
        }
    }

    private suspend fun ensureLocalHome(profileId: Int) {
        if (prefs.getInt("nativeCatalogVersion:$profileId", 0) < 4) {
            val native = context.assets.open("petalix/streaming-services.json").bufferedReader().use { it.readText() }
            val existing = collections.getCurrentCollections(profileId)
            val imported = collections.importFromJson(native)
            require(imported.size == 1 && imported[0].folders.size == 9)
            val seed = imported.single()
            val removedServices = setOf("folder-73STREAM", "folder-300STREAM")
            val prior = existing.firstOrNull { it.id == seed.id }?.let { collection ->
                collection.copy(folders = collection.folders.filterNot { it.id in removedServices }.map { folder ->
                    if (folder.id == "folder-petalix-britbox") folder.copy(focusGifEnabled = true) else folder
                })
            }
            val merged = prior?.copy(folders = prior.folders + seed.folders.filter { fresh -> prior.folders.none { it.id == fresh.id } }) ?: seed
            collections.setCollections(existing.filter { it.id != seed.id } + merged, profileId)
            tmdb.setEnabled(true)
            tmdb.setModernHomeEnabled(true)
            check(prefs.edit().putBoolean("nativeDefaultsSeeded", true).putInt("nativeCatalogVersion:$profileId", 4).commit())
        }
        if (!prefs.getBoolean("nativeHomeServicesV4:$profileId", false)) {
            // Restore the service row as the first experience after a Test 2 update.
            live.updateSettings { it.copy(showInSidebar = true, openOn = "HOME") }
            com.nuvio.tv.livetv.startup.StartPage.save(context, null)
            layout.setSidebarCollapsedByDefault(true)
            experience.setMode(com.nuvio.tv.domain.model.ExperienceMode.ADVANCED)
            layout.setLayout(com.nuvio.tv.domain.model.HomeLayout.MODERN)
            tmdb.setEnabled(true)
            tmdb.setModernHomeEnabled(true)
            tmdb.setUseTrailers(true)
            layout.setFocusedPosterBackdropExpandEnabled(true)
            layout.setFocusedPosterBackdropExpandDelaySeconds(2)
            layout.setFocusedPosterBackdropTrailerEnabled(true)
            layout.setFocusedPosterBackdropTrailerMuted(true)
            layout.setFocusedPosterBackdropTrailerPlaybackTarget(
                com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget.HERO_MEDIA
            )
            check(prefs.edit().putBoolean("nativeHomeServicesV4:$profileId", true).putBoolean("nativeHomeServicesV3", true).commit())
        }
    }

    private suspend fun applySettings(doc: JSONObject) {
        doc.optJSONObject("nativeConfig")?.optString("tmdbApiKey")?.let { PetalixRuntimeConfig.store(context, it) }
        profiles.activeProfileReady.first { it }
        ensureLocalHome(profiles.activeProfileId.value)
        val serverRaw = doc.optString("server")
        val username = doc.optString("username")
        val password = doc.optString("password")
        if (serverRaw.isBlank() || username.isBlank() || password.isBlank()) {
            // Keep existing working provider configuration on transient panel responses.
            return
        }
        val server = PlaylistSource.cleanXtreamServer(serverRaw)
        require(server.startsWith("http://") || server.startsWith("https://"))
        var changed = false
        live.updatePlaylists { existing ->
            val old = existing.firstOrNull { it.id == "petalix-managed" }
            val same = old != null && old.xtreamServer == server &&
                old.xtreamUsername == username && old.xtreamPassword == password
            changed = !same
            val assigned = if (same) old!! else PlaylistSource(
                id = "petalix-managed", name = "Petalix", url = "",
                xtreamServer = server, xtreamUsername = username, xtreamPassword = password,
                importLive = true, importVod = true, streamFormat = "auto"
            )
            listOf(assigned) + existing.filter { it.id != "petalix-managed" }
        }
        if (changed) repository.reloadAfterSync(setOf("petalix-managed"))
    }

    private fun persistSession(doc: JSONObject) {
        val expiry = doc.optString("expires")
        if (expiry.isNotBlank()) {
            runCatching { Instant.parse(expiry) }.getOrElse { error("Invalid subscription expiry") }
            check(prefs.edit().putString("expires", expiry).putLong("verified", System.currentTimeMillis()).commit())
        }
    }

    private fun report() = JSONObject()
        .put("deviceId", deviceId)
        .put("label", Build.MANUFACTURER + " " + Build.MODEL)
        .put("brand", "petalix")
        .put("packageName", BuildConfig.APPLICATION_ID)
        .put("clientVersion", BuildConfig.VERSION_NAME)
        .put("clientBuild", BuildConfig.VERSION_CODE)
        .put("capabilities", JSONArray(listOf("panel-session-origin-v1", "petalix-stock-native-v1")))
        .put("settingsRevision", prefs.getString("revision", ""))
        .put("playlistRevision", prefs.getString("playlistRevision", ""))

    private suspend fun acknowledge() {
        runCatching { post("settings-ack", report(), token()) }
    }

    private suspend fun post(path: String, data: JSONObject, bearer: String?): JSONObject =
        withContext(Dispatchers.IO) {
            var origin = BuildConfig.PETALIX_PANEL
            repeat(3) {
                val request = Request.Builder()
                    .url(origin.trimEnd('/') + "/api/device/" + path)
                    .post(data.toString().toRequestBody("application/json".toMediaType()))
                if (bearer != null) request.header("Authorization", "Bearer $bearer")
                http.newCall(request.build()).execute().use { response ->
                    if (response.code in listOf(301,302,303,307,308)) {
                        val next = response.header("Location").orEmpty()
                        val parsed = next.toHttpUrlOrNull()
                            ?: error("Panel address is unavailable; saved activation retained.")
                        require(parsed.isHttps && parsed.port == 443 && parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.encodedQuery == null && parsed.fragment == null && parsed.encodedPath == "/api/device/" + path &&
                            parsed.host in setOf(
                                "petalix-control.pinkgiant5.chatgpt.site",
                                "petalix-control.caroliinaconsultant.chatgpt.site"
                            )) { "Unrecognized panel redirect; saved activation retained." }
                        origin = "https://" + parsed.host
                    } else {
                        val payload = response.body?.string().orEmpty()
                        val doc = runCatching { JSONObject(payload) }.getOrElse {
                            error("Panel response unavailable; saved activation retained.")
                        }
                        if (response.code in listOf(401, 403) && bearer != null) {
                            prefs.edit().putBoolean("accessDenied", true).commit()
                            throw PetalixActivationRequired()
                        }
                        if (!response.isSuccessful)
                            error(doc.optString("error", "Could not reach Petalix Control."))
                        prefs.edit().putBoolean("accessDenied", false).commit()
                        return@withContext doc
                    }
                }
            }
            error("The panel redirected too many times; saved activation retained.")
        }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("petalix.session.v2", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("petalix.session.v2",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun storeToken(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(value.toByteArray())
        check(prefs.edit().putString("token",
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }

    private fun token(): String = runCatching {
        val parts = prefs.getString("token", "")!!.split('.')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    }.getOrDefault("")
}
