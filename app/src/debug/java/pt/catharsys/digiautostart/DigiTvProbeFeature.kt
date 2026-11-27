package pt.catharsys.digiautostart

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/**
 * Debug-only, opt-in probes for understanding how the official DIGI TV app
 * handles external links and exposes its TV interface to Android accessibility.
 *
 * This feature never injects key events and never changes the DIGI TV package.
 */
object DigiTvProbeFeature {
    private const val TARGET_PACKAGE = "ro.digionline.tv"
    private const val LIVE_CHANNEL_ONE_CANDIDATE = "digitv://ia-livestream/1"

    fun show(activity: Activity) {
        val options = arrayOf(
            "Mapear APK DIGI TV e Definições ZTE (leitura)",
            "Inspecionar aplicação e deep links (sem abrir)",
            "Testar deep link candidato para o canal 1",
            "Abrir DIGI TV normalmente",
            "Ativar observação da interface (Acessibilidade)",
            "Diagnósticos e envio de relatório"
        )
        AlertDialog.Builder(activity)
            .setTitle("Investigação DIGI TV")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> DevicePackageMap.run(activity)
                    1 -> inspect(activity)
                    2 -> launchCandidate(activity)
                    3 -> launchNormally(activity)
                    4 -> explainAccessibilityProbe(activity)
                    5 -> DiagnosticsFeature.show(activity)
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun inspect(activity: Activity) {
        val pm = activity.packageManager
        val details = StringBuilder()
        var installed = false
        try {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(TARGET_PACKAGE, PackageManager.GET_ACTIVITIES)
            installed = true
            details.append("package=$TARGET_PACKAGE;versionName=${info.versionName};versionCode=${info.longVersionCode}")
            info.activities.orEmpty().forEach { item ->
                details.append(";activity=${item.name},exported=${item.exported},enabled=${item.enabled}")
            }
        } catch (error: PackageManager.NameNotFoundException) {
            details.append("package_not_installed=$TARGET_PACKAGE")
        }

        val candidate = candidateIntent()
        val resolved = candidate.resolveActivity(pm)
        val matches = try {
            pm.queryIntentActivities(candidate, PackageManager.MATCH_DEFAULT_ONLY)
                .joinToString(",") { it.activityInfo.name }
        } catch (error: Exception) {
            "query_error=${error.javaClass.simpleName}"
        }
        val accessibilityEnabled = isProbeEnabled(activity)
        details.append(";candidateUri=$LIVE_CHANNEL_ONE_CANDIDATE")
        details.append(";resolvedActivity=${resolved?.className ?: "none"}")
        details.append(";matchingActivities=$matches")
        details.append(";accessibilityProbeEnabled=$accessibilityEnabled")

        DiagnosticsFeature.record(activity, "digi_tv_probe_inspection", details.toString())
        val summary = if (installed) {
            "DIGI TV instalada. Deep link: ${resolved?.className ?: "sem atividade correspondente"}."
        } else {
            "A aplicação DIGI TV não foi encontrada."
        }
        Toast.makeText(activity, summary, Toast.LENGTH_LONG).show()
    }

    private fun launchCandidate(activity: Activity) {
        val intent = candidateIntent()
        val resolved = intent.resolveActivity(activity.packageManager)
        if (resolved == null) {
            DiagnosticsFeature.record(activity, "digi_tv_deeplink_not_resolved", "uri=$LIVE_CHANNEL_ONE_CANDIDATE")
            Toast.makeText(activity, "A DIGI TV não registou este deep link.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            DiagnosticsFeature.record(activity, "digi_tv_deeplink_launch_requested",
                "uri=$LIVE_CHANNEL_ONE_CANDIDATE;activity=${resolved.className};elapsedRealtimeMs=$startedAt")
            activity.startActivity(intent)
            Toast.makeText(activity, "Deep link enviado. Verifica o ecrã da box.", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            DiagnosticsFeature.record(activity, "digi_tv_deeplink_launch_failed",
                "exception=${error.javaClass.simpleName};message=${error.message ?: ""}")
            Toast.makeText(activity, "Não foi possível abrir o deep link: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchNormally(activity: Activity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
        if (intent == null) {
            DiagnosticsFeature.record(activity, "digi_tv_normal_launch_failed", "reason=no_launch_intent")
            Toast.makeText(activity, "Não foi encontrada a atividade de arranque da DIGI TV.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            DiagnosticsFeature.record(activity, "digi_tv_normal_launch_requested")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            activity.startActivity(intent)
        } catch (error: Exception) {
            DiagnosticsFeature.record(activity, "digi_tv_normal_launch_failed",
                "exception=${error.javaClass.simpleName};message=${error.message ?: ""}")
            Toast.makeText(activity, "Falha ao abrir DIGI TV: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun explainAccessibilityProbe(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Observação da interface DIGI TV")
            .setMessage(
                "A recolha de acessibilidade é opcional e exige ativação manual nas definições Android. " +
                    "Quando ativa, a DAdA observa apenas eventos e elementos da janela da aplicação ro.digionline.tv. " +
                    "Pode registar textos visíveis, nomes de componentes, foco e seleção para perceber a navegação. " +
                    "Não captura imagens ou áudio e não recolhe eventos de outras aplicações. Os campos que o Android identifica como palavras-passe são substituídos por um marcador; ainda assim, texto visível pode conter informação sensível. " +
                    "Os registos só são guardados quando a recolha de Diagnósticos está ativa e só são enviados quando pedires o envio do relatório. " +
                    "O relatório é encriptado antes do envio para o repositório privado."
            )
            .setPositiveButton("Abrir definições") { _, _ ->
                DiagnosticsFeature.record(activity, "digi_tv_accessibility_settings_opened")
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun candidateIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(LIVE_CHANNEL_ONE_CANDIDATE)).apply {
        setPackage(TARGET_PACKAGE)
        addCategory(Intent.CATEGORY_DEFAULT)
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun isProbeEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val component = ComponentName(context, DigiTvAccessibilityProbeService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }
}
