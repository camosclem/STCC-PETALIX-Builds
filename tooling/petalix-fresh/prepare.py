"""Apply a small Petalix overlay to a clean Nuvio + IPTV beta.4.1 checkout."""
import argparse
import importlib.util
import json
import re
from pathlib import Path
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
TOOLING = HERE.parent
STOCK = TOOLING / "petalix-stock"
UPSTREAM = "bc02df448c7836931544f66d255acf344f73dd84"
PANEL = "https://petalix-control.caroliinaconsultant.chatgpt.site"
PACKAGE = "com.petalix.iptv.preview"


def edit(path, old, new):
    text = path.read_text()
    if text.count(old) != 1:
        raise ValueError(f"Expected a unique upstream anchor in {path}: {old[:70]!r}")
    path.write_text(text.replace(old, new, 1))


def prepare(root, version_code):
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    if head != UPSTREAM or version_code <= 1795632277:
        raise ValueError("Use the pinned clean upstream checkout and a newer build number.")
    app = root / "app"
    java = app / "src/main/java/com/nuvio/tv"
    petalix = java / "petalix"
    petalix.mkdir()
    for name in ("PetalixActivationActivity.kt", "PetalixManagedCoordinator.kt"):
        shutil.copy2(TOOLING / "petalix-clean/android" / name, petalix)
    for name in ("PanelChannelIdentity.kt", "PetalixRuntimeConfig.kt", "PetalixLocalsActivity.kt",
                 "PetalixLocalStations.kt", "PetalixLocalCandidate.kt"):
        shutil.copy2(STOCK / name, petalix)
    shutil.copy2(HERE / "PetalixHome.kt", petalix)
    tests = app / "src/test/java/com/nuvio/tv/petalix"
    tests.mkdir(parents=True, exist_ok=True)
    shutil.copy2(HERE / "PetalixHomeTest.kt", tests)

    assets = app / "src/main/assets/petalix"
    assets.mkdir(parents=True)
    shutil.copytree(STOCK / "home-art", assets / "home-art")
    shutil.copy2(TOOLING / "android-build/local-stations.json", assets)
    document = json.loads((STOCK / "streaming-services.json").read_text())
    document[0]["showAllTab"] = False
    for folder in document[0]["folders"]:
        folder["hideTitle"] = False
        if folder["id"] == "folder-petalix-britbox":
            folder.pop("titleLogoUrl", None)
    (assets / "streaming-services.json").write_text(json.dumps(document, ensure_ascii=False))
    (assets / "upstream-release.json").write_text(
        json.dumps({"tag": "1.2.0-beta.4.1", "sha": UPSTREAM, "package": PACKAGE})
    )

    gradle = app / "build.gradle.kts"
    edit(gradle, 'applicationId = "com.nuvio.tv"', f'applicationId = "{PACKAGE}"')
    edit(gradle, 'variant.applicationId.set(if (testBuild) "com.nuvio.livetv.test" else "com.nuvio.livetv")',
         f'variant.applicationId.set("{PACKAGE}")')
    with gradle.open("a") as f:
        f.write(f'''
android.defaultConfig.versionCode = {version_code}
android.defaultConfig.versionName = "1.2.0-petalix.panel{version_code}"
android.defaultConfig.buildConfigField("String", "PETALIX_PANEL", buildConfigString("{PANEL}"))
android.splits.abi.isEnable = false
android.defaultConfig.ndk.abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
''')
    brand_spec = importlib.util.spec_from_file_location("brand", TOOLING / "petalix-clean/prepare_stock.py")
    brand = importlib.util.module_from_spec(brand_spec)
    brand_spec.loader.exec_module(brand)
    logo = TOOLING / "android-build/petalix_metallic_logo.png"
    for res in (app / "src").glob("*/res"):
        for file in res.glob("values*/strings.xml"):
            raw = file.read_bytes()
            updated = re.sub(rb'(<string\b[^>]*\bname="app_name"[^>]*>)[^<]*(</string>)[ \t]*(?=\r?$)',
                             rb'\g<1>Petalix\g<2>', raw, flags=re.MULTILINE)
            if updated != raw:
                file.write_bytes(updated)
        brand.update_artwork(res, logo)

    manifest = app / "src/main/AndroidManifest.xml"
    edit(manifest, '        <meta-data\n            android:name="io.sentry.auto-init"',
         '        <activity android:name=".petalix.PetalixActivationActivity" android:exported="false" android:screenOrientation="landscape" />\n'
         '        <activity android:name=".petalix.PetalixLocalsActivity" android:exported="false" android:screenOrientation="landscape" />\n'
         '        <meta-data\n            android:name="io.sentry.auto-init"')
    main = java / "MainActivity.kt"
    edit(main, "open class MainActivity : ComponentActivity() {",
         "open class MainActivity : ComponentActivity() {\n"
         "    @javax.inject.Inject lateinit var petalix: com.nuvio.tv.petalix.PetalixManagedCoordinator")
    edit(main, "        installSplashScreen()\n        super.onCreate(savedInstanceState)", """        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (!petalix.ready) {
            startActivity(Intent(this, com.nuvio.tv.petalix.PetalixActivationActivity::class.java))
            finish()
            return
        }
        lifecycleScope.launch { petalix.seedHome() }
        lifecycleScope.launch {
            androidx.lifecycle.repeatOnLifecycle(this@MainActivity.lifecycle, androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    try {
                        petalix.refresh()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: com.nuvio.tv.petalix.PetalixActivationRequired) {
                        startActivity(Intent(this@MainActivity, com.nuvio.tv.petalix.PetalixActivationActivity::class.java))
                        finish()
                        return@repeatOnLifecycle
                    } catch (_: Exception) { }
                    kotlinx.coroutines.delay(15_000)
                }
            }
        }""")
    # Use the lifecycle extension on the owner; closing the app stops device check-ins.
    edit(main, "import androidx.lifecycle.lifecycleScope", "import androidx.lifecycle.lifecycleScope\nimport androidx.lifecycle.repeatOnLifecycle")
    edit(main, "androidx.lifecycle.repeatOnLifecycle(this@MainActivity.lifecycle, androidx.lifecycle.Lifecycle.State.STARTED)",
         "this@MainActivity.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED)")
    coordinator = petalix / "PetalixManagedCoordinator.kt"
    edit(coordinator, "import kotlinx.coroutines.Dispatchers",
         "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.flow.collectLatest\nimport kotlinx.coroutines.flow.first")
    edit(coordinator, "    private val repository: LiveTvRepository\n",
         "    private val repository: LiveTvRepository,\n"
         "    private val profiles: com.nuvio.tv.core.profile.ProfileManager,\n"
         "    private val collections: com.nuvio.tv.data.local.CollectionsDataStore\n")
    edit(coordinator, "    suspend fun activate(pin: String)", """    init { PetalixRuntimeConfig.load(context) }

    private val pendingUpdate = kotlinx.coroutines.flow.MutableStateFlow("")
    val updateRequests: kotlinx.coroutines.flow.StateFlow<String> get() = pendingUpdate
    suspend fun appUpdate(): JSONObject = post("update", report(), token())

    val localsZip: String get() = prefs.getString("localsZip", "").orEmpty()

    fun dismissLocalsPrompt() {
        prefs.edit().putBoolean("localsPromptResolvedV2", true).apply()
    }

    internal suspend fun lookupLocals(zip: String): PetalixLocalStations.Result =
        PetalixLocalStations(context, http).lookup(zip) {
            repository.ensureLoaded()
            kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                repository.channels.first { it.isNotEmpty() }
            }.orEmpty()
        }

    suspend fun localKeys(): List<String> = live.petalixLocalKeys()

    suspend fun saveLocals(zip: String, keys: List<String>) {
        require(zip.matches(Regex("[0-9]{5}")) && keys.isNotEmpty()) { "Choose a ZIP code and local channels." }
        live.savePetalixLocalKeys(keys)
        prefs.edit().putString("localsZip", zip).putBoolean("localsPromptResolvedV2", true).apply()
    }

    suspend fun seedHome() {
        profiles.activeProfileReady.first { it }
        profiles.activeProfileId.collectLatest { profileId ->
            val bundled = context.assets.open("petalix/streaming-services.json").bufferedReader().use { it.readText() }
            val serviceCollection = collections.importFromJson(bundled).single()
            collections.addPetalixDefaults(profileId, listOf(liveCollection(), serviceCollection))
        }
    }

    suspend fun activate(pin: String)""")
    edit(coordinator, """        doc.optJSONObject("playlist")?.let { playlist ->
            if (playlist.has("rules") && playlist.has("revision")) {
                live.applyPetalixPlaylistRules(playlist)
                prefs.edit().putString("playlistRevision", playlist.optString("revision")).apply()
            }
        }
        if (changed) repository.reloadAfterSync(setOf("petalix-managed"))""", """        val feedRevision = config?.optString("guidePoolRevision").orEmpty()
        if (changed || (feedRevision.isNotBlank() && feedRevision != prefs.getString("playlistRevision", ""))) {
            repository.reloadAfterSync(setOf("petalix-managed"))
            prefs.edit().putString("playlistRevision", feedRevision).apply()
        }""")
    edit(coordinator, """    private suspend fun applySettings(doc: JSONObject) {
        val serverRaw""", """    private suspend fun applySettings(doc: JSONObject) {
        pendingUpdate.value = doc.optJSONObject("appUpdate")?.optString("requestId").orEmpty()
        val config = doc.optJSONObject("nativeConfig")
        config?.optString("tmdbApiKey")?.takeIf { it.matches(Regex("[a-fA-F0-9]{32}")) }
            ?.let { PetalixRuntimeConfig.store(context, it) }
        val serverRaw""")
    edit(coordinator, 'JSONArray(listOf("playlist-v1"))',
         'JSONArray(listOf("panel-session-origin-v1", "petalix-stock-native-v1", "guide-only-feeds-v1", "device-updates-v1"))')

    preferences = java / "data/local/AddonPreferences.kt"
    edit(preferences, "import kotlinx.coroutines.flow.map", "import kotlinx.coroutines.flow.map\nimport kotlinx.coroutines.flow.onStart")
    edit(preferences, "    val addonEnabledStates: Flow", """    private val retiredAssignment = stringPreferencesKey("managed_panel_pengu")

    private fun isRetiredManifest(url: String, assigned: String?) =
        canonicalizeUrl(url) == assigned || url.lowercase().filter(Char::isLetterOrDigit).contains("penguplay")

    private suspend fun clearRetiredManifest(profileId: Int) {
        factory.get(profileId, FEATURE).edit { preferences ->
            val assigned = preferences[retiredAssignment]?.let(::canonicalizeUrl)
            val urls = getCurrentList(preferences)
            val kept = urls.filterNot { isRetiredManifest(it, assigned) }
            if (kept != urls) {
                preferences[orderedUrlsKey] = gson.toJson(kept)
                preferences.remove(legacyUrlsKey)
            }
            val keys = getCurrentEnabledStates(preferences)
            val states = keys.filterKeys { !isRetiredManifest(it, assigned) }
            if (states != keys) preferences[addonEnabledStatesKey] = gson.toJson(states)
            preferences.remove(retiredAssignment)
        }
    }

    val addonEnabledStates: Flow""")
    edit(preferences, """                legacySet.toList()
            }
        }
    }

    private val retiredAssignment""", """                legacySet.toList()
            }
        }.onStart { clearRetiredManifest(pid) }
    }

    private val retiredAssignment""")
    edit(preferences, "    suspend fun addAddon(url: String): Boolean {",
         "    suspend fun addAddon(url: String): Boolean {\n        if (isRetiredManifest(url, null)) return false")

    collections = java / "data/local/CollectionsDataStore.kt"
    edit(collections, "    suspend fun addCollection(collection: Collection) {", """    internal suspend fun addPetalixDefaults(profileId: Int, defaults: List<Collection>) {
        val marker = stringPreferencesKey("petalix_seeded_collections")
        store(profileId).edit { prefs ->
            if (prefs[marker] == "1") return@edit
            val current = parseCollections(prefs[collectionsKey])
            val merged = defaults.map { seed -> current.firstOrNull { it.id == seed.id } ?: seed } +
                current.filter { item -> defaults.none { it.id == item.id } }
            prefs[collectionsKey] = gson.toJson(merged.map { it.toSerializable() })
            prefs[marker] = "1"
        }
    }

    suspend fun addCollection(collection: Collection) {""")

    live_preferences = java / "livetv/data/LiveTvPreferences.kt"
    edit(live_preferences, "    suspend fun createCustomGroup(name: String): String {", """    suspend fun petalixLocalKeys(): List<String> =
        decodeCustomGroups(store.data.first()[my(Keys.customGroups)])
            .firstOrNull { it.id == com.nuvio.tv.petalix.LOCALS_GROUP }?.channelKeys.orEmpty()

    suspend fun savePetalixLocalKeys(keys: List<String>) {
        val id = com.nuvio.tv.petalix.LOCALS_GROUP
        val group = CustomGroup(id, "My Locals", keys.distinct())
        updateCustomGroups { groups ->
            if (groups.any { it.id == id }) groups.map { if (it.id == id) group else it }
            else groups + group
        }
    }

    suspend fun createCustomGroup(name: String): String {""")
    settings = java / "ui/screens/settings/LiveTvSettingsContent.kt"
    edit(settings, '            if (show(LiveTvPage.MAIN)) item(key = "channels") {', """            if (show(LiveTvPage.MAIN)) item(key = "petalix_local_stations") {
                SettingsGroupCard {
                    SettingsActionRow(
                        title = "Local channels",
                        subtitle = "Find stations by ZIP code and update My Locals",
                        onClick = {
                            context.startActivity(android.content.Intent(context, com.nuvio.tv.petalix.PetalixLocalsActivity::class.java))
                        }
                    )
                }
            }
            if (show(LiveTvPage.MAIN)) item(key = "channels") {""")
    source_url = java / "livetv/data/LiveTvRepository.kt"
    edit(source_url, 'else "${pl.id}|${e.tvgId ?: ""}|${e.name}"',
         'else com.nuvio.tv.petalix.PanelChannelIdentity.key(pl.id, e.url, "${pl.id}|${e.tvgId ?: ""}|${e.name}")')

    nav = java / "ui/navigation/NuvioNavHost.kt"
    edit(nav, "import androidx.compose.runtime.Composable", """import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.nuvio.tv.petalix.LIVE_CATEGORY_ARGUMENT
import com.nuvio.tv.petalix.LIVE_COLLECTION
import com.nuvio.tv.petalix.guideGroup""") if "import androidx.compose.runtime.LaunchedEffect\n" not in nav.read_text() else edit(
        nav, "import androidx.compose.runtime.Composable", """import androidx.compose.runtime.Composable
import com.nuvio.tv.petalix.LIVE_CATEGORY_ARGUMENT
import com.nuvio.tv.petalix.LIVE_COLLECTION
import com.nuvio.tv.petalix.guideGroup""")
    edit(nav, """                onNavigateToFolderDetail = { collectionId, folderId ->
                    navController.navigate(Screen.FolderDetail.createRoute(collectionId, folderId))
                }""", """                onNavigateToFolderDetail = { collectionId, folderId ->
                    if (collectionId == LIVE_COLLECTION) {
                        navController.navigate(Screen.LiveTv.route) { launchSingleTop = true }
                        navController.currentBackStackEntry?.savedStateHandle?.set(LIVE_CATEGORY_ARGUMENT, folderId)
                    } else {
                        navController.navigate(Screen.FolderDetail.createRoute(collectionId, folderId))
                    }
                }""")
    edit(nav, """        forkPageComposable(Screen.LiveTv.route) {
            com.nuvio.tv.livetv.ui.LiveTvGuideScreen(""", """        forkPageComposable(Screen.LiveTv.route) { entry ->
            val guide: com.nuvio.tv.livetv.ui.LiveTvViewModel = androidx.hilt.navigation.compose.hiltViewModel(entry)
            val requested by entry.savedStateHandle.getStateFlow<String?>(LIVE_CATEGORY_ARGUMENT, null).collectAsState()
            val guideState by guide.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(requested, guideState.groups) {
                val tile = requested ?: return@LaunchedEffect
                if (guideState.groups.none { it.count > 0 }) return@LaunchedEffect
                val group = guideGroup(tile, guideState.groups)
                if (group != null) guide.selectGroup(group.id)
                else if (tile == "locals") context.startActivity(
                    android.content.Intent(context, com.nuvio.tv.petalix.PetalixLocalsActivity::class.java)
                )
                else Toast.makeText(context, "This category is unavailable in your guide.", Toast.LENGTH_SHORT).show()
                entry.savedStateHandle.set<String?>(LIVE_CATEGORY_ARGUMENT, null)
            }
            com.nuvio.tv.livetv.ui.LiveTvGuideScreen(
                viewModel = guide,""")
    folders = java / "ui/screens/collection/FolderDetailViewModel.kt"
    edit(folders, """            val folder = collection?.folders?.find { it.id == folderId }

            if (folder""", """            val addons = addonRepository.getInstalledAddons().first().enabledAddons()
            val folder = collection?.folders?.find { it.id == folderId }?.let { saved ->
                if (collectionId == com.nuvio.tv.petalix.SERVICES_COLLECTION) {
                    com.nuvio.tv.petalix.withServiceCharts(saved, addons)
                } else saved
            }

            if (folder""")
    edit(folders, "            val addons = addonRepository.getInstalledAddons().first().enabledAddons()\n            val homeLayout",
         "            val homeLayout")
    modern = java / "ui/screens/home/ModernHomePresentation.kt"
    edit(modern, """    val lookups = buildCarouselRowLookups(rows)
    return ModernHomePresentationState(
        rows = rows.asStable(),""", """    val frontDoor = setOf(
        "collection_${com.nuvio.tv.petalix.LIVE_COLLECTION}",
        "collection_${com.nuvio.tv.petalix.SERVICES_COLLECTION}"
    )
    val orderedRows = frontDoor.mapNotNull { key -> rows.firstOrNull { it.key == key } } +
        rows.filterNot { it.key in frontDoor }
    val lookups = buildCarouselRowLookups(orderedRows)
    return ModernHomePresentationState(
        rows = orderedRows.asStable(),""")

    updater = app / "src/full/java/com/nuvio/tv/updater"
    shutil.copy2(STOCK / "PanelUpdateRepository.kt", updater / "UpdateRepository.kt")
    model = updater / "model/AppUpdate.kt"
    edit(model, "val assetSha256: String? = null", "val assetSha256: String? = null,\n    val expectedVersionCode: Int? = null")
    vm = updater / "UpdateViewModel.kt"
    if "import kotlinx.coroutines.flow.collect\n" not in vm.read_text():
        edit(vm, "import kotlinx.coroutines.flow.first", "import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.flow.collect")
    edit(vm, "    init {", """    init {
        viewModelScope.launch {
            updateRepository.deviceUpdateRequests.collect { request ->
                if (request.isNotBlank()) checkForUpdates(force = true, showNoUpdateFeedback = false)
            }
        }""")
    edit(vm, "val remoteNewer = VersionUtils.isRemoteNewer(update.tag, BuildConfig.VERSION_NAME)",
         "val remoteNewer = update.expectedVersionCode?.let { it > BuildConfig.VERSION_CODE } ?: VersionUtils.isRemoteNewer(update.tag, BuildConfig.VERSION_NAME)")
    # Bind the panel's runtime TMDB key to stock metadata services.
    for path in java.rglob("*.kt"):
        if path.parent == petalix:
            continue
        text = path.read_text()
        new = re.sub(r"(?:com\.nuvio\.tv\.)?BuildConfig\.TMDB_API_KEY", "com.nuvio.tv.petalix.PetalixRuntimeConfig.apiKey", text)
        new = re.sub(r"private val (\w+) = com.nuvio.tv.petalix.PetalixRuntimeConfig.apiKey",
                     r"private val \1 get() = com.nuvio.tv.petalix.PetalixRuntimeConfig.apiKey", new)
        if new != text:
            path.write_text(new)

    print(f"Prepared {PACKAGE} {version_code} from {UPSTREAM}. Customer signing is a separate step.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("upstream", type=Path)
    parser.add_argument("--version-code", type=int, required=True)
    args = parser.parse_args()
    prepare(args.upstream.resolve(), args.version_code)
