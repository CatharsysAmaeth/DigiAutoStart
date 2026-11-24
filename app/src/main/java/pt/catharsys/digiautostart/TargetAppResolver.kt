package pt.catharsys.digiautostart

import android.content.Context
import android.content.Intent

/**
 * Resolves the DIGI TV launcher without requiring QUERY_ALL_PACKAGES.
 * The current official package remains the default; fallback discovery is
 * restricted to Android TV launcher activities and labels containing "digi".
 */
object TargetAppResolver {
    const val DEFAULT_PACKAGE = "ro.digionline.tv"
    private const val PREFS = "digi_autostart"
    private const val KEY_TARGET_PACKAGE = "target_package"

    data class Candidate(val packageName: String, val label: String)

    fun selectedPackage(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val selected = prefs.getString(KEY_TARGET_PACKAGE, null)
        if (!selected.isNullOrBlank() && hasLaunchIntent(context, selected)) return selected

        if (hasLaunchIntent(context, DEFAULT_PACKAGE)) {
            prefs.edit().putString(KEY_TARGET_PACKAGE, DEFAULT_PACKAGE).apply()
            return DEFAULT_PACKAGE
        }

        val matches = candidates(context).filter {
            it.packageName != context.packageName &&
                it.label.contains("digi", ignoreCase = true)
        }
        if (matches.size == 1) {
            prefs.edit().putString(KEY_TARGET_PACKAGE, matches.single().packageName).apply()
            return matches.single().packageName
        }
        return null
    }

    fun candidates(context: Context): List<Candidate> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = context.packageManager.queryIntentActivities(intent, 0)
        return resolved.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            val label = info.loadLabel(context.packageManager)?.toString()?.takeIf { it.isNotBlank() }
                ?: packageName
            Candidate(packageName, label)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    fun select(context: Context, packageName: String) {
        if (hasLaunchIntent(context, packageName)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_TARGET_PACKAGE, packageName).apply()
        }
    }

    fun hasLaunchIntent(context: Context, packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    fun displayName(context: Context, packageName: String?): String {
        if (packageName.isNullOrBlank()) return "Não detectada"
        return try {
            val app = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(app).toString()
        } catch (_: Exception) {
            packageName
        }
    }
}
