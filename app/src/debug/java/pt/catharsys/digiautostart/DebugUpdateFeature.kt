package pt.catharsys.digiautostart

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object DebugUpdateFeature {
    private const val OWNER = "CatharsysAmaeth"
    private const val REPO = "DigiAutoStart-Diagnostics"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/contents"

    fun check(activity: Activity) {
        val token = try {
            String(android.util.Base64.decode(BuildConfig.DIAGNOSTICS_GITHUB_TOKEN_B64, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
        if (token.isBlank()) {
            Toast.makeText(activity, "Esta compilação não tem acesso ao repositório privado.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(activity, "A verificar atualizações…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val metadataText = getText(token, "$API/apk/version.json")
                val metadata = JSONObject(metadataText)
                val remoteCode = metadata.getInt("versionCode")
                val remoteName = metadata.getString("versionName")
                if (remoteCode <= BuildConfig.VERSION_CODE) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Já tens a versão mais recente ($remoteName).", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }

                val apk = downloadApk(activity.applicationContext, token, "$API/apk/DigiAutoStart-debug.apk")
                val expectedSha = metadata.getString("sha256").lowercase()
                val actualSha = sha256(apk)
                if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                    apk.delete()
                    error("A verificação SHA-256 do APK falhou.")
                }
                activity.runOnUiThread {
                    AlertDialog.Builder(activity)
                        .setTitle("Atualização disponível")
                        .setMessage("Versão $remoteName disponível. Queres descarregar e instalar?")
                        .setPositiveButton("Instalar") { _, _ -> install(activity, apk) }
                        .setNegativeButton("Agora não", null)
                        .show()
                }
            } catch (error: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Não foi possível verificar atualizações: ${error.message ?: error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun getText(token: String, endpoint: String): String {
        val connection = open(token, endpoint, "application/vnd.github+json")
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("GitHub HTTP $status")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val encoded = json.getString("content").replace("\n", "")
            return String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadApk(context: android.content.Context, token: String, endpoint: String): File {
        val connection = open(token, endpoint, "application/vnd.github.raw")
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("GitHub HTTP $status ao descarregar APK")
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val output = File(directory, "DigiAutoStart-debug-update.apk")
            connection.inputStream.use { input -> output.outputStream().use { input.copyTo(it) } }
            return output
        } finally {
            connection.disconnect()
        }
    }

    private fun open(token: String, endpoint: String, accept: String) =
        (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "DigiAutoStart-Debug")
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun install(activity: Activity, apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            activity.startActivity(settings)
            Toast.makeText(activity, "Autoriza a instalação para DigiAutoStart e volta a carregar em Verificar atualizações.", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${BuildConfig.APPLICATION_ID}.diagnostics.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }

}
