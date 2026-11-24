package pt.catharsys.digiautostart

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
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
    private const val TARGET_PACKAGE = "ro.digionline.tv"
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
            context.applicationContext.registerReceiver(instance, filter, Context.RECEIVER_NOT_EXPORTED)
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
            "Exportar ZIP encriptado",
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
                        record(activity, if (enabled) "capture_started" else "capture_stopped")
                        if (enabled) attach(activity.applicationContext) else detach(activity.applicationContext)
                        Toast.makeText(activity, if (enabled) "Recolha iniciada." else "Recolha parada.", Toast.LENGTH_SHORT).show()
                    }
                    1 -> askPasswordAndExport(activity)
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

    private fun askPasswordAndExport(activity: Activity) {
        val input = EditText(activity).apply {
            hint = "Password acordada"
            inputType = 129 // textPassword
            setSingleLine(true)
        }
        AlertDialog.Builder(activity)
            .setTitle("Encriptar relatório")
            .setMessage("Introduz a password acordada. Não fica guardada na aplicação.")
            .setView(input)
            .setPositiveButton("Criar ZIP") { _, _ ->
                val password = input.text?.toString().orEmpty()
                if (password.isBlank()) {
                    Toast.makeText(activity, "A password não pode estar vazia.", Toast.LENGTH_LONG).show()
                } else {
                    try {
                        exportEncrypted(activity, password)
                    } catch (error: Exception) {
                        Toast.makeText(activity, "Falha ao criar relatório: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun exportEncrypted(activity: Activity, password: String) {
        val source = logFile(activity)
        if (!source.exists() || source.length() == 0L) {
            Toast.makeText(activity, "Ainda não existem eventos. Inicia a recolha e reproduz o problema.", Toast.LENGTH_LONG).show()
            return
        }
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
                "target_package=$TARGET_PACKAGE\n" +
                "screen_broadcasts_are_observations_not_proof_of_standby_or_wake\n"
        )
        val zip = File(exportDir, "DigiAutoStart-$stamp-encrypted.zip")
        val parameters = ZipParameters().apply {
            fileNameInZip = "events.jsonl"
            compressionMethod = CompressionMethod.DEFLATE
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
        val zipFile = ZipFile(zip, password.toCharArray())
        zipFile.addFile(source, parameters)
        val metadataParameters = ZipParameters().apply {
            fileNameInZip = "device-summary.txt"
            compressionMethod = CompressionMethod.DEFLATE
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
        zipFile.addFile(metadata, metadataParameters)

        val uri: Uri = FileProvider.getUriForFile(
            activity,
            "${BuildConfig.APPLICATION_ID}.diagnostics.files",
            zip
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, "Partilhar relatório encriptado"))
        record(activity, "diagnostic_export_created", "file_size=${zip.length()}")
    }

    private fun isCapturing(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)

    private fun logFile(context: Context): File =
        File(context.filesDir, "diagnostics/events.jsonl").apply { parentFile?.mkdirs() }

    private fun isoNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
}
