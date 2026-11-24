package pt.catharsys.digiautostart

import android.app.Activity
import android.content.Context

/** No-op implementation: experimental standby automation is Debug-only. */
object AutomationFeature {
    fun attach(context: Context) = Unit
    fun configure(activity: Activity) = Unit
}
