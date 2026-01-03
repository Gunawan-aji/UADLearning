package com.uad.uadlearningapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import androidx.core.app.NotificationCompat

class SholatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sholatName = intent.getStringExtra("SHOLAT_NAME") ?: "Sholat"
        val channelId = "SHOLAT_CHANNEL"
        val notificationId = 101

        // 1. Buat Notifikasi
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Pengingat Sholat", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.islam)
            .setContentTitle("Waktunya Sholat $sholatName")
            .setContentText("Marilah kita sholat tepat pada waktunya.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)

        // 2. Putar Suara Adzan Otomatis
        try {
            val mediaPlayer = MediaPlayer.create(context, R.raw.adzan)
            mediaPlayer.start()

            // Suara akan berhenti otomatis setelah selesai,
            mediaPlayer.setOnCompletionListener { it.release() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}