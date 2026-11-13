package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TreeFileEntity
import com.example.ui.FeldbuchUiState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
  val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }
  val isFiltering = uiState.pathFilterQuery.isNotBlank()

  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Repo / Tree Header Bar
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
        // Filename Filter Input (case-insensitive path substring only)
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
              loadingPaths = uiState.loadingPaths,
              onToggleExpand = {
                expandedFolders[group.folderPath] = !isExpanded
              },
              onToggleFile = onToggleTrayPath,
              onOpenPreview = onOpenFilePreview,
            )
          }
        }
      }
    }

    // Persistent Floating Context Tray Bar
    if (uiState.treeFiles.isNotEmpty() || uiState.trayFiles.isNotEmpty()) {
      Surface(
        onClick = onOpenTraySheet,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .testTag("context_tray_pill"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Outlined.Inventory2, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text =
                  "Context Tray · ${uiState.trayFiles.size} ${if (uiState.trayFiles.size == 1) "file" else "files"}",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text =
                  "${numberFmt.format(uiState.trayCharCount)} chars · ~${numberFmt.format(uiState.trayEstimatedTokens)} tokens",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
              )
            }
          }
          Text(text = "Inspect", style = MaterialTheme.typography.labelLarge)
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
    Surface(
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surfaceContainerLowest,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.AccountTree,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
        )
        Text(
          text = "Connect your GitHub repository",
          style = MaterialTheme.typography.headlineSmall,
        )
        Text(
          text =
            "Configure your GitHub owner, repo, branch, and PAT in Setup. Feldbuch fetches the repo tree on open and fetches raw file contents only for files you tick.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
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
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "$owner/$repo ($branch)",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
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
          if (isFetchingTree) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp).width(20.dp))
          } else {
            Icon(imageVector = Icons.Outlined.Refresh, contentDescription = "Refresh GitHub tree")
          }
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
  loadingPaths: Set<String>,
  onToggleExpand: () -> Unit,
  onToggleFile: (String) -> Unit,
  onOpenPreview: (TreeFileEntity) -> Unit,
) {
  val selectedInFolder = files.count { it.path in selectedTrayPaths }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
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
            imageVector = Icons.Outlined.Folder,
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
              if (selectedInFolder > 0) "${files.size} (${selectedInFolder} in tray)"
              else "${files.size}",
            style = MaterialTheme.typography.labelSmall,
            color =
              if (selectedInFolder > 0) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Icon(
          imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
          contentDescription = if (isExpanded) "Collapse folder" else "Expand folder",
        )
      }

      AnimatedVisibility(visible = isExpanded) {
        Column(modifier = Modifier.fillMaxWidth()) {
          files.forEach { file ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            val isChecked = file.path in selectedTrayPaths
            val isLoading = file.path in loadingPaths
            val fileName = file.path.substringAfterLast('/')

            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable {
                    if (file.cachedText != null) {
                      onOpenPreview(file)
                    } else {
                      onToggleFile(file.path)
                    }
                  }
                  .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (isLoading) {
                Box(
                  modifier = Modifier.minimumInteractiveComponentSize(),
                  contentAlignment = Alignment.Center,
                ) {
                  CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.height(18.dp).width(18.dp),
                  )
                }
              } else {
                Checkbox(
                  checked = isChecked,
                  onCheckedChange = { onToggleFile(file.path) },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("tree_file_checkbox"),
                )
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = fileName,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                val meta =
                  if (file.cachedText != null) {
                    "${file.cachedText.length} chars · cached"
                  } else {
                    "${file.sizeBytes} bytes"
                  }
                Text(
                  text = meta,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
  onToggleTray: () -> Unit,
  onDismiss: () -> Unit,
) {
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
          Text(
            text = "$chars chars · ~${chars / 4} tokens",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        FilterChip(
          selected = isInTray,
          onClick = onToggleTray,
          label = { Text(if (isInTray) "In Tray" else "Add to Tray") },
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
      ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
          Text(
            text = file.cachedText ?: "(Not fetched yet — tick checkbox to fetch raw content.)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}
