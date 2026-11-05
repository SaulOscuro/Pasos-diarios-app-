package com.ostwaldviz.pasosdiarios

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** A single next-upcoming reminder. Pure plan selection lives outside Android. */
data class StepReminder(val day: LocalDate, val index: Int, val scheduledAt: ZonedDateTime)

/** User-configured local clock schedule; never requires network or exact-alarm access. */
object StepReminderPlanner {
    fun next(
        now: ZonedDateTime,
        today: DailyProgress,
        tomorrowPlan: StepPlan
    ): StepReminder {
        for (offset in 0L..1L) {
            val day = now.toLocalDate().plusDays(offset)
            val plan = if (day == today.date) today.plan else tomorrowPlan
            for (index in 0 until plan.blockCount) {
                if (day == today.date && index in today.completed) continue
                val scheduledAt = day.atTime(plan.suggestedTime(index)).atZone(now.zone)
                if (scheduledAt.toInstant().isAfter(now.toInstant())) {
                    return StepReminder(day, index, scheduledAt)
                }
            }
        }
        // The next day always contains at least one block.
        error("Could not calculate next reminder")
    }

    fun stillRelevant(
        reminder: StepReminder,
        now: ZonedDateTime,
        current: DailyProgress
    ): Boolean {
        if (reminder.day != current.date ||
            reminder.index !in 0 until current.plan.blockCount ||
            reminder.index in current.completed
        ) return false
        val actualTime = current.date.atTime(current.plan.suggestedTime(reminder.index))
            .atZone(now.zone)
        if (actualTime.toInstant() != reminder.scheduledAt.toInstant()) return false
        val minutes = Duration.between(actualTime.toInstant(), now.toInstant()).toMinutes()
        // Don't alert for an obsolete alarm after the clock or the schedule changes.
        return minutes in 0..90
    }
}

/**
 * Schedule only the next pending block. Each delivered broadcast schedules the following one,
 * so even plans with 100 blocks do not need 100 concurrent alarms.
 */
class StepReminders(context: Context) {
    private val app = context.applicationContext
    private val settings = app.getSharedPreferences("pasos_diarios_reminders_v1", Context.MODE_PRIVATE)
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val alarmManager = app.getSystemService(AlarmManager::class.java)

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recordatorios de caminatas",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos para levantarte y completar tus bloques de pasos."
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun enabled(): Boolean = settings.getBoolean(ENABLED_KEY, false)

    fun permissionGranted(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun canPost(): Boolean =
        permissionGranted() &&
            manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    fun setEnabled(value: Boolean) {
        settings.edit().putBoolean(ENABLED_KEY, value).apply()
        reschedule()
    }

    fun reschedule() {
        alarmManager.cancel(alarmIntent())
        if (!enabled() || !canPost()) return
        val repository = DailyRepository(StepPreferences(app))
        val current = repository.current()
        val upcoming = StepReminderPlanner.next(
            ZonedDateTime.now(), current, repository.reminderPlanForTomorrow()
        )
        val intent = alarmIntent(
            upcoming.day.toString(),
            upcoming.index,
            upcoming.scheduledAt.toInstant().toEpochMilli()
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            upcoming.scheduledAt.toInstant().toEpochMilli(),
            intent
        )
    }

    fun handleAlarm(intent: Intent) {
        try {
            if (!enabled() || !canPost()) return
            val date = runCatching {
                LocalDate.parse(intent.getStringExtra(EXTRA_DAY))
            }.getOrNull() ?: return
            val index = intent.getIntExtra(EXTRA_INDEX, -1)
            val whenMs = intent.getLongExtra(EXTRA_TRIGGER, Long.MIN_VALUE)
            if (whenMs == Long.MIN_VALUE) return
            val current = DailyRepository(StepPreferences(app)).current()
            if (index !in 0 until current.plan.blockCount) return
            val occurrence = StepReminder(
                date, index,
                ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(whenMs), ZoneId.systemDefault())
            )
            if (StepReminderPlanner.stillRelevant(occurrence, ZonedDateTime.now(), current)) {
                showReminder(current, index)
            }
        } finally {
            reschedule()
        }
    }

    private fun showReminder(record: DailyProgress, index: Int) {
        val openApp = PendingIntent.getActivity(
            app, 0,
            Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val steps = NumberFormat.getIntegerInstance(Locale("es", "MX"))
            .format(record.plan.stepsForBlock(index))
        val notification = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_walk)
            .setContentTitle("¡Hora de caminar!")
            .setContentText("Bloque ${index + 1}: ${steps} pasos. Levántate y muévete.")
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun alarmIntent(
        day: String? = null,
        index: Int = -1,
        atMillis: Long = -1L
    ): PendingIntent {
        val intent = Intent(app, StepReminderReceiver::class.java).apply {
            action = ACTION_ALARM
            if (day != null) putExtra(EXTRA_DAY, day)
            if (index >= 0) putExtra(EXTRA_INDEX, index)
            if (atMillis >= 0) putExtra(EXTRA_TRIGGER, atMillis)
        }
        return PendingIntent.getBroadcast(
            app, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val CHANNEL_ID = "walking_steps_v1"
        private const val ENABLED_KEY = "enabled"
        private const val ACTION_ALARM = "com.ostwaldviz.pasosdiarios.STEP_REMINDER"
        private const val EXTRA_DAY = "day"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_TRIGGER = "trigger"
        private const val ALARM_REQUEST_CODE = 41
        private const val NOTIFICATION_ID = 101
    }
}

/** Manifest receivers keep notifications working with the activity closed or after reboot. */
class StepReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        StepReminders(context).handleAlarm(intent)
    }
}

class StepReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No background service: system time, zone, reboot and package updates re-arm the next alarm.
        StepReminders(context).reschedule()
    }
}
