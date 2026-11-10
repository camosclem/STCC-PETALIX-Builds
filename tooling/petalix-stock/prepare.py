#!/usr/bin/env python3
"""Petalix Home/service restoration on pinned native Nuvio + IPTV 13.1."""
import argparse,hashlib,importlib.util,json,re,shutil,subprocess
from pathlib import Path
PIN='c0c9e8a2bf8bf0de8645438f9365d37c97da9f6e'
PACKAGE='com.petalix.iptv.hometest'
HERE=Path(__file__).resolve().parent

def once(p,old,new):
    s=p.read_text();assert s.count(old)==1,(p,str(old)[:65],s.count(old));p.write_text(s.replace(old,new))

def native_catalog(text):
    document=json.loads(text)
    assert len(document)==1 and len(document[0]['folders'])==9
    for folder in document[0]['folders']:
        for source in folder['sources']:
            filters=source.get('filters',{})
            for key,value in list(filters.items()):
                if value=='@panel_today':del filters[key]
    result=json.dumps(document,ensure_ascii=False,separators=(',',':'))
    assert '@panel_' not in result
    return result

def prepare(root,version,production=False):
    package="com.petalix.iptv.preview" if production else PACKAGE
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()==PIN
    spec=importlib.util.spec_from_file_location('brand',HERE.parent/'petalix-clean/prepare_stock.py');brand=importlib.util.module_from_spec(spec);spec.loader.exec_module(brand)
    logo=HERE.parent/'android-build/petalix_metallic_logo.png'
    assert hashlib.sha256(logo.read_bytes()).hexdigest()==brand.LOGO_SHA
    gradle=root/'app/build.gradle.kts'
    once(gradle,'applicationId = "com.nuvio.tv"','applicationId = "'+package+'"')
    once(gradle,'variant.applicationId.set(if (isPlaystore) "com.nuvio.appdebug" else "com.nuvio.livetv")','variant.applicationId.set("'+package+'")')
    once(gradle,'variant.applicationId.set(if (testBuild) "com.nuvio.livetv.test" else "com.nuvio.livetv")','variant.applicationId.set("'+package+'")')
    once(gradle,'debug {\n            signingConfig = signingConfigs.getByName("release")','debug {\n            signingConfig = signingConfigs.getByName("debug")')
    once(gradle,'buildConfigField("boolean", "FEATURE_IN_APP_UPDATES_ENABLED", "true")','buildConfigField("boolean", "FEATURE_IN_APP_UPDATES_ENABLED", "false")')
    with gradle.open('a') as f:
        f.write('\nandroid.defaultConfig.versionCode = '+str(version)+'\nandroid.defaultConfig.versionName = "1.1.0-petalix.home13.1-test4"\nandroid.defaultConfig.buildConfigField("String", "PETALIX_PANEL", buildConfigString("https://petalix-control.caroliinaconsultant.chatgpt.site"))\n')
        # CI builds with Android's debug signing and the protected signing job
        # applies the customer's stable release certificate afterward.
    if production:
        gradle.write_text(gradle.read_text().replace('"1.1.0-petalix.home13.1-test4"', '"1.1.0-petalix.panel'+str(version)+'"').replace('"FEATURE_IN_APP_UPDATES_ENABLED", "false"', '"FEATURE_IN_APP_UPDATES_ENABLED", "true"'))
    from PIL import Image
    art=Image.open(logo).convert('RGBA')
    # IPTV launcher activities use flavor-specific resources that override main.
    for res in (root/'app/src').glob('*/res'):
        brand.brand_strings(res);brand.update_artwork(res,logo)
        for pattern in ['drawable*/livetv_wordmark_iptv*.png','drawable*/nuvio_text.png',
                        'drawable*/ic_launcher.png','mipmap*/banner*.png',
                        'mipmap*/iptv_ic_launcher*.png','mipmap*/iptv_banner*.png']:
            for target in res.glob(pattern):
                with Image.open(target) as old:dims=old.size
                brand.contain_transparent(art,dims).save(target)
    branding_spec=importlib.util.spec_from_file_location('labels',HERE.parent/'android-build/branding_patch.py');labels=importlib.util.module_from_spec(branding_spec);branding_spec.loader.exec_module(labels);labels.apply(root,'petalix')
    for strings in (root/'app/src').rglob('strings.xml'):
        strings.write_text(re.sub(r'(<string\b[^>]*\bname="app_name"[^>]*>).*?(</string>)',(r'\1Petalix\2' if production else r'\1Petalix Home Test\2'),strings.read_text(),flags=re.S))
    dest=root/'app/src/main/java/com/nuvio/tv/petalix';dest.mkdir(parents=True,exist_ok=True)
    for name in ['PetalixManagedCoordinator.kt','PetalixActivationActivity.kt','PetalixLocalsActivity.kt','PetalixRuntimeConfig.kt']:shutil.copy(HERE/name,dest/name)
    collection_store=root/'app/src/main/java/com/nuvio/tv/data/local/CollectionsDataStore.kt'
    once(collection_store,'suspend fun setCollections(collections: List<Collection>) {\n        store().edit', 'suspend fun setCollections(collections: List<Collection>, profileId: Int = profileManager.activeProfileId.value) {\n        store(profileId).edit')
    once(collection_store,'suspend fun getCurrentCollections(): List<Collection> {\n        val prefs = store().data.first()', 'suspend fun getCurrentCollections(profileId: Int = profileManager.activeProfileId.value): List<Collection> {\n        val prefs = store(profileId).data.first()')
    assets=root/'app/src/main/assets/petalix';assets.mkdir(parents=True,exist_ok=True);(assets/'streaming-services.json').write_text(native_catalog((HERE/'streaming-services.json').read_text()))
    manifest=root/'app/src/main/AndroidManifest.xml'
    once(manifest,'        <meta-data\n            android:name="io.sentry.auto-init"','        <activity android:name=".petalix.PetalixLocalsActivity" android:exported="false" android:screenOrientation="landscape" />\n        <activity android:name=".petalix.PetalixActivationActivity" android:exported="false" android:screenOrientation="landscape" />\n        <meta-data\n            android:name="io.sentry.auto-init"')
    main=root/'app/src/main/java/com/nuvio/tv/MainActivity.kt'
    once(main,'open class MainActivity : ComponentActivity() {','open class MainActivity : ComponentActivity() {\n    @javax.inject.Inject lateinit var petalixBridge: com.nuvio.tv.petalix.PetalixManagedCoordinator')
    once(main,'val openOnRoute = remember { com.nuvio.tv.livetv.startup.StartPage.cachedRoute(this@MainActivity) }',
         'val openOnRoute = remember { if (petalixBridge.homeServicesMigrationPending) null else com.nuvio.tv.livetv.startup.StartPage.cachedRoute(this@MainActivity) }')
    once(main,'        installSplashScreen()\n        super.onCreate(savedInstanceState)','''        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (!petalixBridge.ready) {
            startActivity(Intent(this, com.nuvio.tv.petalix.PetalixActivationActivity::class.java))
            finish()
            return
        }
        lifecycleScope.launch { petalixBridge.observeProfiles() }
        lifecycleScope.launch {
            while (true) {
                try { petalixBridge.refresh() }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: com.nuvio.tv.petalix.PetalixActivationRequired) {
                    startActivity(Intent(this@MainActivity, com.nuvio.tv.petalix.PetalixActivationActivity::class.java))
                    finish()
                    return@launch
                }
                catch (_: Exception) { }
                kotlinx.coroutines.delay(15_000L)
            }
        }''')
    for p in (root/'app/src/main/java').rglob('*.kt'):
        if '/petalix/' in str(p):continue
        s=p.read_text();new=re.sub(r'(?:com\.nuvio\.tv\.)?BuildConfig\.TMDB_API_KEY','com.nuvio.tv.petalix.PetalixRuntimeConfig.apiKey',s)
        new=re.sub(r'private val (\w+) = com.nuvio.tv.petalix.PetalixRuntimeConfig.apiKey',r'private val \1 get() = com.nuvio.tv.petalix.PetalixRuntimeConfig.apiKey',new)
        if new!=s:p.write_text(new)
    nav=root/'app/src/main/java/com/nuvio/tv/ui/navigation/NuvioNavHost.kt'
    once(nav,'            MetaDetailsScreen(\n','            MetaDetailsScreen(\n                petalixServiceId = savedState.get<String>("petalixServiceId"),\n')
    once(nav,'''                navArgument("folderId") { type = NavType.StringType }
            )
        ) {
            com.nuvio.tv.ui.screens.collection.FolderDetailScreen(''','''                navArgument("folderId") { type = NavType.StringType }
            )
        ) { serviceEntry ->
            val service = com.nuvio.tv.petalix.ui2.PetalixServices.findFolder(
                serviceEntry.arguments?.getString("folderId").orEmpty()
            ).takeIf {
                serviceEntry.arguments?.getString("collectionId") == "collection-streaming-services-only"
            }
            if (service != null) {
                com.nuvio.tv.petalix.ui2.PetalixServiceScreen(
                    onHome = { navController.popBackStack() },
                    onDetail = { itemId, itemType, addonBaseUrl ->
                        val heroBackdrop = HeroBackdropState.consumeAndClear()
                        navController.navigate(Screen.Detail.createRoute(itemId, itemType, addonBaseUrl, heroBackdropUrl = heroBackdrop))
                        navController.currentBackStackEntry?.savedStateHandle?.set("petalixServiceId", service.id)
                    }
                )
            } else com.nuvio.tv.ui.screens.collection.FolderDetailScreen(''')
    guide=root/'app/src/main/java/com/nuvio/tv/livetv/ui/LiveTvGuideScreen.kt'
    once(guide,'''    if (!viewModel.isSettingsLoaded(loadedSettings) || !menuLoaded) {
        Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background))
        return
    }
''','')
    loading=root/'app/src/main/java/com/nuvio/tv/livetv/ui/LiveTvLoadingHost.kt'
    once(loading,'import androidx.compose.runtime.getValue', 'import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.setValue')
    once(loading,'    val message = when {','''    val localsPrefs = remember(context) { context.getSharedPreferences("petalix_session_v2", android.content.Context.MODE_PRIVATE) }
    var showLocals by remember { androidx.compose.runtime.mutableStateOf(false) }
    val channelCount by entry.liveTvRepository().channels.collectAsState()
    fun dismissLocalsPrompt() {
        localsPrefs.edit().putBoolean("localsPromptShownV1", true).apply()
        showLocals = false
    }
    androidx.compose.runtime.LaunchedEffect(route, live.loading, channelCount.size) {
        if (route == Screen.LiveTv.route && !live.loading && channelCount.isNotEmpty() &&
            !localsPrefs.getBoolean("localsSetupV1", false) && !localsPrefs.getBoolean("localsPromptShownV1", false)) {
            showLocals = true
        }
    }
    val message = when {''')
    once(loading,'    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {','''    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        if (showLocals && route == Screen.LiveTv.route) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { dismissLocalsPrompt() }) {
                val chooseFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                androidx.compose.runtime.LaunchedEffect(Unit) { chooseFocus.requestFocus() }
                Row(modifier = Modifier
                    .background(Color(0xFF192536), RoundedCornerShape(14.dp))
                    .border(2.dp, Color(0xFF79B8FF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    LiveText("Show local stations for your ZIP?", color = Color.White, size = 16.sp)
                    androidx.compose.material3.Button(
                        onClick = {
                            dismissLocalsPrompt()
                            context.startActivity(android.content.Intent(context, com.nuvio.tv.petalix.PetalixLocalsActivity::class.java))
                        }, modifier = Modifier.padding(start = 12.dp).focusRequester(chooseFocus)
                    ) { androidx.compose.material3.Text("Choose ZIP") }
                    androidx.compose.material3.TextButton(onClick = { dismissLocalsPrompt() }) {
                        androidx.compose.material3.Text("Later")
                    }
                }
            }
        }''')
    once(loading,'import androidx.compose.runtime.getValue','import androidx.compose.runtime.getValue\nimport androidx.compose.ui.focus.focusRequester')
    settings=root/'app/src/main/java/com/nuvio/tv/ui/screens/settings/LiveTvSettingsContent.kt'
    layout_prefs=root/'app/src/main/java/com/nuvio/tv/data/local/LayoutPreferenceDataStore.kt'
    once(layout_prefs, 'prefs[focusedPosterBackdropTrailerEnabledKey] ?: false',
         'prefs[focusedPosterBackdropTrailerEnabledKey] ?: true')
    once(layout_prefs, '?: FocusedPosterTrailerPlaybackTarget.HERO_MEDIA.name',
         '?: FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD.name')
    player_prefs=root/'app/src/main/java/com/nuvio/tv/data/local/PlayerSettingsDataStore.kt'
    once(player_prefs, 'val streamAutoPlayMode: StreamAutoPlayMode = StreamAutoPlayMode.MANUAL',
         'val streamAutoPlayMode: StreamAutoPlayMode = StreamAutoPlayMode.FIRST_STREAM')
    once(player_prefs, '} ?: StreamAutoPlayMode.MANUAL,', '} ?: StreamAutoPlayMode.FIRST_STREAM,')
    once(settings,'    // HUB: one Live TV page', '    val petalixContext = androidx.compose.ui.platform.LocalContext.current\n    // HUB: one Live TV page')
    once(settings,'            if (show(LiveTvPage.MAIN)) item(key = "channels") {', '''            if (show(LiveTvPage.MAIN)) item(key = "petalix_locals") {
                SettingsGroupCard {
                    SettingsActionRow("Local stations", "Change ZIP code or local stations", onClick = {
                        petalixContext.startActivity(android.content.Intent(petalixContext, com.nuvio.tv.petalix.PetalixLocalsActivity::class.java))
                    })
                }
            }
            if (show(LiveTvPage.MAIN)) item(key = "channels") {''')
    # Narrow, independently checked ports; no old application shell or guide replacement.
    for name in ['prepare_service_media','britbox_intro','service_ui_patch','home_services_patch']:
        helper_spec=importlib.util.spec_from_file_location(name,HERE/(name+'.py'))
        helper=importlib.util.module_from_spec(helper_spec);helper_spec.loader.exec_module(helper)
        helper.apply(root)
    if production:
        helper_spec=importlib.util.spec_from_file_location('native_customer_patch',HERE/'native_customer_patch.py')
        helper=importlib.util.module_from_spec(helper_spec);helper_spec.loader.exec_module(helper);helper.apply(root)
    assert not list(dest.glob('ManagedPolicy*'))
    print(json.dumps({'upstream':'1.1.0-beta.13.1','sha':PIN,'package':package,'versionCode':version,'nativeGuide':True,'nativeSports':True,'nativeSettingsUnrestricted':not production,'streamingServices':9,'horizontalHomeTiles':True,'referenceBuild':1793881218,'customerRollout':False}))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--upstream',type=Path,required=True);p.add_argument('--version',type=int,required=True);p.add_argument('--production',action='store_true');a=p.parse_args();prepare(a.upstream.resolve(),a.version,a.production)
