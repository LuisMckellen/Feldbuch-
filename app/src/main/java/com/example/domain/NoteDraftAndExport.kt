package com.example.domain

import com.example.data.local.ChatMessageEntity
import com.example.data.local.FeldbuchSettings
import com.example.data.local.SessionNoteEntity
import com.example.data.network.ApiChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object NoteDraftPrompt {
  fun buildMessagesForDraft(unsentMessages: List<ChatMessageEntity>): List<ApiChatMessage> {
    val system =
      """
      You summarize a field brainstorming chat into a concise session note.
      Return ONLY the following three lines in plain text, with no extra markdown, no code fences, and no commentary:
      What I was thinking about: <one line>
      What I concluded: <only what the user said, or "nothing settled">
      Still open / what to weigh: <the questions left>
      """
        .trimIndent()

    val transcript =
      unsentMessages.joinToString("\n") { msg ->
        val speaker = if (msg.role == "user") "USER" else "ASSISTANT"
        "[$speaker] ${msg.content}"
      }

    val user =
      """
      Summarize these ${unsentMessages.size} brainstorming messages into the exact three-line format.
      Remember: for "What I concluded:", report ONLY conclusions explicitly stated by USER; if USER did not state a conclusion, write exactly "nothing settled".

      Transcript:
      $transcript
      """
        .trimIndent()

    return listOf(
      ApiChatMessage(role = "system", content = system),
      ApiChatMessage(role = "user", content = user),
    )
  }

  fun formatForCopy(
    draftText: String,
    messageCount: Int,
    startMs: Long,
    endMs: Long,
  ): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val startStr = fmt.format(Date(startMs))
    val endFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val endStr =
      if (SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startMs)) ==
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(endMs))
      ) {
        endFmt.format(Date(endMs))
      } else {
        fmt.format(Date(endMs))
      }
    return "${draftText.trim()}\n$messageCount messages, $startStr–$endStr"
  }
}

object ExportSerializer {
  fun buildExportJson(
    settings: FeldbuchSettings,
    treeFileCount: Int,
    messages: List<ChatMessageEntity>,
    sessionNotes: List<SessionNoteEntity>,
  ): String {
    val root = JSONObject()
    root.put("exportedAtMs", System.currentTimeMillis())
    // Note: PAT and API key are intentionally omitted from exports.
    root.put(
      "repo",
      JSONObject().apply {
        put("owner", settings.githubOwner)
        put("repo", settings.githubRepo)
        put("branch", settings.githubBranch)
        put("treeFetchedAtMs", settings.treeFetchedAtMs)
        put("fileCount", treeFileCount)
      },
    )

    val msgArr = JSONArray()
    messages.forEach { m ->
      msgArr.put(
        JSONObject().apply {
          put("id", m.id)
          put("role", m.role)
          put("content", m.content)
          put("timestampMs", m.timestampMs)
          put("contextPaths", m.contextPaths)
          put("includedInNote", m.includedInNote)
        }
      )
    }
    root.put("messages", msgArr)

    val notesArr = JSONArray()
    sessionNotes.forEach { n ->
      notesArr.put(
        JSONObject().apply {
          put("id", n.id)
          put("draftText", n.draftText)
          put("messageCount", n.messageCount)
          put("startTimestampMs", n.startTimestampMs)
          put("endTimestampMs", n.endTimestampMs)
          put("createdAtMs", n.createdAtMs)
          put("isCurrentDraft", n.isCurrentDraft)
        }
      )
    }
    root.put("sessionNotes", notesArr)

    return root.toString(2)
  }

