# sonora-ingest

A small companion service for Sonora. It lets the phone **upload local music into a
Navidrome or Jellyfin library**, which neither server accepts through the Subsonic API:
Navidrome has no upload by design, and Jellyfin expects files in a scanned folder.

The service receives the file, validates it, deduplicates it, writes it into the music
folder, triggers the library scan and returns the server track id. The app can then mark
the local copy as uploaded and, after verification, delete it.

## Why a service

The Subsonic/OpenSubsonic API has no upload endpoint. Navidrome scans a folder; Jellyfin
does the same. Writing into that folder from the phone needs an HTTP service with write
access to the library — that is this service.

## Quick start (Navidrome)

1. Create the Navidrome admin user first (its web UI), and a music folder shared by both
   containers.
2. Copy `docker-compose.example.yml` to `docker-compose.yml`.
3. Set the secrets:
   ```bash
   export INGEST_TOKEN="$(openssl rand -hex 32)"
   export NAVIDROME_PASSWORD="the admin or app password"
   docker compose up -d
   ```
4. Check it:
   ```bash
   curl -H "Authorization: Bearer $INGEST_TOKEN" http://localhost:8088/health
   ```
5. In Sonora, Settings → Server upload: URL `http://<host>:8088`, token `$INGEST_TOKEN`.

For Jellyfin:

```yaml
environment:
  SERVER_TYPE: jellyfin
  SCAN_URL: http://jellyfin:8096
  SCAN_API_KEY: "<Jellyfin API key>"
  # Verification goes through the Subsonic/Subfin endpoint:
  SUBSONIC_URL: http://jellyfin:8096/subfin
  SCAN_USER: "<Subfin user>"
  SCAN_PASSWORD: "<Subfin password>"
```

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `TOKEN` | required | Bearer token the app sends |
| `PORT` | `8088` | HTTP port |
| `LIBRARY_PATH` | `/music` | Folder the music server scans |
| `INCOMING_PATH` | `$LIBRARY_PATH/.sonora-ingest` | Temporary files and state |
| `STATE_PATH` | `$INCOMING_PATH/state.json` | Dedup state (JSON) |
| `SERVER_TYPE` | `navidrome` | `navidrome` or `jellyfin` |
| `SCAN_URL` | — | Server base URL used to trigger the scan |
| `SUBSONIC_URL` | `SCAN_URL` | Subsonic endpoint used to verify the track |
| `SCAN_USER` / `SCAN_PASSWORD` | — | Subsonic token auth |
| `SCAN_API_KEY` | — | Jellyfin API key |
| `MAX_UPLOAD_BYTES` | 4 GiB | Per-file limit |

The service has **no TLS of its own**: put it behind Caddy/Traefik or run it on a trusted
LAN. Never expose it directly to the internet.

## API

All endpoints require `Authorization: Bearer <TOKEN>`.

### `GET /health`

```json
{"ok":true,"version":1,"libraryPath":"/music","ffprobe":true,"serverType":"navidrome"}
```

### `POST /library/match`

Ask before sending bytes.

```json
{"sha256":"…","metadata":{"title":"Golden Hour","artist":"Mira Sol","album":"Golden Hour","track":1,"durationMs":214000,"isrc":"…","mbid":"…"}}
```

Answers:

```json
{"decision":"new"}
{"decision":"duplicate","match":"hash","serverTrackId":"42","path":"Mira Sol/Golden Hour/01 - Golden Hour.flac"}
{"decision":"needs_review","match":"metadata","reason":"a close metadata match exists; not uploaded automatically"}
```

### `POST /uploads`

```json
{"filename":"01 - Golden Hour.flac","size":38210432,"sha256":"…","metadata":{…}}
```

`201`:

```json
{"uploadId":"…","offset":0,"chunkSize":8388608}
```

A duplicate answers `200` with `{"duplicate":true,…}`; a close metadata match answers
`200` with `{"decision":"needs_review",…}`.

### `PATCH /uploads/{id}`

Send chunks with `Content-Range: bytes start-end/total`. The body length must match the
range. A wrong offset answers `409` with the expected `offset`.

```bash
curl -X PATCH -H "Authorization: Bearer $TOKEN" \
  -H "Content-Range: bytes 0-8388607/38210432" \
  --data-binary @chunk.bin http://localhost:8088/uploads/<id>
```

### `POST /uploads/{id}/complete`

Verifies the SHA-256, validates the audio (ffprobe if installed), writes the file under
`LIBRARY_PATH`, triggers the scan and returns the upload in `waiting_scan`.

### `GET /uploads/{id}`

Returns the upload. When `waiting_scan`, it asks the server whether the track is indexed
and switches to `done` with `serverTrackId` when it is.

### `DELETE /uploads/{id}`

Cancels an upload and removes its temporary file. It never deletes a file already moved
into the library.

### `POST /scan`

Triggers the server scan manually.

## Deduplication

- **SHA-256** exact: duplicate, no bytes sent.
- **ISRC** or **MBID** known: duplicate.
- **Metadata close** (title, artist, duration ±2 s): `needs_review`, never uploaded
  automatically.
- **Path collision**: a suffix (` (2)`) is added; a file is never overwritten.
- The app marks both a fresh upload and a confirmed duplicate as `UPLOADED`; only exact
  duplicates are treated that way.

## Audio admission

- Allowed extensions: `flac`, `mp3`, `m4a`, `aac`, `ogg`, `opus`, `wav`.
- `ffprobe` (installed in the image) checks that there is an audio stream and rejects
  files shorter than 30 s.
- Without `ffprobe`, the duration sent by the app is used.

The service is deliberately conservative: WhatsApp voice notes, recordings and other
short audio are rejected instead of polluting the library.

## Tests

```bash
go test ./...
```

End-to-end, against a running service with a writable library:

```bash
TOKEN=test LIBRARY_PATH=/tmp/sonora-music INCOMING_PATH=/tmp/sonora-in \
  /tmp/sonora-ingest &
INGEST_URL=http://127.0.0.1:8088 INGEST_TOKEN=test python3 e2e_test.py
```

It checks health/auth, a new upload, the hash duplicate, a close-metadata `needs_review`,
a path collision (` (2)`), a rejected ZIP and a rejected short file. The default songs are
`music/test_songs/Album gratuit, vol. 8`; set `INGEST_SONGS` for another folder.

## Limits

- No tag rewriting yet: the file is placed using the metadata the app sends. Upload good
  tags, or wait for the tag-writing step.
- No acoustic fingerprint yet: a re-encoded copy is caught only by ISRC/MBID or a close
  metadata match (`needs_review`).
- State is one JSON file; fine for a personal library, not for millions of tracks.
- Jellyfin verification requires a Subsonic/Subfin endpoint; Jellyfin's own search API is
  not used yet.
