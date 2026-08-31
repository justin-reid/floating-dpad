package com.floatingdpad.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.floatingdpad.MainActivity
import com.floatingdpad.R
import com.floatingdpad.input.ShizukuKeySender
import com.floatingdpad.settings.Prefs

/**
 * Holds the overlay for as long as the user wants it, which means a foreground service:
 * a plain service would be killed the moment the app is backgrounded, and the whole
 * point is that this thing lives on top of *other* apps.
 */
class OverlayService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var window: OverlayWindow

    private var lastNotReadyToastAt = 0L

    private val shizukuListener: (ShizukuKeySender.State) -> Unit = { state ->
        window.setReady(state == ShizukuKeySender.State.READY)
        updateNotification(state)
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        ShizukuKeySender.init(this)
        window = OverlayWindow(this, prefs, ShizukuKeySender).apply {
            onNotReady = ::warnNotReady
        }
        createChannel()
        prefs.registerListener(this)
        ShizukuKeySender.addListener(shizukuListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.overlayEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_RECONNECT -> ShizukuKeySender.connect()
        }

        if (!Settings.canDrawOverlays(this)) {
            // Nothing to show and no way to ask from here; hand it back to the activity.
            startForegroundWithStatus()
            Toast.makeText(this, R.string.overlay_permission_missing, Toast.LENGTH_LONG).show()
            prefs.overlayEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        prefs.overlayEnabled = true
        startForegroundWithStatus()
        window.show()
        window.setReady(ShizukuKeySender.isReady)
        ShizukuKeySender.connect()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.onConfigurationChanged()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        // Dragging writes x/y continuously; rebuilding the view on each frame would be
        // both pointless and visibly janky.
        if (key == null || key in Prefs.POSITION_KEYS) return
        window.refreshConfig()
    }

    override fun onDestroy() {
        isRunning = false
        ShizukuKeySender.removeListener(shizukuListener)
        prefs.unregisterListener(this)
        window.hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- notification ------------------------------------------------------------

    private fun startForegroundWithStatus() {
        val notification = buildNotification(ShizukuKeySender.state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isRunning = true
    }

    private fun updateNotification(state: ShizukuKeySender.State) {
        if (!isRunning) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: ShizukuKeySender.State): Notification {
        val ready = state == ShizukuKeySender.State.READY
        val statusRes = when (state) {
            ShizukuKeySender.State.READY -> R.string.status_ready
            ShizukuKeySender.State.CONNECTING -> R.string.status_connecting
            ShizukuKeySender.State.NOT_INSTALLED -> R.string.status_not_installed
            ShizukuKeySender.State.NOT_RUNNING -> R.string.status_not_running
            ShizukuKeySender.State.PERMISSION_REQUIRED -> R.string.status_permission_required
            ShizukuKeySender.State.FAILED -> R.string.status_failed
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(statusRes))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(activityIntent())
            .addAction(0, getString(R.string.action_stop), servicePendingIntent(ACTION_STOP, 1))

        if (!ready) {
            // Milestone 6: a dead backend must never look like a working one.
            builder.setColor(0xFFE5574E.toInt())
                .setColorized(false)
                .addAction(
                    0,
                    getString(R.string.action_reconnect),
                    servicePendingIntent(ACTION_RECONNECT, 2),
                )
        }
        return builder.build()
    }

    private fun activityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, OverlayService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_overlay),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            description = getString(R.string.channel_overlay_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun warnNotReady() {
        val now = SystemClock.uptimeMillis()
        if (now - lastNotReadyToastAt < TOAST_THROTTLE_MS) return
        lastNotReadyToastAt = now
        val message = when (ShizukuKeySender.state) {
            ShizukuKeySender.State.NOT_INSTALLED -> R.string.status_not_installed
            ShizukuKeySender.State.PERMISSION_REQUIRED -> R.string.status_permission_required
            else -> R.string.status_not_running
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        ShizukuKeySender.connect()
    }

    companion object {
        const val ACTION_STOP = "com.floatingdpad.action.STOP"
        const val ACTION_RECONNECT = "com.floatingdpad.action.RECONNECT"

        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1
        private const val TOAST_THROTTLE_MS = 3000L

        @Volatile
        var isRunning: Boolean = false
            internal set

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
                .onFailure { context.stopService(Intent(context, OverlayService::class.java)) }
        }

        fun toggle(context: Context) {
            if (isRunning) stop(context) else start(context)
        }
    }
}
