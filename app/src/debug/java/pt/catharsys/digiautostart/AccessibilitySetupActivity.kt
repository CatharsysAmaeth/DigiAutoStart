package pt.catharsys.digiautostart

import android.accessibilityservice.AccessibilityService
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
 * The app explains the scope and obtains in-app consent, then delegates the
 * actual enable/disable decision to Android Settings.
 */
class AccessibilitySetupActivity : Activity() {
    private lateinit var status: TextView
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
        content.addView(actionButton("Autorizar e abrir Definições Android") {
            AlertDialog.Builder(this)
                .setTitle("Confirmar autorização")
                .setMessage(
                    "Confirmas que compreendes o acesso descrito e queres abrir as Definições Android para activar manualmente o Observador de interface DIGI TV (Debug)? O DAdA não consegue activar o serviço por ti."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar") { _, _ -> openSystemAuthorization() }
                .show()
        }, params(12))
        content.addView(actionButton("Resolução avançada (experimental)") {
            runAdvancedRecovery()
        }, params(12))
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
            append("\nA autorização final é sempre gerida pelo Android.")
        }
        DiagnosticsFeature.record(
            this, "accessibility_setup_status",
            "installed=$installed,active=$active,secure_enabled=$secureEnabled"
        )
    }

    private fun matches(info: AccessibilityServiceInfo): Boolean =
        info.resolveInfo.serviceInfo?.let {
            it.packageName == serviceComponent.packageName &&
                it.name == serviceComponent.className
        } == true

    private fun openSystemAuthorization() {
        val detailsIntent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
            putExtra(Intent.EXTRA_COMPONENT_NAME, serviceComponent.flattenToString())
        }
        val opened = runCatching {
            if (detailsIntent.resolveActivity(packageManager) != null) {
                startActivity(detailsIntent)
                true
            } else false
        }.getOrDefault(false)
        if (!opened) {
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .onFailure {
                    AlertDialog.Builder(this)
                        .setTitle("Definições indisponíveis")
                        .setMessage("Não foi possível abrir as Definições de Acessibilidade nesta ROM.")
                        .setPositiveButton("OK", null)
                        .show()
                }
        }
        DiagnosticsFeature.record(
            this, "accessibility_authorization_settings_opened",
            "details_intent_opened=$opened"
        )
    }

    private fun runAdvancedRecovery() {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val installed = runCatching {
            manager?.installedAccessibilityServiceList.orEmpty().any { matches(it) }
        }.getOrDefault(false)
        DiagnosticsFeature.record(
            this, "accessibility_advanced_recovery_requested",
            "service_visible_to_accessibility_manager=$installed"
        )
        val explanation = if (installed) {
            "O Android reconhece o serviço como instalado, mas a ROM pode não o apresentar na sua página de Acessibilidade. Vou tentar abrir primeiro a página específica do serviço e, se não existir, a página geral. A activação continua a exigir a tua confirmação no Android."
        } else {
            "O Android não está a devolver o serviço na lista de serviços instalados. Não existe uma forma segura de o activar à força. Podes abrir as Definições gerais para confirmar o estado."
        }
        AlertDialog.Builder(this)
            .setTitle("Resolução avançada")
            .setMessage(
                "$explanation\n\nEste modo não altera definições protegidas, não usa root e não contorna a autorização do Android."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Tentar abrir Definições") { _, _ -> openSystemAuthorization() }
            .show()
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
}
