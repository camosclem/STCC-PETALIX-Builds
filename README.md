# Petalix Native Android TV beta v0.6

Native Android TV Home, Sports, Teams, Favorites and native Guide with a Media3 preview window, based on the user's Android TV reference screenshots. No `Your apps` rail, and no external streaming-service tiles in the native Home.

**Navigation**: D-pad and OK work through Android's native View focus system. Long-press OK on a channel to favorite. Guide categories, channels and preview are native. Home has channel rails from the actual connected M3U/Xtream/PIN catalog. Sports supports optional beta ESPN scores and schedules independently of the M3U provider. Public undocumented ESPN scoreboards are available only in DEBUG builds; production/commercial distribution requires permission or licensed sports data. No stream URLs or ESPN secrets are extracted from Binged.

**Sources**: M3U/Xtream imports, Movies/TV Shows browsing and optional PIN activation are retained through the existing hybrid Settings/import screen. That importer now synchronizes its channel catalog in bounded batches to a local native cache; no service credentials are compiled into the APK. PIN activation remains unconfigured until the separate beta panel is hosted, so choose beta preview in a debug build.

**Not yet complete**: an exact visual/behavior match to Binged, licensed commercial sports scores and game feeds, replay/DVR backend, fully native Movies/Shows and Settings, independent TV device QA, and production panel deployment. The existing Petalix production app is untouched.

Build via the `Petalix Native Android TV v0.6 beta` GitHub Actions workflow on `test/petalix-native-v06`.

## Beta v0.6.2 test changes
- Native My Locals in top navigation. ZIP lookup only reports streams actually present in the saved playlist; exact 28601/28602 are mapped to Charlotte/Hickory, with limited beta suggestions for other major-city prefixes. No invented or nationally supplied streams.
- Correct callsign-based prioritization, allow manually choosing another market or searching by station/callsign with maximum 12 results. The hosted admin panel must later supply nationwide, exact ZIP-to-market assignments.
- Home rails limited to 24 tiles with a View all shortcut to the guide (avoids rendering hundreds of live logos simultaneously); focus is retained across refreshes and returning from playback.
- New local station selection persists on device, appears on Home, and heads the TV guide.
- This is a **beta**, not yet ready for customer deployment. Xtream, sports data usage rights, network reliability, and broad device compatibility require testing.
