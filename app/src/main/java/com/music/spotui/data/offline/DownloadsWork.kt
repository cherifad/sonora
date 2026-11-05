package com.music.spotui.data.offline

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.myspoti.engine.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps downloads going when the app is not in front (or was killed): the queue lives in
 * [OfflineDownloader] and its `pending.json`, this worker resumes it under WorkManager and
 * shows the progress in a notification. Downloads already run in-process; the worker only
 * holds the process and resumes what a restart left behind.
 */
object DownloadsWork {
    private const val WORK = "offline-downloads"

    /** Schedules the download worker (network required, replaces a finished run). */
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }

    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK) }
    }
}

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!OfflineStore.pendingFile.exists()) return Result.success()
        val remaining = java.util.concurrent.atomic.AtomicInteger(0)
        val progress = java.util.concurrent.atomic.AtomicReference(-1f)
        val ticker = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
        ).launch {
            while (isActive) {
                runCatching { setForeground(foreground(context, remaining.get(), progress.get())) }
                delay(1_000)
            }
        }
        return try {
            setForeground(foreground(context, 0, -1f))
            OfflineDownloader.awaitPending { left, p -> remaining.set(left); progress.set(p) }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // The queue file still holds the requests: a retry picks them up.
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally {
            ticker.cancel()
        }
    }

    private fun foreground(context: Context, remaining: Int, progress: Float): ForegroundInfo {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(CHANNEL) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (remaining > 0) context.resources.getQuantityString(R.plurals.download_notification_count, remaining, remaining)
        else context.getString(R.string.download_notification_downloading)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.msp_ic_notification)
            .setContentTitle(title)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (progress >= 0f) builder.setProgress(100, (progress * 100).toInt(), false)
        else builder.setProgress(0, 0, true)
        return ForegroundInfo(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val NOTIFICATION_ID = 0x4D53
    }
}
