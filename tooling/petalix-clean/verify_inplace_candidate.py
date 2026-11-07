#!/usr/bin/env python3
"""Fail closed if a Petalix 11.1 candidate loses upgrade identity or saved PIN wiring.

This is a source-level development gate, NOT a signing, backend, or activation test.
Only run against source generated from the pinned Nuvio+ 1.1.0-beta.11.1 tag.
"""
import argparse
from pathlib import Path

PACKAGE = "com.petalix.iptv.preview"
STAGING = "com.petalix.iptv.staging"
MIN_VERSION_CODE = 1793709919  # Independently verified previous panel-signed test.


def require(content: str, needle: str, label: str) -> None:
    if needle not in content:
        raise RuntimeError(f"Petalix upgrade guard failed: {label}")


def verify(root: Path) -> None:
    gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    coordinator = (root / "app/src/main/java/com/nuvio/tv/petalix/PetalixManagedCoordinator.kt").read_text(encoding="utf-8")
    activity = (root / "app/src/main/java/com/nuvio/tv/petalix/PetalixActivationActivity.kt").read_text(encoding="utf-8")
    live = (root / "app/src/main/java/com/nuvio/tv/livetv/data/LiveTvPreferences.kt").read_text(encoding="utf-8")
    main = (root / "app/src/main/java/com/nuvio/tv/MainActivity.kt").read_text(encoding="utf-8")

    require(gradle, f'applicationId = "{PACKAGE}"', "original default package")
    require(gradle,
            f'variant.applicationId.set(if (testBuild) "{STAGING}" else "{PACKAGE}")',
            "release variant keeps original package, test variant remains separate")
    require(gradle, f'variant.applicationId.set(if (isPlaystore) "com.nuvio.appdebug" else "{STAGING}")',
            "debug variant isolated from customer package")
    require(gradle, 'buildConfigField("boolean", "FEATURE_IN_APP_UPDATES_ENABLED", "false")',
            "disable upstream replacement/update channel")
    require(gradle, 'buildConfigString("https://petalix-control.pinkgiant5.chatgpt.site")',
            "Petalix panel origin")
    require(gradle, 'versionCode = 17942500',
            "expected monotonically newer versionCode base")
    require(gradle, 'debug {\n            signingConfig = signingConfigs.getByName("debug")',
            "public CI uses disposable debug signing, not release credentials")

    require(coordinator, 'getSharedPreferences("petalix_session_v2"', "existing saved-session preference alias")
    require(coordinator, '"petalix.session.v2"', "existing AndroidKeyStore session alias")
    require(coordinator, '.put("packageName", BuildConfig.APPLICATION_ID)', "server-reported approved Android package ID")
    require(coordinator, '.put("brand", "petalix")', "correct activation service")
    require(coordinator, '.put("deviceId", deviceId)', "existing device record ID")
    require(coordinator, 'if (!hasSavedActivation) throw PetalixActivationRequired()', "saved session is not lost after restart")
    require(coordinator, 'if (response.code in listOf(401, 403) && bearer != null)',
            "server revocation/expiry handling")
    require(coordinator, 'live.updatePlaylists', "managed provider configuration delivery")
    require(coordinator, 'live.applyPetalixPlaylistRules', "managed playlist sorting/hiding")
    require(activity, 'if (manager.hasSavedActivation)', "existing customer activation restoration")
    require(main, 'petalixBridge.refresh()', "periodic server verification")
    require(live, 'com.nuvio.tv.petalix.PanelPlaylist.view', "guide rules overlay")

    # Guardrail: the PIN bridge is useful only when production signing, version,
    # allowlist and on-device data retention have been independently validated.
    print("PASS: in-place Petalix package and source-level PIN/guide upgrade invariants.")
    print("NOT PROVEN: release signing, panel authorization, preserved device data, live playback.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", type=Path, required=True)
    args = parser.parse_args()
    verify(args.upstream.resolve())


if __name__ == "__main__":
    main()
