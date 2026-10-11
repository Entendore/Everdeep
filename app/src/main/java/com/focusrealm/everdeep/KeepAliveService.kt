package com.focusrealm.everdeep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL = "everdeep"
        private const val NOTIF_ID = 11
        var instance: KeepAliveService? = null

        fun start(context: Context) =
            context.startForegroundService(Intent(context, KeepAliveService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, KeepAliveService::class.java))
    }

    private var lastText = ""; private var lastBlock: String? = null
    private var progCur = 0; private var progMax = 0

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Focus status", NotificationManager.IMPORTANCE_MIN))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, build("🕯️ The hero keeps watch while you work"))
        instance = this
        return START_STICKY
    }

    private var lastSeen = 0L
    fun update(text: String, blockPkg: String? = null) {
        if (text == lastText && blockPkg == lastBlock) return
        lastText = text; lastBlock = blockPkg
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(text, blockPkg))
    }

    fun progress(cur: Int, max: Int) {
        if (cur == progCur && max == progMax) return
        progCur = cur; progMax = max
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(lastText, lastBlock))
    }

    private fun build(text: String, blockPkg: String? = null): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Everdeep")
            .setContentText(text)
            .setProgress(progMax, progCur, progMax <= 0)
            .setOngoing(true)
            .setContentIntent(pi)
            .apply {
                if (blockPkg != null) addAction(0,
                    "🚫 Block ${FocusBus.labelFor(blockPkg)}",
                    PendingIntent.getBroadcast(this@KeepAliveService, blockPkg.hashCode(),
                        Intent(this@KeepAliveService, BlockReceiver::class.java)
                            .putExtra("pkg", blockPkg),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { instance = null; super.onDestroy() }
}
