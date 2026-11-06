# Petalix TV Beta 0.3 (Binged-style design)

Independent Android TV test app (package com.petalix.tv.guidebeta). This does not modify the production Petalix app.

Changes from 0.2:
- NVIDIA Shield TV remote: directional spatial focus, OK/Select, menu and existing Back behavior.
- Stream a large M3U source in bounded JSON batches; retain up to 2800 live, 800 movies, 500 shows, and use per-group caps to spread channels across categories. Excluded entries are counted.
- Animated streaming-service collection tiles with original custom focus sounds and an Appearance > Navigation sounds toggle; honors reduced-motion.
- Existing M3U URL/file, Xtream login, XMLTV, and native Media3 playback are retained.

Note: This is a debug beta. If Android refuses to install on top of 0.2 due to different debug signing certificates, uninstall the older Petalix TV Beta first. That will clear its locally stored playlist settings. Do not uninstall the separate production Petalix app.

Your playlist login is not embedded in the APK or source. Enter it on-device in Settings > Playlists.

Android TV hardware, provider playlist behavior, and native playback remain to be tested.