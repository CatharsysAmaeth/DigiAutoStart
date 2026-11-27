package pt.catharsys.digiautostart

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Base64
import android.widget.Toast
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Debug-only opt-in diagnostics. Reports are ordinary ZIP files uploaded only
 * after the user explicitly chooses "Criar e enviar relatório".
 */
object DiagnosticsFeature {
    private const val PREFS = "digi_autostart_diagnostics"
    private const val MAX_LOG_BYTES = 1024L * 1024L
    private const val MAX_EVIDENCE_BYTES = 32L * 1024L * 1024L
    private const val MAX_ARCHIVE_BYTES = 48L * 1024L * 1024L

    private var receiver: BroadcastReceiver? = null

    fun attach(context: Context) {
        if (!isCapturing(context) || receiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        val instance = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                record(ctx, "screen_broadcast_received", "action=${intent.action}")
            }
        }
        receiver = instance
        if (Build.VERSION.SDK_INT >= 33) {
            context.applicationContext.registerReceiver(instance, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.applicationContext.registerReceiver(instance, filter)
        }
        record(context, "screen_receiver_attached")
    }

    fun detach(context: Context) {
        val current = receiver ?: return
        try {
            context.applicationContext.unregisterReceiver(current)
        } catch (_: IllegalArgumentException) {
        }
        receiver = null
        record(context, "screen_receiver_detached")
    }

    fun isCapturing(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)

    fun record(context: Context, event: String, details: String = "") {
        if (!isCapturing(context)) return
        val file = logFile(context)
        if (file.exists() && file.length() > MAX_LOG_BYTES) {
            val previous = File(file.parentFile, "events.previous.jsonl")
            if (previous.exists()) previous.delete()
            file.renameTo(previous)
        }
        val safeDetails = redact(details).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
        val safeEvent = redact(event).replace("\"", "")
        val line = """{"time":"${isoNow()}","event":"$safeEvent","details":"$safeDetails"}""" + "\n"
        file.parentFile?.mkdirs()
        file.appendText(line)
    }

