# Petalix 2 native Android TV test build

This branch contains the reviewed Petalix 2 UI changes for the checksum-pinned managed
Petalix source package `1793124972`.

- Package: `com.petalix.iptv.ui2test`
- Version: `2.0.0-ui-test.2` (`200002`)
- Source branch: `test/petalix-2-fullscreen`
- Baseline SHA-256: `3131640e3324b5d2fc3fa5e016379aee834266fcf2900ed2c8066af9184ad518`
- Upstream revision: `50e713e33b9ed6fc48bd3bdec5a773e0a552d160`

The test package installs beside the customer app. It does not update production, alter
panel settings, or include a production signing key.

## Prepare and build

Use JDK 17, Android SDK 36, Build Tools 36.0.0, and NDK 29.0.14206865.

```bash
export ANDROID_HOME=/absolute/path/to/android-sdk
export TMDB_API_KEY=your_own_tmdb_v3_key
python3 tooling/petalix2/prepare.py /absolute/empty/petalix2-source
cd /absolute/empty/petalix2-source
./gradlew :app:assembleFullDebug :app:testFullDebugUnitTest \
  --tests 'com.nuvio.tv.petalix.*' --no-daemon
```

The preparation verifies the pinned baseline archive and overlays the 32 reviewed modified
files. The abandoned truncated profile-writer temporary file is removed. No metadata key,
customer configuration, or signing credential is committed.

## Scope

- Full-width Home and six-item top navigation with no left sidebar or On Demand tab.
- Separate Movies and TV Shows routes using the existing details and playback paths.
- Distinct service-inspired Netflix, Disney+, Prime Video, Hulu, Max, Paramount+, Apple TV,
  Peacock, Tubi, and Pluto experiences.
- Provider-scoped catalog queries and search over the loaded service catalog.
- Horizontal Live TV categories retaining the existing EPG, preview/player, favorites,
  recently watched, parental visibility, and playlist behavior.
- Existing device activation, accounts, history, favorites, and playback source code retained.

The service browsers do not contain proprietary service code or bypass subscriptions or DRM.
Device activation, D-pad behavior, live playback, and VOD playback require Android TV testing
with authorized accounts and sources.

The isolated debug build skips ART startup-profile compilation and its dependent update-dex
metadata task because the recovered export has no generated L8 profile for the new test package.
Release build behavior is unchanged.
