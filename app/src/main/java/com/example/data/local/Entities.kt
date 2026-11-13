package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tree_files")
data class TreeFileEntity(
  @PrimaryKey val path: String,
  val sizeBytes: Long = 0L,
  val cachedText: String? = null,
  val cachedAtMs: Long = 0L,
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val role: String, // "user" or "assistant"
  val content: String,
  val timestampMs: Long,
  val contextPaths: String = "",
  val includedInNote: Boolean = false,
  val errorReason: String? = null,
  val errorDetail: String? = null,
)

@Entity(tableName = "session_notes")
data class SessionNoteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val draftText: String,
  val messageCount: Int,
  val startTimestampMs: Long,
  val endTimestampMs: Long,
  val createdAtMs: Long,
  val isCurrentDraft: Boolean = true,
)

enum class OutboxKind(val wireValue: String, val label: String) {
  NOTE("note", "Note"),
  IDEA("idea", "Idea"),
  QUESTION("question", "Question");

  companion object {
    fun fromWire(value: String): OutboxKind =
      entries.firstOrNull { it.wireValue == value } ?: NOTE
  }
}

enum class OutboxState(val wireValue: String, val label: String) {
  QUEUED("queued", "Queued"),
  SENDING("sending", "Sending"),
  SENT("sent", "Sent"),
  FAILED("failed", "Failed");

  companion object {
    fun fromWire(value: String): OutboxState =
      entries.firstOrNull { it.wireValue == value } ?: QUEUED
  }
}

@Entity(tableName = "outbox")
data class OutboxEntity(
  @PrimaryKey val id: String,
  val kind: String, // "note" | "idea" | "question"
  val body: String,
  val createdAt: Long,
  val state: String, // "queued" | "sending" | "sent" | "failed"
  val attempts: Int = 0,
  val lastError: String? = null,
  val answerText: String? = null,
  val citationsJson: String? = null,
)
