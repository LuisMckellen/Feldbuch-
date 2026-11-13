package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.network.GitHubDeviceCodeResponse
import com.example.data.network.GitHubOAuthClient

@Composable
fun GitHubOAuthDialog(
  initialClientId: String,
  isAuthenticating: Boolean,
  activeDeviceCode: GitHubDeviceCodeResponse?,
  onStartPkce: (clientId: String) -> Unit,
  onStartDeviceFlow: (clientId: String) -> Unit,
  onCancelDeviceFlow: () -> Unit,
  onCopiedCode: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var clientId by remember(initialClientId) { mutableStateOf(initialClientId) }
  val clipboard = LocalClipboardManager.current

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Outlined.LockOpen,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("GitHub OAuth2 Sign-In")
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Authenticate with GitHub (repo scope) to browse and switch private repositories directly in the file tree drawer. Tokens are encrypted at rest with Android Keystore.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
          value = clientId,
          onValueChange = { clientId = it },
          label = { Text("GitHub OAuth Client ID") },
          placeholder = { Text("Ov23li… or Iv1…") },
          supportingText = {
            Text(
              "Set via GITHUB_OAUTH_CLIENT_ID secret or enter your GitHub App / OAuth App Client ID. Callback: ${GitHubOAuthClient.REDIRECT_URI}",
              style = MaterialTheme.typography.labelSmall,
            )
          },
          leadingIcon = {
            Icon(imageVector = Icons.Outlined.Key, contentDescription = null)
          },
          singleLine = true,
          enabled = activeDeviceCode == null,
          textStyle = MaterialTheme.typography.labelMedium,
          modifier = Modifier.fillMaxWidth().testTag("oauth_client_id_input"),
        )

        if (activeDeviceCode != null) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
            modifier = Modifier.fillMaxWidth().testTag("oauth_device_code_card"),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Enter this code at ${activeDeviceCode.verificationUri}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = activeDeviceCode.userCode,
                  style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.testTag("oauth_device_user_code"),
                )
                OutlinedButton(
                  onClick = {
                    clipboard.setText(AnnotatedString(activeDeviceCode.userCode))
                    onCopiedCode("Copied code ${activeDeviceCode.userCode}")
                  },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy device code",
                    modifier = Modifier.padding(end = 4.dp),
                  )
                  Text("Copy")
                }
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                  strokeWidth = 2.dp,
                  modifier = Modifier.height(14.dp).width(14.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Waiting for GitHub authorization…",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }
          }
        } else {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          Button(
            onClick = { onStartPkce(clientId) },
            enabled = clientId.isNotBlank() && !isAuthenticating,
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("oauth_pkce_signin_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.OpenInBrowser,
              contentDescription = null,
              modifier = Modifier.padding(end = 6.dp),
            )
            Text("Continue in Browser (PKCE)")
          }

          OutlinedButton(
            onClick = { onStartDeviceFlow(clientId) },
            enabled = clientId.isNotBlank() && !isAuthenticating,
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("oauth_device_signin_button"),
          ) {
            Text("Sign in with Device Code")
          }
        }
      }
    },
    confirmButton = {
      if (activeDeviceCode != null) {
        TextButton(onClick = onCancelDeviceFlow) { Text("Cancel authorization") }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    },
  )
}
