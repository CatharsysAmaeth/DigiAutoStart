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

## User-mediated accessibility setup

The Debug menu provides **Ativar observador DIGI TV (Debug)**. Its setup screen:
- explains the observer's limited package scope and the kind of UI information it can read;
- asks for explicit in-app consent before opening Android settings;
- checks the installed and enabled state again when the user returns to DAdA; and
- tries the Android TV service-specific route before falling back to the general Accessibility page only if the service cannot be found or the route cannot be launched.

On Android TV, the setup screen tries the AOSP TvSettings OEM link `android.settings.ACCESSIBILITY_SHORTCUT_SERVICES_TV_OEM_LINK`, passing the installed `AccessibilityServiceInfo` in the `accessibilityServiceInfo` extra. This is an AOSP OEM hook, not a public Android SDK guarantee. A successful `startActivity()` call means only that Android accepted the launch request; it does not prove that the service-specific screen was displayed.

Debug 0.6.6 records a read-only PackageManager preflight before launching the route:
- the activity selected by `resolveActivity()`;
- the matching exported activities visible to PackageManager;
- the accessibility service ID and component; and
- whether the launch request was accepted or threw an exception.

The resolved activity is evidence of PackageManager routing, not proof of the final screen rendered by a vendor Settings application. The app does not attempt to inspect or control another app's private UI. The next report should let us distinguish an unresolved OEM hook, a handler in the ZTE Settings package, or a handler in AOSP TvSettings.

The setup screen never writes Settings.Secure, uses root/ADB, injects input, or bypasses Android's system confirmation. In-app consent is additional disclosure, not a substitute for system-level authorization. This flow and the observer are Debug-only.
