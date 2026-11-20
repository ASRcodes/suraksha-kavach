"""Download dataset archives only. Never resolve or visit a URL found inside a dataset."""
from __future__ import annotations
import argparse
import hashlib
import json
import urllib.request
import zipfile
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCES = [
    ("phiusiil.zip", "https://archive.ics.uci.edu/static/public/967/phiusiil+phishing+url+dataset.zip",
     "CC-BY-4.0", "Prasad and Chandra, PhiUSIIL, UCI 967 (2024)"),
    ("sms_spam.zip", "https://archive.ics.uci.edu/static/public/228/sms+spam+collection.zip",
     "CC-BY-4.0", "Almeida and Hidalgo, SMS Spam Collection, UCI 228 (2011)"),
    ("tranco.zip", "https://tranco-list.eu/top-1m.csv.zip",
     "Provider-specific terms; see https://tranco-list.eu/ attribution", "Tranco, Le Pochat et al."),
    ("indic_messages.csv", "https://huggingface.co/datasets/anmolshrivastav/scam_ham_india_14_languages/resolve/34e2c413aefd2e8f2a6e93177a155f29733290a5/scam_ham_india_14_languages.csv",
     "MIT", "anmolshrivastav; synthetic multilingual corpus"),
    ("scamshield_train.jsonl", "https://huggingface.co/datasets/sidzzz07/scamshield-dataset/resolve/059dee884476a45e380808e68d09e74baff67291/train.jsonl",
     "MIT (publisher declaration); only Synthetic_Tier_C rows are used", "sidzzz07; synthetic Indian scam examples"),
    ("public_suffix_list.dat", "https://publicsuffix.org/list/public_suffix_list.dat",
     "MPL-2.0", "Mozilla / Public Suffix List contributors"),
]
def download(name: str, url: str, directory: Path) -> dict:
    target = directory / name
    resolved = url
    last_modified = None
    if not target.exists():
        req = urllib.request.Request(url, headers={"User-Agent": "SurakshaKavach-Research/0.1"})
        with urllib.request.urlopen(req, timeout=90) as response:
            resolved = response.url
            last_modified = response.headers.get("Last-Modified")
            with target.with_suffix(target.suffix + ".part").open("wb") as output:
                total = 0
                while chunk := response.read(256 * 1024):
                    total += len(chunk)
                    if total > 120 * 1024 * 1024:
                        raise ValueError("Dataset exceeds the 120 MiB safety limit")
                    output.write(chunk)
        target.with_suffix(target.suffix + ".part").replace(target)
    return {"file": name, "url": url, "resolved_url": resolved,
            "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
            "bytes": target.stat().st_size, "last_modified": last_modified}

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", type=Path, default=ROOT / "data")
    args = parser.parse_args()
    args.data_dir.mkdir(parents=True, exist_ok=True)
    manifest = {"fetched_utc": datetime.now(timezone.utc).isoformat(), "sources": []}
    for name, url, license_name, credit in SOURCES:
        print("Fetching dataset:", name, flush=True)
        for attempt in range(3):
            try:
                record = download(name, url, args.data_dir)
                break
            except (TimeoutError, OSError) as error:
                if attempt == 2:
                    raise
                print(f"Retry {attempt + 1} for {name}: {type(error).__name__}", flush=True)
                time.sleep(2)
        record.update(license=license_name, attribution=credit)
        manifest["sources"].append(record)
        if name in {"phiusiil.zip", "sms_spam.zip", "tranco.zip"}:
            with zipfile.ZipFile(args.data_dir / name) as archive:
                for member in archive.infolist():
                    if member.is_dir() or member.file_size > 100 * 1024 * 1024:
                        continue
                    basename = Path(member.filename).name
                    if basename in {"PhiUSIIL_Phishing_URL_Dataset.csv", "SMSSpamCollection", "top-1m.csv"}:
                        # Extract only explicit basenames; no archive path traversal.
                        (args.data_dir / basename).write_bytes(archive.read(member))
    (args.data_dir / "sources_manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print("Dataset archives downloaded. No contained links were opened.", flush=True)

if __name__ == "__main__":
    main()
