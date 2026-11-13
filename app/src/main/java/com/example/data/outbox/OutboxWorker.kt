package com.example.data.outbox

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.example.data.local.FeldbuchDatabase
import java.util.concurrent.TimeUnit

class OutboxWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val dao = FeldbuchDatabase.getInstance(applicationContext).feldbuchDao()
    val sender =
      OutboxSenderRegistry.getOverrideOrNull() ?: EuleOutboxSender.fromContext(applicationContext)
    val processor = OutboxProcessor(dao = dao, sender = sender)
    val report = processor.flushQueued()
    return if (report.remainingQueuedCount > 0) {
      Result.retry()
    } else {
      Result.success()
    }
  }
}

object OutboxScheduler {
  const val UNIQUE_WORK_NAME = "feldbuch_outbox_flush"

  fun enqueueConnectedFlush(context: Context) {
    val constraints =
      Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    val request =
      OneTimeWorkRequestBuilder<OutboxWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(
          BackoffPolicy.EXPONENTIAL,
          WorkRequest.MIN_BACKOFF_MILLIS,
          TimeUnit.MILLISECONDS,
        )
        .build()

    WorkManager.getInstance(context.applicationContext)
      .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
  }
}
