package com.meghanath.musix.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the app alive while a backup, restore or export is running.
 * It does no copying itself; the ViewModels start and stop it around their work.
 */
class TransferService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Working"
        Log.d(TAG, "started: $title")
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(title),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        acquireWakeLock()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "stopped")
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Backup and export", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(title: String): Notification {
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Musix is working in the background")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .setContentIntent(openApp)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Musix:transfer").apply {
            setReferenceCounted(false)
            acquire(60L * 60L * 1000L) // gives up by itself after one hour
        }
    }

    companion object {
        private const val TAG = "TransferService"
        private const val CHANNEL_ID = "musix_transfers"
        private const val NOTIFICATION_ID = 4101
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, title: String) {
            val intent = Intent(context, TransferService::class.java).putExtra(EXTRA_TITLE, title)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TransferService::class.java))
        }
    }
}