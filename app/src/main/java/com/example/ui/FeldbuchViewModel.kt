package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.FeldbuchDatabase
import com.example.data.local.FeldbuchSettings
import com.example.data.local.OutboxEntity
import com.example.data.local.OutboxKind
import com.example.data.local.SessionNoteEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.TreeFileEntity
import android.net.Uri
import com.example.data.network.ApiChatMessage
import com.example.data.network.ChatApiResult
import com.example.data.network.DeviceTokenPollResult
import com.example.data.network.EuleApiResult
import com.example.data.network.EuleAskResponse
import com.example.data.network.EuleCitation
import com.example.data.network.EuleSearchHit
import com.example.data.network.FeldbuchOAuthConfig
import com.example.data.network.GitHubClient
import com.example.data.network.GitHubDeviceCodeResponse
import com.example.data.network.GitHubOAuthClient
import com.example.data.network.GitHubRepositorySummary
import com.example.data.network.OAuthBrowserLauncher
import com.example.data.network.OAuthRedirectDispatcher
import com.example.data.network.OpenAiClient
import com.example.data.network.PkceAuthSession
import com.example.data.outbox.EuleApiFactory
import com.example.data.outbox.EuleOutboxSender
import com.example.data.outbox.OutboxProcessor
import com.example.data.outbox.OutboxScheduler
import com.example.data.outbox.OutboxSenderRegistry
import com.example.data.sync.GitHubTreeSyncScheduler
import com.example.data.twa.AppSigningFingerprint
import com.example.data.twa.StellwerkLaunchMode
import com.example.data.twa.StellwerkLauncher
import com.example.domain.ContextCapLimiter
import com.example.domain.ExportSerializer
import com.example.domain.FolderGroup
import com.example.domain.NoteDraftPrompt
import com.example.domain.PathBlocklist
import com.example.domain.TreeBrowserHelper
import com.example.domain.UrlValidationResult
import com.example.domain.UrlValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class FeldbuchUiState(
  val treeFiles: List<TreeFileEntity> = emptyList(),
  val pathFilterQuery: String = "",
  val drawerSearchQuery: String = "",
  val drawerSearchByFullPath: Boolean = false,
  val filteredTreeFiles: List<TreeFileEntity> = emptyList(),
  val folderGroups: List<FolderGroup> = emptyList(),
  val loadingPaths: Set<String> = emptySet(),
  val trayFiles: List<TreeFileEntity> = emptyList(),
  val trimmedTrayPaths: Set<String> = emptySet(),
  val trayCharCount: Int = 0,
  val trayOriginalCharCount: Int = 0,
  val trayEstimatedTokens: Int = 0,
  val messages: List<ChatMessageEntity> = emptyList(),
  val unsentMessages: List<ChatMessageEntity> = emptyList(),
  val chatComposerText: String = "",
  val currentDraft: SessionNoteEntity? = null,
  val allSessionNotes: List<SessionNoteEntity> = emptyList(),
  val editableDraftText: String = "",
  val outboxItems: List<OutboxEntity> = emptyList(),
  val settings: FeldbuchSettings = FeldbuchSettings(),
  val effectiveOAuthClientId: String = "",
  val accessibleRepos: List<GitHubRepositorySummary> = emptyList(),
  val isLoadingRepos: Boolean = false,
  val isAuthenticatingGitHub: Boolean = false,
  val activeDeviceCode: GitHubDeviceCodeResponse? = null,
  val showOAuthDialog: Boolean = false,
  val secretRecoveryNotice: String? = null,
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
  val euleReachable: Boolean = false,
  val isCheckingEule: Boolean = false,
  val euleSearchQuery: String = "",
  val isSearchingEule: Boolean = false,
  val euleSearchHits: List<EuleSearchHit> = emptyList(),
  val hasSearchedEule: Boolean = false,
  val euleSearchErrorReason: String? = null,
  val euleSearchErrorDetail: String? = null,
  val euleAskInput: String = "",
  val isAskingEule: Boolean = false,
  val euleAskResponse: EuleAskResponse? = null,
  val euleAskLastQuestion: String? = null,
  val euleAskErrorReason: String? = null,
  val euleAskErrorDetail: String? = null,
  val selectedCitation: EuleCitation? = null,
  val isFlushingOutbox: Boolean = false,
  val appPackageName: String = "",
  val appSha256Fingerprint: String = "",
) {
  val isGitHubOAuthSignedIn: Boolean
    get() = settings.githubOAuthToken.isNotBlank()
  val isEuleConfigured: Boolean
    get() = settings.euleUrl.isNotBlank() || settings.euleDemoMode
  val shouldShowHomeBanner: Boolean
    get() =
      TreeBrowserHelper.shouldShowHomeBanner(
        unsentCount = unsentMessages.size,
        hubReachable = hubReachable,
        isPast1500 = isPast1500,
      )

  val shouldShowQuietBadge: Boolean
    get() =
      TreeBrowserHelper.shouldShowQuietBadge(
        unsentCount = unsentMessages.size,
        hubReachable = hubReachable,
        isPast1500 = isPast1500,
      )
}

