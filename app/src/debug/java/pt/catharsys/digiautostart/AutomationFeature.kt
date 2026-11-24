package pt.catharsys.digiautostart

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.widget.Toast

/** Debug-only setup UI for the foreground automation service. */
object AutomationFeature {
    private const val PREFS = "digi_autostart"

    fun configure(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("automation_enabled", false)
        val mode = prefs.getInt("mode", 2)
        val overlay = Settings.canDrawOverlays(activity)
        val usage = hasUsageAccess(activity)
        val message = buildString {
            append("Automação: ").append(if (enabled) "ATIVA" else "desativada")
            append("\nModo: ").append(if (mode == 1) "A — abrir sempre" else "B — apenas se DIGI TV era a última aplicação")
            append("\nMostrar sobre outras aplicações: ").append(if (overlay) "autorizado" else "por autorizar")
            if (mode != 1) append("\nAcesso às estatísticas: ").append(if (usage) "autorizado" else "por autorizar")
            append("\n\nQuando ativa, fica um serviço em primeiro plano com uma notificação discreta. O serviço monitoriza o ecrã mesmo com esta aplicação fora de vista e tenta retomar a DIGI TV ao acordar.")
            append("\n\nO serviço é reiniciado após o arranque da box, se a automação estiver ativa.")
        }
        val actions = mutableListOf<String>()
        if (!overlay) actions += "Autorizar «Mostrar sobre outras aplicações»"
        if (mode != 1 && !usage) actions += "Autorizar acesso às estatísticas"
        if (!enabled) actions += "Ativar automação"
        if (enabled) actions += "Desativar automação"

        // Android AlertDialog cannot display setMessage() and setItems() together.
        // Show status first, then present the action list in a separate dialog.
        AlertDialog.Builder(activity)
            .setTitle("Automação automática")
            .setMessage(message)
            .setPositiveButton("Continuar") { _, _ ->
                AlertDialog.Builder(activity)
                    .setTitle("O que pretendes fazer?")
                    .setItems(actions.toTypedArray()) { _, which ->
                        when (actions[which]) {
                            "Autorizar «Mostrar sobre outras aplicações»" ->
                                activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${activity.packageName}")))
                            "Autorizar acesso às estatísticas" ->
                                activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            "Ativar automação" -> {
                                val ready = Settings.canDrawOverlays(activity) &&
                                    (prefs.getInt("mode", 2) == 1 || hasUsageAccess(activity))
                                if (!ready) {
                                    Toast.makeText(activity, "Concede primeiro as permissões indicadas e volta a abrir esta opção.", Toast.LENGTH_LONG).show()
                                } else {
                                    prefs.edit().putBoolean("automation_enabled", true).apply()
                                    try {
                                        AutomationService.start(activity)
                                        Toast.makeText(activity, "Automação ativada. O serviço fica ativo em segundo plano.", Toast.LENGTH_LONG).show()
                                    } catch (error: Exception) {
                                        prefs.edit().putBoolean("automation_enabled", false).apply()
                                        Toast.makeText(activity, "Não foi possível iniciar o serviço: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            "Desativar automação" -> {
                                prefs.edit().putBoolean("automation_enabled", false).putBoolean("wake_should_launch", false).apply()
                                AutomationService.stop(activity)
                                Toast.makeText(activity, "Automação desativada.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Fechar", null)
                    .show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    fun startIfEnabled(context: Context) {
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("automation_enabled", false)) return
        try {
            AutomationService.start(context)
        } catch (error: Exception) {
            DiagnosticsFeature.record(context, "automation_service_start_failed",
                "exception=${error.javaClass.simpleName}")
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }
}
