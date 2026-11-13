package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FeldbuchDao {
  @Query("SELECT * FROM tree_files ORDER BY path ASC")
  fun observeAllTreeFiles(): Flow<List<TreeFileEntity>>

  @Query("SELECT * FROM tree_files")
  suspend fun getAllTreeFilesOnce(): List<TreeFileEntity>

  @Query("DELETE FROM tree_files")
  suspend fun deleteAllTreeFiles()

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTreeFiles(files: List<TreeFileEntity>)

  @Query("UPDATE tree_files SET cachedText = :text, cachedAtMs = :cachedAtMs WHERE path = :path")
  suspend fun updateCachedContent(path: String, text: String, cachedAtMs: Long)

  @Transaction
  suspend fun replaceTreePreservingCachedContent(newEntries: List<TreeFileEntity>) {
    val existingByPath = getAllTreeFilesOnce().associateBy { it.path }
    val merged =
      newEntries.map { entry ->
        val old = existingByPath[entry.path]
        if (old?.cachedText != null) {
          entry.copy(cachedText = old.cachedText, cachedAtMs = old.cachedAtMs)
        } else {
          entry
        }
      }
    deleteAllTreeFiles()
    if (merged.isNotEmpty()) {
      insertTreeFiles(merged)
    }
  }

  @Query("SELECT * FROM chat_messages ORDER BY timestampMs ASC, id ASC")
  fun observeAllMessages(): Flow<List<ChatMessageEntity>>

  @Query("SELECT * FROM chat_messages WHERE includedInNote = 0 AND errorReason IS NULL ORDER BY timestampMs ASC, id ASC")
  fun observeUnsentMessages(): Flow<List<ChatMessageEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: ChatMessageEntity): Long

  @Query("UPDATE chat_messages SET errorReason = :reason, errorDetail = :detail WHERE id = :id")
  suspend fun updateMessageError(id: Long, reason: String?, detail: String?)

  @Query("UPDATE chat_messages SET contextPaths = :contextPaths, errorReason = NULL, errorDetail = NULL WHERE id = :id")
  suspend fun clearMessageErrorAndUpdateContext(id: Long, contextPaths: String)

  @Query("UPDATE chat_messages SET includedInNote = 1 WHERE id IN (:ids)")
  suspend fun markMessagesIncluded(ids: List<Long>)

  @Query("DELETE FROM chat_messages")
  suspend fun clearAllMessages()

  @Query("SELECT * FROM session_notes WHERE isCurrentDraft = 1 ORDER BY createdAtMs DESC LIMIT 1")
  fun observeCurrentDraft(): Flow<SessionNoteEntity?>

  @Query("SELECT * FROM session_notes ORDER BY createdAtMs DESC")
  fun observeAllSessionNotes(): Flow<List<SessionNoteEntity>>

  @Query("UPDATE session_notes SET isCurrentDraft = 0 WHERE isCurrentDraft = 1")
  suspend fun archivePreviousDrafts()

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSessionNote(note: SessionNoteEntity): Long

  @Query("UPDATE session_notes SET draftText = :newText WHERE id = :id")
  suspend fun updateDraftText(id: Long, newText: String)

  @Query("DELETE FROM session_notes")
  suspend fun clearAllSessionNotes()

  @Query("SELECT * FROM outbox ORDER BY createdAt DESC")
  fun observeOutboxItems(): Flow<List<OutboxEntity>>

  @Query("SELECT * FROM outbox ORDER BY createdAt DESC")
  suspend fun getAllOutboxItemsOnce(): List<OutboxEntity>

  @Query("SELECT * FROM outbox WHERE state = 'queued' ORDER BY createdAt ASC")
  suspend fun getQueuedOutboxItemsOnce(): List<OutboxEntity>

  @Query("SELECT * FROM outbox WHERE id = :id LIMIT 1")
  suspend fun getOutboxItemById(id: String): OutboxEntity?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertOutboxItem(item: OutboxEntity)

  @Query("UPDATE outbox SET state = 'sending' WHERE id = :id AND state = 'queued'")
  suspend fun claimQueuedItemForSending(id: String): Int

  @Query("UPDATE outbox SET state = 'sent', lastError = NULL WHERE id = :id")
  suspend fun markOutboxSent(id: String)

  @Query(
    "UPDATE outbox SET state = 'sent', lastError = NULL, answerText = :answerText, citationsJson = :citationsJson WHERE id = :id"
  )
  suspend fun markOutboxSentWithAnswer(id: String, answerText: String, citationsJson: String?)

  @Query("UPDATE outbox SET state = :nextState, attempts = :attempts, lastError = :lastError WHERE id = :id")
  suspend fun markOutboxAttemptFailed(
    id: String,
    nextState: String,
    attempts: Int,
    lastError: String?,
  )

  @Query("UPDATE outbox SET state = 'queued', attempts = 0, lastError = NULL WHERE id = :id AND state = 'failed'")
  suspend fun resetFailedItemToQueued(id: String): Int

  @Query("UPDATE outbox SET state = 'queued' WHERE state = 'sending'")
  suspend fun resetStuckSendingToQueued(): Int

  @Query("DELETE FROM outbox WHERE state = 'sent'")
  suspend fun deleteSentOutboxItems()
}
