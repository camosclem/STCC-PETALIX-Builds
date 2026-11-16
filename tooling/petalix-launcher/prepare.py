#!/usr/bin/env python3
"""Brand the exact Brock 1.2.3 tree and add the existing Petalix service bridge.

This isolated test never uses a customer package or release signing key. Native
Home, sidebar, launcher, apps, guide, player and profile screens stay upstream.
"""
import argparse
import hashlib
import importlib.util
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
STOCK = HERE.parent / 'petalix-stock'
UPSTREAM = '93fb5affff07a2a84721c2cd0e0e71f578df0dc0'
PACKAGE = 'com.petalix.iptv.stocktest2'
ORIGIN = 'https://petalix-control.caroliinaconsultant.chatgpt.site'


def once(path, old, new):
    text = path.read_text()
    if text.count(old) != 1:
        raise RuntimeError(f'Upstream binding changed: {path.name}: {old[:65]!r}')
    path.write_text(text.replace(old, new))


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def prepare(root, version):
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    if revision != UPSTREAM:
        raise RuntimeError('Use the exact NuvioTVLauncher 1.2.3 commit.')
    if not 1 <= version < 2_100_000_000:
        raise ValueError('Invalid Android test version code.')
    source = root / 'app/src/main/java/com/nuvio/tv'
    dest = source / 'petalix'
    dest.mkdir(parents=True, exist_ok=True)
    assets = root / 'app/src/main/assets'
    (assets / 'petalix').mkdir(parents=True, exist_ok=True)

    brand = module('launcher_brand', HERE.parent / 'petalix-clean/prepare_stock.py')
    logo = HERE.parent / 'android-build/petalix_metallic_logo.png'
    if hashlib.sha256(logo.read_bytes()).hexdigest() != brand.LOGO_SHA:
        raise RuntimeError('Unexpected Petalix logo asset.')
    from PIL import Image
    image = Image.open(logo).convert('RGBA')
    for res in (root / 'app/src').glob('*/res'):
        brand.brand_strings(res)
        brand.update_artwork(res, logo)
        for pattern in ('mipmap*/banner*.png', 'drawable*/nuvio_text.png', 'drawable*/livetv_wordmark_iptv*.png'):
            for target in res.glob(pattern):
                with Image.open(target) as old:
                    size = old.size
                brand.contain_transparent(image, size).save(target)
        for strings in res.glob('values*/strings.xml'):
            strings.write_text(re.sub(r'(<string\b[^>]*\bname="app_name"[^>]*>).*?(</string>)',
                                     r'\1Petalix Launcher Test\2', strings.read_text(), flags=re.S))

    gradle = root / 'app/build.gradle.kts'
    once(gradle, 'applicationId = "com.nuvio.tv"', f'applicationId = "{PACKAGE}"')
    once(gradle, 'variant.applicationId.set(if (isPlaystore) "com.nuvio.appdebug" else "com.nuvio.tv.launcher")',
         f'variant.applicationId.set("{PACKAGE}")')
    once(gradle, 'variant.applicationId.set(if (testBuild) "com.nuvio.tv.launcher.test" else "com.nuvio.tv.launcher")',
         f'variant.applicationId.set("{PACKAGE}")')
    # The test uses the committed upstream test key, never the private Petalix customer key.
    with gradle.open('a') as out:
        out.write(f'''\nandroid.defaultConfig.versionCode = {version}
android.defaultConfig.versionName = "1.2.3-petalix.launcher-test.{version}"
android.defaultConfig.buildConfigField("String", "PETALIX_PANEL", buildConfigString("{ORIGIN}"))
android.defaultConfig.ndk.abiFilters += listOf("armeabi-v7a", "arm64-v8a")
''')
    vpn = root / 'vpn-android/build.gradle.kts'
    once(vpn, '        minSdk = 24', '        minSdk = 24\n        ndk.abiFilters += listOf("armeabi-v7a", "arm64-v8a")')
    once(vpn, ').map { (abi, goArch, compiler) ->',
         ').filter { (abi) -> abi in listOf("armeabi-v7a", "arm64-v8a") }.map { (abi, goArch, compiler) ->')

    for name in ('PetalixManagedCoordinator.kt', 'PetalixActivationActivity.kt',
                 'PetalixLocalsActivity.kt', 'PetalixLocalStations.kt', 'PetalixRuntimeConfig.kt'):
        shutil.copyfile(STOCK / name, dest / name)
    shutil.copyfile(HERE.parent / 'android-build/local-stations.json', assets / 'petalix/local-stations.json')
    shutil.copyfile(STOCK / 'streaming-services.json', assets / 'petalix/streaming-services.json')

    # Share service data through native collections. Do not transplant the old Home wrapper.
    collection = source / 'data/local/CollectionsDataStore.kt'
    once(collection, 'suspend fun setCollections(collections: List<Collection>) {\n        store().edit',
         'suspend fun setCollections(collections: List<Collection>, profileId: Int = profileManager.activeProfileId.value) {\n        store(profileId).edit')
    once(collection, 'suspend fun getCurrentCollections(): List<Collection> {\n        val prefs = store().data.first()',
         'suspend fun getCurrentCollections(profileId: Int = profileManager.activeProfileId.value): List<Collection> {\n        val prefs = store(profileId).data.first()')
    coordinator = dest / 'PetalixManagedCoordinator.kt'
    text = coordinator.read_text()
    start = text.index('    private suspend fun ensureLocalHome(')
    end = text.index('    private suspend fun applySettings(', start)
    text = text[:start] + '''    private suspend fun ensureLocalHome(profileId: Int) {
        // Native Home, launcher configuration and layout defaults belong to the user.
        if (!prefs.getBoolean("launcherServiceDefaults:$profileId", false)) {
            live.updateSettings { it.copy(onDemandInSidebar = true,
                onDemandStreamsPosition = "FIRST", onDemandAutoPlay = true) }
            tmdb.setEnabled(true)
            tmdb.setModernHomeEnabled(true)
            check(prefs.edit().putBoolean("launcherServiceDefaults:$profileId", true).commit())
        }
    }

''' + text[end:]
    coordinator.write_text(text)

    prefs = source / 'livetv/data/LiveTvPreferences.kt'
    once(prefs, '    suspend fun createCustomGroup(name: String): String {', '''    suspend fun petalixLocalKeys(): List<String> =
        decodeCustomGroups(store.data.first()[my(Keys.customGroups)])
            .firstOrNull { it.id == com.nuvio.tv.livetv.model.ChannelGroup.CUSTOM_PREFIX + "petalix_locals" }
            ?.channelKeys.orEmpty()

    suspend fun savePetalixLocalKeys(keys: List<String>) {
        val id = com.nuvio.tv.livetv.model.ChannelGroup.CUSTOM_PREFIX + "petalix_locals"
        val group = CustomGroup(id, "My Locals", keys.distinct())
        updateCustomGroups { groups -> if (groups.any { it.id == id }) groups.map { if (it.id == id) group else it } else groups + group }
    }

    suspend fun createCustomGroup(name: String): String {''')
    manifest = root / 'app/src/main/AndroidManifest.xml'
    once(manifest, '        <meta-data\n            android:name="io.sentry.auto-init"',
         '        <activity android:name=".petalix.PetalixLocalsActivity" android:exported="false" />\n'
         '        <activity android:name=".petalix.PetalixActivationActivity" android:exported="false" />\n'
         '        <meta-data\n            android:name="io.sentry.auto-init"')
    main = source / 'MainActivity.kt'
    once(main, 'package com.nuvio.tv\n', 'package com.nuvio.tv\n\nimport androidx.lifecycle.repeatOnLifecycle\n')
    once(main, 'open class MainActivity : ComponentActivity() {',
         'open class MainActivity : ComponentActivity() {\n    @javax.inject.Inject lateinit var petalixBridge: com.nuvio.tv.petalix.PetalixManagedCoordinator')
    once(main, '        installSplashScreen()\n        super.onCreate(savedInstanceState)', '''        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (!petalixBridge.ready) {
            startActivity(Intent(this, com.nuvio.tv.petalix.PetalixActivationActivity::class.java))
            finish()
            return
        }
        if (petalixBridge.shouldPromptForLocals && !intent.getBooleanExtra("skipLocalsOnce", false)) {
            startActivity(Intent(this, com.nuvio.tv.petalix.PetalixLocalsActivity::class.java).putExtra("setup", true))
            finish()
            return
        }
        lifecycleScope.launch { petalixBridge.observeProfiles() }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    try { petalixBridge.refresh() }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: com.nuvio.tv.petalix.PetalixActivationRequired) {
                        startActivity(Intent(this@MainActivity, com.nuvio.tv.petalix.PetalixActivationActivity::class.java))
                        finish()
                        return@repeatOnLifecycle
                    }
                    catch (_: Exception) { /* Keep the saved activation during a temporary outage. */ }
                    kotlinx.coroutines.delay(30_000L)
                }
            }
        }''')
    # Existing authenticated panel key delivery, retaining native TMDB UI and data handling.
    for path in source.rglob('*.kt'):
        if dest in path.parents:
            continue
        text = path.read_text()
        updated = re.sub(r'(?:com\.nuvio\.tv\.)?BuildConfig\.TMDB_API_KEY',
                         'com.nuvio.tv.petalix.PetalixRuntimeConfig.apiKey', text)
        updated = re.sub(r'private val (\w+) = com.nuvio.tv.petalix.PetalixRuntimeConfig.apiKey',
                         r'private val \1 get() = com.nuvio.tv.petalix.PetalixRuntimeConfig.apiKey', updated)
        if updated != text:
            path.write_text(updated)

    sys.path.insert(0, str(STOCK))
    module('launcher_panel_bridge', STOCK / 'native_customer_patch.py').apply(root, native_launcher=True)
    # Use the new fork's VOD auto-play option, with the provider ahead of add-ons.
    panel = dest / 'PetalixPanelSettings.kt'
    once(panel, 'onDemandStreamsPosition = if (values.getBoolean("vod.first")) "FIRST" else "LAST",',
         'onDemandStreamsPosition = if (values.getBoolean("vod.first")) "FIRST" else "LAST", onDemandAutoPlay = values.getBoolean("vod.first"),')
    # No panel collection rewriting: the native collection editor and rendering stay intact.
    native = dest / 'ManagedNativeSettings.kt'
    text = native.read_text()
    text = text.replace('PetalixServiceCatalog.reconcile(\n            values.getString(key), context.assets.open("petalix/streaming-services.json").bufferedReader().use { it.readText() }\n        )', 'values.getString(key)')
    text = text.replace('PetalixServiceCatalog.reconcile(\n            prepared.getString(collectionKey),context.assets.open("petalix/streaming-services.json").bufferedReader().use { it.readText() }\n        )', 'prepared.getString(collectionKey)')
    native.write_text(text)
    # Retain the panel's consent-based, app-only remote help without changing native navigation.
    remote = (HERE.parent / 'android-build/PetalixRemoteControl.kt').read_text()
    (dest / 'PetalixRemoteControl.kt').write_text(remote.replace('coordinator:ManagedCoordinator', 'coordinator:PetalixManagedCoordinator'))
    once(coordinator, '    suspend fun appUpdate(): JSONObject =', '''    suspend fun remoteControl(sessionId: String, action: String, ack: Long): JSONObject {
        if (!ready) throw PetalixActivationRequired()
        return post("remote-control", report().put("sessionId", sessionId).put("action", action).put("ack", ack), token())
    }
    suspend fun appUpdate(): JSONObject =''')
    once(coordinator, '"management-v4",', '"management-v4", "remote-control-v1",')
    once(main, '    override fun onResume() {\n        super.onResume()',
         '    override fun onResume() {\n        super.onResume()\n        com.nuvio.tv.petalix.PetalixRemoteControl.attach(this, petalixBridge)')
    once(main, '    override fun onPause() {',
         '    override fun onPause() {\n        com.nuvio.tv.petalix.PetalixRemoteControl.detach(this, petalixBridge)')
    print(json.dumps({'source': UPSTREAM, 'upstream': 'Br0ck25/NuvioTVLauncher 1.2.3',
                      'package': PACKAGE, 'versionCode': version, 'customerRollout': False,
                      'nativeHome': True, 'nativeLauncher': True}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--upstream', type=Path, required=True)
    parser.add_argument('--version', type=int, required=True)
    args = parser.parse_args()
    prepare(args.upstream.resolve(), args.version)
