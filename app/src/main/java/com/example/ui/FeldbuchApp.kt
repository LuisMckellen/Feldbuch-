package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ContextTraySheet
import com.example.ui.screens.FilePreviewSheet
import com.example.ui.screens.SessionNoteScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TreeBrowserScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FeldbuchTab(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  TREE("tree", "Tree", Icons.Filled.AccountTree, Icons.Outlined.AccountTree),
  CHAT("chat", "Chat", Icons.Filled.Forum, Icons.Outlined.Forum),
  NOTE_DRAFT("note_draft", "Note", Icons.Filled.EditNote, Icons.Outlined.EditNote),
  SETUP("setup", "Setup", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeldbuchApp(viewModel: FeldbuchViewModel = viewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route ?: FeldbuchTab.TREE.route

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val snackbarHostState = remember { SnackbarHostState() }
  var showTraySheet by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshHubStatus()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
    val msg = uiState.errorMessage ?: uiState.statusMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearFeedback()
    }
  }

  val exportJsonLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
      uri ->
      if (uri != null) {
        try {
          val json = viewModel.buildExportJson()
          context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
          }
          viewModel.showStatus("Exported session JSON.")
        } catch (e: Exception) {
          viewModel.showStatus("Export failed: ${e.message}")
        }
      }
    }

  val exportMarkdownLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
      if (uri != null) {
        try {
          val markdown = viewModel.buildExportMarkdown()
          context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(markdown.toByteArray(Charsets.UTF_8))
          }
          viewModel.showStatus("Exported session notes as Markdown.")
        } catch (e: Exception) {
          viewModel.showStatus("Markdown export failed: ${e.message}")
        }
      }
    }

  val exportTextLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
      if (uri != null) {
        try {
          val plainText = viewModel.buildExportText()
          context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(plainText.toByteArray(Charsets.UTF_8))
          }
          viewModel.showStatus("Exported session notes as plain text.")
        } catch (e: Exception) {
          viewModel.showStatus("Text export failed: ${e.message}")
        }
      }
    }

  fun navigateToTab(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  if (showTraySheet) {
    ContextTraySheet(
      trayFiles = uiState.trayFiles,
      totalChars = uiState.trayCharCount,
      estimatedTokens = uiState.trayEstimatedTokens,
      onRemovePath = viewModel::toggleTrayPath,
      onClearTray = viewModel::clearTray,
      onPreviewFile = { file ->
        showTraySheet = false
        viewModel.openFilePreview(file)
      },
      onDismiss = { showTraySheet = false },
    )
  }

  uiState.previewFile?.let { file ->
    FilePreviewSheet(
      file = file,
      isInTray = file.path in uiState.settings.selectedTrayPaths,
      onToggleTray = { viewModel.toggleTrayPath(file.path) },
      onDismiss = viewModel::closeFilePreview,
    )
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (useNavRail) {
        NavigationRail(
          containerColor = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.fillMaxHeight(),
        ) {
          FeldbuchTab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationRailItem(
              selected = selected,
              onClick = { navigateToTab(tab.route) },
              icon = {
                TabIconWithBadge(
                  tab = tab,
                  selected = selected,
                  waitingCount = uiState.unsentMessages.size,
                )
              },
              label = { Text(tab.label) },
              modifier = Modifier.testTag("nav_${tab.route}"),
            )
          }
        }
      }

      Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
          TopAppBar(
            colors =
              TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
              ),
            title = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "Feldbuch",
                  style = MaterialTheme.typography.headlineMedium,
                )
                if (uiState.shouldShowQuietBadge) {
                  Spacer(modifier = Modifier.width(10.dp))
                  Surface(
                    onClick = { navigateToTab(FeldbuchTab.NOTE_DRAFT.route) },
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.testTag("quiet_waiting_badge"),
                  ) {
                    Text(
                      text = "${uiState.unsentMessages.size} waiting",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                  }
                }
              }
            },
            actions = {
              IconButton(
                onClick = { showTraySheet = true },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("top_tray_button"),
              ) {
                BadgedBox(
                  badge = {
                    if (uiState.trayFiles.isNotEmpty()) {
                      Badge(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                      ) {
                        Text("${uiState.trayFiles.size}")
                      }
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = "Open context tray",
                  )
                }
              }
            },
          )
        },
        bottomBar = {
          if (!useNavRail) {
            NavigationBar(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
              FeldbuchTab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                NavigationBarItem(
                  selected = selected,
                  onClick = { navigateToTab(tab.route) },
                  icon = {
                    TabIconWithBadge(
                      tab = tab,
                      selected = selected,
                      waitingCount = uiState.unsentMessages.size,
                    )
                  },
                  label = { Text(tab.label) },
                  modifier = Modifier.testTag("nav_${tab.route}"),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        Box(
          modifier = Modifier.fillMaxSize().padding(innerPadding),
          contentAlignment = Alignment.TopCenter,
        ) {
          Column(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {
            AnimatedVisibility(visible = uiState.shouldShowHomeBanner) {
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                shape = MaterialTheme.shapes.medium,
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("home_draft_banner"),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "${uiState.unsentMessages.size} messages waiting — draft the note?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                  )
                  Button(
                    onClick = {
                      navigateToTab(FeldbuchTab.NOTE_DRAFT.route)
                      viewModel.draftSessionNote()
                    },
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                      ),
                    modifier = Modifier.testTag("banner_draft_action"),
                  ) {
                    Text("Draft Note")
                  }
                }
              }
            }

            NavHost(
              navController = navController,
              startDestination = FeldbuchTab.TREE.route,
              modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
              composable(FeldbuchTab.TREE.route) {
                TreeBrowserScreen(
                  uiState = uiState,
                  onRefreshTree = viewModel::refreshTree,
                  onFilterQueryChange = viewModel::updatePathFilterQuery,
                  onToggleTrayPath = viewModel::toggleTrayPath,
                  onOpenFilePreview = viewModel::openFilePreview,
                  onOpenTraySheet = { showTraySheet = true },
                  onNavigateToSetup = { navigateToTab(FeldbuchTab.SETUP.route) },
                )
              }

              composable(FeldbuchTab.CHAT.route) {
                ChatScreen(
                  uiState = uiState,
                  onSendMessage = viewModel::sendChatMessage,
                  onOpenTraySheet = { showTraySheet = true },
                  onNavigateToSetup = { navigateToTab(FeldbuchTab.SETUP.route) },
                  onMessageCopied = viewModel::showStatus,
                )
              }

              composable(FeldbuchTab.NOTE_DRAFT.route) {
                SessionNoteScreen(
                  uiState = uiState,
                  onDraftNote = viewModel::draftSessionNote,
                  onDraftTextChange = viewModel::updateDraftText,
                  onBuildCopyText = viewModel::buildCopyText,
                  onMarkSettled = viewModel::markUnsentMessagesSettled,
                  onExportJson = {
                    val dateTag =
                      SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.getDefault()).format(Date())
                    exportJsonLauncher.launch("feldbuch-export-$dateTag.json")
                  },
                  onExportMarkdown = {
                    val dateTag =
                      SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.getDefault()).format(Date())
                    exportMarkdownLauncher.launch("feldbuch-notes-$dateTag.md")
                  },
                  onExportText = {
                    val dateTag =
                      SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.getDefault()).format(Date())
                    exportTextLauncher.launch("feldbuch-notes-$dateTag.txt")
                  },
                  onCopiedMessage = viewModel::showStatus,
                )
              }

              composable(FeldbuchTab.SETUP.route) {
                SettingsScreen(
                  uiState = uiState,
                  onSaveGitHub = viewModel::saveGitHubSettings,
                  onTestGitHub = viewModel::testGitHubSettings,
                  onSaveChatAndHub = viewModel::saveChatAndHubSettings,
                  onTestChat = viewModel::testChatEndpoint,
                  onRefreshHub = viewModel::refreshHubStatus,
                  onClearMessages = viewModel::clearChatHistory,
                  onClearTree = viewModel::clearCachedTree,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TabIconWithBadge(tab: FeldbuchTab, selected: Boolean, waitingCount: Int) {
  val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
  if (tab == FeldbuchTab.NOTE_DRAFT && waitingCount > 0) {
    BadgedBox(
      badge = {
        Badge(
          containerColor = MaterialTheme.colorScheme.secondary,
          contentColor = MaterialTheme.colorScheme.onSecondary,
        ) {
          Text("$waitingCount")
        }
      }
    ) {
      Icon(imageVector = icon, contentDescription = tab.label)
    }
  } else {
    Icon(imageVector = icon, contentDescription = tab.label)
  }
}
