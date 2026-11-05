# Petalix test update: locals, settings, and remote help

This recipe retains `com.petalix.iptv.preview` and the existing authorized signing path. It does not roll out to customer devices automatically.

- My Locals appears on Home and the guide blade. Personal station selections use a dedicated profile preference so panel settings locks cannot silently discard them. Saving verifies persistence and opens the selected group.
- The ZIP editor focuses an action instead of automatically opening the keyboard. Lookup hides the keyboard. Nationwide local groups can be hidden after setup while the full accessible catalog remains available for editing My Locals.
- Program drawing is clipped to its cell and row. Guide names, numbers, darkness, and background controls are honored. Home hero and Continue Watching visibility are honored.
- The native settings registry adds 61 controls, including standalone profile, diagnostics, P2P, guide, playback, subtitle, badge, and layout preferences. Set-valued preferences support validated JSON arrays and restoration of their previous local values.
- `management-v4` advertises the extended settings. The panel must filter these keys for older APKs and require an updated receipt before marking them applied.
- `remote-control-v1` advertises app-only remote help. The TV must approve each session. Sessions expire after ten minutes, have an on-TV end button, stop when the activity pauses, and permit only the app navigation and playback key allowlist. There is no screen capture or device-wide Home command.

## Verification completed locally

Patch application against the published v11 source and Python syntax checks passed. Panel TypeScript and production build passed. Panel checks exercise extended-setting validation, draft/send behavior, older-client filtering, customer isolation, session consent, command acknowledgement, session termination, and authentication.

## Required before APK delivery

Android compilation, native unit tests, original-certificate signing, and artifact verification have not run for this update. The customer will perform TV testing; no emulator is required. Remote navigation responsiveness and device-specific behavior remain unverified until the APK is installed.

The panel's full controls index enumerates supported remote preferences. OAuth sign-in, Android permission prompts, and hardware-only actions still require the TV. This update does not claim remote bindings for every action in the upstream Settings menu.
