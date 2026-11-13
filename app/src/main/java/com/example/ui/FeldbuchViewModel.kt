package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.FeldbuchDatabase
import com.example.data.local.FeldbuchSettings
import com.example.data.local.SessionNoteEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.TreeFileEntity
import com.example.data.network.ApiChatMessage
import com.example.data.network.GitHubClient
import com.example.data.network.OpenAiClient
import com.example.domain.ExportSerializer
import com.example.domain.FolderGroup
import com.example.domain.NoteDraftPrompt
import com.example.domain.TreeBrowserHelper
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeldbuchUiState(
  val treeFiles: List<TreeFileEntity> = emptyList(),
  val pathFilterQuery: String = "",
  val filteredTreeFiles: List<TreeFileEntity> = emptyList(),
  val folderGroups: List<FolderGroup> = emptyList(),
  val loadingPaths: Set<String> = emptySet(),
  val trayFiles: List<TreeFileEntity> = emptyList(),
  val trayCharCount: Int = 0,
  val trayEstimatedTokens: Int = 0,
  val messages: List<ChatMessageEntity> = emptyList(),
  val unsentMessages: List<ChatMessageEntity> = emptyList(),
  val currentDraft: SessionNoteEntity? = null,
  val allSessionNotes: List<SessionNoteEntity> = emptyList(),
  val editableDraftText: String = "",
  val settings: FeldbuchSettings = FeldbuchSettings(),
  val hubReachable: Boolean = false,
  val isCheckingHub: Boolean = false,
  val isPast1500: Boolean = false,
  val isFetchingTree: Boolean = false,
  val isSendingChat: Boolean = false,
  val isDraftingNote: Boolean = false,
  val isTestingGitHub: Boolean = false,
  val gitHubTestStatus: String? = null,
  val isTestingChat: Boolean = false,
  val chatTestStatus: String? = null,
  val availableModels: List<String> = emptyList(),
  val previewFile: TreeFileEntity? = null,
  val statusMessage: String? = null,
  val errorMessage: String? = null,
) {
  val shouldShowHomeBanner: Boolean
    get() =
      TreeBrowserHelper.shouldShowHomeBanner(
        unsentCount = unsentMessages.size,
        hubReachable = hubReachable,
        localTime = if (isPast1500) LocalTime.of(15, 0) else LocalTime.of(12, 0),
      )

  val shouldShowQuietBadge: Boolean
    get() =
      TreeBrowserHelper.shouldShowQuietBadge(
        unsentCount = unsentMessages.size,
        hubReachable = hubReachable,
        localTime = if (isPast1500) LocalTime.of(15, 0) else LocalTime.of(12, 0),
      )
}

private data class TransientState(
  val pathFilterQuery: String = "",
  val loadingPaths: Set<String> = emptySet(),
  val hubReachable: Boolean = false,
  val isCheckingHub: Boolean = false,
  val isPast1500: Boolean = !LocalTime.now().isBefore(LocalTime.of(15, 0)),
  val isFetchingTree: Boolean = false,
  val isSendingChat: Boolean = false,
  val isDraftingNote: Boolean = false,
  val isTestingGitHub: Boolean = false,
  val gitHubTestStatus: String? = null,
  val isTestingChat: Boolean = false,
  val chatTestStatus: String? = null,
  val availableModels: List<String> = emptyList(),
  val previewFile: TreeFileEntity? = null,
  val statusMessage: String? = null,
  val errorMessage: String? = null,
  val localDraftOverride: String? = null,
)

class FeldbuchViewModel(application: Application) : AndroidViewModel(application) {
  private val appContext: Context = application.applicationContext
  private val dao = FeldbuchDatabase.getInstance(appContext).feldbuchDao()
  private val settingsStore = SettingsDataStore(appContext)
  private val openAiClient = OpenAiClient()
  private val gitHubClient = GitHubClient()

  private val transientState = MutableStateFlow(TransientState())

