package pt.catharsys.digiautostart

import android.app.Activity
import android.widget.Button
import android.widget.LinearLayout

/** Public Release menu: configuration only, without Debug tools. */
object DebugMenuFeature {
    fun addButtons(activity: Activity, content: LinearLayout, onSaved: () -> Unit) {
        val button = Button(activity).apply {
            text = "Configurar"
            textSize = 16f
            isAllCaps = false
            minHeight = (56 * resources.displayMetrics.density).toInt()
            isFocusable = true
            setOnClickListener { AutomationFeature.configure(activity, onSaved) }
        }
        content.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (12 * activity.resources.displayMetrics.density).toInt()
        })
    }
}
