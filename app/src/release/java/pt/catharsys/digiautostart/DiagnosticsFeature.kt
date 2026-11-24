package pt.catharsys.digiautostart

import android.app.Activity
import android.content.Context

/** No-op implementation: diagnostic code and UI are absent from Release. */
object DiagnosticsFeature {
    fun attach(context: Context) = Unit
    fun detach(context: Context) = Unit
    fun record(context: Context, event: String, details: String = "") = Unit
    fun show(activity: Activity) = Unit
}
