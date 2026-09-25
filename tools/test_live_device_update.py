#!/usr/bin/env python3
"""
Test Harness: Live On-Device In-App Update Simulation.

Permits end-to-end testing of Hazel's auto-update mechanism directly on a
physical or emulated Android device without requiring an actual release on GitHub.

How it works:
1. Builds/locates the GitHub Debug APK.
2. Serves a local mock release feed and APK file via HTTP on port 8998.
3. Automatically sets up ADB reverse port forwarding (adb reverse tcp:8998 tcp:8998).
4. The connected device connects to http://127.0.0.1:8998/releases.json, detects
   the newer version (e.g. 1.0.9-test), displays the top bar 'Update' pill and More
   screen notification red dot, downloads the APK, and prompts the installer.
5. Preserves all existing user preferences, downloads history, and databases.
"""

import http.server
import json
import os
import subprocess
import sys
import threading
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
PORT = 8998


def find_apk() -> Path:
    candidates = [
        REPO_ROOT / "app" / "build" / "outputs" / "apk" / "github" / "debug",
        REPO_ROOT / "app" / "build" / "outputs" / "apk" / "debug",
        REPO_ROOT / "app" / "build" / "outputs" / "apk" / "fdroid" / "debug",
    ]
    for d in candidates:
        if not d.exists():
            continue
        for name in [
            "Hazel-v1.0.8-arm64-v8a-debug.apk",
            "Hazel-v1.0.8-universal-debug.apk",
        ]:
            p = d / name
            if p.exists():
                return p
        apks = list(d.glob("*.apk"))
        if apks:
            return apks[0]

    all_apks = list((REPO_ROOT / "app" / "build" / "outputs").glob("**/*.apk"))
    if all_apks:
        return all_apks[0]
    return REPO_ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "Hazel-v1.0.8-arm64-v8a-debug.apk"


class UpdateHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        sys.stdout.write(f"  [Device HTTP Request] {format % args}\n")
        sys.stdout.flush()

    def do_GET(self):
        apk_path = find_apk()
        if self.path == "/releases.json":
            apk_size = apk_path.stat().st_size if apk_path.exists() else 0
            release_payload = [
                {
                    "tag_name": "v1.0.9-preview",
                    "name": "Hazel v1.0.9 Live Device Test",
                    "draft": False,
                    "prerelease": False,
                    "published_at": "2026-09-25T20:00:00Z",
                    "body": "Live test update verifying:\n- Auto-update notification dot\n- Home top bar Update pill\n- In-app background download\n- Android package installer prompting without silent failure\n- Data, settings, and download history retention across upgrades",
                    "assets": [
                        {
                            "name": "Hazel-v1.0.9-preview-arm64-v8a.apk",
                            "browser_download_url": f"http://127.0.0.1:{PORT}/Hazel-v1.0.9-preview.apk",
                            "size": apk_size,
                            "content_type": "application/vnd.android.package-archive"
                        }
                    ]
                }
            ]
            data = json.dumps(release_payload, indent=2).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return

        if self.path == "/Hazel-v1.0.9-preview.apk":
            if not apk_path.exists():
                self.send_error(404, "APK not built yet")
                return
            data = apk_path.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return

        self.send_error(404, "Not Found")


def run_adb(cmd: list[str]) -> str:
    result = subprocess.run(["adb"] + cmd, capture_output=True, text=True, check=False)
    return result.stdout.strip()


def main():
    print("=" * 70)
    print("  Hazel Live On-Device In-App Update Simulator")
    print("=" * 70)

    devices = run_adb(["devices"])
    print(f"Connected devices:\n{devices}\n")
    if "device" not in devices.splitlines()[-1]:
        print("Error: No ADB device connected. Please connect your phone via USB and enable USB debugging.")
        return 1

    apk_path = find_apk()
    if not apk_path.exists():
        print("Building GithubDebug APK for test release...")
        gradlew = REPO_ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
        subprocess.run([str(gradlew), ":app:assembleGithubDebug"], check=True, cwd=str(REPO_ROOT))
        apk_path = find_apk()

    print(f"APK ready: {apk_path} ({apk_path.stat().st_size / (1024*1024):.1f} MB)")

    print(f"Setting up ADB reverse port forwarding: tcp:{PORT} -> tcp:{PORT}...")
    run_adb(["reverse", f"tcp:{PORT}", f"tcp:{PORT}"])

    server = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), UpdateHandler)
    server_thread = threading.Thread(target=server.serve_forever, daemon=True)
    server_thread.start()

    print(f"\nMock release feed server live at http://127.0.0.1:{PORT}/releases.json")
    print("\nNext steps on your connected device:")
    print("  1. Launch Hazel on your device.")
    print("  2. Open the Home screen: observe the [ ⤓ Update ] pill button beside incognito.")
    print("  3. Open 'More' tab: observe the red notification dot badge on the CPU icon.")
    print("  4. Tap 'Software update': observe the red dot on the Hazel component row.")
    print("  5. Tap 'Hazel': observe 'Version 1.0.9 is ready' hero card.")
    print("  6. Tap 'Download update': observe live progress and speed.")
    print("  7. Tap 'Install update': observe the system package installer prompt.")
    print("  8. Confirm the upgrade: verify app data, history, and settings are preserved intact!")
    print("\nPress Ctrl+C when testing is complete to stop the local feed server...")

    try:
        while True:
            threading.Event().wait(1)
    except KeyboardInterrupt:
        print("\nStopping mock server and cleaning up ADB reverse forwarding...")
        server.shutdown()
        run_adb(["reverse", "--remove", f"tcp:{PORT}"])
        print("Done.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
