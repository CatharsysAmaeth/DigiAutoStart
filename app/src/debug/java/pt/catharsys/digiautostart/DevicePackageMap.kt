package pt.catharsys.digiautostart

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.zip.ZipFile

/**
 * Read-only package map for the installed DIGI TV and TV Settings APKs.
 * It records exported components, launcher handlers, OEM Settings handlers,
 * APK paths/splits, DEX/asset strings relevant to navigation, and URI schemes.
 * It never starts another app, changes settings, or injects input.
 */
object DevicePackageMap {
    private const val DIGI = "ro.digionline.tv"
    private val settingsPackages = listOf("com.android.tv.settings", "com.android.settings")
    private val interesting = Regex(
        "(?i)(digitv|digi.?tv|channel|livestream|live.?tv|stream|deeplink|deep.?link|intent|launcher|player|accessibility|tv.?home|home.?screen|ver mais)"
    )
    private val uriPattern = Regex("(?i)\\b([a-z][a-z0-9+.-]{1,24})://[a-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*")

    fun run(activity: Activity) {
        DiagnosticsFeature.record(activity, "device_package_map_started")
        val progress = ProgressDialog.show(
            activity,
            "Mapeamento técnico",
            "A analisar os APKs instalados. Aguarda…",
            true,
            false
        )
        Thread({
            try {
                val report = buildReport(activity)
                DiagnosticsFeature.record(activity, "device_package_map_completed", "characters=${report.length}")
                activity.runOnUiThread {
                    progress.dismiss()
                    if (!activity.isFinishing) showReport(activity, report)
                }
            } catch (error: Throwable) {
                DiagnosticsFeature.record(
                    activity,
                    "device_package_map_failed",
                    "type=${error.javaClass.name};message=${error.message};stack=${Log.getStackTraceString(error).take(3000)}"
                )
                activity.runOnUiThread {
                    progress.dismiss()
                    if (!activity.isFinishing) {
                        AlertDialog.Builder(activity)
                            .setTitle("Falha no mapeamento")
                            .setMessage("A análise foi interrompida. O erro ficou registado nos Diagnósticos (Debug).\n\n${error.javaClass.simpleName}: ${error.message ?: "sem detalhe"}")
                            .setPositiveButton("Fechar", null)
                            .show()
                    }
                }
            }
        }, "dada-device-package-map").start()
    }

    private fun buildReport(activity: Activity): String {
        val pm = activity.packageManager
        val lines = mutableListOf<String>()
        fun add(value: String) { lines += value }

        add("=== DEVICE ===")
        add("model=${Build.MANUFACTURER} ${Build.MODEL}; android=${Build.VERSION.RELEASE}; sdk=${Build.VERSION.SDK_INT}")

        val packages = listOf(DIGI) + settingsPackages
        for (pkg in packages) {
            add("=== PACKAGE $pkg ===")
            val info = try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(
                    pkg,
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                        PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or
                        PackageManager.GET_META_DATA
                )
            } catch (e: Exception) {
                add("package_lookup_error=${e.javaClass.simpleName}:${e.message}")
                continue
            }
            val app = info.applicationInfo
            if (app == null) {
                add("application_info=null")
                continue
            }
            add("version=${info.versionName};versionCode=${if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode}")
            add("app_enabled=${app.enabled};system_app=${(app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0}")
            add("sourceDir=${app.sourceDir};publicSourceDir=${app.publicSourceDir}")
            add("splitSourceDirs=${app.splitSourceDirs?.joinToString("|")}")
            add("targetSdk=${app.targetSdkVersion};minSdk=${app.minSdkVersion}")

            info.activities.orEmpty().forEach { a ->
                add("activity=${a.name};exported=${a.exported};enabled=${a.enabled};permission=${a.permission};launchMode=${a.launchMode};process=${a.processName}")
            }
            info.services.orEmpty().forEach { s ->
                add("service=${s.name};exported=${s.exported};enabled=${s.enabled};permission=${s.permission}")
            }
            info.receivers.orEmpty().forEach { r ->
                add("receiver=${r.name};exported=${r.exported};enabled=${r.enabled};permission=${r.permission}")
            }
            info.providers.orEmpty().forEach { p ->
                add("provider=${p.name};exported=${p.exported};authority=${p.authority};permission=${p.readPermission}")
            }

            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            add("launchIntent=${launchIntent?.component?.flattenToString()};action=${launchIntent?.action}")

            if (pkg == DIGI) {
                val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(pkg)
                @Suppress("DEPRECATION")
                val leanbackMatches = pm.queryIntentActivities(leanback, PackageManager.MATCH_ALL)
                add("leanbackLaunchers=${leanbackMatches.joinToString("|") { "${it.activityInfo.name}:exported=${it.activityInfo.exported}" }}")
            }

            if (pkg == "com.android.tv.settings") {
                val actions = listOf(
                    "android.settings.ACCESSIBILITY_SETTINGS",
                    "android.settings.ACCESSIBILITY_TV_OEM_LINK",
                    "android.settings.ACCESSIBILITY_SHORTCUT_SERVICES_TV_OEM_LINK",
                    "android.settings.ACCESSIBILITY_DETAILS_SETTINGS",
                    "android.settings.ACCESSIBILITY_SHORTCUT_SETTINGS"
                )
                for (action in actions) {
                    val intent = Intent(action).setPackage(pkg)
                    @Suppress("DEPRECATION")
                    val matches = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                    add("settingsRoute=$action;handlers=${matches.joinToString("|") { "${it.activityInfo.name}:exported=${it.activityInfo.exported}:enabled=${it.activityInfo.enabled}" }}")
                }
            }

            val paths = (listOfNotNull(app.sourceDir) + app.splitSourceDirs.orEmpty()).distinct()
            for (path in paths) {
                scanApk(path, add = ::add)
            }
        }

