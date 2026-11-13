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

  @Query("SELECT * FROM chat_messages WHERE includedInNote = 0 ORDER BY timestampMs ASC, id ASC")
  fun observeUnsentMessages(): Flow<List<ChatMessageEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: ChatMessageEntity): Long

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
}