private data class TransientState(
  val pathFilterQuery: String = "",
  val drawerSearchQuery: String = "",
  val drawerSearchByFullPath: Boolean = false,
  val loadingPaths: Set<String> = emptySet(),
  val chatComposerText: String = "",
  val hubReachable: Boolean = false,
  val isCheckingHub: Boolean = false,
  val isPast1500: Boolean = TreeBrowserHelper.isCurrentClockPast1500(),
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
  val accessibleRepos: List<GitHubRepositorySummary> = emptyList(),
  val isLoadingRepos: Boolean = false,
  val isAuthenticatingGitHub: Boolean = false,
  val activeDeviceCode: GitHubDeviceCodeResponse? = null,
  val showOAuthDialog: Boolean = false,
  val euleReachable: Boolean = false,
  val isCheckingEule: Boolean = false,
  val euleSearchQuery: String = "",
  val isSearchingEule: Boolean = false,
  val euleSearchHits: List<EuleSearchHit> = emptyList(),
  val hasSearchedEule: Boolean = false,
  val euleSearchErrorReason: String? = null,
  val euleSearchErrorDetail: String? = null,
  val euleAskInput: String = "",
  val isAskingEule: Boolean = false,
  val euleAskResponse: EuleAskResponse? = null,
  val euleAskLastQuestion: String? = null,
  val euleAskErrorReason: String? = null,
  val euleAskErrorDetail: String? = null,
  val selectedCitation: EuleCitation? = null,
  val isFlushingOutbox: Boolean = false,
  val appPackageName: String = "",
  val appSha256Fingerprint: String = "",
)

class FeldbuchViewModel(application: Application) : AndroidViewModel(application) {
  private val appContext: Context = application.applicationContext
  private val dao = FeldbuchDatabase.getInstance(appContext).feldbuchDao()
  private val settingsStore = SettingsDataStore(appContext)
  private val openAiClient = OpenAiClient()
  private val gitHubClient = GitHubClient()
  private val gitHubOAuthClient = GitHubOAuthClient()
  private val outboxProcessor =
    OutboxProcessor(
      dao = dao,
      sender = OutboxSenderRegistry.getOverrideOrNull() ?: EuleOutboxSender.fromContext(appContext),
    )

  private var pendingPkceSession: PkceAuthSession? = null
  private var devicePollJob: Job? = null

  private val transientState = MutableStateFlow(TransientState())

