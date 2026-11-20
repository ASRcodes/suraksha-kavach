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

## Phase 3 guardian and audio

Guardian SMS is an explicit opt-in. The number is encrypted with Android Keystore, and
only a generic warning is sent, at most once per 30 minutes. Attempts are saved before
sending, so a restart or a clock rollback does not reset the limit. Test-only dummy
verdicts never send SMS. Real held-out samples use the normal pipeline and may request
SMS when the user has opted in. Carrier service and a default SMS SIM are required;
Android reports submission, not proof that the guardian read the message. Detection,
history, and bundled audio work without internet.

Hindi, Punjabi, and English WAV warnings are generated locally with
[eSpeak NG 1.52.0](https://github.com/espeak-ng/espeak-ng/releases/tag/1.52.0),
a GPL-3.0 build tool; its engine is not shipped in the APK. Scripts, source checksum,
and WAV measurements are in tools/ and docs/validation/. Android MediaPlayer plays
these static warnings; Android TextToSpeech is a fallback using an installed offline
voice only. Silent mode, Do Not Disturb and notification volume are respected. Native
speakers should review pronunciation on the test phone. No message content enters TTS.

An optional SYSTEM_ALERT_WINDOW permission enables a dismissible warning over other
apps when the phone is unlocked. It does not inspect other apps or intercept their
taps. Lock-screen warnings use NotificationCompat full-screen intents when Android
permits them. Setup shows notification access, notification permission, full-screen
access, overlay access, battery exemption, and optional SMS permission separately.

Additional declared library: AndroidX SavedState 1.3.3 (Apache 2.0). Native APIs added:
SmsManager, PendingIntent, ACTION_DIAL, AudioManager, MediaPlayer, AudioAttributes,
TextToSpeech, WindowManager TYPE_APPLICATION_OVERLAY, KeyguardManager and PowerManager.
Android test builds replace SMS delivery with an in-memory fake; no automated test
can send carrier SMS. Only this instrumentation subclass permits screenshots.

## Phase 4 files and family code word

Use Home > Check a file, or share a file from Android Files to Kavach. File checks
stream SHA-256 locally and inspect APK metadata with PackageManager.getPackageArchiveInfo.
They never install or execute an APK. Scans are limited to 100 MB. ZIP/APK input is
briefly copied to private cache for metadata inspection and removed afterward; leftovers
are removed before the next inspection. Names, bytes and hashes are not put in history.
A renamed APK is still recognized by its archive/manifest. Permission combinations involving
SMS, overlays, accessibility, app installation and device admin raise warnings; common
APK scam names are an additional warning sign. These are risk heuristics, not proof of malware.

The starter signature list contains **only the harmless 68-byte EICAR test signature**.
It is not a comprehensive malware database. Other files can still be dangerous even when
no known sign is found. ZIP contents and executable code are not analyzed. EICAR has a
distinct test explanation and never sends a guardian SMS. The official source is
https://www.eicar.org/download-anti-malware-testfile/. Automated tests generate its bytes
in memory and do not execute them. No real malware is used.

The optional folder watcher uses a user-granted Storage Access Framework folder and
JobScheduler, roughly every 15 minutes when Android permits. Android 11+ does not allow
granting the Downloads root, so select a small subfolder. It checks new direct children,
up to 20 per run in folders with at most 1,000 entries; there is no recursion. Providers
without modification dates require manual scans. Scheduling, simultaneous changes and
provider timestamps make this a best-effort convenience, not an immediate download blocker.
The folder URI is encrypted; only timing/counter preferences are kept for progress.

The family code uses PBKDF2-HMAC-SHA256 (210,000 iterations, a random 128-bit salt, a
256-bit result), wrapped by Android Keystore encryption. Only the versioned salted hash
is stored. The suspicious-call screen always advises hanging up and calling a saved
number, including when a code matches. No calls are recorded and no voice-clone detector
is claimed; Tier 3 is still excluded.

Additional declared native/JDK APIs: ContentResolver, DocumentsContract and OpenableColumns
(Storage Access Framework), getPackageArchiveInfo, MessageDigest SHA-256, ZipFile,
SecretKeyFactory/PBEKeySpec and MessageDigest.isEqual. AndroidX FileProvider is used only
in the debug test fixture route. No additional third-party runtime libraries or cloud APIs
were added. The no-code unsigned dummy APK is built with Android SDK aapt2 36.1.0 from
tools/fixtures/AndroidManifest.xml; it contains only manifest/resources and is never installed.
Rebuild it with python tools/build_dummy_apk.py.
