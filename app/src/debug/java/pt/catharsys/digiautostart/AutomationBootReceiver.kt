package pt.catharsys.digiautostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the opt-in monitor after boot; opening DIGI TV is a separate opt-in. */
class AutomationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = context.getSharedPreferences("digi_autostart", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("automation_enabled", false)
        if (!enabled) {
            DiagnosticsFeature.record(context, "automation_boot_ignored", "reason=automation_disabled")
            return
        }

        val launchOnBoot = prefs.getBoolean("launch_digi_on_boot", false)
        try {
            AutomationService.start(context, fromBoot = true)
            DiagnosticsFeature.record(
                context,
                "automation_recovery_requested",
                "reason=${intent.action},launch_on_boot=$launchOnBoot"
            )
        } catch (error: Exception) {
            DiagnosticsFeature.record(
                context,
                "automation_boot_restart_failed",
                "exception=${error.javaClass.simpleName}"
            )
        }
    }
}
