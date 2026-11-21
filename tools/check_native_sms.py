"""Exercise a real SMS notification on an emulator, never a physical device.

Install the debug and Android-test APKs first. The Android instrumentation replaces
SmsManager with a fake sender. One held-out fixture goes through the emulator's
incoming-SMS console, default messaging app, NotificationListenerService and UI.
No fixture URL is opened. Notification-listener access is restored afterward.
With --locked, require the background full-screen notification route with the
overlay disabled. Lock-screen and full-screen permission settings are restored.
"""
from __future__ import annotations

import argparse
from collections.abc import Callable
import json
import os
from pathlib import Path
import queue
import re
import shutil
import subprocess
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "org.suraksha.kavach"
COMPONENT = PACKAGE + "/" + PACKAGE + ".notifications.KavachNotificationListener"


def find_adb() -> str:
    if executable := shutil.which("adb"):
        return executable
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk and os.environ.get("LOCALAPPDATA"):
        sdk = str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk")
    if sdk:
        executable = Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
        if executable.is_file():
            return str(executable)
    raise SystemExit("Set ANDROID_HOME or put adb on PATH.")


def wait_for_no_default_network(adb: Callable[..., str], timeout_seconds: float = 30) -> list[str]:
    """Radio settings return before ConnectivityService finishes disconnecting."""
    deadline = time.monotonic() + timeout_seconds
    while True:
        network = [line.strip() for line in adb("shell", "dumpsys", "connectivity").splitlines()
                   if "Active default network:" in line]
        if network == ["Active default network: none"]:
            return network
        if time.monotonic() >= deadline:
            raise TimeoutError(f"The emulator still has a default IP network: {network}")
        time.sleep(0.25)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial", help="Isolated emulator serial, for example emulator-5582")
    parser.add_argument("--locked", action="store_true", help="Lock the emulator before SMS delivery and require a full-screen warning")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("This check only accepts an emulator, never a physical phone.")
    base = [find_adb(), "-s", args.serial]

    def adb(*arguments: str, timeout: int = 30) -> str:
        result = subprocess.run(base + list(arguments), capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=timeout, check=True)
        return result.stdout.strip()

    def set_listeners(value: str) -> None:
        if value in ("", "null"):
            adb("shell", "settings", "delete", "secure", "enabled_notification_listeners")
        else:
            adb("shell", "settings", "put", "secure", "enabled_notification_listeners", value)

    # Refuse a disconnected or differently routed target before changing settings.
    if adb("get-state") != "device":
        raise SystemExit("The isolated emulator must be booted.")
    avd_name = adb("emu", "avd", "name").splitlines()[0]
    if not avd_name.startswith("Suraksha_API"):
        raise SystemExit("Use a dedicated Suraksha_API emulator for this check.")
    api = int(adb("shell", "getprop", "ro.build.version.sdk"))
    original_airplane = adb("shell", "settings", "get", "global", "airplane_mode_on")
    assert original_airplane in {"0", "1"}
    sms_package = ""
    network: list[str] = []
    airplane_during_test = original_airplane
    original_lock_disabled: str | None = None
    original_full_screen_mode: str | None = None
    report_name = "native-sms-" + args.serial + ("-locked" if args.locked else "")
    if args.locked:
        original_lock_disabled = adb("shell", "locksettings", "get-disabled", "--user", "0")
        if original_lock_disabled not in {"true", "false"}:
            raise RuntimeError("Could not read the dedicated emulator's lock-screen setting.")
        if api >= 34:
            operation = adb("shell", "appops", "get", PACKAGE, "USE_FULL_SCREEN_INTENT")
            mode = re.search(r"USE_FULL_SCREEN_INTENT:\s*(allow|ignore|deny|default|foreground)", operation)
            if mode:
                original_full_screen_mode = mode.group(1)
            elif "No operations" in operation:
                original_full_screen_mode = "default"
            else:
                raise RuntimeError("Could not read the app's full-screen notification permission.")

    samples = json.loads((ROOT / "app/src/main/assets/demo_samples.json").read_text(encoding="utf-8"))["samples"]
    sample = next(row for row in samples
                  if row["language"] == "hi-Latn" and row["kind"] == "danger"
                  and row["message"].isascii())
    # Console commands are single-line. The detector already normalizes whitespace.
    message = " ".join(sample["message"].split())
    if any(ord(character) < 32 for character in message):
        raise SystemExit("The fixture contains an unsupported console control character.")

    original = adb("shell", "settings", "get", "secure", "enabled_notification_listeners")
    aliases = {COMPONENT, PACKAGE + "/.notifications.KavachNotificationListener"}
    original_enabled = any(part in aliases for part in original.split(":"))
    others = [part for part in original.split(":") if part not in {"", "null", *aliases}]
    help_result = subprocess.run(base + ["shell", "cmd", "notification", "help"],
                                 capture_output=True, text=True, timeout=30, check=False)
    native_access_api = "allow_listener COMPONENT" in help_result.stdout + help_result.stderr

    def set_access(enabled: bool) -> None:
        if native_access_api:
            adb("shell", "cmd", "notification", "allow_listener" if enabled else "disallow_listener", COMPONENT)
        else:
            set_listeners(":".join(others + ([COMPONENT] if enabled else [])))
    command = base + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                      PACKAGE + ".NativeNotificationTest", "-e", "native_sms", "true"]
    if args.locked:
        command += ["-e", "native_locked", "true"]
    command.append(PACKAGE + ".test/" + PACKAGE + ".KavachTestRunner")
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, encoding="utf-8", errors="replace")
    lines: queue.Queue[str | None] = queue.Queue()

    def read_output() -> None:
        assert process.stdout is not None
        for line in process.stdout:
            lines.put(line)
        lines.put(None)

    threading.Thread(target=read_output, daemon=True).start()
    transcript: list[str] = []
    sent = rebound = False
    lock_requested = locked_confirmed = full_screen_visible = False
    finished = False
    deadline = time.monotonic() + 240
    artifacts = ROOT / "artifacts"
    artifacts.mkdir(exist_ok=True)
    try:
        # A fresh system image needs a notification-capable default SMS receiver.
        if api >= 31:
            sms_package = adb("shell", "cmd", "role", "get-role-holders", "--user", "0", "android.app.role.SMS").split(";")[0].strip()
            if not re.fullmatch(r"[A-Za-z0-9_.]+", sms_package):
                raise RuntimeError("The dedicated emulator has no default SMS app.")
            if api >= 33:
                grant = subprocess.run(base + ["shell", "pm", "grant", sms_package, "android.permission.POST_NOTIFICATIONS"],
                                       capture_output=True, text=True, timeout=30)
                if grant.returncode != 0 and "has not requested permission" not in grant.stderr:
                    raise RuntimeError("Could not enable notifications for the emulator's SMS app.")
        if api >= 30:
            # Incoming SMS requires the emulated radio. IP networking stays disabled;
            # the standard device suite independently verifies airplane-mode inference.
            adb("shell", "svc", "wifi", "disable")
            adb("shell", "svc", "data", "disable")
            adb("shell", "cmd", "connectivity", "airplane-mode", "disable")
            adb("shell", "svc", "wifi", "disable")
            adb("shell", "svc", "data", "disable")
            adb("emu", "gsm", "voice", "home")
            time.sleep(2)
        airplane_during_test = adb("shell", "settings", "get", "global", "airplane_mode_on")
        network = wait_for_no_default_network(adb)
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=1)
            except queue.Empty:
                continue
            if line is None:
                finished = True
                break
            transcript.append(line)
            print(line.rstrip(), flush=True)
            if "INSTRUMENTATION_STATUS_CODE: 1" in line and not rebound:
                # Instrumentation force-stops the app, including its old service binding.
                # Grant access after that reset, like onboarding with the app running.
                set_access(False)
                time.sleep(1)
                set_access(True)
                rebound = True
            if "READY_TO_LOCK_SCREEN" in line and args.locked and not lock_requested:
                if api >= 34:
                    adb("shell", "appops", "set", PACKAGE, "USE_FULL_SCREEN_INTENT", "allow")
                adb("shell", "locksettings", "set-disabled", "--user", "0", "false")
                adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
                adb("shell", "input", "keyevent", "223")
                lock_requested = True
            if "LOCKED_BACKGROUND_CONFIRMED" in line:
                locked_confirmed = True
            if "LOCKED_FULL_SCREEN_WARNING_VISIBLE" in line:
                full_screen_visible = True
            if "READY_FOR_EMULATED_SMS" in line and not sent:
                if args.locked and not (lock_requested and locked_confirmed):
                    raise RuntimeError("The app did not confirm a locked, background state before SMS injection.")
                bound_until = time.monotonic() + 30
                while True:
                    listing = adb("shell", "dumpsys", "notification")
                    live = re.search(r"Live notification listeners.*?(?=Snoozed notification listeners|\Z)", listing, re.S)
                    if live and any(alias in live.group(0) for alias in aliases):
                        break
                    if time.monotonic() >= bound_until:
                        raise TimeoutError("The listener did not rebind before SMS injection.")
                    time.sleep(0.5)
                response = adb("emu", "sms", "send", "+12025550142", message)
                if "KO:" in response:
                    raise RuntimeError("The emulator rejected the SMS fixture.")
                sent = True
                print("Injected one virtual incoming SMS; no carrier contacted.", flush=True)
        else:
            raise TimeoutError("The native notification check exceeded 240 seconds.")
    finally:
        if finished:
            process.wait(timeout=20)
        elif process.poll() is None:
            process.terminate()
            subprocess.run(base + ["shell", "am", "force-stop", PACKAGE], timeout=20, check=False)
        process.wait(timeout=20)
        if "OK (1 test)" not in "".join(transcript):
            # Dedicated-emulator diagnostics contain only synthetic fixture activity.
            commands = {"logcat": ["logcat", "-d", "-v", "brief"],
                        "notifications": ["shell", "dumpsys", "notification"],
                        "windows": ["shell", "dumpsys", "window"],
                        "activities": ["shell", "dumpsys", "activity", "activities"],
                        "power": ["shell", "dumpsys", "power"],
                        "telephony": ["shell", "dumpsys", "telephony.registry"],
                        "roles": ["shell", "dumpsys", "role"]}
            if sms_package:
                commands["sms-package"] = ["shell", "dumpsys", "package", sms_package]
            for name, command in commands.items():
                try:
                    diagnostic = subprocess.run(base + command, capture_output=True, timeout=30)
                    (artifacts / (report_name + "-" + name + ".txt")).write_bytes(diagnostic.stdout[-1_000_000:])
                except (OSError, subprocess.TimeoutExpired):
                    print("Could not collect emulator diagnostic: " + name, flush=True)
        if native_access_api:
            set_access(original_enabled)
        else:
            set_listeners(original)
        if api >= 30 and original_airplane == "1":
            adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
        if args.locked:
            if original_full_screen_mode is not None:
                adb("shell", "appops", "set", PACKAGE, "USE_FULL_SCREEN_INTENT", original_full_screen_mode)
            assert original_lock_disabled is not None
            adb("shell", "locksettings", "set-disabled", "--user", "0", original_lock_disabled)
            adb("shell", "input", "keyevent", "224")
            adb("shell", "wm", "dismiss-keyguard")
        (artifacts / (report_name + ".txt")).write_text(
            "".join(transcript), encoding="utf-8")

    output = "".join(transcript)
    success = process.returncode == 0 and sent and "OK (1 test)" in output and "FAILURES!!!" not in output
    if args.locked:
        success = success and lock_requested and locked_confirmed and full_screen_visible
    report = {
        "avd": avd_name,
        "physical_device": False,
        "fixture_id": sample["id"],
        "input_transform": "whitespace folded for the single-line emulator console",
        "url_opened": False,
        "carrier_contacted": False,
        "listener_permission_restored": True,
        "notification_shell_api": native_access_api,
        "sms_package": sms_package,
        "airplane_mode_during_sms": airplane_during_test == "1",
        "original_airplane_mode_restored": True,
        "locked_screen_test": args.locked,
        "locked_background_before_sms": locked_confirmed,
        "full_screen_notification_visible": full_screen_visible,
        "lock_and_full_screen_settings_restored": args.locked,
        "network": network,
        "passed": success,
    }
    (artifacts / (report_name + ".json")).write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return 0 if success else 1


if __name__ == "__main__":
    raise SystemExit(main())