  val uiState: StateFlow<FeldbuchUiState> =
    combine(
        dao.observeAllTreeFiles(),
        dao.observeAllMessages(),
        dao.observeCurrentDraft(),
        dao.observeAllSessionNotes(),
        settingsStore.settingsFlow,
        transientState,
      ) { values ->
        @Suppress("UNCHECKED_CAST")
        val treeFiles = values[0] as List<TreeFileEntity>
        @Suppress("UNCHECKED_CAST")
        val messages = values[1] as List<ChatMessageEntity>
        val currentDraft = values[2] as SessionNoteEntity?
        @Suppress("UNCHECKED_CAST")
        val allSessionNotes = values[3] as List<SessionNoteEntity>
        val settings = values[4] as FeldbuchSettings
        val transient = values[5] as TransientState

        val unsent = messages.filter { !it.includedInNote }
        val filtered = TreeBrowserHelper.filterPathsByFilename(treeFiles, transient.pathFilterQuery)
        val groups = TreeBrowserHelper.groupByFolder(filtered)
        val trayFiles = treeFiles.filter { it.path in settings.selectedTrayPaths }
        val traySummary = TreeBrowserHelper.computeTraySummary(trayFiles)

        FeldbuchUiState(
          treeFiles = treeFiles,
          pathFilterQuery = transient.pathFilterQuery,
          filteredTreeFiles = filtered,
          folderGroups = groups,
          loadingPaths = transient.loadingPaths,
          trayFiles = trayFiles,
          trayCharCount = traySummary.totalChars,
          trayEstimatedTokens = traySummary.estimatedTokens,
          messages = messages,
          unsentMessages = unsent,
          currentDraft = currentDraft,
          allSessionNotes = allSessionNotes,
          editableDraftText = transient.localDraftOverride ?: currentDraft?.draftText.orEmpty(),
          settings = settings,
          hubReachable = transient.hubReachable,
          isCheckingHub = transient.isCheckingHub,
          isPast1500 = transient.isPast1500,
          isFetchingTree = transient.isFetchingTree,
          isSendingChat = transient.isSendingChat,
          isDraftingNote = transient.isDraftingNote,
          isTestingGitHub = transient.isTestingGitHub,
          gitHubTestStatus = transient.gitHubTestStatus,
          isTestingChat = transient.isTestingChat,
          chatTestStatus = transient.chatTestStatus,
          availableModels = transient.availableModels,
          previewFile = transient.previewFile,
          statusMessage = transient.statusMessage,
          errorMessage = transient.errorMessage,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FeldbuchUiState(),
      )

  init {
    refreshHubStatus()
    fetchTreeOnceOnOpen()
  }

  private fun fetchTreeOnceOnOpen() {
    viewModelScope.launch {
      val s = settingsStore.settingsFlow.first()
      if (s.githubOwner.isNotBlank() && s.githubRepo.isNotBlank()) {
        refreshTreeInternal(s, silentOnFailure = true)
      }
    }
  }

  fun refreshTree() {
    viewModelScope.launch {
      val s = settingsStore.settingsFlow.first()
      if (s.githubOwner.isBlank() || s.githubRepo.isBlank()) {
        transientState.update {
          it.copy(errorMessage = "Set GitHub owner and repo in Setup first.")
        }
        return@launch
      }
      refreshTreeInternal(s, silentOnFailure = false)
    }
  }

  private suspend fun refreshTreeInternal(s: FeldbuchSettings, silentOnFailure: Boolean) {
    transientState.update { it.copy(isFetchingTree = true, errorMessage = null) }
    val result =
      gitHubClient.fetchTree(
        owner = s.githubOwner,
        repo = s.githubRepo,
        branch = s.githubBranch,
        pat = s.githubPat,
      )
    result.fold(
      onSuccess = { entries ->
        val entities = entries.map { TreeFileEntity(path = it.path, sizeBytes = it.sizeBytes) }
        dao.replaceTreePreservingCachedContent(entities)
        val validPaths = entities.map { it.path }.toSet()
        val currentTray = settingsStore.settingsFlow.first().selectedTrayPaths
        settingsStore.updateSelectedTrayPaths(currentTray.intersect(validPaths))
        settingsStore.updateTreeFetchedAt(System.currentTimeMillis())
        transientState.update {
          it.copy(
            isFetchingTree = false,
            statusMessage = if (silentOnFailure) null else "Loaded ${entities.size} paths from ${s.githubOwner}/${s.githubRepo}",
          )
        }
      },
      onFailure = { err ->
        transientState.update {
          it.copy(
            isFetchingTree = false,
            errorMessage =
              if (silentOnFailure) null else (err.message ?: "Failed to fetch GitHub tree."),
          )
        }
      },
    )
  }

  fun updatePathFilterQuery(query: String) {
    transientState.update { it.copy(pathFilterQuery = query) }
  }

  fun toggleTrayPath(path: String) {
    viewModelScope.launch {
      val s = settingsStore.settingsFlow.first()
      val currentTray = s.selectedTrayPaths.toMutableSet()

      if (path in currentTray) {
        currentTray.remove(path)
        settingsStore.updateSelectedTrayPaths(currentTray)
        return@launch
      }

      if (path in transientState.value.loadingPaths) return@launch

      transientState.update { it.copy(loadingPaths = it.loadingPaths + path, errorMessage = null) }
      val result =
        gitHubClient.fetchRawFile(
          owner = s.githubOwner,
          repo = s.githubRepo,
          branch = s.githubBranch,
          path = path,
          pat = s.githubPat,
        )

      result.fold(
        onSuccess = { rawText ->
          dao.updateCachedContent(path = path, text = rawText, cachedAtMs = System.currentTimeMillis())
          val latestTray = settingsStore.settingsFlow.first().selectedTrayPaths.toMutableSet()
          latestTray.add(path)
          settingsStore.updateSelectedTrayPaths(latestTray)
          transientState.update { it.copy(loadingPaths = it.loadingPaths - path) }
        },
        onFailure = { err ->
          val existing = uiState.value.treeFiles.firstOrNull { it.path == path }
          if (existing?.cachedText != null) {
            val latestTray = settingsStore.settingsFlow.first().selectedTrayPaths.toMutableSet()
            latestTray.add(path)
            settingsStore.updateSelectedTrayPaths(latestTray)
            transientState.update {
              it.copy(
                loadingPaths = it.loadingPaths - path,
                statusMessage = "Offline: used cached copy of $path",
              )
            }
          } else {
            transientState.update {
              it.copy(
                loadingPaths = it.loadingPaths - path,
                errorMessage = err.message ?: "Failed to fetch $path",
              )
            }
          }
        },
      )
    }
  }

  fun clearTray() {
    viewModelScope.launch { settingsStore.updateSelectedTrayPaths(emptySet()) }
  }

  fun openFilePreview(file: TreeFileEntity) {
    transientState.update { it.copy(previewFile = file) }
  }

  fun closeFilePreview() {
    transientState.update { it.copy(previewFile = null) }
  }

  fun refreshHubStatus() {
    viewModelScope.launch {
      val currentHubUrl = settingsStore.settingsFlow.first().hubUrl
      val past1500 = !LocalTime.now().isBefore(LocalTime.of(15, 0))
      transientState.update {
        it.copy(isPast1500 = past1500, isCheckingHub = currentHubUrl.isNotBlank())
      }
      val reachable = openAiClient.checkHubReachable(currentHubUrl)
      transientState.update {
        it.copy(
          hubReachable = reachable,
          isCheckingHub = false,
          isPast1500 = !LocalTime.now().isBefore(LocalTime.of(15, 0)),
        )
      }
    }
  }

  fun sendChatMessage(question: String) {
    val trimmedQuestion = question.trim()
    if (trimmedQuestion.isEmpty()) return

    viewModelScope.launch {
      val snapshot = uiState.value
      val endpoint = snapshot.settings.endpointUrl
      if (endpoint.isBlank()) {
        transientState.update {
          it.copy(errorMessage = "Configure your OpenAI-compatible /v1 endpoint in Setup first.")
        }
        return@launch
      }

      transientState.update { it.copy(isSendingChat = true, errorMessage = null) }

      val tray = snapshot.trayFiles.filter { it.cachedText != null }
      val trayPathsJoined = tray.joinToString(", ") { it.path }
      val now = System.currentTimeMillis()

      dao.insertMessage(
        ChatMessageEntity(
          role = "user",
          content = trimmedQuestion,
          timestampMs = now,
          contextPaths = trayPathsJoined,
          includedInNote = false,
        )
      )

      val apiMessages = buildList {
        if (tray.isNotEmpty()) {
          val contextBlock =
            tray.joinToString("\n\n") { file ->
              "=== FILE: ${file.path} ===\n${file.cachedText.orEmpty()}"
            }
          add(
            ApiChatMessage(
              role = "system",
              content =
                "You are a concise field brainstorming partner. Ground answers in the user's attached repository files when relevant.\n\n$contextBlock",
            )
          )
        } else {
          add(
            ApiChatMessage(
              role = "system",
              content = "You are a concise field brainstorming partner.",
            )
          )
        }

        val recentHistory = snapshot.unsentMessages.takeLast(14)
        recentHistory.forEach { msg ->
          add(ApiChatMessage(role = msg.role, content = msg.content))
        }
        add(ApiChatMessage(role = "user", content = trimmedQuestion))
      }

      val result =
        openAiClient.sendChatCompletion(
          endpointUrl = endpoint,
          apiKey = snapshot.settings.apiKey,
          model = snapshot.settings.modelName,
          messages = apiMessages,
        )

      result.fold(
        onSuccess = { reply ->
          dao.insertMessage(
            ChatMessageEntity(
              role = "assistant",
              content = reply,
              timestampMs = System.currentTimeMillis(),
              contextPaths = trayPathsJoined,
              includedInNote = false,
            )
          )
          transientState.update { it.copy(isSendingChat = false) }
        },
        onFailure = { err ->
          transientState.update {
            it.copy(
              isSendingChat = false,
              errorMessage = err.message ?: "Chat request failed.",
            )
          }
        },
      )
    }
  }

  fun draftSessionNote() {
    viewModelScope.launch {
      val snapshot = uiState.value
      val unsent = snapshot.unsentMessages
      if (unsent.isEmpty()) {
        transientState.update { it.copy(errorMessage = "No waiting messages to draft into a note.") }
        return@launch
      }
      if (snapshot.settings.endpointUrl.isBlank()) {
        transientState.update {
          it.copy(errorMessage = "Configure your /v1 endpoint in Setup before drafting.")
        }
        return@launch
      }

      transientState.update {
        it.copy(isDraftingNote = true, errorMessage = null, statusMessage = null)
      }

      val promptMessages = NoteDraftPrompt.buildMessagesForDraft(unsent)
      val result =
        openAiClient.sendChatCompletion(
          endpointUrl = snapshot.settings.endpointUrl,
          apiKey = snapshot.settings.apiKey,
          model = snapshot.settings.modelName,
          messages = promptMessages,
        )

      result.fold(
        onSuccess = { draftText ->
          val startMs = unsent.minOf { it.timestampMs }
          val endMs = unsent.maxOf { it.timestampMs }
          dao.archivePreviousDrafts()
          dao.insertSessionNote(
            SessionNoteEntity(
              draftText = draftText,
              messageCount = unsent.size,
              startTimestampMs = startMs,
              endTimestampMs = endMs,
              createdAtMs = System.currentTimeMillis(),
              isCurrentDraft = true,
            )
          )
          transientState.update {
            it.copy(
              isDraftingNote = false,
              localDraftOverride = draftText,
              statusMessage = "Drafted session note from ${unsent.size} messages.",
            )
          }
        },
        onFailure = { err ->
          transientState.update {
            it.copy(
              isDraftingNote = false,
              errorMessage = err.message ?: "Failed to draft session note.",
            )
          }
        },
      )
    }
  }

  fun updateDraftText(newText: String) {
    transientState.update { it.copy(localDraftOverride = newText) }
    val current = uiState.value.currentDraft
    if (current != null) {
      viewModelScope.launch { dao.updateDraftText(current.id, newText) }
    }
  }

  fun buildCopyText(): String? {
    val state = uiState.value
    val text = state.editableDraftText.trim()
    if (text.isEmpty()) return null
    val draft = state.currentDraft
    val unsent = state.unsentMessages
    val count = draft?.messageCount ?: unsent.size
    val startMs =
      draft?.startTimestampMs ?: unsent.minOfOrNull { it.timestampMs } ?: System.currentTimeMillis()
    val endMs =
      draft?.endTimestampMs ?: unsent.maxOfOrNull { it.timestampMs } ?: System.currentTimeMillis()
    return NoteDraftPrompt.formatForCopy(text, count, startMs, endMs)
  }

  fun markUnsentMessagesSettled() {
    viewModelScope.launch {
      val ids = uiState.value.unsentMessages.map { it.id }
      if (ids.isNotEmpty()) {
        dao.markMessagesIncluded(ids)
        transientState.update {
          it.copy(statusMessage = "Marked ${ids.size} messages as settled.")
        }
      }
    }
  }

  fun buildExportJson(): String {
    val state = uiState.value
    return ExportSerializer.buildExportJson(
      settings = state.settings,
      treeFileCount = state.treeFiles.size,
      messages = state.messages,
      sessionNotes = state.allSessionNotes,
    )
  }

  fun buildExportMarkdown(): String {
    val state = uiState.value
    val unsent = state.unsentMessages
    val now = System.currentTimeMillis()
    return ExportSerializer.buildSessionNotesMarkdown(
      settings = state.settings,
      editableDraftText = state.editableDraftText,
      currentDraft = state.currentDraft,
      allSessionNotes = state.allSessionNotes,
      fallbackMessageCount = unsent.size,
      fallbackStartMs = unsent.minOfOrNull { it.timestampMs } ?: now,
      fallbackEndMs = unsent.maxOfOrNull { it.timestampMs } ?: now,
    )
  }

  fun buildExportText(): String {
    val state = uiState.value
    val unsent = state.unsentMessages
    val now = System.currentTimeMillis()
    return ExportSerializer.buildSessionNotesText(
      settings = state.settings,
      editableDraftText = state.editableDraftText,
      currentDraft = state.currentDraft,
      allSessionNotes = state.allSessionNotes,
      fallbackMessageCount = unsent.size,
      fallbackStartMs = unsent.minOfOrNull { it.timestampMs } ?: now,
      fallbackEndMs = unsent.maxOfOrNull { it.timestampMs } ?: now,
    )
  }

  fun saveGitHubSettings(owner: String, repo: String, branch: String, pat: String) {
    viewModelScope.launch {
      settingsStore.updateGitHubSettings(owner, repo, branch, pat)
      transientState.update { it.copy(statusMessage = "GitHub settings saved locally.") }
    }
  }

  fun testGitHubSettings(owner: String, repo: String, branch: String, pat: String) {
    viewModelScope.launch {
      settingsStore.updateGitHubSettings(owner, repo, branch, pat)
      transientState.update {
        it.copy(isTestingGitHub = true, gitHubTestStatus = null, errorMessage = null)
      }
      val result = gitHubClient.fetchTree(owner, repo, branch, pat)
      result.fold(
        onSuccess = { entries ->
          val entities = entries.map { TreeFileEntity(path = it.path, sizeBytes = it.sizeBytes) }
          dao.replaceTreePreservingCachedContent(entities)
          settingsStore.updateTreeFetchedAt(System.currentTimeMillis())
          transientState.update {
            it.copy(
              isTestingGitHub = false,
              gitHubTestStatus = "200 OK · ${entries.size} files in tree",
            )
          }
        },
        onFailure = { err ->
          transientState.update {
            it.copy(
              isTestingGitHub = false,
              gitHubTestStatus = "Failed: ${err.message ?: "GitHub error"}",
            )
          }
        },
      )
    }
  }

  fun saveChatAndHubSettings(endpointUrl: String, apiKey: String, modelName: String, hubUrl: String) {
    viewModelScope.launch {
      settingsStore.updateChatAndHubSettings(endpointUrl, apiKey, modelName, hubUrl)
      transientState.update { it.copy(statusMessage = "Chat & hub settings saved locally.") }
      refreshHubStatus()
    }
  }

  fun testChatEndpoint(endpointUrl: String, apiKey: String, modelName: String, hubUrl: String) {
    viewModelScope.launch {
      settingsStore.updateChatAndHubSettings(endpointUrl, apiKey, modelName, hubUrl)
      transientState.update {
        it.copy(isTestingChat = true, chatTestStatus = null, errorMessage = null)
      }
      val result = openAiClient.fetchModels(endpointUrl, apiKey)
      result.fold(
        onSuccess = { models ->
          transientState.update {
            it.copy(
              isTestingChat = false,
              availableModels = models,
              chatTestStatus = "200 OK · ${models.size} models",
            )
          }
        },
        onFailure = { err ->
          transientState.update {
            it.copy(
              isTestingChat = false,
              chatTestStatus = "Failed: ${err.message ?: "Endpoint error"}",
            )
          }
        },
      )
    }
  }

  fun clearChatHistory() {
    viewModelScope.launch {
      dao.clearAllMessages()
      dao.clearAllSessionNotes()
      transientState.update {
        it.copy(localDraftOverride = null, statusMessage = "Cleared chat history and session notes.")
      }
    }
  }

  fun clearCachedTree() {
    viewModelScope.launch {
      dao.deleteAllTreeFiles()
      settingsStore.updateTreeFetchedAt(0L)
      settingsStore.updateSelectedTrayPaths(emptySet())
      transientState.update {
        it.copy(pathFilterQuery = "", statusMessage = "Cleared cached GitHub tree.")
      }
    }
  }

  fun showStatus(message: String) {
    transientState.update { it.copy(statusMessage = message) }
  }

  fun clearFeedback() {
    transientState.update { it.copy(statusMessage = null, errorMessage = null) }
  }
}
