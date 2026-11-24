package pt.catharsys.digiautostart

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val targetPackage = "ro.digionline.tv"
    private val preferences by lazy { getSharedPreferences("digi_autostart", MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var delayValue: TextView

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

        content.addView(text("DigiAutoStart", 30f, true).apply {
            setTextColor(0xFFFFFFFF.toInt())
        })
        content.addView(text("DIGI TV • Assistente de arranque", 16f).apply {
            setTextColor(0xFFB8C7D1.toInt())
        }, margin(top = 4, bottom = 20))

        status = text("", 18f, true).apply {
            setTextColor(0xFF8FE3B0.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(0xFF1D3038.toInt())
        }
        content.addView(status, margin(bottom = 20))

        content.addView(text("Modo pretendido", 20f, true).apply {
            setTextColor(0xFFFFFFFF.toInt())
        }, margin(bottom = 8))

        val modeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val always = radio("Modo A — abrir sempre após sair de standby", 1)
        val last = radio("Modo B — só se DIGI TV era a última aplicação", 2)
        modeGroup.addView(always)
        modeGroup.addView(last)
        modeGroup.check(if (preferences.getInt("mode", 2) == 1) 1 else 2)
        modeGroup.setOnCheckedChangeListener { _, checked ->
            preferences.edit().putInt("mode", if (checked == 1) 1 else 2).apply()
        }
        content.addView(modeGroup, margin(bottom = 18))

        content.addView(text("Atraso pretendido antes de abrir", 18f, true).apply {
            setTextColor(0xFFFFFFFF.toInt())
        })
        delayValue = text("", 16f).apply { setTextColor(0xFFB8C7D1.toInt()) }
        content.addView(delayValue, margin(top = 4))
        val delay = SeekBar(this).apply {
            max = 30
            progress = preferences.getInt("delay_seconds", 5).coerceIn(0, 30)
            contentDescription = "Atraso em segundos"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    delayValue.text = "$value segundos"
                    if (fromUser) preferences.edit().putInt("delay_seconds", value).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        content.addView(delay, margin(bottom = 20))
        delayValue.text = "${delay.progress} segundos"

        content.addView(button("Testar abertura da DIGI TV") { launchDigiTv() }, margin(bottom = 12))
        content.addView(button("Atualizar estado") { refreshStatus() }, margin(bottom = 20))

        if (BuildConfig.DEBUG) {
            content.addView(button("Configurar automação (teste)") { AutomationFeature.configure(this) }, margin(bottom = 12))
            content.addView(button("Verificar atualizações") { DebugUpdateFeature.check(this) }, margin(bottom = 12))
            content.addView(button("Diagnósticos (Debug)") { DiagnosticsFeature.show(this) }, margin(bottom = 20))
        }

        content.addView(text("Estado desta versão", 20f, true).apply {
            setTextColor(0xFFFFFFFF.toInt())
        }, margin(bottom = 8))
        content.addView(text(
            "Esta versão inclui um protótipo experimental de deteção de standby/retoma e tentativa de abertura automática da DIGI TV. Requer configuração explícita das permissões no botão «Configurar automação (teste)» e ainda precisa de validação nesta box. O recetor deixa de funcionar se o Android terminar o processo da aplicação.",
            16f
        ).apply { setTextColor(0xFFD2DCE2.toInt()) }, margin(bottom = 14))

        content.addView(text(
            "A aplicação não altera o launcher, firmware, bootloader nem partições do equipamento.",
            14f
        ).apply { setTextColor(0xFF8FA3AE.toInt()) })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
    }

    private fun refreshStatus() {
        status.text = if (packageManager.getLaunchIntentForPackage(targetPackage) != null) {
            "DIGI TV instalada • pronta para teste"
        } else {
            "DIGI TV não encontrada • confirma que está instalada"
        }
        DiagnosticsFeature.record(this, "target_status_checked", "launch_intent_available=${packageManager.getLaunchIntentForPackage(targetPackage) != null}")
    }

    private fun launchDigiTv() {
        DiagnosticsFeature.record(this, "manual_launch_requested")
        val intent = packageManager.getLaunchIntentForPackage(targetPackage)
        if (intent == null) {
            refreshStatus()
            DiagnosticsFeature.record(this, "manual_launch_failed", "reason=no_launch_intent")
            Toast.makeText(this, "Não foi encontrada uma atividade de arranque da DIGI TV.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(intent)
            DiagnosticsFeature.record(this, "manual_launch_start_activity_returned")
        } catch (error: Exception) {
            DiagnosticsFeature.record(this, "manual_launch_exception", "exception=${error.javaClass.simpleName}")
            Toast.makeText(this, "Não foi possível abrir a DIGI TV: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun radio(value: String, idValue: Int) = RadioButton(this).apply {
        id = idValue
        text = value
        textSize = 16f
        setTextColor(0xFFFFFFFF.toInt())
        minHeight = dp(54)
        isFocusable = true
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        textSize = 16f
        isAllCaps = false
        minHeight = dp(56)
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun margin(bottom: Int = 0, top: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            this.bottomMargin = dp(bottom)
            this.topMargin = dp(top)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
