# Petalix continuation in personal Work

Project: STCC + Petalix, appgprj_6acf877e0cf081918e344a7be1051e24.
Live panel: https://petalix-control.caroliinaconsultant.chatgpt.site
GitHub: camosclem/STCC-PETALIX-Builds, branch test/petalix-13.2-home.

Stay on Homeless Brian NuvioPlusIPTV 1.1.0-beta.13.2, pinned commit 6e18ff250d93c0c1bac64bda75f49b035027ab99. No Brock/Nuvio launcher or Monet launcher. Test package com.petalix.iptv.hometest installs separately from customer package com.petalix.iptv.preview. Preserve the production signing key; never rotate it or distribute this isolated test as a production OTA.

Chris tests first. Do not publish panel changes, reset PINs, revoke devices, clear customer data, or send updates to active viewers. Do not watch/poll builds; Chris reports when they finish. Then check the result, fix failures if necessary and return the exact APK asset matching the latest branch commit.

Latest tested build before this repair: commit 3802f48235e4065fa728ce1ab8104b6e0bf0d504, APK version 1794921716. Compilation passed, but Chris reported missing Live TV shortcuts, missing show thumbnails, broken BritBox, provider VOD absent, unwanted Pengu and collection/remote labels, wrong startup/guide color.

Device feedback repair restores the Live TV row in the actual Home row composer, ordered My Locals, Entertainment, Sports, News. Programme/EPG artwork and exact-title TMDB backdrops update the cards and focused hero. Local assets remain fallback when programme metadata is absent. Landscape artwork falls back to posters and adopts refreshed enrichment. BritBox gets explicit local card/logo artwork, TV-first sources and a one-time saved-folder repair. Remove STREAMING COLLECTION and remote OK hints. Pengu is disabled unless explicitly enabled from raw panel settings; previously enabled inherited assignments require saving off, then on. Keep Kaptain artwork only; no Kaptain playback addon.

Provider VOD: keep On Demand visible while importing or on errors, enable assigned import, restart import after connection changes under a stable source ID, clear the old retry delay once, retry transient failures after five minutes, preserve imported data. Include populated provider movie/series shelves on Home. This is import recovery, not proof the provider endpoint works on Chris's device. If it still fails, read the exact On Demand error and diagnose the selected TV's authenticated provider route; do not substitute the owner's master credentials for customer logins.

Default startup Home, no auto-play last channel, native default theme/guide backdrop. Preserve later owner/viewer settings after one-time migration. Native actor/show details already include cast photos, biographies, filmography, directors, seasons and episode info. Enable TMDB artwork/basic/details/credits/episode enrichment. Favorites backup and restore remains panel controlled, ZIP locals excludes sports/team duplicates.

Sports: generic NFL/NHL/NBA/MLB replay channels cannot match specific games. Accept replay only when both teams are identified; display programme matchup in the match label. Keep channel identities and favorites unchanged.

Verification still required: install over Home Test without clearing data, check first opening is Home; four Live TV tiles before streaming services; focus each tile and recent channel; BritBox content, not just icon; TV artwork; provider VOD import, direct playback and episode playback; actor biographies and credits; ZIP excludes sports; replay options identify matchups; sidebar/Back restoration; favorites. Automated compile/tests cannot establish live provider playback or TV rendering.

Account continuity: owning the project/repository and having permissions is required in the destination account. This document does not move project ownership or customer data. No credentials, PINs or signing secrets are included here.
