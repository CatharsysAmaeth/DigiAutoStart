package pt.catharsys.digiautostart

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Debug-only, user-mediated setup for the DIGI TV accessibility observer.
 *
 * The Android TV OEM link is not a public SDK contract. Before launching it,
 * record PackageManager's resolved activity and matching handlers so that a
 * successful startActivity() is not mistaken for proof that a service-specific
 * Settings screen was actually selected.
 */
class AccessibilitySetupActivity : Activity() {
    private lateinit var status: TextView
    private var lastLaunchRoute = "Ainda não foi aberta nenhuma página do sistema."

    private val serviceComponent by lazy {
        ComponentName(this, DigiTvAccessibilityProbeService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun render() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xFF101820.toInt())
        }
        content.addView(label("Observador DIGI TV", 26f, true))
        content.addView(label(
            "Autorização de Acessibilidade (Debug)", 16f, false
        ).apply { setTextColor(0xFFB8DDF2.toInt()) }, params(8))
        content.addView(label(
            "O observador recolhe eventos e um resumo dos elementos visíveis apenas na aplicação oficial DIGI TV (ro.digionline.tv), enquanto a recolha de diagnósticos estiver activa. Não executa cliques, não simula teclas e não controla outras aplicações.",
            16f
        ), params(18))
        content.addView(label(
            "A Acessibilidade pode permitir observar conteúdo apresentado no ecrã. Não actives o serviço se não concordares com este acesso. Os relatórios só são enviados mediante uma acção explícita e destinam-se ao repositório privado de diagnósticos.",
            15f
        ).apply { setTextColor(0xFFFFD18A.toInt()) }, params(18))
        status = label("", 17f, true).apply {
            setTextColor(0xFF8FE3B0.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFF1D3038.toInt())
        }
        content.addView(status, params(18))
        content.addView(actionButton("Autorizar observador nas Definições Android") {
            AlertDialog.Builder(this)
                .setTitle("Confirmar autorização")
                .setMessage(
                    "Confirmas que compreendes o acesso descrito e queres abrir a confirmação do Android para activar manualmente o Observador de interface DIGI TV (Debug)? A autorização final será sempre confirmada nas Definições do sistema."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar") { _, _ -> openSystemAuthorization() }
                .show()
        }, params(12))
        content.addView(actionButton("Abrir acessibilidade geral da TV (diagnóstico)") {
            openGeneralTvAccessibility()
        }, params(8))
        content.addView(actionButton("Abrir autorização específica (destino directo)") {
            confirmAndOpenSpecificAuthorizationDirect()
        }, params(8))
        content.addView(actionButton("Inventário técnico completo (Android + Acessibilidade)") {
            DeviceAccessibilityInventory.run(this)
        }, params(8))
        content.addView(actionButton("Actualizar estado") { refreshStatus() }, params(8))
        content.addView(actionButton("Voltar") { finish() }, params(8))
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
        refreshStatus()
    }

    private fun refreshStatus() {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val installed = runCatching {
            manager?.installedAccessibilityServiceList.orEmpty().any { matches(it) }
        }.getOrDefault(false)
        val active = runCatching {
            manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .orEmpty().any { matches(it) }
        }.getOrDefault(false)
        val secureEnabled = runCatching {
            Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty().split(':').any {
                it.equals(serviceComponent.flattenToString(), true) ||
                    it.equals(serviceComponent.flattenToShortString(), true)
            }
        }.getOrDefault(false)
        status.text = buildString {
            append("Serviço instalado: "); append(if (installed) "sim" else "não")
            append("\nAutorização do Android: ")
            append(if (active && secureEnabled) "activa" else "inactiva")
            append("\nÚltimo encaminhamento: "); append(lastLaunchRoute)
            append("\nA autorização final é sempre gerida pelo Android.")
        }
        DiagnosticsFeature.record(
            this, "accessibility_setup_status",
            "installed=$installed,active=$active,secure_enabled=$secureEnabled,route=$lastLaunchRoute"
        )
    }

    private fun matches(info: AccessibilityServiceInfo): Boolean =
        info.resolveInfo.serviceInfo?.let {
            it.packageName == serviceComponent.packageName &&
                it.name == serviceComponent.className
        } == true

