# Local library upload (companion ingest)

> **Early version.** Uploads ship from Sonora 1.5.0 as a first cut. The flow works end to end
> (upload, dedup, scan, delete-local), but the ingest API, its state file and the app screens
> can still change between releases. Treat it as a personal-server feature, not a hosted
> service, and keep a copy of the ingest state if the library matters.

Navidrome and Jellyfin have no Subsonic upload endpoint: Navidrome does not accept uploads by
design, and Jellyfin expects files to appear in a scanned folder. Sonora therefore ships a
small companion service, [`ingest/`](../ingest/), that writes into the music folder the server
already scans, then asks the server to scan again.

```
Sonora (phone)                ingest service                Navidrome / Jellyfin
     │                              │                              │
     │ POST /auth/exchange          │                              │
     │ {u,t,s} or {apiKey}          │  ping (proves the login)     │
     ├─────────────────────────────▶├─────────────────────────────▶│
     │◀── signed upload token ──────┤◀──────── status ok ──────────┤
     │                              │                              │
     │ POST /uploads + chunks       │  write into the music folder │
     ├─────────────────────────────▶│─────────────────────────────▶│
     │◀── waiting_scan / done ──────┤  startScan, then verify      │
     │                              │                              │
```

The password never reaches the ingest service: the app sends the same `u`/`t`/`s` it would
send to the server (or its OpenSubsonic API key), and the service only forwards it to `ping`.

## Before you start

- A music server Sonora already connects to (Navidrome or Jellyfin + Subfin).
- The music folder the server scans, writable by the ingest container.
- A Subsonic/Subfin account the ingest may use for `startScan` and `search3` (a dedicated
  account is better; it does not need admin beyond scanning).
- Docker (or a Go 1.22 toolchain to run the service directly).

## Quick start with Docker Compose

The example [`ingest/docker-compose.yml`](../ingest/docker-compose.yml) starts Navidrome and
the ingest on a shared `./music` folder:

```bash
cd ingest
export INGEST_TOKEN="$(openssl rand -hex 32)"
export NAVIDROME_PASSWORD="the Navidrome password"
docker compose up -d
```

The scanned library lives in `./music/navidrome`; test fixtures can stay in `./music`. The
ingest writes to `./music/navidrome` and keeps its dedup state in
`./music/navidrome/.sonora-ingest/state.json`.

Check it:

```bash
curl -H "Authorization: Bearer $INGEST_TOKEN" http://localhost:8088/health
```

`ffprobe` is installed in the image, so `/health` reports `"ffprobe": true` and real audio
validation runs. A local `go run .` without ffmpeg falls back to the duration the app sends.

The service is also published as a multi-arch image (`linux/amd64`, `linux/arm64`):

```bash
docker pull ghcr.io/cherifad/sonora-ingest:latest
```

Release tags publish the matching version (`ghcr.io/cherifad/sonora-ingest:1.5.0`) and
`latest`. Compose uses the published image by default; uncomment `build: .` in
[`ingest/docker-compose.yml`](../ingest/docker-compose.yml) to build from source instead.

### Recommended name

Give the service its own name next to the server, not a random host:

```
<server-name>-upload.<domain>
```

Examples:

| Server | Ingest |
|---|---|
| `https://music.example.com` | `https://music-upload.example.com` |
| `https://navidrome.example.com` | `https://navidrome-upload.example.com` |
| `http://192.168.1.20:4533` | `http://192.168.1.20:8088` |

The onboarding and the Settings field prefill this address for you. Put the ingest behind the
same reverse proxy as the server, with TLS:

```caddyfile
music-upload.example.com {
    reverse_proxy 127.0.0.1:8088
}
```

Never expose port `8088` directly to the internet: the service has no TLS of its own.

## Connecting the app

1. **First run**: after the server connects, the onboarding asks for the ingest URL. It is
   prefilled with the recommendation above; press **Connect**, then **Continue** (or
   **Skip for now**).
2. **Later**: **Settings → Server upload**, same field.
3. The app stores the URL and the issued token encrypted. There is no token to copy.

`POST /auth/exchange` returns an HMAC-signed token (default lifetime 30 days). The app
re-exchanges automatically when the ingest answers `401`, using the Subsonic credentials it
already holds. The static `TOKEN` stays available for `curl` and administration.

## Using it

1. Open **Local Files**.
2. Press **Upload**. The first press opens the server-upload settings if no target is set.
3. Each track gets a badge:

| Badge | Meaning |
|---|---|
| `Queued` | waiting for the worker |
| `Uploading` | chunks are being sent |
| `Scanning` | written, the server scan is running |
| `Uploaded` | confirmed on the server |
| `On server` | duplicate, already on the server |
| `Review` | close metadata match, not uploaded |
| `Failed` | network, server or validation error |

Uploads are queued with WorkManager, Wi-Fi only by default, and survive the app being closed.
A duplicate is a success: the local file is marked `Uploaded` / `On server`, exactly like a
fresh upload, and never prompts.

### What is sent

The app skips obvious non-music before it even queues a file: blank title or artist, files
between 1 s and 30 s, and the usual voice-note folders (WhatsApp, Telegram, Recordings, call
recordings, ringtones, alarms, notifications). The ingest enforces the same floor again with
`ffprobe`: a file shorter than 30 s, without an audio stream, or not audio at all (a ZIP
renamed `.mp3`, for example) is rejected.

