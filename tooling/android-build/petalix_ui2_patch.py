"""Prepare the existing Petalix 2 design for the *original* authorized Petalix signing path.

This never embeds a key, disables activation, authorizes an alternate Android
package, or touches STCC. The signed APK MUST be issued by the panel's existing
OIDC-controlled release signing workflow. DO NOT sideload the keyless build.
"""
import os
import pathlib
import re
import subprocess
import sys

PINNED_UI2_SOURCE = "7f716eab5c80b156f26ad238762f683b807591f4"
AUTHORIZED_PACKAGE = "com.petalix.iptv.preview"

def replace_exact(original, old, new, label):
    if original.count(old) != 1:
        raise RuntimeError(f"Petalix 2 authorized integration requires review: {label}")
    return original.replace(old, new)

def patch_authorized_identity(work, version_code, version_name):
    root = pathlib.Path(work)
    number = int(version_code)
    if not (1 <= number <= 2_100_000_000):
        raise RuntimeError("Unrecognized panel-issued Android version code")
    if not re.fullmatch(r"1\.1\.0-petalix\.panel[0-9]+", version_name):
        raise RuntimeError("Unrecognized panel-issued Petalix version name")
    if not version_name.endswith(str(number)):
        raise RuntimeError("Panel version name/code mismatch")
    gradle = root / "app/build.gradle.kts"
    before = gradle.read_text(encoding="utf-8")
    after = replace_exact(
        before,
        'variant.applicationId.set("com.petalix.iptv.ui2test")',
        f'variant.applicationId.set("{AUTHORIZED_PACKAGE}")',
        "test-only application ID",
    )
    after = replace_exact(
        after,
        "android.defaultConfig.versionCode = 200002",
        f"android.defaultConfig.versionCode = {number}",
        "test-only version code",
    )
    after = replace_exact(
        after,
        'android.defaultConfig.versionName = "2.0.0-ui-test.2"',
        f'android.defaultConfig.versionName = "{version_name}"',
        "test-only version name",
    )
    gradle.write_text(after, encoding="utf-8")

    manifest = root / "app/src/main/AndroidManifest.xml"
    src = manifest.read_text(encoding="utf-8")
    src = replace_exact(src, 'android:label="Petalix 2 Test"', 'android:label="Petalix"', "manifest label")
    manifest.write_text(src, encoding="utf-8")

    coordinator = root / "app/src/main/java/com/nuvio/tv/petalix/ManagedCoordinator.kt"
    content = coordinator.read_text(encoding="utf-8")
    if '.put("packageName",BuildConfig.APPLICATION_ID)' not in content:
        raise RuntimeError("Existing server-managed PIN activation binding changed")
    if 'post("activate",report().put("code",pin),null)' not in content:
        raise RuntimeError("Existing authorized activation path missing")

    connection = root / "app/src/main/java/com/nuvio/tv/petalix/PanelConnectionPolicy.kt"
    policy = connection.read_text(encoding="utf-8")
    if 'petalix-control.caroliinaconsultant.chatgpt.site' not in policy:
        raise RuntimeError("Existing verified panel connection was removed")
    if 'com.petalix.iptv.ui2test' in after:
        raise RuntimeError("Isolated test package survived the Gradle override")
    return AUTHORIZED_PACKAGE

def prepare_authorized_ui2(item, work, cache):
    """Reconstruct pinned UI2 source and replace only version, ID and app label."""
    src = pathlib.Path("ui2-sources").resolve()
    if not (src / "tooling/petalix2/prepare.py").is_file():
        raise RuntimeError("Pinned Petalix 2 source checkout missing")
    sha = subprocess.run(["git", "-C", str(src), "rev-parse", "HEAD"],
                         check=True, capture_output=True, text=True).stdout.strip()
    if sha != PINNED_UI2_SOURCE:
        raise RuntimeError("Petalix 2 source commit mismatch")
    from home_baseline import FILES, baseline_url, verify_archive, UPSTREAM
    if item.get("source", {}).get("sha") != UPSTREAM:
        raise RuntimeError("Petalix 2 requires the exact verified Home baseline revision")
    filename, _ = FILES["petalix"]
    archive = pathlib.Path(cache) / filename
    if not archive.exists():
        from build import download
        download(baseline_url("petalix"), archive)
    verify_archive(archive, "petalix")
    if pathlib.Path(work).exists() and any(pathlib.Path(work).iterdir()):
        raise RuntimeError("Petalix 2 must reconstruct into an empty workspace")
    subprocess.run([sys.executable, str(src / "tooling/petalix2/prepare.py"),
                    str(work), "--archive", str(archive.resolve())], check=True)

    code = int(item["versionCode"])
    name = f"1.1.0-petalix.panel{code}"
    patch_authorized_identity(work, code, name)
    api_key = os.environ.get("TMDB_API_KEY", "")
    if not re.fullmatch(r"[0-9a-fA-F]{32}", api_key):
        raise RuntimeError("Existing Petalix Control metadata settings were not supplied")
    # prepare.py already writes local.properties from the authorized panel's
    # masked TMDB_API_KEY; no hard-coded private key is stored in GitHub.
    return name

def verify_authorized_source_archive(original, updated):
    """UI2 is a deliberate full UI change; require its source plus secure PIN binding."""
    import zipfile
    from home_baseline import verify_archive
    verify_archive(original, "petalix")
    with zipfile.ZipFile(updated) as source:
        files = source.namelist()
        required = [
            "app/src/main/java/com/nuvio/tv/petalix/ManagedCoordinator.kt",
            "app/src/main/java/com/nuvio/tv/petalix/ui2/Petalix2Home.kt",
            "app/src/main/java/com/nuvio/tv/petalix/ui2/Petalix2Services.kt",
            "app/src/main/java/com/nuvio/tv/petalix/PanelConnectionPolicy.kt",
        ]
        for path in required:
            match = [name for name in files if name.endswith("/" + path)]
            if len(match) != 1:
                raise RuntimeError("Authorized UI2 source is incomplete: " + path)
        data = source.read(next(name for name in files if name.endswith(
            "/app/src/main/java/com/nuvio/tv/petalix/ManagedCoordinator.kt"))).decode()
        if '.put("packageName",BuildConfig.APPLICATION_ID)' not in data:
            raise RuntimeError("Authorized PIN package binding missing from source")
