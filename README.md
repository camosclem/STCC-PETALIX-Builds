# Petalix TV Guide Beta v0.5 — PIN / master playlist test

Separate Android/Google TV beta with Petalix identity. **Do not replace the existing live Petalix app.**

New: native D-pad-friendly PIN entry; download assigned Live TV, Movies and TV Shows from the separate Petalix Control BETA server in paginated batches; `My Locals` now uses the admin's ZIP-to-market assignments rather than generic zip-prefix guesses for paired customers; native Android guide with a live video preview window and on-focus channel tuning. The existing manual M3U/Xtream import remains available in demo mode.

The activation service is not hosted in this source package. To build a customer-ready APK after deployment, pass `-PpetalixControlUrl=https://your-control-panel.example` when invoking Gradle. With no URL compiled in, the first-run **beta-only** screen asks for the server HTTPS address and offers a demo path. Customer PINs and stream secrets are never included in the repository.

Current beta limitations: automated device refresh and series episode catalog aren't yet fully production hardened; preview window may not support all provider codecs/DRM; TV focus behavior is unverified on hardware; app still uses WebView on Home and Movies/Series, while its PIN screen and Guide are native. External data rights, customer services, backups, stable release signing, permissions and server TLS must be verified before distribution.

Run JavaScript checks: `node --check app/src/main/assets/app.js`, and `node tests/test_m3u.js`.
