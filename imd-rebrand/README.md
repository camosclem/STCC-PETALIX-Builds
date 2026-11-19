# Petalix IMD preview

Petalix is a modified build of [IMD TV by majorleeevil](https://github.com/majorleeevil/imd-tv), based on v0.5.2 at commit `369d43e05ba9de7e5b61fb0621fb7f2bf7558da8`.

Changes on November 19, 2026 include Petalix branding, a separate Android package, a plum and gold palette for Celestial Glass, PIN setup for Petalix Control, a ZIP lookup for My Locals, and a Sports Hub category based on the loaded playlist. The upstream updater is disabled in this preview. IMD TV's guide, playlist sync, and Movies and Series screens are used.

The app and its modifications are released under GPL-3.0. The APK release includes the corresponding source and original licence. To rebuild, apply `petalix.patch` to the commit above, copy this directory's `res/` into `app/src/main/res/`, copy `tooling/android-build/local-stations.json` from this repository into `app/src/main/assets/`, and run `./gradlew :app:assembleRelease` with Java 17 and Android SDK 35.

Use a separate preview device PIN in setup. Choose Local channels ZIP code after connecting or under Settings → Playlists. Manual Xtream, M3U and Stalker setup is also available. Preview package `com.petalix.imdpreview` uses the runner's debug signature.
