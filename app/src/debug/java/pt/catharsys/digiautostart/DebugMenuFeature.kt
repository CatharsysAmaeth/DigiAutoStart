package pt.catharsys.digiautostart

import android.app.Activity
import android.widget.Button
import android.widget.LinearLayout

/** Debug-only menu. It is not compiled into the normal Release APK. */
object DebugMenuFeature {
    fun addButtons(activity: Activity, content: LinearLayout, onSaved: () -> Unit) {
        content.addView(button(activity, "Configurar") {
            AutomationFeature.configure(activity, onSaved)
        }, params(activity, 12))
        content.addView(button(activity, "Investigar DIGI TV (Debug)") {
            DigiTvProbeFeature.show(activity)
        }, params(activity, 12))
        content.addView(button(activity, "Diagnosticar Acessibilidade (Debug)") {
            AccessibilityServiceDiagnostic.run(activity)
        }, params(activity, 12))
        content.addView(button(activity, "Verificar atualizações") {
            DebugUpdateFeature.check(activity)
        }, params(activity, 12))
        content.addView(button(activity, "Diagnósticos (Debug)") {
            DiagnosticsFeature.show(activity)
        }, params(activity, 20))
    }

    private fun button(activity: Activity, value: String, action: () -> Unit) =
        Button(activity).apply {
            text = value
            textSize = 16f
            isAllCaps = false
            minHeight = (56 * resources.displayMetrics.density).toInt()
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun params(activity: Activity, bottom: Int) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (bottom * activity.resources.displayMetrics.density).toInt() }
}
