package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.FeldbuchSettings
import com.example.domain.UrlValidationResult
import com.example.domain.UrlValidator
import com.example.ui.FeldbuchUiState
import com.example.ui.components.EditorialCardHeader
import com.example.ui.components.PulsingStatusBeacon
import com.example.ui.theme.FeldbuchMotion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  uiState: FeldbuchUiState,
  onSaveGitHub: (owner: String, repo: String, branch: String, pat: String) -> Unit,
  onTestGitHub: (owner: String, repo: String, branch: String, pat: String) -> Unit,
  onOpenGitHubOAuth: () -> Unit = {},
  onSignOutGitHubOAuth: () -> Unit = {},
  onSaveChatAndHub: (endpointUrl: String, apiKey: String, modelName: String, hubUrl: String, maxTokens: Int) -> Unit,
  onTestChat: (endpointUrl: String, apiKey: String, modelName: String, hubUrl: String, maxTokens: Int) -> Unit,
  onRefreshHub: () -> Unit,
  onSaveEule: (euleUrl: String, euleKey: String, demoMode: Boolean) -> Unit = { _, _, _ -> },
  onRefreshEule: () -> Unit = {},
  onSaveStellwerkUrl: (String) -> Unit = {},
  onOpenStellwerk: (String) -> Unit = {},
  onCopiedMessage: (String) -> Unit = {},
  onUpdateTreeSync: (enabled: Boolean, intervalHours: Long) -> Unit = { _, _ -> },
  onTriggerBackgroundTreeSync: () -> Unit = {},
  onDismissSecretNotice: () -> Unit,
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
  var maxTokensText by remember(uiState.settings.maxTokens) {
    mutableStateOf(uiState.settings.maxTokens.toString())
  }
  var hubUrl by remember(uiState.settings.hubUrl) { mutableStateOf(uiState.settings.hubUrl) }
  var showApiKey by remember { mutableStateOf(false) }

  var euleUrl by remember(uiState.settings.euleUrl) { mutableStateOf(uiState.settings.euleUrl) }
  var euleKey by remember(uiState.settings.euleKey) { mutableStateOf(uiState.settings.euleKey) }
  var euleDemoMode by remember(uiState.settings.euleDemoMode) {
    mutableStateOf(uiState.settings.euleDemoMode)
  }
  var showEuleKey by remember { mutableStateOf(false) }

  val endpointValidation = UrlValidator.validateHttps(endpointUrl, "Endpoint URL", allowBlank = true)
  val endpointTestValidation = UrlValidator.validateHttps(endpointUrl, "Endpoint URL", allowBlank = false)
  val hubValidation = UrlValidator.validateHttps(hubUrl, "Hub URL", allowBlank = true)
  val euleValidation = UrlValidator.validateHttps(euleUrl, "Eule URL", allowBlank = true)
  val parsedMaxTokens = maxTokensText.trim().toIntOrNull()?.takeIf { it > 0 }
  val maxTokensInvalid = maxTokensText.trim().isEmpty() || parsedMaxTokens == null

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    uiState.secretRecoveryNotice?.let { notice ->
      item {
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.errorContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
          modifier = Modifier.fillMaxWidth().testTag("secret_recovery_banner"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Outlined.WarningAmber,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
            )
            Text(
              text = notice,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismissSecretNotice) { Text("Dismiss") }
          }
        }
      }
    }

    // Group 1: GitHub Repository (Read-only)
    item {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
        modifier =
          Modifier.fillMaxWidth()
            .animateContentSize(animationSpec = FeldbuchMotion.ExpandSizeSpring),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          EditorialCardHeader(
            icon = Icons.Outlined.AccountTree,
            title = "GitHub Repository",
          )
          Text(
            text = "Sign in with OAuth2 to browse private repositories in the drawer, or use a fine-grained PAT below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().testTag("settings_github_oauth_card"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector =
                    if (uiState.isGitHubOAuthSignedIn) Icons.Outlined.VerifiedUser
                    else Icons.Outlined.LockOpen,
                  contentDescription = null,
                  tint =
                    if (uiState.isGitHubOAuthSignedIn) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(
                    text =
                      if (uiState.isGitHubOAuthSignedIn) {
                        "OAuth2: @${uiState.settings.githubOAuthLogin}"
                      } else {
                        "GitHub OAuth2 not connected"
                      },
                    style = MaterialTheme.typography.labelLarge,
                  )
                  Text(
                    text =
                      if (uiState.isGitHubOAuthSignedIn) {
                        "${uiState.accessibleRepos.size} accessible repos · encrypted token"
                      } else {
                        "Grants read access to private repos via drawer"
                      },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              if (uiState.isGitHubOAuthSignedIn) {
                TextButton(
                  onClick = onSignOutGitHubOAuth,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("settings_oauth_signout_button"),
                ) {
                  Text("Sign out")
                }
              } else {
                OutlinedButton(
                  onClick = onOpenGitHubOAuth,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("settings_oauth_signin_button"),
                ) {
                  Text("Sign in")
                }
              }
            }
          }

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
            supportingText = {
              Text("Use a fine-grained, read-only GitHub token for one repo.")
            },
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

    // Group 2: Chat Endpoint & Hub Check (HTTPS only)
    item {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
        modifier =
          Modifier.fillMaxWidth()
            .animateContentSize(animationSpec = FeldbuchMotion.ExpandSizeSpring),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          EditorialCardHeader(
            icon = Icons.Outlined.Forum,
            title = "Chat /v1 Endpoint & Hub",
          )
          Text(
            text = "All URLs require https:// (cleartext HTTP is disabled). Context caps: 20,000 chars/file, 60,000 total.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          OutlinedTextField(
            value = endpointUrl,
            onValueChange = { endpointUrl = it },
            label = { Text("Endpoint base URL (https://)") },
            placeholder = { Text("https://your-host.ts.net/v1") },
            isError = endpointValidation is UrlValidationResult.Invalid,
            supportingText =
              if (endpointValidation is UrlValidationResult.Invalid) {
                { Text(endpointValidation.message) }
              } else {
                null
              },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().testTag("endpoint_url_input"),
          )

          OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key (encrypted at rest)") },
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

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = modelName,
              onValueChange = { modelName = it },
              label = { Text("Model ID") },
              singleLine = true,
              textStyle = MaterialTheme.typography.labelMedium,
              modifier = Modifier.weight(1.4f).testTag("model_name_input"),
            )
            OutlinedTextField(
              value = maxTokensText,
              onValueChange = { input -> maxTokensText = input.filter { it.isDigit() } },
              label = { Text("max_tokens") },
              placeholder = { Text(FeldbuchSettings.DEFAULT_MAX_TOKENS.toString()) },
              isError = maxTokensInvalid,
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              textStyle = MaterialTheme.typography.labelMedium,
              modifier = Modifier.weight(1f).testTag("max_tokens_input"),
            )
          }

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
            label = { Text("Hub check URL (https://, 2s timeout)") },
            placeholder = { Text("https://your-host.ts.net/health") },
            isError = hubValidation is UrlValidationResult.Invalid,
            supportingText =
              if (hubValidation is UrlValidationResult.Invalid) {
                { Text(hubValidation.message) }
              } else {
                null
              },
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
                val safeMaxTokens = parsedMaxTokens ?: FeldbuchSettings.DEFAULT_MAX_TOKENS
                onSaveChatAndHub(endpointUrl, apiKey, modelName, hubUrl, safeMaxTokens)
                onRefreshHub()
              },
              enabled =
                endpointValidation is UrlValidationResult.Valid &&
                  hubValidation is UrlValidationResult.Valid &&
                  hubUrl.isNotBlank() &&
                  !maxTokensInvalid,
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
              onClick = {
                val safeMaxTokens = parsedMaxTokens ?: FeldbuchSettings.DEFAULT_MAX_TOKENS
                onTestChat(endpointUrl, apiKey, modelName, hubUrl, safeMaxTokens)
              },
              enabled =
                !uiState.isTestingChat &&
                  endpointTestValidation is UrlValidationResult.Valid &&
                  hubValidation is UrlValidationResult.Valid &&
                  !maxTokensInvalid,
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("test_chat_button"),
            ) {
              if (uiState.isTestingChat) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp).width(16.dp))
              } else {
                Text("Test Chat")
              }
            }

            Button(
              onClick = {
                val safeMaxTokens = parsedMaxTokens ?: FeldbuchSettings.DEFAULT_MAX_TOKENS
                onSaveChatAndHub(endpointUrl, apiKey, modelName, hubUrl, safeMaxTokens)
              },
              enabled =
                endpointValidation is UrlValidationResult.Valid &&
                  hubValidation is UrlValidationResult.Valid &&
                  !maxTokensInvalid,
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("save_settings_button"),
            ) {
              Text("Save Chat")
            }
          }
        }
      }
    }

    // Group 3: Eule Memory Server (HTTPS only + Demo Mode)
    item {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
        modifier =
          Modifier.fillMaxWidth()
            .animateContentSize(animationSpec = FeldbuchMotion.ExpandSizeSpring)
            .testTag("settings_eule_card"),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          val dotColor =
            when {
              uiState.euleReachable -> MaterialTheme.colorScheme.tertiary
              uiState.isEuleConfigured -> MaterialTheme.colorScheme.error
              else -> MaterialTheme.colorScheme.outline
            }

          EditorialCardHeader(
            icon = Icons.Outlined.AutoAwesome,
            title = "Eule Memory Server",
            trailing = {
              PulsingStatusBeacon(
                color = dotColor,
                isPulsing = uiState.euleReachable || uiState.isCheckingEule,
                modifier = Modifier.testTag("eule_settings_status_dot"),
              )
            },
          )

          Text(
            text = "Connect over Tailscale HTTPS for /api/search, /api/ask, and /api/memory/suggest. Enable Demo mode to test locally without a server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          OutlinedTextField(
            value = euleUrl,
            onValueChange = { euleUrl = it },
            label = { Text("Eule URL (https://)") },
            placeholder = { Text("https://eule.tailnet.ts.net") },
            isError = euleValidation is UrlValidationResult.Invalid,
            supportingText =
              if (euleValidation is UrlValidationResult.Invalid) {
                { Text(euleValidation.message) }
              } else {
                null
              },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().testTag("eule_url_input"),
          )

          OutlinedTextField(
            value = euleKey,
            onValueChange = { euleKey = it },
            label = { Text("Eule key (optional, encrypted at rest)") },
            supportingText = {
              Text("Leave blank to omit the Authorization header.")
            },
            singleLine = true,
            visualTransformation =
              if (showEuleKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { showEuleKey = !showEuleKey }) {
                Icon(
                  imageVector =
                    if (showEuleKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                  contentDescription = if (showEuleKey) "Hide Eule key" else "Show Eule key",
                )
              }
            },
            textStyle = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth().testTag("eule_key_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(text = "Demo mode", style = MaterialTheme.typography.labelLarge)
              Text(
                text = "Uses built-in FakeEuleApi when Eule URL is blank.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = euleDemoMode,
              onCheckedChange = { euleDemoMode = it },
              modifier = Modifier.testTag("eule_demo_switch"),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text =
                when {
                  uiState.isCheckingEule -> "Checking Eule…"
                  uiState.euleReachable && uiState.settings.euleDemoMode && uiState.settings.euleUrl.isBlank() ->
                    "Demo mode active"
                  uiState.euleReachable -> "Eule reachable (/health 200)"
                  uiState.settings.euleUrl.isBlank() && !uiState.settings.euleDemoMode -> "Eule disabled"
                  else -> "Eule unreachable"
                },
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = {
                onSaveEule(euleUrl, euleKey, euleDemoMode)
                onRefreshEule()
              },
              enabled =
                !uiState.isCheckingEule &&
                  euleValidation is UrlValidationResult.Valid &&
                  (euleUrl.isNotBlank() || euleDemoMode),
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("check_eule_button"),
            ) {
              if (uiState.isCheckingEule) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp).width(16.dp))
              } else {
                Text("Check Eule")
              }
            }

            Button(
              onClick = { onSaveEule(euleUrl, euleKey, euleDemoMode) },
              enabled = euleValidation is UrlValidationResult.Valid,
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("save_eule_button"),
            ) {
              Text("Save Eule")
            }
          }
        }
      }
    }

    // Group 4: Stellwerk Web App (TWA)
    item {
      StellwerkSettingsCard(
        uiState = uiState,
        onSaveStellwerkUrl = onSaveStellwerkUrl,
        onOpenStellwerk = onOpenStellwerk,
        onCopiedMessage = onCopiedMessage,
      )
    }

    // Group 5: Local Data
    item {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        modifier =
          Modifier.fillMaxWidth()
            .animateContentSize(animationSpec = FeldbuchMotion.ExpandSizeSpring),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          EditorialCardHeader(
            icon = Icons.Outlined.Sync,
            title = "Local Cache & Offline Sync",
          )
          val syncTimestampText =
            if (uiState.settings.treeFetchedAtMs > 0L) {
              "Last cached: ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(uiState.settings.treeFetchedAtMs))}"
            } else {
              "Not synced yet"
            }
          Text(
            text =
              "Tree paths: ${uiState.treeFiles.size} ($syncTimestampText) · Messages: ${uiState.messages.size} (${uiState.unsentMessages.size} waiting)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(text = "Background tree sync", style = MaterialTheme.typography.labelLarge)
              Text(
                text = "Refreshes cached repo tree and attached files via WorkManager when connected.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = uiState.settings.treeAutoSyncEnabled,
              onCheckedChange = { enabled ->
                onUpdateTreeSync(enabled, uiState.settings.treeSyncIntervalHours)
              },
              modifier = Modifier.testTag("tree_auto_sync_switch"),
            )
          }

          AnimatedVisibility(
            visible = uiState.settings.treeAutoSyncEnabled,
            enter = FeldbuchMotion.expandFadeIn(),
            exit = FeldbuchMotion.shrinkFadeOut(),
          ) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              listOf(1L, 6L, 12L, 24L).forEach { hours ->
                FilterChip(
                  selected = uiState.settings.treeSyncIntervalHours == hours,
                  onClick = { onUpdateTreeSync(true, hours) },
                  label = { Text("Every ${hours}h", style = MaterialTheme.typography.labelSmall) },
                  modifier = Modifier.testTag("tree_sync_interval_${hours}h"),
                )
              }
            }
          }

          OutlinedButton(
            onClick = onTriggerBackgroundTreeSync,
            enabled =
              uiState.settings.githubOwner.isNotBlank() &&
                uiState.settings.githubRepo.isNotBlank(),
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("trigger_background_tree_sync_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Refresh,
              contentDescription = null,
              modifier = Modifier.padding(end = 6.dp),
            )
            Text("Queue Background Sync Now")
          }

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
