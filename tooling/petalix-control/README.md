# Petalix Control — direct channel editing integration (draft)

**Status: code prepared; NOT connected to the live site.** This repo contains Android build tooling; the hosted Petalix Control backend and its deployment are not here. This branch is isolated from production main and must not be merged to attempt to grant access. GitHub administration is NOT Petalix Control backend authorization.

## Goal
Enable approved Petalix admins to request channel-group changes **in ChatGPT directly**, without recurring Work sessions. Keep the existing Petalix Control domain, original activation and app, and all customers unchanged.

## Provided
`lineup-curation.mjs`: metadata-only preview; keeps Sports visible, adds **Western**, **FanDuel**, **TSN** where matching existing channels are found, alphabetizes ordinary groups/channels and orders TSN numerically. Does not create separate Sports Events, League Pass or Team groups. Outputs existing IDs and group assignments only, preserves underlying source channels. Test via:

```bash
node --test tooling/petalix-control/lineup-curation.test.mjs
```

## One-time integration into the **existing** hosted Petalix Control backend

1. Open the **original** site source/deployment in ChatGPT Work or the site's owner editor. Determine how the saved `lineup` settings (`genreOrder`, `genreChannelOrder`, `hiddenGenres`) are stored and how device playlists are generated.
2. Add owner/admin authenticated operations and expose them to the connected Petalix plugin: 
   - `read_live_lineup(brand)`: revision, counts, sanitized channel metadata (NO provider URLs, tokens, PINs, customer data).
   - `preview_live_group_edits(brand, expectedRevision, rules)`: no-write changes, unmatched channels, Sports Hub compatibility warnings.
   - `publish_live_group_edits(brand, expectedRevision, previewId)`: requires explicit owner authorization and exact revision/preview match; versioned transaction, audit receipt, rollback snapshot, no automated customer rollout.
   - `rollback_live_group_edits(brand, expectedRevision, auditReceiptId)`: owner-confirmed safe restoration.
3. Use the site's **existing session authorization**, short-lived scoped credentials or secured first-party connector authentication. Do not add unauthenticated public write routes, store backend keys in GitHub, or weaken the `workflowCommit` signed-APK policy.
4. Apply grouping as a **view** over original source channels. Keep IDs, playback URLs, EPG data, favorites, Sports Hub discovery and authorized device registrations intact. Ensure the auto-refresh job reapplies saved curation without losing it.
5. Dry-run and inspect matches with actual Petalix channel metadata, capture the old revision, then test a physical TV: Sports Hub games using FanDuel, TSN and normal Sports feeds; favorites, guide, logos, playback; validate rollback.
6. Connect the new four actions to this ChatGPT conversation's Petalix connector. The existing connector currently supports playlist status and refresh but not live group edits. Adding code to this GitHub repo alone **does not** add tool permissions or deploy the hosted site.

**Acceptance:** ChatGPT reads and previews Petalix group changes; a real authorized publish produces a new lineup revision and shows on a test device. Future routine changes use these connected actions without Work credits. STCC and production signing remain unchanged.

**No live edits or connector permission changes have been made by this draft.**
