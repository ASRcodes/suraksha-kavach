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
