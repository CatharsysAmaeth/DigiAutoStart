# DIGI R1A test plan

## Test environment

Record before testing:
- Device model and firmware build.
- Android version and security patch.
- DIGI TV version.
- Installation method.
- App permissions and special access granted.

Do not publish device identifiers, credentials or private network details.

## Test stages

### T1 - Installation
- Install the standalone APK.
- Confirm DigiAutoStart appears in the app list.
- Confirm DIGI TV and the original launcher remain functional.
- Check that Widevine L1 and device certification remain intact.

### T2 - Manual launch
- Launch DIGI TV using the app's manual test.
- Test with DIGI TV installed and available.
- Test graceful handling if the package cannot be resolved.

### T3 - Foreground tracking
- Evaluate Usage Access.
- Open DIGI TV, another app and the launcher.
- Check whether the app can reliably identify the last foreground app.

### T4 - Standby and wake
- Test short and long standby periods.
- Record which events are observed.
- Distinguish a real wake event from process restart or boot.

### T5 - Automation modes
- Test Mode A.
- Test Mode B.
- Test configurable delay and cooldown.
- Test disabled state.

### T6 - Reliability
- Repeat tests across multiple standby cycles.
- Record successes, failures and timing.
- Check behavior after reboot.
- Check whether Android terminates or restricts the app.

## Evidence rules

Only mark a test as passed when supported by observed device results.
Separate confirmed behavior, failed tests and unverified hypotheses.
