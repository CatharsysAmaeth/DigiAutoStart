# DAdA 0.6.1 Debug investigation test plan

## T1 — Installation and isolation

- Install the Android 12 Debug update over the existing DAdA Debug app.
- Confirm the package identity remains `pt.catharsys.digiautostart`.
- Confirm the Debug version updates without uninstalling the existing app.
- Confirm the Release APK is not modified or published by this feature branch.
- Confirm DIGI TV and the original Android TV launcher remain unchanged.

## T2 — Package and deep-link discovery

- Start opt-in Diagnostics collection.
- Open “Investigar DIGI TV (Debug)”.
- Run “Inspecionar aplicação e deep links” and record the installed DIGI TV version, exported activities and whether `digitv://ia-livestream/1` resolves to an activity.
- Do not interpret a route string found in the JavaScript bundle as proof that the URI is accepted.
- If an activity resolves, run the candidate deep-link test once and record the resulting screen.
- Compare with “Abrir DIGI TV normalmente”.
- Record whether either route reaches the initial screen, channel 1, an error, or another screen.

## T3 — Accessibility observation

- Enable Diagnostics collection in DAdA.
- From the investigation screen, open Android Accessibility settings.
- Manually enable “Observador de interface DIGI TV (Debug)”.
- Return to DAdA and verify the probe reports as enabled.
- Open DIGI TV normally and wait for the initial screen to settle.
- Record the initial highlighted “Ver mais” state.
- Observe whether window-state, content-change, focus and selection events are emitted.
- Confirm the view-tree summary identifies visible labels, focusable/clickable nodes and bounds.
- Navigate manually with DOWN, DOWN, OK and record the corresponding focus/selection changes and resulting channel screen.
- Disable the accessibility observer after the test if no longer needed.

## T4 — Variable startup timing

- With Diagnostics and the accessibility observer enabled, open DIGI TV normally five times.
- For each run, record the first DIGI TV window event, the first non-empty view-tree snapshot, the initial “Ver mais” highlight and the first observed live-channel/player state.
- Repeat after returning to DAdA between runs.
- Do not use a fixed delay as a proxy for readiness; derive timing from observed UI events.
- Record any run where the accessibility tree is unavailable or incomplete.

## T5 — Report and privacy

- Upload the encrypted report only after reviewing the test session and choosing “Criar e enviar relatório”.
- Treat visible UI text as potentially sensitive even with email/phone redaction.
- Confirm reports are stored only in the private diagnostics repository.
- Delete local logs after successful review if they are no longer needed.

## Existing DAdA acceptance checks

Retain the installation, configuration, standby/wake, startup/recovery, target-resolution and Release-isolation checks from the previous acceptance plan.

## Evidence rules

Mark a test as passed only when supported by observed device results. Distinguish confirmed behavior from failed tests and unverified assumptions. Do not publish device identifiers, diagnostic reports or private network details.
