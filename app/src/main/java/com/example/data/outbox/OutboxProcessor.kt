package com.example.data.outbox

import com.example.data.local.FeldbuchDao
import com.example.data.local.OutboxEntity
import com.example.data.local.OutboxKind
import com.example.data.local.OutboxState
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OutboxFlushReport(
  val sentCount: Int = 0,
  val permanentlyFailedCount: Int = 0,
  val remainingQueuedCount: Int = 0,
)

class OutboxProcessor(
  private val dao: FeldbuchDao,
  private val sender: OutboxSender = OutboxSenderRegistry.get(),
) {
  private val flushMutex = Mutex()

  suspend fun recoverStuckOnStartup(): Int = dao.resetStuckSendingToQueued()

  suspend fun enqueueCapture(
    kind: OutboxKind,
    body: String,
    id: String = UUID.randomUUID().toString(),
    createdAt: Long = System.currentTimeMillis(),
  ): OutboxEntity {
    val cleanBody = body.trim()
    require(cleanBody.isNotEmpty()) { "Capture body cannot be blank." }
    val item =
      OutboxEntity(
        id = id,
        kind = kind.wireValue,
        body = cleanBody,
        createdAt = createdAt,
        state = OutboxState.QUEUED.wireValue,
        attempts = 0,
        lastError = null,
      )
    dao.insertOutboxItem(item)
    return item
  }

  suspend fun retryFailed(id: String): Boolean = dao.resetFailedItemToQueued(id) > 0

  suspend fun clearSent() {
    dao.deleteSentOutboxItems()
  }

  suspend fun flushQueued(): OutboxFlushReport =
    flushMutex.withLock {
      val queuedItems = dao.getQueuedOutboxItemsOnce()
      var sentCount = 0
      var failedPermanently = 0
      var remainingQueued = 0

      for (item in queuedItems) {
        // Atomic transition queued -> sending guarantees the same UUID is never sent twice.
        val claimedRows = dao.claimQueuedItemForSending(item.id)
        if (claimedRows == 0) continue

        val result =
          try {
            sender.send(item)
          } catch (t: Throwable) {
            OutboxSendResult.Failed(t.message ?: "Unexpected outbox error")
          }

        when (result) {
          is OutboxSendResult.Sent -> {
            dao.markOutboxSent(item.id)
            sentCount++
          }
          is OutboxSendResult.SentWithAnswer -> {
            dao.markOutboxSentWithAnswer(
              id = item.id,
              answerText = result.answerText,
              citationsJson = result.citationsJson,
            )
            sentCount++
          }
          is OutboxSendResult.Failed -> {
            val nextAttempts = item.attempts + 1
            if (nextAttempts >= MAX_ATTEMPTS) {
              dao.markOutboxAttemptFailed(
                id = item.id,
                nextState = OutboxState.FAILED.wireValue,
                attempts = nextAttempts,
                lastError = result.errorMessage,
              )
              failedPermanently++
            } else {
              dao.markOutboxAttemptFailed(
                id = item.id,
                nextState = OutboxState.QUEUED.wireValue,
                attempts = nextAttempts,
                lastError = result.errorMessage,
              )
              remainingQueued++
            }
          }
        }
      }

      OutboxFlushReport(
        sentCount = sentCount,
        permanentlyFailedCount = failedPermanently,
        remainingQueuedCount = remainingQueued,
      )
    }

  companion object {
    const val MAX_ATTEMPTS = 8
  }
}
