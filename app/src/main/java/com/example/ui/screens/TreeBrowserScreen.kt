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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TreeFileEntity
import com.example.domain.PathBlocklist
import com.example.domain.TreeHierarchyBuilder
import com.example.ui.FeldbuchUiState
import com.example.ui.components.AnimatedCounterValue
import com.example.ui.components.ArchivalEmptyStateCard
import com.example.ui.components.SpinningSyncIcon
import com.example.ui.theme.FeldbuchMotion
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun TreeBrowserScreen(
  uiState: FeldbuchUiState,
  onRefreshTree: () -> Unit,
  onFilterQueryChange: (String) -> Unit,
  onToggleTrayPath: (String) -> Unit,
  onOpenFilePreview: (TreeFileEntity) -> Unit,
  onOpenTraySheet: () -> Unit,
  onNavigateToSetup: () -> Unit,
) {
  val numberFmt = NumberFormat.getIntegerInstance(Locale.getDefault())
  val coroutineScope = rememberCoroutineScope()
  val listState = rememberLazyListState()
  val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }
  var currentDirectoryPath by
    remember(uiState.settings.githubOwner, uiState.settings.githubRepo) {
      mutableStateOf("")
    }
  val isFiltering = uiState.pathFilterQuery.isNotBlank()

  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        TreeStatusBar(
          owner = uiState.settings.githubOwner,
          repo = uiState.settings.githubRepo,
          branch = uiState.settings.githubBranch,
          fileCount = uiState.treeFiles.size,
          treeFetchedAtMs = uiState.settings.treeFetchedAtMs,
          isFetchingTree = uiState.isFetchingTree,
          onRefreshTree = onRefreshTree,
          onNavigateToSetup = onNavigateToSetup,
        )
      }

      if (uiState.treeFiles.isNotEmpty()) {
        item {
          OutlinedTextField(
            value = uiState.pathFilterQuery,
            onValueChange = onFilterQueryChange,
            placeholder = { Text("Filter filenames or paths…") },
            leadingIcon = {
              Icon(imageVector = Icons.Outlined.FilterList, contentDescription = "Filter paths")
            },
            trailingIcon = {
              if (uiState.pathFilterQuery.isNotEmpty()) {
                IconButton(onClick = { onFilterQueryChange("") }) {
                  Icon(imageVector = Icons.Outlined.Close, contentDescription = "Clear filter")
                }
              }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().testTag("path_filter_input"),
          )
        }

        item {
          Text(
            text =
              if (isFiltering) {
                "${uiState.filteredTreeFiles.size} of ${uiState.treeFiles.size} paths"
              } else {
                "${uiState.treeFiles.size} paths in ${uiState.folderGroups.size} folders"
              },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        item {
          RepoBreadcrumbBar(
            currentDirectoryPath = currentDirectoryPath,
            rootLabel = uiState.settings.githubRepo.ifBlank { "root" },
            testTagPrefix = "tree",
            onNavigateToDirectory = { targetDir ->
              currentDirectoryPath = targetDir
              if (targetDir.isEmpty()) {
                coroutineScope.launch { listState.animateScrollToItem(0) }
              } else {
                uiState.folderGroups.forEach { g ->
                  if (g.folderPath == targetDir || g.folderPath.startsWith(targetDir)) {
                    expandedFolders[g.folderPath] = true
                  }
                }
                val matchIdx =
                  uiState.folderGroups.indexOfFirst {
                    it.folderPath == targetDir || it.folderPath.startsWith(targetDir)
                  }
                if (matchIdx >= 0) {
                  // Header items before folderGroups: status(0), filter(1), count(2), breadcrumb(3)
                  coroutineScope.launch { listState.animateScrollToItem(4 + matchIdx) }
                }
              }
            },
          )
        }

        if (uiState.folderGroups.isEmpty()) {
          item {
            Surface(
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text("No matching file paths", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Try a shorter filename substring.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(uiState.folderGroups, key = { it.folderPath }) { group ->
            val isExpanded =
              if (isFiltering) true
              else expandedFolders[group.folderPath] ?: (uiState.folderGroups.size <= 3)

            CollapsibleFolderCard(
              folderPath = group.folderPath,
              files = group.files,
              isExpanded = isExpanded,
              selectedTrayPaths = uiState.settings.selectedTrayPaths,
              trimmedTrayPaths = uiState.trimmedTrayPaths,
              loadingPaths = uiState.loadingPaths,
              modifier =
                Modifier.animateItem(
                  fadeInSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
                  fadeOutSpec = tween(FeldbuchMotion.FAST_EXIT_MS),
                  placementSpec = FeldbuchMotion.OffsetSpring,
                ),
              onToggleExpand = {
                val nextExpanded = !isExpanded
                expandedFolders[group.folderPath] = nextExpanded
                val normalizedFolder = if (group.folderPath == "(root)") "" else group.folderPath
                currentDirectoryPath =
                  if (nextExpanded) normalizedFolder
                  else TreeHierarchyBuilder.parentDirectoryOfFolder(normalizedFolder)
              },
              onToggleFile = { path ->
                currentDirectoryPath = TreeHierarchyBuilder.parentDirectoryOfFile(path)
                onToggleTrayPath(path)
              },
              onOpenPreview = { file ->
                currentDirectoryPath = TreeHierarchyBuilder.parentDirectoryOfFile(file.path)
                onOpenFilePreview(file)
              },
            )
          }
        }
      }
    }

    val totalCap = uiState.settings.totalCharCap.coerceAtLeast(1)
    val capacityFraction by
      animateFloatAsState(
        targetValue = (uiState.trayCharCount.toFloat() / totalCap.toFloat()).coerceIn(0f, 1f),
        animationSpec = FeldbuchMotion.SnappySpring,
        label = "trayCapacityFraction",
      )

    AnimatedVisibility(
      visible = uiState.treeFiles.isNotEmpty() || uiState.trayFiles.isNotEmpty(),
      enter = FeldbuchMotion.pillSlideIn(),
      exit = FeldbuchMotion.pillSlideOut(),
      modifier = Modifier.align(Alignment.BottomCenter),
    ) {
      Surface(
        onClick = onOpenTraySheet,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier =
          Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .testTag("context_tray_pill"),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(imageVector = Icons.Outlined.Inventory2, contentDescription = null)
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                val trimmedSuffix =
                  if (uiState.trimmedTrayPaths.isNotEmpty()) " · trimmed" else ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "Context Tray · ",
                    style = MaterialTheme.typography.titleSmall,
                  )
                  AnimatedCounterValue(
                    count = uiState.trayFiles.size,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                  )
                  Text(
                    text =
                      " ${if (uiState.trayFiles.size == 1) "file" else "files"}$trimmedSuffix",
                    style = MaterialTheme.typography.titleSmall,
                  )
                }
                Text(
                  text =
                    "${numberFmt.format(uiState.trayCharCount)} chars · ~${numberFmt.format(uiState.trayEstimatedTokens)} tokens",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
              }
            }
            Text(
              text = "Inspect",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.secondaryContainer,
            )
          }

          // Subtle capacity gauge bar along bottom of tray pill
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f))
          ) {
            Box(
              modifier =
                Modifier.fillMaxHeight()
                  .fillMaxWidth(capacityFraction)
                  .background(
                    if (uiState.trimmedTrayPaths.isNotEmpty()) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.secondaryContainer
                  )
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TreeStatusBar(
  owner: String,
  repo: String,
  branch: String,
  fileCount: Int,
  treeFetchedAtMs: Long,
  isFetchingTree: Boolean,
  onRefreshTree: () -> Unit,
  onNavigateToSetup: () -> Unit,
) {
  val hasRepo = owner.isNotBlank() && repo.isNotBlank()
  if (!hasRepo && fileCount == 0) {
    ArchivalEmptyStateCard(
      icon = Icons.Outlined.AccountTree,
      eyebrow = "REPOSITORY SOURCE",
      title = "Connect your GitHub repository",
      body =
        "Configure your GitHub owner, repo, branch, and fine-grained PAT in Setup. Feldbuch caches the repo tree and fetches raw file contents only for files you attach.",
    ) {
      Button(
        onClick = onNavigateToSetup,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
          ),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("open_setup_from_empty"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Settings,
          contentDescription = null,
          modifier = Modifier.padding(end = 6.dp),
        )
        Text("Open Setup")
      }
    }
  } else {
    val timeText =
      if (treeFetchedAtMs > 0L) {
        "tree as of ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(treeFetchedAtMs))}"
      } else {
        "tree not fetched yet"
      }
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceContainer,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier =
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "$owner/$repo",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
              ) {
                Text(
                  text = branch,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
            Text(
              text = "$fileCount paths · $timeText",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag("tree_timestamp_label"),
            )
          }
          IconButton(
            onClick = onRefreshTree,
            enabled = !isFetchingTree,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("refresh_tree_button"),
          ) {
            SpinningSyncIcon(
              isSpinning = isFetchingTree,
              imageVector = Icons.Outlined.Refresh,
              contentDescription = "Refresh GitHub tree",
              tint =
                if (isFetchingTree) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurface,
            )
          }
        }
        AnimatedVisibility(
          visible = isFetchingTree,
          enter = FeldbuchMotion.expandFadeIn(),
          exit = FeldbuchMotion.shrinkFadeOut(),
        ) {
          LinearProgressIndicator(
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().height(2.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun CollapsibleFolderCard(
  folderPath: String,
  files: List<TreeFileEntity>,
  isExpanded: Boolean,
  selectedTrayPaths: Set<String>,
  trimmedTrayPaths: Set<String>,
  loadingPaths: Set<String>,
  modifier: Modifier = Modifier,
  onToggleExpand: () -> Unit,
  onToggleFile: (String) -> Unit,
  onOpenPreview: (TreeFileEntity) -> Unit,
) {
  val selectedInFolder = files.count { it.path in selectedTrayPaths }
  val chevronRotation by
    animateFloatAsState(
      targetValue = if (isExpanded) 180f else 0f,
      animationSpec = FeldbuchMotion.SnappySpring,
      label = "folderCardChevron",
    )
  val borderColor by
    animateColorAsState(
      targetValue =
        if (selectedInFolder > 0) MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.outlineVariant,
      animationSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
      label = "folderCardBorder",
    )

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, borderColor),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = if (isExpanded) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = folderPath,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text =
              if (selectedInFolder > 0) "${files.size} ($selectedInFolder in tray)"
              else "${files.size}",
            style = MaterialTheme.typography.labelSmall,
            color =
              if (selectedInFolder > 0) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          imageVector = Icons.Outlined.ExpandMore,
          contentDescription = if (isExpanded) "Collapse folder" else "Expand folder",
          modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
        )
      }

      AnimatedVisibility(
        visible = isExpanded,
        enter = FeldbuchMotion.expandFadeIn(),
        exit = FeldbuchMotion.shrinkFadeOut(),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          files.forEach { file ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            val isBlocked = PathBlocklist.isBlocked(file.path)
            val isChecked = !isBlocked && file.path in selectedTrayPaths
            val isTrimmed = file.path in trimmedTrayPaths
            val isLoading = file.path in loadingPaths
            val fileName = file.path.substringAfterLast('/')
            val rowTint by
              animateColorAsState(
                targetValue =
                  if (isChecked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f)
                  else Color.Transparent,
                animationSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
                label = "treeFileRowTint",
              )

            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .background(rowTint)
                  .alpha(if (isBlocked) 0.52f else 1f)
                  .then(
                    if (isBlocked) {
                      Modifier
                    } else {
                      Modifier.clickable {
                        if (file.cachedText != null) {
                          onOpenPreview(file)
                        } else {
                          onToggleFile(file.path)
                        }
                      }
                    }
                  )
                  .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              when {
                isBlocked -> {
                  Box(
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("blocked_lock_icon"),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Lock,
                      contentDescription = "Blocked sensitive file",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                isLoading -> {
                  Box(
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    contentAlignment = Alignment.Center,
                  ) {
                    CircularProgressIndicator(
                      strokeWidth = 2.dp,
                      modifier = Modifier.height(18.dp).width(18.dp),
                    )
                  }
                }
                else -> {
                  Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggleFile(file.path) },
                    modifier =
                      Modifier.minimumInteractiveComponentSize().testTag("tree_file_checkbox"),
                  )
                }
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = fileName,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                val meta =
                  when {
                    isBlocked -> "blocked · sensitive path"
                    file.cachedText != null -> "${file.cachedText.length} chars · cached"
                    else -> "${file.sizeBytes} bytes"
                  }
                Text(
                  text = meta,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              if (isTrimmed) {
                Surface(
                  shape = MaterialTheme.shapes.extraSmall,
                  color = MaterialTheme.colorScheme.secondaryContainer,
                  modifier = Modifier.testTag("trimmed_badge"),
                ) {
                  Text(
                    text = "trimmed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewSheet(
  file: TreeFileEntity,
  isInTray: Boolean,
  isTrimmed: Boolean,
  isLoading: Boolean = false,
  onToggleTray: () -> Unit,
  onDismiss: () -> Unit,
) {
  val isBlocked = PathBlocklist.isBlocked(file.path)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = file.path,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          val chars = file.cachedText?.length ?: 0
          val trimmedLabel = if (isTrimmed) " · trimmed" else ""
          Text(
            text =
              if (file.cachedText == null && isLoading) "Fetching raw content…"
              else "$chars chars · ~${chars / 4} tokens$trimmedLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (isBlocked) {
          Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.errorContainer,
          ) {
            Text(
              text = "Blocked",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        } else {
          FilterChip(
            selected = isInTray,
            onClick = onToggleTray,
            label = { Text(if (isInTray) "In Tray" else "Add to Tray") },
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
      ) {
        if (file.cachedText == null && isLoading) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp).width(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "Fetching raw file from GitHub…",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
            Text(
              text = file.cachedText ?: "(Not fetched yet — tap Add to Tray or tick checkbox to fetch raw content.)",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}
