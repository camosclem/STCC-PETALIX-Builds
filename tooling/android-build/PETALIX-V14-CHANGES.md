# Petalix: restore the native Nuvio guide

- Restore LiveTvGuideScreen, GuideRowStrip and LiveTvViewModel from the existing verified 8.3 Nuvio baseline. Keep the native guide rendering, preview, focus and key handlers. The only guide addition hands Home category requests to existing native playlist groups.
- Remove the ZIP onboarding prompt, Find Locals dialog, My Locals Home tile and custom guide header.
- Remove custom hiding of nationwide locals by restoring the original channel-list ViewModel. Existing provider local channels remain in their normal playlist groups.
- Stop merging the retired local selection into the native group list. Preserve stored data without altering the provider playlist.
- Keep the Petalix Home/services interfaces, authorized activation/signing, panel settings bridge and app remote support.

Verification: applied against the published v13 source; native ViewModel and row renderer match the verified baseline byte for byte; no remaining Kotlin references to the retired locals dialog or custom header. Android build and signing run through the existing test pipeline. The user performs physical TV testing; no emulator run.