    fun show(activity: Activity) {
        val state = if (isCapturing(activity)) "ATIVA" else "inativa"
        val options = arrayOf(
            if (isCapturing(activity)) "Parar recolha" else "Iniciar recolha",
            "Criar e enviar relatório",
            "Apagar registos"
        )
        AlertDialog.Builder(activity)
            .setTitle("Diagnósticos — recolha $state")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val enabled = !isCapturing(activity)
                        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putBoolean("enabled", enabled).apply()
                        if (enabled) attach(activity.applicationContext) else detach(activity.applicationContext)
                        Toast.makeText(activity, if (enabled) "Recolha iniciada." else "Recolha parada.", Toast.LENGTH_SHORT).show()
                    }
                    1 -> createAndUpload(activity)
                    2 -> {
                        logFile(activity).delete()
                        File(logFile(activity).parentFile, "events.previous.jsonl").delete()
                        Toast.makeText(activity, "Registos apagados.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun createAndUpload(activity: Activity) {
        val source = logFile(activity)
        if (!source.exists() || source.length() == 0L) {
            Toast.makeText(activity, "Ainda não existem eventos. Inicia a recolha e reproduz o problema.", Toast.LENGTH_LONG).show()
            return
        }
        val token = secret(BuildConfig.DADA_PRIVATE_TOKEN_B64)
        val repository = secret(BuildConfig.DADA_PRIVATE_REPOSITORY_B64)
        if (token.isBlank() || !repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            Toast.makeText(activity, "A configuração do canal interno desta compilação está incompleta.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(activity, "A criar e enviar relatório…", Toast.LENGTH_LONG).show()
        Thread {
            val message = try {
                val exportDir = File(activity.cacheDir, "diagnostics").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val evidenceRoot = File(activity.filesDir, "diagnostics/evidence")
                val evidenceFiles = if (evidenceRoot.isDirectory) {
                    evidenceRoot.walkTopDown().filter { it.isFile }.sortedBy { it.absolutePath }.toList()
                } else emptyList()
                val evidenceBytes = evidenceFiles.sumOf { it.length() }
                if (evidenceBytes > MAX_EVIDENCE_BYTES) {
                    error("A evidência excede o limite seguro de 32 MiB; não foi enviada.")
                }

                val metadata = File(exportDir, "device-summary.txt")
                metadata.writeText(
                    "DigiAutoStart diagnostics\n" +
                        "created_at=${isoNow()}\n" +
                        "app_version=${BuildConfig.VERSION_NAME}\n" +
                        "android_sdk=${Build.VERSION.SDK_INT}\n" +
                        "android_release=${Build.VERSION.RELEASE}\n" +
                        "device_model=${redact(Build.MODEL)}\n" +
                        "manufacturer=${redact(Build.MANUFACTURER)}\n" +
                        "brand=${redact(Build.BRAND)}\n" +
                        "device=${redact(Build.DEVICE)}\n" +
                        "product=${redact(Build.PRODUCT)}\n" +
                        "build_fingerprint=${redact(Build.FINGERPRINT)}\n" +
                        "security_patch=${Build.VERSION.SECURITY_PATCH}\n" +
                        "supported_abis=${Build.SUPPORTED_ABIS.joinToString(",")}\n" +
                        "target_package=${TargetAppResolver.selectedPackage(activity) ?: "not_detected"}\n" +
                        "evidence_files=${evidenceFiles.size}\n" +
                        "evidence_bytes=$evidenceBytes\n" +
                        "screen_broadcasts_are_observations_not_proof_of_standby_or_wake\n"
                )

                val zip = File(exportDir, "DigiAutoStart-$stamp.zip")
                ZipOutputStream(FileOutputStream(zip)).use { output ->
                    addZipEntry(output, "events.jsonl", redact(source.readText()))
                    val previous = File(source.parentFile, "events.previous.jsonl")
                    if (previous.exists() && previous.length() > 0L) {
                        addZipEntry(output, "events.previous.jsonl", redact(previous.readText()))
                    }
                    addZipEntry(output, "device-summary.txt", redact(metadata.readText()))
                    addEvidenceEntries(output, evidenceRoot, evidenceFiles)
                }
                if (zip.length() > MAX_ARCHIVE_BYTES) {
                    error("O relatório excede 48 MiB; não foi enviado.")
                }

                val encoded = Base64.encodeToString(zip.readBytes(), Base64.NO_WRAP)
                val body = JSONObject()
                    .put("message", "Add DigiAutoStart diagnostic report")
                    .put("content", encoded)
                    .toString()
                val endpoint = "https://api.github.com/repos/$repository/contents/reports/${zip.name}"
                val status = githubRequest(token, endpoint, body)
                if (status !in 200..299) error("GitHub HTTP $status")
                "Relatório enviado com sucesso para o canal interno."
            } catch (error: Exception) {
                "Falha ao enviar relatório: ${error.message ?: error.javaClass.simpleName}"
            }
            activity.runOnUiThread {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun addZipEntry(output: ZipOutputStream, name: String, content: String) {
        output.putNextEntry(ZipEntry(name))
        output.write(content.toByteArray(Charsets.UTF_8))
        output.closeEntry()
    }

    private fun addEvidenceEntries(output: ZipOutputStream, root: File, files: List<File>) {
        if (files.isEmpty()) return
        var total = 0L
        for (file in files) {
            total += file.length()
            if (total > MAX_EVIDENCE_BYTES) error("A evidência excede o limite seguro.")
            val relative = file.relativeTo(root).invariantSeparatorsPath
            if (relative.split('/').any { it == ".." }) error("Caminho de evidência inválido.")
            output.putNextEntry(ZipEntry("evidence/$relative"))
            file.inputStream().use { input -> input.copyTo(output) }
            output.closeEntry()
        }
    }

    /** Remove common credential, account-contact and phone-number patterns before export. */
    private fun redact(input: String): String {
        var value = input
        value = value.replace(Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s\\\",]+"), "\$1[REDACTED]")
        value = value.replace(Regex("(?i)\\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b"), "[REDACTED_TOKEN]")
        value = value.replace(
            Regex("(?i)\\b(password|passwd|token|access[_-]?token|refresh[_-]?token|session(?:[_-]?id)?|api[_-]?key|pin|otp)\\s*[:=]\\s*(\"[^\"]*\"|'[^']*'|[^,\\s}]+)"),
            "\$1=[REDACTED]"
        )
        value = value.replace(Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"), "[REDACTED_EMAIL]")
        value = value.replace(Regex("(?<![A-Za-z0-9])(?:\\+?\\d[\\d .()/-]{7,}\\d)(?![A-Za-z0-9])"), "[REDACTED_PHONE]")
        return value
    }

    private fun secret(encoded: String): String = try {
        if (encoded.isBlank()) "" else String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        ""
    }

    private fun githubRequest(token: String, endpoint: String, body: String): Int {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "DigiAutoStart-Debug")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            stream?.use { it.copyTo(ByteArrayOutputStream()) }
            return status
        } finally {
            connection.disconnect()
        }
    }

    private fun logFile(context: Context): File =
        File(context.filesDir, "diagnostics/events.jsonl").apply { parentFile?.mkdirs() }

    private fun isoNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
}
