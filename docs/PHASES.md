# Phase evidence

## Phase 0
- `:core:test`: 6 tests, zero failures.
- `:app:assembleDebug`: passed.
- `:app:testDebugUnitTest`: no Android-independent tests yet; shared logic is tested in core.
- `:app:lintDebug`: passed after fixing API 26 styling and lock-screen fallbacks.
- Physical phone: pending user device access.
- Emulator UI smoke test: added, not yet executed; initial boot exceeded available memory.

## Phase 1
- `:core:test`: 20 tests, zero failures; all 50 Python/Kotlin rows agree at 1e-10.
- Python dataset/export tests: 7 passed.
- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, and instrumentation APK compilation passed.
- Desktop JVM median 0.96 ms and p95 1.87 ms per held-out URL/message pair; physical-phone measurement pending.
- All held-out demo pairs pass through the actual core pipeline. No demo-specific classification rules.
- The app still uses the explicit Phase 0 demo until Phase 2 wires real models.
- Data provenance, licenses, split fingerprints, limitations, and integration correction are recorded in `/ml`.

## Phase 2
- `:core:test`: 20 tests, zero failures. App JVM test task has no separate tests; Android integration is covered on the emulator.
- Debug APK, instrumentation APK, and Android lint: passed; lint reports no issues.
- Android API 26 emulator: all 4 instrumentation tests passed (15.015 s).
- Airplane mode ON and active default network NONE: all 4 tests passed again (16.121 s).
- Verified: Google/YouTube/Wikipedia produce no notification; a real held-out Hinglish sample opens the red screen; explicit demo mode still works; old metadata is deleted.
- SQLCipher database integrity check passed. The activity table has exactly level/domain/timestamp/reasonCodes; its on-disk header is encrypted and test message text is absent.
- Home screenshot inspected for layout. Red-screen visibility was asserted through Compose UI tests; fuller visual and large-font review continues in Phase 5.
- Phase 0 and Phase 1 GitHub Actions runs passed on Linux.
- Physical-phone checks remain pending USB access. See PHONE_TESTS.md.

## Phase 3
- Core: 24 tests passed. Debug APK, instrumentation APK and lint passed; zero lint issues.
- API 26 emulator: all 7 tests passed (23.207 s), in airplane mode with no active default network.
- Guardian tests verify opt-in, permission, dummy-alert exclusion, encrypted number storage, generic message content, persistent rate limiting, and clock rollback. The transport is a fake; no carrier SMS was sent.
- Android decoded all three bundled warning WAVs. Audible quality and physical carrier delivery remain phone checks.
- Overlay visibility is checked against rendered screen pixels as well as Compose controls; dismissal passed. The red activity, home and setup layouts were inspected.
- Direct battery-exemption requests were replaced with Android battery settings plus status, avoiding the restricted request API.
- Phase 2 GitHub Actions passed: https://github.com/ASRcodes/suraksha-kavach/actions/runs/44816154100

## Phase 4
- Core: 30 tests passed. Debug APK, instrumentation APK and lint passed; no lint issues.
- API 26 emulator: all 10 tests passed (47.488 s), airplane mode on with no default network.
- EICAR is detected from its SHA-256 even with an APK filename; normal text passes; a renamed ZIP/APK is flagged from native requested-permission metadata; malformed APKs receive caution.
- Share-to-scan opens the red file warning; temporary scan archives are deleted and history excludes the private test filename.
- The code-word setup/check UI and salted-hash storage passed. Correct answers continue to recommend hanging up and calling a saved number.
- The dummy fixture contains only AndroidManifest.xml and an empty resources.arsc table, with hasCode=false and no components. It was inspected, never installed or executed.
- The optional folder watcher needs a phone SAF-provider/battery check; it is explicitly bounded and best effort.

## Phase 5
- Core: 30 tests passed. Python: 7 tests passed. Debug, instrumentation and R8-optimized unsigned release APKs build; lint reports no issues.
- Android API 26 offline suite: 13 executed tests passed in 88.485 seconds. The incoming-SMS case is deliberately deferred in that run, then separately executed and passed in 9.832 seconds: 14 unique Android tests passed overall.
- Hindi/Punjabi warnings also passed at 2.0× system font size in 10.682 seconds; titles, fixed bottom actions and scrolling to replay were checked. Actual screenshots are in images/.
- All 50 Android/Python parity rows match at 1e-10. Warm evaluation median 2.2566 ms, p95 5.0186 ms and max 5.8942 ms; model loading took 3782.1988 ms. These are emulator measurements, not phone measurements.
- The optimized APK, locally signed with the Android debug key only for testing, passed a real virtual-SMS notification → listener → red-warning check. A normal Google link remained silent; encrypted history remained usable with no active network. Reopening after force-stop successfully reconnected the listener.
- Every bundled native library passed structural 16 KB ELF-load and APK-entry alignment checks. The unsigned release still requires a proper release signing key for distribution.
- Protection pause is rechecked around asynchronous work; notification text uses a bounded transient queue; cancellation, denied permissions, disabled channels, guardian updates and clear-data cleanup are handled. UI wording explains optional guardian SMS and avoids model jargon.
- The earlier Android 16 Windows run had five UI/screenshot failures. All five passed in the Linux/KVM follow-up: 13 standard checks passed in 50.643 seconds, the 2.0× font check passed in 5.439 seconds and all 50 parity rows matched. The separate virtual incoming-SMS test timed out after listener connection, so complete notification coverage is still pending. See validation/android36-windows-attempt.json and validation/android36-linux-initial.json.
- Physical-phone delivery, notification/permission/battery behavior, SAF access, pronunciation and timing remain pending. Follow PHONE_TESTS.md and DEMO_CHECKLIST.md.

### Malformed-link follow-up
- A regression test reproduced a dangerous scam-language message being downgraded to suspicious when its link could not be parsed. The fallback now keeps the stronger text verdict and also records the invalid-link reason. A malformed link without scam language still receives caution.
- All 31 core tests, debug/release builds and lint pass. Models, feature extraction, training data and thresholds are unchanged. All 14 API 26 integration tests, the large-font check and optimized-release smoke test passed again. The release check verifies installation and a separate force-stop/reopen, including a fresh virtual SMS after reconnection. validation/malformed-link-regression.json records the rebuilt APK hashes and results.
