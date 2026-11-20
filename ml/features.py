"""Portable lexical features. Mirrored in core/UrlFeatures.kt."""
from __future__ import annotations
from collections import Counter
from dataclasses import dataclass
import math
import re
import unicodedata
from urllib.parse import urlsplit
import numpy as np

FEATURE_NAMES = [
    "url_length", "host_length", "path_length", "query_length", "host_dots",
    "host_hyphens", "url_at", "url_percent", "url_digits", "digit_ratio",
    "path_depth", "query_pairs", "is_https", "is_ip", "has_punycode",
    "is_shortener", "risky_tld", "credential_path", "brand_subdomain",
    "lookalike", "subdomain_depth", "host_entropy", "non_ascii", "has_port", "has_user_info",
]
SHORTENERS = {"bit.ly", "t.co", "tinyurl.com", "is.gd", "cutt.ly", "rb.gy", "shorturl.at",
              "lnkd.in", "goo.gl", "ow.ly", "rebrand.ly", "s.id", "tiny.cc"}
RISKY_TLDS = {"xyz", "top", "click", "work", "zip", "mov", "cam", "buzz", "icu", "loan", "rest", "support"}
BRANDS = {"google": "google.com", "youtube": "youtube.com", "paypal": "paypal.com",
          "amazon": "amazon.com", "microsoft": "microsoft.com", "apple": "apple.com",
          "whatsapp": "whatsapp.com", "facebook": "facebook.com", "instagram": "instagram.com",
          "sbi": "sbi.co.in", "hdfc": "hdfcbank.com", "icici": "icicibank.com",
          "paytm": "paytm.com", "phonepe": "phonepe.com", "netflix": "netflix.com"}
CREDENTIALS = re.compile(r"login|signin|sign-in|verify|verification|kyc|password|otp|account[-_/]?update|secure[-_/]?bank|refund|challan", re.I)
SKELETON = str.maketrans({"а": "a", "е": "e", "о": "o", "р": "p", "с": "c", "х": "x",
                        "у": "y", "і": "i", "ј": "j", "ӏ": "l", "0": "o", "1": "l",
                        "3": "e", "5": "s", "7": "t"})
HEURISTIC_WEIGHTS = {"PUNYCODE": .20, "LOOKALIKE": .45, "BRAND_IN_SUBDOMAIN": .45,
    "IP_HOST": .30, "RISKY_TLD": .10, "MANY_HYPHENS": .15, "LONG_URL": .10,
    "SHORTENER": .35, "CREDENTIAL_PATH": .15, "USER_INFO": .50}

def utf16_length(text: str) -> int:
    return len(text.encode("utf-16-le")) // 2

