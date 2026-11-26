# Development diagnostics

Diagnostic collection is intended only for internal development and device testing.

- Diagnostic tools and the DIGI TV investigation probes exist only in Debug builds.
- Collection is opt-in and is not part of normal application operation.
- The normal Release contains no diagnostic upload implementation, diagnostic screen, accessibility observer or internal test-update tools.
- The DIGI TV accessibility observer must be enabled manually by the user in Android Accessibility settings. It filters events to package `ro.digionline.tv`, records a bounded summary of visible UI nodes, and does not capture screenshots, audio or events from other apps.
- Visible text from the DIGI TV interface can appear in the diagnostic log. Password-marked nodes are represented by a marker; email addresses and phone-like strings are redacted. The report must still be treated as potentially sensitive.
- Reports are uploaded only after the user explicitly selects the report-upload action. The ZIP uses AES-256 encryption and is sent to the configured private diagnostics repository.
- Diagnostic reports must never be published in the public source repository.

Do not commit diagnostic reports, credentials, signing material, internal service locations or personal/network information.


## Accessibility service registration preflight

Before asking the user to open Android Accessibility settings, run **Diagnosticar Acessibilidade (Debug)** from the DAdA Debug menu.

This read-only check records:
- whether PackageManager can retrieve the declared service component and its metadata;
- whether the service is discoverable through the `ACTION_ACCESSIBILITY_SERVICE` intent;
- whether the service metadata XML can be loaded and exposes the expected package filter and window-content capability;
- whether AccessibilityManager includes the component in its installed and enabled service lists; and
- whether Android's enabled-accessibility-services secure setting contains the component.

The check never enables/disables a service, writes secure settings, injects input, or opens Accessibility settings. Use its result to distinguish APK/PackageManager registration problems from AccessibilityManager or Settings UI discovery problems. Do not ask the user to repeat the settings check until the preflight result has been reviewed.
