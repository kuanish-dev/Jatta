package com.jatta.shared

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jatta.study.remind.Reminder
import com.jatta.study.remind.ReminderScheduler
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "jatta-reminder"
private const val CHANNEL_ID = "reminders"
private const val TEXT_KEY = "text"

class AndroidReminders(private val context: Context, private val askPermission: () -> Unit) : ReminderScheduler {

    override fun askPermission() = askPermission.invoke()

    override fun schedule(reminder: Reminder?) {
        val work = WorkManager.getInstance(context)
        if (reminder == null) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay((reminder.atMs - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(TEXT_KEY to reminder.text))
            .build()
        work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Напоминания о занятиях", NotificationManager.IMPORTANCE_DEFAULT))
        val open = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val tap = PendingIntent.getActivity(applicationContext, 0, open, PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Jatta")
            .setContentText(inputData.getString(TEXT_KEY))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        manager.notify(1, notification)
        return Result.success()
    }
}
