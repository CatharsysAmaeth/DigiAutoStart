package pt.catharsys.digiautostart

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
    private const val GITHUB_OWNER = "CatharsysAmaeth"
    private const val GITHUB_REPO = "DigiAutoStart-Diagnostics"
    private const val TOKEN_ALIAS = "DigiAutoStartDiagnosticsUpload"
    private const val TOKEN_PREF = "encrypted_github_token"
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
            "Configurar envio privado",
            "Enviar último relatório",
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
                    2 -> configureUpload(activity)
                    3 -> uploadLatest(activity)
                    4 -> {
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
        val previous = File(source.parentFile, "events.previous.jsonl")
        if (previous.exists() && previous.length() > 0L) {
            val previousParameters = ZipParameters().apply {
                fileNameInZip = "events.previous.jsonl"
                compressionMethod = CompressionMethod.DEFLATE
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
            zipFile.addFile(previous, previousParameters)
        }
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


    private fun configureUpload(activity: Activity) {
        val input = EditText(activity).apply {
            hint = "GitHub fine-grained token"
            inputType = 129
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            setSingleLine(true)
        }
        AlertDialog.Builder(activity)
            .setTitle("Acesso ao repositório privado")
            .setMessage(
                "Cria um Fine-grained personal access token limitado ao repositório " +
                    "$GITHUB_OWNER/$GITHUB_REPO, com Contents: Read and write. " +
                    "O token será cifrado com uma chave Android Keystore deste equipamento."
            )
            .setView(input)
            .setPositiveButton("Guardar e validar") { _, _ ->
                val token = input.text?.toString()?.trim().orEmpty()
                if (!token.startsWith("github_pat_")) {
                    Toast.makeText(activity, "Token GitHub inválido ou vazio.", Toast.LENGTH_LONG).show()
                } else {
                    try {
                        saveToken(activity, token)
                        runNetwork(activity, "A validar acesso ao repositório privado…") {
                            val status = githubRequest(token, "GET", "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO", null)
                            if (status !in 200..299) error("GitHub HTTP $status")
                            "Acesso validado ao repositório privado."
                        }
                    } catch (error: Exception) {
                        Toast.makeText(activity, "Não foi possível guardar o token: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun uploadLatest(activity: Activity) {
        val token = try { loadToken(activity) } catch (error: Exception) {
            Toast.makeText(activity, "Configura primeiro o acesso ao GitHub.", Toast.LENGTH_LONG).show()
            return
        }
        if (token.isNullOrBlank()) {
            Toast.makeText(activity, "Configura primeiro o acesso ao GitHub.", Toast.LENGTH_LONG).show()
            return
        }
        val directory = File(activity.cacheDir, "diagnostics")
        val zip = directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith("-encrypted.zip") }
            ?.maxByOrNull { it.lastModified() }
        if (zip == null) {
            Toast.makeText(activity, "Ainda não existe um ZIP encriptado para enviar.", Toast.LENGTH_LONG).show()
            return
        }
        runNetwork(activity, "A enviar relatório encriptado para o GitHub privado…") {
            val encoded = Base64.encodeToString(zip.readBytes(), Base64.NO_WRAP)
            val path = "reports/${zip.name}"
            val body = JSONObject()
                .put("message", "Add encrypted DigiAutoStart diagnostic report")
                .put("content", encoded)
                .toString()
            val url = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/contents/$path"
            val status = githubRequest(token, "PUT", url, body)
            if (status !in 200..299) error("GitHub HTTP $status")
            "Relatório enviado para o repositório privado."
        }
    }

    private fun runNetwork(activity: Activity, workingMessage: String, operation: () -> String) {
        Toast.makeText(activity, workingMessage, Toast.LENGTH_LONG).show()
        Thread {
            val message = try { operation() } catch (error: Exception) {
                "Falha no envio: ${error.message ?: error.javaClass.simpleName}"
            }
            activity.runOnUiThread {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun githubRequest(token: String, method: String, endpoint: String, body: String?): Int {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "DigiAutoStart-Debug")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            stream?.use { it.copyTo(java.io.ByteArrayOutputStream()) }
            return status
        } finally {
            connection.disconnect()
        }
    }

    private fun saveToken(context: Context, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(TOKEN_PREF, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    private fun loadToken(context: Context): String? {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN_PREF, null) ?: return null
        val payload = Base64.decode(stored, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(TOKEN_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                TOKEN_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun isCapturing(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)

    private fun logFile(context: Context): File =
        File(context.filesDir, "diagnostics/events.jsonl").apply { parentFile?.mkdirs() }

    private fun isoNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date())
}
