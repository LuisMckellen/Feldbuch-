package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.local.FeldbuchDatabase
import com.example.data.local.OutboxEntity
import com.example.data.local.OutboxKind
import com.example.data.local.OutboxState
import com.example.data.outbox.FakeOutboxSender
import com.example.data.outbox.OutboxProcessor
import com.example.data.outbox.OutboxScheduler
import com.example.data.outbox.OutboxSendResult
import com.example.data.outbox.OutboxSender
import com.example.data.outbox.OutboxSenderRegistry
import com.example.data.outbox.OutboxWorker
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class H2OutboxTests {

  @get:Rule val tempFolder = TemporaryFolder()

  @After
  fun tearDown() {
    OutboxSenderRegistry.resetToDefault()
  }

  private fun createInMemoryDb(): FeldbuchDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, FeldbuchDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @Test
  fun `enqueueCapture writes offline item to outbox and FakeOutboxSender marks queued to sending to sent`() =
    runBlocking {
      val db = createInMemoryDb()
      try {
        val dao = db.feldbuchDao()
        val observedStateDuringSend = mutableListOf<String>()
        val inspectingSender =
          object : OutboxSender {
            override suspend fun send(item: OutboxEntity): OutboxSendResult {
              val inDb = dao.getOutboxItemById(item.id)
              if (inDb != null) {
                observedStateDuringSend += inDb.state
              }
              return FakeOutboxSender().send(item)
            }
          }

        val processor = OutboxProcessor(dao = dao, sender = inspectingSender)
        val note = processor.enqueueCapture(OutboxKind.NOTE, "  Check roof bolts  ")
        val idea = processor.enqueueCapture(OutboxKind.IDEA, "Add voice memo tag")
        val question = processor.enqueueCapture(OutboxKind.QUESTION, "When was Eule synced?")

        // UUID validity and initial queued state before flush
        UUID.fromString(note.id)
        assertEquals("Check roof bolts", note.body)
        assertEquals(OutboxKind.NOTE.wireValue, note.kind)
        assertEquals(OutboxState.QUEUED.wireValue, note.state)
        assertEquals(0, note.attempts)
        assertNull(note.lastError)
        assertEquals(3, dao.getQueuedOutboxItemsOnce().size)

        val report = processor.flushQueued()
        assertEquals(3, report.sentCount)
        assertEquals(0, report.remainingQueuedCount)
        assertEquals(0, report.permanentlyFailedCount)

        // Observed transition through sending -> sent
        assertEquals(listOf("sending", "sending", "sending"), observedStateDuringSend)

        val all = dao.getAllOutboxItemsOnce()
        assertEquals(3, all.size)
        assertTrue(all.all { it.state == OutboxState.SENT.wireValue })
        assertEquals(
          setOf(note.id, idea.id, question.id),
          all.map { it.id }.toSet(),
        )
      } finally {
        db.close()
      }
    }

  @Test
  fun `state machine retries up to 8 attempts then marks failed and retryFailed requeues`() =
    runBlocking {
      val db = createInMemoryDb()
      try {
        val dao = db.feldbuchDao()
        var callCount = 0
        var shouldFail = true
        val controllableSender =
          object : OutboxSender {
            override suspend fun send(item: OutboxEntity): OutboxSendResult {
              callCount++
              return if (shouldFail) {
                OutboxSendResult.Failed("Eule unreachable (#$callCount)")
              } else {
                FakeOutboxSender().send(item)
              }
            }
          }

        val processor = OutboxProcessor(dao = dao, sender = controllableSender)
        val item = processor.enqueueCapture(OutboxKind.QUESTION, "Offline site status?")

        // Attempts 1..7 keep item in queued state with incrementing attempts & lastError
        for (attempt in 1 until OutboxProcessor.MAX_ATTEMPTS) {
          val report = processor.flushQueued()
          assertEquals(0, report.sentCount)
          assertEquals(1, report.remainingQueuedCount)
          assertEquals(0, report.permanentlyFailedCount)

          val stored = dao.getOutboxItemById(item.id)!!
          assertEquals(OutboxState.QUEUED.wireValue, stored.state)
          assertEquals(attempt, stored.attempts)
          assertEquals("Eule unreachable (#$attempt)", stored.lastError)
        }

        // Attempt 8 transitions to permanent failed state
        val eighthReport = processor.flushQueued()
        assertEquals(0, eighthReport.sentCount)
        assertEquals(0, eighthReport.remainingQueuedCount)
        assertEquals(1, eighthReport.permanentlyFailedCount)

        val failedStored = dao.getOutboxItemById(item.id)!!
        assertEquals(OutboxState.FAILED.wireValue, failedStored.state)
        assertEquals(OutboxProcessor.MAX_ATTEMPTS, failedStored.attempts)
        assertEquals("Eule unreachable (#8)", failedStored.lastError)

        // Failed items are not retried automatically on subsequent flushes
        val noopReport = processor.flushQueued()
        assertEquals(0, noopReport.sentCount)
        assertEquals(8, callCount)

        // Manual "Send again" transitions failed -> queued and resets attempts
        assertTrue(processor.retryFailed(item.id))
        val requeuedStored = dao.getOutboxItemById(item.id)!!
        assertEquals(OutboxState.QUEUED.wireValue, requeuedStored.state)
        assertEquals(0, requeuedStored.attempts)
        assertNull(requeuedStored.lastError)

        // Network/sender recovers -> sent
        shouldFail = false
        val recoveryReport = processor.flushQueued()
        assertEquals(1, recoveryReport.sentCount)
        assertEquals(9, callCount)
        assertEquals(OutboxState.SENT.wireValue, dao.getOutboxItemById(item.id)!!.state)
      } finally {
        db.close()
      }
    }

  @Test
  fun `concurrent flushes and duplicate db claims never double send the same id`() =
    runBlocking {
      val db = createInMemoryDb()
      try {
        val dao = db.feldbuchDao()
        val sentIds = CopyOnWriteArrayList<String>()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val slowSender =
          object : OutboxSender {
            override suspend fun send(item: OutboxEntity): OutboxSendResult {
              sentIds.add(item.id)
              firstStarted.complete(Unit)
              releaseFirst.await()
              return OutboxSendResult.Sent
            }
          }

        val processorA = OutboxProcessor(dao = dao, sender = slowSender)
        val processorB = OutboxProcessor(dao = dao, sender = slowSender)
        val item = processorA.enqueueCapture(OutboxKind.NOTE, "Single-delivery note")

        val jobA = async(Dispatchers.IO) { processorA.flushQueued() }
        firstStarted.await()
        // While processorA holds the item in 'sending', processorB also attempts to flush
        val jobB = async(Dispatchers.IO) { processorB.flushQueued() }
        delay(50)
        releaseFirst.complete(Unit)

        val reports = awaitAll(jobA, jobB)
        assertEquals(1, reports.sumOf { it.sentCount })
        assertEquals(listOf(item.id), sentIds.toList())
        assertEquals(OutboxState.SENT.wireValue, dao.getOutboxItemById(item.id)!!.state)

        // Direct atomic claim check on a newly queued item
        val item2 = processorA.enqueueCapture(OutboxKind.IDEA, "Atomic claim check")
        assertEquals(1, dao.claimQueuedItemForSending(item2.id))
        assertEquals(0, dao.claimQueuedItemForSending(item2.id))
      } finally {
        db.close()
      }
    }

  @Test
  fun `outbox survives app restart and resets stuck sending items back to queued on startup`() =
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val dbFile = File(tempFolder.root, "restart_outbox.db")

      // Session 1: write queued item + simulate crash leaving another item stuck in 'sending'
      val db1 =
        Room.databaseBuilder(context, FeldbuchDatabase::class.java, dbFile.absolutePath)
          .allowMainThreadQueries()
          .build()
      val dao1 = db1.feldbuchDao()
      val processor1 = OutboxProcessor(dao = dao1, sender = FakeOutboxSender())

      val queuedItem = processor1.enqueueCapture(OutboxKind.NOTE, "Persisted queued note")
      val stuckItem = processor1.enqueueCapture(OutboxKind.IDEA, "Interrupted mid-send idea")
      assertEquals(1, dao1.claimQueuedItemForSending(stuckItem.id))
      assertEquals(OutboxState.SENDING.wireValue, dao1.getOutboxItemById(stuckItem.id)!!.state)
      db1.close()

      // Session 2: reopen same database file after restart
      val db2 =
        Room.databaseBuilder(context, FeldbuchDatabase::class.java, dbFile.absolutePath)
          .allowMainThreadQueries()
          .build()
      try {
        val dao2 = db2.feldbuchDao()
        val processor2 = OutboxProcessor(dao = dao2, sender = FakeOutboxSender())

        val recoveredCount = processor2.recoverStuckOnStartup()
        assertEquals(1, recoveredCount)

        val queuedAfterRestart = dao2.getOutboxItemById(queuedItem.id)
        val stuckAfterRestart = dao2.getOutboxItemById(stuckItem.id)
        assertNotNull(queuedAfterRestart)
        assertNotNull(stuckAfterRestart)
        assertEquals(OutboxState.QUEUED.wireValue, queuedAfterRestart!!.state)
        assertEquals(OutboxState.QUEUED.wireValue, stuckAfterRestart!!.state)

        // Both items flush cleanly to 'sent'
        val report = processor2.flushQueued()
        assertEquals(2, report.sentCount)
        assertEquals(OutboxState.SENT.wireValue, dao2.getOutboxItemById(queuedItem.id)!!.state)
        assertEquals(OutboxState.SENT.wireValue, dao2.getOutboxItemById(stuckItem.id)!!.state)
      } finally {
        db2.close()
      }
    }

  @Test
  fun `OutboxWorker retries on transient failure succeeds with FakeOutboxSender and OutboxScheduler enforces connected network`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val config = Configuration.Builder().build()
      WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

      val db = FeldbuchDatabase.getInstance(context)
      withContext(Dispatchers.IO) { db.clearAllTables() }
      val dao = db.feldbuchDao()

      val processor = OutboxProcessor(dao = dao, sender = FakeOutboxSender())
      val item = processor.enqueueCapture(OutboxKind.NOTE, "Worker delivery note")

      // 1. Transient failure -> Result.retry() and attempts incremented while staying queued
      OutboxSenderRegistry.set(
        object : OutboxSender {
          override suspend fun send(item: OutboxEntity): OutboxSendResult =
            OutboxSendResult.Failed("Temporary 503")
        }
      )
      val workerAttempt1 = TestListenableWorkerBuilder.from(context, OutboxWorker::class.java).build()
      val result1 = workerAttempt1.doWork()
      assertEquals(ListenableWorker.Result.retry(), result1)

      val afterRetry = dao.getOutboxItemById(item.id)!!
      assertEquals(OutboxState.QUEUED.wireValue, afterRetry.state)
      assertEquals(1, afterRetry.attempts)
      assertEquals("Temporary 503", afterRetry.lastError)

      // 2. FakeOutboxSender -> Result.success() and state becomes sent
      OutboxSenderRegistry.set(FakeOutboxSender())
      val workerAttempt2 = TestListenableWorkerBuilder.from(context, OutboxWorker::class.java).build()
      val result2 = workerAttempt2.doWork()
      assertEquals(ListenableWorker.Result.success(), result2)

      val afterSuccess = dao.getOutboxItemById(item.id)!!
      assertEquals(OutboxState.SENT.wireValue, afterSuccess.state)

      // 3. Scheduler enqueues unique connected-network work
      OutboxScheduler.enqueueConnectedFlush(context)
      val workInfos =
        WorkManager.getInstance(context)
          .getWorkInfosForUniqueWork(OutboxScheduler.UNIQUE_WORK_NAME)
          .get()
      assertEquals(1, workInfos.size)
      val info = workInfos[0]
      assertTrue(
        info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING
      )
      assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
      assertTrue(info.tags.contains(OutboxWorker::class.java.name))
      // Cancel test work cleanly
      WorkManager.getInstance(context).cancelUniqueWork(OutboxScheduler.UNIQUE_WORK_NAME).result.get()
    }
  }
}
