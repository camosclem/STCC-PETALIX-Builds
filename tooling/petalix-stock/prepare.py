#!/usr/bin/env python3
"""Stock 13.1 test: branding, native services collection, and a PIN connection only."""
import argparse,hashlib,importlib.util,json,re,shutil,subprocess
from pathlib import Path
PIN='c0c9e8a2bf8bf0de8645438f9365d37c97da9f6e'
PACKAGE='com.petalix.iptv.stocktest'
HERE=Path(__file__).resolve().parent

def once(p,old,new):
    s=p.read_text();assert s.count(old)==1,(p,str(old)[:65],s.count(old));p.write_text(s.replace(old,new))

def native_catalog(text):
    document=json.loads(text)
    assert len(document)==1 and len(document[0]['folders'])==10
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
    with gradle.open('a') as f:f.write('\nandroid.defaultConfig.versionCode = '+str(version)+'\nandroid.defaultConfig.versionName = "1.1.0-petalix.stock13.1-test"\nandroid.defaultConfig.buildConfigField("String", "PETALIX_PANEL", buildConfigString("https://petalix-control.caroliinaconsultant.chatgpt.site"))\n')
    brand.brand_strings(root/'app/src/main/res');brand.update_artwork(root/'app/src/main/res',logo)
    branding_spec=importlib.util.spec_from_file_location('labels',HERE.parent/'android-build/branding_patch.py');labels=importlib.util.module_from_spec(branding_spec);branding_spec.loader.exec_module(labels);labels.apply(root,'petalix')
    strings=root/'app/src/main/res/values/strings.xml';once(strings,'<string name="app_name">Petalix</string>','<string name="app_name">Petalix Test</string>')
    dest=root/'app/src/main/java/com/nuvio/tv/petalix';dest.mkdir(parents=True,exist_ok=True)
    for name in ['PetalixManagedCoordinator.kt','PetalixActivationActivity.kt','PetalixRuntimeConfig.kt']:shutil.copy(HERE/name,dest/name)
    assets=root/'app/src/main/assets/petalix';assets.mkdir(parents=True,exist_ok=True);(assets/'streaming-services.json').write_text(native_catalog((HERE/'streaming-services.json').read_text()))
    manifest=root/'app/src/main/AndroidManifest.xml'
    once(manifest,'        <meta-data\n            android:name="io.sentry.auto-init"','        <activity android:name=".petalix.PetalixActivationActivity" android:exported="false" android:screenOrientation="landscape" />\n        <meta-data\n            android:name="io.sentry.auto-init"')
    main=root/'app/src/main/java/com/nuvio/tv/MainActivity.kt'
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
    assert not list(dest.glob('ManagedPolicy*'))
    print(json.dumps({'upstream':'1.1.0-beta.13.1','sha':PIN,'package':PACKAGE,'versionCode':version,'nativeGuide':True,'nativeSports':True,'nativeSettingsUnrestricted':True,'streamingServices':10,'customerRollout':False}))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--upstream',type=Path,required=True);p.add_argument('--version',type=int,required=True);a=p.parse_args();prepare(a.upstream.resolve(),a.version)
