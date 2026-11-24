package pt.catharsys.digiautostart

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings

/** Debug-only foreground service that keeps the screen receiver alive. */
class AutomationService : Service() {
    private var screenReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, false)
                .putBoolean(KEY_SHOULD_LAUNCH, false)
                .apply()
            unregisterScreenReceiver()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        registerScreenReceiver()
        DiagnosticsFeature.record(this, "automation_service_started")
        if (intent?.action == ACTION_BOOT_COMPLETED &&
            prefs.getBoolean(KEY_LAUNCH_ON_BOOT, false)) {
            DiagnosticsFeature.record(this, "automation_boot_launch_scheduled", "delay_seconds=$BOOT_LAUNCH_DELAY_SECONDS")
            handler.postDelayed({ launchTarget("boot") }, BOOT_LAUNCH_DELAY_SECONDS * 1000L)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterScreenReceiver()
        DiagnosticsFeature.record(this, "automation_service_destroyed")
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun startAsForeground() {
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2, Intent(this, AutomationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("DAdA ativo")
            .setContentText("Assistente de arranque da DIGI TV em execução")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "Desativar", stopIntent).build())
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, "Automação DAdA", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Indica que o DAdA está a monitorizar o despertar da box."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> decideOnScreenOff()
                    Intent.ACTION_SCREEN_ON -> handleScreenOn()
                }
            }
        }
        screenReceiver = receiver
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        DiagnosticsFeature.record(this, "automation_screen_receiver_registered")
    }

    private fun unregisterScreenReceiver() {
        val current = screenReceiver ?: return
        try { unregisterReceiver(current) } catch (_: IllegalArgumentException) { }
        screenReceiver = null
    }

    private fun decideOnScreenOff() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val mode = prefs.getInt(KEY_MODE, 2)
        val targetWasLast = mode == 1 || wasTargetLastForeground()
        prefs.edit().putBoolean(KEY_SHOULD_LAUNCH, targetWasLast).apply()
        DiagnosticsFeature.record(this, "automation_screen_off_decision",
            "mode=${if (mode == 1) "A" else "B"},target_was_last_foreground=$targetWasLast")
    }

    private fun wasTargetLastForeground(): Boolean {
        if (!AutomationFeature.hasUsageAccess(this)) {
            DiagnosticsFeature.record(this, "automation_usage_access_missing")
            return false
        }
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = manager.queryEvents(end - LOOKBACK_MS, end) ?: return false
        val homePackage = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        )?.activityInfo?.packageName
        val event = UsageEvents.Event()
        var lastUserPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val foreground = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (!foreground) continue
            val packageName = event.packageName ?: continue
            if (packageName == applicationContext.packageName ||
                packageName == homePackage || packageName == "com.android.systemui") continue
            lastUserPackage = packageName
        }
        return lastUserPackage == TARGET_PACKAGE
    }

    private fun handleScreenOn() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val shouldLaunch = prefs.getBoolean(KEY_SHOULD_LAUNCH, false)
        prefs.edit().putBoolean(KEY_SHOULD_LAUNCH, false).apply()
        DiagnosticsFeature.record(this, "automation_screen_on", "should_launch=$shouldLaunch")
        if (!shouldLaunch) return
        val delay = prefs.getInt(KEY_DELAY, 5).coerceIn(0, 30)
        DiagnosticsFeature.record(this, "automation_launch_scheduled", "reason=wake,delay_seconds=$delay")
        handler.postDelayed({ launchTarget("wake") }, delay * 1000L)
    }

    private fun launchTarget(reason: String) {
        if (!Settings.canDrawOverlays(this)) {
            DiagnosticsFeature.record(this, "automation_launch_blocked", "reason=overlay_permission_missing,trigger=$reason")
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
        if (launchIntent == null) {
            DiagnosticsFeature.record(this, "automation_launch_failed", "reason=no_launch_intent,trigger=$reason")
            return
        }
        try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val launchRequestedAt = System.currentTimeMillis()
            startActivity(launchIntent)
            DiagnosticsFeature.record(this, "automation_launch_start_activity_returned", "trigger=$reason")
            handler.postDelayed({ verifyTargetForeground(reason, launchRequestedAt) }, VERIFY_LAUNCH_DELAY_MS)
        } catch (error: Exception) {
            DiagnosticsFeature.record(this, "automation_launch_exception",
                "trigger=$reason,exception=${error.javaClass.simpleName}")
        }
    }

    private fun verifyTargetForeground(reason: String, launchRequestedAt: Long) {
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = manager.queryEvents(launchRequestedAt, end) ?: return
        val event = UsageEvents.Event()
        var targetResumed = false
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if ((event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) &&
                event.packageName == TARGET_PACKAGE && event.timeStamp >= launchRequestedAt) {
                targetResumed = true
            }
        }
        DiagnosticsFeature.record(
            this,
            if (targetResumed) "automation_launch_foreground_observed" else "automation_launch_foreground_not_observed",
            "trigger=$reason,delay_ms=$VERIFY_LAUNCH_DELAY_MS"
        )
    }

    companion object {
        const val ACTION_STOP = "pt.catharsys.digiautostart.action.STOP_AUTOMATION"
        const val ACTION_BOOT_COMPLETED = "pt.catharsys.digiautostart.action.BOOT_COMPLETED"
        private const val PREFS = "digi_autostart"
        private const val KEY_ENABLED = "automation_enabled"
        private const val KEY_SHOULD_LAUNCH = "wake_should_launch"
        private const val KEY_MODE = "mode"
        private const val KEY_DELAY = "delay_seconds"
        private const val KEY_LAUNCH_ON_BOOT = "launch_digi_on_boot"
        private const val TARGET_PACKAGE = "ro.digionline.tv"
        private const val LOOKBACK_MS = 24 * 60 * 60 * 1000L
        private const val VERIFY_LAUNCH_DELAY_MS = 5000L
        private const val VERIFY_LOOKBACK_MS = 15000L
        private const val BOOT_LAUNCH_DELAY_SECONDS = 15
        private const val CHANNEL_ID = "digiautostart_automation"
        private const val NOTIFICATION_ID = 4102

        fun start(context: Context, fromBoot: Boolean = false) {
            val intent = Intent(context, AutomationService::class.java)
            if (fromBoot) intent.action = ACTION_BOOT_COMPLETED
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AutomationService::class.java).setAction(ACTION_STOP))
        }
    }
}
