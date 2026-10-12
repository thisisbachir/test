package com.seventyfivehard.tracker.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.seventyfivehard.tracker.MainActivity
import com.seventyfivehard.tracker.R
import com.seventyfivehard.tracker.data.Settings
import com.seventyfivehard.tracker.data.TrackerRepository
import com.seventyfivehard.tracker.domain.AttemptStatus
import com.seventyfivehard.tracker.domain.Task
import com.seventyfivehard.tracker.domain.log
import com.seventyfivehard.tracker.domain.status
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

object Reminders {
    private const val CHANNEL_ID = "daily_reminder"
    private const val NOTIFICATION_ID = 75

    fun schedule(context: Context, settings: Settings) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        if (!settings.reminderEnabled) return
        val now = ZonedDateTime.now()
        var next = now.with(LocalTime.of(settings.reminderHour, settings.reminderMinute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        // Inexact on purpose: no special alarm permission needed, a few minutes' drift is fine.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("MissingPermission")
    fun notifyIfUnfinished(context: Context) {
        val data = TrackerRepository.get(context).data.value
        val attempt = data.current ?: return
        val today = LocalDate.now()
        val status = attempt.status(today) as? AttemptStatus.Active ?: return
        val left = Task.entries.filterNot { it.isDone(attempt.log(today)) }
        if (left.isEmpty()) return

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = "Day ${status.day}: ${left.size} task${if (left.size == 1) "" else "s"} left"
        val body = left.joinToString(" · ") { it.title }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\nMiss one and it's back to Day 1."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission revoked.
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.notifyIfUnfinished(context)
        Reminders.schedule(context, TrackerRepository.get(context).data.value.settings)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminders.schedule(context, TrackerRepository.get(context).data.value.settings)
        }
    }
}
