# Phone checks, by phase

Use harmless URL strings. Do not open a suspicious URL. Simulate never opens a link. Dummy TEST alerts never send SMS; real samples can send the opted-in guardian warning.

## Phase 0
1. Install the debug APK and open Suraksha Kavach. Demo mode starts off.
2. Enable notification access, warning notifications, and full-screen access where available.
3. Enable demo mode. Open Simulate and enter a message containing `example.org`.
4. Confirm a clearly labelled red TEST ALERT, a plain domain, and a large “I understand” button.
5. Repeat with Hindi and Punjabi text surrounding a URL; verify text stays readable at large system font sizes.
6. Receive an SMS or WhatsApp notification containing a URL. Confirm the warning notification.
7. With the phone locked, check whether Android permits the full-screen alert. While unlocked,
   a heads-up notification may be shown instead; tapping it must open the red screen.
8. Repeat the same notification promptly: it must not create repeated alerts.
9. Turn demo mode off: notification scanning must stop generating dummy warnings.
10. In airplane mode, use Simulate again. The same local extraction and warning must work.

Expected Phase 0 limitation: every URL, including Google or YouTube, triggers a TEST alert
while demo mode is on. No risk classifier is installed in this phase.

## Phase 1 checkpoint
The JVM detector is now trained and verified. The app integration arrives in Phase 2.
For this checkpoint, repeat the Phase 0 notification and Simulate checks in explicit demo mode.
Keep demo mode off for normal use; the Phase 0 app does not provide real protection.

## Phase 2 phone checks
1. Install the new debug APK. Wait for the local scanner to finish loading.
2. Home > Check a message: enter a normal Google or YouTube URL. No warning notification should appear.
3. Settings > Simulate a message: keep Demo mode OFF. Try the Hinglish held-out example. A red warning should show the real domain and plain-language reasons.
4. Repeat the held-out example with airplane mode enabled. Never open any test URL.
5. Open Activity: entries contain only result, domain and time. No message text, sender, URL path or query should appear.
6. Change the language to Hindi and Punjabi. Repeat a warning and check that navigation and reasons change language.
7. Enhanced mode starts OFF. Turning it ON only enables an explicit list-download button; ordinary scanning remains offline.
8. Choose a retention period. Clear all data after reviewing the confirmation: the app closes and starts fresh on the next launch.

Guardian SMS and spoken warnings are added in Phase 3. Full-screen background behavior depends on Android permission and lock state; tap the urgent notification if Android shows it instead of an activity.

## Phase 3 phone checks
1. Complete the four-step setup. Deny each optional permission once and confirm the status stays truthful. Grant notification access and warnings for the notification demo.
2. Select Hindi, then Punjabi. Tap Hear a sample; review volume, pronunciation and the wording with a fluent speaker. Repeat in airplane mode. Silent/DND should suppress automatic audio.
3. Save a consenting family contact. SMS starts off until explicitly enabled; accept SMS permission only if wanted. Test the Call my guardian button: it opens the dialer, without placing a call automatically.
4. With normal cellular service and a default SMS SIM selected, run a real held-out dangerous sample. Confirm red warning, local voice and one generic guardian SMS without message text/domain. Check the SMS status.
5. Repeat within 30 minutes, then restart the app and repeat. No further SMS should be requested. A TEST alert in explicit demo mode must never send SMS.
6. Check red warnings while unlocked, locked, and while another app is visible. If Android restricts full-screen notifications, enable the optional overlay while unlocked or tap the urgent notification. Dismiss and replay must work.
7. Revoke SMS or overlay permission in Android settings. Return to setup and verify status updates and local scanning still works.

Airplane mode validates local detection/audio/history, not carrier delivery. SMS can fail or remain queued without cellular service. Emulator tests use a fake SMS transport; physical carrier delivery remains a phone check.

## Phase 4 phone checks
1. Home > Check a file: choose a normal local text or image file. Expect No known warning signs, with the limited-check explanation.
2. Check the standard harmless EICAR test file: expect a clearly labelled EICAR test warning, no guardian SMS, and no file execution. Do not use real malware.
3. Copy the manifest-only dummy-risky.apk from app/src/androidTest/assets to the phone. Do not install it. Share it from Files to Kavach: expect a red file warning about permissions. Rename it to ordinary.zip and repeat; the permissions should still trigger.
4. Check an unreadable/truncated APK: expect caution, never a green result. Activity must show File check and time without a filename, file contents or hash.
5. Optionally choose a small Downloads subfolder to watch, then add a new test file and tap Check new files now. Revoke folder access and confirm the app asks you to choose a folder again. Background checks may be delayed by Android.
6. Settings > Set a family code word: save matching entries. Home > suspicious call: verify the large code-word and hang-up/callback instructions, then check correct and incorrect answers. A correct answer must still recommend calling the saved number.
7. Restart the app and confirm the code still works. Clear all data and confirm the guardian, code, watched-folder access and history are reset.
