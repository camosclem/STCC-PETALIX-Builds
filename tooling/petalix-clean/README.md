# Petalix clean rebuild staging — 1.1.0-beta.11.1

**Staging only. No customer deployment or edits to the STCC service.**

## Goal

Create a clean Petalix-branded test build from the official `homelessbrian/NuvioPlusIPTV`
commit `a7e67cec36e1e28d79b889b5630c4b2095931888` (tag `1.1.0-beta.11.1`).
Use the existing metallic Petalix logo. Do not replace the native Live TV guide or native
Sports hub. Preserve GPLv3 license and author credits and the original internal source
namespaces.

The stock build only changes the app display name, selected user-facing text,
launcher graphics, Android application ID, and monotonically increasing test version.
Disable the *upstream* automatic APK updater so the branded package is not silently
replaced by an APK with another package/signature. Petalix updates must be released
using the verified Petalix signing certificate and panel-managed delivery.

## Curated TV guide

Organize locally supplied, properly authorized playlists using `organize_playlist.py`.
Never commit actual M3U outputs, provider logins or private playlist URLs to GitHub.
Only known foreign-labeled source groups are excluded; unknown-region channels are
retained for review so that no legitimate US stations are accidentally lost.

* Locals, Entertainment, News first
* Sports: Major Networks, FanDuel regional sports, TSN+, NFL, NBA, MLB, NHL,
  College, Soccer, Golf & Tennis, Racing, Combat, Other
* Movies, Kids & Family, Lifestyle, Documentaries, Western, Music
* Condensed 24/7 TV & Series, Movies, Kids, Music, Other

League-specific *team pages*, search and game cards already exist in upstream Sports.
The guide organizes existing feed channels by sport rather than inventing
individual streams for every team. Only show categories containing real matching feeds.
A requested TSN+ channel is a specific Canada exception. The script does not
synthesize TSN+ or FanDuel streams if the source does not contain them.

```sh
python3 tooling/petalix-clean/organize_playlist.py \
    /secure/private-provider.m3u /secure/petalix-usa-clean.m3u
# Optional: include alternate-quality duplicate channels in a separate Backup Streams group
python3 tooling/petalix-clean/organize_playlist.py \
    /secure/private-provider.m3u /secure/petalix-usa-with-backups.m3u --include-backups
```

Output files include the original stream links and must be kept private. Guide IDs and
logos are retained. Quality is estimated from names only, so test picture/audio
before finalizing a preferred feed.

## Release requirements (not yet verified)

1. Compile the isolated upstream-based source, inspect the test-only debug APK and
   test D-pad navigation, Live TV guide, sports events, player and backup feed handling.
2. Restore compatible **Petalix panel activation, PIN persistence, and managed
   playlist delivery**, or else the stock APK would not recognize existing customer
   activation. Use existing customers' same package ID and original certificate.
3. Ensure every existing customer APK's version code is lower and that same-signature
   Android in-place updating, data persistence, EPG and favorites actually pass testing.
4. Confirm provider authorization and playback for USA sports networks, TSN+ and
   FanDuel, and filter channels without publishing customer credentials.
5. Sign through the original protected release process, then issue a limited
   internal update before customer rollout from the panel.

This repository's **staging** workflow uses a *debug* certificate and is NOT an
in-place upgrade for existing customers. Do not upload its APK as a public customer
release. Production stays unchanged until all release gates have passed.