### Deduplication

The ingest decides, the app only reports:

| Match | Decision | App state |
|---|---|---|
| same SHA-256 | duplicate | `Uploaded` / `On server` |
| same ISRC | duplicate | `Uploaded` / `On server` |
| same MusicBrainz recording id | duplicate | `Uploaded` / `On server` |
| same normalised title + artist, duration ±2 s | `needs_review` | `Review`, **not uploaded** |
| otherwise | new | upload |

A `needs_review` file stays on the phone; resolve it by checking the tags or by removing the
existing server copy, then upload again. There is no per-file choice dialog by design.

### Deleting local copies

Once a track is `Uploaded`, the Local Files header shows a cleanup button. The app verifies
each candidate against the server (`getSong` on the registered track id) and only then opens
the Android system delete dialog. Files the server cannot confirm are never offered, so an
unreachable server or a stale entry cannot delete anything. Android 11+ only; the system
dialog is capped at 2000 files per request.

## Security

- **Token exchange, not a shared password.** The app sends `u`/`t`/`s` (or an API key); the
  service forwards it to `ping`. The password stays on the phone.
- **Signed tokens.** HMAC-SHA256, key derived from `TOKEN`; changing `TOKEN` revokes every
  issued token. Claims: `sub`, `srv`, `scope=upload`, `iat`, `exp`.
- **Rate limit.** `/auth/exchange` accepts 5 attempts per minute per IP.
- **Allowlist.** `ALLOWED_USERS=adlen,partner` restricts who may exchange a token, even when
  the server would accept the login.
- **No SSRF.** The ingest only talks to its configured `SUBSONIC_URL` / `SCAN_URL`; the app
  cannot point it at another host.
- **HTTPS or trusted LAN.** Put it behind Caddy/Traefik, or keep it on the LAN.
- **Least privilege.** Use a dedicated Subsonic account for `startScan`/`search3`, not the
  admin account when the server allows it.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `TOKEN` | required | Static bearer token (curl/admin) and HMAC secret for issued tokens |
| `TOKEN_TTL_HOURS` | `720` | Lifetime of a token issued by `/auth/exchange` (30 days) |
| `ALLOWED_USERS` | all | Comma-separated Subsonic usernames allowed to exchange a token |
| `PORT` | `8088` | HTTP port |
| `LIBRARY_PATH` | `/music` | Folder the music server scans |
| `INCOMING_PATH` | `$LIBRARY_PATH/.sonora-ingest` | Temporary files and state |
| `STATE_PATH` | `$INCOMING_PATH/state.json` | Dedup state (JSON) |
| `SERVER_TYPE` | `navidrome` | `navidrome` or `jellyfin` |
| `SCAN_URL` | — | Server base URL used to trigger the scan |
| `SUBSONIC_URL` | `SCAN_URL` | Subsonic endpoint used to verify tracks and exchange tokens |
| `SCAN_USER` / `SCAN_PASSWORD` | — | Subsonic token auth for scan/verify |
| `SCAN_API_KEY` | — | Jellyfin API key |
| `MAX_UPLOAD_BYTES` | 4 GiB | Per-file limit |

The full HTTP API (`/health`, `/library/match`, `/uploads`, `/scan`) is documented in
[`ingest/README.md`](../ingest/README.md).

## Known limits (early version)

- The app reads each file into memory before chunking it. Fine for albums, not ideal for very
  large files; streaming upload is on the roadmap.
- `Scanning` entries are retried on the next worker run; a slow server scan can leave a file
  in that state for a while.
- One library path and one server per ingest instance.
- No acoustic fingerprint (Chromaprint) yet: two different masters of the same recording are
  only caught when their metadata is close (`Review`) or their tags match (duplicate).
- Uploads are not transcoded: the server receives the original file.
- Deleting local copies needs Android 11+.
- Losing `state.json` does not lose music, but the ingest forgets what it already uploaded;
  the next run can re-upload duplicates (the server still dedups by its own scan).

## Troubleshooting

| Symptom | Check |
|---|---|
| `401` from the ingest | token expired or `TOKEN` changed; the app re-exchanges on its own, or reconnect from Settings |
| `no Subsonic endpoint configured` | set `SUBSONIC_URL` (or `SCAN_URL`) |
| `/health` says `"ffprobe": false` | you run the service outside Docker without ffmpeg; validation falls back to the app duration |
| stuck on `Scanning` | `SCAN_URL`, `SCAN_USER`/`SCAN_PASSWORD`; check the ingest logs |
| `Review` | a close metadata match exists; adjust tags or remove the server copy, then upload again |
| `On server` when you expected an upload | the hash/ISRC/MBID already exists; that is the intended result |
| nothing deleted | the server did not confirm the track, or Android is older than 11 |

Logs: `docker compose logs -f ingest`.

## Roadmap

- Stream uploads instead of buffering whole files.
- Automatic retry of `Scanning` entries with backoff.
- Chromaprint acoustic fingerprint.
- Optional tag rewriting and cover embedding.
- A small admin page for `needs_review`.
