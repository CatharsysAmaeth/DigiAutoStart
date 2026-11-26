# DAdA 0.6.5 acceptance and investigation test plan

## T1 — Installation and upgrade

- Install the Android 12 Debug update over the existing DAdA Debug app.
- Confirm the package identity remains `pt.catharsys.digiautostart`.
- Confirm the Debug version updates without uninstalling the existing app.
- Confirm saved preferences remain available after the update.
- Confirm the original launcher and DIGI TV remain functional.
- Confirm Widevine L1 and platform certification remain intact.

## T2 — Configuration and permissions

- Open the “Configurar” screen using TV remote navigation.
- Confirm Mode A and Mode B can be selected and saved.
- Confirm the wake delay can be set from 0 to 30 seconds.
- Confirm launch-at-startup can be enabled independently.
- Confirm the app explains and requests only the required special permissions.
- Confirm Mode B requires Usage Access and both automation modes require Display over other apps access.

## T3 — Standby and wake

- Test Mode A across repeated standby/wake cycles.
- Test Mode B with DIGI TV as the last foreground app.
- Test Mode B with another app or the launcher as the last foreground app.
- Confirm the configured delay is respected.
- Confirm the disabled state does not reopen the TV app.

## T4 — Startup and recovery

- Test startup launch when enabled.
- Confirm startup launch remains disabled by default.
- Confirm enabled standby monitoring is restored after a device restart.
- Confirm enabled monitoring is restored after an application update.
- Confirm startup launch remains independent of Mode A/B.

## T5 — Target resolution

- Confirm the official DIGI TV app is selected by default when available.
- Test conservative discovery of compatible Android TV alternatives.
- Confirm ambiguous candidates require manual selection.
- Confirm the selected target is used consistently for standby and startup.

## T6 — Package and deep-link discovery

- Start opt-in Diagnostics collection.
- Open “Investigar DIGI TV (Debug)”.
- Run “Inspecionar aplicação e deep links” and record the installed DIGI TV version, exported activities and whether `digitv://ia-livestream/1` resolves to an activity.
- Do not interpret a route string found in the JavaScript bundle as proof that the URI is accepted.
- If an activity resolves, run the candidate deep-link test once and record the resulting screen.
- Compare with “Abrir DIGI TV normalmente”.
- Record whether either route reaches the initial screen, channel 1, an error, or another screen.

## T7 — Accessibility service registration preflight

- Install Debug 0.6.5 over the existing Debug app; do not uninstall.
- Open the Debug menu and run "Diagnosticar Acessibilidade (Debug)".
- Record PackageManager component lookup, accessibility-service intent discovery, metadata XML parsing, AccessibilityManager installed/enabled lists, and the secure enabled-services setting.
- Review the result before opening Android Accessibility settings or asking the user to enable anything.
- This diagnostic is read-only and must not modify secure settings or enable the service.

## T8 — User-mediated accessibility setup

- Open **Ativar observador DIGI TV (Debug)** from the Debug menu.
- Read the disclosure and explicitly consent before opening Android settings.
- Confirm DAdA first opens the Android TV service-specific confirmation screen titled for “Observador de interface DIGI TV (Debug)”, if the ROM exposes the AOSP TvSettings OEM link.
- If that specific route is unavailable, confirm DAdA falls back to the general Accessibility page and reports the fallback honestly.
- If the service-specific page opens, inspect the service name and enable it only after reading the system confirmation.
- Return to DAdA, press “Actualizar estado”, and confirm the active state is reported only if Android lists the service as enabled.
- Upload the opt-in diagnostic report so the recorded route and post-return state can be checked.
- Do not enable TalkBack or Switch Access as a workaround.

## T9 — Accessibility observation

- Enable Diagnostics collection in DAdA.
- From the investigation screen, open Android Accessibility settings.
- Manually enable “Observador de interface DIGI TV (Debug)”.
- Open DIGI TV normally and wait for the initial screen to settle.
- Record the initial highlighted “Ver mais” state.
- Observe whether window-state, content-change, focus and selection events are emitted.
- Confirm the view-tree summary identifies visible labels, focusable/clickable nodes and bounds.
- Navigate manually with DOWN, DOWN, OK and record the corresponding focus/selection changes and resulting channel screen.
- Disable the accessibility observer after the test if no longer needed.

## T10 — Variable startup timing

- With Diagnostics and the accessibility observer enabled, open DIGI TV normally five times.
- For each run, record the first DIGI TV window event, the first non-empty view-tree snapshot, the initial “Ver mais” highlight and the first observed live-channel/player state.
- Repeat after returning to DAdA between runs.
- Do not use a fixed delay as a proxy for readiness; derive timing from observed UI events.
- Record any run where the accessibility tree is unavailable or incomplete.

## T11 — Release isolation and privacy

- Confirm the normal Release has no Debug menu, diagnostic collection, DIGI TV accessibility observer or internal test-update tools.
- Confirm Release does not request Debug-only internet, package-install or accessibility-service declarations.
- Enable the accessibility observer only for the test and only after reading its disclosure.
- The observer is restricted to package `ro.digionline.tv`; it records bounded visible UI text and node metadata, not screenshots or audio.
- Password-marked accessibility nodes are recorded as markers rather than text. Email and phone-like strings are redacted, but all reports must still be treated as potentially sensitive.
- Upload the encrypted report only after explicitly choosing “Criar e enviar relatório”; reports belong only in the private diagnostics repository.
- Delete local logs after successful review if no longer needed.

## Evidence rules

Mark a test as passed only when supported by observed device results. Distinguish confirmed behavior from failed tests and unverified assumptions. Do not publish device identifiers, diagnostic reports or private network details.
