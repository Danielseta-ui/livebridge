package com.appsfolder.livebridge.liveupdate

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

class NowBarForegroundService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val posted = synchronized(pendingLock) { pending }
        if (posted == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    posted.id,
                    posted.notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(posted.id, posted.notification)
            }
        } catch (_: Throwable) {
            @Suppress("DEPRECATION")
            startForeground(posted.id, posted.notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class Pending(val id: Int, val notification: Notification)

    companion object {
        private const val ACTION_STOP = "com.appsfolder.livebridge.STOP_NOW_BAR_FGS"
        private val pendingLock = Any()
        private var pending: Pending? = null
        private var appContext: Context? = null

        fun publish(context: Context, notificationId: Int, notification: Notification) {
            val application = context.applicationContext
            synchronized(pendingLock) {
                appContext = application
                pending = Pending(notificationId, notification)
            }
            ContextCompat.startForegroundService(
                application,
                Intent(application, NowBarForegroundService::class.java)
            )
        }

        fun stop() {
            val application = synchronized(pendingLock) {
                pending = null
                appContext
            } ?: return
            application.startService(
                Intent(application, NowBarForegroundService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
