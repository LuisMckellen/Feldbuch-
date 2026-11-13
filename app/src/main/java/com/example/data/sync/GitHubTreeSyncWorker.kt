package com.example.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.FeldbuchDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.local.TreeFileEntity
import com.example.data.network.GitHubClient
import com.example.domain.PathBlocklist
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

object GitHubTreeSyncRegistry {
  @Volatile private var clientOverride: GitHubClient? = null
  @Volatile private var settingsStoreOverride: SettingsDataStore? = null

  fun getClient(): GitHubClient = clientOverride ?: GitHubClient()

  fun getSettingsStore(context: Context): SettingsDataStore =
    settingsStoreOverride ?: SettingsDataStore(context.applicationContext)

  fun setOverrides(
    client: GitHubClient? = null,
    settingsStore: SettingsDataStore? = null,
  ) {
    clientOverride = client
    settingsStoreOverride = settingsStore
  }

  fun resetToDefault() {
    clientOverride = null
    settingsStoreOverride = null
  }
}

class GitHubTreeSyncWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val settingsStore = GitHubTreeSyncRegistry.getSettingsStore(applicationContext)
    val settings = settingsStore.settingsFlow.first()

    val owner = settings.githubOwner.trim()
    val repo = settings.githubRepo.trim()
    if (owner.isEmpty() || repo.isEmpty()) {
      return Result.success(workDataOf(KEY_SYNCED_COUNT to 0))
    }

    val branch = settings.githubBranch.trim().ifBlank { "main" }
    val token = settings.githubOAuthToken.ifBlank { settings.githubPat }
    val client = GitHubTreeSyncRegistry.getClient()

    val treeResult =
      client.fetchTree(
        owner = owner,
        repo = repo,
        branch = branch,
        pat = token,
      )

    return treeResult.fold(
      onSuccess = { entries ->
        val dao = FeldbuchDatabase.getInstance(applicationContext).feldbuchDao()
        val entities = entries.map { TreeFileEntity(path = it.path, sizeBytes = it.sizeBytes) }
        dao.replaceTreePreservingCachedContent(entities)

        val validAllowedPaths = PathBlocklist.filterAllowed(entities.map { it.path }.toSet())
        val currentTray = PathBlocklist.filterAllowed(settings.selectedTrayPaths)
        val retainedTray = currentTray.intersect(validAllowedPaths)
        if (retainedTray != settings.selectedTrayPaths) {
          settingsStore.updateSelectedTrayPaths(retainedTray)
        }

        // Keep already-attached non-blocked files refreshed for offline access too.
        var refreshedTrayCount = 0
        for (path in retainedTray) {
          val rawRes =
            client.fetchRawFile(
              owner = owner,
              repo = repo,
              branch = branch,
              path = path,
              pat = token,
            )
          rawRes.onSuccess { text ->
            dao.updateCachedContent(
              path = path,
              text = text,
              cachedAtMs = System.currentTimeMillis(),
            )
            refreshedTrayCount++
          }
        }

        settingsStore.updateTreeFetchedAt(System.currentTimeMillis())
        Result.success(
          workDataOf(
            KEY_SYNCED_COUNT to entities.size,
            KEY_REFRESHED_TRAY_COUNT to refreshedTrayCount,
          )
        )
      },
      onFailure = { err ->
        val msg = err.message.orEmpty()
        val isPermanentAuthOrNotFound =
          msg.contains("HTTP 401") || msg.contains("HTTP 403") || msg.contains("HTTP 404")
        if (isPermanentAuthOrNotFound || runAttemptCount >= MAX_RETRY_ATTEMPTS) {
          Result.failure(workDataOf(KEY_ERROR to msg))
        } else {
          Result.retry()
        }
      },
    )
  }

  companion object {
    const val KEY_SYNCED_COUNT = "synced_count"
    const val KEY_REFRESHED_TRAY_COUNT = "refreshed_tray_count"
    const val KEY_ERROR = "error"
    private const val MAX_RETRY_ATTEMPTS = 4
  }
}

object GitHubTreeSyncScheduler {
  const val PERIODIC_WORK_NAME = "feldbuch_github_tree_periodic_sync"
  const val IMMEDIATE_WORK_NAME = "feldbuch_github_tree_immediate_sync"

  fun schedulePeriodicSync(
    context: Context,
    intervalHours: Long = 6L,
    enabled: Boolean = true,
  ) {
    val wm = WorkManager.getInstance(context.applicationContext)
    if (!enabled) {
      wm.cancelUniqueWork(PERIODIC_WORK_NAME)
      return
    }

    val safeHours = intervalHours.coerceIn(1L, 24L)
    val constraints =
      Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    val request =
      PeriodicWorkRequestBuilder<GitHubTreeSyncWorker>(safeHours, TimeUnit.HOURS)
        .setConstraints(constraints)
        .setBackoffCriteria(
          BackoffPolicy.EXPONENTIAL,
          WorkRequest.MIN_BACKOFF_MILLIS,
          TimeUnit.MILLISECONDS,
        )
        .build()

    wm.enqueueUniquePeriodicWork(
      PERIODIC_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  fun enqueueImmediateSync(context: Context) {
    val constraints =
      Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    val request =
      OneTimeWorkRequestBuilder<GitHubTreeSyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(
          BackoffPolicy.EXPONENTIAL,
          WorkRequest.MIN_BACKOFF_MILLIS,
          TimeUnit.MILLISECONDS,
        )
        .build()

    WorkManager.getInstance(context.applicationContext)
      .enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
  }

  fun cancelPeriodicSync(context: Context) {
    WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
  }
}
