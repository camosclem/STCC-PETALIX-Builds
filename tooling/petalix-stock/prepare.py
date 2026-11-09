#!/usr/bin/env python3
"""Isolated Petalix Test 2: stock IPTV, complete branding and service browsing."""
import argparse,hashlib,importlib.util,json,re,shutil,subprocess
from pathlib import Path
PIN='c0c9e8a2bf8bf0de8645438f9365d37c97da9f6e'
PACKAGE='com.petalix.iptv.stocktest2'
HERE=Path(__file__).resolve().parent

def once(p,old,new):
    s=p.read_text();assert s.count(old)==1,(p,str(old)[:65],s.count(old));p.write_text(s.replace(old,new))

def native_catalog(text):
    document=json.loads(text)
    assert len(document)==1 and len(document[0]['folders'])==11
    for folder in document[0]['folders']:
        for source in folder['sources']:
            filters=source.get('filters',{})
            for key,value in list(filters.items()):
                if value=='@panel_today':del filters[key]
    result=json.dumps(document,ensure_ascii=False,separators=(',',':'))
    assert '@panel_' not in result
    return result

def prepare(root,version):
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()==PIN
    spec=importlib.util.spec_from_file_location('brand',HERE.parent/'petalix-clean/prepare_stock.py');brand=importlib.util.module_from_spec(spec);spec.loader.exec_module(brand)
    logo=HERE.parent/'android-build/petalix_metallic_logo.png'
    assert hashlib.sha256(logo.read_bytes()).hexdigest()==brand.LOGO_SHA
    gradle=root/'app/build.gradle.kts'
    once(gradle,'applicationId = "com.nuvio.tv"','applicationId = "'+PACKAGE+'"')
    once(gradle,'variant.applicationId.set(if (isPlaystore) "com.nuvio.appdebug" else "com.nuvio.livetv")','variant.applicationId.set("'+PACKAGE+'")')
    once(gradle,'variant.applicationId.set(if (testBuild) "com.nuvio.livetv.test" else "com.nuvio.livetv")','variant.applicationId.set("'+PACKAGE+'")')
    once(gradle,'debug {\n            signingConfig = signingConfigs.getByName("release")','debug {\n            signingConfig = signingConfigs.getByName("debug")')
    once(gradle,'buildConfigField("boolean", "FEATURE_IN_APP_UPDATES_ENABLED", "true")','buildConfigField("boolean", "FEATURE_IN_APP_UPDATES_ENABLED", "false")')
    with gradle.open('a') as f:f.write('\nandroid.defaultConfig.versionCode = '+str(version)+'\nandroid.defaultConfig.versionName = "1.1.0-petalix.stock13.1-test2"\nandroid.defaultConfig.buildConfigField("String", "PETALIX_PANEL", buildConfigString("https://petalix-control.caroliinaconsultant.chatgpt.site"))\n')
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
        strings.write_text(re.sub(r'(<string\b[^>]*\bname="app_name"[^>]*>).*?(</string>)',r'\1Petalix Test 2\2',strings.read_text(),flags=re.S))
    dest=root/'app/src/main/java/com/nuvio/tv/petalix';dest.mkdir(parents=True,exist_ok=True)
    for name in ['PetalixManagedCoordinator.kt','PetalixActivationActivity.kt','PetalixRuntimeConfig.kt','PetalixServicesUi.kt']:shutil.copy(HERE/name,dest/name)
    assets=root/'app/src/main/assets/petalix';assets.mkdir(parents=True,exist_ok=True);(assets/'streaming-services.json').write_text(native_catalog((HERE/'streaming-services.json').read_text()))
    manifest=root/'app/src/main/AndroidManifest.xml'
    once(manifest,'        <meta-data\n            android:name="io.sentry.auto-init"','        <activity android:name=".petalix.PetalixActivationActivity" android:exported="false" android:screenOrientation="landscape" />\n        <meta-data\n            android:name="io.sentry.auto-init"')
    main=root/'app/src/main/java/com/nuvio/tv/MainActivity.kt'
    once(main,'                            add(Screen.Home.route)','                            add(Screen.Home.route)\n                            add(com.nuvio.tv.petalix.PETALIX_SERVICES_ROUTE)')
    once(main,'                            add(\n                                DrawerItem(\n                                    route = Screen.Settings.route,','                            add(DrawerItem(route = com.nuvio.tv.petalix.PETALIX_SERVICES_ROUTE, label = "Streaming Services", icon = Icons.Default.VideoLibrary))\n                            add(\n                                DrawerItem(\n                                    route = Screen.Settings.route,')
    once(main,'open class MainActivity : ComponentActivity() {','open class MainActivity : ComponentActivity() {\n    @javax.inject.Inject lateinit var petalixBridge: com.nuvio.tv.petalix.PetalixManagedCoordinator')
    once(main,'        installSplashScreen()\n        super.onCreate(savedInstanceState)','''        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (!petalixBridge.ready) {
            startActivity(Intent(this, com.nuvio.tv.petalix.PetalixActivationActivity::class.java))
            finish()
            return
        }
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
    once(nav,'        composable(Screen.Home.route) {','''        composable(com.nuvio.tv.petalix.PETALIX_SERVICES_ROUTE) {
            com.nuvio.tv.petalix.PetalixServicesScreen(
                onLiveTv = { navController.navigate(Screen.LiveTv.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onFolder = { collection, folder -> navController.navigate(Screen.FolderDetail.createRoute(collection, folder)) }
            )
        }
        composable(Screen.Home.route) {''')
    once(nav,'            HomeScreen(\n','''            HomeScreen(
                onPetalixLiveTv = { navController.navigate(Screen.LiveTv.route) },
                onPetalixServices = { navController.navigate(com.nuvio.tv.petalix.PETALIX_SERVICES_ROUTE) },
                onPetalixSettings = { navController.navigate(Screen.Settings.route) },
''')
    home=root/'app/src/main/java/com/nuvio/tv/ui/screens/home/HomeScreen.kt'
    once(home,'fun HomeScreen(\n','''fun HomeScreen(
    onPetalixLiveTv: () -> Unit = {},
    onPetalixServices: () -> Unit = {},
    onPetalixSettings: () -> Unit = {},
''')
    once(home,'    val showStartupLoader = when {','''    val petalixFallback = homeStableGateReleased && (!hasAnyContent || !modernPresentationReady)
    val showStartupLoader = if (petalixFallback) false else when {''')
    once(home,'        when {\n            !uiState.layoutPreferencesReady -> {','''        when {
            petalixFallback -> com.nuvio.tv.petalix.PetalixEmptyHome(onPetalixLiveTv, onPetalixServices, onPetalixSettings)
            !uiState.layoutPreferencesReady -> {''')
    folder=root/'app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailScreen.kt'
    once(folder,'    val folder = uiState.folder\n','''    val folder = uiState.folder
    if (viewModel.isPetalixService) {
        com.nuvio.tv.petalix.PetalixServiceHub(uiState, onNavigateToDetail, onBack,
            onRetry = viewModel::retryPetalixService, onMore = viewModel::loadMoreItems)
        return
    }
''')
    vm=root/'app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailViewModel.kt'
    once(vm,'    private fun loadFolder() {','''    val isPetalixService: Boolean get() = collectionId == com.nuvio.tv.petalix.PETALIX_SERVICES_COLLECTION
    fun retryPetalixService() {
        if (!isPetalixService) return
        if (_uiState.value.tabs.isEmpty()) {
            loadFolder()
            return
        }
        val retryTabs = _uiState.value.tabs.withIndex().filter { it.value.source != null && !it.value.isLoading }
        _uiState.update { state -> state.copy(tabs = state.tabs.mapIndexed { index, tab ->
            if (retryTabs.any { it.index == index }) tab.copy(isLoading = true, error = null) else tab
        }) }
        retryTabs.forEach { (index, tab) -> tab.source?.let { loadSourceForTab(index, it) } }
    }
    private fun loadFolder() {''')
    assert not list(dest.glob('ManagedPolicy*'))
    print(json.dumps({'upstream':'1.1.0-beta.13.1','sha':PIN,'package':PACKAGE,'versionCode':version,'nativeGuide':True,'nativeSports':True,'nativeSettingsUnrestricted':True,'streamingServices':11,'customerRollout':False}))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--upstream',type=Path,required=True);p.add_argument('--version',type=int,required=True);a=p.parse_args();prepare(a.upstream.resolve(),a.version)
