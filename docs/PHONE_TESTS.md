# Phone checks, by phase

Use harmless URL strings. Do not open a suspicious URL. Simulate never sends a message.

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
