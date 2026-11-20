# Architecture and privacy boundaries

Both Simulate and NotificationListenerService call the same application scanner,
which calls the pure Kotlin MessagePipeline. Message and URL strings are ephemeral.
The only result crossing into Android alert/history handling contains a domain,
verdict and timestamp. Reasons are part of the verdict.

Android controls background activity launch and full-screen intent delivery.
The app uses a high-importance notification, checks full-screen access on API 34+,
and always supplies a content intent so a user can open the warning.
There is no claim that a link was actually blocked: interception belongs to
the separately gated Tier 3.

Phase 0 demo mode is debug-only, explicitly enabled, initially off, and uniform
across all URLs. Model-based scoring replaces the default pipeline in Phase 2.
The test mode stays labelled and separate from genuine risk claims.

The planned persistent activity schema has exactly verdict, domain, timestamp,
and reason codes. Room with SQLCipher is introduced with history in Phase 2.
Guardian contact, consent and language are separate user settings.
Microphone access and voice analysis are outside Phases 0–5.
