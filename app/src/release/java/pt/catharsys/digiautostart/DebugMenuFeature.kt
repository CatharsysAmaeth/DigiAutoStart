package pt.catharsys.digiautostart

import android.app.Activity
import android.widget.LinearLayout

/** Empty Release implementation: no Debug menu is packaged. */
object DebugMenuFeature {
    fun addButtons(activity: Activity, content: LinearLayout, onSaved: () -> Unit) = Unit
}
