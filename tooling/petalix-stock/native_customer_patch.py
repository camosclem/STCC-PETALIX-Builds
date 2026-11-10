"""Customer package, panel updater and native preference bindings for pinned 13.1."""
from pathlib import Path
import shutil,json
HERE=Path(__file__).resolve().parent

def once(p,old,new):
 s=p.read_text();assert s.count(old)==1,(p,str(old)[:80],s.count(old));p.write_text(s.replace(old,new))

def apply(root, *, enable_updates=True):
 source=root/'app/src/main/java/com/nuvio/tv';dest=source/'petalix'
 with (root/'app/proguard-rules.pro').open('a') as stream:stream.write('\n-keep class com.nuvio.tv.petalix.** { *; }\n')
 for name in ['ManagedNativeSettings.kt','PetalixPanelSettings.kt','PanelChannelIdentity.kt','PetalixServiceCatalog.kt']:
  shutil.copyfile(HERE/name,dest/name)
 for name in ['managed-native-controls.json','pin-settings-map.json']:
  shutil.copyfile(HERE/name,root/'app/src/main/assets'/name)
 # Expose the existing singleton datastores; never create a second instance for a file.
 for file,anchor,addition in [
 ('livetv/data/LiveTvPreferences.kt','    private object Keys {','    internal fun managedPreferences() = store\n    internal fun managedDevicePreferences() = deviceStore\n'),
 ('data/local/DeviceLocalPlayerPreferences.kt','    private val aspectModeKey =','    internal fun managedPreferences() = store\n\n    private val aspectModeKey ='),
 ('data/local/ProfileDataStore.kt','    private val dataStore = context.profileDataStore','    private val dataStore = context.profileDataStore\n    internal fun managedPreferences() = dataStore'),
 ('data/local/SentrySettingsDataStore.kt','    private val dataStore = context.sentrySettingsDataStore','    private val dataStore = context.sentrySettingsDataStore\n    internal fun managedPreferences() = dataStore'),
 ('core/torrent/TorrentSettings.kt','    val settings: Flow<TorrentSettingsData>','    internal fun managedPreferences() = context.torrentDataStore\n    val settings: Flow<TorrentSettingsData>')]:
  once(source/file,anchor,addition+anchor if file.startswith('livetv') else addition)
 tests=root/'app/src/test/java/com/nuvio/tv/petalix';tests.mkdir(parents=True,exist_ok=True)
 shutil.copyfile(HERE.parent/'android-build/PetalixPanelControlsTest.kt',tests/'PetalixPanelControlsTest.kt')
 shutil.copyfile(HERE/'PanelChannelIdentityTest.kt',tests/'PanelChannelIdentityTest.kt')
 repo=source/'livetv/data/LiveTvRepository.kt'
 once(repo,'val baseKey = "${pl.id}|${e.tvgId ?: ""}|${e.name}"', 'val baseKey = com.nuvio.tv.petalix.PanelChannelIdentity.key(pl.id, e.url, "${pl.id}|${e.tvgId ?: ""}|${e.name}")')
 rename=source/'livetv/data/RenamedChannels.kt'
 once(rename,'        val byLink = HashMap<String, MutableList<LiveChannel>>()','        val byProvider = HashMap<String, MutableList<LiveChannel>>()\n        val byLink = HashMap<String, MutableList<LiveChannel>>()')
 once(rename,'            byLink.getOrPut(c.sourceId','''            if (c.sourceId == "petalix-managed") com.nuvio.tv.petalix.PanelChannelIdentity.streamId(c.url)?.let { id ->
                byProvider.getOrPut(c.sourceId + "\\n" + id) { ArrayList(1) } += c
            }
            byLink.getOrPut(c.sourceId''')
 once(rename,'            val hit = one(byLink, links[old])','''            val provider = if (rest.startsWith("stream:")) rest.removePrefix("stream:") else links[old]?.let { com.nuvio.tv.petalix.PanelChannelIdentity.streamId(it) }
            val hit = one(byProvider, provider) ?: one(byLink, links[old])''')
 prefs=source/'livetv/data/LiveTvPreferences.kt'
 once(prefs,'    suspend fun clearFavorites() {','''    suspend fun mergePanelFavorites(keys: List<String>, profileId: Int) {
        store.edit { p ->
            val key = my(Keys.favorites, profileId)
            val current = decodeStringList(p[key])
            p[key] = encodeStringList((current + keys).distinct())
        }
    }
    suspend fun clearFavorites() {''')
 # Managed optional addon assignments use the public native add/remove methods.
 p=source/'data/local/AddonPreferences.kt'
 once(p,'    suspend fun ensureMigrated() {', '''    private suspend fun applyPanelAddon(key: String, manifest: String?) {
        val pref = stringPreferencesKey("managed_panel_" + key)
        val old = store().data.first()[pref]
        if (old == manifest) return
        if (manifest != null) addAddon(manifest)
        if (old != null) removeAddon(old)
        store().edit { if (manifest == null) it.remove(pref) else it[pref] = manifest }
    }
    suspend fun applyManagedCncverse(manifest: String?) = applyPanelAddon("cncverse", manifest)
    suspend fun applyManagedPengu(manifest: String?) = applyPanelAddon("pengu", manifest)
    suspend fun applyManagedKaptain(manifest: String?) = applyPanelAddon("kaptain", manifest)
    suspend fun ensureMigrated() {''')
 coordinator=dest/'PetalixManagedCoordinator.kt'
 once(coordinator,'    private val repository: LiveTvRepository','    private val panelSettings: PetalixPanelSettings,\n    private val repository: LiveTvRepository')
 once(coordinator,'        val serverRaw = doc.optString("server")','''        doc.optJSONObject("nativeConfig")?.optJSONArray("epgSources")?.let { sources ->
            val guides = (0 until sources.length()).map { i ->
                val source = sources.getJSONObject(i)
                com.nuvio.tv.livetv.model.EpgSource(id="petalix-panel-epg:" + source.getString("id"),name=source.getString("name"),url=source.getString("url"))
            }
            var changed = false
            live.updateEpgSources { existing ->
                val kept = existing.filterNot { it.id.startsWith("petalix-panel-epg:") }
                val prior = existing.filter { it.id.startsWith("petalix-panel-epg:") }
                changed = prior.map { it.id to it.url } != guides.map { it.id to it.url }
                kept + guides.map { guide -> prior.firstOrNull { it.id == guide.id && it.url == guide.url }?.copy(name=guide.name) ?: guide }
            }
            if (changed) repository.reloadAfterSync(guides.map { it.id }.toSet())
        }
        val serverRaw = doc.optString("server")''')
 once(coordinator,'    private suspend fun applySettings(doc: JSONObject) {','''    private suspend fun applySettings(doc: JSONObject) {
        applyPanelSettings(doc)
''')
 # Apply overrides after profile defaults, before choosing playlist credentials.
 once(coordinator,'        applyPanelSettings(doc)\n','')
 once(coordinator,'        ensureLocalHome(profiles.activeProfileId.value)\n','        ensureLocalHome(profiles.activeProfileId.value)\n        applyPanelSettings(doc)\n')
 once(coordinator,'            listOf(assigned) + existing.filter { it.id != "petalix-managed" }','            if (PetalixPanelPolicy.state.value.third) listOf(assigned) else listOf(assigned) + existing.filter { it.id != "petalix-managed" }')
 once(coordinator,'        runCatching { post("settings-ack", report(), token()) }', '        runCatching { post("settings-ack", report(), token()) }\n        runCatching { syncFavorites() }')
 once(coordinator,'    private fun persistSession(doc: JSONObject) {','''    private suspend fun syncFavorites() {
        val channels = repository.channels.value
        if (channels.isEmpty()) return
        val profile = profiles.activeProfileId.value
        val restored = post("favorites", report().put("action", "poll").put("profileId", profile), token()).optJSONObject("restore")
        if (restored != null && profiles.activeProfileId.value == profile) {
            val entries = restored.getJSONArray("favorites")
            val keys = (0 until entries.length()).map { index ->
                val entry = entries.getJSONObject(index)
                val id = entry.optString("providerId")
                if (id.isNotBlank()) PanelChannelIdentity.providerKey(id) else entry.getString("key")
            }
            live.mergePanelFavorites(keys, profile)
            post("favorites", report().put("action", "ack").put("requestId", restored.getString("requestId")).put("profileId", profile), token())
        }
        if (profiles.activeProfileId.value != profile) return
        val current = live.userState.first().favorites
        if (profiles.activeProfileId.value != profile) return
        val byKey = channels.associateBy { it.key }
        val entries = JSONArray()
        for (key in current) {
            val c = byKey[key]
            entries.put(JSONObject().put("key", key).put("providerId", c?.takeIf { it.sourceId == "petalix-managed" }?.let { PanelChannelIdentity.streamId(it.url) } ?: if (key.startsWith("petalix-managed|stream:")) key.substringAfter("|stream:") else "")
                .put("name", c?.name.orEmpty()).put("epgId", c?.tvgId.orEmpty()))
        }
        post("favorites", report().put("action", "backup").put("profileId", profile).put("favorites", entries), token())
    }
    private fun persistSession(doc: JSONObject) {''')
 once(coordinator,'    private fun persistSession(doc: JSONObject) {','''    private suspend fun applyPanelSettings(doc: JSONObject) {
        try {
            val result = panelSettings.apply(doc)
            prefs.edit().putString("revision", result).putInt("settingsPending", 0).putBoolean("settingsError", false).commit()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { prefs.edit().putInt("settingsPending", 1).putBoolean("settingsError", true).commit() }
'''+('''        doc.optJSONObject("appUpdate")?.optString("requestId")?.takeIf { it.isNotBlank() }?.let { pendingUpdateRequest.value = it }
    }
    private val pendingUpdateRequest = kotlinx.coroutines.flow.MutableStateFlow("")
    val updateRequests: kotlinx.coroutines.flow.StateFlow<String> get() = pendingUpdateRequest
    suspend fun appUpdate(): JSONObject = post("update", report(), token())
''' if enable_updates else '''    }
''')+'''
    private fun persistSession(doc: JSONObject) {''')
 once(coordinator,'JSONArray(listOf("panel-session-origin-v1", "petalix-stock-native-v1"))','JSONArray(listOf("panel-session-origin-v1", "petalix-stock-native-v1", "management-v2", "management-v3", "management-v4", "home-services-first-v1", "service-hubs-v1", "native-panel-settings-v1", "favorites-backup-v1"'+(', "device-updates-v1"' if enable_updates else '')+'))')
 once(coordinator,'        .put("playlistRevision", prefs.getString("playlistRevision", ""))','''        .put("playlistRevision", prefs.getString("playlistRevision", ""))
        .put("settingsApplied", if (prefs.getBoolean("settingsError", false)) 0 else panelSettings.appliedCount)
        .put("hideOtherLocals", panelSettings.hideOtherLocals)
        .put("settingsPending", prefs.getInt("settingsPending", 0))
        .put("settingsError", prefs.getBoolean("settingsError", false))''')
 nav=source/'ui/navigation/NuvioNavHost.kt'
 once(nav,'    NavHost(\n','''    val petalixBackStack by navController.currentBackStackEntryAsState()
    val petalixPolicy by com.nuvio.tv.petalix.PetalixPanelPolicy.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(petalixBackStack?.destination?.route, petalixPolicy) {
        if (com.nuvio.tv.petalix.PetalixPanelPolicy.blocked(petalixBackStack?.destination?.route.orEmpty())) {
            if (!navController.popBackStack()) navController.navigate(Screen.Home.route)
        }
    }
    NavHost(
''')
 services=dest/'ui2/PetalixServices.kt'
 once(services,'@ApplicationContext context:Context):ViewModel()', '@ApplicationContext context:Context,private val panelFactory:com.nuvio.tv.data.local.ProfileDataStoreFactory,private val panelProfiles:com.nuvio.tv.core.profile.ProfileManager):ViewModel()')
 once(services,'            val rows=results.mapNotNull','''            val dedup=panelFactory.get(panelProfiles.activeProfileId.value,"layout_settings").data.first()[androidx.datastore.preferences.core.booleanPreferencesKey("service_hub_deduplicate")] ?: true
            val rows=results.mapNotNull''')
 once(services,'                old.copy(rows=merged,loading=false','''                val seen=hashSetOf<String>()
                val visible=if(!dedup)merged else merged.map { row -> row.copy(items=row.items.filter { item -> seen.add("${item.apiType}:${item.id}") }) }
                old.copy(rows=visible,loading=false''')
 if not enable_updates: return
 full=root/'app/src/full/java/com/nuvio/tv/updater'
 shutil.copyfile(HERE/'PanelUpdateRepository.kt',full/'UpdateRepository.kt')
 once(full/'UpdateViewModel.kt','import kotlinx.coroutines.flow.first','import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.flow.collect')
 once(full/'model/AppUpdate.kt','val assetSha256: String? = null','val assetSha256: String? = null,\n    val expectedVersionCode: Int? = null')
 once(full/'UpdateViewModel.kt','    init {','''    init {
        viewModelScope.launch {
            updateRepository.deviceUpdateRequests.collect { request ->
                if (request.isNotBlank()) checkForUpdates(force = true, showNoUpdateFeedback = false)
            }
        }''')
 once(full/'UpdateViewModel.kt','val remoteNewer = VersionUtils.isRemoteNewer(update.tag, BuildConfig.VERSION_NAME)','val remoteNewer = update.expectedVersionCode?.let { it > BuildConfig.VERSION_CODE } ?: VersionUtils.isRemoteNewer(update.tag, BuildConfig.VERSION_NAME)')
