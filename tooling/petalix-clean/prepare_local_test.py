#!/usr/bin/env python3
"""Prepare PERSONAL-ONLY Nuvio+ 11.1 Petalix DEBUG test (no panel writes).

This is a separable test app to let the owner test the native Nuvio guide,
sports and playback with sources added locally inside Settings. It is NOT an
authenticated Petalix customer app, update to 8.3, or production alternative.

The ordinary Petalix 11.1 prepare_stock.py script runs first; this follow-up
changes ONLY the DEBUG variant. Release still requires existing panel signing.
"""
import argparse
import shutil
from pathlib import Path

APP_ID = "com.petalix.iptv.localtest"


def once(file: Path, old: str, new: str, label: str) -> None:
    text = file.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise RuntimeError(f"Local-test-only patch requires review: {label} ({text.count(old)} matches)")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


def apply(root: Path) -> None:
    gradle = root / "app/build.gradle.kts"
    main = root / "app/src/main/java/com/nuvio/tv/MainActivity.kt"
    coordinator = root / "app/src/main/java/com/nuvio/tv/petalix/PetalixManagedCoordinator.kt"
    repository = root / "app/src/main/java/com/nuvio/tv/livetv/data/LiveTvRepository.kt"

    once(
        gradle,
        'variant.applicationId.set(if (isPlaystore) "com.nuvio.appdebug" else "com.petalix.iptv.staging")',
        f'variant.applicationId.set(if (isPlaystore) "com.nuvio.appdebug" else "{APP_ID}")',
        "separate disposable-debug app identity",
    )
    # Staging source forces a Petalix PIN at launch. The user's personal debug
    # build instead uses upstream Nuvio's normal on-device Live TV playlist setup.
    # This is not a server-side activation bypass, does not touch release builds,
    # and does not grant access to any panel-assigned streams or content.
    once(
        main,
        '        if (!petalixBridge.ready) {',
        '        if (!BuildConfig.DEBUG && !petalixBridge.ready) {',
        "PIN gate remains mandatory for non-debug/production",
    )
    once(
        main,
        '        lifecycleScope.launch {\n            while (true) {\n                try { petalixBridge.refresh() }',
        '        if (!BuildConfig.DEBUG) lifecycleScope.launch {\n            while (true) {\n                try { petalixBridge.refresh() }',
        "panel session refresh remains mandatory for non-debug/production",
    )
    # The fork's Gradle config can make the debug variant non-debuggable.
    # Match the isolated application ID rather than relying on BuildConfig.DEBUG.
    once(
        main,
        'if (!BuildConfig.DEBUG && !petalixBridge.ready) {',
        'if (BuildConfig.APPLICATION_ID != "com.petalix.iptv.localtest" && !petalixBridge.ready) {',
        "only the separate personal app skips managed activation",
    )
    once(
        main,
        'if (!BuildConfig.DEBUG) lifecycleScope.launch {',
        'if (BuildConfig.APPLICATION_ID != "com.petalix.iptv.localtest") lifecycleScope.launch {',
        "only the separate personal app skips panel polling",
    )

    # Categorize the owner's imported source IN THE APP on every source refresh.
    # Never include personal playlist URLs, credentials or mutable feed snapshots
    # in the public source tree or APK.
    source = Path(__file__).with_name("android")
    shutil.copyfile(source / "PetalixLineup.kt",
                    root / "app/src/main/java/com/nuvio/tv/petalix/PetalixLineup.kt")
    tests = root / "app/src/test/java/com/nuvio/tv/petalix"
    tests.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source / "PetalixLineupTest.kt", tests / "PetalixLineupTest.kt")
    once(
        repository,
        '        _channels.value = out\n    }\n\n    private suspend fun buildPrograms(',
        '        _channels.value = if (com.nuvio.tv.BuildConfig.APPLICATION_ID == "com.petalix.iptv.localtest") '
        'com.nuvio.tv.petalix.PetalixLineup.organize(out, multi) else out\n'
        '    }\n\n    private suspend fun buildPrograms(',
        "curate imported M3U/Xtream channels while preserving original keys and streams",
    )

    # Defensive verification: no production identity, release signing, or
    # saved activation storage is modified.
    a = gradle.read_text(encoding="utf-8")
    m = main.read_text(encoding="utf-8")
    c = coordinator.read_text(encoding="utf-8")
    assert f'variant.applicationId.set(if (isPlaystore) "com.nuvio.appdebug" else "{APP_ID}")' in a
    assert 'variant.applicationId.set(if (testBuild) "com.petalix.iptv.staging" else "com.petalix.iptv.preview")' in a
    assert 'debug {\n            signingConfig = signingConfigs.getByName("debug")' in a
    assert 'BuildConfig.APPLICATION_ID != "com.petalix.iptv.localtest" && !petalixBridge.ready' in m
    assert 'BuildConfig.APPLICATION_ID != "com.petalix.iptv.localtest") lifecycleScope.launch' in m
    assert "PetalixLineup.organize(out, multi)" in repository.read_text(encoding="utf-8")
    assert (root / "app/src/main/java/com/nuvio/tv/petalix/PetalixLineup.kt").exists()
    assert 'getSharedPreferences("petalix_session_v2"' in c
    assert '.put("packageName", BuildConfig.APPLICATION_ID)' in c
    assert 'buildConfigField("boolean", "FEATURE_IN_APP_UPDATES_ENABLED", "false")' in a
    print("PASS: separate personal Petalix app with on-device USA sports/locals/24-7 lineup. No customer update or activation change.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", required=True, type=Path)
    args = parser.parse_args()
    apply(args.upstream.resolve())
