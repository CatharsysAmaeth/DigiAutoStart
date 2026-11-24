package pt.catharsys.digiautostart

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val preferences by lazy { getSharedPreferences("digi_autostart", MODE_PRIVATE) }
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticsFeature.record(this, "activity_created")
        render()
        refreshStatus()
    }

    override fun onStart() {
        super.onStart()
        DiagnosticsFeature.attach(applicationContext)
        AutomationFeature.startIfEnabled(applicationContext)
        DiagnosticsFeature.record(this, "activity_started")
    }

    override fun onResume() {
        super.onResume()
        DiagnosticsFeature.record(this, "activity_resumed")
        if (::status.isInitialized) refreshStatus()
    }

    override fun onPause() {
        DiagnosticsFeature.record(this, "activity_paused")
        super.onPause()
    }

    override fun onStop() {
        DiagnosticsFeature.record(this, "activity_stopped")
        super.onStop()
    }

    private fun render() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
            setBackgroundColor(0xFF101820.toInt())
        }
        content.addView(text("DAdA", 34f, true).apply { setTextColor(0xFFFFFFFF.toInt()) })
        val buildLabel = "${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEBUG) " • DEBUG" else ""
        content.addView(text("DIGI TV · Assistente de arranque · $buildLabel", 15f).apply {
            setTextColor(0xFFB8DDF2.toInt())
        }, margin(top = 4, bottom = 20))

        status = text("", 18f, true).apply {
            setTextColor(0xFF8FE3B0.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(0xFF1D3038.toInt())
        }
        content.addView(status, margin(bottom = 20))

        if (BuildConfig.DEBUG) {
            content.addView(button("Configurar") { AutomationFeature.configure(this) }, margin(bottom = 12))
            content.addView(button("Verificar atualizações") { DebugUpdateFeature.check(this) }, margin(bottom = 12))
            content.addView(button("Diagnósticos (Debug)") { DiagnosticsFeature.show(this) }, margin(bottom = 20))
        }

        content.addView(text("Sobre o DAdA", 20f, true).apply { setTextColor(0xFFFFFFFF.toInt()) }, margin(bottom = 8))
        content.addView(text(
            "Assistente de arranque da DIGI TV. A configuração reúne as opções de abertura ao ligar a box, reabertura após standby, permissões e seleção da aplicação. A deteção automática mantém a aplicação oficial como padrão.",
            16f
        ).apply { setTextColor(0xFFD2DCE2.toInt()) }, margin(bottom = 14))
        content.addView(text(
            "O DAdA não altera o launcher, firmware, bootloader nem partições do equipamento.",
            14f
        ).apply { setTextColor(0xFF8FA3AE.toInt()) })

        setContentView(ScrollView(this).apply { isFillViewport = true; addView(content) })
    }

    private fun refreshStatus() {
        val target = TargetAppResolver.selectedPackage(this)
        val monitor = preferences.getBoolean("automation_enabled", false)
        val boot = preferences.getBoolean("launch_digi_on_boot", false)
        status.text = buildString {
            append("Aplicação: ")
            append(TargetAppResolver.displayName(this@MainActivity, target))
            append("\nAutomação após standby: ")
            append(if (monitor) "ativa" else "desativada")
            append("\nAbrir ao ligar a box: ")
            append(if (boot) "ativo" else "desativado")
        }
        DiagnosticsFeature.record(this, "target_status_checked",
            "target_package=${target ?: "none"},monitor_enabled=$monitor,launch_on_boot=$boot")
    }

    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        textSize = 16f
        isAllCaps = false
        minHeight = dp(56)
        isFocusable = true
        setOnClickListener { action(); refreshStatus() }
    }

    private fun margin(bottom: Int = 0, top: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            this.bottomMargin = dp(bottom)
            this.topMargin = dp(top)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
