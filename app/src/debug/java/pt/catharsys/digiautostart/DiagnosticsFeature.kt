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
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-only diagnostics. Captures only DigiAutoStart-owned events and the
 * two screen broadcasts observed while the app process is alive.
 */
object DiagnosticsFeature {
    private const val PREFS = "digi_autostart_diagnostics"
    private const val MAX_LOG_BYTES = 1024L * 1024L
    
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

    fun record(context: Context, event: String, details: String = "") {
        if (!isCapturing(context)) return
        val file = logFile(context)
        if (file.exists() && file.length() > MAX_LOG_BYTES) {
            val previous = File(file.parentFile, "events.previous.jsonl")
            if (previous.exists()) previous.delete()
            file.renameTo(previous)
        }
        val safeDetails = details.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
        val line = """{"time":"${isoNow()}","event":"$event","details":"$safeDetails"}""" + "\n"
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
        val password = secret(BuildConfig.DADA_DEBUG_REPORT_PASSWORD_B64)
        val repository = secret(BuildConfig.DADA_PRIVATE_REPOSITORY_B64)
        if (token.isBlank() || password.isBlank() || !repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            Toast.makeText(activity, "A configuração do canal interno desta compilação está incompleta.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(activity, "A criar e enviar relatório encriptado…", Toast.LENGTH_LONG).show()
        Thread {
            val message = try {
                val exportDir = File(activity.cacheDir, "diagnostics").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val metadata = File(exportDir, "device-summary.txt")
                metadata.writeText(
                    "DigiAutoStart diagnostics\n" +
                        "created_at=${isoNow()}\n" +
                        "app_version=${BuildConfig.VERSION_NAME}\n" +
                        "android_sdk=${Build.VERSION.SDK_INT}\n" +
                        "android_release=${Build.VERSION.RELEASE}\n" +
                        "device_model=${Build.MODEL}\n" +
                        "manufacturer=${Build.MANUFACTURER}\n" +
                        "target_package=${TargetAppResolver.selectedPackage(activity) ?: "not_detected"}\n" +
                        "screen_broadcasts_are_observations_not_proof_of_standby_or_wake\n"
                )
                val zip = File(exportDir, "DigiAutoStart-$stamp-encrypted.zip")
                val zipFile = ZipFile(zip, password.toCharArray())
                zipFile.addFile(source, encryptedParameters("events.jsonl"))
                val previous = File(source.parentFile, "events.previous.jsonl")
                if (previous.exists() && previous.length() > 0L) {
                    zipFile.addFile(previous, encryptedParameters("events.previous.jsonl"))
                }
                zipFile.addFile(metadata, encryptedParameters("device-summary.txt"))

                val encoded = Base64.encodeToString(zip.readBytes(), Base64.NO_WRAP)
                val body = JSONObject()
                    .put("message", "Add encrypted DigiAutoStart diagnostic report")
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

    private fun encryptedParameters(name: String) = ZipParameters().apply {
        fileNameInZip = name
        compressionMethod = CompressionMethod.DEFLATE
        isEncryptFiles = true
        encryptionMethod = EncryptionMethod.AES
        aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
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

    private fun isCapturing(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)

    private fun logFile(context: Context): File =
        File(context.filesDir, "diagnostics/events.jsonl").apply { parentFile?.mkdirs() }

    private fun isoNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
}
