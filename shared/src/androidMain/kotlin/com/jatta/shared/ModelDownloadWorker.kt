package com.jatta.shared

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

private const val WORK_NAME = "jatta-model-download"
private const val CHANNEL_ID = "model-download"
private const val PROGRESS_ID = 2
private const val DONE_ID = 3
private const val DOWNLOADED_KEY = "downloaded"
private const val MAX_ATTEMPTS = 20

/**
 * Downloads Gemma's file as foreground work with a progress notification, so it goes on after the learner
 * leaves the app. A lost connection is retried once the network is back, and the file continues where it stopped.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val notifications = context.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result {
        val file = GemmaModel.file(applicationContext)
        if (file.isReady) return Result.success()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Загрузка модели", NotificationManager.IMPORTANCE_LOW))
        // Android refuses a foreground start from the background on later retries; the work then runs as usual.
        runCatching { setForeground(foreground(0)) }
        val job = coroutineContext[Job]
        val done = withContext(Dispatchers.IO) {
            try {
                file.download(isStopped = { job?.isActive == false }) { bytes ->
                    setProgressAsync(workDataOf(DOWNLOADED_KEY to bytes))
                    notifications.notify(PROGRESS_ID, progress(bytes))
                }
            } catch (e: IOException) {
                false
            }
        }
        return when {
            done -> Result.success().also { notifications.notify(DONE_ID, finished()) }
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    private fun foreground(bytes: Long): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_ID, progress(bytes), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_ID, progress(bytes))
        }

    private fun progress(bytes: Long): Notification {
        val total = GemmaModel.BYTES
        return Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Jatta скачивает Gemma 4 E2B")
            .setContentText("${bytes / 1_000_000} из ${total / 1_000_000} МБ")
            .setProgress(1000, (bytes * 1000 / total).toInt(), false)
            .setContentIntent(openApp())
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, "Отменить", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build(),
            )
            .build()
    }

    private fun finished(): Notification = Notification.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("Модель скачана")
        .setContentText("Откройте Jatta, чтобы проверить её точность")
        .setContentIntent(openApp())
        .setAutoCancel(true)
        .build()

    private fun openApp(): PendingIntent {
        val open = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        return PendingIntent.getActivity(applicationContext, 0, open, PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        /** Starts the download unless it runs already and follows it to the end; true when it finished well. */
        suspend fun follow(context: Context, onProgress: (Long) -> Unit): Boolean {
            val work = WorkManager.getInstance(context)
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            val last = work.getWorkInfosForUniqueWorkFlow(WORK_NAME)
                .mapNotNull { it.firstOrNull() }
                .onEach { info -> info.progress.getLong(DOWNLOADED_KEY, -1).takeIf { it >= 0 }?.let(onProgress) }
                .first { it.state.isFinished }
            return last.state == WorkInfo.State.SUCCEEDED
        }

        suspend fun isRunning(context: Context): Boolean =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME).first().any { !it.state.isFinished }
    }
}
