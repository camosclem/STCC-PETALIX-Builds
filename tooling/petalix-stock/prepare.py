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
    with gradle.open('a') as f:
        f.write('\nandroid.defaultConfig.versionCode = '+str(version)+'\nandroid.defaultConfig.versionName = "1.1.0-petalix.home13.1-test1"\nandroid.defaultConfig.buildConfigField("String", "PETALIX_PANEL", buildConfigString("https://petalix-control.caroliinaconsultant.chatgpt.site"))\n')
        # Use the pinned upstream TEST key for this isolated package. It is recoverable
        # from the same source commit, unlike an ephemeral runner-generated debug key.
        f.write('''
android.signingConfigs.getByName("debug").apply {
    storeFile = rootProject.file("keystore/livetv-fork.jks")
    storePassword = "livetvfork"
    keyAlias = "livetv"
    keyPassword = "livetvfork"
}
''')
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
        strings.write_text(re.sub(r'(<string\b[^>]*\bname="app_name"[^>]*>).*?(</string>)',r'\1Petalix Home Test\2',strings.read_text(),flags=re.S))
    dest=root/'app/src/main/java/com/nuvio/tv/petalix';dest.mkdir(parents=True,exist_ok=True)
    for name in ['PetalixManagedCoordinator.kt','PetalixActivationActivity.kt','PetalixRuntimeConfig.kt']:shutil.copy(HERE/name,dest/name)
    assets=root/'app/src/main/assets/petalix';assets.mkdir(parents=True,exist_ok=True);(assets/'streaming-services.json').write_text(native_catalog((HERE/'streaming-services.json').read_text()))
    manifest=root/'app/src/main/AndroidManifest.xml'
    once(manifest,'        <meta-data\n            android:name="io.sentry.auto-init"','        <activity android:name=".petalix.PetalixActivationActivity" android:exported="false" android:screenOrientation="landscape" />\n        <meta-data\n            android:name="io.sentry.auto-init"')
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
    # Narrow, independently checked ports; no old application shell or guide replacement.
    for name in ['prepare_service_media','service_ui_patch','home_services_patch']:
        helper_spec=importlib.util.spec_from_file_location(name,HERE/(name+'.py'))
        helper=importlib.util.module_from_spec(helper_spec);helper_spec.loader.exec_module(helper)
        helper.apply(root)
    assert not list(dest.glob('ManagedPolicy*'))
    print(json.dumps({'upstream':'1.1.0-beta.13.1','sha':PIN,'package':PACKAGE,'versionCode':version,'nativeGuide':True,'nativeSports':True,'nativeSettingsUnrestricted':True,'streamingServices':11,'horizontalHomeTiles':True,'referenceBuild':1793881218,'customerRollout':False}))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--upstream',type=Path,required=True);p.add_argument('--version',type=int,required=True);a=p.parse_args();prepare(a.upstream.resolve(),a.version)
