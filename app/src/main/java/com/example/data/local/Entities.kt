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