    private fun describeActivity(component: ComponentName?): String =
        component?.let { "${it.packageName}/${it.className}" } ?: "none"

    private fun confirmAndOpenSpecificAuthorizationDirect() {
        AlertDialog.Builder(this)
            .setTitle("Confirmar autorização")
            .setMessage(
                "A DAdA vai abrir directamente a actividade de autorização do serviço, se a ROM a disponibilizar. A autorização final continua a exigir a tua confirmação nas Definições Android."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ -> openSpecificAuthorizationDirect() }
            .show()
    }

    private fun openSpecificAuthorizationDirect() {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val serviceInfo = runCatching {
            manager?.installedAccessibilityServiceList.orEmpty().firstOrNull { matches(it) }
        }.getOrNull()
        val resolved = runCatching {
            val probe = Intent(TV_ACCESSIBILITY_SERVICE_ACTION)
            packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.let { ComponentName(it.packageName, it.name) }
        }.getOrNull()

        val details = "service=${serviceComponent.flattenToString()};service_found=${serviceInfo != null};destination=${describeActivity(resolved)}"
        DiagnosticsFeature.record(this, "accessibility_tv_oem_direct_preflight", details)

        if (serviceInfo == null || resolved == null) {
            lastLaunchRoute = "destino directo indisponível; service_found=${serviceInfo != null}; destination=${describeActivity(resolved)}"
            refreshStatus()
            return
        }

        val intent = Intent(TV_ACCESSIBILITY_SERVICE_ACTION).apply {
            component = resolved
            putExtra(TV_ACCESSIBILITY_SERVICE_INFO_EXTRA, serviceInfo)
        }
        val opened = runCatching {
            startActivity(intent)
            true
        }.getOrElse { error ->
            DiagnosticsFeature.record(
                this, "accessibility_tv_oem_direct_failed",
                "error=${error.javaClass.simpleName}:${error.message.orEmpty().take(160)};$details"
            )
            false
        }
        lastLaunchRoute = if (opened) {
            "pedido directo enviado a ${describeActivity(resolved)}"
        } else {
            "falha ao abrir destino directo ${describeActivity(resolved)}"
        }
        DiagnosticsFeature.record(
            this, "accessibility_tv_oem_direct_result",
            "opened=$opened;$details"
        )
    }

    private fun openGeneralTvAccessibility() {
        val intent = Intent(TV_ACCESSIBILITY_GENERAL_ACTION)
        val resolved = runCatching {
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.let { ComponentName(it.packageName, it.name) }
        }.getOrNull()
        val candidates = runCatching {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { item -> item.activityInfo?.let {
                    "${it.packageName}/${it.name}:exported=${it.exported}:enabled=${it.enabled}:permission=${it.permission.orEmpty()}"
                } }.distinct().take(MAX_ACTIVITY_CANDIDATES)
        }.getOrDefault(emptyList())
        DiagnosticsFeature.record(
            this, "accessibility_tv_general_preflight",
            "resolved=${describeActivity(resolved)};candidates=${candidates.joinToString(",")}"
        )
        val opened = runCatching {
            startActivity(intent)
            true
        }.getOrElse { error ->
            DiagnosticsFeature.record(
                this, "accessibility_tv_general_failed",
                "error=${error.javaClass.simpleName}:${error.message.orEmpty().take(160)}"
            )
            false
        }
        lastLaunchRoute = if (opened) {
            "página geral TV; destino: ${describeActivity(resolved)}"
        } else {
            "não foi possível abrir a página geral TV"
        }
        DiagnosticsFeature.record(
            this, "accessibility_tv_general_result",
            "opened=$opened;resolved=${describeActivity(resolved)};candidates=${candidates.joinToString(",")}"
        )
    }

