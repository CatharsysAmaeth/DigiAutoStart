# Debug diagnostics

Diagnostics are Debug-only and opt-in. They capture DigiAutoStart lifecycle events, screen broadcasts and the automation service's own decisions and launch outcomes.

## Automation events

- Service start, destruction and screen receiver registration.
- SCREEN_OFF decision: selected mode and whether DIGI TV was the last foreground user app.
- Missing Usage Access.
- SCREEN_ON decision and scheduled delay.
- Launch blocked, missing launch intent, exception, or startActivity call returned.

The implementation does not export a general app-usage history. Mode B reads UsageEvents transiently and records only whether DIGI TV matched the last foreground user app.

## Runtime architecture

The Debug foreground service keeps the dynamic screen receiver alive while the Activity is stopped. It uses START_STICKY and a BOOT_COMPLETED receiver to request recovery after process reclamation and device reboot. A persistent notification is displayed while enabled.

## Permissions

- Usage Access: Mode B only.
- Display over other apps: Android background-activity-launch exemption.
- Foreground service and boot completed: keep and restore the receiver.
- Internet, FileProvider and package-install permission: Debug diagnostics/update flow.

These permissions and components are absent from Release. No root, accessibility service, overlay window, bootloader or firmware modification is used.

## Privacy

- Capture is opt-in and bounded to approximately 1 MiB plus one previous segment.
- No serial number, MAC address, IP address, SSID, account information, credentials or DIGI TV content is collected.
- Reports are AES-256 encrypted and uploaded to the private diagnostics repository.
