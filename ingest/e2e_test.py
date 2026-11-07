#!/usr/bin/env python3
"""End-to-end checks for sonora-ingest, against a running service.

  INGEST_URL=http://127.0.0.1:8088 INGEST_TOKEN=test python3 e2e_test.py

The service must be started with a writable LIBRARY_PATH and INCOMING_PATH. The default
songs folder is music/test_songs/Album gratuit, vol. 8 (relative to this directory); set
INGEST_SONGS to another folder of FLAC files.
"""
import hashlib
import json
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("INGEST_URL", "http://127.0.0.1:8088")
TOKEN = os.environ.get("INGEST_TOKEN", "test")
SONGS = os.environ.get("INGEST_SONGS", "music/test_songs/Album gratuit, vol. 8")


def request(method, path, body=None, headers=None, raw=False, authorized=True):
    data = body if raw else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if authorized:
        req.add_header("Authorization", "Bearer " + TOKEN)
    if not raw and body is not None:
        req.add_header("Content-Type", "application/json")
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def json_request(method, path, body=None, headers=None, authorized=True):
    status, payload = request(method, path, body, headers, authorized=authorized)
    return status, (json.loads(payload) if payload else None)


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def metadata(path, duration=180000):
    name = os.path.basename(path)
    number, rest = name.split(" - ", 1)
    return {
        "title": rest.rsplit(".", 1)[0],
        "artist": "Various Artists",
        "albumArtist": "Various Artists",
        "album": "Album gratuit, vol. 8",
        "track": int(number),
        "durationMs": duration,
    }


def upload(path, meta=None):
    size = os.path.getsize(path)
    digest = sha256(path)
    status, created = json_request("POST", "/uploads", {
        "filename": os.path.basename(path),
        "size": size,
        "sha256": digest,
        "metadata": meta or metadata(path),
    })
    assert status == 201, (status, created)
    upload_id = created["uploadId"]
    with open(path, "rb") as handle:
        data = handle.read()
    status, body = request("PATCH", f"/uploads/{upload_id}", data, {
        "Content-Range": f"bytes 0-{size - 1}/{size}",
    }, raw=True)
    assert status == 200, (status, body)
    status, completed = json_request("POST", f"/uploads/{upload_id}/complete")
    assert status == 200, (status, completed)
    status, current = json_request("GET", f"/uploads/{upload_id}")
    assert status == 200, (status, current)
    return current


def main():
    files = sorted(
        os.path.join(SONGS, name)
        for name in os.listdir(SONGS)
        if name.endswith(".flac")
    )
    assert len(files) >= 2, files
    first, second = files[0], files[1]

    status, health = json_request("GET", "/health")
    print("health", status, health)
    assert status == 200 and health["ok"]

    status, _ = json_request("GET", "/health", authorized=False)
    print("auth without token", status)
    assert status == 401

    status, match = json_request("POST", "/library/match", {
        "sha256": sha256(first), "metadata": metadata(first),
    })
    print("match before upload", status, match)
    assert match["decision"] == "new"

    result = upload(first)
    print("upload", result["status"], result["path"])
    assert result["status"] == "waiting_scan"

    status, match = json_request("POST", "/library/match", {
        "sha256": sha256(first), "metadata": metadata(first),
    })
    print("match after upload", status, match)
    assert match["decision"] == "duplicate" and match["match"] == "hash"

    status, duplicate = json_request("POST", "/uploads", {
        "filename": os.path.basename(first),
        "size": os.path.getsize(first),
        "sha256": sha256(first),
        "metadata": metadata(first),
    })
    print("duplicate create", status, duplicate)
    assert status == 200 and duplicate.get("duplicate") is True

    status, review = json_request("POST", "/uploads", {
        "filename": os.path.basename(second),
        "size": os.path.getsize(second),
        "sha256": sha256(second),
        "metadata": metadata(first),
    })
    print("close metadata", status, review)
    assert review.get("decision") == "needs_review"

    collision = metadata(first)
    collision["durationMs"] = 190000
    result = upload(second, collision)
    print("path collision", result["status"], result["path"])
    assert result["path"].endswith(" (2).flac")

    status, rejected = json_request("POST", "/uploads", {
        "filename": "voice.zip", "size": 10, "sha256": "x",
        "metadata": {"title": "Voice", "artist": "Me", "durationMs": 5000},
    })
    print("zip rejected", status, rejected)
    assert status == 422

    short = "/tmp/sonora-ingest-short.flac"
    with open(short, "wb") as handle:
        handle.write(b"not really audio")
    status, created = json_request("POST", "/uploads", {
        "filename": "short-test.flac", "size": os.path.getsize(short),
        "sha256": sha256(short),
        "metadata": {"title": "Short", "artist": "Me", "durationMs": 5000},
    })
    assert status == 201
    with open(short, "rb") as handle:
        data = handle.read()
    request("PATCH", f"/uploads/{created['uploadId']}", data, {
        "Content-Range": f"bytes 0-{len(data) - 1}/{len(data)}",
    }, raw=True)
    status, body = json_request("POST", f"/uploads/{created['uploadId']}/complete")
    print("short complete", status, body)
    assert status == 422

    print("all e2e checks passed")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print("FAILED:", error, file=sys.stderr)
        sys.exit(1)
