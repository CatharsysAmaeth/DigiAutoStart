package pt.catharsys.digiautostart

import android.app.Activity
import android.app.AppOpsManager
import android.app.AlertDialog
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.widget.Toast

/**
 * Debug-only standby/wake automation experiment.
 *
 * SCREEN_ON/OFF are observed only while this process is alive. Background
 * Activity launch is attempted only after the user grants the special overlay
 * permission, which is an Android background-launch exemption. No overlay is
 * drawn by this application.
 */
object AutomationFeature {
    private const val PREFS = "digi_autostart"
    private const val TARGET_PACKAGE = "ro.digionline.tv"
    private const val LOOKBACK_MS = 15 * 60 * 1000L
    private var receiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())

    fun attach(context: Context) {
        val app = context.applicationContext
        if (!app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("automation_enabled", false) || receiver != null) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val instance = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenOff(ctx)
                    Intent.ACTION_SCREEN_ON -> onScreenOn(ctx)
                }
            }
        }
        receiver = instance
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(instance, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            app.registerReceiver(instance, filter)
        }
        DiagnosticsFeature.record(app, "automation_receiver_attached")
    }

    fun configure(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("automation_enabled", false)
        val mode = prefs.getInt("mode", 2)
        val overlay = Settings.canDrawOverlays(activity)
        val usage = hasUsageAccess(activity)
        val requirements = buildString {
            append("Automação: ").append(if (enabled) "ATIVA" else "desativada")
            append("\nModo: ").append(if (mode == 1) "A — sempre" else "B — apenas se DIGI TV era a última aplicação")
            append("\nMostrar sobre outras aplicações: ").append(if (overlay) "autorizado" else "por autorizar")
            if (mode != 1) append("\nAcesso às estatísticas de utilização: ").append(if (usage) "autorizado" else "por autorizar")
            append("\n\nA permissão de sobreposição é usada apenas como exceção do Android para permitir abrir a DIGI TV em segundo plano. A aplicação não desenha qualquer sobreposição.")
            append("\n\nEsta é uma experiência Debug. O recetor só funciona enquanto o processo da aplicação estiver vivo.")
        }

        val actions = mutableListOf<String>()
        if (!overlay) actions += "Autorizar «Mostrar sobre outras aplicações»"
        if (mode != 1 && !usage) actions += "Autorizar acesso às estatísticas"
        if (!enabled) actions += "Ativar automação"
        if (enabled) actions += "Desativar automação"

        AlertDialog.Builder(activity)
            .setTitle("Automação de standby — teste")
            .setMessage(requirements)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "Autorizar «Mostrar sobre outras aplicações»" -> {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${activity.packageName}"))
                        activity.startActivity(intent)
                    }
                    "Autorizar acesso às estatísticas" ->
                        activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    "Ativar automação" -> {
                        val ready = Settings.canDrawOverlays(activity) &&
                            (prefs.getInt("mode", 2) == 1 || hasUsageAccess(activity))
                        if (!ready) {
                            Toast.makeText(activity, "Concede primeiro as permissões indicadas.", Toast.LENGTH_LONG).show()
                        } else {
                            prefs.edit().putBoolean("automation_enabled", true).apply()
                            attach(activity.applicationContext)
                            Toast.makeText(activity, "Automação ativada.", Toast.LENGTH_LONG).show()
                        }
                    }
                    "Desativar automação" -> {
                        prefs.edit().putBoolean("automation_enabled", false).putBoolean("wake_should_launch", false).apply()
                        detach(activity.applicationContext)
                        Toast.makeText(activity, "Automação desativada.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun detach(context: Context) {
        val current = receiver ?: return
        try {
            context.applicationContext.unregisterReceiver(current)
        } catch (_: IllegalArgumentException) {
        }
        receiver = null
        DiagnosticsFeature.record(context, "automation_receiver_detached")
    }

    private fun onScreenOff(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = prefs.getInt("mode", 2)
        val lastPackage = if (mode == 1) null else lastForegroundPackage(context)
        val shouldLaunch = if (mode == 1) true else lastPackage == TARGET_PACKAGE
        prefs.edit().putBoolean("wake_should_launch", shouldLaunch).apply()
        DiagnosticsFeature.record(
            context,
            "automation_screen_off_decision",
            "mode=${if (mode == 1) "A" else "B"},last_foreground=${lastPackage ?: "unknown"},should_launch=$shouldLaunch"
        )
    }

    private fun onScreenOn(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shouldLaunch = prefs.getBoolean("wake_should_launch", false)
        prefs.edit().putBoolean("wake_should_launch", false).apply()
        DiagnosticsFeature.record(context, "automation_screen_on", "should_launch=$shouldLaunch")
        if (!shouldLaunch) return

        val delay = prefs.getInt("delay_seconds", 5).coerceIn(0, 30)
        DiagnosticsFeature.record(context, "automation_launch_scheduled", "delay_seconds=$delay")
        handler.postDelayed({
            if (!Settings.canDrawOverlays(context)) {
                DiagnosticsFeature.record(context, "automation_launch_blocked", "reason=overlay_permission_missing")
                return@postDelayed
            }
            val launch = context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
            if (launch == null) {
                DiagnosticsFeature.record(context, "automation_launch_failed", "reason=no_launch_intent")
                return@postDelayed
            }
            try {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(launch)
                DiagnosticsFeature.record(context, "automation_launch_start_activity_returned")
            } catch (error: Exception) {
                DiagnosticsFeature.record(context, "automation_launch_exception", "exception=${error.javaClass.simpleName}")
            }
        }, delay * 1000L)
    }

    private fun lastForegroundPackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = manager.queryEvents(end - LOOKBACK_MS, end) ?: return null
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                last = event.packageName
            }
        }
        return last
    }

    private fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }
}
