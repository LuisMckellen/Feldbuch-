package com.example.data.outbox

import com.example.data.local.OutboxEntity

sealed class OutboxSendResult {
  data object Sent : OutboxSendResult()

  data class SentWithAnswer(
    val answerText: String,
    val citationsJson: String? = null,
  ) : OutboxSendResult()

  data class Failed(val errorMessage: String) : OutboxSendResult()
}

interface OutboxSender {
  suspend fun send(item: OutboxEntity): OutboxSendResult
}

/** H2 fake sender implementation kept for unit tests and explicit fake wiring. */
class FakeOutboxSender : OutboxSender {
  override suspend fun send(item: OutboxEntity): OutboxSendResult = OutboxSendResult.Sent
}

object OutboxSenderRegistry {
  @Volatile private var overrideSender: OutboxSender? = null

  fun get(): OutboxSender = overrideSender ?: FakeOutboxSender()

  fun getOverrideOrNull(): OutboxSender? = overrideSender

  fun set(sender: OutboxSender) {
    overrideSender = sender
  }

  fun resetToDefault() {
    overrideSender = null
  }
}
