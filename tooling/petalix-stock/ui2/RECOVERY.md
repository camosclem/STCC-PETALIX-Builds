# Petalix service UI provenance

Recovered from the panel-built Nuvio + IPTV 8.3 generation, build **1793881218**.

- Release tag: `panel-7f824415-e623-4fed-a898-89550ef3c693`
- Archive: `Petalix-UI2-Authorized-Test-1793881218-Source.zip`
- Archive SHA-256: `88beb6fe08b948b3909c1cfe88e2474010738e715a75f0fb450bda862e8998f9`
- Source recipe: `d557c3e`; UI2 foundation: `906bdb444b911b9f5eb105d4f9f9ae37839e1f2f`

The eight final service landing compositions, their matching title/episode
presentations, search, and shared TV controls were recovered from the verified
release source. The separate generic Arvio hub was not its final service entry:
the final entry calls `DistinctPetalixServiceScreen`.

The portable adapter resolves the existing native collection folder IDs, reads
the current eleven-service catalog, and uses the authenticated runtime metadata
key. It retains the archived Tubi/Pluto layouts and adds an explicit BritBox
landing and detail treatment. Current provider associations match the seeded
stock catalog. Obsolete synthesized cue setup is omitted; Home service motion
uses the separately recovered original MP4 assets and their original audio.

Only presentation is added to the native detail screen. Existing native guarded
playback, episode, season, library and return-navigation callbacks are reused.
Native guide, settings, activation and player implementations are retained.

`PetalixServiceWidgets.kt` includes logo/media helpers adapted from ARVIO;
the matching ARVIO license and notice are bundled by the media asset helper.
