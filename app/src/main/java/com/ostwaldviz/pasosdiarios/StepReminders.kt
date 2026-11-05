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
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.graphics.Color
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
        // A new channel is needed because Android keeps the previous channel's
        // original sound/vibration settings even after updating the application.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Avisos destacados para caminar",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos emergentes con sonido y vibración para los bloques pendientes."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 140, 200)
            enableLights(true)
            lightColor = Color.GREEN
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
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

    /** Returns the unacknowledged reminder for today when its block is still pending. */
    fun pendingBlock(record: DailyProgress): Int? {
        val day = settings.getString(PENDING_DAY, null)
        val index = settings.getInt(PENDING_INDEX, -1)
        val timestamp = settings.getLong(PENDING_AT, -1L)
        val age = System.currentTimeMillis() - timestamp
        val valid = enabled() && day == record.date.toString() &&
            index in 0 until record.plan.blockCount && index !in record.completed &&
            timestamp > 0 && age in 0..MAX_PENDING_AGE_MS
        if (!valid && day != null) clearPending()
        return index.takeIf { valid }
    }

    /** Acknowledge the notice, not the steps themselves. */
    fun acknowledgePending() {
        clearPending()
        manager.cancelAll()
    }

    private fun clearPending() {
        settings.edit().remove(PENDING_DAY).remove(PENDING_INDEX).remove(PENDING_AT).apply()
    }

    fun setEnabled(value: Boolean) {
        settings.edit().putBoolean(ENABLED_KEY, value).apply()
        if (!value) acknowledgePending()
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
        settings.edit()
            .putString(PENDING_DAY, record.date.toString())
            .putInt(PENDING_INDEX, index)
            .putLong(PENDING_AT, System.currentTimeMillis())
            .apply()
        val openApp = PendingIntent.getActivity(
            app, 0,
            Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val steps = NumberFormat.getIntegerInstance(Locale("es", "MX"))
            .format(record.plan.stepsForBlock(index))
        val title = "¡Es momento de caminar!"
        val detail = "Bloque ${index + 1} · ${steps} pasos. Levántate y toma una pausa."
        val notification = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_walk)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(Notification.BigTextStyle().bigText(
                "$detail Abre Pasos Diarios para ver tu aviso y tu progreso."
            ))
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(Color.rgb(66, 214, 154))
            .setShowWhen(true)
            .setAutoCancel(true)
            .build()
        // Use a distinct ID for each session so Android can surface a fresh banner;
        // clear older walking reminders to avoid flooding the notification shade.
        manager.cancelAll()
        manager.notify(NOTIFICATION_ID_BASE + index, notification)
        // The visible activity can react immediately without polling.
        app.sendBroadcast(Intent(ACTION_REMINDER_SHOWN).setPackage(app.packageName))
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
        internal const val ACTION_REMINDER_SHOWN =
            "com.ostwaldviz.pasosdiarios.REMINDER_SHOWN"
        private const val CHANNEL_ID = "walking_steps_prominent_v2"
        private const val ENABLED_KEY = "enabled"
        private const val PENDING_DAY = "pending_day"
        private const val PENDING_INDEX = "pending_index"
        private const val PENDING_AT = "pending_at"
        private const val MAX_PENDING_AGE_MS = 2 * 60 * 60 * 1000L
        private const val ACTION_ALARM = "com.ostwaldviz.pasosdiarios.STEP_REMINDER"
        private const val EXTRA_DAY = "day"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_TRIGGER = "trigger"
        private const val ALARM_REQUEST_CODE = 41
        private const val NOTIFICATION_ID_BASE = 200
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
        // Accept only the documented system actions, never arbitrary broadcasts.
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> StepReminders(context).reschedule()
        }
    }
}
