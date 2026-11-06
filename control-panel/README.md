# Petalix Control BETA — Independent Master Playlist & PIN Manager

A **separate**, functional API and admin web panel for testing a master M3U catalog, customer PIN activation, device slots, and curated ZIP-code locals. It is not yet deployed on the public internet and does **not** operate on the existing Petalix Control customer database or its live users.

## What is implemented

- Create a master playlist, import an authorized M3U from file in bounded batches, and store up to 250,000 streams in SQLite. **All channel stream URLs are encrypted at rest.** Re-imports preserve channel IDs, hidden state and assigned local markets for matching streams.
- Web admin interface to search/edit channels and groups, hide/show a channel, and assign networks (ABC/CBS/NBC/FOX/PBS etc.). Changes increment the master revision.
- Create customers, generate random 8-digit PINs (stored as keyed HMAC hashes, **not plaintext**), set expiry/device limits, activate a device by PIN, revoke/reactivate, and rotate PINs without changing the master.
- Device bearer token after PIN authentication; device gets its assigned master in pages of at most 250, divided into live, movie and show. Authentication tokens are stored hashed at rest.
- ZIP-to-local-TV-market table. Assign exact ZIP codes to a market and station entries to that market. Lookup returns **up to 12 actual stations from the customer’s assigned catalog** — not 100 unrelated channels. Unmapped ZIP returns an informative empty result instead of inventing stations.
- Bulk import ZIP-market mappings as a CSV (`zipcode,market`) supplied by the admin. No proprietary Nielsen DMA table is bundled; source/licensing is your responsibility.
- Dockerfile for deployment behind an HTTPS reverse proxy with persistent SQLite storage.

## Local testing

Requires Python 3.12+, dependencies from `requirements.txt`. Generate separate random secrets:

```bash
export PETALIX_ADMIN_KEY="$(python -c 'import secrets;print(secrets.token_urlsafe(36))')"
export PETALIX_PIN_SECRET="$(python -c 'import secrets;print(secrets.token_urlsafe(48))')"
export PETALIX_FERNET_KEY="$(python -c 'from cryptography.fernet import Fernet;print(Fernet.generate_key().decode())')"
export PETALIX_DB_PATH="./beta-petalix.sqlite3"
python -m uvicorn panel.app:app --host 127.0.0.1 --port 8080
```

Open `http://127.0.0.1:8080/` on your own computer. Enter the `PETALIX_ADMIN_KEY` you just generated. For devices outside localhost, **deploy behind HTTPS**; do not expose the server’s HTTP port directly to the internet. Store the Fernet key and PIN secret safely and persist the SQLite volume; losing either secret makes existing data and activations unrecoverable.

Example ZIP mapping CSV:

```csv
zipcode,market
28601,Charlotte, NC
```

Because commas are part of the market name, **quote the market field** in real CSV:

```csv
zipcode,market
28601,"Charlotte, NC"
28602,"Charlotte, NC"
```

The market-to-channel relationship is configured through **Channels & Groups**. A ZIP by itself does not guarantee a station exists or that its stream is licensed to the provider.

### Device flow

1. Admin creates `Petalix Master`, imports their authorized M3U file, and configures market assignments.
2. Admin creates a customer with a device limit and copies their unique eight-digit PIN.
3. The test app first launches on its native `ActivationActivity`. Customer enters PIN. When deployed with `-PpetalixControlUrl=https://your-panel.example`, the customer does not enter provider URLs, server settings, usernames or passwords.
4. Native Android code exchanges the PIN for a device token, securely requests paginated Live TV/Movies/Series, then displays content in Petalix.
5. Customer enters ZIP under `My Locals`; the app requests that ZIP’s assigned station list from the control server and saves it.

The v0.5 debug APK may expose a **beta-only panel URL field** when the production panel domain is not compiled in; it also offers an explicit demo option until hosting is configured. Neither is appropriate for a customer-facing release.

## API routes

- `GET /api/admin/summary` and `POST /api/admin/masters` (admin bearer token required)
- `POST /api/admin/masters/{id}/import` (authenticated M3U file upload)
- `GET /api/admin/masters/{id}/channels`, `PATCH /api/admin/channels/{id}`
- `POST /api/admin/markets`, `PUT /api/admin/markets/{id}/zipcodes`, `POST /api/admin/markets/import-csv`
- `POST /api/admin/customers`, `PATCH /api/admin/customers/{id}`, `POST /api/admin/customers/{id}/regenerate-pin`, `POST /api/admin/customers/{id}/reset-devices`
- `POST /api/device/activate`, `GET /api/device/catalog?kind=live&offset=0&limit=150`, `GET /api/device/locals?zipcode=28601`

Run tests: `python tests/test_panel.py` (uses fake/nonfunctional streams, isolated SQLite and random keys).

## Production gates (not yet complete)

This beta **must not** be exposed to real customers before HTTPS deployment, secure backups, session/audit controls, external rate limiting, device testing on Shield/ONN/Android or Google TV, streaming-rights verification, privacy review, and provider authentication are addressed. Xtream login remains supported in the **manual TV beta interface**, not yet as a server-side master connection. M3U-upload masters work with a paired PIN. The panel cannot instantly modify a compiled native APK or update production customers without a deployment and permissions.
