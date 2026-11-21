package com.smart.msrlab.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Date

/**
 * NotificationService — prayer time notification channels + AlarmManager scheduling
 *
 * Mirrors Flutter notification_service.dart:
 *   initNotifications() → createChannel()
 *   schedulePrayerNotifications(prayerTimes, adhanEnabled) → scheduleAlarms()
 */
object NotificationService {

    const val CHANNEL_ID   = "prayer_times"
    const val CHANNEL_NAME = "Prayer Times"
    const val ACTION_PRAYER = "com.smart.msrlab.PRAYER_ALARM"

    private val PRAYER_NAMES = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")

    /**
     * Create notification channel. Call once from Application.onCreate().
     * Required for Android 8.0+ — safe to call multiple times.
     * Mirrors Flutter initNotifications().
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Reminders for the 6 daily prayer times"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 100, 300)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Schedule one AlarmManager alarm per prayer time for today.
     * Prayer times in the past are silently skipped.
     * Mirrors Flutter schedulePrayerNotifications().
     *
     * @param prayerTimes List of 6 Date objects [fajr, sunrise, dhuhr, asr, maghrib, isha]
     * @param adhanEnabled If false, all alarms are cancelled and no new ones scheduled
     */
    fun schedulePrayerNotifications(
        context: Context,
        prayerTimes: List<Date>,
        adhanEnabled: Boolean,
    ) {
        val am  = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        // Cancel all existing prayer alarms first
        prayerTimes.forEachIndexed { index, _ ->
            val pi = buildPendingIntent(context, index, PRAYER_NAMES.getOrElse(index) { "Prayer" })
            am.cancel(pi)
        }

        if (!adhanEnabled) return

        // Schedule new alarms only for future times
        prayerTimes.forEachIndexed { index, time ->
            if (time.time <= now) return@forEachIndexed // skip past times

            val pi = buildPendingIntent(context, index, PRAYER_NAMES.getOrElse(index) { "Prayer" })
            try {
                // setExactAndAllowWhileIdle fires even in Doze mode
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.time, pi)
            } catch (_: SecurityException) {
                // API 31+: SCHEDULE_EXACT_ALARM not granted → use inexact
                am.set(AlarmManager.RTC_WAKEUP, time.time, pi)
            }
        }
    }

    /** Cancel all scheduled prayer alarms */
    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        PRAYER_NAMES.forEachIndexed { index, name ->
            am.cancel(buildPendingIntent(context, index, name))
        }
    }

    /** Show a prayer notification immediately. Called by PrayerAlarmReceiver. */
    fun showPrayerNotification(context: Context, prayerName: String, notificationId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Waktu $prayerName")
            .setContentText("Sudah masuk waktu solat $prayerName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .build()
        nm.notify(notificationId, notification)
    }

    private fun buildPendingIntent(
        context: Context,
        requestCode: Int,
        prayerName: String,
    ): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER
            putExtra("prayer_name", prayerName)
            putExtra("notification_id", requestCode)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/**
 * PrayerAlarmReceiver — triggered by AlarmManager at prayer time
 * Registered in AndroidManifest.xml
 */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerName     = intent.getStringExtra("prayer_name") ?: "Solat"
        val notificationId = intent.getIntExtra("notification_id", 0)
        NotificationService.showPrayerNotification(context, prayerName, notificationId)
    }
}