def ascii_host(value: str) -> str | None:
    try:
        host = value.strip().rstrip(".").lower()
        if not host or len(host) > 253 or any(c.isspace() for c in host): return None
        if ":" in host:
            host = host.removeprefix("[").removesuffix("]")
            return host if host.count(":") >= 2 and re.fullmatch(r"[0-9a-f:.]+", host) else None
        host = host.encode("idna").decode("ascii").lower()
        if any(not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", p) for p in host.split(".")):
            return None
        return host
    except (UnicodeError, ValueError): return None

@dataclass(frozen=True)
class ParsedUrl:
    raw: str
    host: str
    path: str
    query: str
    https: bool
    port: int
    user_info: bool
    ip: bool

def parse_url(value: str) -> ParsedUrl | None:
    try:
        text = value.strip()
        if not text or utf16_length(text) > 16_384 or any(ord(c) < 32 for c in text): return None
        raw = text if "://" in text else "https://" + text
        if re.search(r'[\s<>"\\{}|^]', raw) or chr(96) in raw: return None
        if re.search(r"%(?![0-9a-fA-F]{2})", raw): return None
        uri = urlsplit(raw)
        if uri.scheme.lower() not in {"http", "https"} or not uri.netloc: return None
        host = ascii_host(uri.hostname or "")
        if not host: return None
        numeric = bool(re.fullmatch(r"\d{1,3}(?:\.\d{1,3}){3}", host))
        ip = ":" in host or (numeric and all(0 <= int(p) <= 255 for p in host.split(".")))
        if numeric and not ip: return None
        if not ip and "." not in host: return None
        port = uri.port if uri.port is not None else -1
        if port != -1 and not 1 <= port <= 65535: return None
        return ParsedUrl(raw, host, uri.path, uri.query, uri.scheme.lower() == "https",
                         port, "@" in uri.netloc, ip)
    except (ValueError, UnicodeError): return None

class PublicSuffixList:
    def __init__(self, text: str):
        self.exact, self.wildcard, self.exceptions = set(), set(), set()
        for line in text.splitlines():
            rule = line.strip()
            if not rule or rule.startswith("//"): continue
            target = self.exceptions if rule.startswith("!") else self.wildcard if rule.startswith("*.") else self.exact
            normalized = ascii_host(rule.removeprefix("!").removeprefix("*."))
            if normalized: target.add(normalized)

    def registrable(self, value: str) -> str | None:
        host = ascii_host(value)
        if not host or ":" in host or all(x.isdecimal() for x in host.split(".")): return None
        labels, size = host.split("."), 1
        for i in range(len(labels)):
            suffix = ".".join(labels[i:])
            if suffix in self.exceptions:
                size = len(labels) - i - 1
                break
            if suffix in self.exact: size = max(size, len(labels) - i)
            if i > 0 and suffix in self.wildcard: size = max(size, len(labels) - i + 1)
        return ".".join(labels[-size - 1:]) if len(labels) > size else None

def one_edit_away(a: str, b: str) -> bool:
    if abs(len(a) - len(b)) > 1: return False
    i = j = edits = 0
    while i < len(a) and j < len(b):
        if a[i] == b[j]:
            i += 1; j += 1
            continue
        edits += 1
        if edits > 1: return False
        if len(a) >= len(b): i += 1
        if len(b) >= len(a): j += 1
    return edits + len(a) - i + len(b) - j == 1

def flags(url: ParsedUrl, psl: PublicSuffixList) -> set[str]:
    reasons = set()
    registered = psl.registrable(url.host) or ""
    subdomain = url.host[:-len(registered)].rstrip(".") if registered else url.host
    if any(p.startswith("xn--") for p in url.host.split(".")): reasons.add("PUNYCODE")
    if url.ip: reasons.add("IP_HOST")
    if url.host.rsplit(".", 1)[-1] in RISKY_TLDS: reasons.add("RISKY_TLD")
    if url.host.count("-") >= 4: reasons.add("MANY_HYPHENS")
    if utf16_length(url.raw) > 160 or len(url.host) > 70: reasons.add("LONG_URL")
    if url.host in SHORTENERS or registered in SHORTENERS: reasons.add("SHORTENER")
    if CREDENTIALS.search(url.path + "?" + url.query): reasons.add("CREDENTIAL_PATH")
    if url.user_info: reasons.add("USER_INFO")
    if any(registered != official and brand in re.split(r"[.-]", subdomain) for brand, official in BRANDS.items()):
        reasons.add("BRAND_IN_SUBDOMAIN")
    label = registered.split(".")[0]
    try: label = label.encode("ascii").decode("idna").lower()
    except UnicodeError: pass
    skeleton = label.translate(SKELETON)
    if any(len(brand) >= 4 and registered != official and label != brand
           and (skeleton == brand or one_edit_away(skeleton, brand)) for brand, official in BRANDS.items()):
        reasons.add("LOOKALIKE")
    return reasons

def url_features(url: ParsedUrl, psl: PublicSuffixList) -> np.ndarray:
    f = flags(url, psl)
    digits = sum("0" <= c <= "9" for c in url.raw)
    registered = psl.registrable(url.host)
    depth = len(url.host.split(".")) - len(registered.split(".")) if registered else 0
    entropy = sum(-n / len(url.host) * math.log2(n / len(url.host)) for n in Counter(url.host).values())
    return np.asarray([
        utf16_length(url.raw), len(url.host), utf16_length(url.path), utf16_length(url.query),
        url.host.count("."), url.host.count("-"), url.raw.count("@"), url.raw.count("%"),
        digits, digits / utf16_length(url.raw), len([p for p in url.path.split("/") if p]),
        url.query.count("&") + 1 if url.query else 0, int(url.https), int(url.ip),
        int("PUNYCODE" in f), int("SHORTENER" in f), int("RISKY_TLD" in f),
        int("CREDENTIAL_PATH" in f), int("BRAND_IN_SUBDOMAIN" in f), int("LOOKALIKE" in f),
        depth, entropy, int(any(ord(c) > 127 for c in url.raw)), int(url.port >= 0), int(url.user_info)
    ], dtype=np.float32)

URL_LANGUAGE_PATTERN = re.compile(
    r"https?://\S+|www\.\S+|(?<![\w@])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+(?:[a-z]{2,63})(?::[0-9]{1,5})?(?:[/?#]\S*)?",
    re.IGNORECASE,
)

def normalize_plain(text: str) -> str:
    return re.sub(r"\s+", " ", unicodedata.normalize("NFKC", text).lower()).strip()[:4096]

def normalize_text(text: str) -> str:
    return re.sub(r"\s+", " ", URL_LANGUAGE_PATTERN.sub(" ", normalize_plain(text))).strip()

def template_key(text: str) -> str:
    text = normalize_plain(text)
    text = re.sub(r"https?://\S+|www\.\S+|[a-z0-9.-]+\.(?:com|in|org|net|info|xyz)\S*", " <url> ", text)
    text = re.sub(r"\d+", " <n> ", text)
    text = re.sub(r"\b(?:sbi|hdfc|icici|pnb|axis|kotak)\b", " <bank> ", text)
    return re.sub(r"\s+", " ", text).strip()

def heuristic_score(url: ParsedUrl, psl: PublicSuffixList) -> tuple[float, bool, set[str]]:
    reasons = flags(url, psl)
    return min(.99, sum(HEURISTIC_WEIGHTS[x] for x in reasons)), bool(reasons & {"SHORTENER", "PUNYCODE", "IP_HOST"}), reasons