    private fun openSystemAuthorization() {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val serviceInfo = runCatching {
            manager?.installedAccessibilityServiceList.orEmpty().firstOrNull { matches(it) }
        }.getOrNull()

        val tvServiceIntent = serviceInfo?.let {
            Intent(TV_ACCESSIBILITY_SERVICE_ACTION).apply {
                putExtra(TV_ACCESSIBILITY_SERVICE_INFO_EXTRA, it)
            }
        }

        if (tvServiceIntent != null) {
            val resolved = runCatching {
                packageManager.resolveActivity(
                    tvServiceIntent, PackageManager.MATCH_DEFAULT_ONLY
                )?.activityInfo?.let { ComponentName(it.packageName, it.name) }
            }.getOrNull()
            val candidates = runCatching {
                packageManager.queryIntentActivities(
                    tvServiceIntent, PackageManager.MATCH_DEFAULT_ONLY
                ).mapNotNull { item ->
                    item.activityInfo?.let {
                        "${it.packageName}/${it.name}:exported=${it.exported}"
                    }
                }.distinct().take(MAX_ACTIVITY_CANDIDATES)
            }.getOrDefault(emptyList())

            val serviceId = runCatching { serviceInfo.id }.getOrDefault("unavailable")
            val servicePackage = serviceInfo.resolveInfo.serviceInfo?.packageName ?: "unknown"
            val serviceClass = serviceInfo.resolveInfo.serviceInfo?.name ?: "unknown"
            val preflight = "resolved=${describeActivity(resolved)};candidates=${candidates.joinToString(",")};serviceId=$serviceId;service=$servicePackage/$serviceClass"

            DiagnosticsFeature.record(
                this, "accessibility_tv_oem_link_preflight", preflight
            )

            val tvSettingsOpened = runCatching {
                startActivity(tvServiceIntent)
                true
            }.getOrElse { error ->
                DiagnosticsFeature.record(
                    this, "accessibility_tv_oem_link_failed",
                    "error=${error.javaClass.simpleName}:${error.message.orEmpty().take(160)};$preflight"
                )
                false
            }

            if (tvSettingsOpened) {
                lastLaunchRoute = "pedido enviado; destino resolvido: ${describeActivity(resolved)}"
                DiagnosticsFeature.record(
                    this, "accessibility_tv_oem_link_launch_accepted",
                    "action=$TV_ACCESSIBILITY_SERVICE_ACTION;resolved=${describeActivity(resolved)};candidates=${candidates.joinToString(",")};service_found=true"
                )
                return
            }
        } else {
            DiagnosticsFeature.record(
                this, "accessibility_tv_oem_link_preflight",
                "resolved=none;candidates=none;service_found=false"
            )
        }

        // Fallback only when the TV OEM link cannot be launched.
        val fallbackOpened = runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            true
        }.getOrElse { error ->
            DiagnosticsFeature.record(
                this, "accessibility_general_settings_failed",
                "error=${error.javaClass.simpleName}:${error.message.orEmpty().take(160)}"
            )
            false
        }
        lastLaunchRoute = if (fallbackOpened) {
            "página geral de Acessibilidade (fallback)"
        } else {
            "não foi possível abrir as Definições Android"
        }
        DiagnosticsFeature.record(
            this, "accessibility_authorization_settings_opened",
            "tv_oem_link_launch_accepted=false,general_settings_opened=$fallbackOpened,service_found=${serviceInfo != null}"
        )
        if (!fallbackOpened) {
            AlertDialog.Builder(this)
                .setTitle("Definições indisponíveis")
                .setMessage("Não foi possível abrir as Definições de Acessibilidade nesta ROM.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun label(value: String, size: Float, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
        }

    private fun actionButton(value: String, action: () -> Unit) =
        Button(this).apply {
            text = value
            textSize = 16f
            isAllCaps = false
            minHeight = dp(56)
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun params(bottom: Int) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TV_ACCESSIBILITY_SERVICE_ACTION =
            "android.settings.ACCESSIBILITY_SHORTCUT_SERVICES_TV_OEM_LINK"
        private const val TV_ACCESSIBILITY_SERVICE_INFO_EXTRA =
            "accessibilityServiceInfo"
        private const val TV_ACCESSIBILITY_GENERAL_ACTION =
            "android.settings.ACCESSIBILITY_TV_OEM_LINK"
        private const val MAX_ACTIVITY_CANDIDATES = 8
    }
}
