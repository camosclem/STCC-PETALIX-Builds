# Petalix Native Android TV beta v0.6

Native Android TV Home, Sports, Teams, Favorites and native Guide with a Media3 preview window, based on the user's Android TV reference screenshots. No `Your apps` rail, and no external streaming-service tiles in the native Home.

**Navigation**: D-pad and OK work through Android's native View focus system. Long-press OK on a channel to favorite. Guide categories, channels and preview are native. Home has channel rails from the actual connected M3U/Xtream/PIN catalog. Sports lists channels rather than inventing live game data.

**Sources**: M3U/Xtream imports, Movies/TV Shows browsing and optional PIN activation are retained through the existing hybrid Settings/import screen. That importer now synchronizes its channel catalog in bounded batches to a local native cache; no service credentials are compiled into the APK. PIN activation remains unconfigured until the separate beta panel is hosted, so choose beta preview in a debug build.

**Not yet complete**: an exact visual/behavior match to Binged, real sports scores and game feeds, replay/DVR backend, fully native Movies/Shows and Settings, independent TV device QA, and production panel deployment. The existing Petalix production app is untouched.

Build via the `Petalix Native Android TV v0.6 beta` GitHub Actions workflow on `test/petalix-native-v06`.
