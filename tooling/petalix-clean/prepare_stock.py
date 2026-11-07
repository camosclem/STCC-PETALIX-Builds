#!/usr/bin/env python3
"""Prepare an isolated, stock-upstream Petalix *test* build. NO customer signing."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from PIL import Image, ImageOps

PINNED_SHA = "a7e67cec36e1e28d79b889b5630c4b2095931888"
LOGO_SHA = "ef789202dbef793eadc6af20840f4beeed241802bbf0167a756ed1e92264a30f"
APPLICATION_ID = "com.petalix.iptv.preview"
VERSION_CODE = 1794250000
VERSION_NAME = "1.1.0-petalix.stock11.1-test"


def replace_once(path, old, new):
    content = path.read_text(encoding="utf-8")
    if content.count(old) != 1:
        raise RuntimeError("Incompatible upstream source: expected exactly one " + repr(old[:50]))
    path.write_text(content.replace(old, new, 1), encoding="utf-8")


def brand_strings(res):
    pattern = re.compile(r'(<string\b[^>]*\bname="([^"]+)"[^>]*>)(.*?)(</string>)', re.S)
    protected = re.compile(
        r"license|attribution|copyright|credit|source|supporters|"
        r"donat|contribut|trakt|simkl|account|oauth|login|sync", re.I
    )
    for file in res.glob("values*/strings.xml"):
        raw = file.read_text(encoding="utf-8")
        def rewrite(match):
            start, name, value, end = match.groups()
            if name == "app_name":
                return start + "Petalix" + end
            if protected.search(name) or "http" in value.lower():
                return match.group(0)
            # User-facing display copy only; retain developer credits and real third-party identities.
            return start + re.sub(r"\bNuvio(?:TV)?\b", "Petalix", value) + end
        file.write_text(pattern.sub(rewrite, raw), encoding="utf-8")


def contain_transparent(art, size):
    # Place the exact existing metallic logo on a transparent icon/banner canvas.
    width, height = size
    image = Image.new("RGBA", size, (0, 0, 0, 0))
    padded = ImageOps.contain(art, (int(width * .88), int(height * .82)), Image.Resampling.LANCZOS)
    image.alpha_composite(padded, ((width - padded.width)//2, (height - padded.height)//2))
    return image


def update_artwork(res, logo_file):
    image = Image.open(logo_file).convert("RGBA")
    for mipmap in res.glob("mipmap-*"):
        if not mipmap.is_dir():
            continue
        sample = mipmap/"ic_launcher.png"
        if not sample.exists():
            continue
        with Image.open(sample) as existing:
            dims = existing.size
        mark = contain_transparent(image, dims)
        for target in mipmap.glob("ic_launcher*.png"):
            mark.save(target)
    for wordmark in (res/"drawable-nodpi").glob("app_logo_wordmark*.png"):
        with Image.open(wordmark) as old:
            dims = old.size
        contain_transparent(image, dims).save(wordmark)
    mark_file = res/"drawable-nodpi/app_logo_mark.png"
    if mark_file.exists():
        with Image.open(mark_file) as old:
            dims = old.size
        contain_transparent(image, dims).save(mark_file)
    for banner in (res/"drawable/tv_banner.png", res/"drawable-nodpi/tv_banner.png"):
        if banner.exists():
            with Image.open(banner) as old:
                dims = old.size
            contain_transparent(image, dims).save(banner)


def apply_panel_bridge(root):
    """Copy only the essential Petalix activation and guide bridge to stock 11.1."""
    import shutil
    source_dir = Path(__file__).with_name("android")
    dest = root/"app/src/main/java/com/nuvio/tv/petalix"
    dest.mkdir(parents=True, exist_ok=True)
    for name in ("PetalixManagedCoordinator.kt", "PetalixActivationActivity.kt"):
        shutil.copyfile(source_dir/name, dest/name)
    shutil.copyfile(Path(__file__).resolve().parents[1]/"android-build/PanelPlaylist.kt",
                    dest/"PanelPlaylist.kt")

    gradle = root/"app/build.gradle.kts"
    with gradle.open("a", encoding="utf-8") as stream:
        stream.write('\nandroid.defaultConfig.buildConfigField("String", "PETALIX_PANEL", '
                     'buildConfigString("https://petalix-control.pinkgiant5.chatgpt.site"))\n')

    manifest = root/"app/src/main/AndroidManifest.xml"
    replace_once(manifest,
        '        <meta-data\n            android:name="io.sentry.auto-init"',
        '        <activity android:name=".petalix.PetalixActivationActivity" '
        'android:exported="false" android:screenOrientation="landscape" />\n'
        '        <meta-data\n            android:name="io.sentry.auto-init"')

    main = root/"app/src/main/java/com/nuvio/tv/MainActivity.kt"
    replace_once(main, 'open class MainActivity : ComponentActivity() {',
        'open class MainActivity : ComponentActivity() {\n'
        '    @javax.inject.Inject lateinit var petalixBridge: '
        'com.nuvio.tv.petalix.PetalixManagedCoordinator\n')
    replace_once(main,
        '        installSplashScreen()\n        super.onCreate(savedInstanceState)',
        '''        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (!petalixBridge.ready) {
            startActivity(Intent(this, com.nuvio.tv.petalix.PetalixActivationActivity::class.java))
            finish()
            return
        }
        lifecycleScope.launch {
            while (true) {
                try { petalixBridge.refresh() }
                catch (_: com.nuvio.tv.petalix.PetalixActivationRequired) {
                    startActivity(Intent(this@MainActivity,
                        com.nuvio.tv.petalix.PetalixActivationActivity::class.java))
                    finish()
                    return@launch
                }
                catch (_: Exception) { /* Saved activation and playback remain available offline. */ }
                kotlinx.coroutines.delay(900_000L)
            }
        }''')

    prefs = root/"app/src/main/java/com/nuvio/tv/livetv/data/LiveTvPreferences.kt"
    raw = prefs.read_text(encoding="utf-8")
    old = '    private fun profileView(p: Preferences, id: Int): Preferences {'
    if raw.count(old) != 1:
        raise RuntimeError("Missing 11.1 profile view; playlist bridge cannot install")
    start = raw.index(old)
    stop = raw.index('\n    private suspend fun seedProfile', start)
    part = raw[start:stop]
    if part.count('if (id <= 1) return p') != 1 or part.count('return m') != 1:
        raise RuntimeError("Unexpected profile mapping in 11.1; review guide patch")
    part = part.replace('if (id <= 1) return p',
        'if (id <= 1) return com.nuvio.tv.petalix.PanelPlaylist.view(p,"petalix-managed")')
    part = part.replace('return m',
        'return com.nuvio.tv.petalix.PanelPlaylist.view(m,"petalix-managed")')
    raw = raw[:start] + part + raw[stop:]
    needle = '''        withMyMenu(p0, id)
    }.map { p ->'''
    if raw.count(needle) != 1:
        raise RuntimeError("Unexpected 11.1 guide settings mapping")
    raw = raw.replace(needle, '''        com.nuvio.tv.petalix.PanelPlaylist.view(withMyMenu(p0, id),"petalix-managed")
    }.map { p ->''')
    needle2 = '    suspend fun updatePlaylists(transform: (List<PlaylistSource>) -> List<PlaylistSource>) {'
    if raw.count(needle2) != 1:
        raise RuntimeError("Unexpected 11.1 playlist update API")
    raw = raw.replace(needle2, '''    suspend fun applyPetalixPlaylistRules(document: org.json.JSONObject) {
        store.edit { p -> com.nuvio.tv.petalix.PanelPlaylist.store(p, document) }
    }

'''+needle2)
    prefs.write_text(raw, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", type=Path, required=True)
    parser.add_argument("--logo", type=Path, required=True)
    args = parser.parse_args()

    root = args.upstream.resolve()
    rev = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    if rev != PINNED_SHA:
        raise RuntimeError("Wrong upstream commit: refusing unreviewed source " + rev)
    if hashlib.sha256(args.logo.read_bytes()).hexdigest() != LOGO_SHA:
        raise RuntimeError("Petalix logo checksum mismatch")

    gradle = root/"app/build.gradle.kts"
    replace_once(gradle, 'applicationId = "com.nuvio.tv"', 'applicationId = "' + APPLICATION_ID + '"')
    replace_once(gradle, "versionCode = 1074", "versionCode = " + str(VERSION_CODE // 100))
    # Upstream multiplies the base code by 100 below defaultConfig.
    replace_once(gradle, 'versionName = "1.1.0-beta.11"', 'versionName = "' + VERSION_NAME + '"')
    # Upstream androidComponents otherwise overrides the Gradle applicationId.
    replace_once(gradle, 'variant.applicationId.set(if (isPlaystore) "com.nuvio.appdebug" else "com.nuvio.livetv")',
                 'variant.applicationId.set(if (isPlaystore) "com.nuvio.appdebug" else "com.petalix.iptv.staging")')
    replace_once(gradle, 'variant.applicationId.set(if (testBuild) "com.nuvio.livetv.test" else "com.nuvio.livetv")',
                 'variant.applicationId.set(if (testBuild) "com.petalix.iptv.staging" else "com.petalix.iptv.preview")')

    # Upstream points its DEBUG build to the private release keystore, which is
    # intentionally unavailable in public staging CI. Restore Android's default
    # disposable debug keystore; customer updates still require the original
    # protected Petalix release signer.
    replace_once(gradle, 'debug {\n            signingConfig = signingConfigs.getByName("release")',
                 'debug {\n            signingConfig = signingConfigs.getByName("debug")')

    # The original upstream updater would install somebody else's APK/signature.
    # The native app's UI and Sports implementation remain otherwise untouched.
    replace_once(gradle, 'buildConfigField("boolean", "FEATURE_IN_APP_UPDATES_ENABLED", "true")',
                 'buildConfigField("boolean", "FEATURE_IN_APP_UPDATES_ENABLED", "false")')

    res = root/"app/src/main/res"
    brand_strings(res)
    # Use the existing reviewed branding-only text substitution for Kotlin UI labels.
    # It excludes core logic, internal namespaces, upstream URLs and legal credits.
    import importlib.util
    patch_path = Path(__file__).resolve().parents[1]/"android-build/branding_patch.py"
    spec = importlib.util.spec_from_file_location("petalix_branding_patch", patch_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.apply(root, "petalix")
    update_artwork(res, args.logo)
    apply_panel_bridge(root)
    details = {
        "base_tag": "1.1.0-beta.11.1",
        "base_sha": PINNED_SHA,
        "brand": "Petalix",
        "package_name": APPLICATION_ID,
        "debug_package_name": "com.petalix.iptv.staging",
        "version_code": VERSION_CODE,
        "version_name": VERSION_NAME,
        "native_guide": "unaltered",
        "native_sports": "unaltered",
        "panel_bridge": "Petalix PIN/session and playlist rules (test)",
        "customer_ready": False,
        "reason": "Requires original release signature plus Petalix panel PIN/session migration verification"
    }
    print(json.dumps(details, indent=2))


if __name__ == "__main__":
    main()