        // Test only URI schemes actually found in the installed DIGI APK's DEX/resources.
        val schemes = lines.flatMap { uriPattern.findAll(it).map { m -> m.groupValues[1].lowercase() } }
            .filter { it !in setOf("http", "https", "content", "file", "mailto") }
            .distinct().take(30)
        add("=== DISCOVERED URI SCHEMES ===")
        if (schemes.isEmpty()) add("No non-standard URI scheme strings found in readable APK content.")
        for (scheme in schemes) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://probe")).setPackage(DIGI)
            @Suppress("DEPRECATION")
            val matches = runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
            add("uriScheme=$scheme;handlers=${matches.joinToString("|") { it.activityInfo.name }}")
        }

        return lines.joinToString("\n").take(26000)
    }

    private fun showReport(activity: Activity, report: String) {
        DiagnosticsFeature.record(activity, "device_package_map", report)
        val view = TextView(activity).apply {
            text = report
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(22, 18, 22, 18)
        }
        AlertDialog.Builder(activity)
            .setTitle("Mapa técnico — DIGI TV e Definições")
            .setView(ScrollView(activity).apply { addView(view) })
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun scanApk(path: String, add: (String) -> Unit) {
        add("apkScan=$path")
        try {
            ZipFile(File(path)).use { zip ->
                val entries = zip.entries().asSequence().filter {
                    it.name.matches(Regex("classes[0-9]*\\.dex")) ||
                        (it.name.startsWith("assets/") && !it.isDirectory)
                }.take(30).toList()
                add("apkEntriesSelected=${entries.size}")
                val found = linkedSetOf<String>()
                var totalBytesRead = 0
                val maxTotalBytes = 12 * 1024 * 1024
                for (entry in entries) {
                    if (totalBytesRead >= maxTotalBytes || found.size >= 100) break
                    if (entry.isDirectory || entry.size == 0L) continue
                    val entryLimit = minOf(4 * 1024 * 1024, maxTotalBytes - totalBytesRead)
                    if (entry.size > entryLimit.toLong()) {
                        add("apkEntrySkipped=${entry.name};size=${entry.size};reason=per_entry_limit")
                        continue
                    }
                    val bytes = zip.getInputStream(entry).use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var remaining = entryLimit
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            remaining -= count
                        }
                        output.toByteArray()
                    }
                    totalBytesRead += bytes.size
                    val raw = bytes.toString(Charsets.ISO_8859_1)
                    for (match in Regex("[\\x20-\\x7E]{5,240}").findAll(raw)) {
                        val value = match.value.trim()
                        if (interesting.containsMatchIn(value)) {
                            found += "${entry.name}:$value"
                            if (found.size >= 100) break
                        }
                    }
                }
                add("apkBytesScanned=$totalBytesRead")
                add("apkRelevantStringsCount=${found.size}")
                found.forEach { add("apkString=$it") }
            }
        } catch (e: Exception) {
            add("apkScanError=${e.javaClass.simpleName}:${e.message}")
        }
    }
}
