# Repeating Android checks

Use a dedicated emulator named Suraksha_API26 or Suraksha_API36. Device tests replace guardian SMS with an in-memory sender. They never contact a carrier, visit a fixture URL, or install the dummy APK. Screenshot access exists only in the test application.

## Build and run

Use JDK 17 and the Android SDK configured by Android Studio:

~~~powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease
~~~

Pure logic tests live in :core; :app has no separate JVM-only tests. With ADB on PATH and the isolated emulator on emulator-5582:

~~~powershell
adb -s emulator-5582 install -r -g app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5582 install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5582 shell appops set org.suraksha.kavach SYSTEM_ALERT_WINDOW allow
adb -s emulator-5582 shell input keyevent 82
adb -s emulator-5582 shell am instrument -w -r org.suraksha.kavach.test/org.suraksha.kavach.KavachTestRunner
~~~

The standard run executes 13 tests and defers the incoming-SMS test with an assumption. The runner reports 14 discovered tests, so do not count all 14 as executed from that output alone.

The portable helper combines installation, offline-state verification, the suite, optional large-font checks and report collection:

~~~powershell
python tools/check_device.py emulator-5582 --large-font
~~~

On API 26, enable airplane mode and disable Wi-Fi before invoking it. On current Android versions it configures airplane mode itself. GitHub CI repeats the checks on dedicated Android 14 and Android 16 Linux emulators with KVM and then runs the actual incoming-SMS check below.

## Actual notification-listener path

~~~powershell
python tools/check_native_sms.py emulator-5582
~~~

This helper verifies the emulator identity, temporarily enables notification-listener access after instrumentation resets the process, sends one held-out Hinglish fixture through the emulator console and default SMS app, and restores the original listener setting. It folds only whitespace for the single-line console protocol. The fixture's expected label never controls app detection. Reports go into ignored artifacts/.

On current Android emulators it grants the default SMS app notification permission and enables the emulated radio for incoming SMS, while keeping Wi-Fi/mobile data disabled and verifying no active default IP network. It restores the original airplane-mode state afterward. The standard suite separately checks inference, warnings, history and audio in airplane mode. Failure diagnostics are collected only on the guarded, dedicated emulator.

## Offline and large text

Turn on airplane mode and disable Wi-Fi in the emulator. Verify that dumpsys connectivity reports no active default network before running instrumentation; merely setting the airplane-mode flag is insufficient.

Set system font scale to 2.0 on this isolated emulator, run PhaseFiveDeviceTest#hindiAndPunjabiWarningsKeepBothActionsVisible, and restore the original scale afterward. That test checks the title, both fixed actions and scrolling to the replay button. Screenshots are written to the test app's private cache.

PhaseFiveDeviceTest#androidScoresMatchAllFiftyPythonRowsAndRecordLatency checks all 50 Python rows at a score tolerance of 1e-10. Its android-latency.json separates model loading from warmed inference. Emulator timings do not establish physical-phone performance.

After assembling the release APK, `python tools/check_native_apk.py` verifies the ELF load-segment and uncompressed APK-entry alignment of every bundled native library. Its report is `docs/validation/native-apk.json`. This is a structural 16 KB page-size check, not a substitute for running on a physical 16 KB device.

## Physical phone

Follow PHONE_TESTS.md and DEMO_CHECKLIST.md with a consenting guardian. Carrier delivery, dual-SIM behavior, pronunciation, OEM battery policies and real background/lock-screen permission behavior require that phone. Always select the intended device explicitly for ADB operations.
