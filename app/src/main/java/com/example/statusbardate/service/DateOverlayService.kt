package com.example.statusbardate.service

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
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.statusbardate.MainActivity
import com.example.statusbardate.R
import com.example.statusbardate.data.OverlayConfig
import com.example.statusbardate.data.PrefsManager
import com.example.statusbardate.data.TextColorMode
import com.example.statusbardate.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime

class DateOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefsManager: PrefsManager
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var powerManager: PowerManager

    private var dateTextView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentForegroundPackage: String? = null
    private var lastAppliedWhiteText: Boolean? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var configJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val midnightRunnable = Runnable {
        refreshDateText()
        scheduleNextMidnightRefresh()
    }

    private val foregroundPollRunnable = object : Runnable {
        override fun run() {
            val config = prefsManager.config.value
            if (config.isEnabled &&
                config.colorMode == TextColorMode.AUTO &&
                config.autoDetectLightApps &&
                powerManager.isInteractive
            ) {
                detectForegroundPackage()?.let { pkg ->
                    if (pkg != currentForegroundPackage) {
                        currentForegroundPackage = pkg
                        applyTextColor(config)
                    }
                }
                mainHandler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    private val systemEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED -> {
                    refreshDateText()
                    scheduleNextMidnightRefresh()
                }
                Intent.ACTION_CONFIGURATION_CHANGED -> {
                    lastAppliedWhiteText = null
                    applyTextColor(prefsManager.config.value)
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    restartForegroundPollingIfNeeded()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    mainHandler.removeCallbacks(foregroundPollRunnable)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        prefsManager = PrefsManager.getInstance(this)

        createNotificationChannel()
        startForegroundCompat()

        if (!Settings.canDrawOverlays(this)) {
            prefsManager.setEnabled(false)
            stopSelf()
            return
        }

        setupOverlayView()
        registerSystemReceivers()
        scheduleNextMidnightRefresh()

        configJob = serviceScope.launch {
            prefsManager.config.collect { config ->
                if (!config.isEnabled) {
                    stopSelf()
                } else {
                    lastAppliedWhiteText = null
                    updateOverlayAppearance(config)
                    restartForegroundPollingIfNeeded()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || !prefsManager.config.value.isEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        refreshDateText()
        restartForegroundPollingIfNeeded()
        return START_STICKY
    }

    private fun setupOverlayView() {
        if (dateTextView != null) return

        val config = prefsManager.config.value
        val textView = TextView(this).apply {
            typeface = config.fontStyle.toAndroidTypeface()
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            setSingleLine(true)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = config.offsetX
            y = config.offsetY
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        dateTextView = textView
        layoutParams = params
        windowManager.addView(textView, params)
        updateOverlayAppearance(config)
    }

    private fun updateOverlayAppearance(config: OverlayConfig) {
        val tv = dateTextView ?: return
        val params = layoutParams ?: return

        tv.text = config.formatOption.format()
        tv.typeface = config.fontStyle.toAndroidTypeface()
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.textSizeSp)
        applyTextColor(config)

        if (params.x != config.offsetX || params.y != config.offsetY) {
            params.x = config.offsetX
            params.y = config.offsetY
            try {
                windowManager.updateViewLayout(tv, params)
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    private fun refreshDateText() {
        dateTextView?.text = prefsManager.config.value.formatOption.format()
    }

    private fun applyTextColor(config: OverlayConfig) {
        val tv = dateTextView ?: return
        val isSystemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val useWhiteText = when (config.colorMode) {
            TextColorMode.WHITE -> true
            TextColorMode.BLACK -> false
            TextColorMode.AUTO -> {
                val fgPkg = currentForegroundPackage
                if (config.autoDetectLightApps &&
                    fgPkg != null &&
                    config.lightAppPackages.contains(fgPkg)
                ) {
                    false // Nền app sáng -> dùng chữ đen
                } else {
                    isSystemNight
                }
            }
        }

        if (lastAppliedWhiteText == useWhiteText) return
        lastAppliedWhiteText = useWhiteText

        if (useWhiteText) {
            tv.setTextColor(Color.WHITE)
            tv.setShadowLayer(2.2f, 0f, 1f, Color.argb(135, 0, 0, 0))
        } else {
            tv.setTextColor(Color.parseColor("#1C1B1F"))
            tv.setShadowLayer(1.8f, 0f, 1f, Color.argb(105, 255, 255, 255))
        }
    }

    private fun restartForegroundPollingIfNeeded() {
        mainHandler.removeCallbacks(foregroundPollRunnable)
        val config = prefsManager.config.value
        if (config.isEnabled &&
            config.colorMode == TextColorMode.AUTO &&
            config.autoDetectLightApps &&
            PermissionHelper.hasUsageStatsPermission(this) &&
            powerManager.isInteractive
        ) {
            mainHandler.post(foregroundPollRunnable)
        } else {
            currentForegroundPackage = null
            applyTextColor(config)
        }
    }

    private fun detectForegroundPackage(): String? {
        if (!PermissionHelper.hasUsageStatsPermission(this)) return null
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 15_000L
        return try {
            val events = usageStatsManager.queryEvents(beginTime, endTime)
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    latestPkg = event.packageName
                }
            }
            latestPkg
        } catch (_: Exception) {
            null
        }
    }

    private fun scheduleNextMidnightRefresh() {
        mainHandler.removeCallbacks(midnightRunnable)
        val now = ZonedDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val delayMillis = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L) + 150L
        mainHandler.postDelayed(midnightRunnable, delayMillis)
    }

    private fun registerSystemReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            systemEventReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hiển thị ngày trên thanh trạng thái",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Duy trì hiển thị ngày/tháng cạnh đồng hồ"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Đang hiển thị ngày/tháng")
            .setContentText("Chạm để điều chỉnh vị trí, font hoặc màu chữ")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(midnightRunnable)
        mainHandler.removeCallbacks(foregroundPollRunnable)
        configJob?.cancel()
        serviceScope.cancel()
        try {
            unregisterReceiver(systemEventReceiver)
        } catch (_: IllegalArgumentException) {
        }
        dateTextView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        dateTextView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "status_bar_date_channel"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 1_100L

        fun start(context: Context) {
            val intent = Intent(context, DateOverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DateOverlayService::class.java))
        }
    }
}