  fun buildSessionNotesMarkdown(
    settings: FeldbuchSettings,
    editableDraftText: String,
    currentDraft: SessionNoteEntity?,
    allSessionNotes: List<SessionNoteEntity>,
    fallbackMessageCount: Int,
    fallbackStartMs: Long,
    fallbackEndMs: Long,
  ): String {
    val entries =
      resolveExportEntries(
        editableDraftText = editableDraftText,
        currentDraft = currentDraft,
        allSessionNotes = allSessionNotes,
        fallbackMessageCount = fallbackMessageCount,
        fallbackStartMs = fallbackStartMs,
        fallbackEndMs = fallbackEndMs,
      )
    val sb = StringBuilder()
    sb.appendLine("# Feldbuch Session Notes")
    if (settings.githubOwner.isNotBlank() && settings.githubRepo.isNotBlank()) {
      sb.appendLine(
        "> Repo: `${settings.githubOwner}/${settings.githubRepo}` (${settings.githubBranch})"
      )
    }
    sb.appendLine()

    entries.forEachIndexed { index, entry ->
      if (index > 0) sb.appendLine("\n---\n")
      val createdFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
      val createdStr = createdFmt.format(Date(entry.createdAtMs))
      val formattedBody =
        NoteDraftPrompt.formatForCopy(
          draftText = entry.text,
          messageCount = entry.messageCount,
          startMs = entry.startMs,
          endMs = entry.endMs,
        )
      sb.appendLine("## Session Note · $createdStr")
      sb.appendLine()
      sb.appendLine(formattedBody)
    }
    return sb.toString().trimEnd() + "\n"
  }

  fun buildSessionNotesText(
    settings: FeldbuchSettings,
    editableDraftText: String,
    currentDraft: SessionNoteEntity?,
    allSessionNotes: List<SessionNoteEntity>,
    fallbackMessageCount: Int,
    fallbackStartMs: Long,
    fallbackEndMs: Long,
  ): String {
    val entries =
      resolveExportEntries(
        editableDraftText = editableDraftText,
        currentDraft = currentDraft,
        allSessionNotes = allSessionNotes,
        fallbackMessageCount = fallbackMessageCount,
        fallbackStartMs = fallbackStartMs,
        fallbackEndMs = fallbackEndMs,
      )
    val sb = StringBuilder()
    sb.appendLine("FELDBUCH SESSION NOTES")
    if (settings.githubOwner.isNotBlank() && settings.githubRepo.isNotBlank()) {
      sb.appendLine("Repo: ${settings.githubOwner}/${settings.githubRepo} (${settings.githubBranch})")
    }
    sb.appendLine()

    entries.forEachIndexed { index, entry ->
      if (index > 0) sb.appendLine("\n----------------------------------------\n")
      val createdFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
      val createdStr = createdFmt.format(Date(entry.createdAtMs))
      val formattedBody =
        NoteDraftPrompt.formatForCopy(
          draftText = entry.text,
          messageCount = entry.messageCount,
          startMs = entry.startMs,
          endMs = entry.endMs,
        )
      sb.appendLine("Session Note ($createdStr)")
      sb.appendLine(formattedBody)
    }
    return sb.toString().trimEnd() + "\n"
  }

  private data class NoteExportEntry(
    val text: String,
    val messageCount: Int,
    val startMs: Long,
    val endMs: Long,
    val createdAtMs: Long,
  )

  private fun resolveExportEntries(
    editableDraftText: String,
    currentDraft: SessionNoteEntity?,
    allSessionNotes: List<SessionNoteEntity>,
    fallbackMessageCount: Int,
    fallbackStartMs: Long,
    fallbackEndMs: Long,
  ): List<NoteExportEntry> {
    val trimmedEdit = editableDraftText.trim()
    val result = mutableListOf<NoteExportEntry>()

    if (allSessionNotes.isNotEmpty()) {
      allSessionNotes.forEach { note ->
        val effectiveText =
          if (currentDraft != null && note.id == currentDraft.id && trimmedEdit.isNotEmpty()) {
            trimmedEdit
          } else {
            note.draftText.trim()
          }
        if (effectiveText.isNotEmpty()) {
          result.add(
            NoteExportEntry(
              text = effectiveText,
              messageCount = note.messageCount,
              startMs = note.startTimestampMs,
              endMs = note.endTimestampMs,
              createdAtMs = note.createdAtMs,
            )
          )
        }
      }
    }

    if (result.isEmpty() && trimmedEdit.isNotEmpty()) {
      result.add(
        NoteExportEntry(
          text = trimmedEdit,
          messageCount = fallbackMessageCount,
          startMs = fallbackStartMs,
          endMs = fallbackEndMs,
          createdAtMs = System.currentTimeMillis(),
        )
      )
    }
    return result
  }
}
