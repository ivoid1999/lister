package com.zirohill.lister

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Data
import java.util.*
import android.content.BroadcastReceiver
import android.util.Log

object ReminderApi {
    private const val CHANNEL_ID = "reminder_channel"
    private const val CHANNEL_NAME = "Reminder Channel"
    private const val CHANNEL_DESC = "Channel for reminder notifications"
    const val DEFAULT_TYPE = "Eagle"
    const val DEFAULT_MESSAGE = "Have you completed the goals & tasks today?"

    /**
     * Schedules an exact daily reminder alarm with custom type and message.
     * If `type` or `message` are null or blank, defaults are used.
     */
    fun scheduleReminder(
        context: Context,
        hour: Int,
        minute: Int,
        type: String? = null,
        message: String? = null,
    ) {
        if (!PreferencesManager.isReminderEnabled()) {
            cancelReminder(context, type)
            return
        }

        val reminderType = type?.takeIf { it.isNotBlank() } ?: DEFAULT_TYPE
        val reminderMessage = message?.takeIf { it.isNotBlank() } ?: DEFAULT_MESSAGE

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("reminder_type", reminderType)
            putExtra("reminder_message", reminderMessage)
        }
        val requestCode = reminderType.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_MONTH, 1)
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (BuildConfig.DEBUG) {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }

        val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE).edit()
        prefs.putInt("${reminderType}_hour", hour)
        prefs.putInt("${reminderType}_minute", minute)
        prefs.apply()
    }

    /**
     * Cancels a scheduled reminder by its type.
     * If `type` is null or blank, default type is used.
     */
    fun cancelReminder(context: Context, type: String?) {
        val reminderType = type?.takeIf { it.isNotBlank() } ?: DEFAULT_TYPE
        val intent = Intent(context, AlarmReceiver::class.java)
        val requestCode = reminderType.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Creates the notification channel for Android O+.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Worker class to show the notification using WorkManager for reliability.
     */
    class ReminderWorker(appContext: Context, workerParams: WorkerParameters) :
        CoroutineWorker(appContext, workerParams) {

        override suspend fun doWork(): Result {
            createNotificationChannel(applicationContext)

            val type = DEFAULT_TYPE
            val message = DEFAULT_MESSAGE

            val launchIntent =
                applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(type)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(applicationContext).notify(type.hashCode(), notification)
            return Result.success()
        }
    }
}

/**
 * BroadcastReceiver that triggers WorkManager notification when alarm fires.
 * Also reschedules the alarm for the next day.
 */
class AlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderType = intent.getStringExtra("reminder_type")?.takeIf { it.isNotBlank() }
            ?: ReminderApi.DEFAULT_TYPE
        val reminderMessage = intent.getStringExtra("reminder_message")?.takeIf { it.isNotBlank() }
            ?: ReminderApi.DEFAULT_MESSAGE

        val workRequest = OneTimeWorkRequestBuilder<ReminderApi.ReminderWorker>()
            .setInputData(
                Data.Builder()
                    .putString("reminder_type", reminderType)
                    .putString("reminder_message", reminderMessage)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)

        val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
        val hour = prefs.getInt("${reminderType}_hour", 9)
        val minute = prefs.getInt("${reminderType}_minute", 0)
        ReminderApi.scheduleReminder(context, hour, minute, reminderType, reminderMessage)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Device booted - rescheduling reminders or tasks")
        }
    }
}
