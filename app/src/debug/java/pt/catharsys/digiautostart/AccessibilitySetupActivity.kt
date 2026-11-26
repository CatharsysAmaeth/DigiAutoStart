package pt.catharsys.digiautostart

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
 * On Android TV, first try the AOSP TV Settings OEM link that opens the
 * selected service's own confirmation/toggle screen. If the ROM does not
 * implement that hook, fall back to the standard Accessibility settings page.
 * The system Settings UI remains responsible for the final user confirmation.
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

    private fun openSystemAuthorization() {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val serviceInfo = runCatching {
            manager?.installedAccessibilityServiceList.orEmpty().firstOrNull { matches(it) }
        }.getOrNull()

        // AOSP Android TV Settings exposes this exported OEM hook specifically
        // to open one installed accessibility service's own confirmation page.
        // The extra name and Parcelable type are defined by TvSettings.
        val tvServiceIntent = serviceInfo?.let {
            Intent(TV_ACCESSIBILITY_SERVICE_ACTION).apply {
                putExtra(TV_ACCESSIBILITY_SERVICE_INFO_EXTRA, it)
            }
        }

        val tvSettingsOpened = tvServiceIntent?.let { intent ->
            runCatching {
                startActivity(intent)
                true
            }.getOrElse { error ->
                DiagnosticsFeature.record(
                    this, "accessibility_tv_oem_link_failed",
                    "error=${error.javaClass.simpleName}:${error.message.orEmpty().take(160)}"
                )
                false
            }
        } ?: false

        if (tvSettingsOpened) {
            lastLaunchRoute = "página específica do serviço Android TV"
            DiagnosticsFeature.record(
                this, "accessibility_tv_oem_link_opened",
                "action=$TV_ACCESSIBILITY_SERVICE_ACTION,component=${serviceComponent.flattenToString()}"
            )
            return
        }

        // Fallback for ROMs that do not implement the Android TV OEM link.
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
            "página geral de Acessibilidade (a ROM não disponibilizou a página específica)"
        } else {
            "não foi possível abrir as Definições Android"
        }
        DiagnosticsFeature.record(
            this, "accessibility_authorization_settings_opened",
            "tv_oem_link_opened=$tvSettingsOpened,general_settings_opened=$fallbackOpened,service_found=${serviceInfo != null}"
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
    }
}
