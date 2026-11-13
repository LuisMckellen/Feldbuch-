package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.local.FeldbuchDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.local.TreeFileEntity
import com.example.data.network.GitHubClient
import com.example.data.security.DecryptResult
import com.example.data.security.SecretCipher
import com.example.data.sync.GitHubTreeSyncRegistry
import com.example.data.sync.GitHubTreeSyncScheduler
import com.example.data.sync.GitHubTreeSyncWorker
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitHubTreeSyncWorkerTests {

  @get:Rule val tempFolder = TemporaryFolder()

  private class FakeSecretCipher : SecretCipher {
    override fun encrypt(plaintext: String): String = plaintext

    override fun decrypt(encoded: String): DecryptResult =
      if (encoded.isEmpty()) DecryptResult.Empty else DecryptResult.Success(encoded)
  }

  @After
  fun tearDown() {
    GitHubTreeSyncRegistry.resetToDefault()
  }

  @Test
  fun `worker syncs tree to room refreshes attached tray files and prunes blocked or deleted paths`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = FeldbuchDatabase.getInstance(context)
      withContext(Dispatchers.IO) { db.clearAllTables() }

      val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
      val server = MockWebServer()
      server.start()
      try {
        val dsFile = File(tempFolder.root, "sync_worker_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dsScope) { dsFile }
        val settingsStore = SettingsDataStore(dataStore, FakeSecretCipher())
        settingsStore.updateGitHubSettings(
          owner = "octocat",
          repo = "feldbuch",
          branch = "main",
          pat = "ghp_test_token",
        )
        // README.md is attached in tray; old/deleted.md will vanish; docs/guide.md already cached locally
        settingsStore.updateSelectedTrayPaths(setOf("README.md", "deleted.md"))
        withContext(Dispatchers.IO) {
          db.feldbuchDao()
            .insertTreeFiles(
              listOf(
                TreeFileEntity("docs/guide.md", sizeBytes = 42, cachedText = "Existing offline guide", cachedAtMs = 100L),
                TreeFileEntity("deleted.md", sizeBytes = 10, cachedText = "Old text", cachedAtMs = 100L),
              )
            )
        }

        server.enqueue(
          MockResponse()
            .setResponseCode(200)
            .setBody(
              """
              {
                "tree": [
                  {"path": "README.md", "type": "blob", "size": 120},
                  {"path": "docs/guide.md", "type": "blob", "size": 42},
                  {"path": "memory/private.md", "type": "blob", "size": 90}
                ]
              }
              """.trimIndent()
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("# Updated README offline copy"))

        GitHubTreeSyncRegistry.setOverrides(
          client = GitHubClient(baseUrl = server.url("/").toString(), enforceHttps = false),
          settingsStore = settingsStore,
        )

        val worker = TestListenableWorkerBuilder<GitHubTreeSyncWorker>(context).build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val success = result as ListenableWorker.Result.Success
        assertEquals(3, success.outputData.getInt(GitHubTreeSyncWorker.KEY_SYNCED_COUNT, -1))
        assertEquals(1, success.outputData.getInt(GitHubTreeSyncWorker.KEY_REFRESHED_TRAY_COUNT, -1))

        val storedFiles = withContext(Dispatchers.IO) { db.feldbuchDao().getAllTreeFilesOnce() }
        assertEquals(3, storedFiles.size)
        val readme = storedFiles.firstOrNull { it.path == "README.md" }
        assertEquals("# Updated README offline copy", readme?.cachedText)
        val guide = storedFiles.firstOrNull { it.path == "docs/guide.md" }
        assertEquals("Existing offline guide", guide?.cachedText)

        val updatedSettings = settingsStore.settingsFlow.first()
        assertTrue(updatedSettings.treeFetchedAtMs > 0L)
        assertEquals(setOf("README.md"), updatedSettings.selectedTrayPaths)

        val treeReq = server.takeRequest()
        assertEquals("/repos/octocat/feldbuch/git/trees/main?recursive=1", treeReq.path)
        assertEquals("Bearer ghp_test_token", treeReq.getHeader("Authorization"))
        val rawReq = server.takeRequest()
        assertEquals("/repos/octocat/feldbuch/contents/README.md?ref=main", rawReq.path)
      } finally {
        server.shutdown()
        dsScope.cancel()
        withContext(Dispatchers.IO) { db.clearAllTables() }
      }
    }
  }

  @Test
  fun `worker retries on transient 500 fails on 401 and succeeds cleanly when repo is blank`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
      val server = MockWebServer()
      server.start()
      try {
        val dsFile = File(tempFolder.root, "sync_errors.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dsScope) { dsFile }
        val settingsStore = SettingsDataStore(dataStore, FakeSecretCipher())

        GitHubTreeSyncRegistry.setOverrides(
          client = GitHubClient(baseUrl = server.url("/").toString(), enforceHttps = false),
          settingsStore = settingsStore,
        )

        // Blank repo -> immediate success with 0 synced
        val emptyWorker = TestListenableWorkerBuilder<GitHubTreeSyncWorker>(context).build()
        val emptyRes = emptyWorker.doWork()
        assertTrue(emptyRes is ListenableWorker.Result.Success)
        assertEquals(0, (emptyRes as ListenableWorker.Result.Success).outputData.getInt(GitHubTreeSyncWorker.KEY_SYNCED_COUNT, -1))
        assertEquals(0, server.requestCount)

        // Configured repo + 500 -> Retry
        settingsStore.updateGitHubSettings("octocat", "feldbuch", "main", "")
        server.enqueue(MockResponse().setResponseCode(500).setBody("server busy"))
        val worker500 = TestListenableWorkerBuilder<GitHubTreeSyncWorker>(context).build()
        assertTrue(worker500.doWork() is ListenableWorker.Result.Retry)

        // Configured repo + 401 -> Failure
        server.enqueue(MockResponse().setResponseCode(401).setBody("bad token"))
        val worker401 = TestListenableWorkerBuilder<GitHubTreeSyncWorker>(context).build()
        assertTrue(worker401.doWork() is ListenableWorker.Result.Failure)
      } finally {
        server.shutdown()
        dsScope.cancel()
      }
    }
  }

  @Test
  fun `scheduler enqueues and cancels periodic and immediate work with connected constraints`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        Configuration.Builder().build(),
      )
      val wm = WorkManager.getInstance(context)

      GitHubTreeSyncScheduler.schedulePeriodicSync(context, intervalHours = 12L, enabled = true)
      val periodicInfos =
        wm.getWorkInfosForUniqueWork(GitHubTreeSyncScheduler.PERIODIC_WORK_NAME).get()
      assertEquals(1, periodicInfos.size)
      assertEquals(WorkInfo.State.ENQUEUED, periodicInfos[0].state)
      assertEquals(NetworkType.CONNECTED, periodicInfos[0].constraints.requiredNetworkType)
      assertTrue(periodicInfos[0].constraints.requiresBatteryNotLow())

      GitHubTreeSyncScheduler.enqueueImmediateSync(context)
      val immediateInfos =
        wm.getWorkInfosForUniqueWork(GitHubTreeSyncScheduler.IMMEDIATE_WORK_NAME).get()
      assertEquals(1, immediateInfos.size)
      assertEquals(NetworkType.CONNECTED, immediateInfos[0].constraints.requiredNetworkType)

      GitHubTreeSyncScheduler.schedulePeriodicSync(context, intervalHours = 12L, enabled = false)
      val afterCancel =
        wm.getWorkInfosForUniqueWork(GitHubTreeSyncScheduler.PERIODIC_WORK_NAME).get()
      assertEquals(WorkInfo.State.CANCELLED, afterCancel[0].state)
      assertNotNull(afterCancel[0])

      // Verify SettingsDataStore interval persistence
      val dsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
      try {
        val dsFile = File(tempFolder.root, "sync_prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = dsScope) { dsFile }
        val store = SettingsDataStore(dataStore, FakeSecretCipher())
        store.updateTreeSyncPreferences(enabled = false, intervalHours = 12L)
        val loaded = store.settingsFlow.first()
        assertFalse(loaded.treeAutoSyncEnabled)
        assertEquals(12L, loaded.treeSyncIntervalHours)
      } finally {
        dsScope.cancel()
      }
    }
  }
}
