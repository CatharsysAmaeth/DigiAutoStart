package pt.catharsys.digiautostart

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Debug-only raw evidence collector. Copies original APK entries without
 * decompiling or executing them. All output stays in app-private storage
 * until the user explicitly sends a diagnostic report.
 */
internal object ApkEvidenceExtractor {
    private const val DIGI = "ro.digionline.tv"
    private const val TV_SETTINGS = "com.android.tv.settings"
    private const val MAX_TOTAL_BYTES = 32L * 1024L * 1024L
    private const val MAX_ENTRY_BYTES = 20L * 1024L * 1024L

    fun extract(context: Context): String {
        val root = File(context.filesDir, "diagnostics/evidence")
        if (root.exists() && !root.deleteRecursively()) {
            error("Não foi possível limpar a recolha anterior.")
        }
        if (!root.mkdirs() && !root.isDirectory) error("Não foi possível criar a pasta de evidência.")

        val lines = mutableListOf<String>()
        var remaining = MAX_TOTAL_BYTES
        val budget: (Long) -> Long = { bytes ->
            remaining -= bytes
            remaining
        }
        extractPackage(context, DIGI, "digi", root, lines, budget)
        extractPackage(context, TV_SETTINGS, "zte-tv-settings", root, lines, budget)

        val index = File(root, "evidence-index.txt")
        index.writeText(
            "DAdA raw APK evidence\n" +
                "mode=read-only;no APK code executed;no settings changed\n" +
                "max_total_bytes=$MAX_TOTAL_BYTES\n" +
                lines.joinToString("\n") + "\n"
        )
        val copied = lines.count { it.startsWith("copied=") }
        val total = lines.filter { it.startsWith("copied=") }
            .sumOf { it.substringAfter("bytes=").substringBefore(";").toLongOrNull() ?: 0L }
        return "evidenceFiles=$copied;evidenceBytes=$total;index=${index.length()};root=files/diagnostics/evidence"
    }

    private fun extractPackage(
        context: Context,
        packageName: String,
        folder: String,
        root: File,
        lines: MutableList<String>,
        consumeBudget: (Long) -> Long
    ) {
        val app = try {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        } catch (error: Exception) {
            lines += "packageUnavailable=$packageName;${error.javaClass.simpleName}"
            return
        }

        val apkPaths = (listOfNotNull(app.sourceDir) + app.splitSourceDirs.orEmpty()).distinct()
        lines += "package=$packageName;apkCount=${apkPaths.size}"
        for (apkPath in apkPaths) {
            val apkFile = File(apkPath)
            val apkLabel = apkFile.nameWithoutExtension.replace(Regex("[^A-Za-z0-9._-]"), "_")
            try {
                ZipFile(apkFile).use { zip ->
                    val entries = zip.entries().asSequence()
                        .filter { !it.isDirectory && isRelevant(packageName, it.name) }
                        .sortedBy { it.name }
                        .toList()
                    lines += "apk=${apkFile.name};selectedEntries=${entries.size}"
                    for (entry in entries) {
                        val size = entry.size
                        if (size <= 0L) {
                            lines += "skipped=${apkFile.name}:${entry.name};reason=unknown_or_empty_size"
                            continue
                        }
                        if (size > MAX_ENTRY_BYTES) {
                            lines += "skipped=${apkFile.name}:${entry.name};bytes=$size;reason=entry_limit"
                            continue
                        }
                        val remaining = consumeBudget(0L)
                        if (size > remaining) {
                            lines += "skipped=${apkFile.name}:${entry.name};bytes=$size;reason=total_limit"
                            continue
                        }
                        val relative = "$folder/$apkLabel/${entry.name}"
                        if (relative.split('/').any { it == ".." }) {
                            lines += "skipped=${apkFile.name}:${entry.name};reason=unsafe_path"
                            continue
                        }
                        val destination = File(root, relative)
                        destination.parentFile?.mkdirs()
                        val digest = MessageDigest.getInstance("SHA-256")
                        var copied = 0L
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(destination).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    copied += count
                                    if (copied > MAX_ENTRY_BYTES || copied > size) {
                                        error("APK entry exceeded declared size: ${entry.name}")
                                    }
                                    digest.update(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        if (copied != size) {
                            destination.delete()
                            lines += "failed=${apkFile.name}:${entry.name};expected=$size;actual=$copied"
                            continue
                        }
                        val sha = digest.digest().joinToString("") { "%02x".format(it) }
                        lines += "copied=$relative;bytes=$copied;sha256=$sha;apk=${apkFile.name};compressed=${entry.compressedSize}"
                        consumeBudget(copied)
                    }
                }
            } catch (error: Exception) {
                lines += "apkReadError=${apkFile.name};${error.javaClass.simpleName}:${error.message.orEmpty().take(180)}"
            }
        }
    }

    private fun isRelevant(packageName: String, name: String): Boolean {
        if (name.matches(Regex("classes[0-9]*\\.dex"))) return true
        if (name == "AndroidManifest.xml" || name == "resources.arsc") return true
        if (packageName == DIGI) {
            return name == "assets/index.android.bundle" ||
                (name.startsWith("assets/") && (
                    name.endsWith(".js", true) || name.endsWith(".json", true) ||
                        name.endsWith(".map", true)
                    ))
        }
        if (packageName == TV_SETTINGS) {
            return name.startsWith("res/xml/") && name.endsWith(".xml", true)
        }
        return false
    }
}
