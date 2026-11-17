# Petalix cinema Home test

This test changes the native Home composition, not the Android launcher or the live panel.

## Implemented

- Full-width hero artwork with a readable dark gradient, compact Home heading and a new title panel.
- Responsive 16:9 catalog shelves and Continue Watching cards, with a white focus outline.
- Focused movie and series trailers in the hero after a two-second dwell; shelves stay visible.
- Foreground, sidebar and scroll guards for preview playback; trailer requests wait for settled focus.
- Current title and artwork render while additional metadata loads instead of showing a previous title.
- Existing branded service art, native service intro animations and BritBox repair retained.
- Native sidebar, guide, ZIP locals filtering, provider-first playback and panel favorites retained.

## Device acceptance before any customer release

1. Update Petalix Home Test without clearing data. Verify the new hero and landscape shelves appear for the existing profile.
2. Move across all nine service cards. Verify each logo/art and animation, including BritBox at the end of the row.
3. Move across Sports, News, Entertainment and My Locals. Verify the hero title/art follows focus and does not show a previous service.
4. Move across recently watched channels. Verify current programme art or channel fallback follows focus.
5. Pause on a movie/show with an available trailer. Verify playback starts in the main preview, while shelves remain visible.
6. Move away, open the sidebar, press Home, and return. Verify there is no old trailer audio/video continuing in the background.
7. Open a movie and an episode from provider VOD, resume playback, press Back, and verify the selected shelf/tile is restored.
8. Check ZIP locals excludes sports/team duplicates and the native guide and favorites still work.
9. Test on Shield and ONN. Do not distribute the isolated test package as a production OTA.

Compilation and automated tests do not establish that live provider playback or TV rendering has passed these checks.

## Fresh catalogs and artwork

The replacement test adds authoritative TMDB daily movie and TV trending charts, popular movies and TV, cinemas and TV airing today. No Pengu/provider category substitutes or fabricated Netflix viewing rankings. Successful chart snapshots are persisted, checked every minute while Home is visible and refreshed after 30 minutes. Failed requests back off for five minutes and retain saved rows. Native title and ratings enrichment remains in place; TMDB vote averages are no longer labelled IMDb.

Remote artwork memory entries expire every six hours, with background ETag/Last-Modified checks and visible logo/hero invalidation. The existing branded card designs remain local; supplementary Kaptain collages use current remote files with bundled offline fallbacks. New valid artwork at the approved URLs no longer breaks APK preparation because of an old image hash. This cannot force an upstream host to publish newer art or correct missing logos.

The test sets playlist and guide refresh intervals to four hours once per profile. Subsequent owner/viewer choices remain available. Native background scheduling, active-player safeguards and favorites are retained. MDBList retains its existing authenticated metadata integration and cache; no subscription or API key is created or modified.

Additional acceptance: reopen Home offline, verify saved shelves remain, then reconnect and verify daily movie and TV shelves refresh without clearing app data. Verify manual channel logo choices remain respected. Verify provider playback and existing favorites after refresh. Do not install or distribute the superseded cinema-only build.

## Repairs from Chris's TV test

The prior combined build compiled but was not accepted on TV. The Live TV row was accidentally omitted by the catalog composer; the new composer has a regression test for its actual rendered row list. Four shortcuts precede streaming services in this order: My Locals, Entertainment, Sports, News. Programme art from EPG or an exact-title TMDB lookup drives the shortcut and focus hero; bundled art remains a fallback when metadata is absent.

Remove the STREAMING COLLECTION caption and remote OK hints. Repair existing BritBox folder artwork and sources once; put TV shelves first rather than an empty movie shelf. Poster fallback is connected to the native card loader; adopt refreshed backdrop enrichment instead of retaining a broken image indefinitely.

Native default theme/guide backdrop and Home startup are restored once per profile, including after the first panel assignment. Later owner/viewer settings remain adjustable. On Demand stays in the sidebar during an empty/failed import, assigned imports are enabled, initial stale failure timestamps are cleared once, connection changes under the same source ID trigger reimport, and failed imports retry after five minutes. Populated provider movie and series rows are included on Home. These changes do not prove that the selected device's upstream feed is healthy.

Pengu requires a raw explicit panel opt-in; inherited assignments are disabled. If Pengu was already explicitly enabled before this update, save it off, then on to enable it again. No playback subscription is purchased. Native credits/actor biography/filmography/episode metadata screens are retained and their TMDB enrichment defaults enabled.

Sports matching rejects generic league replay channels unless both sides of the selected matchup are identified. When available, programme matchup appears in the replay option label. No channel, PIN, favorite or production user record is deleted.

Recheck BritBox content, not merely the thumbnail; VOD import count and playback; Home rather than guide on fresh opening; guide palette; show images; four focused shortcut backgrounds; native credits and actor pages; generic replay exclusion. The build remains an isolated test.
