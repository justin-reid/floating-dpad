package com.floatingdpad.boot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.floatingdpad.MainActivity
import com.floatingdpad.R
import com.floatingdpad.overlay.OverlayService
import com.floatingdpad.settings.Prefs

/**
 * Restores the pad after a reboot, if the user asked for that.
 *
 * Two things can go wrong here and both are handled by falling back to a notification
 * rather than failing quietly: Android restricts which foreground service types may be
 * started from BOOT_COMPLETED, and Shizuku itself is not running yet at boot -- it has
 * to be started by hand over wireless debugging. A pad that appears but cannot send
 * anything is worse than no pad, so say so.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = Prefs.get(context)
        if (!prefs.startOnBoot) return
        if (!Settings.canDrawOverlays(context)) return

        try {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        } catch (t: Throwable) {
            Log.w(TAG, "Could not start the overlay from boot; prompting instead", t)
            promptToStart(context)
        }
    }

    private fun promptToStart(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_boot),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.boot_prompt_title))
            .setContentText(context.getString(R.string.boot_prompt_text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val TAG = "BootReceiver"
        const val CHANNEL_ID = "boot"
        const val NOTIFICATION_ID = 2
    }
}
