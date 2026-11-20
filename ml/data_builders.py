"""Public data parsers. Strings only: no page visits or payload execution."""
from __future__ import annotations
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path
from features import PublicSuffixList, parse_url, normalize_plain, template_key

TRUST_CANDIDATES = {
    "google.com", "google.co.in", "youtube.com", "youtu.be", "wikipedia.org", "microsoft.com",
    "apple.com", "amazon.com", "amazon.in", "facebook.com", "instagram.com", "whatsapp.com",
    "netflix.com", "linkedin.com", "github.com", "stackoverflow.com", "mozilla.org",
    "bbc.com", "bbc.co.uk", "ndtv.com", "indianexpress.com", "thehindu.com", "timesofindia.com",
    "sbi.co.in", "onlinesbi.sbi", "hdfcbank.com", "icicibank.com", "phonepe.com", "paytm.com",
    "uidai.gov.in", "india.gov.in", "mygov.in", "irctc.co.in", "npci.org.in",
    "digilocker.gov.in", "cybercrime.gov.in", "rbi.org.in", "incometax.gov.in",
}
LANGUAGES = {"English": "en", "Hindi": "hi", "Hinglish": "hi-Latn", "Punjabi": "pa"}

def fingerprint(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()

def bucket(group: str) -> str:
    value = int(fingerprint("suraksha-split-v1:" + group)[:8], 16) % 100
    return "test" if value < 15 else "validation" if value < 30 else "train"

def read_openphish(path: Path):
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            yield {"url": line.strip(), "label": 1, "source": "openphish-local"}

def read_urlhaus(path: Path):
    lines = [line for line in path.read_text(encoding="utf-8-sig").splitlines() if not line.startswith("#")]
    for row in csv.reader(lines):
        if len(row) > 2 and row[2].startswith(("http://", "https://")):
            yield {"url": row[2], "label": 1, "source": "urlhaus-local"}
        elif row and row[0].startswith(("http://", "https://")):
            yield {"url": row[0], "label": 1, "source": "urlhaus-local"}

def build_urls(data: Path, psl: PublicSuffixList, include_private: bool = False):
    records = []
    with (data / "PhiUSIIL_Phishing_URL_Dataset.csv").open(encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            records.append({"url": row["URL"].strip(), "label": 1 - int(row["label"]), "source": "uci-phiusiil"})
    top = []
    with (data / "top-1m.csv").open(encoding="utf-8-sig", newline="") as f:
        for row in csv.reader(f):
            if len(row) < 2: continue
            rank, domain = int(row[0]), row[1].strip().lower()
            if rank <= 100_000: top.append(domain)
            if rank <= 40_000: records.append({"url": "https://" + domain, "label": 0, "source": "tranco-benign-proxy"})
            if rank > 100_000: break
    allowlist = {domain for domain in top if domain in TRUST_CANDIDATES and psl.registrable(domain) == domain}
    if include_private:
        for name, reader in [("openphish.txt", read_openphish), ("urlhaus.csv", read_urlhaus)]:
            if (data / name).exists(): records.extend(reader(data / name))
    unique, conflicts, invalid = {}, set(), 0
    for row in records:
        parsed = parse_url(row["url"])
        if parsed is None:
            invalid += 1
            continue
        group = psl.registrable(parsed.host) or parsed.host
        if group in allowlist: continue
        key = parsed.host + "\0" + parsed.path + "\0" + parsed.query
        if key in unique and unique[key]["label"] != row["label"]:
            conflicts.add(key)
            continue
        row.update(id=fingerprint("url:" + key), group=group, split=bucket("url:" + group))
        unique.setdefault(key, row)
    rows = [row for key, row in unique.items() if key not in conflicts]
    return rows, sorted(allowlist), {"invalid_dropped": invalid, "conflicting_urls_dropped": len(conflicts)}

def build_texts(data: Path):
    records = []
    with (data / "indic_messages.csv").open(encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            if row["language"] not in LANGUAGES: continue
            label = row["label"].strip().lower()
            records.append({"text": row["text"], "label": int(label in {"scam", "1"}),
                "language": LANGUAGES[row["language"]], "source": "indic-synthetic", "synthetic": True})
    with (data / "SMSSpamCollection").open(encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\r\n").split("\t", 1)
            if len(parts) == 2:
                records.append({"text": parts[1], "label": int(parts[0] == "spam"),
                    "language": "en", "source": "uci-sms-spam-proxy", "synthetic": False})
    with (data / "scamshield_train.jsonl").open(encoding="utf-8") as f:
        for line in f:
            row = json.loads(line)
            if row.get("source_dataset") != "Synthetic_Tier_C": continue
            language = LANGUAGES.get(row.get("language"))
            if language:
                records.append({"text": row["text"], "label": int(row["is_scam"]),
                    "language": language, "source": "scamshield-synthetic", "synthetic": True})
    unique, conflicts = {}, set()
    for row in records:
        text = normalize_plain(row["text"])
        if not text: continue
        key = template_key(text)
        if key in unique and unique[key]["label"] != row["label"]:
            conflicts.add(key); continue
        row.update(text=text, id=fingerprint("text:" + text), group=key, split=bucket("text:" + key))
        unique.setdefault(key, row)
    return [row for key, row in unique.items() if key not in conflicts], {
        "raw_rows": len(records), "normalized_templates": len(unique),
        "conflicting_templates_dropped": len(conflicts),
        "limitation": "Generated templates and spam proxies are not a representative field scam corpus."
    }

def split_summary(rows):
    return {split: {"rows": sum(r["split"] == split for r in rows),
                    "labels": dict(Counter(str(r["label"]) for r in rows if r["split"] == split))}
            for split in ("train", "validation", "test")}
