"""Exercise a real SMS notification on an emulator, never a physical device.

Install the debug and Android-test APKs first. The Android instrumentation replaces
SmsManager with a fake sender. One held-out fixture goes through the emulator's
incoming-SMS console, default messaging app, NotificationListenerService and UI.
No fixture URL is opened. Notification-listener access is restored afterward.
"""
from __future__ import annotations

import argparse
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


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial", help="Isolated emulator serial, for example emulator-5582")
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
                      PACKAGE + ".NativeNotificationTest", "-e", "native_sms", "true",
                      PACKAGE + ".test/" + PACKAGE + ".KavachTestRunner"]
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
        network = [line.strip() for line in adb("shell", "dumpsys", "connectivity").splitlines()
                   if "Active default network:" in line]
        assert network == ["Active default network: none"], network
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
            if "READY_FOR_EMULATED_SMS" in line and not sent:
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
                        "telephony": ["shell", "dumpsys", "telephony.registry"],
                        "roles": ["shell", "dumpsys", "role"]}
            if sms_package:
                commands["sms-package"] = ["shell", "dumpsys", "package", sms_package]
            for name, command in commands.items():
                try:
                    diagnostic = subprocess.run(base + command, capture_output=True, timeout=30)
                    (artifacts / ("native-sms-" + args.serial + "-" + name + ".txt")).write_bytes(diagnostic.stdout[-1_000_000:])
                except (OSError, subprocess.TimeoutExpired):
                    print("Could not collect emulator diagnostic: " + name, flush=True)
        if native_access_api:
            set_access(original_enabled)
        else:
            set_listeners(original)
        if api >= 30 and original_airplane == "1":
            adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
        (artifacts / ("native-sms-" + args.serial + ".txt")).write_text(
            "".join(transcript), encoding="utf-8")

    output = "".join(transcript)
    success = process.returncode == 0 and sent and "OK (1 test)" in output and "FAILURES!!!" not in output
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
        "network": network,
        "passed": success,
    }
    (artifacts / ("native-sms-" + args.serial + ".json")).write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return 0 if success else 1


if __name__ == "__main__":
    raise SystemExit(main())
