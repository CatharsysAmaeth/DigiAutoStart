package pt.catharsys.digiautostart

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Debug-only configuration screen for all startup and standby options. */
object AutomationFeature {
    private const val PREFS = "digi_autostart"
    private const val KEY_ENABLED = "automation_enabled"
    private const val KEY_LAUNCH_ON_BOOT = "launch_digi_on_boot"

    fun configure(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val target = TargetAppResolver.selectedPackage(activity)
        val candidates = TargetAppResolver.candidates(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), dp(activity, 8))
        }
        val monitor = Switch(activity).apply {
            text = "Reabrir DIGI TV depois do standby"
            isChecked = prefs.getBoolean(KEY_ENABLED, false)
        }
        val boot = Switch(activity).apply {
            text = "Abrir DIGI TV quando a box liga"
            isChecked = prefs.getBoolean(KEY_LAUNCH_ON_BOOT, false)
        }
        root.addView(monitor)
        root.addView(boot, params(activity, 0, 8))

        root.addView(label(activity, "Comportamento após standby"), params(activity, 16, 4))
        val modeGroup = RadioGroup(activity).apply { orientation = RadioGroup.VERTICAL }
        val modeA = RadioButton(activity).apply { id = 1; text = "Modo A — abrir sempre" }
        val modeB = RadioButton(activity).apply { id = 2; text = "Modo B — só se DIGI TV era a última aplicação" }
        modeGroup.addView(modeA)
        modeGroup.addView(modeB)
        modeGroup.check(if (prefs.getInt("mode", 2) == 1) 1 else 2)
        root.addView(modeGroup)

        root.addView(label(activity, "Atraso antes de reabrir"), params(activity, 12, 4))
        val delayLabel = label(activity, "")
        val delay = SeekBar(activity).apply {
            max = 30
            progress = prefs.getInt("delay_seconds", 5).coerceIn(0, 30)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    delayLabel.text = "$value segundos"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        delayLabel.text = "${delay.progress} segundos"
        root.addView(delayLabel)
        root.addView(delay)

        root.addView(label(activity, "Aplicação de destino"), params(activity, 16, 4))
        val targetLabel = label(activity,
            TargetAppResolver.displayName(activity, target) +
                if (target == TargetAppResolver.DEFAULT_PACKAGE) " (padrão)" else "")
        root.addView(targetLabel)
        val chooseTarget = Button(activity).apply {
            text = "Detetar novamente / escolher aplicação"
            isAllCaps = false
        }
        root.addView(chooseTarget)
        chooseTarget.setOnClickListener {
            val refreshed = TargetAppResolver.candidates(activity)
            val preferred = refreshed.filter { it.label.contains("digi", ignoreCase = true) }
            val options = if (preferred.isNotEmpty()) preferred else refreshed
            if (options.isEmpty()) {
                Toast.makeText(activity, "Não foram encontradas aplicações Android TV compatíveis.", Toast.LENGTH_LONG).show()
            } else {
                AlertDialog.Builder(activity)
                    .setTitle("Escolher aplicação DIGI TV")
                    .setItems(options.map { "${it.label}  (${it.packageName})" }.toTypedArray()) { _, which ->
                        TargetAppResolver.select(activity, options[which].packageName)
                        targetLabel.text = options[which].label
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }

        root.addView(label(activity, "Permissões necessárias"), params(activity, 16, 4))
        val overlayButton = Button(activity).apply {
            text = if (Settings.canDrawOverlays(activity)) "Sobreposição: autorizada" else "Autorizar mostrar sobre outras aplicações"
            isAllCaps = false
            setOnClickListener {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}")))
            }
        }
        root.addView(overlayButton)
        val usageButton = Button(activity).apply {
            text = if (hasUsageAccess(activity)) "Estatísticas de utilização: autorizadas" else "Autorizar acesso às estatísticas (Modo B)"
            isAllCaps = false
            setOnClickListener { activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        root.addView(usageButton)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("DAdA · Configurar")
            .setView(android.widget.ScrollView(activity).apply { addView(root) })
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedTarget = TargetAppResolver.selectedPackage(activity)
                if (selectedTarget == null) {
                    Toast.makeText(activity, "Seleciona uma aplicação DIGI TV antes de guardar.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val wantsMonitor = monitor.isChecked
                val wantsBoot = boot.isChecked
                val mode = if (modeGroup.checkedRadioButtonId == 1) 1 else 2
                if ((wantsMonitor || wantsBoot) && !Settings.canDrawOverlays(activity)) {
                    Toast.makeText(activity, "Autoriza primeiro «Mostrar sobre outras aplicações».", Toast.LENGTH_LONG).show()
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")))
                    return@setOnClickListener
                }
                if (wantsMonitor && mode == 2 && !hasUsageAccess(activity)) {
                    Toast.makeText(activity, "O Modo B precisa de acesso às estatísticas de utilização.", Toast.LENGTH_LONG).show()
                    activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    return@setOnClickListener
                }
                prefs.edit()
                    .putBoolean(KEY_ENABLED, wantsMonitor)
                    .putBoolean(KEY_LAUNCH_ON_BOOT, wantsBoot)
                    .putInt("mode", mode)
                    .putInt("delay_seconds", delay.progress)
                    .apply()
                if (wantsMonitor) {
                    try {
                        AutomationService.start(activity)
                    } catch (error: Exception) {
                        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
                        Toast.makeText(activity, "Não foi possível iniciar a automação: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    AutomationService.stop(activity)
                }
                Toast.makeText(activity, "Configuração do DAdA guardada.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    fun startIfEnabled(context: Context) {
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)) return
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

    private fun label(context: Context, value: String) = TextView(context).apply {
        text = value
        textSize = 15f
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(0, dp(context, 5), 0, dp(context, 5))
    }

    private fun params(context: Context, top: Int, bottom: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(context, top)
            bottomMargin = dp(context, bottom)
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
