# Development diagnostics

Diagnostic collection is intended only for internal development and device testing.

- Diagnostic tools and the DIGI TV investigation probes exist only in Debug builds.
- Collection is opt-in and is not part of normal application operation.
- The normal Release contains no diagnostic upload implementation, diagnostic screen, accessibility observer or internal test-update tools.
- The DIGI TV accessibility observer must be enabled manually by the user in Android Accessibility settings. It filters events to package `ro.digionline.tv`, records a bounded summary of visible UI nodes, and does not capture screenshots, audio or events from other apps.
- Visible text from the DIGI TV interface can appear in the diagnostic log. Email addresses and phone-like strings are redacted, but the report should still be treated as potentially sensitive.
- Reports are uploaded only after the user explicitly selects the report-upload action. The ZIP uses AES-256 encryption and is sent to the configured private diagnostics repository.
- Diagnostic reports must never be published in the public source repository.

Do not commit diagnostic reports, credentials, signing material, internal service locations or personal/network information.
