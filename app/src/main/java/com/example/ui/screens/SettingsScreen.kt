package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.FeldbuchUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  uiState: FeldbuchUiState,
  onSaveGitHub: (owner: String, repo: String, branch: String, pat: String) -> Unit,
  onTestGitHub: (owner: String, repo: String, branch: String, pat: String) -> Unit,
  onSaveChatAndHub: (endpointUrl: String, apiKey: String, modelName: String, hubUrl: String) -> Unit,
  onTestChat: (endpointUrl: String, apiKey: String, modelName: String, hubUrl: String) -> Unit,
  onRefreshHub: () -> Unit,
  onClearMessages: () -> Unit,
  onClearTree: () -> Unit,
) {
  var ghOwner by remember(uiState.settings.githubOwner) {
    mutableStateOf(uiState.settings.githubOwner)
  }
  var ghRepo by remember(uiState.settings.githubRepo) { mutableStateOf(uiState.settings.githubRepo) }
  var ghBranch by remember(uiState.settings.githubBranch) {
    mutableStateOf(uiState.settings.githubBranch)
  }
  var ghPat by remember(uiState.settings.githubPat) { mutableStateOf(uiState.settings.githubPat) }
  var showPat by remember { mutableStateOf(false) }

  var endpointUrl by remember(uiState.settings.endpointUrl) {
    mutableStateOf(uiState.settings.endpointUrl)
  }
  var apiKey by remember(uiState.settings.apiKey) { mutableStateOf(uiState.settings.apiKey) }
  var modelName by remember(uiState.settings.modelName) {
    mutableStateOf(uiState.settings.modelName)
  }
  var hubUrl by remember(uiState.settings.hubUrl) { mutableStateOf(uiState.settings.hubUrl) }
  var showApiKey by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Group 1: GitHub Repository (Read-only)
    item {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(text = "GitHub Repository (Read-only)", style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "Stored on-device only. PAT is sent only in the Authorization header to api.github.com.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = ghOwner,
              onValueChange = { ghOwner = it },
              label = { Text("Owner") },
              singleLine = true,
              textStyle = MaterialTheme.typography.labelMedium,
              modifier = Modifier.weight(1f).testTag("github_owner_input"),
            )
            OutlinedTextField(
              value = ghRepo,
              onValueChange = { ghRepo = it },
              label = { Text("Repo") },
              singleLine = true,
              textStyle = MaterialTheme.typography.labelMedium,
              modifier = Modifier.weight(1f).testTag("github_repo_input"),
            )
          }

          OutlinedTextField(
            value = ghBranch,
            onValueChange = { ghBranch = it },
            label = { Text("Branch (default main)") },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().testTag("github_branch_input"),
          )

          OutlinedTextField(
            value = ghPat,
            onValueChange = { ghPat = it },
            label = { Text("Personal Access Token (PAT)") },
            singleLine = true,
            visualTransformation =
              if (showPat) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { showPat = !showPat }) {
                Icon(
                  imageVector =
                    if (showPat) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                  contentDescription = if (showPat) "Hide PAT" else "Show PAT",
                )
              }
            },
            textStyle = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().testTag("github_pat_input"),
          )

          uiState.gitHubTestStatus?.let { status ->
            Text(
              text = status,
              style = MaterialTheme.typography.labelMedium,
              color =
                if (status.startsWith("200")) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("github_test_status"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = { onTestGitHub(ghOwner, ghRepo, ghBranch, ghPat) },
              enabled = !uiState.isTestingGitHub && ghOwner.isNotBlank() && ghRepo.isNotBlank(),
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("test_github_button"),
            ) {
              if (uiState.isTestingGitHub) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp).width(16.dp))
              } else {
                Text("Test GitHub")
              }
            }

            Button(
              onClick = { onSaveGitHub(ghOwner, ghRepo, ghBranch, ghPat) },
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("save_github_button"),
            ) {
              Text("Save GitHub")
            }
          }
        }
      }
    }

    // Group 2: Chat Endpoint & Hub Check
    item {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(text = "Chat /v1 Endpoint & Hub", style = MaterialTheme.typography.headlineSmall)

          OutlinedTextField(
            value = endpointUrl,
            onValueChange = { endpointUrl = it },
            label = { Text("Endpoint base URL") },
            placeholder = { Text("http://127.0.0.1:9099/v1") },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().testTag("endpoint_url_input"),
          )

          OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation =
              if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { showApiKey = !showApiKey }) {
                Icon(
                  imageVector =
                    if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                  contentDescription = if (showApiKey) "Hide API key" else "Show API key",
                )
              }
            },
            textStyle = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
          )

          OutlinedTextField(
            value = modelName,
            onValueChange = { modelName = it },
            label = { Text("Model ID") },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().testTag("model_name_input"),
          )

          if (uiState.availableModels.isNotEmpty()) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              uiState.availableModels.forEach { m ->
                FilterChip(
                  selected = modelName == m,
                  onClick = { modelName = m },
                  label = { Text(m, style = MaterialTheme.typography.labelSmall) },
                )
              }
            }
          }

          uiState.chatTestStatus?.let { status ->
            Text(
              text = status,
              style = MaterialTheme.typography.labelMedium,
              color =
                if (status.startsWith("200")) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("chat_test_status"),
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          OutlinedTextField(
            value = hubUrl,
            onValueChange = { hubUrl = it },
            label = { Text("Hub check URL (2s timeout)") },
            placeholder = { Text("http://127.0.0.1:9099/v1/models") },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().testTag("hub_url_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector =
                  if (uiState.hubReachable) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                contentDescription = null,
                tint =
                  if (uiState.hubReachable) MaterialTheme.colorScheme.tertiary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text =
                  when {
                    uiState.isCheckingHub -> "Checking hub…"
                    uiState.hubReachable -> "Hub reachable"
                    uiState.settings.hubUrl.isBlank() -> "Hub check disabled"
                    else -> "Hub unreachable"
                  },
                style = MaterialTheme.typography.labelMedium,
              )
            }
            OutlinedButton(
              onClick = {
                onSaveChatAndHub(endpointUrl, apiKey, modelName, hubUrl)
                onRefreshHub()
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("recheck_hub_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
              )
              Text("Check Hub")
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = { onTestChat(endpointUrl, apiKey, modelName, hubUrl) },
              enabled = !uiState.isTestingChat && endpointUrl.isNotBlank(),
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("test_chat_button"),
            ) {
              if (uiState.isTestingChat) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp).width(16.dp))
              } else {
                Text("Test Chat")
              }
            }

            Button(
              onClick = { onSaveChatAndHub(endpointUrl, apiKey, modelName, hubUrl) },
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("save_settings_button"),
            ) {
              Text("Save Chat")
            }
          }
        }
      }
    }

    // Group 3: Local Data
    item {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(text = "Local Cache", style = MaterialTheme.typography.titleMedium)
          Text(
            text =
              "Tree paths: ${uiState.treeFiles.size} · Messages: ${uiState.messages.size} (${uiState.unsentMessages.size} waiting)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onClearMessages,
              enabled = uiState.messages.isNotEmpty() || uiState.allSessionNotes.isNotEmpty(),
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
              )
              Text("Clear Chat")
            }
            OutlinedButton(
              onClick = onClearTree,
              enabled = uiState.treeFiles.isNotEmpty(),
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
              )
              Text("Clear Tree")
            }
          }
        }
      }
    }
  }
}
