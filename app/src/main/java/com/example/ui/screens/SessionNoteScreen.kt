package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.ui.FeldbuchUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionNoteScreen(
  uiState: FeldbuchUiState,
  onDraftNote: () -> Unit,
  onDraftTextChange: (String) -> Unit,
  onBuildCopyText: () -> String?,
  onMarkSettled: () -> Unit,
  onExportJson: () -> Unit,
  onExportMarkdown: () -> Unit,
  onExportText: () -> Unit,
  onCopiedMessage: (String) -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  var showExportMenu by remember { mutableStateOf(false) }
  val hasExportableNotes =
    uiState.editableDraftText.isNotBlank() || uiState.allSessionNotes.isNotEmpty()

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
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
          Text(
            text = "Session Note",
            style = MaterialTheme.typography.headlineSmall,
          )
          Text(
            text =
              if (uiState.unsentMessages.isEmpty()) {
                "All messages are settled. Start a brainstorming chat to collect new turns."
              } else {
                "${uiState.unsentMessages.size} waiting ${if (uiState.unsentMessages.size == 1) "message" else "messages"} ready to summarize into your three-line handoff note."
              },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = onDraftNote,
              enabled = !uiState.isDraftingNote && uiState.unsentMessages.isNotEmpty(),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
              modifier =
                Modifier.weight(1f).minimumInteractiveComponentSize().testTag("draft_note_button"),
            ) {
              if (uiState.isDraftingNote) {
                CircularProgressIndicator(
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onSecondary,
                  modifier = Modifier.height(18.dp).width(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Drafting…")
              } else {
                Icon(
                  imageVector = Icons.Outlined.AutoAwesome,
                  contentDescription = null,
                  modifier = Modifier.padding(end = 6.dp),
                )
                Text("Draft Note")
              }
            }

            OutlinedButton(
              onClick = onExportJson,
              enabled = uiState.messages.isNotEmpty() || uiState.allSessionNotes.isNotEmpty(),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("export_json_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
              )
              Text("Export JSON")
            }
          }
        }
      }
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "EDITABLE HANDOFF NOTE",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = uiState.editableDraftText,
          onValueChange = onDraftTextChange,
          placeholder = {
            Text(
              "What I was thinking about: …\nWhat I concluded: …\nStill open / what to weigh: …"
            )
          },
          minLines = 6,
          maxLines = 14,
          textStyle = MaterialTheme.typography.labelMedium,
          modifier = Modifier.fillMaxWidth().testTag("draft_note_editor"),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          FilledTonalButton(
            onClick = {
              val payload = onBuildCopyText()
              if (payload != null) {
                clipboard.setText(AnnotatedString(payload))
                onCopiedMessage("Copied note + message count line to clipboard.")
              }
            },
            enabled = uiState.editableDraftText.isNotBlank(),
            modifier =
              Modifier.weight(1f).minimumInteractiveComponentSize().testTag("copy_note_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.ContentCopy,
              contentDescription = null,
              modifier = Modifier.padding(end = 6.dp),
            )
            Text("Copy Note")
          }

          Box(modifier = Modifier.weight(1f)) {
            FilledTonalButton(
              onClick = { showExportMenu = true },
              enabled = hasExportableNotes,
              modifier =
                Modifier.fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("export_note_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
              )
              Text("Export Note")
            }

            DropdownMenu(
              expanded = showExportMenu,
              onDismissRequest = { showExportMenu = false },
            ) {
              DropdownMenuItem(
                text = { Text("Markdown (.md)") },
                leadingIcon = {
                  Icon(imageVector = Icons.Outlined.Description, contentDescription = null)
                },
                onClick = {
                  showExportMenu = false
                  onExportMarkdown()
                },
                modifier = Modifier.testTag("export_markdown_option"),
              )
              DropdownMenuItem(
                text = { Text("Plain Text (.txt)") },
                leadingIcon = {
                  Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null)
                },
                onClick = {
                  showExportMenu = false
                  onExportText()
                },
                modifier = Modifier.testTag("export_text_option"),
              )
            }
          }
        }

        OutlinedButton(
          onClick = onMarkSettled,
          enabled = uiState.unsentMessages.isNotEmpty(),
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .testTag("mark_settled_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.CheckCircleOutline,
            contentDescription = null,
            modifier = Modifier.padding(end = 6.dp),
          )
          Text("Mark Settled")
        }
      }
    }

    if (uiState.allSessionNotes.isNotEmpty()) {
      item {
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "SAVED DRAFTS (${uiState.allSessionNotes.size})",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      items(uiState.allSessionNotes, key = { it.id }) { past ->
        val dateStr =
          SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(past.createdAtMs))
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "$dateStr · ${past.messageCount} messages",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary,
            )
            Text(
              text = past.draftText,
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      }
    }
  }
}
