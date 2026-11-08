#!/usr/bin/env python3
"""Check a Subsonic/OpenSubsonic server against the endpoints Sonora uses.

  tools/server_contract.py --url http://10.0.2.2:4533 --user admin --password secret
  tools/server_contract.py --url https://music.example --user me --api-key ...
  tools/server_contract.py ... --insecure --json

Required endpoints must answer `status: ok`; optional ones (scan, bookmarks, shares,
now playing, OpenSubsonic extensions) are reported as warnings when the server does not
have them. The password and the token are never printed.
"""
import argparse
import hashlib
import json
import random
import ssl
import string
import sys
import urllib.error
import urllib.parse
import urllib.request

API_VERSION = "1.16.1"
CLIENT = "Sonora"

REQUIRED = [
    ("ping", {}),
    ("getAlbumList2", {"type": "alphabeticalByName", "size": "1"}),
    ("getArtists", {}),
    ("search3", {"query": "a", "songCount": "1", "albumCount": "1", "artistCount": "1"}),
    ("getPlaylists", {}),
    ("getStarred2", {}),
    ("getGenres", {}),
]

OPTIONAL = [
    ("getOpenSubsonicExtensions", {}),
    ("getScanStatus", {}),
    ("getBookmarks", {}),
    ("getShares", {}),
    ("getNowPlaying", {}),
]


def auth_params(args):
    if args.api_key:
        return {"apiKey": args.api_key}
    salt = "".join(random.choices(string.ascii_letters + string.digits, k=12))
    token = hashlib.md5((args.password + salt).encode("utf-8")).hexdigest()
    return {"u": args.user, "t": token, "s": salt}


def url_for(args, method, extra):
    params = {
        "v": API_VERSION,
        "c": CLIENT,
        "f": "json",
        **auth_params(args),
        **extra,
    }
    return args.url.rstrip("/") + "/rest/" + method + ".view?" + urllib.parse.urlencode(params)


def call(args, method, extra):
    request = urllib.request.Request(url_for(args, method, extra))
    context = ssl._create_unverified_context() if args.insecure else None
    with urllib.request.urlopen(request, timeout=args.timeout, context=context) as response:
        text = response.read().decode("utf-8", "replace")
    root = json.loads(text).get("subsonic-response")
    if root is None:
        raise ValueError("not a Subsonic envelope")
    if root.get("status") != "ok":
        error = root.get("error") or {}
        raise ValueError("status failed: %s %s" % (error.get("code"), error.get("message")))
    return root


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", required=True, help="server base URL, e.g. http://10.0.2.2:4533")
    parser.add_argument("--user", help="Subsonic username (token auth)")
    parser.add_argument("--password", help="Subsonic password or app password (token auth)")
    parser.add_argument("--api-key", help="OpenSubsonic API key, instead of user/password")
    parser.add_argument("--timeout", type=float, default=10.0)
    parser.add_argument("--insecure", action="store_true", help="skip TLS verification (self-signed test server)")
    parser.add_argument("--json", action="store_true", help="print a JSON report")
    args = parser.parse_args()

    if not args.api_key and not (args.user and args.password):
        parser.error("give --user and --password, or --api-key")

    report = {"server": {}, "required": {}, "optional": {}, "extensions": []}
    failures = 0

    try:
        ping = call(args, "ping", {})
    except Exception as error:
        print("cannot reach %s: %s" % (args.url, error), file=sys.stderr)
        return 2

    report["server"] = {
        "type": ping.get("type"),
        "serverVersion": ping.get("serverVersion"),
        "apiVersion": ping.get("version"),
        "openSubsonic": bool(ping.get("openSubsonic")),
    }

    if ping.get("openSubsonic"):
        try:
            extensions = call(args, "getOpenSubsonicExtensions", {}).get("openSubsonicExtensions") or []
            report["extensions"] = sorted(e.get("name") for e in extensions if e.get("name"))
        except Exception:
            report["extensions"] = []

    for method, extra in REQUIRED:
        try:
            call(args, method, extra)
            report["required"][method] = "ok"
        except Exception as error:
            report["required"][method] = "fail: %s" % error
            failures += 1

    for method, extra in OPTIONAL:
        if method == "getOpenSubsonicExtensions" and not ping.get("openSubsonic"):
            report["optional"][method] = "skipped (not OpenSubsonic)"
            continue
        try:
            call(args, method, extra)
            report["optional"][method] = "ok"
        except Exception as error:
            report["optional"][method] = "unsupported: %s" % error

    if args.json:
        print(json.dumps(report, indent=2))
    else:
        server = report["server"]
        label = server["type"] or "Subsonic"
        if server["serverVersion"]:
            label += " " + server["serverVersion"]
        if server["openSubsonic"]:
            label += " (OpenSubsonic)"
        print("Server: %s, API %s" % (label, server["apiVersion"] or "?"))
        if report["extensions"]:
            print("Extensions: " + ", ".join(report["extensions"]))
        for method, state in report["required"].items():
            print(("OK   " if state == "ok" else "FAIL ") + method + ("" if state == "ok" else ": " + state))
        for method, state in report["optional"].items():
            print(("OK   " if state == "ok" else "WARN ") + method + ("" if state == "ok" else ": " + state))

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
