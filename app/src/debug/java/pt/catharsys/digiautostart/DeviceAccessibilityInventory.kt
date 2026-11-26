package pt.catharsys.digiautostart

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.ScrollView
import android.widget.TextView
import android.util.DisplayMetrics
import java.io.File
import java.util.Locale

/**
 * Read-only inventory of device build details and Android/TV accessibility routes.
 * It never launches settings or changes any system state.
 */
object DeviceAccessibilityInventory {
    private data class Route(val label: String, val action: String, val extra: String = "")

    private val routes = listOf(
        Route("Acessibilidade geral", Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Route("Detalhes de serviço (API oculta)", "android.settings.ACCESSIBILITY_DETAILS_SETTINGS", Intent.EXTRA_COMPONENT_NAME),
        Route("Configuração de atalhos", "android.settings.ACCESSIBILITY_SHORTCUT_SETTINGS"),
        Route("Acessibilidade geral Android TV OEM", "android.settings.ACCESSIBILITY_TV_OEM_LINK"),
        Route("Legendas Android TV OEM", "android.settings.ACCESSIBILITY_CAPTION_TV_OEM_LINK"),
        Route("Correcção de cor Android TV OEM", "android.settings.ACCESSIBILITY_COLOR_CORRECTION_TV_OEM_LINK"),
        Route("Autorização específica Android TV OEM", "android.settings.ACCESSIBILITY_SHORTCUT_SERVICES_TV_OEM_LINK", "accessibilityServiceInfo"),
        Route("Cor e movimento", "android.settings.ACCESSIBILITY_COLOR_MOTION_SETTINGS"),
        Route("Contraste de cor", "android.settings.ACCESSIBILITY_COLOR_CONTRAST_SETTINGS"),
        Route("Reduzir cores brilhantes", "android.settings.REDUCE_BRIGHT_COLORS_SETTINGS"),
        Route("Correcção de cor (Settings)", "com.android.settings.ACCESSIBILITY_COLOR_SPACE_SETTINGS"),
        Route("Inversão de cores", "android.settings.COLOR_INVERSION_SETTINGS"),
        Route("Leitura de texto", "android.settings.TEXT_READING_SETTINGS"),
        Route("Legendas", "android.settings.CAPTIONING_SETTINGS")
    )

    fun run(activity: Activity) {
        val context = activity.applicationContext
        val pm = context.packageManager
        val lines = mutableListOf<String>()
        fun add(key: String, value: Any?) { lines += "$key=$value" }

        lines += "=== IDENTIFICAÇÃO ANDROID / FIRMWARE ==="
        add("android_release", Build.VERSION.RELEASE)
        add("sdk_int", Build.VERSION.SDK_INT)
        add("security_patch", Build.VERSION.SECURITY_PATCH)
        add("build_fingerprint", Build.FINGERPRINT)
        add("build_id", Build.ID)
        add("build_incremental", Build.VERSION.INCREMENTAL)
        add("build_display", Build.DISPLAY)
        add("build_type", Build.TYPE)
        add("build_tags", Build.TAGS)
        add("base_os", Build.VERSION.BASE_OS)
        add("manufacturer", Build.MANUFACTURER)
        add("model", Build.MODEL)
        add("brand", Build.BRAND)
        add("device", Build.DEVICE)
        add("product", Build.PRODUCT)
        add("board", Build.BOARD)
        add("hardware", Build.HARDWARE)
        add("bootloader", Build.BOOTLOADER)
        add("host", Build.HOST)
        add("supported_abis", Build.SUPPORTED_ABIS.joinToString(","))
        add("supported_32_bit_abis", Build.SUPPORTED_32_BIT_ABIS.joinToString(","))
        add("supported_64_bit_abis", Build.SUPPORTED_64_BIT_ABIS.joinToString(","))
        add("process_64_bit", if (Build.VERSION.SDK_INT >= 23) android.os.Process.is64Bit() else "unknown")
        add("locale", Locale.getDefault().toLanguageTag())

        lines += "=== MEMÓRIA / ECRÃ / ARMAZENAMENTO ==="
        val memory = android.app.ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.getMemoryInfo(memory)
        add("ram_total_bytes", memory.totalMem)
        add("ram_available_bytes", memory.availMem)
        add("ram_low_memory", memory.lowMemory)
        val dm = context.resources.displayMetrics
        add("display_width_px", dm.widthPixels)
        add("display_height_px", dm.heightPixels)
        add("display_density", dm.density)
        add("display_density_dpi", dm.densityDpi)
        add("display_scaled_density", dm.scaledDensity)
        add("configuration_ui_mode", context.resources.configuration.uiMode)
        add("configuration_screen_layout", context.resources.configuration.screenLayout)
        add("configuration_orientation", context.resources.configuration.orientation)
        add("configuration_smallest_width_dp", context.resources.configuration.smallestScreenWidthDp)
        val stat = StatFs(context.filesDir.absolutePath)
        add("data_storage_total_bytes", stat.totalBytes)
        add("data_storage_available_bytes", stat.availableBytes)
        add("external_storage_state", Environment.getExternalStorageState())

        lines += "=== FUNCIONALIDADES DO SISTEMA ==="
        try {
            pm.systemAvailableFeatures.orEmpty().mapNotNull { it.name }.sorted().forEach { lines += "feature=$it" }
        } catch (e: Exception) { add("features_error", e.javaClass.simpleName) }

        lines += "=== SERVIÇOS DE ACESSIBILIDADE ==="
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val installed = runCatching { manager?.installedAccessibilityServiceList.orEmpty() }.getOrDefault(emptyList())
        val enabled = runCatching {
            manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty()
        }.getOrDefault(emptyList())
        add("accessibility_manager_available", manager != null)
        add("accessibility_installed_count", installed.size)
        add("accessibility_enabled_count", enabled.size)
        val enabledIds = enabled.mapNotNull { serviceId(it) }.toSet()
        installed.forEachIndexed { index, info ->
            val si = info.resolveInfo.serviceInfo
            lines += "accessibility_service[$index]=component=${si?.packageName}/${si?.name};id=${serviceId(info)};label=${runCatching { info.resolveInfo.loadLabel(pm) }.getOrNull()};enabled=${serviceId(info) in enabledIds};feedback=${info.feedbackType};eventTypes=${info.eventTypes};flags=${info.flags};canRetrieveWindowContent=${info.canRetrieveWindowContent};settingsActivity=${runCatching { info.settingsActivityName }.getOrNull()}"
        }
        val secure = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        }.getOrDefault("")
        add("secure_enabled_accessibility_services_empty", secure.isBlank())
        add("secure_enabled_accessibility_services_count", secure.split(':').count { it.isNotBlank() })
        add("accessibility_shortcut_target", runCatching {
            Settings.Secure.getString(context.contentResolver, "accessibility_shortcut_target_service")
        }.getOrNull())
        add("accessibility_button_target", runCatching {
            Settings.Secure.getString(context.contentResolver, "accessibility_button_target_component")
        }.getOrNull())
        listOf(
            "accessibility_button_targets",
            "accessibility_gesture_targets",
            "accessibility_qs_targets",
            "accessibility_shortcut_dialog_shown",
            "touch_exploration_enabled",
            "accessibility_display_magnification_enabled"
        ).forEach { key ->
            add("secure_$key", runCatching {
                Settings.Secure.getString(context.contentResolver, key)
            }.getOrNull())
        }

        lines += "=== ROTAS / ACTIVIDADES DE DEFINIÇÕES ==="
        routes.forEach { route ->
            val intent = Intent(route.action)
            val resolved = runCatching {
                pm.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            }.getOrNull()
            val candidates = runCatching {
                pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                    .mapNotNull { it.activityInfo }
                    .distinctBy { it.packageName + "/" + it.name }
                    .take(12)
            }.getOrDefault(emptyList())
            lines += "route=${route.label};action=${route.action};extra=${route.extra.ifBlank { "none" }};resolved=${resolved?.packageName}/${resolved?.name};exported=${resolved?.exported};permission=${resolved?.permission};candidate_count=${candidates.size};candidates=${candidates.joinToString("|") { "${it.packageName}/${it.name}:exported=${it.exported}:enabled=${it.enabled}:permission=${it.permission.orEmpty()}" }}"
        }

        lines += "=== PACOTES DE DEFINIÇÕES CONHECIDOS ==="
        listOf("com.android.tv.settings", "com.android.settings", "com.google.android.marvin.talkback").forEach { pkg ->
            val info = runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }.getOrNull()
            add("package_$pkg", info?.versionName ?: "not_visible_or_not_installed")
        }

        val report = lines.joinToString("\n")
        DiagnosticsFeature.record(context, "device_accessibility_inventory", report)
        val view = TextView(activity).apply {
            text = report
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(24, 20, 24, 20)
        }
        AlertDialog.Builder(activity)
            .setTitle("Inventário técnico — Android e Acessibilidade")
            .setView(ScrollView(activity).apply { addView(view) })
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun serviceId(info: AccessibilityServiceInfo): String =
        runCatching { info.id }.getOrDefault("unavailable")
}
