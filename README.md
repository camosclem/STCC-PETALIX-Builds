# Petalix TV · Binged-style independent Android TV beta v0.2

This is a **new, independently packaged test app**, not the existing Petalix production app and not a modification of Binged code. The application ID is `com.petalix.tv.guidebeta` so it installs beside existing apps.

## New in v0.2
- Original, Binged-inspired dark UI with top navigation, expandable side category menu, featured live-TV hero, channel grid and horizontal program guide
- Petalix transparent icon and wordmark
- Add authorized M3U URL or local M3U file
- Add Xtream Codes with server URL, username and password, including live streams, VOD movies, series and on-demand episodes when supported by the provider
- Optional XMLTV program guide URL
- **Movies** and **TV Shows** streaming-service-inspired browsing tiles for Netflix, Prime Video, Hulu, Disney+, Max, Paramount+, Apple TV+, Peacock, Tubi and Pluto TV, plus Other
- Service tile assignment uses text hints from the connected provider's categories; it does not provide access to those independent services or verify their catalogs. A title not identified by provider metadata appears in Other
- Favorites, My Locals manual channel selection, ZIP-code preference, Search, Sports filters, Teams interface, and Continue Watching
- Native Android Media3 player for URL streams; HTTP/HLS/DASH playback subject to codec/authorization support

## Not yet functional
- Binged cloud recording/Replays (UI only)
- Automatically identifying ZIP-code local channels (currently manual)
- Live game schedules/scores and automatic team tracking
- True cross-device Arvio sync and reliable playback position resume
- Native streaming-service logins, multi-view, DRM, advanced stream header configuration
- A complete device/player compatibility matrix

## Credentials and limitations
Playlist URLs and Xtream login credentials are currently stored in local Android WebView storage for this beta. Do **not** share the APK/device with credentials saved, and do not publish private URLs. Prefer HTTPS if provider supports it. No sources, channels, movies or shows are bundled.
Xtream API credentials must be for an account you are authorized to use.
A successful APK compilation does not establish that all Xtream providers or streams work.

## Build / test
The GitHub workflow on `openguide-tv-beta` builds a **debug-signed** APK with Android SDK 35 using Gradle. GitHub Releases has the direct-download beta asset.
Install on NVIDIA Shield via the APK, open **Settings → Playlists & Sources**, add M3U or Xtream, then browse Live TV, Movies and TV Shows.
