# Petalix 2.0 Test 2 — safe release integration

Source branch: `test/petalix-2-fullscreen`, commit `7f716ea`.

The isolated package is `com.petalix.iptv.ui2test`, version `2.0.0-ui-test.2` (200002). The uploaded Test 2 APK uses an **Android debug signing certificate**, unlike production Petalix 8.3. Its SHA-256 and signer fingerprint are pinned in `verify_test_release.py`.

The separate GitHub Actions workflow downloads the original public APK, verifies the binary checksum, package, version and certificate, then publishes an **unapproved prerelease** with a test-only JSON manifest. It does not register devices, deploy to customers, or mark activation as successful. It does not use or alter the production signing credentials.

## Required work in the existing Petalix Control server

1. Inspect the device activation endpoint's package/service identity policy and explicitly authorize the UI2 test package **only** for approved test users/devices, including the intended signing identity and version. Never disable package or signature verification.
2. Have the existing panel's **Test Builds** channel import the verified GitHub prerelease APK and its release JSON. Display version, package, SHA-256, certificate fingerprint, source commit, download and **activation blocked** status, separately from Production. Do not trigger the current production promotion or automatic customer update actions.
3. Verify PIN activation with an authorized account and status renewal on Android TV / Nvidia Shield. Then test D-pad navigation, Home UI, service layouts, Live TV EPG, VOD and playback. Only then mark activation/device testing passed.
4. Keep future test delivery in the same panel-connected GitHub pipeline, not on an unregistered second website.

The GitHub repository contains Android source and release tooling, **not the deployed Petalix Control server code or its backend configuration**. A prerelease alone cannot change the backend's `/api/device/activate` allowlist or website interface. The existing panel runner (`tooling/android-build/runner.py`) requires a server-issued build job and trusted signing flow; never fabricate that identity or spoof a production package.

**Never claim activation works until it has passed on a real test device.**
