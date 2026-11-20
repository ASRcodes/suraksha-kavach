# Suraksha Kavach

Offline, family-first Android scam protection for SatHack. Kotlin + Material 3; min SDK 26.

## Status
Phase 0 is implemented and builds successfully. Six core unit tests and Android lint pass. Its explicit demo mode assigns a test DANGEROUS verdict to
every extracted link. It does not claim to be a trained detector.
Phone checks will follow when a physical device is available.

## Architecture
- `:core`: pure Kotlin/JVM extraction, scoring, and tests; no Android imports.
- `:app`: Compose UI, Android notification access, local alerts and device integrations.
- `/ml`: Python datasets, training/export, held-out reports, parity fixtures.

## Privacy and safe testing
Message bodies and full URLs stay in memory, never in logs or storage.
Only verdict, domain, timestamp and reason codes may enter encrypted history.
Android backup is disabled. No analytics or remote scoring.
Tests use URL strings, harmless EICAR, and a permissions-only dummy APK.

## Libraries, datasets, and APIs used
Direct dependencies are pinned in `gradle/libs.versions.toml`.

| Component | Version | Purpose / license |
|---|---|---|
| Kotlin + Compose compiler | 2.2.20 | Language/compiler; Apache 2.0 |
| Android Gradle Plugin | 8.13.2 | Build; Apache 2.0 |
| Gradle | 8.13 | Build/wrapper; Apache 2.0 |
| AndroidX Core KTX | 1.17.0 | NotificationCompat; Apache 2.0 |
| AndroidX Activity Compose | 1.11.0 | Activity/UI; Apache 2.0 |
| AndroidX Lifecycle | 2.9.4 | Lifecycle; Apache 2.0 |
| Compose BOM | 2025.10.01 | UI, Foundation, Material 3, icons, tooling/tests; Apache 2.0 |
| kotlinx.coroutines | 1.10.2 | Asynchronous work; Apache 2.0 |
| kotlinx.serialization JSON | 1.9.0 | JSON models; Apache 2.0 |
| JUnit | 4.13.2 | Tests; EPL 1.0 |
| AndroidX Test / runner | 1.3.0 / 1.7.0 | Device tests; Apache 2.0 |
| Android framework | API 26–36 | NotificationListenerService, notifications, settings, activities |
| JDK standard library | 17 | URI, MessageDigest SHA-256 |

No datasets or cloud APIs are used in Phase 0. Further dependencies and dataset provenance
are declared as each phase is implemented.

## Phase gates
Each phase receives a build, unit tests and a commit. The user authorized proceeding
through Phases 0–5 before providing phone access. Tier 3 remains unapproved.

## Build and install

Use JDK 17 and Android SDK 36. Open this folder in Android Studio or run:

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Phone steps: [docs/PHONE_TESTS.md](docs/PHONE_TESTS.md).

CI uses `actions/checkout@v4`, `actions/setup-java@v4` (Temurin 17),
`gradle/actions/setup-gradle@v4`, and `actions/upload-artifact@v4`.

## Phase 1 models and evaluation

The portable URL forest and character n-gram text classifier are trained and exported.
Kotlin/Python parity is tested on 50 held-out pairs. Source archives are kept out of Git;
the APK bundles JSON models, domain lists and the full Public Suffix List.

- Full URL alert pipeline: 41,112 held-out rows; precision 99.84%, recall 98.92%, F1 99.38%, FPR 0.0915%.
- Text classifier: 1,284 held-out rows; precision 99.35%, recall 95.05%, F1 97.15%, FPR 0.2081%.
- These are dataset results, not field accuracy. Indic data is synthetic; Punjabi has only 76 test templates.
- The language preprocessor strips URLs. A correction and re-evaluation on unchanged partitions is disclosed.

See [the metrics report](ml/reports/METRICS.md), [source checksums](ml/reports/sources_manifest.json),
[training instructions](ml/README.md), and [split fingerprints](ml/reports/split_manifest.json).

### Additional declared libraries and datasets

| Component | Version / source | Use and license |
|---|---|---|
| Python | 3.11 | Training only; PSF |
| NumPy | 2.2.6 | Feature arrays; BSD-3-Clause |
| SciPy | 1.15.3 | Sparse TF-IDF matrices; BSD-3-Clause |
| scikit-learn | 1.7.2 | ExtraTrees and logistic regression; BSD-3-Clause |
| joblib | 1.6.0 | scikit-learn dependency; BSD-3-Clause |
| threadpoolctl | 3.8.0 | Native thread control; BSD-3-Clause |
| cloudpickle | 3.1.3 | joblib dependency; BSD-3-Clause |
| [PhiUSIIL, UCI 967](https://archive.ics.uci.edu/dataset/967/phiusil-phishing-url-dataset) | Prasad & Chandra, 2024 | URL strings/labels; CC BY 4.0 |
| [SMS Spam Collection, UCI 228](https://archive.ics.uci.edu/dataset/228/sms+spam+collection) | Almeida & Hidalgo, 2011 | English spam/ham proxy; CC BY 4.0 |
| [Tranco](https://tranco-list.eu/) | Snapshot/checksum in reports | Popular-domain proxy and reviewed allowlist; provider terms include noncommercial restrictions |
| [Indian multilingual scam/ham](https://huggingface.co/datasets/anmolshrivastav/scam_ham_india_14_languages) | Pinned revision in ml/README.md | Synthetic messages; publisher-declared MIT |
| [ScamShield corpus](https://huggingface.co/datasets/sidzzz07/scamshield-dataset) | Pinned revision in ml/README.md | Only Synthetic_Tier_C training rows; publisher-declared MIT |
| [Public Suffix List](https://publicsuffix.org/list/) | Snapshot/checksum in reports | ICANN and private-domain boundaries; MPL 2.0 |

Dataset archives were downloaded with Python urllib over HTTPS. No links inside a dataset
were opened. No cloud scoring or inference API is used. OpenPhish and URLhaus parsers exist
for appropriately licensed local files; those feeds are not part of these shipped models.

## Phase 2 implementation

The app now loads the integrity-checked JSON models locally, distinguishes yellow and red warnings,
and keeps a bounded activity log in Room backed by SQLCipher. The database passphrase is random
and wrapped with an Android Keystore AES-GCM key. Failure to open encrypted history never creates
a plaintext fallback. Message text remains transient.

Additional declared dependencies: AndroidX Room runtime/ktx/compiler 2.8.5 (Apache 2.0),
KSP 2.2.20-2.0.4 (Apache 2.0), and net.zetetic:sqlcipher-android 4.10.0
(SQLCipher community BSD-style licensing and bundled native notices). Native APIs added:
Android Keystore, AES-GCM via javax.crypto, SecureRandom, JobScheduler, AtomicFile,
HttpURLConnection, and ActivityManager.clearApplicationUserData.

Enhanced mode is off by default. Its explicit refresh button downloads the public repository
domain list over HTTPS; no URLs, messages, history, files, or query parameters are sent.
The ordinary scanner never performs a network lookup. Auto-deletion runs on scans/startup
and through an Android-scheduled background job; system battery restrictions can delay jobs.

Device and build evidence for this phase is added to docs/PHASES.md when checks finish.
