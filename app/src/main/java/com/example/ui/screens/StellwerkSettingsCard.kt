package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.data.twa.AppSigningFingerprint
import com.example.domain.UrlValidationResult
import com.example.domain.UrlValidator
import com.example.ui.FeldbuchUiState
import com.example.ui.components.EditorialCardHeader
import com.example.ui.theme.FeldbuchMotion

@Composable
fun StellwerkSettingsCard(
  uiState: FeldbuchUiState,
  onSaveStellwerkUrl: (String) -> Unit,
  onOpenStellwerk: (String) -> Unit,
  onCopiedMessage: (String) -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  var stellwerkUrl by remember(uiState.settings.stellwerkUrl) {
    mutableStateOf(uiState.settings.stellwerkUrl)
  }
  val urlValidation = UrlValidator.validateHttps(stellwerkUrl, "Stellwerk URL", allowBlank = true)
  val openValidation = UrlValidator.validateHttps(stellwerkUrl, "Stellwerk URL", allowBlank = false)
  val fingerprint = uiState.appSha256Fingerprint
  val packageName = uiState.appPackageName

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    tonalElevation = 1.dp,
    modifier =
      Modifier.fillMaxWidth()
        .animateContentSize(animationSpec = FeldbuchMotion.ExpandSizeSpring)
        .testTag("settings_stellwerk_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      EditorialCardHeader(
        icon = Icons.AutoMirrored.Outlined.OpenInNew,
        title = "Stellwerk Web App (TWA)",
      )
      Text(
        text =
          "Launch your Stellwerk web shell as a Trusted Web Activity (falling back to Custom Tabs) from the top bar. Requires https:// and Digital Asset Links on Stellwerk.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedTextField(
        value = stellwerkUrl,
        onValueChange = { stellwerkUrl = it },
        label = { Text("Stellwerk URL (https://)") },
        placeholder = { Text("https://stellwerk.tailnet.ts.net") },
        isError = urlValidation is UrlValidationResult.Invalid,
        supportingText =
          if (urlValidation is UrlValidationResult.Invalid) {
            { Text(urlValidation.message) }
          } else {
            null
          },
        singleLine = true,
        textStyle = MaterialTheme.typography.labelMedium,
        modifier = Modifier.fillMaxWidth().testTag("stellwerk_url_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = {
            onSaveStellwerkUrl(stellwerkUrl)
            onOpenStellwerk(stellwerkUrl)
          },
          enabled = openValidation is UrlValidationResult.Valid,
          modifier =
            Modifier.weight(1f).minimumInteractiveComponentSize().testTag("open_stellwerk_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.padding(end = 6.dp),
          )
          Text("Open")
        }

        Button(
          onClick = { onSaveStellwerkUrl(stellwerkUrl) },
          enabled = urlValidation is UrlValidationResult.Valid,
          modifier =
            Modifier.weight(1f).minimumInteractiveComponentSize().testTag("save_stellwerk_button"),
        ) {
          Text("Save URL")
        }
      }

      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Outlined.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(end = 6.dp),
              )
              Text(
                text = "SIGNING SHA-256 FINGERPRINT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          if (packageName.isNotBlank()) {
            Text(
              text = "Package: $packageName",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag("stellwerk_package_name"),
            )
          }

          Text(
            text =
              if (fingerprint.isNotBlank()) fingerprint
              else "Unavailable in this environment (install signed APK to read SHA-256).",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.testTag("stellwerk_sha256_text"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = {
                clipboard.setText(AnnotatedString(fingerprint))
                onCopiedMessage("Copied SHA-256 fingerprint to clipboard.")
              },
              enabled = fingerprint.isNotBlank(),
              modifier =
                Modifier.weight(1f).minimumInteractiveComponentSize().testTag("copy_sha256_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
              )
              Text("Copy SHA-256")
            }

            TextButton(
              onClick = {
                val snippet =
                  AppSigningFingerprint.buildAssetLinksSnippet(
                    packageName = packageName.ifBlank { "com.example" },
                    sha256Fingerprint = fingerprint,
                  )
                clipboard.setText(AnnotatedString(snippet))
                onCopiedMessage("Copied assetlinks.json snippet.")
              },
              enabled = fingerprint.isNotBlank(),
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("copy_assetlinks_button"),
            ) {
              Text("assetlinks.json")
            }
          }
        }
      }
    }
  }
}
