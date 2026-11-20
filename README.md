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
