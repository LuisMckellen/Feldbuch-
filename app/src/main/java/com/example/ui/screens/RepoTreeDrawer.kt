package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TreeFileEntity
import com.example.data.network.GitHubRepositorySummary
import com.example.domain.TreeHierarchyBuilder
import com.example.domain.VisibleTreeRow
import com.example.ui.FeldbuchUiState
import com.example.ui.components.ArchivalEmptyStateCard
import com.example.ui.components.SpinningSyncIcon
import com.example.ui.theme.FeldbuchMotion
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun RepoTreeDrawerSheet(
  uiState: FeldbuchUiState,
  onRefreshTree: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onToggleSearchByFullPath: () -> Unit,
  onToggleTrayPath: (String) -> Unit,
  onOpenFilePreview: (TreeFileEntity) -> Unit,
  onOpenTraySheet: () -> Unit,
  onNavigateToSetup: () -> Unit,
  onCloseDrawer: () -> Unit,
  onOpenGitHubOAuth: () -> Unit = {},
  onSignOutGitHub: () -> Unit = {},
  onRefreshRepos: () -> Unit = {},
  onSelectRepository: (GitHubRepositorySummary) -> Unit = {},
) {
  val focusManager = LocalFocusManager.current
  val numberFmt = NumberFormat.getIntegerInstance(Locale.getDefault())
  val coroutineScope = rememberCoroutineScope()
  val treeListState = rememberLazyListState()
  val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }
  var showRepoPicker by remember { mutableStateOf(false) }
  var currentDirectoryPath by
    remember(uiState.settings.githubOwner, uiState.settings.githubRepo) {
      mutableStateOf("")
    }
  var pendingScrollFolderPath by remember { mutableStateOf<String?>(null) }
  val searchQuery = uiState.drawerSearchQuery
  val isFiltering = searchQuery.isNotBlank()

  val treeRoot =
    remember(
      uiState.treeFiles,
      searchQuery,
      uiState.drawerSearchByFullPath,
      uiState.settings.selectedTrayPaths,
    ) {
      TreeHierarchyBuilder.buildTree(
        files = uiState.treeFiles,
        filterQuery = searchQuery,
        selectedTrayPaths = uiState.settings.selectedTrayPaths,
        matchNameOnly = !uiState.drawerSearchByFullPath,
      )
    }

  val visibleRows =
    remember(
      treeRoot,
      expandedFolders.toMap(),
      isFiltering,
      uiState.settings.selectedTrayPaths,
      uiState.trimmedTrayPaths,
      uiState.loadingPaths,
    ) {
      TreeHierarchyBuilder.flattenVisibleRows(
        root = treeRoot,
        expandedFolders = expandedFolders,
        isFiltering = isFiltering,
        selectedTrayPaths = uiState.settings.selectedTrayPaths,
        trimmedTrayPaths = uiState.trimmedTrayPaths,
        loadingPaths = uiState.loadingPaths,
      )
    }

  LaunchedEffect(pendingScrollFolderPath, visibleRows) {
    val target = pendingScrollFolderPath ?: return@LaunchedEffect
    val idx = visibleRows.indexOfFirst { it is VisibleTreeRow.FolderRow && it.folderPath == target }
    if (idx >= 0) {
      treeListState.animateScrollToItem(idx)
    }
    pendingScrollFolderPath = null
  }

  val hasRepo =
    uiState.settings.githubOwner.isNotBlank() && uiState.settings.githubRepo.isNotBlank()

  Column(
    modifier =
      Modifier.fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .testTag("repo_tree_drawer"),
  ) {
    // Drawer Header + Pinned Search Filter
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text =
                if (hasRepo) "${uiState.settings.githubOwner}/${uiState.settings.githubRepo}"
                else "Repository Tree",
              style = MaterialTheme.typography.headlineSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val timeText =
              if (uiState.settings.treeFetchedAtMs > 0L) {
                "tree as of ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(uiState.settings.treeFetchedAtMs))}"
              } else {
                "tree not fetched yet"
              }
            val metaLine =
              if (hasRepo) {
                "${uiState.settings.githubBranch} · ${uiState.treeFiles.size} paths · $timeText"
              } else {
                "Configure repository in Setup"
              }
            Text(
              text = metaLine,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("drawer_tree_timestamp"),
            )
          }

          if (hasRepo) {
            IconButton(
              onClick = onRefreshTree,
              enabled = !uiState.isFetchingTree,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("drawer_refresh_tree_button"),
            ) {
              SpinningSyncIcon(
                isSpinning = uiState.isFetchingTree,
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh GitHub repository tree",
                tint =
                  if (uiState.isFetchingTree) MaterialTheme.colorScheme.secondary
                  else MaterialTheme.colorScheme.onSurface,
              )
            }
          }

          IconButton(
            onClick = onCloseDrawer,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("close_drawer_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = "Close file tree drawer",
            )
          }
        }

        DrawerGitHubAuthBar(
          uiState = uiState,
          showRepoPicker = showRepoPicker,
          onToggleRepoPicker = { showRepoPicker = !showRepoPicker },
          onOpenGitHubOAuth = onOpenGitHubOAuth,
          onSignOutGitHub = {
            showRepoPicker = false
            onSignOutGitHub()
          },
          onRefreshRepos = onRefreshRepos,
          onSelectRepository = { repo ->
            showRepoPicker = false
            onSelectRepository(repo)
          },
        )

        // Top Search Filter for quickly finding files by name
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          enabled = uiState.treeFiles.isNotEmpty(),
          placeholder = {
            Text(
              text =
                if (uiState.treeFiles.isEmpty()) "Fetch tree to search files…"
                else "Search files by name…",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.Search,
              contentDescription = "Search files",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { onSearchQueryChange("") },
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("drawer_search_clear_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = "Clear file search",
                )
              }
            }
          },
          singleLine = true,
          shape = MaterialTheme.shapes.medium,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
          textStyle = MaterialTheme.typography.labelMedium,
          modifier = Modifier.fillMaxWidth().testTag("drawer_search_filter_input"),
        )

        AnimatedVisibility(
          visible = uiState.treeFiles.isNotEmpty() && isFiltering,
          enter = FeldbuchMotion.expandFadeIn(),
          exit = FeldbuchMotion.shrinkFadeOut(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val matchCount = treeRoot.totalFileCount
            Text(
              text =
                "$matchCount ${if (matchCount == 1) "match" else "matches"} of ${uiState.treeFiles.size}",
              style = MaterialTheme.typography.labelSmall,
              color =
                if (matchCount == 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag("drawer_search_match_count"),
            )

            FilterChip(
              selected = uiState.drawerSearchByFullPath,
              onClick = onToggleSearchByFullPath,
              label = { Text("Full path", style = MaterialTheme.typography.labelSmall) },
              modifier = Modifier.testTag("drawer_search_scope_chip"),
            )
          }
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    if (uiState.treeFiles.isNotEmpty()) {
      RepoBreadcrumbBar(
        currentDirectoryPath = currentDirectoryPath,
        rootLabel = uiState.settings.githubRepo.ifBlank { "root" },
        testTagPrefix = "drawer",
        onNavigateToDirectory = { targetDir ->
          currentDirectoryPath = targetDir
          if (targetDir.isEmpty()) {
            expandedFolders.keys.toList().forEach { expandedFolders[it] = false }
            coroutineScope.launch { treeListState.animateScrollToItem(0) }
          } else {
            val ancestors = TreeHierarchyBuilder.ancestorFolderPaths(targetDir).toSet()
            expandedFolders.keys.toList().forEach { key ->
              if (key !in ancestors) {
                expandedFolders[key] = false
              }
            }
            ancestors.forEach { expandedFolders[it] = true }
            pendingScrollFolderPath = targetDir
          }
        },
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }

    // Tree Body
    if (!hasRepo && uiState.treeFiles.isEmpty()) {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(18.dp),
        contentAlignment = Alignment.Center,
      ) {
        ArchivalEmptyStateCard(
          icon = Icons.Outlined.AccountTree,
          eyebrow = "GITHUB TREE",
          title = "No repository connected",
          body = "Sign in with GitHub or configure your owner, repo, branch, and PAT in Setup.",
        ) {
          Button(
            onClick = {
              if (uiState.isGitHubOAuthSignedIn) {
                showRepoPicker = true
                if (uiState.accessibleRepos.isEmpty()) onRefreshRepos()
              } else {
                onOpenGitHubOAuth()
              }
            },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
              ),
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("drawer_empty_oauth_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.LockOpen,
              contentDescription = null,
              modifier = Modifier.padding(end = 6.dp),
            )
            Text(
              if (uiState.isGitHubOAuthSignedIn) "Choose Repository" else "Sign in with GitHub"
            )
          }
          OutlinedButton(
            onClick = {
              onCloseDrawer()
              onNavigateToSetup()
            },
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("drawer_open_setup_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Settings,
              contentDescription = null,
              modifier = Modifier.padding(end = 6.dp),
            )
            Text("Open Setup")
          }
        }
      }
    } else if (visibleRows.isEmpty()) {
      Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (isFiltering) {
          Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
          text =
            if (isFiltering) "No matching files"
            else if (uiState.isFetchingTree) "Fetching repository tree…"
            else "Repository tree is empty",
          style = MaterialTheme.typography.titleMedium,
        )
        if (isFiltering) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "No files matched \"${searchQuery.trim()}\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!uiState.drawerSearchByFullPath && !searchQuery.contains('/')) {
              TextButton(
                onClick = onToggleSearchByFullPath,
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Text("Search full path")
              }
            }
            TextButton(
              onClick = { onSearchQueryChange("") },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Text("Clear search")
            }
          }
        } else if (!uiState.isFetchingTree) {
          Spacer(modifier = Modifier.height(6.dp))
          TextButton(
            onClick = onRefreshTree,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Text("Fetch Tree")
          }
        }
      }
    } else {
      LazyColumn(
        state = treeListState,
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("drawer_tree_list"),
        contentPadding = PaddingValues(vertical = 6.dp),
      ) {
        items(visibleRows, key = { it.key }) { row ->
          val itemModifier =
            Modifier.animateItem(
              fadeInSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
              fadeOutSpec = tween(FeldbuchMotion.FAST_EXIT_MS),
              placementSpec = FeldbuchMotion.OffsetSpring,
            )
          when (row) {
            is VisibleTreeRow.FolderRow -> {
              DrawerFolderRow(
                row = row,
                isActiveDirectory = currentDirectoryPath == row.folderPath,
                modifier = itemModifier,
                onClick = {
                  val nextExpanded = !row.isExpanded
                  expandedFolders[row.folderPath] = nextExpanded
                  currentDirectoryPath =
                    if (nextExpanded) row.folderPath
                    else TreeHierarchyBuilder.parentDirectoryOfFolder(row.folderPath)
                },
              )
            }
            is VisibleTreeRow.FileRow -> {
              DrawerFileRow(
                row = row,
                searchQuery = searchQuery,
                modifier = itemModifier,
                onToggleSelection = {
                  currentDirectoryPath = TreeHierarchyBuilder.parentDirectoryOfFile(row.file.path)
                  onToggleTrayPath(row.file.path)
                },
                onPreview = {
                  currentDirectoryPath = TreeHierarchyBuilder.parentDirectoryOfFile(row.file.path)
                  onOpenFilePreview(row.file)
                },
              )
            }
          }
        }
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Footer Context Tray Summary Strip
    Surface(
      onClick = {
        onCloseDrawer()
        onOpenTraySheet()
      },
      color = MaterialTheme.colorScheme.surfaceContainer,
      modifier = Modifier.fillMaxWidth().testTag("drawer_tray_footer"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = Icons.Outlined.Inventory2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            val trimmedSuffix =
              if (uiState.trimmedTrayPaths.isNotEmpty()) " · trimmed" else ""
            Text(
              text =
                "Tray: ${uiState.trayFiles.size} ${if (uiState.trayFiles.size == 1) "file" else "files"}$trimmedSuffix",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text =
                "${numberFmt.format(uiState.trayCharCount)} chars · ~${numberFmt.format(uiState.trayEstimatedTokens)} tok",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Text(
          text = "Inspect",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.secondary,
        )
      }
    }
  }
}

@Composable
private fun DrawerFolderRow(
  row: VisibleTreeRow.FolderRow,
  isActiveDirectory: Boolean = false,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val rowBg by
    animateColorAsState(
      targetValue =
        if (isActiveDirectory) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f)
        else Color.Transparent,
      animationSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
      label = "folderRowBg",
    )
  val chevronRotation by
    animateFloatAsState(
      targetValue = if (row.isExpanded) 180f else 0f,
      animationSpec = FeldbuchMotion.SnappySpring,
      label = "folderChevron",
    )

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(rowBg)
        .clickable(onClick = onClick)
        .minimumInteractiveComponentSize()
        .padding(end = 12.dp, top = 4.dp, bottom = 4.dp)
        .testTag("drawer_folder_${row.folderPath}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IndentGuides(depth = row.depth)
    Icon(
      imageVector = Icons.Outlined.ExpandMore,
      contentDescription = if (row.isExpanded) "Collapse ${row.name}" else "Expand ${row.name}",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
    )
    Spacer(modifier = Modifier.width(4.dp))
    Icon(
      imageVector = if (row.isExpanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.secondary,
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = row.name,
      style = MaterialTheme.typography.titleSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text =
        if (row.selectedFileCount > 0) "${row.totalFileCount} (${row.selectedFileCount})"
        else "${row.totalFileCount}",
      style = MaterialTheme.typography.labelSmall,
      color =
        if (row.selectedFileCount > 0) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun DrawerFileRow(
  row: VisibleTreeRow.FileRow,
  searchQuery: String,
  modifier: Modifier = Modifier,
  onToggleSelection: () -> Unit,
  onPreview: () -> Unit,
) {
  val highlightStyle =
    SpanStyle(
      fontWeight = FontWeight.SemiBold,
      background = MaterialTheme.colorScheme.secondaryContainer,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  val annotatedName =
    remember(row.fileName, searchQuery, highlightStyle) {
      buildHighlightedFileName(row.fileName, searchQuery, highlightStyle)
    }
  val rowBg by
    animateColorAsState(
      targetValue =
        if (row.isSelected && !row.isBlocked) {
          MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f)
        } else {
          Color.Transparent
        },
      animationSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
      label = "drawerFileRowBg",
    )

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(rowBg)
        .alpha(if (row.isBlocked) 0.52f else 1f)
        .then(
          if (row.isBlocked) {
            Modifier
          } else {
            Modifier.clickable(onClick = onPreview)
          }
        )
        .minimumInteractiveComponentSize()
        .padding(end = 12.dp, top = 2.dp, bottom = 2.dp)
        .testTag("drawer_file_${row.file.path}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IndentGuides(depth = row.depth)
    when {
      row.isBlocked -> {
        Box(
          modifier = Modifier.minimumInteractiveComponentSize().testTag("drawer_blocked_lock"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = "Blocked sensitive path",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      row.isLoading -> {
        Box(
          modifier = Modifier.minimumInteractiveComponentSize(),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.height(16.dp).width(16.dp),
          )
        }
      }
      else -> {
        Checkbox(
          checked = row.isSelected,
          onCheckedChange = { onToggleSelection() },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("drawer_file_checkbox"),
        )
      }
    }

    Icon(
      imageVector = Icons.Outlined.Description,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = 6.dp),
    )

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = annotatedName,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val subtitle =
        when {
          row.isBlocked -> "blocked · sensitive path"
          row.file.cachedText != null -> "${row.file.cachedText.length} chars · cached"
          else -> "${row.file.sizeBytes} B"
        }
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    if (row.isTrimmed) {
      Spacer(modifier = Modifier.width(6.dp))
      Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.testTag("drawer_trimmed_badge"),
      ) {
        Text(
          text = "trimmed",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
      }
    }
  }
}

internal fun buildHighlightedFileName(
  fileName: String,
  query: String,
  highlightStyle: SpanStyle,
): AnnotatedString {
  val trimmed = query.trim()
  if (trimmed.isEmpty()) return AnnotatedString(fileName)

  val tokens =
    trimmed
      .split(Regex("\\s+"))
      .map { it.substringAfterLast('/') }
      .filter { it.isNotEmpty() }
  if (tokens.isEmpty()) return AnnotatedString(fileName)

  val lowerName = fileName.lowercase(Locale.ROOT)
  return buildAnnotatedString {
    append(fileName)
    tokens.forEach { token ->
      val needle = token.lowercase(Locale.ROOT)
      var searchFrom = 0
      while (searchFrom < lowerName.length) {
        val hit = lowerName.indexOf(needle, startIndex = searchFrom)
        if (hit < 0) break
        addStyle(highlightStyle, start = hit, end = hit + needle.length)
        searchFrom = hit + needle.length
      }
    }
  }
}

@Composable
private fun IndentGuides(depth: Int) {
  val startPad = 8.dp
  Spacer(modifier = Modifier.width(startPad))
  repeat(depth) {
    Box(
      modifier =
        Modifier.width(14.dp)
          .height(34.dp)
          .padding(start = 6.dp)
          .fillMaxHeight(),
    ) {
      Box(
        modifier =
          Modifier.width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
      )
    }
  }
}
