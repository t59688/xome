#!/usr/bin/env python3
"""Capture the current Xome screen through adb, without navigating or unlocking.

Usage: py scripts/capture_screenshots.py --serial emulator-5554 --name settings
Capture is refused if the foreground app is not Xome. Android FLAG_SECURE remains effective.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def adb(serial: str | None, *args: str) -> subprocess.CompletedProcess[bytes]:
    command = [shutil.which("adb") or "adb"]
    if serial:
        command += ["-s", serial]
    return subprocess.run(command + list(args), capture_output=True, check=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="Safe, foreground-only Xome UI capture")
    parser.add_argument("--serial", help="adb device serial; required if multiple devices")
    parser.add_argument("--name", default="current", help="screenshot label")
    parser.add_argument("--output", type=Path, default=ROOT / "screenshots")
    args = parser.parse_args()

    window = adb(args.serial, "shell", "dumpsys", "window").stdout.decode("utf-8", errors="replace")
    foreground = [line for line in window.splitlines() if "mCurrentFocus" in line or "mFocusedWindow" in line]
    if not foreground or not any("com.aizeek.xome/" in line for line in foreground):
        raise SystemExit("Xome is not foreground; screenshot cancelled")

    shot = adb(args.serial, "exec-out", "screencap", "-p").stdout
    if not shot.startswith(b"\x89PNG\r\n\x1a\n"):
        raise SystemExit("Invalid PNG from adb; capture cancelled")
    args.output.mkdir(parents=True, exist_ok=True)
    safe_name = "".join(c for c in args.name if c.isascii() and (c.isalnum() or c in "-_")) or "current"
    dest = args.output / f"xome-{safe_name}-{datetime.now().strftime('%Y%m%d-%H%M%S')}.png"
    dest.write_bytes(shot)
    print(dest)
    print("Note: secure screens may be blacked out by Android FLAG_SECURE")


if __name__ == "__main__":
    main()
