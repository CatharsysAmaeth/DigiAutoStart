package pt.catharsys.digiautostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the opt-in foreground monitor after the Android TV box boots. */
class AutomationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val enabled = context.getSharedPreferences("digi_autostart", Context.MODE_PRIVATE)
            .getBoolean("automation_enabled", false)
        if (!enabled) return
        try {
            AutomationService.start(context)
            DiagnosticsFeature.record(context, "automation_recovery_requested", "reason=${intent.action}")
        } catch (error: Exception) {
            DiagnosticsFeature.record(context, "automation_boot_restart_failed",
                "exception=${error.javaClass.simpleName}")
        }
    }
}
