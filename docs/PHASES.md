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
