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
