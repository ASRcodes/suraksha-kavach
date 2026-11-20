"""Build an unsigned, no-code APK for metadata inspection only. Never install it."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sdk_value = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
sdk = Path(sdk_value) if sdk_value else Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk"
aapt = sdk / "build-tools/36.1.0" / ("aapt2.exe" if os.name == "nt" else "aapt2")
output = ROOT / "app/src/androidTest/assets/dummy-risky.apk"
output.parent.mkdir(parents=True, exist_ok=True)
subprocess.run([str(aapt), "link", "-I", str(sdk / "platforms/android-36/android.jar"),
                "--manifest", str(ROOT / "tools/fixtures/AndroidManifest.xml"),
                "--min-sdk-version", "26", "--target-sdk-version", "26", "-o", str(output)], check=True)
with zipfile.ZipFile(output) as archive:
    entries = archive.namelist()
    assert set(entries) == {"AndroidManifest.xml", "resources.arsc"}, entries
report = {"sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
          "bytes": output.stat().st_size, "entries": entries,
          "executable_code": False, "signed": False, "installation": "Never installed; archive metadata only",
          "generator": "Android SDK aapt2 36.1.0"}
(ROOT / "docs/validation/dummy-apk.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print("Built manifest-only test APK:", output.relative_to(ROOT))
