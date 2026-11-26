package pt.catharsys.digiautostart

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import org.xmlpull.v1.XmlPullParser

/**
 * Read-only diagnostics for the Debug accessibility observer registration.
 *
 * This does not enable the service, change secure settings, or open system
 * settings. It compares PackageManager registration with AccessibilityManager
 * discovery and the user's enabled-service setting.
 */
object AccessibilityServiceDiagnostic {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun run(activity: Activity) {
        val context = activity.applicationContext
        val component = ComponentName(context, DigiTvAccessibilityProbeService::class.java)
        val pm = context.packageManager
        val lines = mutableListOf<String>()

        lines += "Componente: ${component.flattenToString()}"

        var serviceInfo: android.content.pm.ServiceInfo? = null
        try {
            @Suppress("DEPRECATION")
            serviceInfo = pm.getServiceInfo(component, PackageManager.GET_META_DATA)
            lines += "PackageManager.getServiceInfo: OK"
            lines += "enabled=${serviceInfo.enabled}; exported=${serviceInfo.exported}"
            lines += "permission=${serviceInfo.permission ?: "nenhuma"}"
            lines += "label=${serviceInfo.loadLabel(pm)}"
            lines += "metaData=${serviceInfo.metaData?.keySet()?.joinToString(",") ?: "nenhuma"}"
        } catch (error: PackageManager.NameNotFoundException) {
            lines += "PackageManager.getServiceInfo: NÃO ENCONTRADO"
        } catch (error: Exception) {
            lines += "PackageManager.getServiceInfo: ERRO ${error.javaClass.simpleName}: ${error.message}"
        }

        val actionMatches = try {
            val intent = Intent(AccessibilityService.SERVICE_INTERFACE)
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
                .filter { it.serviceInfo?.let { info ->
                    info.packageName == component.packageName && info.name == component.className
                } == true }
                .size
        } catch (error: Exception) {
            lines += "queryIntentServices: ERRO ${error.javaClass.simpleName}: ${error.message}"
            -1
        }
        lines += "Registo ACTION_ACCESSIBILITY_SERVICE: ${if (actionMatches > 0) "ENCONTRADO" else if (actionMatches == 0) "NÃO ENCONTRADO" else "ERRO"}"

        if (serviceInfo != null) {
            try {
                val parser = serviceInfo.loadXmlMetaData(pm, AccessibilityService.SERVICE_META_DATA)
                if (parser == null) {
                    lines += "Metadata XML: AUSENTE"
                } else {
                    parser.use {
                        var foundRoot = false
                        while (it.eventType != XmlPullParser.END_DOCUMENT) {
                            if (it.eventType == XmlPullParser.START_TAG) {
                                foundRoot = true
                                lines += "XML root: ${it.name}"
                                lines += "packageNames=${it.getAttributeValue(ANDROID_NS, "packageNames") ?: "(não definido)"}"
                                lines += "canRetrieveWindowContent=${it.getAttributeValue(ANDROID_NS, "canRetrieveWindowContent") ?: "(não definido)"}"
                                lines += "accessibilityEventTypes=${it.getAttributeValue(ANDROID_NS, "accessibilityEventTypes") ?: "(não definido)"}"
                                break
                            }
                            it.next()
                        }
                        if (!foundRoot) lines += "Metadata XML: sem elemento raiz"
                    }
                }
            } catch (error: Exception) {
                lines += "Metadata XML: ERRO ${error.javaClass.simpleName}: ${error.message}"
            }
        }

        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        if (manager == null) {
            lines += "AccessibilityManager: INDISPONÍVEL"
        } else {
            val installed = try {
                manager.installedAccessibilityServiceList
            } catch (error: Exception) {
                lines += "Lista de serviços instalados: ERRO ${error.javaClass.simpleName}: ${error.message}"
                emptyList()
            }
            val enabled = try {
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            } catch (error: Exception) {
                lines += "Lista de serviços ativos: ERRO ${error.javaClass.simpleName}: ${error.message}"
                emptyList()
            }
            fun containsTarget(items: List<AccessibilityServiceInfo>) = items.any { info ->
                info.resolveInfo.serviceInfo?.let {
                    it.packageName == component.packageName && it.name == component.className
                } == true
            }
            lines += "AccessibilityManager.installedAccessibilityServiceList: ${if (containsTarget(installed)) "ENCONTRADO" else "NÃO ENCONTRADO"} (total=${installed.size})"
            lines += "AccessibilityManager.enabledAccessibilityServiceList: ${if (containsTarget(enabled)) "ATIVO" else "NÃO ATIVO"} (total=${enabled.size})"
        }

        val enabledSetting = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val secureSettingContains = enabledSetting.split(':').any {
            it.equals(component.flattenToString(), ignoreCase = true) ||
                it.equals(component.flattenToShortString(), ignoreCase = true)
        }
        lines += "Settings.Secure enabled list: ${if (secureSettingContains) "CONTÉM O SERVIÇO" else "NÃO CONTÉM O SERVIÇO"}"

        val report = lines.joinToString("\n")
        DiagnosticsFeature.record(context, "accessibility_service_registration_diagnostic", report.replace("\n", ";"))
        AlertDialog.Builder(activity)
            .setTitle("Diagnóstico de Acessibilidade")
            .setMessage(report)
            .setPositiveButton("Fechar", null)
            .show()
    }
}
