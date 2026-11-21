"""Run offline Android checks on a dedicated emulator and collect compact evidence."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess

from check_native_sms import find_adb, wait_for_no_default_network

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "org.suraksha.kavach"
RUNNER = PACKAGE + ".test/" + PACKAGE + ".KavachTestRunner"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--app-apk", default="app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--test-apk", default="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
    parser.add_argument("--large-font", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Only dedicated emulators are supported, never a physical phone.")
    base = [find_adb(), "-s", args.serial]
    artifacts = ROOT / "artifacts"
    artifacts.mkdir(exist_ok=True)

    def adb(*command: str, timeout: int = 60) -> str:
        return subprocess.run(base + list(command), capture_output=True, text=True,
                              encoding="utf-8", errors="replace", timeout=timeout,
                              check=True).stdout.strip()

    avd = adb("emu", "avd", "name").splitlines()[0]
    if not avd.startswith("Suraksha_API"):
        parser.error("Use a dedicated Suraksha_API emulator.")
    api = int(adb("shell", "getprop", "ro.build.version.sdk"))
    assert adb("shell", "getprop", "sys.boot_completed") == "1"
    for apk in (args.app_apk, args.test_apk):
        print(adb("install", "-r", "-g", str((ROOT / apk).resolve()), timeout=120), flush=True)
    adb("shell", "appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "allow")
    if api >= 33:
        adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
    if api >= 30:
        adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
        adb("shell", "svc", "wifi", "disable")
        adb("shell", "svc", "data", "disable")
    assert adb("shell", "settings", "get", "global", "airplane_mode_on") == "1"
    network = wait_for_no_default_network(adb)
    adb("shell", "input", "keyevent", "224")
    adb("shell", "input", "keyevent", "82")

    def run_case(suffix: str, selected: str | None = None) -> dict:
        command = base + ["shell", "am", "instrument", "-w", "-r"]
        if selected:
            command += ["-e", "class", selected]
        command.append(RUNNER)
        try:
            result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8",
                                    errors="replace", timeout=600)
            output = result.stdout + result.stderr
            status = result.returncode
        except subprocess.TimeoutExpired as error:
            output = (error.stdout or b"").decode("utf-8", "replace") if isinstance(error.stdout, bytes) else error.stdout or ""
            status = -1
            adb("shell", "am", "force-stop", PACKAGE)
        (artifacts / f"api{api}-{suffix}.txt").write_text(output, encoding="utf-8")
        print(output, flush=True)
        codes = [int(value) for value in re.findall(r"^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$", output, re.MULTILINE)]
        expected_skips = 0 if selected else 1
        passed = status == 0 and "OK (" in output and "FAILURES!!!" not in output and codes.count(-4) == expected_skips and not any(code < 0 and code != -4 for code in codes)
        timing = re.search(r"Time: ([\d.]+)", output)
        summary = {"passed": passed, "executed": codes.count(0), "assumptions": codes.count(-4),
                   "seconds": float(timing.group(1)) if timing else None}
        (artifacts / f"api{api}-{suffix}.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        return summary

    standard = run_case("suite")
    report = {"api": api, "avd": avd, "physical_device": False, "airplane_mode": True,
              "network": network, "standard": standard}
    if args.large_font and standard["passed"]:
        original = adb("shell", "settings", "get", "system", "font_scale")
        try:
            adb("shell", "settings", "put", "system", "font_scale", "2.0")
            report["large_font"] = run_case("large-font", PACKAGE + ".PhaseFiveDeviceTest#hindiAndPunjabiWarningsKeepBothActionsVisible")
        finally:
            adb("shell", "settings", "put", "system", "font_scale", original if original not in ("", "null") else "1.0")
    for name in ["android-latency.json", "phase2-home.png"] + [f"warning-{language}-font-{scale}.png" for language in ("hi", "pa") for scale in ("1_0", "2_0")]:
        item = subprocess.run(base + ["exec-out", "run-as", PACKAGE, "cat", "cache/" + name], capture_output=True, timeout=30)
        if item.returncode == 0 and (not name.endswith(".png") or item.stdout.startswith(b"\x89PNG")):
            (artifacts / f"api{api}-{name}").write_bytes(item.stdout)
    (artifacts / f"api{api}-device.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return 0 if standard["passed"] and report.get("large_font", {}).get("passed", True) else 1


if __name__ == "__main__":
    raise SystemExit(main())
