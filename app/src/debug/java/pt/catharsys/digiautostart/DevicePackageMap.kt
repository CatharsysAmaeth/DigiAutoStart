package pt.catharsys.digiautostart

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
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
 * Package metadata and URI handler results are kept ahead of noisy bundle strings.
 * It never starts another app, changes settings, or injects input.
 */
object DevicePackageMap {
    private const val DIGI = "ro.digionline.tv"
    private val settingsPackages = listOf("com.android.tv.settings", "com.android.settings")
    private val interesting = Regex(
        "(?i)(digitv|digi.?tv|channel|livestream|live.?tv|stream|deeplink|deep.?link|intent|player|route|navigation|epg|catch.?up|replay)"
    )
    private val uriPattern = Regex("(?i)\\b([a-z][a-z0-9+.-]{1,24})://[a-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*")

    fun run(activity: Activity) {
        DiagnosticsFeature.record(activity, "device_package_map_started")
        val progress = ProgressDialog.show(
            activity, "Mapeamento técnico", "A analisar os APKs instalados. Aguarda…", true, false
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
                    activity, "device_package_map_failed",
                    "type=${error.javaClass.name};message=${error.message};stack=${Log.getStackTraceString(error).take(3000)}"
                )
                activity.runOnUiThread {
                    progress.dismiss()
                    if (!activity.isFinishing) {
                        AlertDialog.Builder(activity)
                            .setTitle("Falha no mapeamento")
                            .setMessage("A análise foi interrompida. O erro ficou registado nos Diagnósticos (Debug).\n\n${error.javaClass.simpleName}: ${error.message ?: "sem detalhe"}")
                            .setPositiveButton("Fechar", null).show()
                    }
                }
            }
        }, "dada-device-package-map").start()
    }

    private fun buildReport(activity: Activity): String {
        val pm = activity.packageManager
        val metadata = mutableListOf<String>()
        val scanLines = mutableListOf<String>()
        val discoveredUris = linkedSetOf<String>()
        fun meta(value: String) { metadata += value }

        meta("=== DEVICE ===")
        meta("model=${Build.MANUFACTURER} ${Build.MODEL};android=${Build.VERSION.RELEASE};sdk=${Build.VERSION.SDK_INT}")
        meta("processPackage=${activity.packageName}")

        // Always collect all package metadata first; no APK string scan can crowd it out.
        for (pkg in listOf(DIGI) + settingsPackages) {
            meta("=== PACKAGE $pkg ===")
            val info = try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(
                    pkg, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                        PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA
                )
            } catch (e: Exception) {
                meta("package_lookup_error=${e.javaClass.simpleName}:${e.message}")
                continue
            }
            val app = info.applicationInfo
            if (app == null) {
                meta("application_info=null")
                continue
            }
            meta("version=${info.versionName};versionCode=${if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode}")
            meta("app_enabled=${app.enabled};system_app=${(app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0}")
            meta("sourceDir=${app.sourceDir};publicSourceDir=${app.publicSourceDir}")
            meta("splitSourceDirs=${app.splitSourceDirs?.joinToString("|")}")
            meta("targetSdk=${app.targetSdkVersion};minSdk=${app.minSdkVersion}")
            info.activities.orEmpty().forEach { a ->
                meta("activity=${a.name};exported=${a.exported};enabled=${a.enabled};permission=${a.permission};launchMode=${a.launchMode};process=${a.processName}")
            }
            info.services.orEmpty().forEach { s ->
                meta("service=${s.name};exported=${s.exported};enabled=${s.enabled};permission=${s.permission}")
            }
            info.receivers.orEmpty().forEach { r ->
                meta("receiver=${r.name};exported=${r.exported};enabled=${r.enabled};permission=${r.permission}")
            }
            info.providers.orEmpty().forEach { p ->
                meta("provider=${p.name};exported=${p.exported};authority=${p.authority};permission=${p.readPermission}")
            }
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            meta("launchIntent=${launchIntent?.component?.flattenToString()};action=${launchIntent?.action}")

            if (pkg == DIGI) {
                val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(pkg)
                @Suppress("DEPRECATION")
                val matches = pm.queryIntentActivities(leanback, PackageManager.MATCH_ALL)
                meta("leanbackLaunchers=${matches.joinToString("|") { "${it.activityInfo.name}:exported=${it.activityInfo.exported}" }}")
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
                    meta("settingsRoute=$action;handlers=${matches.joinToString("|") { "${it.activityInfo.name}:exported=${it.activityInfo.exported}:enabled=${it.activityInfo.enabled}" }}")
                }
            }
        }

        // Scan only DIGI's installed APKs. Capture URI literals separately from display strings.
        val digiInfo = runCatching {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(DIGI, 0)
        }.getOrNull()
        if (digiInfo == null) {
            scanLines += "DIGI package unavailable for APK scan."
        } else {
            val apkPaths = (listOfNotNull(digiInfo.sourceDir) + digiInfo.splitSourceDirs.orEmpty()).distinct()
            for (path in apkPaths) scanApk(path, scanLines, discoveredUris)
        }

        // Resolve scheme and exact URI candidates using PackageManager only; never launch them.
        meta("=== DIGI URI HANDLER CHECKS (QUERY ONLY) ===")
        val schemes = discoveredUris.mapNotNull { uriPattern.find(it)?.groupValues?.get(1)?.lowercase() }
            .filter { it !in setOf("http", "https", "content", "file", "mailto") }
            .distinct().take(20)
        if (schemes.isEmpty()) meta("No non-standard URI schemes extracted from scanned DIGI APK content.")
        for (scheme in schemes) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://probe")).setPackage(DIGI)
            @Suppress("DEPRECATION")
            val matches = runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
            meta("uriScheme=$scheme;handlers=${matches.joinToString("|") { it.activityInfo.name }}")
        }
        val candidates = linkedSetOf(
            "digitv://ia-livestream/1",
            "digitv://api/pin-validate"
        )
        discoveredUris.filter { it.startsWith("digitv://", true) }.take(12).forEach { candidates += it }
        for (candidate in candidates.take(15)) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(candidate)).setPackage(DIGI)
            @Suppress("DEPRECATION")
            val matches = runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
            meta("uriCandidate=$candidate;handlers=${matches.joinToString("|") { it.activityInfo.name }}")
        }

        val output = StringBuilder()
        (metadata + listOf("=== DIGI APK STRING SAMPLE (FILTERED) ===") + scanLines).forEach { line ->
            if (output.length + line.length + 1 <= 25000) output.append(line).append('\n')
        }
        if (output.length >= 25000) output.append("[report capped after prioritised metadata and filtered DIGI strings]\n")
        return output.toString()
    }

    private fun scanApk(path: String, output: MutableList<String>, discoveredUris: MutableSet<String>) {
        try {
            ZipFile(File(path)).use { zip ->
                val entries = zip.entries().asSequence().filter { entry ->
                    !entry.isDirectory && (
                        entry.name.matches(Regex("classes[0-9]*\\.dex")) ||
                            entry.name.startsWith("assets/") &&
                            (entry.name.contains("bundle", true) || entry.name.endsWith(".js") ||
                                entry.name.endsWith(".json") || entry.name.endsWith(".map"))
                        )
                }.take(18).toList()
                output += "apkScan=${File(path).name};selectedEntries=${entries.size}"
                val found = linkedSetOf<String>()
                var totalBytesRead = 0
                val maxTotalBytes = 10 * 1024 * 1024
                for (entry in entries) {
                    if (totalBytesRead >= maxTotalBytes || found.size >= 45) break
                    if (entry.size <= 0L) continue
                    val entryLimit = minOf(3 * 1024 * 1024, maxTotalBytes - totalBytesRead)
                    if (entry.size > entryLimit.toLong()) {
                        output += "apkEntrySkipped=${entry.name};size=${entry.size};reason=per_entry_limit"
                        continue
                    }
                    val bytes = zip.getInputStream(entry).use { input ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var remaining = entryLimit
                        while (remaining > 0) {
                            val count = input.read(chunk, 0, minOf(chunk.size, remaining))
                            if (count < 0) break
                            buffer.write(chunk, 0, count)
                            remaining -= count
                        }
                        buffer.toByteArray()
                    }
                    totalBytesRead += bytes.size
                    val raw = bytes.toString(Charsets.ISO_8859_1)
                    uriPattern.findAll(raw).forEach { match ->
                        val uri = match.value.trimEnd('.', ',', ';', ')', ']', '}')
                        if (uri.length in 8..300) discoveredUris += uri
                    }
                    for (match in Regex("[\\x20-\\x7E]{6,220}").findAll(raw)) {
                        val value = match.value.trim()
                        if (interesting.containsMatchIn(value)) {
                            found += "${entry.name}:${value}"
                            if (found.size >= 45) break
                        }
                    }
                }
                output += "apkBytesScanned=$totalBytesRead;relevantStrings=${found.size};uriLiterals=${discoveredUris.size}"
                found.forEach { output += "apkString=$it" }
            }
        } catch (e: Exception) {
            output += "apkScanError=${File(path).name};${e.javaClass.simpleName}:${e.message}"
        }
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
}
