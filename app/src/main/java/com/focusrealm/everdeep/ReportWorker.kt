package com.focusrealm.everdeep

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return Result.success()

        val p = c.getSharedPreferences("everdeep", Context.MODE_PRIVATE)
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("report", "Evening report", NotificationManager.IMPORTANCE_DEFAULT))

        val days = p.getStringSet("focusDays", emptySet()) ?: emptySet()
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        var t = System.currentTimeMillis()
        if (!days.contains(fmt.format(Date(t)))) t -= 86_400_000L
        var streak = 0
        while (days.contains(fmt.format(Date(t)))) { streak++; t -= 86_400_000L }

        val top = p.getString("distraction", null)?.split(";")
            ?.mapNotNull { pair ->
                val kv = pair.split("=", limit = 2)
                kv.getOrNull(1)?.toLongOrNull()?.let { FocusBus.labelFor(kv[0]) to it }
            }?.maxByOrNull { it.second }
        val topTxt = top?.let { (l, s) ->
            " · most lost to $l (${if (s < 60) "${s}s" else "${s / 60}m"})" } ?: ""

        val text = "🌙 streak $streak 🔥 · best floor ${p.getInt("bestDepth", 0)}" +
                   " · heroes fallen ${p.getInt("heroesFallen", 0)}$topTxt"
        nm.notify(12, NotificationCompat.Builder(c, "report")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("The deep, at day's end")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$text\n\nOpen Everdeep for the full ledger."))
            .setContentIntent(PendingIntent.getActivity(c, 0,
                Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build())
        return Result.success()
    }
}
