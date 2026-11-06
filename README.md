# OpenGuide TV — Independent Android TV beta

A standalone Android TV app that accepts your own M3U playlist URL or file and optional XMLTV guide. Built independently from Binged and from Petalix. This beta uses a WebView UI plus a native Media3 ExoPlayer.

Features: live channels, guide, favorites, sports categories, user-selected locals, ZIP-code preference, Movies/Series category filters, on-device settings, Android TV remote/D-pad navigation, M3U URL/file import, XMLTV URL import.

Not yet implemented: automatic ZIP-based lineup mapping, actual sports schedules, multiview, recordings, Xtream login, and provider-specific headers. Your playlist must contain authorized streams.

The build workflow runs on branch openguide-tv-beta and creates the OpenGuide-TV-debug-APK artifact. Download the ZIP artifact and extract app-debug.apk to install on Shield.

This app is compiled to a different package (com.openguide.tv) and does not modify Petalix. The Petalix default main branch is unchanged.

On TV: Settings -> Add M3U Playlist, input the URL or choose a file. Optionally import XMLTV for a populated guide.

Do not share playlist URLs containing private login tokens.
