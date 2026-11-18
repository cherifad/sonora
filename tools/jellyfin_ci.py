#!/usr/bin/env python3
"""Prepare a fresh Jellyfin + Subfin server for the contract check.

  tools/jellyfin_ci.py --url http://localhost:8096 --json
  # {"subsonicUsername": "admin", "password": "...", "library": "Music", ...}

Completes the startup wizard, creates the music library and links a Subsonic device,
which is the only way Subfin issues a password (the Jellyfin password is not accepted).
The result is what `tools/server_contract.py` needs; the CI job pipes the two together.

Used by the `contract-jellyfin` job. Run by hand against a container built the same way:

  docker run -d --name jellyfin -p 8096:8096 \\
    -v "$PWD/.ci/jellyfin/config:/config" -v "$PWD/.ci/jellyfin/cache:/cache" \\
    -v "$PWD/.ci/jellyfin/music:/music" jellyfin/jellyfin:10.11.11
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

AUTH_TEMPLATE = 'MediaBrowser Client="sonora-ci", Device="ci", DeviceId="sonora-ci", Version="1.0"'


def request(args, method, path, body=None, token=None, timeout=20):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(args.url.rstrip("/") + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        # The plugin's device manager takes a Jellyfin token in this exact shape.
        req.add_header("Authorization", 'MediaBrowser Token="%s"' % token)
    else:
        req.add_header("Authorization", AUTH_TEMPLATE)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace")


def wait_ready(args):
    """Jellyfin answers 200 on /System/Info/Public long before it can take a POST: the
    startup wizard answers 503 until the server has loaded its plugins and databases."""
    deadline = time.time() + args.wait
    last = ""
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(args.url.rstrip("/") + "/System/Info/Public", timeout=5) as response:
                info = json.loads(response.read().decode("utf-8", "replace"))
            code, _ = request(args, "GET", "/Startup/User")
            # 503 is "still loading"; 200 (wizard pending) and 401 (already completed) are both
            # answers from a server that is ready.
            if code != 503:
                return info
            last = "startup answered %d" % code
        except Exception as error:  # noqa: BLE001 - any failure means "not yet"
            last = str(error)
        time.sleep(2)
    raise SystemExit("Jellyfin did not become ready in %ds (%s)" % (args.wait, last))


def complete_wizard(args, info):
    if info.get("StartupWizardCompleted"):
        return False
    steps = [
        ("/Startup/Configuration", {"UICulture": "en-US", "MetadataCountryCode": "US",
                                    "PreferredMetadataLanguage": "en"}),
        ("/Startup/User", {"Name": args.admin_user, "Password": args.admin_password}),
        ("/Startup/RemoteAccess", {"EnableRemoteAccess": True, "EnableAutomaticPortMapping": False}),
        ("/Startup/Complete", None),
    ]
    for path, body in steps:
        code, text = request(args, "POST", path, body)
        if code >= 300:
            raise SystemExit("%s failed: %d %s" % (path, code, text[:200]))
    return True


def authenticate(args):
    code, text = request(args, "POST", "/Users/AuthenticateByName",
                         {"Username": args.admin_user, "Pw": args.admin_password})
    if code != 200:
        raise SystemExit("cannot sign in as %s: %d %s" % (args.admin_user, code, text[:200]))
    token = json.loads(text).get("AccessToken")
    if not token:
        raise SystemExit("no AccessToken in the answer")
    return token


def ensure_library(args, token):
    query = urllib.parse.urlencode({
        "name": args.library, "collectionType": "music",
        "paths": args.music_path, "refreshLibrary": "false",
    })
    code, text = request(args, "POST", "/Library/VirtualFolders?" + query, None, token)
    # 400 means it already exists, which is what a re-run wants.
    if code >= 300 and code != 400:
        raise SystemExit("cannot create the library: %d %s" % (code, text[:200]))


def link_device(args, token):
    """Subfin issues one app password per device; Jellyfin credentials are not accepted."""
    code, text = request(args, "POST", "/subfin/api/devices/link", {"deviceLabel": args.label}, token)
    if code == 404:
        raise SystemExit("the Subfin plugin is not loaded: is it in /config/plugins?")
    if code >= 300:
        raise SystemExit("cannot link a Subsonic device: %d %s" % (code, text[:200]))
    return json.loads(text)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="http://localhost:8096", help="Jellyfin base URL")
    parser.add_argument("--admin-user", default="admin")
    parser.add_argument("--admin-password", default="contract-only")
    parser.add_argument("--library", default="Music")
    parser.add_argument("--music-path", default="/music")
    parser.add_argument("--label", default="contract-check")
    parser.add_argument("--wait", type=int, default=180, help="seconds to wait for the server")
    parser.add_argument("--json", action="store_true", help="print the credentials as JSON")
    args = parser.parse_args()

    info = wait_ready(args)
    wizard = complete_wizard(args, info)
    token = authenticate(args)
    ensure_library(args, token)
    device = link_device(args, token)

    result = {
        "url": args.url.rstrip("/"),
        "subsonicUsername": device.get("subsonicUsername"),
        "password": device.get("password"),
        "serverVersion": info.get("Version"),
        "wizardRan": wizard,
        "deviceId": device.get("deviceId"),
    }
    if args.json:
        print(json.dumps(result))
    else:
        print("Jellyfin %s, wizard %s, device %s: %s / %s"
              % (result["serverVersion"], "run" if wizard else "already done",
                 result["deviceId"], result["subsonicUsername"], result["password"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