  val uiState: StateFlow<FeldbuchUiState> =
    combine(
        dao.observeAllTreeFiles(),
        dao.observeAllMessages(),
        dao.observeCurrentDraft(),
        dao.observeAllSessionNotes(),
        dao.observeOutboxItems(),
        settingsStore.settingsFlow,
        settingsStore.secretRecoveryNotice,
        transientState,
      ) { values ->
        @Suppress("UNCHECKED_CAST")
        val treeFiles = values[0] as List<TreeFileEntity>
        @Suppress("UNCHECKED_CAST")
        val messages = values[1] as List<ChatMessageEntity>
        val currentDraft = values[2] as SessionNoteEntity?
        @Suppress("UNCHECKED_CAST")
        val allSessionNotes = values[3] as List<SessionNoteEntity>
        @Suppress("UNCHECKED_CAST")
        val outboxItems = values[4] as List<OutboxEntity>
        val settings = values[5] as FeldbuchSettings
        val secretNotice = values[6] as String?
        val transient = values[7] as TransientState

        val unsent = messages.filter { !it.includedInNote && it.errorReason == null }
        val filtered = TreeBrowserHelper.filterPathsByFilename(treeFiles, transient.pathFilterQuery)
        val groups = TreeBrowserHelper.groupByFolder(filtered)

        val allowedTrayFiles =
          treeFiles.filter {
            it.path in settings.selectedTrayPaths && !PathBlocklist.isBlocked(it.path)
          }
        val cappedContext =
          ContextCapLimiter.apply(
            files = allowedTrayFiles,
            perFileCap = settings.perFileCharCap,
            totalCap = settings.totalCharCap,
          )

        FeldbuchUiState(
          treeFiles = treeFiles,
          pathFilterQuery = transient.pathFilterQuery,
          drawerSearchQuery = transient.drawerSearchQuery,
          drawerSearchByFullPath = transient.drawerSearchByFullPath,
          filteredTreeFiles = filtered,
          folderGroups = groups,
          loadingPaths = transient.loadingPaths,
          trayFiles = allowedTrayFiles,
          trimmedTrayPaths = cappedContext.trimmedPaths,
          trayCharCount = cappedContext.totalSentChars,
          trayOriginalCharCount = cappedContext.totalOriginalChars,
          trayEstimatedTokens = cappedContext.estimatedTokens,
          messages = messages,
          unsentMessages = unsent,
          chatComposerText = transient.chatComposerText,
          currentDraft = currentDraft,
          allSessionNotes = allSessionNotes,
          editableDraftText = transient.localDraftOverride ?: currentDraft?.draftText.orEmpty(),
          outboxItems = outboxItems,
          settings = settings,
          effectiveOAuthClientId =
            settings.githubOAuthClientId.ifBlank { FeldbuchOAuthConfig.buildConfigClientId() },
          accessibleRepos = transient.accessibleRepos,
          isLoadingRepos = transient.isLoadingRepos,
          isAuthenticatingGitHub = transient.isAuthenticatingGitHub,
          activeDeviceCode = transient.activeDeviceCode,
          showOAuthDialog = transient.showOAuthDialog,
          secretRecoveryNotice = secretNotice,
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
          previewFile =
            transient.previewFile?.let { p -> treeFiles.firstOrNull { it.path == p.path } ?: p },
          statusMessage = transient.statusMessage,
          errorMessage = transient.errorMessage,
          euleReachable = transient.euleReachable,
          isCheckingEule = transient.isCheckingEule,
          euleSearchQuery = transient.euleSearchQuery,
          isSearchingEule = transient.isSearchingEule,
          euleSearchHits = transient.euleSearchHits,
          hasSearchedEule = transient.hasSearchedEule,
          euleSearchErrorReason = transient.euleSearchErrorReason,
          euleSearchErrorDetail = transient.euleSearchErrorDetail,
          euleAskInput = transient.euleAskInput,
          isAskingEule = transient.isAskingEule,
          euleAskResponse = transient.euleAskResponse,
          euleAskLastQuestion = transient.euleAskLastQuestion,
          euleAskErrorReason = transient.euleAskErrorReason,
          euleAskErrorDetail = transient.euleAskErrorDetail,
          selectedCitation = transient.selectedCitation,
          isFlushingOutbox = transient.isFlushingOutbox,
          appPackageName = transient.appPackageName,
          appSha256Fingerprint = transient.appSha256Fingerprint,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FeldbuchUiState(),
      )

  init {
    transientState.update {
      it.copy(
        appPackageName = appContext.packageName,
        appSha256Fingerprint = AppSigningFingerprint.readSha256(appContext),
      )
    }
    viewModelScope.launch {
      settingsStore.migrateAndSanitizeOnLoad()
      // Put any items left stuck in 'sending' back to 'queued' on app start, then schedule flush.
      outboxProcessor.recoverStuckOnStartup()
      if (dao.getQueuedOutboxItemsOnce().isNotEmpty()) {
        OutboxScheduler.enqueueConnectedFlush(appContext)
      }
      refreshHubStatus()
      refreshEuleHealth()
      val s = settingsStore.settingsFlow.first()
      if (s.githubOAuthToken.isNotBlank()) {
        refreshAccessibleReposInternal(s.githubOAuthToken, silent = true)
      }
      if (s.githubOwner.isNotBlank() && s.githubRepo.isNotBlank()) {
        GitHubTreeSyncScheduler.schedulePeriodicSync(
          context = appContext,
          intervalHours = s.treeSyncIntervalHours,
          enabled = s.treeAutoSyncEnabled,
        )
        refreshTreeInternal(s, silentOnFailure = true)
      }
    }
  }

  fun dismissSecretRecoveryNotice() {
    settingsStore.dismissSecretRecoveryNotice()
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
        pat = effectiveGitHubToken(s),
      )
    result.fold(
      onSuccess = { entries ->
        val entities = entries.map { TreeFileEntity(path = it.path, sizeBytes = it.sizeBytes) }
        dao.replaceTreePreservingCachedContent(entities)
        val validPaths = PathBlocklist.filterAllowed(entities.map { it.path }.toSet())
        val currentTray = settingsStore.settingsFlow.first().selectedTrayPaths
        settingsStore.updateSelectedTrayPaths(currentTray.intersect(validPaths))
        settingsStore.updateTreeFetchedAt(System.currentTimeMillis())
        transientState.update {
          it.copy(
            isFetchingTree = false,
            statusMessage =
              if (silentOnFailure) null
              else "Loaded ${entities.size} paths from ${s.githubOwner}/${s.githubRepo}",
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

  fun updateDrawerSearchQuery(query: String) {
    transientState.update { it.copy(drawerSearchQuery = query) }
  }

  fun toggleDrawerSearchByFullPath() {
    transientState.update { it.copy(drawerSearchByFullPath = !it.drawerSearchByFullPath) }
  }

  fun updateChatComposerText(text: String) {
    transientState.update { it.copy(chatComposerText = text) }
  }

  fun toggleTrayPath(path: String) {
    viewModelScope.launch {
      if (PathBlocklist.isBlocked(path)) {
        transientState.update {
          it.copy(statusMessage = "Blocked: sensitive paths cannot be attached.")
        }
        return@launch
      }

      val s = settingsStore.settingsFlow.first()
      val currentTray = PathBlocklist.filterAllowed(s.selectedTrayPaths).toMutableSet()

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
          pat = effectiveGitHubToken(s),
        )

      result.fold(
        onSuccess = { rawText ->
          dao.updateCachedContent(path = path, text = rawText, cachedAtMs = System.currentTimeMillis())
          val latestTray =
            PathBlocklist.filterAllowed(settingsStore.settingsFlow.first().selectedTrayPaths)
              .toMutableSet()
          latestTray.add(path)
          settingsStore.updateSelectedTrayPaths(latestTray)
          transientState.update { it.copy(loadingPaths = it.loadingPaths - path) }
        },
        onFailure = { err ->
          val existing = uiState.value.treeFiles.firstOrNull { it.path == path }
          if (existing?.cachedText != null) {
            val latestTray =
              PathBlocklist.filterAllowed(settingsStore.settingsFlow.first().selectedTrayPaths)
                .toMutableSet()
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
    if (PathBlocklist.isBlocked(file.path)) return
    transientState.update { it.copy(previewFile = file) }
    if (file.cachedText != null || file.path in transientState.value.loadingPaths) return
    viewModelScope.launch {
      val s = settingsStore.settingsFlow.first()
      if (s.githubOwner.isBlank() || s.githubRepo.isBlank()) return@launch
      transientState.update { it.copy(loadingPaths = it.loadingPaths + file.path) }
      val result =
        gitHubClient.fetchRawFile(
          owner = s.githubOwner,
          repo = s.githubRepo,
          branch = s.githubBranch,
          path = file.path,
          pat = effectiveGitHubToken(s),
        )
      result.fold(
        onSuccess = { rawText ->
          dao.updateCachedContent(
            path = file.path,
            text = rawText,
            cachedAtMs = System.currentTimeMillis(),
          )
          transientState.update { it.copy(loadingPaths = it.loadingPaths - file.path) }
        },
        onFailure = { err ->
          transientState.update {
            it.copy(
              loadingPaths = it.loadingPaths - file.path,
              errorMessage = err.message ?: "Failed to fetch ${file.path}",
            )
          }
        },
      )
    }
  }

  fun closeFilePreview() {
    transientState.update { it.copy(previewFile = null) }
  }

  fun refreshHubStatus() {
    viewModelScope.launch {
      val currentHubUrl = settingsStore.settingsFlow.first().hubUrl
      val past1500 = TreeBrowserHelper.isCurrentClockPast1500()
      transientState.update {
        it.copy(isPast1500 = past1500, isCheckingHub = currentHubUrl.isNotBlank())
      }
      val reachable = openAiClient.checkHubReachable(currentHubUrl)
      transientState.update {
        it.copy(
          hubReachable = reachable,
          isCheckingHub = false,
          isPast1500 = TreeBrowserHelper.isCurrentClockPast1500(),
        )
      }
    }
  }

  fun sendChatMessage() {
    val question = transientState.value.chatComposerText.trim()
    if (question.isEmpty() || transientState.value.isSendingChat) return

    viewModelScope.launch {
      val snapshot = uiState.value
      val endpoint = snapshot.settings.endpointUrl
      val urlCheck = UrlValidator.validateHttps(endpoint, "Endpoint URL")

      if (urlCheck is UrlValidationResult.Invalid) {
        val insertedId =
          dao.insertMessage(
            ChatMessageEntity(
              role = "user",
              content = question,
              timestampMs = System.currentTimeMillis(),
              contextPaths = "",
              includedInNote = false,
              errorReason = "network",
              errorDetail = urlCheck.message,
            )
          )
        // Preserve composer text on send failure; user can fix Setup and retry inline.
        transientState.update { it.copy(isSendingChat = false) }
        return@launch
      }

      transientState.update { it.copy(isSendingChat = true) }

      val capped = prepareCappedAllowedContext(snapshot)
      val trayPathsJoined = capped.items.joinToString(", ") { it.file.path }

      val messageId =
        dao.insertMessage(
          ChatMessageEntity(
            role = "user",
            content = question,
            timestampMs = System.currentTimeMillis(),
            contextPaths = trayPathsJoined,
            includedInNote = false,
          )
        )

      val apiMessages = buildChatPayload(snapshot.unsentMessages, capped, question)
      when (
        val result =
          openAiClient.sendChatCompletion(
            endpointUrl = endpoint,
            apiKey = snapshot.settings.apiKey,
            model = snapshot.settings.modelName,
            messages = apiMessages,
            maxTokens = snapshot.settings.maxTokens,
          )
      ) {
        is ChatApiResult.Success -> {
          dao.insertMessage(
            ChatMessageEntity(
              role = "assistant",
              content = result.reply,
              timestampMs = System.currentTimeMillis(),
              contextPaths = trayPathsJoined,
              includedInNote = false,
            )
          )
          transientState.update { it.copy(isSendingChat = false, chatComposerText = "") }
        }
        is ChatApiResult.Failure -> {
          dao.updateMessageError(
            id = messageId,
            reason = result.shortReason,
            detail = result.detailMessage,
          )
          // Keep chatComposerText untouched when a send fails.
          transientState.update { it.copy(isSendingChat = false) }
        }
      }
    }
  }

  fun retryChatMessage(failedMessage: ChatMessageEntity) {
    if (transientState.value.isSendingChat) return

    viewModelScope.launch {
      val snapshot = uiState.value
      val endpoint = snapshot.settings.endpointUrl
      val urlCheck = UrlValidator.validateHttps(endpoint, "Endpoint URL")
      if (urlCheck is UrlValidationResult.Invalid) {
        dao.updateMessageError(
          id = failedMessage.id,
          reason = "network",
          detail = urlCheck.message,
        )
        return@launch
      }

      transientState.update { it.copy(isSendingChat = true) }

      val capped = prepareCappedAllowedContext(snapshot)
      val trayPathsJoined = capped.items.joinToString(", ") { it.file.path }
      dao.clearMessageErrorAndUpdateContext(failedMessage.id, trayPathsJoined)

      val priorHistory = snapshot.unsentMessages.filter { it.id < failedMessage.id }
      val apiMessages = buildChatPayload(priorHistory, capped, failedMessage.content)

      when (
        val result =
          openAiClient.sendChatCompletion(
            endpointUrl = endpoint,
            apiKey = snapshot.settings.apiKey,
            model = snapshot.settings.modelName,
            messages = apiMessages,
            maxTokens = snapshot.settings.maxTokens,
          )
      ) {
        is ChatApiResult.Success -> {
          dao.insertMessage(
            ChatMessageEntity(
              role = "assistant",
              content = result.reply,
              timestampMs = System.currentTimeMillis(),
              contextPaths = trayPathsJoined,
              includedInNote = false,
            )
          )
          transientState.update {
            if (it.chatComposerText.trim() == failedMessage.content.trim()) {
              it.copy(isSendingChat = false, chatComposerText = "")
            } else {
              it.copy(isSendingChat = false)
            }
          }
        }
        is ChatApiResult.Failure -> {
          dao.updateMessageError(
            id = failedMessage.id,
            reason = result.shortReason,
            detail = result.detailMessage,
          )
          transientState.update { it.copy(isSendingChat = false) }
        }
      }
    }
  }

  private fun prepareCappedAllowedContext(snapshot: FeldbuchUiState) =
    ContextCapLimiter.apply(
      files =
        snapshot.trayFiles.filter {
          it.cachedText != null && !PathBlocklist.isBlocked(it.path)
        },
      perFileCap = snapshot.settings.perFileCharCap,
      totalCap = snapshot.settings.totalCharCap,
    )

  private fun buildChatPayload(
    history: List<ChatMessageEntity>,
    capped: com.example.domain.CappedContextResult,
    question: String,
  ): List<ApiChatMessage> = buildList {
    val nonEmptyItems = capped.items.filter { it.sentText.isNotEmpty() }
    if (nonEmptyItems.isNotEmpty()) {
      val contextBlock =
        nonEmptyItems.joinToString("\n\n") { item ->
          val tag = if (item.wasTrimmed) " [trimmed]" else ""
          "=== FILE: ${item.file.path}$tag ===\n${item.sentText}"
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

    history.filter { it.errorReason == null }.takeLast(14).forEach { msg ->
      add(ApiChatMessage(role = msg.role, content = msg.content))
    }
    add(ApiChatMessage(role = "user", content = question))
  }

  fun draftSessionNote() {
    viewModelScope.launch {
      val snapshot = uiState.value
      val unsent = snapshot.unsentMessages
      if (unsent.isEmpty()) {
        transientState.update { it.copy(errorMessage = "No waiting messages to draft into a note.") }
        return@launch
      }
      val urlCheck = UrlValidator.validateHttps(snapshot.settings.endpointUrl, "Endpoint URL")
      if (urlCheck is UrlValidationResult.Invalid) {
        transientState.update { it.copy(errorMessage = urlCheck.message) }
        return@launch
      }

      transientState.update {
        it.copy(isDraftingNote = true, errorMessage = null, statusMessage = null)
      }

      val promptMessages = NoteDraftPrompt.buildMessagesForDraft(unsent)
      when (
        val result =
          openAiClient.sendChatCompletion(
            endpointUrl = snapshot.settings.endpointUrl,
            apiKey = snapshot.settings.apiKey,
            model = snapshot.settings.modelName,
            messages = promptMessages,
            maxTokens = snapshot.settings.maxTokens,
          )
      ) {
        is ChatApiResult.Success -> {
          val startMs = unsent.minOf { it.timestampMs }
          val endMs = unsent.maxOf { it.timestampMs }
          dao.archivePreviousDrafts()
          dao.insertSessionNote(
            SessionNoteEntity(
              draftText = result.reply,
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
              localDraftOverride = result.reply,
              statusMessage = "Drafted session note from ${unsent.size} messages.",
            )
          }
        }
        is ChatApiResult.Failure -> {
          transientState.update {
            it.copy(
              isDraftingNote = false,
              errorMessage = "${result.shortReason}: ${result.detailMessage}",
            )
          }
        }
      }
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
      val latest = settingsStore.settingsFlow.first()
      if (latest.githubOwner.isNotBlank() && latest.githubRepo.isNotBlank()) {
        GitHubTreeSyncScheduler.schedulePeriodicSync(
          context = appContext,
          intervalHours = latest.treeSyncIntervalHours,
          enabled = latest.treeAutoSyncEnabled,
        )
      } else {
        GitHubTreeSyncScheduler.cancelPeriodicSync(appContext)
      }
      transientState.update { it.copy(statusMessage = "GitHub settings saved (encrypted at rest).") }
    }
  }

  fun testGitHubSettings(owner: String, repo: String, branch: String, pat: String) {
    viewModelScope.launch {
      settingsStore.updateGitHubSettings(owner, repo, branch, pat)
      val latest = settingsStore.settingsFlow.first()
      if (latest.githubOwner.isNotBlank() && latest.githubRepo.isNotBlank()) {
        GitHubTreeSyncScheduler.schedulePeriodicSync(
          context = appContext,
          intervalHours = latest.treeSyncIntervalHours,
          enabled = latest.treeAutoSyncEnabled,
        )
      }
      transientState.update {
        it.copy(isTestingGitHub = true, gitHubTestStatus = null, errorMessage = null)
      }
      val result = gitHubClient.fetchTree(owner, repo, branch, effectiveGitHubToken(latest))
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

  private fun effectiveGitHubToken(s: FeldbuchSettings): String =
    s.githubOAuthToken.ifBlank { s.githubPat }

  fun openOAuthDialog() {
    transientState.update { it.copy(showOAuthDialog = true, errorMessage = null) }
  }

  fun closeOAuthDialog() {
    transientState.update { it.copy(showOAuthDialog = false) }
  }

  fun startGitHubPkceOAuth(clientIdInput: String) {
    val cleanId = clientIdInput.trim().ifBlank { uiState.value.effectiveOAuthClientId }
    viewModelScope.launch {
      if (cleanId.isNotEmpty()) {
        settingsStore.updateGitHubOAuthClientId(cleanId)
      }
      val sessionResult = gitHubOAuthClient.createPkceSession(clientId = cleanId)
      sessionResult.fold(
        onSuccess = { session ->
          pendingPkceSession = session
          transientState.update {
            it.copy(isAuthenticatingGitHub = true, errorMessage = null)
          }
          val launched = OAuthBrowserLauncher.launch(appContext, session.authorizeUrl)
          if (!launched) {
            pendingPkceSession = null
            transientState.update {
              it.copy(
                isAuthenticatingGitHub = false,
                errorMessage = "No browser or Custom Tabs provider found to open GitHub OAuth.",
              )
            }
          }
        },
        onFailure = { err ->
          transientState.update {
            it.copy(errorMessage = err.message ?: "Enter a GitHub OAuth Client ID first.")
          }
        },
      )
    }
  }

  fun handleGitHubOAuthRedirect(uri: Uri) {
    OAuthRedirectDispatcher.consume(uri)
    val payload = GitHubOAuthClient.parseCallbackUri(uri) ?: return
    val session = pendingPkceSession

    if (!payload.error.isNullOrBlank()) {
      pendingPkceSession = null
      transientState.update {
        it.copy(
          isAuthenticatingGitHub = false,
          errorMessage = payload.errorDescription ?: payload.error,
        )
      }
      return
    }

    val code = payload.code.orEmpty()
    if (session == null || payload.state != session.state || code.isBlank()) {
      pendingPkceSession = null
      transientState.update {
        it.copy(
          isAuthenticatingGitHub = false,
          errorMessage = "GitHub OAuth state mismatch or missing code. Please try again.",
        )
      }
      return
    }

    viewModelScope.launch {
      transientState.update { it.copy(isAuthenticatingGitHub = true, errorMessage = null) }
      val tokenResult =
        gitHubOAuthClient.exchangeAuthorizationCode(
          clientId = session.clientId,
          code = code,
          codeVerifier = session.codeVerifier,
          redirectUri = session.redirectUri,
        )
      pendingPkceSession = null
      tokenResult.fold(
        onSuccess = { accessToken -> completeOAuthSignIn(accessToken) },
        onFailure = { err ->
          transientState.update {
            it.copy(
              isAuthenticatingGitHub = false,
              errorMessage = err.message ?: "Failed to complete GitHub OAuth sign-in.",
            )
          }
        },
      )
    }
  }

  fun startGitHubDeviceOAuth(clientIdInput: String) {
    val cleanId = clientIdInput.trim().ifBlank { uiState.value.effectiveOAuthClientId }
    devicePollJob?.cancel()
    viewModelScope.launch {
      if (cleanId.isNotEmpty()) {
        settingsStore.updateGitHubOAuthClientId(cleanId)
      }
      transientState.update {
        it.copy(isAuthenticatingGitHub = true, activeDeviceCode = null, errorMessage = null)
      }
      val codeResult = gitHubOAuthClient.requestDeviceCode(cleanId)
      codeResult.fold(
        onSuccess = { deviceResp ->
          transientState.update { it.copy(activeDeviceCode = deviceResp) }
          OAuthBrowserLauncher.launch(appContext, deviceResp.verificationUri)
          startPollingDeviceToken(cleanId, deviceResp)
        },
        onFailure = { err ->
          transientState.update {
            it.copy(
              isAuthenticatingGitHub = false,
              errorMessage = err.message ?: "Failed to start GitHub Device Flow.",
            )
          }
        },
      )
    }
  }

  private fun startPollingDeviceToken(clientId: String, device: GitHubDeviceCodeResponse) {
    devicePollJob?.cancel()
    devicePollJob =
      viewModelScope.launch {
        var intervalSec = device.intervalSeconds.coerceAtLeast(1)
        val deadlineMs = System.currentTimeMillis() + device.expiresInSeconds * 1000L
        while (isActive && System.currentTimeMillis() < deadlineMs) {
          delay(intervalSec * 1000L)
          when (
            val poll =
              gitHubOAuthClient.pollDeviceAccessTokenOnce(
                clientId = clientId,
                deviceCode = device.deviceCode,
              )
          ) {
            is DeviceTokenPollResult.Success -> {
              completeOAuthSignIn(poll.accessToken)
              return@launch
            }
            is DeviceTokenPollResult.Pending -> Unit
            is DeviceTokenPollResult.SlowDown -> intervalSec += poll.extraSeconds
            is DeviceTokenPollResult.Expired -> {
              transientState.update {
                it.copy(
                  isAuthenticatingGitHub = false,
                  activeDeviceCode = null,
                  errorMessage = "GitHub device code expired. Please try again.",
                )
              }
              return@launch
            }
            is DeviceTokenPollResult.Denied -> {
              transientState.update {
                it.copy(
                  isAuthenticatingGitHub = false,
                  activeDeviceCode = null,
                  errorMessage = "GitHub sign-in was cancelled.",
                )
              }
              return@launch
            }
            is DeviceTokenPollResult.Error -> {
              transientState.update {
                it.copy(
                  isAuthenticatingGitHub = false,
                  activeDeviceCode = null,
                  errorMessage = poll.message,
                )
              }
              return@launch
            }
          }
        }
      }
  }

  fun cancelGitHubDeviceOAuth() {
    devicePollJob?.cancel()
    devicePollJob = null
    transientState.update {
      it.copy(isAuthenticatingGitHub = false, activeDeviceCode = null)
    }
  }

  private suspend fun completeOAuthSignIn(accessToken: String) {
    val profile = gitHubOAuthClient.fetchAuthenticatedUser(accessToken).getOrNull()
    val login = profile?.login ?: "github-user"
    settingsStore.saveGitHubOAuthSession(token = accessToken, login = login)
    transientState.update {
      it.copy(
        isAuthenticatingGitHub = false,
        activeDeviceCode = null,
        showOAuthDialog = false,
        statusMessage = "Signed in to GitHub as @$login",
      )
    }
    refreshAccessibleReposInternal(accessToken, silent = false)
  }

  fun refreshAccessibleRepositories() {
    viewModelScope.launch {
      val token = settingsStore.settingsFlow.first().githubOAuthToken
      if (token.isBlank()) {
        transientState.update { it.copy(errorMessage = "Sign in with GitHub first.") }
        return@launch
      }
      refreshAccessibleReposInternal(token, silent = false)
    }
  }

  private suspend fun refreshAccessibleReposInternal(accessToken: String, silent: Boolean) {
    transientState.update { it.copy(isLoadingRepos = true) }
    val result = gitHubOAuthClient.fetchAccessibleRepos(accessToken)
    result.fold(
      onSuccess = { repos ->
        transientState.update {
          it.copy(
            isLoadingRepos = false,
            accessibleRepos = repos,
          )
        }
      },
      onFailure = { err ->
        transientState.update {
          it.copy(
            isLoadingRepos = false,
            errorMessage =
              if (silent) it.errorMessage
              else (err.message ?: "Failed to load GitHub repositories."),
          )
        }
      },
    )
  }

  fun selectRepository(repo: GitHubRepositorySummary) {
    viewModelScope.launch {
      val previous = settingsStore.settingsFlow.first()
      val isRepoSwitch =
        previous.githubOwner != repo.owner || previous.githubRepo != repo.name
      settingsStore.selectGitHubRepository(
        owner = repo.owner,
        repo = repo.name,
        branch = repo.defaultBranch,
      )
      if (isRepoSwitch) {
        settingsStore.updateSelectedTrayPaths(emptySet())
        transientState.update {
          it.copy(pathFilterQuery = "", drawerSearchQuery = "", previewFile = null)
        }
      }
      val latest = settingsStore.settingsFlow.first()
      GitHubTreeSyncScheduler.schedulePeriodicSync(
        context = appContext,
        intervalHours = latest.treeSyncIntervalHours,
        enabled = latest.treeAutoSyncEnabled,
      )
      refreshTreeInternal(latest, silentOnFailure = false)
    }
  }

  fun signOutGitHubOAuth() {
    devicePollJob?.cancel()
    devicePollJob = null
    pendingPkceSession = null
    viewModelScope.launch {
      settingsStore.clearGitHubOAuthSession()
      transientState.update {
        it.copy(
          accessibleRepos = emptyList(),
          isAuthenticatingGitHub = false,
          activeDeviceCode = null,
          statusMessage = "Signed out of GitHub OAuth.",
        )
      }
    }
  }

  fun saveChatAndHubSettings(
    endpointUrl: String,
    apiKey: String,
    modelName: String,
    hubUrl: String,
    maxTokens: Int,
  ) {
    val endpointCheck = UrlValidator.validateHttps(endpointUrl, "Endpoint URL", allowBlank = true)
    if (endpointCheck is UrlValidationResult.Invalid) {
      transientState.update { it.copy(errorMessage = endpointCheck.message) }
      return
    }
    val hubCheck = UrlValidator.validateHttps(hubUrl, "Hub URL", allowBlank = true)
    if (hubCheck is UrlValidationResult.Invalid) {
      transientState.update { it.copy(errorMessage = hubCheck.message) }
      return
    }

    viewModelScope.launch {
      settingsStore.updateChatAndHubSettings(endpointUrl, apiKey, modelName, hubUrl, maxTokens)
      transientState.update { it.copy(statusMessage = "Chat & hub settings saved securely.") }
      refreshHubStatus()
    }
  }

  fun testChatEndpoint(
    endpointUrl: String,
    apiKey: String,
    modelName: String,
    hubUrl: String,
    maxTokens: Int,
  ) {
    val endpointCheck = UrlValidator.validateHttps(endpointUrl, "Endpoint URL")
    if (endpointCheck is UrlValidationResult.Invalid) {
      transientState.update {
        it.copy(chatTestStatus = "Failed: ${endpointCheck.message}")
      }
      return
    }
    val hubCheck = UrlValidator.validateHttps(hubUrl, "Hub URL", allowBlank = true)
    if (hubCheck is UrlValidationResult.Invalid) {
      transientState.update { it.copy(chatTestStatus = "Failed: ${hubCheck.message}") }
      return
    }

    viewModelScope.launch {
      settingsStore.updateChatAndHubSettings(endpointUrl, apiKey, modelName, hubUrl, maxTokens)
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
        it.copy(
          pathFilterQuery = "",
          drawerSearchQuery = "",
          statusMessage = "Cleared cached GitHub tree.",
        )
      }
    }
  }

  fun captureToOutbox(kind: OutboxKind, body: String) {
    val clean = body.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      outboxProcessor.enqueueCapture(kind = kind, body = clean)
      OutboxScheduler.enqueueConnectedFlush(appContext)
      transientState.update {
        it.copy(statusMessage = "Queued ${kind.label.lowercase()} in outbox.")
      }
    }
  }

  fun retryOutboxItem(id: String) {
    viewModelScope.launch {
      if (outboxProcessor.retryFailed(id)) {
        OutboxScheduler.enqueueConnectedFlush(appContext)
        transientState.update { it.copy(statusMessage = "Re-queued item for sending.") }
      }
    }
  }

  fun clearSentOutboxItems() {
    viewModelScope.launch {
      outboxProcessor.clearSent()
      transientState.update { it.copy(statusMessage = "Cleared sent outbox items.") }
    }
  }

  fun flushOutboxNow() {
    if (transientState.value.isFlushingOutbox) return
    viewModelScope.launch {
      transientState.update { it.copy(isFlushingOutbox = true) }
      val report = outboxProcessor.flushQueued()
      val summary =
        when {
          report.sentCount == 0 && report.permanentlyFailedCount == 0 && report.remainingQueuedCount == 0 ->
            "No queued outbox items to sync."
          report.permanentlyFailedCount > 0 || report.remainingQueuedCount > 0 ->
            "Synced ${report.sentCount} · ${report.remainingQueuedCount} queued · ${report.permanentlyFailedCount} failed."
          else -> "Synced ${report.sentCount} outbox item(s) to Eule."
        }
      transientState.update {
        it.copy(
          isFlushingOutbox = false,
          statusMessage = summary,
        )
      }
    }
  }

  fun refreshEuleHealth() {
    viewModelScope.launch {
      val s = settingsStore.settingsFlow.first()
      val api = EuleApiFactory.create(s)
      if (api == null) {
        transientState.update { it.copy(euleReachable = false, isCheckingEule = false) }
        return@launch
      }
      transientState.update { it.copy(isCheckingEule = true) }
      val reachable = api.checkHealth()
      transientState.update { it.copy(euleReachable = reachable, isCheckingEule = false) }
    }
  }

  fun saveEuleSettings(euleUrl: String, euleKey: String, demoMode: Boolean) {
    val cleanUrl = euleUrl.trim()
    val urlCheck = UrlValidator.validateHttps(cleanUrl, "Eule URL", allowBlank = true)
    if (urlCheck is UrlValidationResult.Invalid) {
      transientState.update { it.copy(errorMessage = urlCheck.message) }
      return
    }
    viewModelScope.launch {
      settingsStore.updateEuleSettings(cleanUrl, euleKey, demoMode)
      if (dao.getQueuedOutboxItemsOnce().isNotEmpty() && (cleanUrl.isNotBlank() || demoMode)) {
        OutboxScheduler.enqueueConnectedFlush(appContext)
      }
      transientState.update {
        it.copy(
          statusMessage =
            if (demoMode && cleanUrl.isBlank()) "Eule Demo mode enabled."
            else "Eule settings saved (encrypted at rest).",
        )
      }
      refreshEuleHealth()
    }
  }

  fun updateEuleSearchQuery(query: String) {
    transientState.update { it.copy(euleSearchQuery = query) }
  }

  fun searchEule() {
    val query = transientState.value.euleSearchQuery.trim()
    if (query.isEmpty() || transientState.value.isSearchingEule) return
    viewModelScope.launch {
      val s = settingsStore.settingsFlow.first()
      val api = EuleApiFactory.create(s)
      if (api == null) {
        transientState.update {
          it.copy(
            euleSearchErrorReason = "setup",
            euleSearchErrorDetail = "Configure Eule HTTPS URL or Demo mode in Setup.",
          )
        }
        return@launch
      }
      transientState.update {
        it.copy(
          isSearchingEule = true,
          euleSearchErrorReason = null,
          euleSearchErrorDetail = null,
        )
      }
      when (val result = api.search(query)) {
        is EuleApiResult.Success -> {
          transientState.update {
            it.copy(
              isSearchingEule = false,
              euleSearchHits = result.data,
              hasSearchedEule = true,
            )
          }
        }
        is EuleApiResult.Failure -> {
          transientState.update {
            it.copy(
              isSearchingEule = false,
              euleSearchErrorReason = result.shortReason,
              euleSearchErrorDetail = result.detailMessage,
            )
          }
        }
      }
    }
  }

  fun updateEuleAskInput(input: String) {
    transientState.update { it.copy(euleAskInput = input) }
  }

  fun askEule(overrideQuestion: String? = null) {
    val question = (overrideQuestion ?: transientState.value.euleAskInput).trim()
    if (question.isEmpty() || transientState.value.isAskingEule) return
    viewModelScope.launch {
      val s = settingsStore.settingsFlow.first()
      val api = EuleApiFactory.create(s)
      if (api == null) {
        transientState.update {
          it.copy(
            euleAskLastQuestion = question,
            euleAskErrorReason = "setup",
            euleAskErrorDetail = "Configure Eule HTTPS URL or Demo mode in Setup.",
          )
        }
        return@launch
      }
      transientState.update {
        it.copy(
          isAskingEule = true,
          euleAskLastQuestion = question,
          euleAskErrorReason = null,
          euleAskErrorDetail = null,
        )
      }
      when (val result = api.ask(question = question, idempotencyKey = UUID.randomUUID().toString())) {
        is EuleApiResult.Success -> {
          transientState.update {
            it.copy(
              isAskingEule = false,
              euleAskResponse = result.data,
              euleAskInput = if (overrideQuestion == null) "" else it.euleAskInput,
            )
          }
        }
        is EuleApiResult.Failure -> {
          // Keep euleAskInput untouched on failure so typed question is never lost.
          transientState.update {
            it.copy(
              isAskingEule = false,
              euleAskErrorReason = result.shortReason,
              euleAskErrorDetail = result.detailMessage,
            )
          }
        }
      }
    }
  }

  fun selectCitation(citation: EuleCitation) {
    transientState.update { it.copy(selectedCitation = citation) }
  }

  fun dismissCitation() {
    transientState.update { it.copy(selectedCitation = null) }
  }

  fun saveStellwerkUrl(url: String) {
    val cleanUrl = url.trim()
    val urlCheck = UrlValidator.validateHttps(cleanUrl, "Stellwerk URL", allowBlank = true)
    if (urlCheck is UrlValidationResult.Invalid) {
      transientState.update { it.copy(errorMessage = urlCheck.message) }
      return
    }
    viewModelScope.launch {
      settingsStore.updateStellwerkUrl(cleanUrl)
      transientState.update {
        it.copy(
          statusMessage =
            if (cleanUrl.isEmpty()) "Cleared Stellwerk URL."
            else "Stellwerk HTTPS URL saved.",
        )
      }
    }
  }

  fun launchStellwerk(context: Context, overrideUrl: String? = null) {
    val targetUrl = (overrideUrl ?: uiState.value.settings.stellwerkUrl).trim()
    if (targetUrl.isEmpty()) {
      transientState.update {
        it.copy(errorMessage = "Set your Stellwerk HTTPS URL in Setup first.")
      }
      return
    }
    val result = StellwerkLauncher.launch(context = context, rawUrl = targetUrl)
    when (result.mode) {
      StellwerkLaunchMode.FAILED ->
        transientState.update { it.copy(errorMessage = result.message) }
      StellwerkLaunchMode.CUSTOM_TAB,
      StellwerkLaunchMode.BROWSER_VIEW ->
        transientState.update { it.copy(statusMessage = result.message) }
      StellwerkLaunchMode.TWA -> Unit
    }
  }

  fun updateTreeSyncSettings(enabled: Boolean, intervalHours: Long) {
    viewModelScope.launch {
      settingsStore.updateTreeSyncPreferences(enabled = enabled, intervalHours = intervalHours)
      val s = settingsStore.settingsFlow.first()
      val hasRepo = s.githubOwner.isNotBlank() && s.githubRepo.isNotBlank()
      if (enabled && hasRepo) {
        GitHubTreeSyncScheduler.schedulePeriodicSync(
          context = appContext,
          intervalHours = s.treeSyncIntervalHours,
          enabled = true,
        )
      } else {
        GitHubTreeSyncScheduler.cancelPeriodicSync(appContext)
      }
      transientState.update {
        it.copy(
          statusMessage =
            if (enabled) "Background tree sync every ${s.treeSyncIntervalHours}h enabled."
            else "Background tree sync paused.",
        )
      }
    }
  }

  fun enqueueBackgroundTreeSyncNow() {
    val s = uiState.value.settings
    if (s.githubOwner.isBlank() || s.githubRepo.isBlank()) {
      transientState.update { it.copy(errorMessage = "Set GitHub owner and repo in Setup first.") }
      return
    }
    GitHubTreeSyncScheduler.enqueueImmediateSync(appContext)
    transientState.update { it.copy(statusMessage = "Queued background repository tree sync.") }
  }

  fun showStatus(message: String) {
    transientState.update { it.copy(statusMessage = message) }
  }

  fun clearFeedback() {
    transientState.update { it.copy(statusMessage = null, errorMessage = null) }
  }
}
